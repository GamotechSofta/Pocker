package com.poker.tournament.dto;

import com.poker.tournament.entity.BlindLevel;
import com.poker.tournament.entity.TournamentStatus;

import java.time.Instant;
import java.util.UUID;

public record TournamentAssignment(UUID tournamentId, String tournamentName, TournamentStatus tournamentStatus,
                                   Stage stage, UUID tableId, Integer tableNumber, Integer seat, Long startingVirtualChips,
                                   BlindLevel initialBlind, Instant assignedAt, Instant preparedAt, String reason,
                                   Instant serverTime, long assignmentVersion, long topologyVersion, Long settledVirtualChips,
                                   boolean multiTable, boolean finalTable, boolean finalTablePreparing, Transfer transfer,
                                   BlindLevel scheduledBlind, Integer activePlayers, Integer provisionalRank,
                                   String finalizationStage, Completion completion) {
    public record Completion(UUID operationId, int resultVersion, int position, Instant completedAt) { }
    public record Transfer(UUID id, String status, int sourceTableNumber, int destinationTableNumber, int destinationSeat,
                           long settledStack, boolean sourceSettled, boolean finalTable, Instant completedAt) { }

    public enum Stage { WAITING, PREPARING, READY, DELAYED, BLOCKED, CANCELLED, ELIMINATED, COMPLETED }
}
