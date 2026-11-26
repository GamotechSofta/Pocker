package com.poker.tournament;

import com.mongodb.client.MongoClient;
import com.poker.config.TableProperties;
import com.poker.dto.AuthResponse;
import com.poker.dto.GameActionRequest;
import com.poker.dto.TableStateResponse;
import com.poker.entity.PokerTable;
import com.poker.exception.ApiException;
import com.poker.game.engine.GameActionException;
import com.poker.game.model.Card;
import com.poker.game.model.Deck;
import com.poker.repository.UserRepository;
import com.poker.support.AbstractMongoTest;
import com.poker.tournament.dto.TournamentAssignment;
import com.poker.tournament.dto.TournamentResults;
import com.poker.tournament.entity.*;
import com.poker.tournament.repository.TournamentBlindScheduleRepository;
import com.poker.tournament.repository.TournamentEntrantRepository;
import com.poker.tournament.repository.TournamentRepository;
import com.poker.tournament.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TournamentGameplayIntegrationTest extends AbstractMongoTest {
    @LocalServerPort private int port;
    @Autowired private MongoTemplate mongo;
    @Autowired private MongoClient clientConnection;
    @Autowired private TournamentRepository tournaments;
    @Autowired private TournamentBlindScheduleRepository schedules;
    @Autowired private TournamentEntrantRepository entrants;
    @Autowired private UserRepository users;
    @Autowired private TournamentTransactions transactions;
    @Autowired private TournamentSeatingService seating;
    @Autowired private TournamentAssignmentService assignments;
    @Autowired private TableProperties properties;
    @Autowired private TournamentStandingsService standings;
    @Autowired private TournamentResultsService resultViews;

    @Test
    void activationAndFirstMoveAreSingleCommitsAcrossWorkers() throws Exception {
        var f = prepare(6);
        var first = worker(f.now());
        var peer = worker(f.now());
        assertEquals(TournamentStatus.STARTING, tournament(f).getStatus());
        assertNull(table(f).getGameplay());
        assertEquals(2, race(() -> first.activate(f.id()), () -> peer.activate(f.id())));
        assertEquals(TournamentStatus.RUNNING, tournament(f).getStatus());
        assertEquals(1, mongo.count(tournamentRows(f), PokerTable.class));
        assertEquals(6, mongo.count(tournamentRows(f), TournamentTableAssignment.class));
        assertEquals(6, ledger(f, TournamentChipLedgerEntry.EntryType.INITIAL_STACK));
        var snapshot = state(first, f);
        assertEquals(1, snapshot.handNumber());
        assertEquals(new TournamentGameplayService.Notice(1, com.poker.websocket.WsEventType.HAND_STARTED), first.notice(f.table()));
        assertEquals(45, snapshot.pot());
        assertEquals(9000, supply(snapshot));
        assertEquals(0, snapshot.dealerSeat());
        assertEquals(1, snapshot.smallBlindSeat());
        assertEquals(2, snapshot.bigBlindSeat());
        UUID actor = actor(snapshot);
        var request = request("CALL", snapshot);
        assertThrows(GameActionException.class, () -> first.act(f.table(), f.players().stream()
                .map(player -> player.user().id()).filter(id -> !id.equals(actor)).findFirst().orElseThrow(), request));
        assertEquals(1, race(() -> first.act(f.table(), actor, request), () -> peer.act(f.table(), actor, request)));
        assertEquals(1, table(f).getGameplay().moves().size());
        assertEquals(2, table(f).getGameplay().revision());
        assertThrows(GameActionException.class, () -> peer.act(f.table(), actor, request("FOLD", snapshot)));
        assertEquals(9000, supply(state(peer, f)));
    }

    @Test
    void activeHandRestoresPrivateStateAndSettlementAbortsWithoutPartialWrites() {
        var f = prepare(2);
        var abort = new TournamentTransactions(clientConnection, mongo) {
            @Override public <T> T run(Function<MongoTemplate, T> action) {
                return super.run(tx -> {
                    action.apply(tx);
                    throw new IllegalStateException("Injected failure before transaction commit");
                });
            }
        };
        var failing = new TournamentGameplayService(mongo, abort, seating, properties, Clock.fixed(f.now(), ZoneOffset.UTC), new TournamentBlindClockService(), new TournamentEliminationService());
        assertThrows(IllegalStateException.class, () -> failing.activate(f.id()));
        assertEquals(TournamentStatus.STARTING, tournament(f).getStatus());
        assertNull(table(f).getGameplay());
        var worker = worker(f.now());
        worker.activate(f.id());
        var before = state(worker, f);
        assertEquals(before.dealerSeat(), before.smallBlindSeat());
        assertEquals(before.dealerSeat(), before.currentTurnSeat());
        UUID actor = actor(before);
        var own = worker.state(f.table(), actor);
        assertEquals(2, own.players().stream().filter(p -> p.playerId().equals(actor)).findFirst().orElseThrow().holeCards().size());
        assertTrue(own.players().stream().filter(p -> !p.playerId().equals(actor)).allMatch(p -> p.holeCards().equals(List.of("HIDDEN", "HIDDEN"))));
        assertEquals(own, worker(f.now().plusSeconds(1)).state(f.table(), actor));
        var commit = request("FOLD", before);
        assertThrows(IllegalStateException.class, () -> failing.act(f.table(), actor, commit));
        assertEquals(own, worker.state(f.table(), actor));
        assertEquals(0, ledger(f, TournamentChipLedgerEntry.EntryType.HAND_RESULT));
        worker.act(f.table(), actor, commit);
        var settled = state(worker, f);
        assertEquals(3000, supply(settled));
        assertEquals(0, settled.pot());
        assertEquals(2, ledger(f, TournamentChipLedgerEntry.EntryType.HAND_RESULT));
        assertThrows(GameActionException.class, () -> worker.act(f.table(), actor, commit));
        assertEquals(2, ledger(f, TournamentChipLedgerEntry.EntryType.HAND_RESULT));
        worker(f.now().plusSeconds(8)).advance(f.table());
        var next = state(worker, f);
        assertEquals(2, next.handNumber());
        assertNotEquals(before.dealerSeat(), next.dealerSeat());
        assertEquals(3000, supply(next));
    }

    @Test
    void unequalAllInsSplitMainAndSidePotsWithoutExpandingSupply() {
        var f = prepare(3);
        var first = worker(f.now());
        first.activate(f.id());
        play(first, f, "FOLD");
        play(first, f, "FOLD");
        var second = worker(f.now().plusSeconds(8));
        second.advance(f.table());
        assertEquals(List.of(1500, 1485, 1515), table(f).getGameplay().openingStacks().stream().map(TournamentHand.Stack::chips).toList());
        rig(f, "2C 3C 4C 5C 6C 7C 8C TH JH QH 9C KH TC AH");
        play(second, f, "ALL_IN");
        play(second, f, "ALL_IN");
        play(second, f, "CALL");
        var state = state(second, f);
        assertEquals(5, state.communityCards().size());
        assertEquals(3, state.showdown().size());
        assertEquals(3, state.payouts().size());
        assertEquals(List.of(1500, 1485, 1515), table(f).getPlayers().stream().map(player -> player.getChipStack()).toList());
        assertEquals(4500, supply(state));
        assertEquals(6, ledger(f, TournamentChipLedgerEntry.EntryType.HAND_RESULT));
        assertEquals(0, standings.admin(f.id(), 0, 50).statistics().eliminatedPlayers());
        assertTrue(state.showdown().stream().filter(row -> !row.playerId().equals(state.viewerPlayerId())).allMatch(row -> row.holeCards().isEmpty()));
    }

    @Test
    void bustPausesFutureHandsAndTournamentRoutesRejectUnassignedAndCashOperations() {
        var f = prepare(2);
        var worker = worker(f.now());
        worker.activate(f.id());
        rig(f, "2C AS 3D AH 5C 8H 9D TS 6C QD 7C KC");
        play(worker, f, "ALL_IN");
        play(worker, f, "CALL");
        var state = state(worker, f);
        assertEquals("PAUSED", state.tournament().phase());
        assertEquals("INSUFFICIENT_PLAYING_STACKS", state.tournament().reason());
        assertEquals(TournamentStatus.PAUSED, tournament(f).getStatus());
        assertEquals(List.of(0, 3000), state.players().stream().map(p -> p.chipStack()).sorted().toList());
        assertTrue(state.players().stream().anyMatch(p -> p.status().equals("SITTING_OUT")));
        worker(f.now().plusSeconds(60)).advance(f.table());
        assertEquals(1, state(worker, f).handNumber());
        var outsider = guest();
        assertThrows(ApiException.class, () -> worker.state(f.table(), outsider.user().id()));
        var cash = "/api/tables/" + f.table();
        for (String operation : List.of("/join", "/leave", "/start")) {
            assertEquals(404, assertThrows(RestClientResponseException.class, () -> http().post().uri(cash + operation)
                    .headers(h -> h.setBearerAuth(f.players().getFirst().token())).retrieve().toBodilessEntity()).getStatusCode().value());
        }
        assertTrue(worker.canSubscribe(f.table(), f.players().getFirst().user().id()));
        assertFalse(worker.canSubscribe(f.table(), outsider.user().id()));
    }

    @Test
    void incompatibleHandJournalPausesAtTheLastSettledCheckpoint() {
        var f = prepare(2);
        var worker = worker(f.now());
        worker.activate(f.id());
        mongo.updateFirst(tableById(f), new Update().set("gameplay.format", 99), PokerTable.class);
        var safe = state(worker, f);
        assertEquals("HAND_RECOVERY_REQUIRED", safe.tournament().reason());
        assertTrue(safe.players().stream().allMatch(p -> p.holeCards().isEmpty() && p.chipStack() == 1500));
        worker(f.now().plusSeconds(properties.getTurnSeconds() + 10)).advance(f.table());
        assertEquals(TournamentStatus.PAUSED, tournament(f).getStatus());
        assertThrows(ApiException.class, () -> worker.pauseClock(f.id(), false));
        assertEquals(0, ledger(f, TournamentChipLedgerEntry.EntryType.HAND_RESULT));
        assertEquals(1, table(f).getGameplay().number());
    }

    @Test
    void initialAnteContributesToPotWithoutReducingTheAmountToCallAndTimeoutWorks() {
        var f = prepare(2, 5);
        var worker = worker(f.now());
        worker.activate(f.id());
        var state = state(worker, f);
        var turn = worker.state(f.table(), actor(state));
        assertEquals(55, turn.pot());
        assertEquals(15, turn.legalActions().callAmount());
        assertEquals(3000, supply(turn));
        worker(turn.turnDeadline().plusMillis(1)).advance(f.table());
        assertEquals(0, state(worker, f).pot());
        assertEquals(2, ledger(f, TournamentChipLedgerEntry.EntryType.HAND_RESULT));
        assertEquals(3000, supply(state(worker, f)));
    }

    @Test
    void exactBoundariesAndCompetingWorkersKeepTheActiveBlindsAndApplyTheLatestLevelOnce() throws Exception {
        var f = prepare(2, List.of(new BlindLevel(1, 15, 30, 0, 3, true),
                new BlindLevel(2, 25, 50, 0, 5, false), new BlindLevel(3, 50, 100, 0, 7, false)));
        var start = worker(f.now());
        assertNull(tournament(f).getBlindClock());
        start.activate(f.id());
        var initial = start.clock(f.id(), f.players().getFirst().user().id());
        assertEquals(f.now(), initial.startedAt());
        assertEquals(3000, initial.remainingMillis());
        assertFalse(initial.breaksSupported());
        assertTrue(initial.levels().getFirst().breakAfter());
        var skipped = new TournamentBlindClockService().advance(tournament(f).getBlindClock(), f.now().plusSeconds(11));
        assertEquals(3, skipped.scheduledLevel());
        assertEquals(2, skipped.version());
        assertEquals(f.now().plusSeconds(8), skipped.levelStartedAt());
        var first = state(start, f);
        worker(f.now().plusMillis(2999)).advanceClock(f.id());
        assertEquals(1, tournament(f).getBlindClock().scheduledLevel());
        var levelTwo = worker(f.now().plusSeconds(3));
        assertEquals(2, race(() -> levelTwo.advanceClock(f.id()), () -> worker(f.now().plusSeconds(3)).advanceClock(f.id())));
        var pending = levelTwo.clock(f.id(), f.players().getFirst().user().id());
        assertEquals(2, pending.version());
        assertEquals(5000, pending.remainingMillis());
        assertTrue(pending.pending());
        assertEquals(first, state(levelTwo, f));
        var delayed = worker(f.now().plusSeconds(11));
        delayed.advanceClock(f.id());
        assertEquals(f.now().plusSeconds(8), tournament(f).getBlindClock().levelStartedAt());
        assertEquals(first, state(delayed, f));
        play(delayed, f, "FOLD");
        var next = worker(f.now().plusSeconds(19));
        assertEquals(2, race(() -> next.advance(f.table()), () -> worker(f.now().plusSeconds(19)).advance(f.table())));
        var actual = state(next, f);
        assertEquals(2, actual.handNumber());
        assertEquals(3, actual.tournament().blindLevel());
        assertEquals(150, actual.pot());
        assertEquals(50, actual.smallBlind());
        assertEquals(100, actual.bigBlind());
        assertEquals(3000, supply(actual));
        var finalLevel = next.clock(f.id(), null);
        assertTrue(finalLevel.finalLevel());
        assertNull(finalLevel.remainingMillis());
        assertNull(finalLevel.next());
        assertFalse(finalLevel.pending());
        worker(f.now().plusSeconds(1000)).advanceClock(f.id());
        assertEquals(3, tournament(f).getBlindClock().scheduledLevel());
        assertEquals(TournamentStatus.RUNNING, tournament(f).getStatus());
    }

    @Test
    void pauseDrainsTheActiveHandAndPreservesRemainingTimeAcrossWorkers() {
        var f = prepare(2, List.of(new BlindLevel(1, 15, 30, 0, 20, false), new BlindLevel(2, 30, 60, 0, 25, false)));
        worker(f.now()).activate(f.id());
        var worker = worker(f.now().plusSeconds(3));
        var pending = worker.pauseClock(f.id(), true);
        assertEquals("PAUSE_PENDING", pending.status());
        assertEquals(pending.version(), worker.pauseClock(f.id(), true).version());
        assertThrows(ApiException.class, () -> worker.pauseClock(f.id(), false));
        var hand = state(worker, f);
        assertEquals(45, hand.pot());
        play(worker(f.now().plusSeconds(5)), f, "FOLD");
        var restarted = worker(f.now().plusSeconds(35));
        restarted.advance(f.table());
        var frozen = restarted.clock(f.id(), null);
        assertEquals("PAUSED", frozen.status());
        assertEquals(15_000, frozen.remainingMillis());
        assertEquals(5000, frozen.elapsedActiveMillis());
        assertEquals(30_000, frozen.accumulatedPausedMillis());
        assertEquals(1, state(restarted, f).handNumber());
        var resumed = restarted.pauseClock(f.id(), false);
        assertEquals("ACTIVE", resumed.status());
        assertEquals(15_000, resumed.remainingMillis());
        assertEquals(resumed.version(), restarted.pauseClock(f.id(), false).version());
        assertEquals(f.now().plusSeconds(50), resumed.levelEndsAt());
        assertEquals(1, resumed.scheduled().levelNumber());
        worker(f.now().plusSeconds(36)).advance(f.table());
        assertEquals(2, state(restarted, f).handNumber());
    }

    @Test
    void phaseSevenJournalReplaysAndCannotBeChangedThroughPlayerClockRoutes() {
        var f = prepare(2, List.of(new BlindLevel(1, 15, 30, 0, 3, false), new BlindLevel(2, 25, 50, 0, 5, false)));
        var start = worker(f.now());
        start.activate(f.id());
        var before = state(start, f);
        mongo.updateFirst(tableById(f), new Update().set("gameplay.format", 1).unset("gameplay.blind"), PokerTable.class);
        mongo.updateFirst(Query.query(Criteria.where("id").is(f.id())), new Update().unset("blindClock"), Tournament.class);
        var restarted = worker(f.now().plusSeconds(4));
        restarted.advanceClock(f.id());
        assertEquals(before, state(restarted, f));
        assertEquals(2, restarted.clock(f.id(), f.players().getFirst().user().id()).scheduled().levelNumber());
        assertThrows(ApiException.class, () -> restarted.clock(f.id(), guest().user().id()));
        var unauthorized = assertThrows(RestClientResponseException.class, () -> http().post()
                .uri("/api/admin/tournaments/" + f.id() + "/clock/pause")
                .headers(h -> h.setBearerAuth(f.players().getFirst().token())).retrieve().toBodilessEntity());
        assertEquals(403, unauthorized.getStatusCode().value());
    }

    @Test
    void sidePotBustsAreRankedByOpeningStackAndRollbackWithSettlement() {
        var f = prepare(4);
        var first = worker(f.now());
        first.activate(f.id());
        play(first, f, "FOLD");
        play(first, f, "FOLD");
        play(first, f, "FOLD");
        var time = f.now().plusSeconds(8);
        var second = worker(time);
        second.advance(f.table());
        rig(f, "AS 4C 2D 6C AH 5D 3H 7D 8H 9D TS QS 3C KC 4S 2C");
        UUID larger = playerAt(f, 0);
        UUID smaller = playerAt(f, 1);
        UUID winner = playerAt(f, 2);
        UUID folder = playerAt(f, 3);
        play(second, f, "ALL_IN");
        assertEquals(0, second.state(f.table(), larger).players().getFirst().chipStack());
        assertEquals(1500, standings.player(f.id(), larger, 0, 50).viewer().settledStack());
        assertEquals(0, standings.player(f.id(), larger, 0, 50).statistics().eliminatedPlayers());
        play(second, f, "CALL");
        play(second, f, "CALL");
        var finish = request("FOLD", state(second, f));
        assertEquals(folder, actor(state(second, f)));
        var failCommit = new TournamentTransactions(clientConnection, mongo) {
            @Override public <T> T run(Function<MongoTemplate, T> work) {
                return super.run(tx -> { work.apply(tx); throw new IllegalStateException("Injected after ranking before commit"); });
            }
        };
        var failing = new TournamentGameplayService(mongo, failCommit, seating, properties, Clock.fixed(time, ZoneOffset.UTC),
                new TournamentBlindClockService(), new TournamentEliminationService());
        assertThrows(IllegalStateException.class, () -> failing.act(f.table(), folder, finish));
        assertNull(entrant(f, larger).getFinishPosition());
        assertNull(entrant(f, smaller).getFinishPosition());
        assertEquals(4, ledger(f, TournamentChipLedgerEntry.EntryType.HAND_RESULT));
        var replacement = worker(time.plusSeconds(1));
        replacement.act(f.table(), folder, finish);
        assertThrows(GameActionException.class, () -> replacement.act(f.table(), folder, finish));
        assertEquals(8, ledger(f, TournamentChipLedgerEntry.EntryType.HAND_RESULT));
        assertEquals(3, entrant(f, larger).getFinishPosition());
        assertEquals(4, entrant(f, smaller).getFinishPosition());
        assertEquals(1500, entrant(f, larger).getElimination().openingStack());
        assertEquals(1485, entrant(f, smaller).getElimination().openingStack());
        var evidence = entrant(f, smaller).getElimination();
        assertEquals(table(f).getGameplay().id(), evidence.handId());
        var ledger = mongo.findById(evidence.settlementId(), TournamentChipLedgerEntry.class);
        assertEquals(entrant(f, smaller).getId(), ledger.getEntrantId());
        assertEquals(0, ledger.getResultingBalance());
        assertThrows(IllegalStateException.class, () -> entrant(f, smaller).reconnect());
        var results = standings.player(f.id(), smaller, 0, 50);
        assertEquals(2, results.statistics().activePlayers());
        assertEquals(2, results.statistics().eliminatedPlayers());
        assertEquals(6000, results.statistics().settledActiveStacks());
        assertEquals(4, results.viewer().finishPosition());
        assertEquals(List.of(3, 4), results.recentEliminations().stream().map(row -> row.finishPosition()).toList());
        var nextWorker = worker(time.plusSeconds(9));
        nextWorker.advance(f.table());
        var headsUp = nextWorker.state(f.table(), winner);
        assertEquals(3, headsUp.handNumber());
        assertEquals(2, headsUp.dealerSeat());
        assertEquals(2, headsUp.smallBlindSeat());
        assertEquals(3, headsUp.bigBlindSeat());
        assertEquals(6000, supply(headsUp));
        assertEquals("TOURNAMENT_ELIMINATED", assertThrows(ApiException.class,
                () -> worker(time.plusSeconds(10)).act(f.table(), smaller, request("FOLD", headsUp))).getCode());
        assertEquals("TOURNAMENT_ELIMINATED", assertThrows(ApiException.class,
                () -> worker(time.plusSeconds(10)).state(f.table(), smaller)).getCode());
    }

    @Test
    void equalStacksUseAssignedSeatsAndLastPlayerFreezesClockWithoutFinalResults() {
        var f = prepare(3, List.of(new BlindLevel(1, 15, 30, 0, 30, false), new BlindLevel(2, 25, 50, 0, 40, false)));
        var start = worker(f.now());
        start.activate(f.id());
        rig(f, "2C 6D AS 3D 7C AH 5C 8C JH 9D 2H QD 3C 4H");
        var initial = standings.admin(f.id(), 0, 50);
        assertEquals(List.of(0, 1, 2), initial.rows().stream().map(row -> row.seat()).toList());
        assertEquals(List.of(1, 2, 3), initial.rows().stream().map(row -> row.activeStanding()).toList());
        assertTrue(initial.rows().stream().allMatch(row -> row.finishPosition() == null));
        play(start, f, "ALL_IN");
        play(start, f, "CALL");
        play(start, f, "CALL");
        assertEquals(2, entrant(f, playerAt(f, 1)).getFinishPosition());
        assertEquals(3, entrant(f, playerAt(f, 2)).getFinishPosition());
        assertEquals(playerAt(f, 0), tournament(f).getStandings().candidate().userId());
        assertEquals(4500, tournament(f).getStandings().candidate().settledStack());
        assertEquals(TournamentStatus.PAUSED, tournament(f).getStatus());
        assertEquals(0, mongo.count(tournamentRows(f), TournamentFinalResult.class));
        var later = worker(f.now().plusSeconds(120));
        later.advance(f.table());
        later.advanceClock(f.id());
        assertEquals(1, state(later, f).handNumber());
        assertEquals(1, later.clock(f.id(), null).scheduled().levelNumber());
        assertThrows(ApiException.class, () -> later.pauseClock(f.id(), false));
        var limited = standings.admin(f.id(), 1, 1);
        assertEquals(3, limited.totalPages());
        assertEquals(2, limited.rows().getFirst().finishPosition());
    }

    @Test
    void privateStandingsRemainAvailableToBustedMembersAndNotOutsiders() {
        var f = prepare(2, List.of(new BlindLevel(1, 15, 30, 0, 300, false)), "PRIVATE-" + UUID.randomUUID());
        var worker = worker(f.now());
        worker.activate(f.id());
        rig(f, "2C AS 3D AH 5C 8H 9D TS 6C QD 7C KC");
        play(worker, f, "ALL_IN");
        play(worker, f, "CALL");
        var busted = f.players().stream().filter(player -> player.user().id().equals(playerAt(f, 1))).findFirst().orElseThrow();
        var data = http().get().uri("/api/tournaments/" + f.id() + "/leaderboard").headers(h -> h.setBearerAuth(busted.token()))
                .retrieve().body(com.poker.tournament.dto.TournamentLeaderboard.class);
        assertEquals(2, data.viewer().finishPosition());
        assertEquals(TournamentEntrantStatus.ELIMINATED, data.viewer().status());
        assertEquals(TournamentAssignment.Stage.ELIMINATED, assignments.get(f.id(), busted.user().id()).stage());
        assertEquals(404, assertThrows(RestClientResponseException.class, () -> http().get().uri("/api/tournaments/" + f.id() + "/leaderboard")
                .headers(h -> h.setBearerAuth(guest().token())).retrieve().toBodilessEntity()).getStatusCode().value());
        assertThrows(ApiException.class, () -> standings.admin(f.id(), 0, 51));
    }

    @Test
    void scheduledMttSeatsUnevenFieldsAndRunsIndependentHandsWithSharedClock() throws Exception {
        var f = prepareMulti(14, 18);
        var left = worker(f.now());
        var right = worker(f.now());
        assertEquals(List.of(5, 5, 4), multiTables(f).stream().map(table -> table.getPlayers().size()).toList());
        assertEquals(2, race(() -> left.activate(f.id()), () -> right.activate(f.id())));
        var tables = multiTables(f);
        assertEquals(3, tables.stream().map(table -> table.getGameplay().id()).distinct().count());
        assertEquals(3, tables.stream().map(table -> table.getGameplay().deck()).distinct().count());
        assertEquals(21000, tables.stream().mapToLong(table -> supply(left.state(table.getId(), member(table)))).sum());
        assertEquals(14, mongo.count(tournamentRows(f), TournamentTableAssignment.class));
        assertEquals(14, ledger(f, TournamentChipLedgerEntry.EntryType.INITIAL_STACK));
        assertThrows(ApiException.class, () -> left.state(tables.getFirst().getId(), member(tables.getLast())));
        assertFalse(left.canSubscribe(tables.getFirst().getId(), member(tables.getLast())));
        var a = tables.getFirst();
        var b = tables.getLast();
        var actual = left.state(b.getId(), member(b));
        foldTable(left, a);
        var later = worker(f.now().plusSeconds(9));
        later.advanceClock(f.id());
        later.advance(a.getId());
        assertEquals(actual, later.state(b.getId(), member(b)));
        var clockA = later.clock(f.id(), member(a));
        var clockB = later.clock(f.id(), member(b));
        assertEquals(clockA.startedAt(), clockB.startedAt());
        assertEquals(clockA.version(), clockB.version());
        assertEquals(2, clockA.scheduled().levelNumber());
        assertEquals(2, clockA.applied().levelNumber());
        assertEquals(1, clockB.applied().levelNumber());
        assertEquals(3, standings.admin(f.id(), 0, 50).activeTables());
        assertEquals(21000, standings.admin(f.id(), 0, 50).statistics().settledActiveStacks());

        assertEquals("PAUSE_PENDING", later.pauseClock(f.id(), true).status());
        foldTable(later, a);
        assertEquals("PAUSE_PENDING", later.clock(f.id(), member(b)).status());
        foldTable(later, tables.get(1));
        foldTable(later, b);
        assertEquals("PAUSED", later.clock(f.id(), member(b)).status());
        assertEquals("ACTIVE", later.pauseClock(f.id(), false).status());
    }

    @Test
    void simultaneousTableSettlementsRankGloballyAndReservedTransferRollsBackAtomically() throws Exception {
        var f = prepareMulti(18, 18);
        var first = worker(f.now());
        first.activate(f.id());
        var tables = multiTables(f);
        prepareBust(first, tables.get(0), Set.of(3, 4));
        prepareBust(first, tables.get(1), Set.of(0, 3, 4, 5));
        var a = first.state(tables.get(0).getId(), bySeat(tables.get(0), 4));
        var b = first.state(tables.get(1).getId(), bySeat(tables.get(1), 4));
        var left = worker(f.now());
        var right = worker(f.now());
        assertEquals(2, race(() -> left.act(a.tableId(), actor(a), request("FOLD", a)),
                () -> right.act(b.tableId(), actor(b), request("FOLD", b))));
        var board = standings.admin(f.id(), 0, 50);
        assertEquals(List.of(15, 16, 17, 18), board.rows().stream().filter(row -> row.finishPosition() != null)
                .map(row -> row.finishPosition()).toList());
        assertEquals(14, board.statistics().activePlayers());
        assertEquals(2, board.statistics().completedHands());
        assertNull(board.candidateEntrantId());
        assertEquals(27000, board.statistics().settledActiveStacks());
        var recorded = mongo.find(tournamentRows(f).addCriteria(Criteria.where("finishPosition").gte(2)), TournamentEntrant.class)
                .stream().sorted(Comparator.comparing(TournamentEntrant::getFinishPosition).reversed()).toList();
        var sequence = recorded.stream().map(entry -> entry.getElimination().settlementSequence()).toList();
        assertEquals(sequence.stream().sorted().toList(), sequence);
        assertEquals(Set.of(1L, 2L), Set.copyOf(sequence));
        var pending = tournament(f).getTopology().pending();
        assertEquals(TournamentTopology.Stage.WAITING_FOR_HANDS, pending.stage());
        first.advanceTransfers(f.id());
        assertEquals(pending.id(), tournament(f).getTopology().pending().id());
        assertNull(tournament(f).getTopology().pending().reservedAt());
        assertEquals(1, first.state(tables.get(2).getId(), member(tables.get(2))).handNumber());

        foldTable(first, tables.getLast());
        first.advanceTransfers(f.id());
        var reserved = tournament(f).getTopology().pending();
        assertEquals(TournamentTopology.Stage.DESTINATION_RESERVED, reserved.stage());
        var move = reserved.transfers().getFirst();
        assertEquals(move.sourceTableId(), assignments.get(f.id(), move.userId()).tableId());
        assertFalse(first.canSubscribe(move.destinationTableId(), move.userId()));
        var abort = new TournamentTransactions(clientConnection, mongo) {
            @Override public <T> T run(Function<MongoTemplate, T> action) {
                return super.run(tx -> { action.apply(tx); throw new IllegalStateException("Injected failure after both seats were written."); });
            }
        };
        var failing = new TournamentGameplayService(mongo, abort, seating, properties, Clock.fixed(f.now(), ZoneOffset.UTC),
                new TournamentBlindClockService(), new TournamentEliminationService());
        assertThrows(IllegalStateException.class, () -> failing.advanceTransfers(f.id()));
        assertEquals(reserved, tournament(f).getTopology().pending());
        assertEquals(move.sourceTableId(), assignments.get(f.id(), move.userId()).tableId());
        var recovered = worker(f.now().plusSeconds(1));
        assertEquals(2, race(() -> recovered.advanceTransfers(f.id()), () -> right.advanceTransfers(f.id())));
        var topology = tournament(f).getTopology();
        assertNull(topology.pending());
        assertEquals(1, topology.history().size());
        var assignment = assignments.get(f.id(), move.userId());
        assertEquals(move.destinationTableId(), assignment.tableId());
        assertEquals(move.destinationSeat(), assignment.seat());
        assertEquals(move.settledStack(), assignment.settledVirtualChips());
        assertEquals(1, assignment.assignmentVersion());
        assertFalse(recovered.canSubscribe(move.sourceTableId(), move.userId()));
        assertThrows(ApiException.class, () -> recovered.act(move.sourceTableId(), move.userId(), request("FOLD", a)));
        assertTrue(recovered.canSubscribe(move.destinationTableId(), move.userId()));
        assertEquals(List.of(4, 5, 5), multiTables(f).stream().map(table -> (int) table.getPlayers().stream()
                .filter(player -> player.getChipStack() > 0).count()).sorted().toList());
        assertEquals(14, mongo.count(tournamentRows(f), TournamentTableAssignment.class));
        assertEquals(18, ledger(f, TournamentChipLedgerEntry.EntryType.INITIAL_STACK));
        assertEquals(3, mongo.count(tournamentRows(f), TournamentSettledHand.class));
        var next = worker(f.now().plusSeconds(10));
        next.advance(move.destinationTableId());
        assertEquals(2, next.state(move.destinationTableId(), move.userId()).handNumber());
        assertEquals(27000, standings.admin(f.id(), 0, 50).statistics().settledActiveStacks());
    }

    @Test
    void aSingleLocalSurvivorKeepsClockRunningAndSixGlobalPlayersReachOneRecoverableFinalTable() {
        var f = prepareMulti(12, 18);
        var game = worker(f.now());
        game.activate(f.id());
        var tables = multiTables(f);
        prepareBust(game, tables.getFirst(), Set.of(0, 1, 2, 3, 4, 5));
        finishBust(game, tables.getFirst(), "CALL");
        assertEquals(TournamentStatus.RUNNING, tournament(f).getStatus());
        assertEquals(TournamentTableMode.MULTI_TABLE, tournament(f).getTableMode());
        assertEquals("WAITING_FOR_TOURNAMENT_PLAYERS", table(f).getGameplay().reason());
        assertEquals("ACTIVE", game.clock(f.id(), bySeat(tables.getFirst(), 4)).status());
        assertNull(tournament(f).getStandings().candidate());
        assertEquals(7, standings.admin(f.id(), 0, 50).statistics().activePlayers());
        prepareBust(game, tables.getLast(), Set.of(3, 4));
        finishBust(game, tables.getLast(), "FOLD");
        game.advanceTransfers(f.id());
        assertEquals(TournamentTopology.Stage.DESTINATION_RESERVED, tournament(f).getTopology().pending().stage());
        var replacement = worker(f.now().plusSeconds(1));
        replacement.advanceTransfers(f.id());
        replacement.advanceTransfers(f.id());
        var layout = tournament(f).getTopology();
        assertEquals(1, layout.history().size());
        assertEquals(tables.getLast().getId(), layout.finalTableId());
        assertNotNull(table(f).getTournamentClosedAt());
        assertEquals(2, mongo.count(tournamentRows(f), TournamentSettledHand.class));
        assertEquals(6, mongo.count(tournamentRows(f), TournamentTableAssignment.class));
        var finalTable = mongo.findById(layout.finalTableId(), PokerTable.class);
        assertEquals(6, finalTable.getPlayers().stream().filter(p -> p.getChipStack() > 0).count());
        assertEquals(18000, finalTable.getPlayers().stream().mapToInt(p -> p.getChipStack()).sum());
        var now = worker(f.now().plusSeconds(10));
        now.advance(finalTable.getId());
        var champion = finalTable.getPlayers().stream().max(Comparator.comparingInt(p -> p.getChipStack())).orElseThrow().getUserId();
        rigWinner(now, finalTable.getId(), champion);
        for (int i = 0; i < 6; i++) {
            var state = now.state(finalTable.getId(), champion);
            now.act(finalTable.getId(), actor(state), request("ALL_IN", state));
        }
        var board = standings.player(f.id(), champion, 0, 50);
        assertEquals(1, board.activeTables());
        assertEquals(2, board.finalTableNumber());
        assertEquals(1, board.statistics().activePlayers());
        assertEquals(11, board.statistics().eliminatedPlayers());
        assertEquals(18000, board.viewer().settledStack());
        assertEquals(board.viewer().entrantId(), board.candidateEntrantId());
        assertEquals(TournamentStatus.PAUSED, tournament(f).getStatus());
        assertEquals(0, mongo.count(tournamentRows(f), TournamentFinalResult.class));
        assertEquals(12, ledger(f, TournamentChipLedgerEntry.EntryType.INITIAL_STACK));
        assertEquals(3, mongo.count(tournamentRows(f), TournamentSettledHand.class));
    }

    @Test
    void anInvalidPersistedReservationPausesForReviewWithoutMovingAnyChips() {
        var f = prepareMulti(12, 18);
        var game = worker(f.now());
        game.activate(f.id());
        var tables = multiTables(f);
        prepareBust(game, tables.getFirst(), Set.of(0, 1, 2, 3, 4, 5));
        finishBust(game, tables.getFirst(), "CALL");
        prepareBust(game, tables.getLast(), Set.of(3, 4));
        finishBust(game, tables.getLast(), "FOLD");
        game.advanceTransfers(f.id());
        var plan = tournament(f).getTopology().pending();
        assertEquals(TournamentTopology.Stage.DESTINATION_RESERVED, plan.stage());
        var transfer = plan.transfers().getFirst();
        mongo.updateFirst(Query.query(Criteria.where("id").is(f.id())), new Update().inc("topology.pending.transfers.0.settledStack", 1), Tournament.class);

        var replacement = worker(f.now().plusSeconds(1));
        replacement.advanceTransfers(f.id());
        var state = tournament(f);
        assertEquals(TournamentTopology.Stage.RECOVERY_REQUIRED, state.getTopology().pending().stage());
        assertEquals(TournamentStatus.PAUSED, state.getStatus());
        assertTrue(state.getTopology().history().isEmpty());
        assertNull(state.getTopology().finalTableId());
        assertEquals("TABLE_TRANSFER_RECOVERY_REQUIRED", replacement.clock(f.id(), transfer.userId()).pauseReason());
        assertEquals("RECOVERY_REQUIRED", assignments.get(f.id(), transfer.userId()).transfer().status());
        assertEquals(transfer.sourceTableId(), assignments.get(f.id(), transfer.userId()).tableId());
        assertEquals(transfer.settledStack(), assignments.get(f.id(), transfer.userId()).settledVirtualChips());
        assertFalse(replacement.canSubscribe(transfer.destinationTableId(), transfer.userId()));
        assertThrows(ApiException.class, () -> replacement.pauseClock(f.id(), false));
        assertEquals(18000, standings.admin(f.id(), 0, 50).statistics().settledActiveStacks());
    }

    @Test
    void sixPlayerFinalizationRollsBackEntireWriteAndCompetingWorkersReuseCommittedResults() throws Exception {
        var f = prepare(6);
        var game = worker(f.now());
        game.activate(f.id());
        prepareBust(game, table(f), Set.of(0, 1, 2, 3, 4, 5));
        finishBust(game, table(f), "CALL");
        var champion = bySeat(table(f), 4);
        var originalRanks = standings.admin(f.id(), 0, 50).rows().stream().map(row -> row.finishPosition()).toList();
        assertEquals(java.util.Arrays.asList(null, 2, 3, 4, 5, 6), originalRanks);
        assertNull(finalizer(f.now()).advance(f.id()));
        assertNull(tournament(f).getFinalization());
        var beforeClaim = new TournamentTransactions(clientConnection, mongo) {
            @Override public <T> T run(Function<MongoTemplate, T> action) {
                return super.run(tx -> { action.apply(tx); throw new IllegalStateException("Injected failure before claim commit"); });
            }
        };
        assertThrows(IllegalStateException.class, () -> new TournamentFinalizationService(mongo, beforeClaim,
                Clock.fixed(f.now().plusSeconds(3), ZoneOffset.UTC)).advance(f.id()));
        assertNull(tournament(f).getFinalization());
        finalizer(f.now().plusSeconds(3)).advance(f.id());
        var claim = tournament(f).getFinalization();
        assertEquals(TournamentFinalization.Stage.CLAIMED, claim.stage());

        var rolledBack = new TournamentTransactions(clientConnection, mongo) {
            @Override public <T> T run(Function<MongoTemplate, T> action) {
                return super.run(tx -> { action.apply(tx); throw new IllegalStateException("Injected failure before completion commit"); });
            }
        };
        var failing = new TournamentFinalizationService(mongo, rolledBack, Clock.fixed(f.now().plusSeconds(6), ZoneOffset.UTC));
        assertThrows(IllegalStateException.class, () -> failing.advance(f.id()));
        assertEquals(claim, tournament(f).getFinalization());
        assertEquals(TournamentStatus.PAUSED, tournament(f).getStatus());
        assertNull(entrant(f, champion).getFinishPosition());
        assertNull(table(f).getTournamentClosedAt());
        assertEquals(0, mongo.count(tournamentRows(f), TournamentFinalResult.class));

        var first = finalizer(f.now().plusSeconds(6));
        var replacement = finalizer(f.now().plusSeconds(7));
        assertEquals(2, race(() -> first.advance(f.id()), () -> replacement.advance(f.id())));
        var complete = tournament(f);
        assertEquals(TournamentStatus.COMPLETED, complete.getStatus());
        assertEquals(claim.operationId(), complete.getCompletion().operationId());
        assertEquals(complete.getCompletion(), replacement.advance(f.id()));
        assertEquals(6, mongo.count(tournamentRows(f), TournamentFinalResult.class));
        assertEquals(9000, complete.getCompletion().initialVirtualChipSupply());
        assertEquals(1, complete.getCompletion().completedHands());
        assertEquals(1, entrant(f, champion).getFinishPosition());
        assertEquals(TournamentEntrantStatus.COMPLETED, entrant(f, champion).getStatus());
        assertEquals(0, mongo.count(tournamentRows(f).addCriteria(Criteria.where("releasedAt").isNull()), TournamentTableAssignment.class));
        assertNotNull(table(f).getTournamentClosedAt());
        var result = resultViews.results(f.id(), champion, 0, 50);
        assertEquals(List.of(1, 2, 3, 4, 5, 6), result.rows().stream().map(TournamentResults.Standing::position).toList());
        assertEquals(1, result.viewer().handsPlayed());
        assertEquals(9000, result.viewer().finalVirtualChips());
        assertEquals(0, ledger(f, TournamentChipLedgerEntry.EntryType.FINAL_SCORE));
        assertEquals("PAUSED", game.clock(f.id(), champion).status());
        assertThrows(ApiException.class, () -> game.pauseClock(f.id(), false));
        assertFalse(game.canSubscribe(f.table(), champion));
        assertThrows(ApiException.class, () -> game.state(f.table(), champion));
        assertEquals(TournamentAssignment.Stage.COMPLETED, assignments.get(f.id(), champion).stage());
        worker(f.now().plusSeconds(90)).advance(f.table());
        assertEquals(1, table(f).getGameplay().number());
        mongo.updateFirst(Query.query(Criteria.where("id").is(f.id())), new Update().set("name", "Changed isolated fixture name"), Tournament.class);
        assertEquals(result, resultViews.results(f.id(), champion, 0, 50));
    }

    @Test
    void headsUpPrivateResultsAndHistoryUseActualParticipantsAndExcludeCancellation() {
        var f = prepare(2, List.of(new BlindLevel(1, 15, 30, 0, 30, false)), "PRIVATE-" + UUID.randomUUID());
        var game = worker(f.now());
        game.activate(f.id());
        rig(f, "2C AS 3D AH 5C 8H 9D TS 6C QD 7C KC");
        play(game, f, "ALL_IN");
        play(game, f, "CALL");
        var winner = playerAt(f, 0);
        var runnerUp = playerAt(f, 1);
        var winnerAuth = f.players().stream().filter(row -> row.user().id().equals(winner)).findFirst().orElseThrow();
        var before = assertThrows(RestClientResponseException.class, () -> http().get().uri("/api/tournaments/" + f.id() + "/results")
                .headers(h -> h.setBearerAuth(winnerAuth.token())).retrieve().toBodilessEntity());
        assertEquals(409, before.getStatusCode().value());
        complete(f.id(), f.now());
        var asWinner = http().get().uri("/api/tournaments/" + f.id() + "/results")
                .headers(h -> h.setBearerAuth(winnerAuth.token())).retrieve().body(TournamentResults.class);
        assertEquals(2, asWinner.podium().size());
        assertEquals(1, asWinner.viewer().position());
        assertEquals(2, resultViews.results(f.id(), runnerUp, 0, 50).viewer().position());
        assertEquals(404, assertThrows(RestClientResponseException.class, () -> http().get().uri("/api/tournaments/" + f.id() + "/results")
                .headers(h -> h.setBearerAuth(guest().token())).retrieve().toBodilessEntity()).getStatusCode().value());

        var cancelled = prepare(2);
        var event = tournament(cancelled);
        event.transitionTo(TournamentStatus.CANCELLED);
        tournaments.save(event);
        assertNull(finalizer(cancelled.now().plusSeconds(30)).advance(cancelled.id()));
        assertEquals(0, mongo.count(tournamentRows(cancelled), TournamentFinalResult.class));
        assertEquals(0, resultViews.history(cancelled.players().getFirst().user().id(), TournamentResultsService.HistoryFilter.ALL, 0, 12).total());
        var won = resultViews.history(winner, TournamentResultsService.HistoryFilter.WINS, 0, 1);
        assertEquals(new TournamentResults.Statistics(1, 1, 0, 1, 1, 100.0), won.statistics());
        assertEquals(f.id(), won.rows().getFirst().tournamentId());
        var second = resultViews.history(runnerUp, TournamentResultsService.HistoryFilter.TOP_THREE, 0, 12);
        assertEquals(new TournamentResults.Statistics(1, 0, 1, 1, 2, 0.0), second.statistics());
        assertEquals(2, second.rows().getFirst().position());
    }

    @Test
    void thirtySixPlayerEngineFixtureFinalizesAllRanksAndPreservesTransferHistory() {
        var f = prepareMulti(36, 36);
        var game = worker(f.now());
        game.activate(f.id());
        var starting = multiTables(f);
        assertEquals(6, starting.size());
        for (var table : starting) {
            prepareBust(game, table, Set.of(0, 1, 2, 3, 4, 5));
            finishBust(game, table, "CALL");
        }
        game.advanceTransfers(f.id());
        game.advanceTransfers(f.id());
        var layout = tournament(f).getTopology();
        assertEquals(1, layout.history().size());
        assertEquals(5, layout.history().getFirst().transfers().size());
        var table = mongo.findById(layout.finalTableId(), PokerTable.class);
        var champion = table.getPlayers().stream().filter(row -> row.getChipStack() > 0).findFirst().orElseThrow().getUserId();
        var next = worker(f.now().plusSeconds(10));
        next.advance(table.getId());
        rigWinner(next, table.getId(), champion);
        for (int index = 0; index < 6; index++) {
            var snapshot = next.state(table.getId(), champion);
            next.act(table.getId(), actor(snapshot), request("ALL_IN", snapshot));
        }
        var result = complete(f.id(), f.now().plusSeconds(10));
        assertEquals(36, result.entrantCount());
        assertEquals(54000, result.initialVirtualChipSupply());
        assertEquals(7, result.completedHands());
        assertEquals(7, mongo.count(tournamentRows(f), TournamentSettledHand.class));
        assertEquals(layout, tournament(f).getTopology());
        assertTrue(multiTables(f).stream().allMatch(row -> row.getTournamentClosedAt() != null));
        assertEquals(36, ledger(f, TournamentChipLedgerEntry.EntryType.INITIAL_STACK));
        assertEquals(36, mongo.count(tournamentRows(f), TournamentFinalResult.class));
        for (int page = 0; page < 3; page++) {
            var board = resultViews.results(f.id(), champion, page, 12);
            assertEquals(3, board.totalPages());
            assertEquals(java.util.stream.IntStream.rangeClosed(page * 12 + 1, page * 12 + 12).boxed().toList(),
                    board.rows().stream().map(TournamentResults.Standing::position).toList());
        }
    }

    @Test
    void corruptClaimCheckpointStaysPausedWithAnOperationalBlocker() {
        var f = prepare(2);
        var game = worker(f.now());
        game.activate(f.id());
        rig(f, "2C AS 3D AH 5C 8H 9D TS 6C QD 7C KC");
        play(game, f, "ALL_IN");
        play(game, f, "CALL");
        finalizer(f.now().plusSeconds(3)).advance(f.id());
        mongo.updateFirst(Query.query(Criteria.where("id").is(f.id())), new Update().inc("standings.candidate.settledStack", 1), Tournament.class);
        assertNull(finalizer(f.now().plusSeconds(6)).advance(f.id()));
        var event = tournament(f);
        assertEquals(TournamentStatus.PAUSED, event.getStatus());
        assertEquals(TournamentFinalization.Stage.RECOVERY_REQUIRED, event.getFinalization().stage());
        assertEquals("INVALID_CHAMPION_CANDIDATE", event.getFinalization().reason());
        assertEquals(0, mongo.count(tournamentRows(f), TournamentFinalResult.class));
    }

    private TournamentFinalizationService finalizer(Instant now) {
        return new TournamentFinalizationService(mongo, transactions, Clock.fixed(now, ZoneOffset.UTC));
    }

    private TournamentCompletion complete(UUID id, Instant settled) {
        finalizer(settled.plusSeconds(3)).advance(id);
        return finalizer(settled.plusSeconds(6)).advance(id);
    }

    private Fixture prepareMulti(int count, int maximum) {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var scheduled = now.plusSeconds(310);
        var event = new Tournament("MTT " + UUID.randomUUID(), null, null, TournamentStartMode.SCHEDULED,
                TournamentTableMode.MULTI_TABLE, TournamentVisibility.PUBLIC, maximum, count, 6, 1500,
                now.minusSeconds(30), now.plusSeconds(300), scheduled, null, UUID.randomUUID());
        event.transitionTo(TournamentStatus.REGISTRATION_OPEN);
        tournaments.save(event);
        schedules.save(new TournamentBlindSchedule(event.getId(), List.of(
                new BlindLevel(1, 5, 10, 0, 8, false), new BlindLevel(2, 10, 20, 0, 300, false))));
        var registrar = new DefaultTournamentRegistrationService(transactions, tournaments, entrants, users, assignments, true);
        var field = new ArrayList<AuthResponse>();
        for (int index = 0; index < count; index++) {
            var player = guest();
            field.add(player);
            registrar.register(event.getId(), player.user().id());
        }
        var start = new TournamentStartService(mongo, transactions, tournaments, seating, Clock.fixed(scheduled, ZoneOffset.UTC));
        start.start(event.getId());
        start.start(event.getId());
        var ready = start.start(event.getId()).getInitialization();
        assertEquals(ready, start.start(event.getId()).getInitialization());
        return new Fixture(event.getId(), ready.tableId(), scheduled, field);
    }

    private List<PokerTable> multiTables(Fixture f) {
        return mongo.find(tournamentRows(f), PokerTable.class).stream().sorted(Comparator.comparingInt(PokerTable::getTableNumber)).toList();
    }

    private static UUID bySeat(PokerTable table, int seat) {
        return table.getPlayers().stream().filter(p -> p.getSeat() == seat).findFirst().orElseThrow().getUserId();
    }

    private static UUID member(PokerTable table) { return table.getPlayers().getFirst().getUserId(); }

    private void foldTable(TournamentGameplayService game, PokerTable table) {
        while (true) {
            var state = game.state(table.getId(), member(table));
            if (!"ACTIVE".equals(state.tournament().phase())) return;
            game.act(table.getId(), actor(state), request("FOLD", state));
        }
    }

    private void prepareBust(TournamentGameplayService game, PokerTable table, Set<Integer> contenders) {
        rig(table.getId(), "4C 5D 2D AS 6C 7D 4H 5S 3H AH 6S 7S 8H 9D TS QS 3C KC 4S 2C");
        for (int i = 0; i < 5; i++) {
            var state = game.state(table.getId(), bySeat(table, 4));
            String action = i == 0 ? "ALL_IN" : contenders.contains(state.currentTurnSeat()) ? "CALL" : "FOLD";
            game.act(table.getId(), actor(state), request(action, state));
        }
    }

    private void finishBust(TournamentGameplayService game, PokerTable table, String command) {
        var state = game.state(table.getId(), bySeat(table, 4));
        game.act(table.getId(), actor(state), request(command, state));
    }

    private void rigWinner(TournamentGameplayService game, UUID tableId, UUID winner) {
        var state = game.state(tableId, winner);
        var order = state.players().stream().filter(p -> p.chipStack() > 0)
                .sorted(Comparator.comparingInt(p -> Math.floorMod(p.seat() - state.dealerSeat() - 1, 6))).toList();
        var other = new ArrayList<>(List.of("3C", "4D", "5H", "6S", "7C", "8D", "3D", "4H", "5S", "6C"));
        var cards = new ArrayList<String>();
        for (String ace : List.of("AS", "AH")) for (var p : order) cards.add(p.playerId().equals(winner) ? ace : other.removeFirst());
        cards.addAll(List.of("8H", "KC", "QD", "JS", "2S", "9H", "2D", "2C"));
        rig(tableId, String.join(" ", cards));
    }

    private UUID playerAt(Fixture f, int seat) { return table(f).getPlayers().get(seat).getUserId(); }
    private TournamentEntrant entrant(Fixture f, UUID user) { return entrants.findByTournamentIdAndUserId(f.id(), user).orElseThrow(); }

    private record Fixture(UUID id, UUID table, Instant now, List<AuthResponse> players) { }

    private Fixture prepare(int size) {
        return prepare(size, 0);
    }

    private Fixture prepare(int size, int ante) {
        return prepare(size, List.of(new BlindLevel(1, 15, 30, ante, 300, false)));
    }

    private Fixture prepare(int size, List<BlindLevel> levels) {
        return prepare(size, levels, null);
    }

    private Fixture prepare(int size, List<BlindLevel> levels, String invite) {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var event = new Tournament("Phase 7 " + UUID.randomUUID(), null, null, TournamentStartMode.SIT_AND_GO,
                TournamentTableMode.SINGLE_TABLE, invite == null ? TournamentVisibility.PUBLIC : TournamentVisibility.PRIVATE, size, size, size, 1500,
                now.minusSeconds(30), now.plusSeconds(120), null, invite, UUID.randomUUID());
        event.transitionTo(TournamentStatus.REGISTRATION_OPEN);
        tournaments.save(event);
        schedules.save(new TournamentBlindSchedule(event.getId(), levels));
        var registrar = new DefaultTournamentRegistrationService(transactions, tournaments, entrants, users, assignments, true);
        var players = new ArrayList<AuthResponse>();
        for (int index = 0; index < size; index++) {
            var player = guest();
            players.add(player);
            if (invite == null) registrar.register(event.getId(), player.user().id());
            else registrar.registerPrivate(event.getId(), player.user().id(), invite);
        }
        var start = new TournamentStartService(mongo, transactions, tournaments, seating, Clock.fixed(now, ZoneOffset.UTC));
        start.start(event.getId());
        var ready = start.start(event.getId()).getInitialization();
        return new Fixture(event.getId(), ready.tableId(), now, List.copyOf(players));
    }

    private TournamentGameplayService worker(Instant now) {
        return new TournamentGameplayService(mongo, transactions, seating, properties, Clock.fixed(now, ZoneOffset.UTC), new TournamentBlindClockService(), new TournamentEliminationService());
    }

    private static long supply(TableStateResponse state) { return state.pot() + state.players().stream().mapToLong(p -> p.chipStack()).sum(); }
    private TableStateResponse state(TournamentGameplayService service, Fixture f) { return service.state(f.table(), f.players().getFirst().user().id()); }
    private static UUID actor(TableStateResponse state) { return state.players().stream().filter(p -> p.isTurn()).findFirst().orElseThrow().playerId(); }
    private static GameActionRequest request(String action, TableStateResponse state) {
        return new GameActionRequest(action, null, null, UUID.randomUUID().toString(), state.tournament().actionVersion());
    }
    private void play(TournamentGameplayService service, Fixture f, String action) {
        var state = state(service, f);
        service.act(f.table(), actor(state), request(action, state));
    }
    private void rig(Fixture f, String drawOrder) {
        rig(f.table(), drawOrder);
    }
    private void rig(UUID tableId, String drawOrder) {
        var cards = new ArrayList<>(List.of(drawOrder.split(" ")));
        Deck.createDeck().drawMultiple(52).stream().map(Card::code).filter(card -> !cards.contains(card)).toList().forEach(cards::add);
        mongo.updateFirst(Query.query(Criteria.where("id").is(tableId)), new Update().set("gameplay.deck", cards), PokerTable.class);
    }
    private PokerTable table(Fixture f) { return mongo.findById(f.table(), PokerTable.class); }
    private Tournament tournament(Fixture f) { return tournaments.findById(f.id()).orElseThrow(); }
    private static Query tableById(Fixture f) { return Query.query(Criteria.where("id").is(f.table())); }
    private static Query tournamentRows(Fixture f) { return Query.query(Criteria.where("tournamentId").is(f.id())); }
    private long ledger(Fixture f, TournamentChipLedgerEntry.EntryType type) {
        return mongo.count(tournamentRows(f).addCriteria(Criteria.where("type").is(type)), TournamentChipLedgerEntry.class);
    }
    private RestClient http() { return RestClient.create("http://localhost:" + port); }
    private AuthResponse guest() { return http().post().uri("/api/auth/guest").retrieve().body(AuthResponse.class); }

    private static int race(Runnable left, Runnable right) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var tasks = List.of(left, right).stream().map(task -> pool.submit(() -> {
                start.await();
                try { task.run(); return 1; }
                catch (GameActionException duplicate) { return 0; }
            })).toList();
            start.countDown();
            return tasks.get(0).get() + tasks.get(1).get();
        }
    }
}
