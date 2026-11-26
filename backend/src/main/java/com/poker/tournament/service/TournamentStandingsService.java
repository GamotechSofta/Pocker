package com.poker.tournament.service;

import com.poker.entity.PokerTable;
import com.poker.entity.PokerTablePlayer;
import com.poker.entity.User;
import com.poker.exception.ApiException;
import com.poker.tournament.dto.TournamentLeaderboard;
import com.poker.tournament.dto.TournamentLeaderboard.Row;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentEntrant;
import com.poker.tournament.entity.TournamentEntrantStatus;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentVisibility;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TournamentStandingsService {
    private static final TournamentEntrantStatus ELIMINATED = TournamentEntrantStatus.ELIMINATED;
    private static final Comparator<TournamentEntrant> ACTIVE_ORDER = Comparator.comparingLong(TournamentEntrant::getCurrentVirtualChips)
            .reversed().thenComparing(entry -> entry.getSeat() == null ? Integer.MAX_VALUE : entry.getSeat())
            .thenComparing(TournamentEntrant::getId);

    static Comparator<TournamentEntrant> activeOrder(Map<UUID, Integer> tables) {
        return Comparator.comparingLong(TournamentEntrant::getCurrentVirtualChips).reversed()
                .thenComparingInt(entry -> tables.getOrDefault(entry.getTableId(), Integer.MAX_VALUE)).thenComparing(ACTIVE_ORDER);
    }
    private final TournamentTransactions transactions;
    private final TournamentBlindClockService clocks;
    private final MongoTemplate mongo;
    private final Clock clock;

    public TournamentStandingsService(TournamentTransactions transactions, TournamentBlindClockService clocks, MongoTemplate mongo, Clock clock) {
        this.transactions = transactions;
        this.clocks = clocks;
        this.mongo = mongo;
        this.clock = clock;
    }

    public TournamentLeaderboard player(UUID id, UUID viewer, int page, int size) {
        if (viewer == null) throw unavailable();
        return load(id, viewer, page, size);
    }

    public TournamentLeaderboard admin(UUID id, int page, int size) { return load(id, null, page, size); }

    public long version(UUID id) {
        var query = Query.query(Criteria.where("_id").is(id));
        query.fields().include("standings.version");
        var event = mongo.findOne(query, Document.class, "tournaments");
        var checkpoint = event == null ? null : event.get("standings", Document.class);
        return checkpoint == null ? 0 : checkpoint.get("version", Number.class).longValue();
    }

    private TournamentLeaderboard load(UUID id, UUID viewer, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Use a nonnegative page and a size between 1 and 50.");
        return transactions.run(tx -> snapshot(tx, id, viewer, page, size));
    }

    private TournamentLeaderboard snapshot(MongoTemplate tx, UUID id, UUID viewer, int page, int size) {
        var now = clock.instant();
        var event = tx.findById(id, Tournament.class);
        if (event == null || (event.getStatus() != TournamentStatus.RUNNING && event.getStatus() != TournamentStatus.PAUSED)
                || event.getInitialization() == null || event.getInitialization().tableId() == null) throw unavailable();
        var query = Query.query(Criteria.where("tournamentId").is(id).and("status").ne(TournamentEntrantStatus.CANCELLED));
        if (viewer != null && event.getVisibility() != TournamentVisibility.PUBLIC && !tx.exists(Query.query(Criteria.where("tournamentId")
                .is(id).and("status").ne(TournamentEntrantStatus.CANCELLED).and("userId").is(viewer)), TournamentEntrant.class)) throw unavailable();
        var tables = TournamentTableBalanceService.tables(tx, event);
        var open = tables.stream().filter(row -> row.getTournamentClosedAt() == null).toList();
        var table = open.isEmpty() ? null : open.getFirst();
        if (table == null || !table.isTournament() || !id.equals(table.getTournamentId()) || table.getGameplay() == null) throw unavailable();

        var entries = tx.find(query, TournamentEntrant.class);
        var seats = open.stream().flatMap(row -> row.getPlayers().stream()).collect(Collectors.toMap(PokerTablePlayer::getUserId, Function.identity()));
        var tableNumbers = tables.stream().collect(Collectors.toMap(PokerTable::getId, PokerTable::getTableNumber));
        var historicalUsers = entries.stream().map(TournamentEntrant::getUserId).filter(user -> !seats.containsKey(user)).toList();
        var names = historicalUsers.isEmpty() ? Map.<UUID, String>of() : tx.find(Query.query(Criteria.where("id").in(historicalUsers)),
                User.class).stream().collect(Collectors.toMap(User::getId, User::getUsername));
        var active = entries.stream().filter(entry -> entry.getStatus() != ELIMINATED && entry.getCurrentVirtualChips() > 0)
                .sorted(activeOrder(tableNumbers)).toList();
        var eliminated = entries.stream().filter(entry -> entry.getStatus() == ELIMINATED)
                .sorted(Comparator.comparing(TournamentEntrant::getFinishPosition, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TournamentEntrant::getId)).toList();
        var unranked = entries.stream().filter(entry -> entry.getStatus() != ELIMINATED && entry.getCurrentVirtualChips() == 0)
                .sorted(ACTIVE_ORDER).toList();
        var all = new ArrayList<Row>(entries.size());
        for (int index = 0; index < active.size(); index++) all.add(row(active.get(index), index + 1, tableNumbers, seats, names, viewer));
        eliminated.forEach(entry -> all.add(row(entry, null, tableNumbers, seats, names, viewer)));
        unranked.forEach(entry -> all.add(row(entry, null, tableNumbers, seats, names, viewer)));
        var checkpoint = event.getStandings();
        var lastHand = table.getGameplay();
        int completed = checkpoint != null ? checkpoint.completedHands() : Math.max(0, lastHand.number() - (lastHand.settledAt() == null ? 1 : 0));
        var values = clocks.snapshot(id, event.getStatus(), clocks.current(tx, event, table, now), table, now);
        long stacks = active.stream().mapToLong(TournamentEntrant::getCurrentVirtualChips).sum();
        var stats = new TournamentLeaderboard.Statistics(entries.size(), active.size(), eliminated.size(),
                entries.stream().mapToLong(TournamentEntrant::getInitialVirtualChips).sum(), stacks,
                active.isEmpty() ? null : active.getFirst().getCurrentVirtualChips(), active.isEmpty() ? null : active.getLast().getCurrentVirtualChips(),
                active.isEmpty() ? null : (double) stacks / active.size(), completed, values.scheduled().levelNumber(), values.elapsedActiveMillis());
        var recent = checkpoint == null ? List.<Row>of() : all.stream().filter(row -> checkpoint.eliminatedEntrantIds().contains(row.entrantId())).toList();
        var own = all.stream().filter(Row::you).findFirst().orElse(null);
        int from = (int) Math.min((long) page * size, all.size());
        var layout = event.getTopology();
        return new TournamentLeaderboard(id, event.getName(), event.getStatus(), checkpoint == null ? 0 : checkpoint.version(), now,
                checkpoint != null ? checkpoint.handId() : lastHand.settledAt() == null ? null : lastHand.id(),
                checkpoint != null ? checkpoint.settledAt() : lastHand.settledAt(), stats, List.copyOf(all.subList(from, Math.min(from + size, all.size()))), own,
                checkpoint == null || checkpoint.candidate() == null ? null : checkpoint.candidate().entrantId(),
                !unranked.isEmpty() || (checkpoint != null && checkpoint.reviewRequired()), recent, page, size, (all.size() + size - 1) / size,
                open.size(), layout == null || layout.finalTableId() == null ? null : tableNumbers.get(layout.finalTableId()),
                layout != null && layout.pending() != null && layout.pending().finalTableId() != null);
    }

    private static Row row(TournamentEntrant entry, Integer rank, Map<UUID, Integer> tables, Map<UUID, PokerTablePlayer> seats,
                           Map<UUID, String> names, UUID viewer) {
        var seat = seats.get(entry.getUserId());
        return new Row(entry.getId(), seat == null ? names.getOrDefault(entry.getUserId(), "Player") : seat.getUsername(),
                entry.getStatus(), rank, entry.getFinishPosition(),
                entry.getCurrentVirtualChips(), entry.getTableId() == null ? null : tables.get(entry.getTableId()), entry.getSeat(), entry.getEliminatedAt(),
                entry.getElimination() == null ? null : entry.getElimination().handNumber(), entry.getUserId().equals(viewer));
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, "TOURNAMENT_NOT_FOUND", "Tournament standings are unavailable.");
    }
}
