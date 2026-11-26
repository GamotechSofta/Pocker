package com.poker.websocket;

import com.poker.service.TableService;
import com.poker.tournament.service.TournamentGameplayService;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final TableService tables;
    private final TournamentGameplayService tournament;
    private final GameSocketConnections connections;

    public JwtStompChannelInterceptor(JwtDecoder jwtDecoder, TableService tables, TournamentGameplayService tournament,
                                      GameSocketConnections connections) {
        this.jwtDecoder = jwtDecoder;
        this.tables = tables;
        this.tournament = tournament;
        this.connections = connections;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Jwt jwt = decode(resolveToken(accessor));
            accessor.setUser(new JwtAuthenticationToken(jwt, List.of()));
            connections.authenticated(accessor.getSessionId(), jwt.getExpiresAt());
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            requireCurrentSession(accessor);
            String destination = accessor.getDestination();
            var tableId = GameSessionEventListener.tableIdFrom(destination);
            boolean gameQueue = tableId != null && destination.startsWith("/user/");
            if (accessor.getUser() == null || (!"/user/queue/errors".equals(destination) && !gameQueue)) {
                throw new MessagingException("The game subscription is unavailable.");
            }
            if (tableId != null && tables.isTournamentTable(tableId)
                    && !tournament.canSubscribe(tableId, UUID.fromString(accessor.getUser().getName()))) {
                throw new MessagingException("The game subscription is unavailable.");
            }
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            requireCurrentSession(accessor);
            if (accessor.getDestination() == null || !accessor.getDestination().startsWith("/app/game/")) {
                throw new MessagingException("The game action destination is unavailable.");
            }
        }
        return message;
    }

    private Jwt decode(String token) {
        if (token == null || token.isBlank()) {
            throw new MessagingException("Authentication is required.");
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            if (expired(jwt)) throw new MessagingException("Authentication is required.");
            return jwt;
        } catch (JwtException ex) {
            throw new MessagingException("Authentication is required.");
        }
    }

    private static String resolveToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return null;
    }

    private static void requireCurrentSession(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof JwtAuthenticationToken authentication) || expired(authentication.getToken())) {
            throw new MessagingException("Authentication is required.");
        }
    }

    private static boolean expired(Jwt jwt) {
        return jwt.getExpiresAt() == null || !Instant.now().isBefore(jwt.getExpiresAt());
    }
}
