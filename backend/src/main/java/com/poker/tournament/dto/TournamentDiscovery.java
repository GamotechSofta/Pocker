package com.poker.tournament.dto;

import com.poker.tournament.entity.BlindLevel;
import com.poker.tournament.entity.TournamentEntrantStatus;
import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentTableMode;
import com.poker.tournament.entity.TournamentVisibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Player-facing projections. Administrative fields and player identifiers stay server-side. */
public final class TournamentDiscovery {
    private TournamentDiscovery() { }

    public record Card(UUID id, String name, String banner, TournamentStatus status,
                       TournamentStartMode startMode, TournamentTableMode tableMode, TournamentVisibility visibility,
                       int minimumEntrants, int maximumEntrants, long startingVirtualChips, long registeredCount,
                       Instant registrationOpeningAt, Instant registrationClosingAt, Instant scheduledStartAt,
                       TournamentEntrantStatus participationStatus, Integer finishPosition, boolean awaitingFinalization) { }

    public record Catalogue(List<Card> tournaments, int page, int size, long totalElements,
                            int totalPages, Instant serverTime) { }

    public record RegisteredPlayer(String displayName, TournamentEntrantStatus status, boolean you) { }

    public record Result(int position, String displayName, long finalVirtualChips, boolean you) { }

    public record Details(Card tournament, String description, int seatsPerTable, Instant completedAt,
                          List<BlindLevel> blindLevels, List<RegisteredPlayer> registeredPlayers,
                          List<Result> results, Instant serverTime) { }
}
