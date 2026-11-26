package com.poker.tournament.service;

import com.poker.config.TableProperties;
import com.poker.dto.GameActionRequest;
import com.poker.dto.TableDetailResponse;
import com.poker.dto.TableStateResponse;
import com.poker.entity.PokerTable;
import com.poker.entity.PokerTablePlayer;
import com.poker.exception.ApiException;
import com.poker.game.engine.ActionType;
import com.poker.game.engine.GameActionException;
import com.poker.game.engine.GameState;
import com.poker.game.engine.GameStatus;
import com.poker.game.engine.PlayerAction;
import com.poker.game.engine.TableConfig;
import com.poker.game.engine.TexasHoldemEngine;
import com.poker.game.model.Card;
import com.poker.game.model.Deck;
import com.poker.game.model.GameMode;
import com.poker.game.variants.TexasHoldemVariant;
import com.poker.mapper.TableMapper;
import com.poker.service.DealTiming;
import com.poker.tournament.dto.TournamentClockResponse;
import com.poker.tournament.entity.BlindLevel;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentBlindClock;
import com.poker.tournament.entity.TournamentBlindSchedule;
import com.poker.tournament.entity.TournamentChipLedgerEntry;
import com.poker.tournament.entity.TournamentEntrant;
import com.poker.tournament.entity.TournamentEntrantStatus;
import com.poker.tournament.entity.TournamentHand;
import com.poker.tournament.entity.TournamentHand.Move;
import com.poker.tournament.entity.TournamentHand.Phase;
import com.poker.tournament.entity.TournamentHand.Stack;
import com.poker.tournament.entity.TournamentInitialization;
import com.poker.tournament.entity.TournamentSettledHand;
import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentStandingCheckpoint;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentTableAssignment;
import com.poker.tournament.entity.TournamentTableMode;
import com.poker.tournament.entity.TournamentTopology;
import com.poker.websocket.WsEventType;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TournamentGameplayService {
    private static final Logger log = LoggerFactory.getLogger(TournamentGameplayService.class);
    private static final int JOURNAL_FORMAT = 2;
    private final MongoTemplate mongo;
    private final TournamentTransactions transactions;
    private final TournamentSeatingService seating;
    private final TableProperties properties;
    private final Clock clock;
    private final TournamentBlindClockService blinds;
    private final TournamentEliminationService eliminations;
    private final TournamentTableBalanceService balance = new TournamentTableBalanceService();

    public TournamentGameplayService(MongoTemplate mongo, TournamentTransactions transactions,
                                     TournamentSeatingService seating, TableProperties properties, Clock clock,
                                     TournamentBlindClockService blinds, TournamentEliminationService eliminations) {
        this.mongo = mongo;
        this.transactions = transactions;
        this.seating = seating;
        this.properties = properties;
        this.clock = clock;
        this.blinds = blinds;
        this.eliminations = eliminations;
    }

    public void tick() {
        var prepared = Query.query(Criteria.where("status").is(TournamentStatus.STARTING)
                .and("initialization.phase").is(TournamentInitialization.Phase.READY)).limit(32);
        prepared.fields().include("id");
        for (var event : mongo.find(prepared, Tournament.class)) {
            safely(event.getId(), this::activate);
        }

        Instant now = clock.instant();
        var dueClocks = Query.query(Criteria.where("status").is(TournamentStatus.RUNNING)
                .and("initialization.phase").is(TournamentInitialization.Phase.READY).orOperator(
                        Criteria.where("blindClock").exists(false), Criteria.where("blindClock.levelEndsAt").lte(now))).limit(32);
        dueClocks.fields().include("id");
        for (var event : mongo.find(dueClocks, Tournament.class)) {
            safely(event.getId(), this::advanceClock);
        }
        var transfers = Query.query(Criteria.where("status").is(TournamentStatus.RUNNING)
                .and("topology.pending.stage").in(TournamentTopology.Stage.WAITING_FOR_HANDS, TournamentTopology.Stage.DESTINATION_RESERVED)).limit(32);
        transfers.fields().include("id");
        for (var event : mongo.find(transfers, Tournament.class)) {
            safely(event.getId(), this::advanceTransfers);
        }
        var due = Query.query(Criteria.where("gameMode").is("TOURNAMENT").and("tournamentClosedAt").isNull().orOperator(
                Criteria.where("gameplay.phase").is(Phase.ACTIVE).and("gameplay.turnDeadline").lte(now),
                Criteria.where("gameplay.phase").is(Phase.BETWEEN_HANDS).and("gameplay.nextHandAt").lte(now))).limit(64);
        due.fields().include("id");
        for (var table : mongo.find(due, PokerTable.class)) {
            safely(table.getId(), this::advance);
        }
    }

    private static void safely(UUID id, Consumer<UUID> task) {
        try {
            task.accept(id);
        } catch (RuntimeException error) {
            log.warn("Tournament gameplay work deferred for {}: {}", id, error.getClass().getSimpleName());
        }
    }

    public void activate(UUID tournamentId) {
        var result = transactions.run(tx -> {
            var tournament = tx.findById(tournamentId, Tournament.class);
            if (tournament == null || tournament.getStatus() != TournamentStatus.STARTING) return null;
            var initialization = tournament.getInitialization();
            if (initialization == null || initialization.phase() != TournamentInitialization.Phase.READY) return null;
            var tables = TournamentTableBalanceService.tables(tx, tournament);
            TournamentBlindSchedule schedule;
            try {
                schedule = blinds.schedule(tx, tournament);
                verifyActivation(tx, tournament, tables, schedule);
            } catch (IllegalArgumentException invalid) {
                var blocked = new TournamentInitialization(initialization.operationId(), TournamentInitialization.Phase.BLOCKED,
                        initialization.claimedAt(), initialization.tableId(), initialization.preparedAt(), "INVALID_ACTIVATION", null, initialization.tableIds());
                tx.updateFirst(event(tournamentId), new Update().set("initialization", blocked)
                        .set("updatedAt", clock.instant()).inc("registrationRevision", 1), Tournament.class);
                return blocked;
            }

            Instant now = clock.instant();
            var blindClock = blinds.initialize(schedule, now);
            // Same document that Express cancellation and registration update. A competing commit retries the snapshot.
            var activation = new Update().set("status", TournamentStatus.RUNNING)
                    .set("startedAt", now).set("blindClock", blindClock).set("updatedAt", now)
                    .inc("registrationRevision", 1);
            if (isMulti(tournament)) activation.set("topology",
                    new TournamentTopology(0, tables.size() == 1 ? tables.getFirst().getId() : null, null, List.of()));
            tx.updateFirst(event(tournamentId), activation, Tournament.class);
            for (var table : tables) startHand(tx, table, now, blindClock.current());
            return initialization;
        });
        if (result == null) return;
        if (result.phase() == TournamentInitialization.Phase.BLOCKED) {
            log.atWarn().addKeyValue("tournamentId", tournamentId).addKeyValue("initializationOperationId", result.operationId())
                    .addKeyValue("reason", result.reason()).log("Tournament activation requires recovery");
        } else {
            for (UUID tableId : result.preparedTableIds()) log.atInfo().addKeyValue("tournamentId", tournamentId)
                    .addKeyValue("tableId", tableId).addKeyValue("initializationOperationId", result.operationId())
                    .log("Tournament table activated");
        }
    }

    private void verifyActivation(MongoTemplate tx, Tournament tournament, List<PokerTable> tables, TournamentBlindSchedule schedule) {
        require(tournament.getGameMode() == GameMode.TOURNAMENT && !tables.isEmpty()
                && tables.size() == tournament.getInitialization().preparedTableIds().size());
        var config = seating.configuration(tournament, schedule.getLevels());
        var field = tx.find(entries(tournament.getId()).addCriteria(Criteria.where("status").ne(TournamentEntrantStatus.CANCELLED)),
                TournamentEntrant.class);
        var assigned = tx.find(entries(tournament.getId()), TournamentTableAssignment.class);
        var ledger = tx.find(entries(tournament.getId()), TournamentChipLedgerEntry.class);
        int count = field.size();
        int expectedMinimum = tournament.getStartMode() == TournamentStartMode.SIT_AND_GO
                ? tournament.getMaximumEntrants() : tournament.getMinimumEntrants();
        require(count >= expectedMinimum && count <= tournament.getMaximumEntrants()
                && count == tables.stream().mapToInt(table -> table.getPlayers().size()).sum()
                && tables.size() == Math.ceilDiv(count, config.maxPlayers())
                && count == assigned.size() && count == ledger.size());
        var seenUsers = new HashSet<UUID>();
        for (int index = 0; index < tables.size(); index++) {
            var table = tables.get(index);
            require(table.isTournament() && tournament.getId().equals(table.getTournamentId()) && table.getGameplay() == null
                    && Integer.valueOf(index + 1).equals(table.getTableNumber()) && TexasHoldemVariant.NAME.equals(table.getVariant())
                    && schedule.getLevels().getFirst().equals(table.getInitialBlind()) && table.getRoomCode() == null && !table.isPrivateRoom()
                    && config.smallBlind() == table.getSmallBlind() && config.bigBlind() == table.getBigBlind()
                    && config.startingChips() == table.getStartingChips() && config.maxPlayers() == table.getMaxPlayers()
                    && table.getPlayers().size() == count / tables.size() + (index < count % tables.size() ? 1 : 0));
            var seenSeats = new HashSet<Integer>();
            verifySeats(table, config, field, assigned, ledger, seenSeats, seenUsers);
        }
    }

    private static void verifySeats(PokerTable table, TableConfig config, List<TournamentEntrant> field,
                                    List<TournamentTableAssignment> assigned, List<TournamentChipLedgerEntry> ledger,
                                    HashSet<Integer> seenSeats, HashSet<UUID> seenUsers) {
        for (var player : table.getPlayers()) {
            require(player.getSeat() >= 0 && player.getSeat() < config.maxPlayers() && seenSeats.add(player.getSeat())
                    && seenUsers.add(player.getUserId()) && player.getChipStack() == config.startingChips() && player.getEscrowId() == null);
            var entrant = field.stream().filter(row -> player.getUserId().equals(row.getUserId())).findFirst().orElse(null);
            require(entrant != null && entrant.getStatus() == TournamentEntrantStatus.SEATED
                    && table.getId().equals(entrant.getTableId()) && Integer.valueOf(player.getSeat()).equals(entrant.getSeat())
                    && entrant.getInitialVirtualChips() == config.startingChips() && entrant.getCurrentVirtualChips() == config.startingChips());
            require(assigned.stream().anyMatch(row -> entrant.getId().equals(row.getEntrantId())
                    && player.getUserId().equals(row.getUserId()) && table.getId().equals(row.getTableId())
                    && row.getSeat() == player.getSeat() && row.getReleasedAt() == null));
            require(ledger.stream().anyMatch(row -> entrant.getId().equals(row.getEntrantId()) && table.getId().equals(row.getTableId())
                    && row.getType() == TournamentChipLedgerEntry.EntryType.INITIAL_STACK && row.getDelta() == config.startingChips()
                    && row.getResultingBalance() == config.startingChips()));
        }
    }

    private static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("The prepared table, assignments, or opening stacks are invalid.");
    }

    public void advanceTransfers(UUID id) {
        var change = transactions.run(tx -> {
            var event = tx.findById(id, Tournament.class);
            if (event != null && event.getStatus() == TournamentStatus.RUNNING) {
                var now = clock.instant();
                balance.advance(tx, event, now);
                var updated = tx.findById(id, Tournament.class);
                if (updated.getTopology() != null && updated.getTopology().pending() != null
                        && updated.getTopology().pending().stage() == TournamentTopology.Stage.RECOVERY_REQUIRED) {
                    var table = clockTable(tx, updated);
                    var value = blinds.advance(blinds.current(tx, updated, table, now), now);
                    blinds.persist(tx, id, blinds.freeze(value, now, TournamentTableBalanceService.RECOVERY), TournamentStatus.PAUSED, now);
                }
                return new TransferAudit(event.getTopology(), updated.getTopology());
            }
            return null;
        });
        if (change == null || change.after() == null || Objects.equals(change.before(), change.after())) return;
        var pending = change.after().pending();
        var prior = change.before() == null ? null : change.before().pending();
        if (pending != null && !Objects.equals(pending, prior)) {
            log.atInfo().addKeyValue("tournamentId", id).addKeyValue("transferOperationId", pending.id())
                    .addKeyValue("stage", pending.stage()).log("Tournament transfer stage changed");
        } else if (pending == null && prior != null && !change.after().history().isEmpty()) {
            var completed = change.after().history().getLast();
            log.atInfo().addKeyValue("tournamentId", id).addKeyValue("transferOperationId", completed.id())
                    .addKeyValue("movedPlayers", completed.transfers().size()).log("Tournament transfers completed");
            for (UUID tableId : completed.closingTables()) log.atInfo().addKeyValue("tournamentId", id)
                    .addKeyValue("tableId", tableId).addKeyValue("transferOperationId", completed.id()).log("Tournament table closed");
        }
        if (change.after().finalTableId() != null &&
                (change.before() == null || !change.after().finalTableId().equals(change.before().finalTableId()))) {
            log.atInfo().addKeyValue("tournamentId", id).addKeyValue("tableId", change.after().finalTableId()).log("Tournament final table ready");
        }
    }

    private record TransferAudit(TournamentTopology before, TournamentTopology after) { }

    private static boolean isMulti(Tournament event) { return event.getTableMode() == TournamentTableMode.MULTI_TABLE; }

    public void advance(UUID tableId) {
        transactions.run(tx -> {
            var table = tx.findById(tableId, PokerTable.class);
            if (table == null || !table.isTournament() || table.getGameplay() == null || table.getTournamentClosedAt() != null) return null;
            var tournament = tx.findById(table.getTournamentId(), Tournament.class);
            if (tournament == null || tournament.getStatus() != TournamentStatus.RUNNING) return null;
            var hand = table.getGameplay();
            Instant now = clock.instant();
            boolean next = hand.phase() == Phase.BETWEEN_HANDS && hand.nextHandAt() != null && !now.isBefore(hand.nextHandAt());
            boolean timeout = hand.phase() == Phase.ACTIVE && hand.turnDeadline() != null && !now.isBefore(hand.turnDeadline());
            if ((!next && !timeout) || (next && balance.hold(tournament))) return null;
            try {
            if (next) {
                if (!table.isTournamentRosterChanged()) replay(table);
                var value = blinds.advance(blinds.current(tx, tournament, table, now), now);
                if (!isMulti(tournament) && tournament.getStandings() == null && table.getPlayers().stream().anyMatch(p -> p.getChipStack() == 0)) {
                    var review = new TournamentStandingCheckpoint(hand.number(), hand.id(), hand.number(), hand.settledAt(), List.of(), null, true);
                    table.recordTournamentHand(boundary(hand, Phase.PAUSED, null, "ELIMINATION_REVIEW_REQUIRED"));
                    tx.save(table);
                    saveStanding(tx, tournament.getId(), review);
                    blinds.persist(tx, tournament.getId(), blinds.freeze(value, now, "ELIMINATION_REVIEW_REQUIRED"), TournamentStatus.PAUSED, now);
                    return null;
                }
                if (value.pauseRequestedAt() != null) {
                    pauseBetweenHands(tx, table);
                    if (hasActiveHand(tx, tournament, table.getId())) blinds.persist(tx, tournament.getId(), value, TournamentStatus.RUNNING, now);
                    else blinds.persist(tx, tournament.getId(), blinds.freeze(value, now, TournamentBlindClockService.ADMIN), TournamentStatus.PAUSED, now);
                    return null;
                }
                // The write fences a simultaneous administrative pause before a new hand can post blinds.
                blinds.persist(tx, tournament.getId(), value, TournamentStatus.RUNNING, now);
                startHand(tx, table, now, value.current());
            } else {
                var engine = replay(table);
                var actor = engine.state().currentPlayer();
                ActionType action = engine.legalActions(actor.getPlayerId()).allows(ActionType.CHECK) ? ActionType.CHECK : ActionType.FOLD;
                move(tx, table, engine, new Move(UUID.fromString(actor.getPlayerId()), null, action, 0), now);
            }
            } catch (UnreplayableHand bad) {
                pause(tx, table, now);
            }
            return null;
        });
    }

    public void advanceClock(UUID tournamentId) {
        var level = transactions.run(tx -> {
            var tournament = tx.findById(tournamentId, Tournament.class);
            if (tournament == null || tournament.getStatus() != TournamentStatus.RUNNING) return null;
            var table = clockTable(tx, tournament);
            var now = clock.instant();
            var value = blinds.advance(blinds.current(tx, tournament, table, now), now);
            if (value != tournament.getBlindClock()) blinds.persist(tx, tournamentId, value, TournamentStatus.RUNNING, now);
            return tournament.getBlindClock() != null && value.scheduledLevel() != tournament.getBlindClock().scheduledLevel()
                    ? value.scheduledLevel() : null;
        });
        if (level != null) log.atInfo().addKeyValue("tournamentId", tournamentId).addKeyValue("level", level)
                .log("Tournament blind level changed");
    }

    public TournamentClockResponse clock(UUID tournamentId, UUID userId) {
        return transactions.run(tx -> {
            var tournament = tx.findById(tournamentId, Tournament.class);
            var table = clockTable(tx, tournament);
            if (userId != null) {
                var entrant = tx.findOne(entries(tournamentId).addCriteria(Criteria.where("userId").is(userId)), TournamentEntrant.class);
                if (entrant == null || entrant.getTableId() == null) throw unavailable();
                if (tournament.getStatus() == TournamentStatus.COMPLETED) {
                    if (entrant.getFinishPosition() == null) throw unavailable();
                } else if (entrant.getStatus() != TournamentEntrantStatus.ELIMINATED || !isMulti(tournament)) {
                    table = assignedTable(tx, entrant.getTableId(), userId).table();
                }
            }
            var now = clock.instant();
            return blinds.snapshot(tournamentId, tournament.getStatus(), blinds.current(tx, tournament, table, now), table, now);
        });
    }

    public TournamentClockResponse pauseClock(UUID tournamentId, boolean pause) {
        return transactions.run(tx -> {
            var tournament = tx.findById(tournamentId, Tournament.class);
            if (tournament != null && tournament.getStatus() == TournamentStatus.COMPLETED) {
                throw new ApiException(HttpStatus.CONFLICT, "TOURNAMENT_COMPLETED", "The official tournament results are available.");
            }
            var table = clockTable(tx, tournament);
            var now = clock.instant();
            var value = blinds.current(tx, tournament, table, now);
            var status = tournament.getStatus();
            if (status == TournamentStatus.PAUSED && !TournamentBlindClockService.ADMIN.equals(value.pauseReason())) {
                throw new ApiException(HttpStatus.CONFLICT, "TOURNAMENT_RECOVERY_REQUIRED", "This gameplay pause requires recovery review.");
            }
            if (pause && status == TournamentStatus.RUNNING) {
                value = blinds.advance(value, now);
                if (hasActiveHand(tx, tournament, null)) {
                    value = blinds.requestPause(value, now);
                } else {
                    for (var current : TournamentTableBalanceService.tables(tx, tournament)) {
                        if (current.getTournamentClosedAt() == null) pauseBetweenHands(tx, current);
                    }
                    value = blinds.freeze(value, now, TournamentBlindClockService.ADMIN);
                    status = TournamentStatus.PAUSED;
                }
            } else if (!pause && status == TournamentStatus.PAUSED) {
                value = blinds.resume(value, now);
                status = TournamentStatus.RUNNING;
                for (var current : TournamentTableBalanceService.tables(tx, tournament)) {
                    if (current.getTournamentClosedAt() != null || current.getGameplay().phase() != Phase.PAUSED) continue;
                    boolean playable = current.getPlayers().stream().filter(player -> player.getChipStack() > 0).count() >= 2;
                    current.recordTournamentHand(boundary(current.getGameplay(), playable ? Phase.BETWEEN_HANDS : Phase.PAUSED,
                            playable ? now.plusSeconds(1) : null, playable ? null : TournamentTableBalanceService.WAITING));
                    tx.save(current);
                }
            } else if (!pause && value.pauseRequestedAt() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "TOURNAMENT_PAUSE_PENDING", "The current hand must settle before you can resume.");
            }
            if (value != tournament.getBlindClock()) blinds.persist(tx, tournamentId, value, status, now);
            return blinds.snapshot(tournamentId, status, value, table, now);
        });
    }

    private PokerTable clockTable(MongoTemplate tx, Tournament event) {
        if (event != null && event.getStatus() == TournamentStatus.COMPLETED && event.getCompletion() != null) {
            var archived = tx.findById(event.getCompletion().finalTableId(), PokerTable.class);
            if (archived == null || !event.getId().equals(archived.getTournamentId()) || archived.getGameplay() == null) throw unavailable();
            return archived;
        }
        if (event == null || (event.getStatus() != TournamentStatus.RUNNING && event.getStatus() != TournamentStatus.PAUSED)
                || event.getInitialization() == null || event.getInitialization().tableId() == null) throw unavailable();
        var table = TournamentTableBalanceService.tables(tx, event).stream().filter(row -> row.getTournamentClosedAt() == null)
                .findFirst().orElse(null);
        if (table == null || !table.isTournament() || table.getGameplay() == null || !event.getId().equals(table.getTournamentId())) throw unavailable();
        return table;
    }

    private boolean hasActiveHand(MongoTemplate tx, Tournament event, UUID excluding) {
        return tx.exists(Query.query(Criteria.where("tournamentId").is(event.getId()).and("tournamentClosedAt").isNull()
                .and("gameplay.phase").is(Phase.ACTIVE).and("id").ne(excluding)), PokerTable.class);
    }

    private void pauseBetweenHands(MongoTemplate tx, PokerTable table) {
        table.recordTournamentHand(boundary(table.getGameplay(), Phase.PAUSED, null, TournamentBlindClockService.ADMIN));
        tx.save(table);
    }

    static TournamentHand boundary(TournamentHand hand, Phase phase, Instant next, String reason) {
        return new TournamentHand(hand.format(), hand.revision() + 1, hand.id(), hand.number(), hand.previousDealer(), hand.dealer(),
                hand.openingStacks(), hand.deck(), hand.moves(), phase, hand.street(), null, next,
                hand.activatedAt(), hand.settledAt(), reason, WsEventType.GAME_STATE, hand.blind());
    }

    public TableDetailResponse detail(UUID tableId, UUID userId) {
        return transactions.run(tx -> TableMapper.toDetail(assignedTable(tx, tableId, userId).table()));
    }

    public UUID subscribedTournament(UUID tableId, UUID userId) {
        return transactions.run(tx -> assignedTable(tx, tableId, userId).table().getTournamentId());
    }

    public boolean canSubscribe(UUID tableId, UUID userId) {
        try {
            transactions.run(tx -> assignedTable(tx, tableId, userId));
            return true;
        } catch (ApiException ignored) {
            return false;
        }
    }

    public TableStateResponse state(UUID tableId, UUID userId) {
        return transactions.run(tx -> {
            var seat = assignedTable(tx, tableId, userId);
            var hand = seat.table().getGameplay();
            if (seat.entrant().getStatus() == TournamentEntrantStatus.ELIMINATED && (hand.settledAt() == null
                    || seat.entrant().getElimination() == null || !hand.id().equals(seat.entrant().getElimination().handId()))) {
                throw eliminated();
            }
            return snapshot(tx, seat.table(), userId);
        });
    }

    public record Notice(long revision, WsEventType event) { }

    public Notice notice(UUID tableId) {
        var query = Query.query(Criteria.where("_id").is(tableId).and("gameMode").is("TOURNAMENT"));
        query.fields().include("gameplay.revision").include("gameplay.event");
        var table = mongo.findOne(query, Document.class, "poker_tables");
        var hand = table == null ? null : table.get("gameplay", Document.class);
        return hand == null ? null : new Notice(hand.get("revision", Number.class).longValue(), WsEventType.valueOf(hand.getString("event")));
    }

    public Notice clockNotice(UUID tournamentId) {
        var query = Query.query(Criteria.where("_id").is(tournamentId));
        query.fields().include("blindClock.version").include("blindClock.event");
        var tournament = mongo.findOne(query, Document.class, "tournaments");
        var value = tournament == null ? null : tournament.get("blindClock", Document.class);
        return value == null ? null : new Notice(value.get("version", Number.class).longValue(), WsEventType.valueOf(value.getString("event")));
    }

    public long topologyVersion(UUID tournamentId) {
        var query = Query.query(Criteria.where("_id").is(tournamentId));
        query.fields().include("topology.version");
        var tournament = mongo.findOne(query, Document.class, "tournaments");
        var topology = tournament == null ? null : tournament.get("topology", Document.class);
        return topology == null ? -1 : topology.get("version", Number.class).longValue();
    }

    public String finalizationStage(UUID tournamentId) {
        var query = Query.query(Criteria.where("_id").is(tournamentId));
        query.fields().include("finalization.stage");
        var event = mongo.findOne(query, Document.class, "tournaments");
        var finalization = event == null ? null : event.get("finalization", Document.class);
        return finalization == null ? null : finalization.getString("stage");
    }

    public void act(UUID tableId, UUID userId, GameActionRequest request) {
        var settled = transactions.run(tx -> {
            var seat = assignedTable(tx, tableId, userId);
            if (seat.entrant().getStatus() == TournamentEntrantStatus.ELIMINATED) throw eliminated();
            if (seat.tournamentStatus() == TournamentStatus.PAUSED) throw GameActionException.invalid("Tournament gameplay is paused.");
            var table = seat.table();
            var hand = table.getGameplay();
            if (request.requestId() == null || request.requestId().isBlank() || request.requestId().length() > 100
                    || request.actionVersion() == null || request.indices() != null) {
                throw GameActionException.invalid("A current turn and request ID are required.");
            }
            Instant now = clock.instant();
            if (hand.phase() != Phase.ACTIVE || request.actionVersion() != hand.revision() || hand.turnDeadline() == null
                    || !now.isBefore(hand.turnDeadline())) {
                throw GameActionException.invalid("This turn has expired. Wait for the current table state.");
            }
            if (hand.moves() != null && hand.moves().stream().anyMatch(move ->
                    request.requestId().equals(move.requestId()) && userId.equals(move.userId()))) {
                throw GameActionException.invalid("This action was already received.");
            }
            ActionType action;
            try {
                action = ActionType.valueOf(request.action().trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (RuntimeException bad) {
                throw GameActionException.invalid("Unknown action.");
            }
            int amount = request.amount() == null ? 0 : request.amount();
            move(tx, table, replay(table), new Move(userId, request.requestId(), action, amount), now);
            var checkpoint = table.getGameplay();
            if (checkpoint.settledAt() == null) return null;
            var standing = tx.findById(table.getTournamentId(), Tournament.class).getStandings();
            return new SettlementAudit(table.getTournamentId(), checkpoint.id(), standing.eliminatedEntrantIds().size());
        });
        if (settled != null) log.atInfo().addKeyValue("tournamentId", settled.tournamentId()).addKeyValue("tableId", tableId)
                .addKeyValue("handId", settled.handId()).addKeyValue("eliminatedPlayers", settled.eliminatedPlayers())
                .log("Tournament hand settled");
    }

    private record SettlementAudit(UUID tournamentId, UUID handId, int eliminatedPlayers) { }

    private record AssignedSeat(PokerTable table, TournamentEntrant entrant, TournamentStatus tournamentStatus) { }

    private AssignedSeat assignedTable(MongoTemplate tx, UUID id, UUID userId) {
        var table = tx.findById(id, PokerTable.class);
        if (table == null || !table.isTournament() || table.getGameplay() == null || table.getTournamentClosedAt() != null) throw unavailable();
        var tournament = tx.findById(table.getTournamentId(), Tournament.class);
        if (tournament == null || (tournament.getStatus() != TournamentStatus.RUNNING && tournament.getStatus() != TournamentStatus.PAUSED)
                || tournament.getInitialization() == null || !tournament.getInitialization().preparedTableIds().contains(id)) throw unavailable();
        var entrant = tx.findOne(entries(table.getTournamentId()).addCriteria(Criteria.where("userId").is(userId)), TournamentEntrant.class);
        if (entrant == null || !(entrant.getStatus() == TournamentEntrantStatus.ACTIVE || entrant.getStatus() == TournamentEntrantStatus.SEATED
                || entrant.getStatus() == TournamentEntrantStatus.ELIMINATED)
                || !id.equals(entrant.getTableId()) || entrant.getSeat() == null) throw unavailable();
        if (isMulti(tournament) && entrant.getStatus() == TournamentEntrantStatus.ELIMINATED) {
            return new AssignedSeat(table, entrant, tournament.getStatus());
        }
        var assignment = tx.findOne(entries(table.getTournamentId()).addCriteria(Criteria.where("entrantId").is(entrant.getId())),
                TournamentTableAssignment.class);
        if (assignment == null || !userId.equals(assignment.getUserId()) || assignment.getReleasedAt() != null
                || !id.equals(assignment.getTableId()) || entrant.getSeat() != assignment.getSeat()
                || table.getPlayers().stream().noneMatch(player -> userId.equals(player.getUserId()) && player.getSeat() == assignment.getSeat())) {
            throw unavailable();
        }
        return new AssignedSeat(table, entrant, tournament.getStatus());
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, "TOURNAMENT_TABLE_NOT_FOUND", "Tournament table is unavailable.");
    }

    private static ApiException eliminated() {
        return new ApiException(HttpStatus.CONFLICT, "TOURNAMENT_ELIMINATED", "This entrant has a final finishing position. Live standings are available.");
    }

    private void startHand(MongoTemplate tx, PokerTable table, Instant now, BlindLevel blind) {
        var previous = table.getGameplay();
        var stacks = table.getPlayers().stream().map(player -> new Stack(player.getUserId(), player.getChipStack())).toList();
        var engine = restoredEngine(table, stacks, previous == null ? 0 : previous.number(), previous == null ? -1 : previous.dealer(), blind);
        var deck = Deck.createDeck();
        deck.shuffle();
        var cards = deck.drawMultiple(Deck.STANDARD_SIZE);
        engine.startHand(Deck.ofCards(cards));
        var hand = new TournamentHand(JOURNAL_FORMAT, previous == null ? 1 : previous.revision() + 1, UUID.randomUUID(),
                engine.state().getHandNumber(), previous == null ? -1 : previous.dealer(), engine.state().getDealerSeat(),
                stacks, cards.stream().map(Card::code).toList(), List.of(), Phase.ACTIVE, engine.state().getStatus(), null, null,
                previous == null ? now : previous.activatedAt(), null, null,
                previous != null && !blind.equals(previous.appliedBlind(table.getInitialBlind())) ? WsEventType.BLINDS_APPLIED : WsEventType.HAND_STARTED, blind);
        commit(tx, table, engine, hand, now, true);
    }

    private void move(MongoTemplate tx, PokerTable table, TexasHoldemEngine engine, Move move, Instant now) {
        var current = table.getGameplay();
        int community = engine.state().getCommunityCards().size();
        apply(engine, move);
        var moves = new ArrayList<>(current.moves());
        moves.add(move);
        WsEventType event = engine.state().getCommunityCards().size() > community ? WsEventType.COMMUNITY_CARDS : WsEventType.PLAYER_ACTION;
        var hand = new TournamentHand(current.format(), current.revision() + 1, current.id(), current.number(), current.previousDealer(), current.dealer(),
                current.openingStacks(), current.deck(), List.copyOf(moves), Phase.ACTIVE, engine.state().getStatus(), null, null,
                current.activatedAt(), null, null, event, current.blind());
        commit(tx, table, engine, hand, now, false);
    }

    private void commit(MongoTemplate tx, PokerTable table, TexasHoldemEngine engine, TournamentHand hand, Instant now, boolean deal) {
        GameState state = engine.state();
        boolean settled = state.getStatus() == GameStatus.NEXT_HAND;
        verifySupply(hand, state);
        Phase phase = Phase.ACTIVE;
        String reason = null;
        Instant next = null;
        Instant deadline = null;
        Tournament tournament = null;
        TournamentStandingCheckpoint standings = null;
        WsEventType event = hand.event();
        if (settled) {
            // Acquire the tournament write fence before reading global counts or assigning unique ranks.
            tx.updateFirst(event(table.getTournamentId()), new Update().inc("registrationRevision", 1), Tournament.class);
            tournament = tx.findById(table.getTournamentId(), Tournament.class);
            standings = settle(tx, tournament, table, hand, state, now);
            var value = blinds.advance(blinds.current(tx, tournament, table, now), now);
            boolean draining = value.pauseRequestedAt() != null;
            boolean adminPause = draining && !hasActiveHand(tx, tournament, table.getId());
            boolean localShort = table.getPlayers().stream().filter(player -> player.getChipStack() > 0).count() < 2;
            boolean globalPause = standings.reviewRequired() || standings.candidate() != null || adminPause;
            reason = standings.reviewRequired() ? "ELIMINATION_REVIEW_REQUIRED"
                    : standings.candidate() != null ? "INSUFFICIENT_PLAYING_STACKS"
                    : draining ? TournamentBlindClockService.ADMIN : localShort ? TournamentTableBalanceService.WAITING : null;
            phase = reason == null ? Phase.BETWEEN_HANDS : Phase.PAUSED;
            next = reason == null ? now.plusSeconds(7) : null;
            if (globalPause) value = blinds.freeze(value, now, reason);
            // Even a normal settlement writes the fence so a concurrent pause request cannot miss this boundary.
            blinds.persist(tx, tournament.getId(), value, globalPause ? TournamentStatus.PAUSED : TournamentStatus.RUNNING, now);
            saveStanding(tx, tournament.getId(), standings);
            event = state.getShowdown().isEmpty() ? WsEventType.HAND_COMPLETED : WsEventType.SHOWDOWN;
        } else {
            if (state.currentPlayer() == null) throw new IllegalStateException("A live hand must have a player to act.");
            long animation = deal ? DealTiming.dealAnimationMs(state.getPlayers().size(), 2) : 0;
            deadline = now.plusMillis(animation + properties.getTurnSeconds() * 1000L);
        }
        table.recordTournamentHand(new TournamentHand(hand.format(), hand.revision(), hand.id(), hand.number(), hand.previousDealer(), hand.dealer(),
                hand.openingStacks(), hand.deck(), hand.moves(), phase, state.getStatus(), deadline, next,
                hand.activatedAt(), settled ? now : null, reason, event, hand.blind()));
        tx.save(table);
        if (settled) {
            boolean multi = isMulti(tournament);
            archive(tx, table, standings, multi);
            if (multi && standings.candidate() == null && !standings.reviewRequired()) balance.request(tx, tournament, now);
        }
    }

    private static void saveStanding(MongoTemplate tx, UUID id, TournamentStandingCheckpoint checkpoint) {
        tx.updateFirst(event(id), new Update().set("standings", checkpoint), Tournament.class);
    }

    private TournamentStandingCheckpoint settle(MongoTemplate tx, Tournament tournament, PokerTable table, TournamentHand hand, GameState state, Instant now) {
        var entrants = tx.find(entries(table.getTournamentId()), TournamentEntrant.class).stream()
                .collect(Collectors.toMap(TournamentEntrant::getUserId, Function.identity()));
        var opening = hand.openingStacks().stream().collect(Collectors.toMap(Stack::userId, Stack::chips));
        List<TournamentEliminationService.Settled> field = new ArrayList<>();
        for (var player : table.getPlayers()) {
            var entry = entrants.get(player.getUserId());
            var stack = state.player(player.getUserId().toString()).getChipStack();
            Integer openingStack = opening.get(player.getUserId());
            if (entry == null || !table.getId().equals(entry.getTableId()) || entry.getCurrentVirtualChips() != player.getChipStack()
                    || openingStack == null || openingStack != player.getChipStack()) {
                throw new IllegalStateException("Tournament settlement does not match its opening checkpoint.");
            }
            if (entry.getStatus() == TournamentEntrantStatus.ELIMINATED) {
                if (openingStack != 0 || stack != 0) throw new IllegalStateException("An eliminated entrant cannot hold chips.");
                continue;
            }
            long delta = stack - entry.getCurrentVirtualChips();
            var ledger = tx.insert(new TournamentChipLedgerEntry(table.getTournamentId(), entry.getId(), TournamentChipLedgerEntry.EntryType.HAND_RESULT,
                    delta, stack, "hand:" + hand.id() + ":" + entry.getId(), table.getId()));
            entry.settleStack(stack, now);
            field.add(new TournamentEliminationService.Settled(entry, openingStack, ledger.getId()));
            player.setChipStack(stack);
        }
        int others = isMulti(tournament) ? Math.toIntExact(tx.count(entries(tournament.getId())
                .addCriteria(Criteria.where("tableId").ne(table.getId()).and("currentVirtualChips").gt(0)), TournamentEntrant.class)) : 0;
        int sequence = tournament.getStandings() == null ? hand.number() : tournament.getStandings().completedHands() + 1;
        var standings = eliminations.rank(hand, field, others, sequence, now);
        for (var row : field) tx.save(row.entrant());
        return standings;
    }

    private static void archive(MongoTemplate tx, PokerTable table, TournamentStandingCheckpoint standings, boolean vacateEliminated) {
        var vacated = entries(table.getTournamentId()).addCriteria(Criteria.where("entrantId").in(standings.eliminatedEntrantIds()));
        var previous = !vacateEliminated || standings.eliminatedEntrantIds().isEmpty() ? List.<TournamentSettledHand.VacatedSeat>of()
                : tx.find(vacated, TournamentTableAssignment.class).stream().map(row -> new TournamentSettledHand.VacatedSeat(
                        row.getId(), row.getEntrantId(), row.getUserId(), row.getSeat(), row.getAssignedAt(), row.getAssignmentVersion())).toList();
        var seats = table.getPlayers().stream()
                .map(row -> new TournamentSettledHand.Seat(row.getUserId(), row.getSeat(), row.getChipStack())).toList();
        tx.insert(new TournamentSettledHand(table.getGameplay().id(), table.getTournamentId(), table.getId(), table.getGameplay(), seats, previous));
        if (!previous.isEmpty()) tx.remove(vacated, TournamentTableAssignment.class);
    }

    private TableStateResponse snapshot(MongoTemplate tx, PokerTable table, UUID userId) {
        if (table.isTournamentRosterChanged()) {
            var hand = table.getGameplay();
            var stacks = table.getPlayers().stream().map(player -> new Stack(player.getUserId(), player.getChipStack())).toList();
            var engine = restoredEngine(table, stacks, hand.number(), hand.dealer(), hand.appliedBlind(table.getInitialBlind()));
            return TableMapper.toSanitizedState(table, engine, userId);
        }
        try {
            return TableMapper.toSanitizedState(table, replay(table), userId, table.getGameplay().turnDeadline());
        } catch (UnreplayableHand bad) {
            var hand = table.getGameplay();
            if (!"HAND_RECOVERY_REQUIRED".equals(hand.reason())) pause(tx, table, clock.instant());
            var stacks = table.getPlayers().stream().map(player -> new Stack(player.getUserId(), player.getChipStack())).toList();
            var engine = restoredEngine(table, stacks, Math.max(0, hand.number() - (hand.settledAt() == null ? 1 : 0)), -1, table.getInitialBlind());
            return TableMapper.toSanitizedState(table, engine, userId);
        }
    }

    private void pause(MongoTemplate tx, PokerTable table, Instant now) {
        var tournament = tx.findById(table.getTournamentId(), Tournament.class);
        var value = blinds.advance(blinds.current(tx, tournament, table, now), now);
        table.recordTournamentHand(boundary(table.getGameplay(), Phase.PAUSED, null, "HAND_RECOVERY_REQUIRED"));
        tx.save(table);
        blinds.persist(tx, table.getTournamentId(), blinds.freeze(value, now, "HAND_RECOVERY_REQUIRED"), TournamentStatus.PAUSED, now);
    }

    private TexasHoldemEngine replay(PokerTable table) {
        var hand = table.getGameplay();
        try {
            if ((hand.format() != 1 && hand.format() != JOURNAL_FORMAT) || hand.deck().size() != Deck.STANDARD_SIZE
                    || new HashSet<>(hand.deck()).size() != Deck.STANDARD_SIZE) throw new IllegalStateException("Invalid hand journal.");
            var engine = restoredEngine(table, hand.openingStacks(), hand.number() - 1, hand.previousDealer(), hand.appliedBlind(table.getInitialBlind()));
            engine.startHand(Deck.ofCards(hand.deck().stream().map(Card::parse).toList()));
            for (var move : hand.moves()) apply(engine, move);
            if (engine.state().getDealerSeat() != hand.dealer() || engine.state().getStatus() != hand.street()) {
                throw new IllegalStateException("Hand journal replay disagrees with the checkpoint.");
            }
            boolean settled = hand.settledAt() != null;
            if (settled != (engine.state().getStatus() == GameStatus.NEXT_HAND)
                    || (hand.phase() == Phase.ACTIVE && (settled || hand.turnDeadline() == null))
                    || (hand.phase() == Phase.BETWEEN_HANDS && (!settled || hand.nextHandAt() == null))) {
                throw new IllegalStateException("Hand progress disagrees with the journal.");
            }
            for (var player : table.getPlayers()) {
                int expected = settled ? engine.state().player(player.getUserId().toString()).getChipStack()
                        : hand.openingStacks().stream().filter(stack -> stack.userId().equals(player.getUserId())).findFirst().orElseThrow().chips();
                if (player.getChipStack() != expected) throw new IllegalStateException("Hand stack checkpoint differs from the journal.");
            }
            verifySupply(hand, engine.state());
            return engine;
        } catch (RuntimeException invalid) {
            throw new UnreplayableHand(invalid);
        }
    }

    private static void verifySupply(TournamentHand hand, GameState state) {
        long opening = hand.openingStacks().stream().mapToLong(Stack::chips).sum();
        long current = state.getPlayers().stream().mapToLong(player -> player.getChipStack()).sum();
        if (opening != current + state.getPot() || state.getPlayers().stream().anyMatch(player -> player.getChipStack() < 0)) {
            io.micrometer.core.instrument.Metrics.counter("poker.tournament.settlement.integrity.failures").increment();
            log.atError().addKeyValue("handId", hand.id()).log("Tournament virtual chip conservation failed");
            throw new IllegalStateException("Tournament hand does not conserve virtual chips.");
        }
    }

    private static TexasHoldemEngine restoredEngine(PokerTable table, List<Stack> stacks, int hand, int dealer, BlindLevel blind) {
        var config = new TableConfig(blind.smallBlind(), blind.bigBlind(), table.getStartingChips(), table.getMaxPlayers(), 5, blind.ante());
        var engine = new TexasHoldemEngine(table.getId().toString(), config);
        for (PokerTablePlayer player : table.getPlayers()) {
            int chips = stacks.stream().filter(stack -> player.getUserId().equals(stack.userId())).findFirst().orElseThrow().chips();
            engine.restoreSeat(player.getUserId().toString(), player.getUsername(), player.getSeat(), chips);
        }
        engine.restoreHandBoundary(hand, dealer);
        return engine;
    }

    private static void apply(TexasHoldemEngine engine, Move move) {
        engine.apply(new PlayerAction(move.userId().toString(), move.action(), move.amount()));
    }

    private static Query entries(UUID tournamentId) { return Query.query(Criteria.where("tournamentId").is(tournamentId)); }
    private static Query event(UUID id) { return Query.query(Criteria.where("id").is(id)); }

    private static class UnreplayableHand extends ApiException {
        private UnreplayableHand(Throwable cause) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "HAND_RECOVERY_REQUIRED", "This hand is paused for recovery.");
            initCause(cause);
        }
    }
}
