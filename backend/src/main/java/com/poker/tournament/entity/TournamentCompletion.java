package com.poker.tournament.entity;

import java.time.Instant;
import java.util.UUID;

public record TournamentCompletion(UUID operationId, int resultVersion, String tournamentName, String banner,
                                   TournamentStartMode startMode, TournamentTableMode tableMode, int entrantCount,
                                   long startingVirtualChips, long initialVirtualChipSupply, UUID championEntrantId,
                                   UUID championResultId, String championName, Instant startedAt, Instant completedAt,
                                   long activeDurationMillis, BlindLevel finalBlind, int completedHands,
                                   UUID finalTableId, int finalTableNumber, int finalActiveTableCount) { }
