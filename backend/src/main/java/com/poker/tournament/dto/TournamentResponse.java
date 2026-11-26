package com.poker.tournament.dto;

import com.poker.game.model.GameMode;
import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentTableMode;
import com.poker.tournament.entity.TournamentVisibility;

import java.time.Instant;
import java.util.UUID;

public record TournamentResponse(
        UUID id, String name, String description, String banner,
        TournamentStartMode startMode, TournamentTableMode tableMode, TournamentVisibility visibility,
        GameMode gameMode, int maximumEntrants, int minimumEntrants, int seatsPerTable,
        long startingVirtualChips, Instant registrationOpeningAt, Instant registrationClosingAt,
        Instant scheduledStartAt, String privateInviteCode, UUID creatorId, TournamentStatus status,
        Instant createdAt, Instant updatedAt, Instant startedAt, Instant completedAt
) { }
