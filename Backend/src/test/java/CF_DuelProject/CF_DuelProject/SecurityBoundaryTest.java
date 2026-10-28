package CF_DuelProject.CF_DuelProject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import CF_DuelProject.CF_DuelProject.config.WebSocketAuthInterceptor;
import CF_DuelProject.CF_DuelProject.controller.MatchController;
import CF_DuelProject.CF_DuelProject.controller.MatchSocketController;
import CF_DuelProject.CF_DuelProject.dto.ChatMessage;
import CF_DuelProject.CF_DuelProject.dto.CreateMatchRequest;
import CF_DuelProject.CF_DuelProject.dto.EmoteMessage;
import CF_DuelProject.CF_DuelProject.dto.SkipVoteMessage;
import CF_DuelProject.CF_DuelProject.ratelimit.RateLimitInterceptor;
import CF_DuelProject.CF_DuelProject.ratelimit.RateLimiterService;
import CF_DuelProject.CF_DuelProject.service.JwtService;
import CF_DuelProject.CF_DuelProject.service.MatchService;
import CF_DuelProject.CF_DuelProject.service.UserService;

/** Negative-path tests for the request/WebSocket boundaries. No Spring context or database required. */
class SecurityBoundaryTest {

    // ───────────────────────── WebSocket authentication ─────────────────────────

