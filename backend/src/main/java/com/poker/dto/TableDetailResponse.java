package com.poker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TableDetailResponse(
        UUID id,
        String name,
        String variant,
        int smallBlind,
        int bigBlind,
        int startingChips,
        int maxPlayers,
        String status,
        UUID createdBy,
        Instant createdAt,
        List<SeatedPlayerResponse> players
) {
}
