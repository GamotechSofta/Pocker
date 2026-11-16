package com.poker.matchmaking;

import com.poker.dto.MatchmakingRequest;
import com.poker.dto.TableDetailResponse;
import com.poker.service.TableService;
import com.poker.wallet.StakeEscrowService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchmakingServiceTest {

    @Test
    void resumesAnExistingSeatedTable() {
        TableService tables = mock(TableService.class);
        StakeEscrowService escrows = mock(StakeEscrowService.class);
        UUID playerId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        when(tables.seatedTableId(playerId)).thenReturn(Optional.of(tableId));
        MatchmakingService service = new MatchmakingService(tables, escrows, 0, 30_000);

        var ticket = service.enqueue(playerId, request());

        assertEquals("MATCHED", ticket.status());
        assertEquals(tableId, ticket.matchedTableId());
    }

    @Test
    void createsABotFallbackTableAfterTimeout() {
        TableService tables = mock(TableService.class);
        StakeEscrowService escrows = mock(StakeEscrowService.class);
        UUID playerId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        when(tables.seatedTableId(playerId)).thenReturn(Optional.empty());
        when(tables.listTables()).thenReturn(List.of());
        when(escrows.reserveEntry(anyString(), anyLong(), anyString())).thenReturn("STAKE_test");
        when(tables.createTable(eq(playerId), any())).thenReturn(new TableDetailResponse(
                tableId,
                "Texas Holdem Quick Match",
                "TEXAS_HOLDEM",
                10,
                20,
                1000,
                6,
                "WAITING",
                playerId,
                Instant.now(),
                List.of()
        ));
        MatchmakingService service = new MatchmakingService(tables, escrows, 0, 30_000);

        var queued = service.enqueue(playerId, request());
        var matched = service.get(queued.ticketId(), playerId);

        assertEquals("MATCHED", matched.status());
        assertNotNull(matched.matchedTableId());
        verify(tables).joinTable(eq(tableId), eq(playerId), any());
        verify(escrows).reserveEntry(eq(playerId.toString()), eq(1000L), anyString());
    }

    private static MatchmakingRequest request() {
        return new MatchmakingRequest("TEXAS_HOLDEM", 10, 20, 6, 1000, true);
    }
}
