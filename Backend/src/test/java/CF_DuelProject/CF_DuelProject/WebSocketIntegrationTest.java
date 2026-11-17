package CF_DuelProject.CF_DuelProject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.mongodb.client.result.UpdateResult;

import CF_DuelProject.CF_DuelProject.model.MatchPrimary;
import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.repository.CFVerificationRepository;
import CF_DuelProject.CF_DuelProject.repository.PrimaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.SecondaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.TournamentRepository;
import CF_DuelProject.CF_DuelProject.repository.UserRepository;
import CF_DuelProject.CF_DuelProject.service.JwtService;

/**
 * End-to-end WebSocket test over a REAL embedded server, REAL Spring Security/STOMP stack and real STOMP clients.
 * Only the persistence layer is mocked (no MongoDB is contacted), so this validates authentication,
 * participant authorization and message attribution — not database behaviour.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    @LocalServerPort int port;

    @MockitoBean PrimaryMatchRepository primaryRepo;
    @MockitoBean SecondaryMatchRepository secondaryRepo;
    @MockitoBean UserRepository userRepo;
    @MockitoBean TournamentRepository tournamentRepo;
    @MockitoBean CFVerificationRepository cfVerificationRepo;
    @MockitoBean MongoTemplate mongoTemplate;

    @Autowired JwtService jwt;

    private MatchPrimary match;

    @BeforeEach
    void data() {
        match = new MatchPrimary();
        match.setId("m1");
        match.setInviteCode("ABC123");
        match.setUser1("alice");
        match.setUser2("bob");
        match.setPlayer1CFHandle("alice");
        match.setPlayer2CFHandle("bob");
        match.setStatus("ONGOING");
        match.setProblems(List.of("https://codeforces.com/contest/1/problem/A", "https://codeforces.com/contest/2/problem/A"));
        match.setStartTime(new Date(System.currentTimeMillis() - 1000));
        match.setEndTime(new Date(System.currentTimeMillis() + 600_000));
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(match));
        when(primaryRepo.findById("m1")).thenReturn(Optional.of(match));

        for (String[] u : new String[][]{{"alice", "alice@x.com"}, {"bob", "bob@x.com"}, {"eve", "eve@x.com"}}) {
            User user = new User();
            user.setEmail(u[1]);
            user.setCfHandle(u[0]);
            user.setCodeforcesVerified(true);
            when(userRepo.findByEmail(u[1])).thenReturn(Optional.of(user));
        }
    }

    // ───────────────────────── client plumbing ─────────────────────────

    private static class Client {
        final CompletableFuture<String> failure = new CompletableFuture<>();
        StompSession session;
    }

    private Client connect(String email) throws Exception {
        WebSocketStompClient stomp = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stomp.setMessageConverter(new MappingJackson2MessageConverter());
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.initialize();
        stomp.setTaskScheduler(scheduler); // needed to track receipts

        Client c = new Client();
        StompHeaders connectHeaders = new StompHeaders();
        if (email != null) connectHeaders.add("Authorization", "Bearer " + jwt.generateToken(email));

        c.session = stomp.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connectHeaders, new StompSessionHandlerAdapter() {
                    @Override public void handleFrame(StompHeaders headers, Object payload) {
                        c.failure.complete("ERROR frame: " + headers.getFirst("message"));
                    }
                    @Override public void handleException(StompSession s, StompCommand cmd, StompHeaders h, byte[] p, Throwable ex) {
                        c.failure.complete("exception: " + ex);
                    }
                    @Override public void handleTransportError(StompSession s, Throwable ex) {
                        c.failure.complete("transport: " + ex);
                    }
                }).get(10, TimeUnit.SECONDS);
        return c;
    }

    private LinkedBlockingQueue<Map<?, ?>> subscribe(Client c, String destination) throws Exception {
        LinkedBlockingQueue<Map<?, ?>> inbox = new LinkedBlockingQueue<>();
        c.session.subscribe(destination, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { inbox.add((Map<?, ?>) payload); }
        });
        // The simple broker sends no receipts. Give the subscription a moment to register; if the server rejected it
        // the failure future completes during this window and the test fails loudly.
        try {
            String rejected = c.failure.get(700, TimeUnit.MILLISECONDS);
            throw new AssertionError("subscription to " + destination + " was rejected: " + rejected);
        } catch (java.util.concurrent.TimeoutException expected) {
            // accepted
        }
        return inbox;
    }

    // ───────────────────────── tests ─────────────────────────

    @Test
    void participantsExchangeChatAndTheServerStampsTheRealSender() throws Exception {
        Client alice = connect("alice@x.com");
        Client bob = connect("bob@x.com");
        LinkedBlockingQueue<Map<?, ?>> bobInbox = subscribe(bob, "/topic/match/ABC123/chat");
        subscribe(alice, "/topic/match/ABC123/chat");

        alice.session.send("/app/match/chat", Map.of("inviteCode", "ABC123", "sender", "bob", "text", "hello"));

        Map<?, ?> received = bobInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(received, "opponent did not receive the chat message");
        assertEquals("alice", received.get("sender"), "sender must come from the authenticated session, not the payload");
        assertEquals("hello", received.get("text"));
    }

    @Test
    void lowercaseInviteCodeInAMessageStillReachesTheMatchTopic() throws Exception {
        Client alice = connect("alice@x.com");
        LinkedBlockingQueue<Map<?, ?>> inbox = subscribe(alice, "/topic/match/ABC123/emotes");

        alice.session.send("/app/match/emote", Map.of("inviteCode", "abc123", "emote", "fire"));

        Map<?, ?> received = inbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(received);
        assertEquals("alice", received.get("sender"));
    }

    @Test
    void aNonParticipantCannotSubscribeToThePrivateMatchTopics() throws Exception {
        Client eve = connect("eve@x.com");
        eve.session.subscribe("/topic/match/ABC123", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return Map.class; }
            @Override public void handleFrame(StompHeaders h, Object p) {}
        });
        String failure = eve.failure.get(5, TimeUnit.SECONDS);
        assertTrue(failure.contains("Not allowed to subscribe"), failure);
    }

    @Test
    void anAnonymousClientCanNeitherSubscribeToAMatchNorSendMessages() throws Exception {
        Client anon = connect(null);
        anon.session.subscribe("/topic/match/ABC123/chat", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return Map.class; }
            @Override public void handleFrame(StompHeaders h, Object p) {}
        });
        assertNotNull(anon.failure.get(5, TimeUnit.SECONDS));

        Client anon2 = connect(null);
        anon2.session.send("/app/match/chat", Map.of("inviteCode", "ABC123", "text", "spam"));
        String failure = anon2.failure.get(5, TimeUnit.SECONDS);
        assertTrue(failure.contains("Authentication required"), failure);
    }

    @Test
    void aNonParticipantsMessagesAreDropped() throws Exception {
        Client alice = connect("alice@x.com");
        Client eve = connect("eve@x.com"); // authenticated, but not in this match
        LinkedBlockingQueue<Map<?, ?>> aliceInbox = subscribe(alice, "/topic/match/ABC123/chat");

        eve.session.send("/app/match/chat", Map.of("inviteCode", "ABC123", "sender", "alice", "text", "forged"));

        assertNull(aliceInbox.poll(2, TimeUnit.SECONDS), "forged message from a non-participant must never be delivered");
    }

    @Test
    void skipVoteIsAttributedToTheAuthenticatedPlayerNotThePayload() throws Exception {
        when(mongoTemplate.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class), any(Update.class), eq(MatchPrimary.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        Client alice = connect("alice@x.com");

        // alice (player 1) tries to vote as bob (player 2)
        alice.session.send("/app/match/skip", Map.of("inviteCode", "ABC123", "sender", "bob"));

        ArgumentCaptor<Update> upd = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, timeout(5000).atLeastOnce())
                .updateFirst(any(org.springframework.data.mongodb.core.query.Query.class), upd.capture(), eq(MatchPrimary.class));
        String json = upd.getAllValues().get(0).getUpdateObject().toJson();
        assertTrue(json.contains("skipVoteUser1") && !json.contains("skipVoteUser2"), json);
    }

    @Test
    void skipVoteFromANonParticipantNeverTouchesTheDatabase() throws Exception {
        Client eve = connect("eve@x.com");
        eve.session.send("/app/match/skip", Map.of("inviteCode", "ABC123", "sender", "alice"));

        verify(mongoTemplate, after(2000).never())
                .updateFirst(any(org.springframework.data.mongodb.core.query.Query.class), any(Update.class), eq(MatchPrimary.class));
    }

    @Test
    void aReconnectingPlayerCanResubscribeAndIsServedTheSameState() throws Exception {
        Client first = connect("alice@x.com");
        subscribe(first, "/topic/match/ABC123");
        first.session.disconnect();

        Client again = connect("alice@x.com"); // page refresh = brand new socket, same account
        LinkedBlockingQueue<Map<?, ?>> inbox = subscribe(again, "/topic/match/ABC123");
        assertTrue(inbox.isEmpty()); // subscribing is accepted; current state is recovered through GET /api/match/status
    }
}
