package com.poker.dto;

import java.util.UUID;

public record PayoutResponse(UUID playerId, int amount) {
}
