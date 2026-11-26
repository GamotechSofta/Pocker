package com.poker.tournament.dto;

import com.poker.tournament.entity.TournamentEntrantStatus;
import com.poker.tournament.entity.TournamentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TournamentLeaderboard(UUID tournamentId, String tournamentName, TournamentStatus tournamentStatus,
                                    long version, Instant serverTime, UUID lastSettledHandId, Instant lastSettledAt,
                                    Statistics statistics, List<Row> rows, Row viewer, UUID candidateEntrantId,
                                    boolean reviewRequired, List<Row> recentEliminations, int page, int size, int totalPages,
                                    int activeTables, Integer finalTableNumber, boolean finalTablePreparing) {
    public record Row(UUID entrantId, String displayName, TournamentEntrantStatus status, Integer activeStanding,
                      Integer finishPosition, long settledStack, Integer tableNumber, Integer seat, Instant eliminatedAt,
                      Integer handsPlayed, boolean you) { }

    public record Statistics(int totalEntrants, int activePlayers, int eliminatedPlayers, long initialSupply,
                             long settledActiveStacks, Long largestStack, Long smallestActiveStack, Double averageActiveStack,
                             int completedHands, Integer scheduledBlindLevel, Long elapsedActiveMillis) { }
}
