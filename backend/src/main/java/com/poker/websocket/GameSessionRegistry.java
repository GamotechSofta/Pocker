package com.poker.websocket;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameSessionRegistry {

    private final Map<String, SessionBinding> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> subscribersByTable = new ConcurrentHashMap<>();
    private final Map<String, Integer> seatsByUserTable = new ConcurrentHashMap<>();

    public void subscribe(String sessionId, UUID userId, UUID tableId) {
        sessions.put(sessionId, new SessionBinding(userId, tableId));
        subscribersByTable.computeIfAbsent(tableId, id -> ConcurrentHashMap.newKeySet()).add(userId);
        seatsByUserTable.merge(key(userId, tableId), 1, Integer::sum);
    }

    public SessionBinding removeSession(String sessionId) {
        SessionBinding binding = sessions.remove(sessionId);
        if (binding == null) {
            return null;
        }
        String key = key(binding.userId(), binding.tableId());
        seatsByUserTable.compute(key, (ignored, count) -> {
            if (count == null || count <= 1) {
                Set<UUID> subscribers = subscribersByTable.get(binding.tableId());
                if (subscribers != null) {
                    subscribers.remove(binding.userId());
                }
                return null;
            }
            return count - 1;
        });
        return binding;
    }

    public boolean hasRemainingSessions(UUID userId, UUID tableId) {
        return seatsByUserTable.getOrDefault(key(userId, tableId), 0) > 0;
    }

    public Set<UUID> subscribers(UUID tableId) {
        return Set.copyOf(subscribersByTable.getOrDefault(tableId, Set.of()));
    }

    private static String key(UUID userId, UUID tableId) {
        return userId + ":" + tableId;
    }

    public record SessionBinding(UUID userId, UUID tableId) {
    }
}
