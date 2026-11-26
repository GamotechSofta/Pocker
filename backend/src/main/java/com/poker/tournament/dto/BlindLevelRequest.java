package com.poker.tournament.dto;

import jakarta.validation.constraints.Min;

public record BlindLevelRequest(
        @Min(1) int levelNumber,
        @Min(1) int smallBlind,
        @Min(1) int bigBlind,
        @Min(0) int ante,
        @Min(1) int durationSeconds,
        boolean breakAfter
) { }
