package com.poker.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameSocketConnections {
    private record Connection(WebSocketSession socket, Instant expiresAt) { }
    private final ConcurrentHashMap<String, Connection> open = new ConcurrentHashMap<>();

    public void opened(WebSocketSession socket) {
        open.put(socket.getId(), new Connection(socket, Instant.now().plusSeconds(15)));
    }

    void authenticated(String sessionId, Instant expiresAt) {
        if (sessionId != null) open.computeIfPresent(sessionId, (id, connection) -> new Connection(connection.socket(), expiresAt));
    }

    public void closed(String id) { open.remove(id); }
    public int count() { return open.size(); }

    @Scheduled(fixedDelay = 1000)
    void closeExpired() {
        var now = Instant.now();
        open.forEach((id, connection) -> {
            if (!now.isBefore(connection.expiresAt()) && open.remove(id, connection)) {
                try { connection.socket().close(CloseStatus.POLICY_VIOLATION); }
                catch (IOException ignored) { /* An already closed transport cannot receive a final close frame. */ }
            }
        });
    }
}
