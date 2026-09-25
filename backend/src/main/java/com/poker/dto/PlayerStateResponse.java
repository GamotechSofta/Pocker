package com.poker.dto;

import java.util.List;
import java.util.UUID;

public record PlayerStateResponse(
        UUID playerId,
        String username,
        int seat,
        int chipStack,
        int betThisRound,
        String status,
        boolean folded,
        boolean allIn,
        boolean isTurn,
        boolean connected,
        List<String> holeCards
) {
}