    private final MatchService matchService = mock(MatchService.class);
    private JwtService jwt;
    private WebSocketAuthInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "SECRET", "unit-test-secret-unit-test-secret-unit-test-secret-1234");
        interceptor = new WebSocketAuthInterceptor(jwt, matchService);
    }

    private Message<?> stomp(StompCommand cmd, String destination, String authHeader, Object user) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(cmd);
        if (destination != null) acc.setDestination(destination);
        if (authHeader != null) acc.addNativeHeader("Authorization", authHeader);
        if (user != null) acc.setUser((java.security.Principal) user);
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    @Test
    void ws_connectWithValidTokenBindsUser() {
        String token = jwt.generateToken("alice@x.com");
        Message<?> out = interceptor.preSend(stomp(StompCommand.CONNECT, null, "Bearer " + token, null), channel);
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class);
        assertNotNull(acc.getUser());
        assertEquals("alice@x.com", acc.getUser().getName());
    }

    @Test
    void ws_connectWithGarbageOrMissingTokenStaysAnonymous() {
        for (String header : new String[]{"Bearer not-a-jwt", "Bearer ", "Basic abc", null}) {
            Message<?> out = interceptor.preSend(stomp(StompCommand.CONNECT, null, header, null), channel);
            assertNull(MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class).getUser(), String.valueOf(header));
        }
    }

    @Test
    void ws_connectWithTokenSignedByAnotherKeyIsRejected() {
        JwtService attacker = new JwtService();
        ReflectionTestUtils.setField(attacker, "SECRET", "attacker-secret-attacker-secret-attacker-secret-0000");
        String forged = attacker.generateToken("alice@x.com");

        Message<?> out = interceptor.preSend(stomp(StompCommand.CONNECT, null, "Bearer " + forged, null), channel);
        assertNull(MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class).getUser());
    }

    @Test
    void ws_sendToAppDestinationRequiresAuthentication() {
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(stomp(StompCommand.SEND, "/app/match/chat", null, null), channel));
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(stomp(StompCommand.SEND, "/app/match/skip", null, null), channel));
    }

    @Test
    void ws_authenticatedSendIsAllowed() {
        Object user = new UsernamePasswordAuthenticationToken("alice@x.com", null, List.of());
        Message<?> sent = stomp(StompCommand.SEND, "/app/match/chat", null, user);
        assertSame(sent, interceptor.preSend(sent, channel));

    }

    // ───────────────────────── WebSocket handlers: sender cannot be spoofed ─────────────────────────

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);

    private MatchSocketController sockets() {
        return new MatchSocketController(template, matchService);
    }

    private static java.security.Principal user(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of());
    }

    @Test
    void chat_senderIsOverwrittenWithAuthenticatedHandle() {
        when(matchService.resolveParticipantHandle("alice@x.com", "ABC123")).thenReturn("alice");
        ChatMessage m = new ChatMessage();
        m.setInviteCode("ABC123");
        m.setSender("bob");              // spoofed
        m.setText("  hello  ");

        sockets().handleChat(m, user("alice@x.com"));

        ArgumentCaptor<ChatMessage> sent = ArgumentCaptor.forClass(ChatMessage.class);
        verify(template).convertAndSend(eq("/topic/match/ABC123/chat"), sent.capture());
        assertEquals("alice", sent.getValue().getSender());
        assertEquals("hello", sent.getValue().getText());
    }

    @Test
    void chat_fromNonParticipantOrAnonymousIsDropped() {
        when(matchService.resolveParticipantHandle("eve@x.com", "ABC123")).thenReturn(null);
        ChatMessage m = new ChatMessage();
        m.setInviteCode("ABC123");
        m.setText("hi");

        sockets().handleChat(m, user("eve@x.com"));
        sockets().handleChat(m, null);

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void chat_longTextIsTruncatedAndBlankIgnored() {
        when(matchService.resolveParticipantHandle("alice@x.com", "ABC123")).thenReturn("alice");
        ChatMessage big = new ChatMessage();
        big.setInviteCode("ABC123");
        big.setText("x".repeat(100_000));
        sockets().handleChat(big, user("alice@x.com"));

        ArgumentCaptor<ChatMessage> sent = ArgumentCaptor.forClass(ChatMessage.class);
        verify(template).convertAndSend(eq("/topic/match/ABC123/chat"), sent.capture());
        assertEquals(500, sent.getValue().getText().length());

        ChatMessage blank = new ChatMessage();
        blank.setInviteCode("ABC123");
        blank.setText("   ");
        sockets().handleChat(blank, user("alice@x.com"));
        verify(template, org.mockito.Mockito.times(1)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void emote_senderOverwrittenAndNonParticipantDropped() {
        when(matchService.resolveParticipantHandle("alice@x.com", "ABC123")).thenReturn("alice");
        EmoteMessage ok = new EmoteMessage();
        ok.setInviteCode("ABC123");
        ok.setSender("bob");
        ok.setEmote("🔥");
        sockets().handleEmote(ok, user("alice@x.com"));

        ArgumentCaptor<EmoteMessage> sent = ArgumentCaptor.forClass(EmoteMessage.class);
        verify(template).convertAndSend(eq("/topic/match/ABC123/emotes"), sent.capture());
        assertEquals("alice", sent.getValue().getSender());

        EmoteMessage bad = new EmoteMessage();
        bad.setInviteCode("OTHER");
        bad.setEmote("x");
        sockets().handleEmote(bad, user("alice@x.com"));
        verify(template, org.mockito.Mockito.times(1)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void skipVote_usesAuthenticatedHandleNotClientSuppliedSender() {
        when(matchService.resolveParticipantHandle("alice@x.com", "ABC123")).thenReturn("alice");
        SkipVoteMessage m = new SkipVoteMessage();
        m.setInviteCode("ABC123");
        m.setSender("bob"); // try to vote on the opponent's behalf

        sockets().handleSkipVote(m, user("alice@x.com"));

        verify(matchService).handleSkipVote("ABC123", "alice");
        verify(matchService, never()).handleSkipVote(anyString(), eq("bob"));
    }

    @Test
    void skipVote_anonymousOrNonParticipantNeverReachesService() {
        when(matchService.resolveParticipantHandle("eve@x.com", "ABC123")).thenReturn(null);
        SkipVoteMessage m = new SkipVoteMessage();
        m.setInviteCode("ABC123");
        m.setSender("alice");

        sockets().handleSkipVote(m, null);
        sockets().handleSkipVote(m, user("eve@x.com"));
        sockets().handleSkipVote(null, user("eve@x.com"));

        verify(matchService, never()).handleSkipVote(anyString(), anyString());
    }

    // ───────────────────────── REST: history authorization & input validation ─────────────────────────

    private MatchController matchController() {
        return new MatchController(matchService, mock(UserService.class));
    }

    @Test
    void history_otherUsersHistoryIsForbidden() {
        when(matchService.isSameAccount("alice@x.com", "bob")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> matchController().getMatchHistoryForUser(
                        new UsernamePasswordAuthenticationToken("alice@x.com", null, List.of()), "bob"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(matchService, never()).getUserMatchHistory("bob");
    }

    @Test
    void history_ownHistoryByHandleIsAllowed() {
        when(matchService.isSameAccount("alice@x.com", "alice")).thenReturn(true);
        when(matchService.getUserMatchHistory("alice")).thenReturn(List.of());

        assertEquals(200, matchController().getMatchHistoryForUser(
                new UsernamePasswordAuthenticationToken("alice@x.com", null, List.of()), "alice").getStatusCode().value());
    }

    @Test
    void createMatch_rejectsAbsurdDurations() {
        UserService users = mock(UserService.class);
        when(users.getVerifiedCfHandleByEmail("alice@x.com")).thenReturn("alice");
        MatchController c = new MatchController(matchService, users);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("alice@x.com", null, List.of());

        for (int minutes : new int[]{Integer.MAX_VALUE, 301, 10_000_000}) {
            CreateMatchRequest req = new CreateMatchRequest();
            req.setDurationMinutes(minutes);
            req.setDifficulty("EASY");
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ResponseStatusException.class, () -> c.create(auth, req)).getStatusCode(), "minutes=" + minutes);
        }
        verify(matchService, never()).createMatch(anyString(), org.mockito.ArgumentMatchers.anyInt(), anyString());
    }

    // ───────────────────────── rate limiting ─────────────────────────

    private boolean hit(RateLimitInterceptor limiter, String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(ip);
        return limiter.preHandle(req, new MockHttpServletResponse(), new Object());
    }

    @Test
    void rateLimit_appliesToBothApiAndAliasPaths() throws Exception {
        for (String uri : new String[]{"/api/match/join", "/match/join"}) {
            RateLimitInterceptor limiter = new RateLimitInterceptor(new RateLimiterService());
            int allowed = 0;
            for (int i = 0; i < 40; i++) if (hit(limiter, uri, "10.0.0.1")) allowed++;
            assertEquals(15, allowed, uri); // join bucket = 15/min
        }
    }

    @Test
    void rateLimit_aliasCannotBeUsedToBypassCreateOrStartLimits() throws Exception {
        RateLimitInterceptor limiter = new RateLimitInterceptor(new RateLimiterService());
        int created = 0, started = 0;
        for (int i = 0; i < 40; i++) {
            if (hit(limiter, "/match/create", "10.0.0.2")) created++;
            if (hit(limiter, "/match/start", "10.0.0.2")) started++;
        }
        assertEquals(8, created);
        assertEquals(15, started);
    }

    @Test
    void rateLimit_isPerClientAndRejectsWith429() throws Exception {
        RateLimitInterceptor limiter = new RateLimitInterceptor(new RateLimiterService());
        for (int i = 0; i < 15; i++) hit(limiter, "/api/match/join", "10.0.0.3");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/match/join");
        req.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(limiter.preHandle(req, res, new Object()));
        assertEquals(429, res.getStatus());

        assertTrue(hit(limiter, "/api/match/join", "10.0.0.4"), "another client must not be affected");
    }

    // ───────────────────────── subscription authorization ─────────────────────────

    @Test
    void ws_subscribingToMatchTopicRequiresAnAuthorizedParticipant() {
        // anonymous
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(stomp(StompCommand.SUBSCRIBE, "/topic/match/ABC123", null, null), channel));

        // authenticated but not a participant
        Object eve = new UsernamePasswordAuthenticationToken("eve@x.com", null, List.of());
        when(matchService.canViewMatch("eve@x.com", "ABC123")).thenReturn(false);
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(stomp(StompCommand.SUBSCRIBE, "/topic/match/ABC123/chat", null, eve), channel));

        // participant: allowed on every sub-topic, identifier is parsed out of the destination
        Object alice = new UsernamePasswordAuthenticationToken("alice@x.com", null, List.of());
        when(matchService.canViewMatch("alice@x.com", "ABC123")).thenReturn(true);
        for (String dest : new String[]{"/topic/match/ABC123", "/topic/match/ABC123/chat", "/topic/match/ABC123/skip", "/topic/match/ABC123/stopped"}) {
            Message<?> sub = stomp(StompCommand.SUBSCRIBE, dest, null, alice);
            assertSame(sub, interceptor.preSend(sub, channel), dest);
        }
    }

    @Test
    void ws_publicTournamentTopicsStayOpen() {
        Message<?> sub = stomp(StompCommand.SUBSCRIBE, "/topic/tournament/t1", null, null);
        assertSame(sub, interceptor.preSend(sub, channel));
        verify(matchService, never()).canViewMatch(anyString(), anyString());
    }

    // ───────────────────────── REST status authorization ─────────────────────────

    @Test
    void status_requiresAuthentication() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class, () -> matchController().getStatus(null, "ABC123")).getStatusCode());
    }

    @Test
    void status_isDelegatedWithCallerIdentity() {
        when(matchService.getMatchStatusFor("ABC123", "alice@x.com")).thenReturn(java.util.Map.of("status", "ONGOING"));
        assertEquals("ONGOING", matchController().getStatus(
                new UsernamePasswordAuthenticationToken("alice@x.com", null, List.of()), "ABC123").get("status"));
    }

    // ───────────────────────── rate limiter memory ─────────────────────────

    @Test
    void rateLimiter_trackedClientsAreBounded() {
        RateLimiterService limiter = new RateLimiterService();
        for (int i = 0; i < 25_000; i++) {
            limiter.resolveBucket("client-" + i + "_join", 15, 15, java.time.Duration.ofMinutes(1));
        }
        assertTrue(limiter.size() <= 10_000, "size=" + limiter.size());
        // a recently used client is still tracked (same bucket instance)
        var b1 = limiter.resolveBucket("client-24999_join", 15, 15, java.time.Duration.ofMinutes(1));
        var b2 = limiter.resolveBucket("client-24999_join", 15, 15, java.time.Duration.ofMinutes(1));
        assertSame(b1, b2);
    }
}
