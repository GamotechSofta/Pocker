package com.poker.websocket;

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
import java.util.Map;

@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public JwtStompChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
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
        }
        return message;
    }

    private Jwt decode(String token) {
        if (token == null || token.isBlank()) {
            throw new MessagingException("Authentication is required.");
        }
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException ex) {
            throw new MessagingException("Authentication is required.");
        }
    }

    private static String resolveToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String queryToken = accessor.getFirstNativeHeader("access_token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken.trim();
        }
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            return null;
        }
        Object stored = attributes.get(JwtHandshakeInterceptor.TOKEN_ATTRIBUTE);
        return stored instanceof String value ? value : null;
    }
}
