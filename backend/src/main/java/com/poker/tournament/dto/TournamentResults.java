package com.poker.tournament.dto;

import com.poker.tournament.entity.TournamentCompletion;
import com.poker.tournament.entity.TournamentEntrantStatus;
import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentTableMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TournamentResults(UUID tournamentId, String status, TournamentCompletion summary, List<Standing> podium,
                                 List<Standing> rows, Standing viewer, int page, int size, long total, long totalPages) {
    public record Standing(UUID id, UUID entrantId, String displayName, int position, long finalVirtualChips,
                           TournamentEntrantStatus participationStatus, Integer tableNumber, Integer handsPlayed,
                           Instant eliminatedAt, Instant completedAt, boolean you) { }

    public record History(List<HistoryItem> rows, Statistics statistics, int page, int size, long total, long totalPages) { }

    public record HistoryItem(UUID tournamentId, String tournamentName, String banner, TournamentStartMode startMode,
                              TournamentTableMode tableMode, int entrantCount, int position, Instant completedAt,
                              TournamentEntrantStatus participationStatus) { }

    public record Statistics(long completed, long wins, long runnerUp, long topThree, Integer bestPosition, Double winRatePercent) { }
}
