package com.poker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTableRequest(
        @NotBlank
        @Size(min = 1, max = 64)
        String name,

        @Size(max = 32)
        String variant,

        @Min(1)
        Integer smallBlind,

        @Min(1)
        Integer bigBlind,

        @Min(2)
        @Max(10)
        Integer maxPlayers,

        @Min(1)
        Integer startingChips,

        /** When true, generates a shareable room code and disables bot fill. */
        Boolean privateRoom
) {
}
