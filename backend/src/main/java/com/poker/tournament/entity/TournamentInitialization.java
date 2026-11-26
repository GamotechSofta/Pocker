package com.poker.tournament.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persisted preparation state. READY does not mean a hand has started. */
public record TournamentInitialization(UUID operationId, Phase phase, Instant claimedAt, UUID tableId,
                                       Instant preparedAt, String reason, Instant retryAt, List<UUID> tableIds) {
    public TournamentInitialization(UUID operationId, Phase phase, Instant claimedAt, UUID tableId,
                                    Instant preparedAt, String reason, Instant retryAt) {
        this(operationId, phase, claimedAt, tableId, preparedAt, reason, retryAt, null);
    }

    public List<UUID> preparedTableIds() {
        return tableIds != null ? tableIds : tableId == null ? List.of() : List.of(tableId);
    }

    public enum Phase { CLAIMED, READY, BLOCKED, RETRYING, CANCELLED }
}
