package com.poker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record JoinTableRequest(
        @Min(0)
        @Max(9)
        Integer seat,
        /** When set, buy-in was already debited during matchmaking (escrow id). */
        String prepaidEscrowId
) {
    public JoinTableRequest(Integer seat) {
        this(seat, null);
    }
}
