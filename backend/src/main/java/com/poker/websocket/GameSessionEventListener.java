package com.poker.websocket;

import com.poker.service.TableService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GameSessionEventListener {

    private static final Pattern GAME_QUEUE = Pattern.compile("/queue/game/([0-9a-fA-F-]{36})");

    private final GameSessionRegistry sessionRegistry;
    private final TableService tableService;
    private final GameBroadcaster broadcaster;
    private final ApplicationEventPublisher events;

    public GameSessionEventListener(
            GameSessionRegistry sessionRegistry,
            TableService tableService,
            GameBroadcaster broadcaster,
            ApplicationEventPublisher events
    ) {
        this.sessionRegistry = sessionRegistry;
        this.tableService = tableService;
        this.broadcaster = broadcaster;
        this.events = events;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        UUID tableId = tableIdFrom(accessor.getDestination());
        if (principal == null || tableId == null || accessor.getSessionId() == null) {
            return;
        }
        UUID userId = UUID.fromString(principal.getName());
        sessionRegistry.subscribe(accessor.getSessionId(), userId, tableId);
        boolean reconnected = tableService.markConnected(tableId, userId, true);
        broadcaster.sendToUser(tableId, userId, WsEventType.GAME_STATE);
        if (reconnected) {
            events.publishEvent(new TableUpdatedEvent(tableId, WsEventType.PLAYER_RECONNECTED));
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        GameSessionRegistry.SessionBinding binding = sessionRegistry.removeSession(event.getSessionId());
        if (binding == null) {
            return;
        }
        if (sessionRegistry.hasRemainingSessions(binding.userId(), binding.tableId())) {
            return;
        }
        tableService.markConnected(binding.tableId(), binding.userId(), false);
        events.publishEvent(new TableUpdatedEvent(binding.tableId(), WsEventType.PLAYER_DISCONNECTED));
    }

    static UUID tableIdFrom(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = GAME_QUEUE.matcher(destination);
        if (!matcher.find()) {
            return null;
        }
        return UUID.fromString(matcher.group(1));
    }
}
