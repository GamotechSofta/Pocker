package com.poker.dto;

import java.util.UUID;

public record SeatedPlayerResponse(
        UUID playerId,
        String username,
        int seat,
        int chipStack
) {
}
