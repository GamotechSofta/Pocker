package com.poker.tournament.service;

import com.poker.entity.PokerTable;
import com.poker.exception.ApiException;
import com.poker.tournament.dto.TournamentAssignment;
import com.poker.tournament.dto.TournamentAssignment.Stage;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentEntrant;
import com.poker.tournament.entity.TournamentEntrantStatus;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentTableAssignment;
import com.poker.tournament.entity.TournamentTableMode;
import com.poker.tournament.entity.TournamentTopology;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TournamentAssignmentService {
    private final TournamentTransactions transactions;

    public TournamentAssignmentService(TournamentTransactions transactions) {
        this.transactions = transactions;
    }

    public TournamentAssignment get(UUID id, UUID viewer) {
        return transactions.run(tx -> {
            var tournament = tx.findById(id, Tournament.class);
            var entry = tx.findOne(Query.query(Criteria.where("tournamentId").is(id).and("userId").is(viewer)), TournamentEntrant.class);
            return snapshot(tx, tournament, entry, Instant.now());
        });
    }

    TournamentAssignment snapshot(MongoTemplate tx, Tournament tournament, TournamentEntrant entry, Instant now) {
        if (tournament == null || entry == null || tournament.getStatus() == TournamentStatus.DRAFT
                || entry.getStatus() == TournamentEntrantStatus.CANCELLED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOURNAMENT_NOT_FOUND", "Tournament not found.");
        }
        var progress = tournament.getInitialization();
        if (tournament.getStatus() == TournamentStatus.CANCELLED) {
            return pending(tournament, Stage.CANCELLED, progress == null ? null : progress.reason(), now);
        }
        if (tournament.getStatus() == TournamentStatus.COMPLETED && tournament.getCompletion() != null && entry.getFinishPosition() != null) {
            var result = tournament.getCompletion();
            return new TournamentAssignment(tournament.getId(), result.tournamentName(), tournament.getStatus(), Stage.COMPLETED,
                    null, null, null, null, null, null, null, null, now, 0, 0, entry.getCurrentVirtualChips(), multi(tournament),
                    false, false, null, result.finalBlind(), null, null, "COMPLETED",
                    new TournamentAssignment.Completion(result.operationId(), result.resultVersion(), entry.getFinishPosition(), result.completedAt()));
        }
        if (entry.getStatus() == TournamentEntrantStatus.ELIMINATED) return pending(tournament, Stage.ELIMINATED, entry.getEliminationReason(), now);
        if (progress == null) return pending(tournament, Stage.WAITING, null, now);
        switch (progress.phase()) {
            case BLOCKED -> { return pending(tournament, Stage.BLOCKED, progress.reason(), now); }
            case RETRYING -> { return pending(tournament, Stage.DELAYED, progress.reason(), now); }
            case CLAIMED -> { return pending(tournament, Stage.PREPARING, null, now); }
            case CANCELLED -> { return pending(tournament, Stage.CANCELLED, progress.reason(), now); }
            default -> { }
        }
        var assigned = tx.findOne(Query.query(Criteria.where("tournamentId").is(tournament.getId())
                .and("entrantId").is(entry.getId())), TournamentTableAssignment.class);
        var table = assigned == null ? null : tx.findById(assigned.getTableId(), PokerTable.class);
        if (assigned == null || assigned.getReleasedAt() != null || table == null || !table.isTournament()
                || table.getTournamentClosedAt() != null || !progress.preparedTableIds().contains(table.getId())
                || !tournament.getId().equals(table.getTournamentId()) || !table.getId().equals(assigned.getTableId())
                || !entry.getUserId().equals(assigned.getUserId()) || !table.getId().equals(entry.getTableId())
                || entry.getSeat() == null || entry.getSeat() != assigned.getSeat()) {
            return pending(tournament, Stage.DELAYED, "ASSIGNMENT_UNAVAILABLE", now);
        }
        var layout = tournament.getTopology();
        boolean finalTable = layout != null && table.getId().equals(layout.finalTableId());
        boolean finalPreparing = layout != null && layout.pending() != null && layout.pending().finalTableId() != null;
        Integer activePlayers = null;
        Integer rank = null;
        if (finalTable || finalPreparing) {
            var numbers = TournamentTableBalanceService.tables(tx, tournament).stream()
                    .collect(Collectors.toMap(PokerTable::getId, PokerTable::getTableNumber));
            var active = tx.find(Query.query(Criteria.where("tournamentId").is(tournament.getId()).and("currentVirtualChips").gt(0)),
                    TournamentEntrant.class);
            activePlayers = active.size();
            var order = TournamentStandingsService.activeOrder(numbers);
            rank = 1 + (int) active.stream().filter(other -> order.compare(other, entry) < 0).count();
        }
        return new TournamentAssignment(tournament.getId(), tournament.getName(), tournament.getStatus(), Stage.READY,
                table.getId(), table.getTableNumber(), assigned.getSeat(), entry.getInitialVirtualChips(),
                table.getInitialBlind(), assigned.getAssignedAt(), table.getPreparedAt(), null, now, assigned.getAssignmentVersion(),
                layout == null ? 0 : layout.version(), entry.getCurrentVirtualChips(), multi(tournament),
                finalTable, finalPreparing, transfer(layout, entry, table),
                tournament.getBlindClock() == null ? null : tournament.getBlindClock().current(), activePlayers, rank,
                finalizationStage(tournament), null);
    }

    private static TournamentAssignment.Transfer transfer(TournamentTopology layout, TournamentEntrant entry, PokerTable table) {
        if (layout == null) return null;
        if (layout.pending() != null) {
            var value = transfer(layout.pending(), entry, table);
            if (value != null) return value;
        }
        for (var plan : layout.history().reversed()) {
            var value = transfer(plan, entry, table);
            if (value != null) return value;
        }
        return null;
    }

    private static TournamentAssignment.Transfer transfer(TournamentTopology.Plan plan, TournamentEntrant entry, PokerTable table) {
        return plan.transfers().stream().filter(row -> entry.getId().equals(row.entrantId())).findFirst().map(row -> {
            boolean completed = plan.stage() == TournamentTopology.Stage.COMPLETED;
            return new TournamentAssignment.Transfer(plan.id(), plan.stage().name(), row.sourceTableNumber(), row.destinationTableNumber(),
                    row.destinationSeat(), completed ? row.settledStack() : entry.getCurrentVirtualChips(),
                    completed || (table.getGameplay() != null && table.getGameplay().settledAt() != null),
                    row.destinationTableId().equals(plan.finalTableId()), plan.completedAt());
        }).orElse(null);
    }

    private static boolean multi(Tournament event) { return event.getTableMode() == TournamentTableMode.MULTI_TABLE; }

    private static String finalizationStage(Tournament event) {
        return event.getFinalization() == null ? null : event.getFinalization().stage().name();
    }

    private static TournamentAssignment pending(Tournament event, Stage stage, String reason, Instant now) {
        return new TournamentAssignment(event.getId(), event.getName(), event.getStatus(), stage,
                null, null, null, null, null, null, null, reason, now, 0, 0, null, multi(event), false, false, null, null, null, null,
                finalizationStage(event), null);
    }
}
