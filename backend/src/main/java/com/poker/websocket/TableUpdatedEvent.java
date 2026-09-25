package com.poker.websocket;

import java.util.UUID;

public record TableUpdatedEvent(UUID tableId, WsEventType type) {
}
