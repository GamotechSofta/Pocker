package com.poker.websocket;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameSessionEventListenerTest {

    @Test
    void parsesUserQueueDestination() {
        UUID tableId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertEquals(tableId, GameSessionEventListener.tableIdFrom("/user/queue/game/" + tableId));
        assertEquals(tableId, GameSessionEventListener.tableIdFrom("/queue/game/" + tableId));
        assertNull(GameSessionEventListener.tableIdFrom("/user/queue/errors"));
        assertNull(GameSessionEventListener.tableIdFrom(null));
    }
}
