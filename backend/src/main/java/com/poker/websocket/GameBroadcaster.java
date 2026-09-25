package com.poker.websocket;

import com.poker.dto.TableStateResponse;
import com.poker.service.TableService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class GameBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final TableService tableService;
    private final GameSessionRegistry sessionRegistry;

    public GameBroadcaster(
            SimpMessagingTemplate messagingTemplate,
            TableService tableService,
            GameSessionRegistry sessionRegistry
    ) {
        this.messagingTemplate = messagingTemplate;
        this.tableService = tableService;
        this.sessionRegistry = sessionRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTableUpdated(TableUpdatedEvent event) {
        broadcast(event.tableId(), event.type());
    }

    public void broadcast(UUID tableId, WsEventType type) {
        Set<UUID> recipients = new HashSet<>(sessionRegistry.subscribers(tableId));
        recipients.addAll(tableService.seatedUserIds(tableId));
        for (UUID userId : recipients) {
            sendToUser(tableId, userId, type);
        }
    }

    public void sendToUser(UUID tableId, UUID userId, WsEventType type) {
        TableStateResponse state = tableService.getState(tableId, userId);
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                destination(tableId),
                WsMessage.of(type, tableId, state)
        );
    }

    public void sendError(UUID tableId, UUID userId, String code, String message) {
        WsMessage error = WsMessage.error(tableId, code, message);
        if (tableId != null) {
            messagingTemplate.convertAndSendToUser(userId.toString(), destination(tableId), error);
        }
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/errors", error);
    }

    static String destination(UUID tableId) {
        return "/queue/game/" + tableId;
    }
}
