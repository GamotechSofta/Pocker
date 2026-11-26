package com.poker.tournament.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TournamentStandingCheckpoint(long version, UUID handId, int completedHands, Instant settledAt,
                                           List<UUID> eliminatedEntrantIds, Candidate candidate, boolean reviewRequired) {
    public record Candidate(UUID entrantId, UUID userId, long settledStack) { }
}
