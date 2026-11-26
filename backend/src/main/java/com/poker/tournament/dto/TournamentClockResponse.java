package com.poker.tournament.dto;

import com.poker.tournament.entity.BlindLevel;
import com.poker.tournament.entity.TournamentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TournamentClockResponse(UUID tournamentId, TournamentStatus tournamentStatus, String status,
                                      Instant startedAt, Instant serverTime, Instant levelStartedAt, Instant levelEndsAt,
                                      Long remainingMillis, long elapsedActiveMillis, long accumulatedPausedMillis,
                                      Instant pausedAt, Instant pauseRequestedAt, String pauseReason,
                                      BlindLevel scheduled, BlindLevel applied, BlindLevel next, List<BlindLevel> levels,
                                      boolean pending, boolean finalLevel, boolean breaksSupported, long version) { }
