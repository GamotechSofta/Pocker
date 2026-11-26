package com.poker.tournament.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TournamentTopology(long version, UUID finalTableId, Plan pending, List<Plan> history) {
    public enum Stage { WAITING_FOR_HANDS, DESTINATION_RESERVED, COMPLETED, RECOVERY_REQUIRED }

    public record Plan(UUID id, Stage stage, Instant requestedAt, Instant reservedAt, Instant completedAt,
                       UUID finalTableId, List<UUID> closingTables, List<Transfer> transfers) {
        public Plan withStage(Stage next, Instant now) {
            return new Plan(id, next, requestedAt, next == Stage.DESTINATION_RESERVED ? now : reservedAt,
                    next == Stage.COMPLETED ? now : completedAt, finalTableId, closingTables, transfers);
        }
    }

    public record Transfer(UUID entrantId, UUID userId, UUID sourceTableId, int sourceTableNumber, int sourceSeat,
                           UUID destinationTableId, int destinationTableNumber, int destinationSeat,
                           long settledStack, long assignmentVersion, Instant sourceAssignedAt) { }
}
