package com.poker.tournament.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** UTC schedule and pause checkpoints shared by all tables and game servers. */
public record TournamentBlindClock(UUID scheduleId, List<BlindLevel> levels, Instant startedAt, int scheduledLevel,
                                   Instant levelStartedAt, Instant levelEndsAt, long pausedMillis, Instant pausedAt,
                                   String pauseReason, Instant pauseRequestedAt, long version, String event) {
    public BlindLevel current() { return levels.get(scheduledLevel - 1); }
    public BlindLevel next() { return scheduledLevel == levels.size() ? null : levels.get(scheduledLevel); }
}
