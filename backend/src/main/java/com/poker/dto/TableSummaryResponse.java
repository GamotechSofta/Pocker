package com.poker.dto;

import java.util.UUID;

public record TableSummaryResponse(
        UUID id,
        String name,
        String variant,
        int smallBlind,
        int bigBlind,
        int maxPlayers,
        int playerCount,
        String status,
        boolean privateRoom,
        String roomCode
) {
}
