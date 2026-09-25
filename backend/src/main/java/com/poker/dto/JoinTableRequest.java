package com.poker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record JoinTableRequest(
        @Min(0)
        @Max(9)
        Integer seat
) {
}
