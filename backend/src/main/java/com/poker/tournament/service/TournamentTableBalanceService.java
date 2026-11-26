package com.poker.tournament.service;

import com.poker.entity.PokerTable;
import com.poker.entity.PokerTablePlayer;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentEntrant;
import com.poker.tournament.entity.TournamentHand;
import com.poker.tournament.entity.TournamentTableAssignment;
import com.poker.tournament.entity.TournamentTopology;
import com.poker.tournament.entity.TournamentTopology.Plan;
import com.poker.tournament.entity.TournamentTopology.Transfer;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.poker.tournament.entity.TournamentTopology.Stage.*;

final class TournamentTableBalanceService {
    static final String WAITING = "WAITING_FOR_TOURNAMENT_PLAYERS";
    static final String RECOVERY = "TABLE_TRANSFER_RECOVERY_REQUIRED";

    static List<PokerTable> tables(MongoTemplate tx, Tournament event) {
        return tx.find(Query.query(Criteria.where("id").in(event.getInitialization().preparedTableIds())), PokerTable.class)
                .stream().sorted(Comparator.comparingInt(PokerTable::getTableNumber)).toList();
    }

    boolean hold(Tournament event) {
        return event.getTopology() != null && event.getTopology().pending() != null;
    }

    void request(MongoTemplate tx, Tournament event, Instant now) {
        var topology = event.getTopology();
        if (topology == null || topology.finalTableId() != null || topology.pending() != null) return;
        var open = openTables(tx, event);
        if (open.stream().anyMatch(table -> table.getGameplay() == null)) return;
        var plan = calculate(tx, event, open, UUID.randomUUID(), now);
        if (plan != null) persist(tx, event, new TournamentTopology(topology.version() + 1, null, plan, topology.history()));
    }

    void advance(MongoTemplate tx, Tournament event, Instant now) {
        var topology = event.getTopology();
        if (topology == null || topology.pending() == null) return;
        var plan = topology.pending();
        if (plan.stage() == RECOVERY_REQUIRED) return;
        var open = openTables(tx, event);
        if (open.stream().anyMatch(table -> table.getGameplay().phase() == TournamentHand.Phase.ACTIVE)) return;
        if (!validSeats(tx, event, open)) {
            recovery(tx, event, topology, plan, now);
            return;
        }
        var current = calculate(tx, event, open, plan.id(), plan.requestedAt());
        if (current == null) {
            persist(tx, event, new TournamentTopology(topology.version() + 1, null, null, topology.history()));
            return;
        }
        if (plan.stage() == WAITING_FOR_HANDS) {
            persist(tx, event, new TournamentTopology(topology.version() + 1, null,
                    current.withStage(DESTINATION_RESERVED, now), topology.history()));
            return;
        }
        if (!current.transfers().equals(plan.transfers()) || !current.closingTables().equals(plan.closingTables())
                || !java.util.Objects.equals(current.finalTableId(), plan.finalTableId())) {
            recovery(tx, event, topology, plan, now);
            return;
        }
        apply(tx, event, open, plan, now);
        var history = new ArrayList<>(topology.history());
        history.add(plan.withStage(COMPLETED, now));
        persist(tx, event, new TournamentTopology(topology.version() + 1, plan.finalTableId(), null, List.copyOf(history)));
    }

    private static void recovery(MongoTemplate tx, Tournament event, TournamentTopology topology, Plan plan, Instant now) {
        persist(tx, event, new TournamentTopology(topology.version() + 1, null, plan.withStage(RECOVERY_REQUIRED, now), topology.history()));
    }

    private static List<PokerTable> openTables(MongoTemplate tx, Tournament event) {
        return tables(tx, event).stream().filter(table -> table.getTournamentClosedAt() == null).toList();
    }

    private record TableSeats(PokerTable table, List<PokerTablePlayer> players) { }

    private Plan calculate(MongoTemplate tx, Tournament event, List<PokerTable> open, UUID operation, Instant now) {
        if (open.size() < 2) return null;
        var seating = open.stream().map(table -> new TableSeats(table, table.getPlayers().stream()
                        .filter(player -> player.getChipStack() > 0).toList()))
                .sorted(Comparator.<TableSeats>comparingInt(row -> row.players().size()).reversed()
                        .thenComparingInt(row -> row.table().getTableNumber())).toList();
        int active = seating.stream().mapToInt(row -> row.players().size()).sum();
        if (active < 2) return null;
        int capacity = event.getSeatsPerTable();
        int keep = Math.ceilDiv(active, capacity);
        if (keep == seating.size() && seating.getFirst().players().size() - seating.getLast().players().size() <= 1) return null;

        var rows = assignments(tx, event).stream().collect(Collectors.toMap(TournamentTableAssignment::getUserId, Function.identity()));
        var moves = new ArrayList<Transfer>();
        var destinations = new ArrayList<PokerTable>();
        Map<UUID, Set<Integer>> occupied = new HashMap<>();
        for (int index = 0; index < keep; index++) {
            var row = seating.get(index);
            int target = active / keep + (index < active % keep ? 1 : 0);
            occupied.put(row.table().getId(), row.players().stream().map(PokerTablePlayer::getSeat).collect(Collectors.toCollection(HashSet::new)));
            for (int missing = row.players().size(); missing < target; missing++) destinations.add(row.table());
        }
        for (int index = 0; index < seating.size(); index++) {
            var source = seating.get(index);
            int target = index < keep ? active / keep + (index < active % keep ? 1 : 0) : 0;
            int excess = Math.max(0, source.players().size() - target);
            var selected = source.players().stream().sorted(Comparator
                    .comparingLong((PokerTablePlayer p) -> rows.get(p.getUserId()).getAssignmentVersion())
                    .thenComparingInt(p -> relative(source.table(), p.getSeat()))).limit(excess).toList();
            for (var player : selected) {
                var destination = destinations.get(moves.size());
                var used = occupied.get(destination.getId());
                int position = relative(source.table(), player.getSeat());
                int seat = IntStream.range(0, capacity).filter(slot -> !used.contains(slot)).boxed()
                        .min(Comparator.comparingInt(slot -> Math.abs(relative(destination, slot) - position))).orElseThrow();
                used.add(seat);
                var row = rows.get(player.getUserId());
                moves.add(new Transfer(row.getEntrantId(), player.getUserId(), source.table().getId(), source.table().getTableNumber(),
                        player.getSeat(), destination.getId(), destination.getTableNumber(), seat, player.getChipStack(),
                        row.getAssignmentVersion() + 1, row.getAssignedAt()));
            }
        }
        var closing = seating.subList(keep, seating.size()).stream().map(row -> row.table().getId()).toList();
        return new Plan(operation, WAITING_FOR_HANDS, now, null, null, keep == 1 ? seating.getFirst().table().getId() : null,
                closing, List.copyOf(moves));
    }

