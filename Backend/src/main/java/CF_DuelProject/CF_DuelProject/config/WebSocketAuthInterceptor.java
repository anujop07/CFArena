package CF_DuelProject.CF_DuelProject.config;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import org.springframework.context.annotation.Lazy;

import CF_DuelProject.CF_DuelProject.service.JwtService;
import CF_DuelProject.CF_DuelProject.service.MatchService;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates STOMP clients with the same JWT used for the REST API.
 *
 * - CONNECT may carry an {@code Authorization: Bearer <jwt>} header; a valid token binds the session to that user.
 *   Connections without a (valid) token are still allowed so that read-only subscriptions keep working.
 * - SEND to application destinations ({@code /app/**}: chat, emotes, skip votes) requires an authenticated session,
 *   so the sender identity can never be supplied by the client.
 */
@Component
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String MATCH_TOPIC_PREFIX = "/topic/match/";

    private static final String USER_PRINCIPAL_KEY = "USER_PRINCIPAL";

    private final JwtService jwtService;
    private final MatchService matchService;

    // @Lazy: MatchService itself uses the messaging template, so resolve it on first use to avoid a startup cycle
    public WebSocketAuthInterceptor(JwtService jwtService, @Lazy MatchService matchService) {
        this.jwtService = jwtService;
        this.matchService = matchService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand()) || StompCommand.STOMP.equals(accessor.getCommand())) {
            Principal auth = extractPrincipalFromHeaders(accessor);
            if (auth != null) {
                accessor.setUser(auth);
                if (accessor.getSessionAttributes() != null) {
                    accessor.getSessionAttributes().put(USER_PRINCIPAL_KEY, auth);
                }
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            ensureUserPopulated(accessor);
            String destination = accessor.getDestination();
            // Match topics carry players' private state (problems, scores, chat): participants only.
            if (destination != null && destination.startsWith(MATCH_TOPIC_PREFIX)) {
                String rest = destination.substring(MATCH_TOPIC_PREFIX.length());
                int slash = rest.indexOf('/');
                String identifier = slash >= 0 ? rest.substring(0, slash) : rest;
                if (accessor.getUser() == null || !matchService.canViewMatch(accessor.getUser().getName(), identifier)) {
                    throw new MessagingException("Not allowed to subscribe to this match");
                }
            }
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            ensureUserPopulated(accessor);
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/app/") && accessor.getUser() == null) {
                throw new MessagingException("Authentication required to send messages");
            }
        }
        return message;
    }

    private void ensureUserPopulated(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            if (accessor.getSessionAttributes() != null) {
                Object cached = accessor.getSessionAttributes().get(USER_PRINCIPAL_KEY);
                if (cached instanceof Principal p) {
                    accessor.setUser(p);
                    return;
                }
            }
            Principal auth = extractPrincipalFromHeaders(accessor);
            if (auth != null) {
                accessor.setUser(auth);
                if (accessor.getSessionAttributes() != null) {
                    accessor.getSessionAttributes().put(USER_PRINCIPAL_KEY, auth);
                }
            }
        }
    }

    private Principal extractPrincipalFromHeaders(StompHeaderAccessor accessor) {
        List<String> headers = accessor.getNativeHeader("Authorization");
        if (headers == null || headers.isEmpty()) {
            headers = accessor.getNativeHeader("authorization");
        }
        if (headers != null && !headers.isEmpty()) {
            String header = headers.get(0);
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                try {
                    if (jwtService.isTokenValid(token)) {
                        String email = jwtService.extractEmail(token);
                        if (email != null) {
                            return new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());
                        }
                    }
                } catch (Exception e) {
                    log.warn("WebSocket invalid token: {}", e.getMessage());
                }
            }
        }
        return null;
    }
}
