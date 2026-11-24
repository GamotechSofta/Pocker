package com.poker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinByCodeRequest(
        @NotBlank
        @Size(min = 4, max = 12)
        String code
) {
}