    private static int relative(PokerTable table, int seat) {
        return Math.floorMod(seat - table.getGameplay().dealer() - 1, table.getMaxPlayers());
    }

    private static List<TournamentTableAssignment> assignments(MongoTemplate tx, Tournament event) {
        return tx.find(Query.query(Criteria.where("tournamentId").is(event.getId()).and("releasedAt").isNull()), TournamentTableAssignment.class);
    }

    private static boolean validSeats(MongoTemplate tx, Tournament event, List<PokerTable> open) {
        var rows = assignments(tx, event).stream().collect(Collectors.toMap(TournamentTableAssignment::getUserId, Function.identity()));
        var entrants = tx.find(Query.query(Criteria.where("tournamentId").is(event.getId()).and("currentVirtualChips").gt(0)),
                TournamentEntrant.class).stream().collect(Collectors.toMap(TournamentEntrant::getUserId, Function.identity()));
        int count = 0;
        Set<UUID> players = new HashSet<>();
        for (var table : open) {
            if (table.getGameplay().settledAt() == null) return false;
            for (var player : table.getPlayers()) {
                if (player.getChipStack() == 0) continue;
                count++;
                var entry = entrants.get(player.getUserId());
                var row = rows.get(player.getUserId());
                if (!players.add(player.getUserId()) || entry == null || row == null || !entry.getId().equals(row.getEntrantId())
                        || !table.getId().equals(row.getTableId()) || !table.getId().equals(entry.getTableId())
                        || row.getSeat() != player.getSeat() || entry.getSeat() == null || entry.getSeat() != player.getSeat()
                        || player.getChipStack() != entry.getCurrentVirtualChips()) return false;
            }
        }
        return count == entrants.size() && count == rows.size();
    }

    private void apply(MongoTemplate tx, Tournament event, List<PokerTable> open, Plan plan, Instant now) {
        var byId = open.stream().collect(Collectors.toMap(PokerTable::getId, Function.identity()));
        var rows = assignments(tx, event).stream().collect(Collectors.toMap(TournamentTableAssignment::getEntrantId, Function.identity()));
        var affected = new HashSet<>(plan.closingTables());
        // A reserved seat is unused in the committed assignment collection. Live rosters change solely in this transaction.
        for (var move : plan.transfers()) {
            var from = byId.get(move.sourceTableId());
            var to = byId.get(move.destinationTableId());
            var entrant = tx.findById(move.entrantId(), TournamentEntrant.class);
            var assignment = rows.get(move.entrantId());
            var player = from.getPlayers().stream().filter(p -> p.getUserId().equals(move.userId())).findFirst().orElseThrow();
            from.removePlayer(player);
            to.getPlayers().removeIf(p -> p.getChipStack() == 0 && p.getSeat() == move.destinationSeat());
            to.addPlayer(player.transferTournamentSeat(move.destinationSeat()));
            entrant.transfer(to.getId(), move.destinationSeat(), now);
            assignment.move(to.getId(), move.destinationSeat(), now);
            tx.save(entrant);
            tx.save(assignment);
            affected.add(from.getId());
            affected.add(to.getId());
        }
        for (var table : open) {
            if (affected.contains(table.getId())) {
                table.changeTournamentRoster(plan.closingTables().contains(table.getId()) ? now : null);
                if (table.getTournamentClosedAt() != null) table.recordTournamentHand(TournamentGameplayService.boundary(
                        table.getGameplay(), TournamentHand.Phase.PAUSED, null, "TABLE_CLOSED"));
            }
            if (table.getTournamentClosedAt() == null && (affected.contains(table.getId()) || table.getId().equals(plan.finalTableId()))
                    && table.getPlayers().stream().filter(p -> p.getChipStack() > 0).count() >= 2) {
                table.recordTournamentHand(TournamentGameplayService.boundary(table.getGameplay(), TournamentHand.Phase.BETWEEN_HANDS, now.plusSeconds(3), null));
                affected.add(table.getId());
            }
            if (affected.contains(table.getId())) tx.save(table);
        }
    }

    private static void persist(MongoTemplate tx, Tournament event, TournamentTopology topology) {
        tx.updateFirst(Query.query(Criteria.where("id").is(event.getId())), new Update().set("topology", topology)
                .inc("registrationRevision", 1), Tournament.class);
    }
}
