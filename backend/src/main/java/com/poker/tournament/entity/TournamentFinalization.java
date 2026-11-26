package com.poker.tournament.entity;

import java.time.Instant;
import java.util.UUID;

public record TournamentFinalization(UUID operationId, Stage stage, Instant claimedAt, Instant readyAt, String reason) {
    public enum Stage { CLAIMED, COMPLETED, RECOVERY_REQUIRED }

    public TournamentFinalization withStage(Stage next, String blocker) {
        return new TournamentFinalization(operationId, next, claimedAt, readyAt, blocker);
    }
}
