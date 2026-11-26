package com.poker.tournament.dto;

import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentTableMode;
import com.poker.tournament.entity.TournamentVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreateTournamentRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2_000) String description,
        @Size(max = 512) String banner,
        @NotNull TournamentStartMode startMode,
        @NotNull TournamentTableMode tableMode,
        @NotNull TournamentVisibility visibility,
        @Min(2) int maximumEntrants,
        @Min(2) int minimumEntrants,
        @Min(2) int seatsPerTable,
        @Min(1) long startingVirtualChips,
        @NotNull @FutureOrPresent Instant registrationOpeningAt,
        @NotNull Instant registrationClosingAt,
        Instant scheduledStartAt,
        @Size(max = 32) String privateInviteCode,
        @NotNull @Size(min = 1) List<@Valid BlindLevelRequest> blindLevels
) { }
