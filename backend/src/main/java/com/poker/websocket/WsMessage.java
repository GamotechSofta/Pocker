package com.poker.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poker.dto.TableStateResponse;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsMessage(
        String type,
        UUID tableId,
        TableStateResponse state,
        String code,
        String message
) {

    public static WsMessage of(WsEventType type, UUID tableId, TableStateResponse state) {
        return new WsMessage(type.name(), tableId, state, null, null);
    }

    public static WsMessage error(UUID tableId, String code, String message) {
        return new WsMessage(WsEventType.ERROR.name(), tableId, null, code, message);
    }
}
