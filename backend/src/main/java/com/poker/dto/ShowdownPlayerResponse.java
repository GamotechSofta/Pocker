package com.poker.dto;

import java.util.List;
import java.util.UUID;

public record ShowdownPlayerResponse(
        UUID playerId,
        List<String> holeCards,
        String handRank,
        String description
) {
}
