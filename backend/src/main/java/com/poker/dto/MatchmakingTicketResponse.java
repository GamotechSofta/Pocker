package com.poker.dto;

import java.time.Instant;
import java.util.UUID;

public record MatchmakingTicketResponse(
        UUID ticketId,
        String status,
        UUID matchedTableId,
        Instant createdAt
) {
}
