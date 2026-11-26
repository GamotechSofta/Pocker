package com.poker.tournament.service;

import com.poker.entity.PokerTable;
import com.poker.entity.User;
import com.poker.game.engine.GameStatus;
import com.poker.tournament.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TournamentFinalizationService {
    private static final Logger log = LoggerFactory.getLogger(TournamentFinalizationService.class);
    private static final long SETTLEMENT_GRACE_MILLIS = 2000;
    private static final long CLAIM_GRACE_MILLIS = 2500;
    private final MongoTemplate mongo;
    private final TournamentTransactions transactions;
    private final Clock clock;

    public TournamentFinalizationService(MongoTemplate mongo, TournamentTransactions transactions, Clock clock) {
        this.mongo = mongo;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void tick() {
        var due = Query.query(Criteria.where("status").is(TournamentStatus.PAUSED)
                .and("standings.candidate.entrantId").exists(true)
                .orOperator(Criteria.where("finalization").isNull(),
                        Criteria.where("finalization.stage").is(TournamentFinalization.Stage.CLAIMED)
                                .and("finalization.readyAt").lte(clock.instant())))
                .with(Sort.by("standings.settledAt", "id")).limit(32);
        due.fields().include("id");
        for (var event : mongo.find(due, Tournament.class)) {
            try {
                advance(event.getId());
            } catch (RuntimeException failure) {
                log.warn("Tournament finalization deferred for {}: {}", event.getId(), failure.getClass().getSimpleName());
            }
        }
    }

    /** Advances one committed state. A recovered worker uses the same persistent operation. */
    public TournamentCompletion advance(UUID id) {
        var result = transactions.run(tx -> {
            var event = tx.findById(id, Tournament.class);
            if (event == null || event.getStatus() != TournamentStatus.PAUSED || event.getStandings() == null
                    || event.getStandings().candidate() == null || event.getStandings().settledAt() == null) {
                return new FinalizationOutcome(event == null ? null : event.getCompletion(), null);
            }
            var now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            var progress = event.getFinalization();
            if (progress == null) {
                if (!now.isBefore(event.getStandings().settledAt().plusMillis(SETTLEMENT_GRACE_MILLIS))) {
                    saveClaim(tx, id, new TournamentFinalization(UUID.randomUUID(), TournamentFinalization.Stage.CLAIMED,
                            now, now.plusMillis(CLAIM_GRACE_MILLIS), null));
                }
                return new FinalizationOutcome(null, null);
            }
            if (progress.stage() != TournamentFinalization.Stage.CLAIMED || now.isBefore(progress.readyAt())) {
                return new FinalizationOutcome(null, null);
            }
            // Fence validation, ranked rows and lifecycle changes against other workers on the same event.
            tx.updateFirst(byId(id), new Update().inc("registrationRevision", 1), Tournament.class);
            Eligible outcome;
            try {
                outcome = validate(tx, event);
            } catch (Blocked failure) {
                var blocked = progress.withStage(TournamentFinalization.Stage.RECOVERY_REQUIRED, failure.getMessage());
                saveClaim(tx, id, blocked);
                return new FinalizationOutcome(null, blocked);
            }
            var completion = commit(tx, event, outcome, now);
            return new FinalizationOutcome(completion, progress.withStage(TournamentFinalization.Stage.COMPLETED, null));
        });
        if (result.transition() != null) {
            var transition = result.transition();
            var message = transition.stage() == TournamentFinalization.Stage.COMPLETED
                    ? log.atInfo().setMessage("Tournament finalized") : log.atWarn().setMessage("Tournament finalization requires recovery");
            message.addKeyValue("tournamentId", id).addKeyValue("finalizationOperationId", transition.operationId())
                    .addKeyValue("reason", transition.reason()).log();
        }
        return result.completion();
    }

    private record FinalizationOutcome(TournamentCompletion completion, TournamentFinalization transition) { }

    private static Eligible validate(MongoTemplate tx, Tournament event) {
        var checkpoint = event.getStandings();
        var candidate = checkpoint.candidate();
        var clock = event.getBlindClock();
        require(!checkpoint.reviewRequired() && checkpoint.completedHands() > 0, "STANDINGS_REVIEW_REQUIRED");
        require(clock != null && clock.pausedAt() != null && clock.pauseRequestedAt() == null
                && "INSUFFICIENT_PLAYING_STACKS".equals(clock.pauseReason()) && event.getStartedAt() != null
                && !clock.pausedAt().isBefore(event.getStartedAt()), "BLIND_CLOCK_NOT_STOPPED");
        require(event.getTopology() == null || event.getTopology().pending() == null, "UNRESOLVED_TABLE_TRANSFER");
        require(event.getInitialization() != null, "TABLES_UNAVAILABLE");
        require(!tx.exists(byTournament(event.getId()), TournamentFinalResult.class), "RESULT_ROWS_ALREADY_EXIST");

        var field = tx.find(byTournament(event.getId()).addCriteria(Criteria.where("initialVirtualChips").gt(0)), TournamentEntrant.class);
        var survivors = field.stream().filter(row -> row.getStatus() == TournamentEntrantStatus.ACTIVE && row.getCurrentVirtualChips() > 0).toList();
        require(survivors.size() == 1 && field.size() >= 2, "INVALID_PARTICIPATING_FIELD");
        var champion = survivors.getFirst();
        require(champion.getId().equals(candidate.entrantId()) && champion.getUserId().equals(candidate.userId())
                && champion.getFinishPosition() == null && champion.getCurrentVirtualChips() == candidate.settledStack()
                && champion.getElimination() == null, "INVALID_CHAMPION_CANDIDATE");

        var tables = TournamentTableBalanceService.tables(tx, event);
        var byTable = tables.stream().collect(Collectors.toMap(PokerTable::getId, Function.identity()));
        require(tables.size() == event.getInitialization().preparedTableIds().size()
                && tables.stream().allMatch(table -> validSettledTable(table, event.getId())), "UNRESOLVED_POKER_HAND");
        var open = tables.stream().filter(table -> table.getTournamentClosedAt() == null).toList();
        require(open.size() == 1 && open.getFirst().getId().equals(champion.getTableId()), "INVALID_FINAL_TABLE");
        var last = open.getFirst();
        require(last.getGameplay().id().equals(checkpoint.handId()) && !clock.pausedAt().isBefore(last.getGameplay().settledAt())
                && (event.getTopology() == null || last.getId().equals(event.getTopology().finalTableId())), "INVALID_FINAL_CHECKPOINT");

        var occupied = tables.stream().flatMap(table -> table.getPlayers().stream()).filter(player -> player.getChipStack() > 0).toList();
        long initialSupply = field.stream().mapToLong(TournamentEntrant::getInitialVirtualChips).sum();
        require(initialSupply == event.getStartingVirtualChips() * field.size() && occupied.size() == 1
                && occupied.getFirst().getUserId().equals(champion.getUserId())
                && champion.getSeat() != null && occupied.getFirst().getSeat() == champion.getSeat()
                && occupied.getFirst().getEscrowId() == null && occupied.getFirst().getChipStack() == initialSupply
                && champion.getCurrentVirtualChips() == initialSupply, "CHIP_SUPPLY_MISMATCH");

        var assignments = tx.find(byTournament(event.getId()).addCriteria(Criteria.where("releasedAt").isNull()), TournamentTableAssignment.class);
        require(assignments.stream().anyMatch(row -> row.getEntrantId().equals(champion.getId()) && champion.getTableId().equals(row.getTableId())
                && champion.getUserId().equals(row.getUserId()) && champion.getSeat() == row.getSeat()), "CHAMPION_ASSIGNMENT_UNAVAILABLE");
        var allocations = tx.find(byTournament(event.getId()).addCriteria(Criteria.where("type").is(TournamentChipLedgerEntry.EntryType.INITIAL_STACK)),
                TournamentChipLedgerEntry.class);
        var allocated = allocations.stream().collect(Collectors.groupingBy(TournamentChipLedgerEntry::getEntrantId));
        require(allocations.size() == field.size() && field.stream().allMatch(entry -> {
            var rows = allocated.getOrDefault(entry.getId(), List.of());
            return rows.size() == 1 && rows.getFirst().getDelta() == event.getStartingVirtualChips()
                    && rows.getFirst().getResultingBalance() == entry.getInitialVirtualChips();
        }), "INITIAL_LEDGER_MISMATCH");

        var evidence = validatePositions(tx, event, field, champion, byTable);
        return new Eligible(field, champion, last, byTable, assignments, evidence, initialSupply);
    }

    private static boolean validSettledTable(PokerTable table, UUID eventId) {
        var hand = table.getGameplay();
        return table.isTournament() && eventId.equals(table.getTournamentId()) && hand != null && hand.id() != null
                && hand.settledAt() != null && hand.phase() == TournamentHand.Phase.PAUSED && hand.street() == GameStatus.NEXT_HAND
                && hand.nextHandAt() == null && hand.turnDeadline() == null;
    }

    private static Map<UUID, TournamentChipLedgerEntry> validatePositions(MongoTemplate tx, Tournament event, List<TournamentEntrant> field,
                                                                         TournamentEntrant champion, Map<UUID, PokerTable> tables) {
        var positions = new HashSet<Integer>();
        var settlementIds = new HashSet<UUID>();
        for (var entrant : field) {
            require(entrant.getTableId() != null && tables.containsKey(entrant.getTableId()) && entrant.getSeat() != null, "INVALID_PARTICIPANT_TABLE");
            if (entrant == champion) continue;
            var elimination = entrant.getElimination();
            var position = entrant.getFinishPosition();
            require(entrant.getStatus() == TournamentEntrantStatus.ELIMINATED && entrant.getCurrentVirtualChips() == 0
                    && entrant.getEliminatedAt() != null && position != null && position >= 2 && position <= field.size()
                    && positions.add(position) && elimination != null && elimination.handId() != null && elimination.settlementId() != null
                    && settlementIds.add(elimination.settlementId()), "INVALID_OFFICIAL_POSITIONS");
        }
        var last = tx.findOne(byTournament(event.getId()).addCriteria(Criteria.where("idempotencyKey")
                .is("hand:" + event.getStandings().handId() + ":" + champion.getId())), TournamentChipLedgerEntry.class);
        require(matches(last, champion, event.getStandings().handId()), "CHAMPION_SETTLEMENT_UNAVAILABLE");
        var rows = tx.find(Query.query(Criteria.where("id").in(settlementIds)), TournamentChipLedgerEntry.class)
                .stream().collect(Collectors.toMap(TournamentChipLedgerEntry::getId, Function.identity()));
        require(field.stream().filter(row -> row != champion).allMatch(row ->
                matches(rows.get(row.getElimination().settlementId()), row, row.getElimination().handId())), "INVALID_ELIMINATION_LEDGER");
        rows.put(last.getId(), last);
        return rows;
    }

    private static boolean matches(TournamentChipLedgerEntry ledger, TournamentEntrant entry, UUID hand) {
        return ledger != null && ledger.getType() == TournamentChipLedgerEntry.EntryType.HAND_RESULT
                && entry.getTournamentId().equals(ledger.getTournamentId()) && entry.getId().equals(ledger.getEntrantId())
                && entry.getTableId().equals(ledger.getTableId()) && ledger.getResultingBalance() == entry.getCurrentVirtualChips()
                && ("hand:" + hand + ":" + entry.getId()).equals(ledger.getIdempotencyKey());
    }

    private static TournamentCompletion commit(MongoTemplate tx, Tournament event, Eligible result, Instant now) {
        var players = tx.find(Query.query(Criteria.where("id").in(result.field().stream().map(TournamentEntrant::getUserId).toList())), User.class)
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        var handCounts = tx.aggregate(Aggregation.newAggregation(
                Aggregation.match(Criteria.where("tournamentId").is(event.getId()).and("type").is(TournamentChipLedgerEntry.EntryType.HAND_RESULT)),
                Aggregation.group("entrantId").count().as("hands")), "tournament_chip_ledger", HandCount.class)
                .getMappedResults().stream().collect(Collectors.toMap(HandCount::id, HandCount::hands));
        var operation = event.getFinalization().operationId();
        var winner = result.champion();
        winner.completeAsChampion(now);
        tx.save(winner);
        var official = result.field().stream().map(entry -> {
            var handId = entry == winner ? event.getStandings().handId() : entry.getElimination().handId();
            var ledger = result.evidence().values().stream().filter(row -> row.getEntrantId().equals(entry.getId())).findFirst().orElseThrow();
            return TournamentFinalResult.official(entry, operation, players.getOrDefault(entry.getUserId(), "Player"),
                    entry.getFinishPosition(), result.tables().get(entry.getTableId()).getTableNumber(), handId, ledger.getId(),
                    handCounts.getOrDefault(entry.getId(), 0), now);
        }).toList();
        tx.insert(official, TournamentFinalResult.class);
        var champion = official.stream().filter(row -> row.getFinishPosition() == 1).findFirst().orElseThrow();
        var finalTable = result.finalTable();
        finalTable.closeCompletedTournament(now);
        tx.save(finalTable);
        for (var assignment : result.assignments()) {
            assignment.release(now);
            tx.save(assignment);
        }
        var clock = event.getBlindClock();
        var summary = new TournamentCompletion(operation, 1, event.getName(), TournamentDiscoveryService.safeBanner(event.getBanner()),
                event.getStartMode(), event.getTableMode(), official.size(), event.getStartingVirtualChips(), result.supply(),
                winner.getId(), champion.getId(), champion.getDisplayName(), event.getStartedAt(), now,
                Math.max(0, clock.pausedAt().toEpochMilli() - clock.startedAt().toEpochMilli() - clock.pausedMillis()),
                finalTable.getGameplay().appliedBlind(finalTable.getInitialBlind()), event.getStandings().completedHands(),
                finalTable.getId(), finalTable.getTableNumber(), 1);
        event.complete(summary);
        tx.updateFirst(byId(event.getId()), new Update().set("status", event.getStatus()).set("updatedAt", now)
                .set("completedAt", event.getCompletedAt()).set("finalization", event.getFinalization()).set("completion", summary), Tournament.class);
        return summary;
    }

    public record HandCount(@org.springframework.data.annotation.Id UUID id, int hands) { }

    private record Eligible(List<TournamentEntrant> field, TournamentEntrant champion, PokerTable finalTable, Map<UUID, PokerTable> tables,
                            List<TournamentTableAssignment> assignments, Map<UUID, TournamentChipLedgerEntry> evidence, long supply) { }

    private static void saveClaim(MongoTemplate tx, UUID id, TournamentFinalization claim) {
        tx.updateFirst(byId(id), new Update().set("finalization", claim).inc("registrationRevision", 1), Tournament.class);
    }

    private static Query byId(UUID id) { return Query.query(Criteria.where("id").is(id)); }
    private static Query byTournament(UUID id) { return Query.query(Criteria.where("tournamentId").is(id)); }
    private static void require(boolean valid, String reason) { if (!valid) throw new Blocked(reason); }
    private static class Blocked extends RuntimeException { Blocked(String reason) { super(reason); } }
}
