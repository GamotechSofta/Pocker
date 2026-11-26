package com.poker.tournament;

import com.mongodb.client.MongoClient;
import com.poker.dto.AuthResponse;
import com.poker.entity.PokerTable;
import com.poker.entity.User;
import com.poker.exception.ApiException;
import com.poker.game.engine.TableConfig;
import com.poker.repository.UserRepository;
import com.poker.service.TableService;
import com.poker.support.AbstractMongoTest;
import com.poker.tournament.dto.TournamentAssignment;
import com.poker.tournament.entity.*;
import com.poker.tournament.repository.TournamentBlindScheduleRepository;
import com.poker.tournament.repository.TournamentEntrantRepository;
import com.poker.tournament.repository.TournamentRepository;
import com.poker.tournament.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TournamentPreparationIntegrationTest extends AbstractMongoTest {
    private static final BlindLevel FIRST = new BlindLevel(1, 15, 30, 0, 300, false);
    @LocalServerPort private int port;
    @Autowired private MongoTemplate mongo;
    @Autowired private MongoClient clientConnection;
    @Autowired private TournamentTransactions transactions;
    @Autowired private TournamentRepository tournaments;
    @Autowired private TournamentEntrantRepository entrants;
    @Autowired private TournamentBlindScheduleRepository schedules;
    @Autowired private TournamentSeatingService seating;
    @Autowired private TournamentAssignmentService assignments;
    @Autowired private UserRepository users;
    @Autowired private TableService cash;

    @Test
    void lastSeatClosesRegistrationAndSeparateWorkersPrepareExactlyOneTable() throws Exception {
        var now = Instant.now();
        var tournament = event(now, TournamentStartMode.SIT_AND_GO, TournamentTableMode.SINGLE_TABLE, null);
        var a = guest();
        var b = guest();
        var registrar = registrar();
        assertEquals(TournamentStatus.REGISTRATION_OPEN, registrar.register(tournament.getId(), a.user().id()).tournament().status());
        assertEquals(TournamentStatus.REGISTRATION_CLOSED, registrar.register(tournament.getId(), b.user().id()).tournament().status());
        assertThrows(ApiException.class, () -> registrar.unregister(tournament.getId(), a.user().id()));
        assertThrows(ApiException.class, () -> registrar.register(tournament.getId(), guest().user().id()));
        var worker = worker(now.plusSeconds(1), seating);
        worker.processDueTournaments();
        assertEquals(TournamentInitialization.Phase.CLAIMED, event(tournament).getInitialization().phase());
        var second = worker(now.plusSeconds(2), seating);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            var firstJob = pool.submit(() -> { gate.await(); return worker.start(tournament.getId()); });
            var otherJob = pool.submit(() -> { gate.await(); return second.start(tournament.getId()); });
            gate.countDown();
            firstJob.get();
            otherJob.get();
        }
        var assigned = assignments.get(tournament.getId(), a.user().id());
        var other = assignments.get(tournament.getId(), b.user().id());
        assertEquals(TournamentStatus.STARTING, assigned.tournamentStatus());
        assertEquals(TournamentAssignment.Stage.READY, assigned.stage());
        assertEquals(assigned.tableId(), other.tableId());
        assertNotEquals(assigned.seat(), other.seat());
        assertTrue(List.of(0, 1).containsAll(List.of(assigned.seat(), other.seat())));
        assertEquals(1500L, assigned.startingVirtualChips());
        assertEquals(FIRST, assigned.initialBlind());
        assertEquals(1, count(tournament, PokerTable.class));
        assertEquals(2, count(tournament, TournamentTableAssignment.class));
        assertEquals(2, count(tournament, TournamentChipLedgerEntry.class));
        assertEquals(3000, mongo.find(Query.query(Criteria.where("tournamentId").is(tournament.getId())),
                TournamentChipLedgerEntry.class).stream().mapToLong(TournamentChipLedgerEntry::getDelta).sum());
        assertEquals(assigned.tableId(), second.start(tournament.getId()).getInitialization().tableId());
        assertTrue(cash.seatedTableId(a.user().id()).isEmpty());
        for (var url : List.of("", "/state")) rejected("/api/tables/" + assigned.tableId() + url, a, 404);
        rejected("/api/tournaments/" + tournament.getId() + "/assignment", guest(), 404);
        assertEquals(assigned.tableId(), client().get().uri("/api/tournaments/" + tournament.getId() + "/assignment")
                .headers(h -> h.setBearerAuth(a.token())).retrieve().body(TournamentAssignment.class).tableId());
        assertThrows(RuntimeException.class, () -> cash.engineSnapshot(assigned.tableId()));
    }

    @Test
    void scheduledCutoffAndServerClockControlPreparationAndCancellation() {
        Instant now = Instant.now();
        var good = event(now, TournamentStartMode.SCHEDULED, TournamentTableMode.SINGLE_TABLE, null);
        var shortField = event(now, TournamentStartMode.SCHEDULED, TournamentTableMode.SINGLE_TABLE, null);
        var a = guest();
        var b = guest();
        registrar().register(good.getId(), a.user().id());
        registrar().register(good.getId(), b.user().id());
        registrar().register(shortField.getId(), a.user().id());
        worker(now.plusSeconds(61), seating).processDueTournaments();
        assertEquals(TournamentStatus.REGISTRATION_CLOSED, event(good).getStatus());
        assertEquals(0, count(good, PokerTable.class));
        worker(now.plusSeconds(119), seating).start(good.getId());
        assertEquals(TournamentStatus.REGISTRATION_CLOSED, event(good).getStatus());
        var due = worker(now.plusSeconds(121), seating);
        due.start(good.getId());
        due.start(good.getId());
        due.start(shortField.getId());
        assertEquals(TournamentAssignment.Stage.READY, assignments.get(good.getId(), a.user().id()).stage());
        assertEquals(TournamentStatus.CANCELLED, event(shortField).getStatus());
        assertEquals("INSUFFICIENT_ENTRANTS", assignments.get(shortField.getId(), a.user().id()).reason());
        assertEquals(0, count(shortField, PokerTable.class));
    }

    @Test
    void failedPreparationRollsBackAndFreshWorkerResumesSameClaim() {
        Instant now = Instant.now();
        var event = event(now, TournamentStartMode.SIT_AND_GO, TournamentTableMode.SINGLE_TABLE, null);
        var a = guest();
        var b = guest();
        registrar().register(event.getId(), a.user().id());
        registrar().register(event.getId(), b.user().id());
        var worker = worker(now.plusSeconds(1), seating);
        var claim = worker.start(event.getId()).getInitialization().operationId();
        var failDuringChips = new TournamentSeatingService((tx, entrant, table, seat, stack, time) -> {
            new DefaultTournamentChipService().issueInitialStack(tx, entrant, table, seat, stack, time);
            throw new IllegalStateException("Injected failure after initial chip write");
        });
        assertThrows(IllegalStateException.class, () -> worker(now.plusSeconds(2), failDuringChips).start(event.getId()));
        assertEquals(TournamentAssignment.Stage.DELAYED, assignments.get(event.getId(), a.user().id()).stage());
        assertEquals(0, count(event, PokerTable.class));
        assertEquals(0, count(event, TournamentTableAssignment.class));
        assertEquals(0, count(event, TournamentChipLedgerEntry.class));
        assertEquals(0, entrants.findByTournamentIdAndUserId(event.getId(), a.user().id()).orElseThrow().getCurrentVirtualChips());
        var failFinal = new TournamentSeatingService(new DefaultTournamentChipService()) {
            @Override
            public PokerTable prepare(MongoTemplate tx, Tournament tournament, List<TournamentEntrant> field,
                                      Map<UUID, User> players, TableConfig config, BlindLevel first, Instant time) {
                super.prepare(tx, tournament, field, players, config, first, time);
                throw new IllegalStateException("Injected failure before publishing READY");
            }
        };
        assertThrows(IllegalStateException.class, () -> worker(now.plusSeconds(13), failFinal).start(event.getId()));
        assertEquals(0, count(event, PokerTable.class));
        var recovered = worker(now.plusSeconds(24), seating).start(event.getId());
        assertEquals(claim, recovered.getInitialization().operationId());
        assertEquals(TournamentInitialization.Phase.READY, recovered.getInitialization().phase());
        assertEquals(1, count(event, PokerTable.class));
        assertEquals(2, count(event, TournamentChipLedgerEntry.class));
    }

    @Test
    void failureDuringClaimAndAfterTableWriteLeaveNoPartialStateAndIndexesRejectDuplicates() {
        Instant now = Instant.now();
        var event = event(now, TournamentStartMode.SIT_AND_GO, TournamentTableMode.SINGLE_TABLE, null);
        var a = guest();
        var b = guest();
        registrar().register(event.getId(), a.user().id());
        registrar().register(event.getId(), b.user().id());
        var failClaim = new TournamentTransactions(clientConnection, mongo) {
            private final AtomicBoolean first = new AtomicBoolean(true);

            @Override
            public <T> T run(Function<MongoTemplate, T> action) {
                if (!first.getAndSet(false)) return super.run(action);
                return super.run(tx -> {
                    action.apply(tx);
                    throw new IllegalStateException("Injected failure before committing the start claim");
                });
            }
        };
        var first = new TournamentStartService(mongo, failClaim, tournaments, seating, Clock.fixed(now, ZoneOffset.UTC));
        assertThrows(IllegalStateException.class, () -> first.start(event.getId()));
        assertEquals(TournamentStatus.REGISTRATION_CLOSED, event(event).getStatus());
        assertNull(event(event).getInitialization());
        worker(now.plusSeconds(1), seating).start(event.getId());

        var failTable = new TournamentSeatingService(new DefaultTournamentChipService()) {
            @Override
            public PokerTable prepare(MongoTemplate tx, Tournament tournament, List<TournamentEntrant> field,
                                      Map<UUID, User> players, TableConfig config, BlindLevel blind, Instant at) {
                tx.insert(PokerTable.prepareTournament(tournament.getId(), tournament.getCreatorId(), tournament.getName(),
                        config, blind, at));
                throw new IllegalStateException("Injected failure after table persistence");
            }
        };
        assertThrows(IllegalStateException.class, () -> worker(now.plusSeconds(2), failTable).start(event.getId()));
        assertEquals(0, count(event, PokerTable.class));
        var ready = worker(now.plusSeconds(13), seating).start(event.getId()).getInitialization();
        var assigned = assignments.get(event.getId(), a.user().id());
        var entry = entrants.findByTournamentIdAndUserId(event.getId(), a.user().id()).orElseThrow();
        assertThrows(DuplicateKeyException.class, () -> mongo.insert(new TournamentTableAssignment(event.getId(),
                UUID.randomUUID(), b.user().id(), ready.tableId(), assigned.seat(), now)));
        assertThrows(DuplicateKeyException.class, () -> mongo.insert(new TournamentChipLedgerEntry(event.getId(),
                entry.getId(), TournamentChipLedgerEntry.EntryType.INITIAL_STACK, 1500, 1500,
                "initial:" + entry.getId(), ready.tableId())));
        assertEquals(1, count(event, PokerTable.class));
        assertEquals(2, count(event, TournamentTableAssignment.class));
        assertEquals(2, count(event, TournamentChipLedgerEntry.class));
    }

    @Test
    void privateAssignmentsRequireMembershipAndMultiTableNeverPrepares() {
        Instant now = Instant.now();
        var code = "PH6-" + UUID.randomUUID();
        var privateEvent = event(now, TournamentStartMode.SIT_AND_GO, TournamentTableMode.SINGLE_TABLE, code);
        var a = guest();
        var b = guest();
        registrar().registerPrivate(privateEvent.getId(), a.user().id(), code);
        registrar().registerPrivate(privateEvent.getId(), b.user().id(), code);
        var worker = worker(now.plusSeconds(1), seating);
        worker.start(privateEvent.getId());
        var table = worker.start(privateEvent.getId()).getInitialization().tableId();
        rejected("/api/tournaments/" + privateEvent.getId() + "/assignment", guest(), 404);
        assertThrows(RuntimeException.class, () -> cash.joinByRoomCode(a.user().id(), code));
        assertThrows(RestClientResponseException.class, () -> client().post().uri("/api/tables/" + table + "/join")
                .headers(h -> h.setBearerAuth(a.token())).retrieve().toBodilessEntity());

        var multi = event(now, TournamentStartMode.SIT_AND_GO, TournamentTableMode.MULTI_TABLE, null);
        registrar().register(multi.getId(), a.user().id());
        registrar().register(multi.getId(), b.user().id());
        worker.start(multi.getId());
        assertEquals("UNSUPPORTED_TABLE", event(multi).getInitialization().reason());
        assertEquals(0, count(multi, PokerTable.class));
    }

    private Tournament event(Instant now, TournamentStartMode start, TournamentTableMode mode, String code) {
        var tournament = new Tournament("Phase 6 " + UUID.randomUUID(), null, null, start, mode,
                code == null ? TournamentVisibility.PUBLIC : TournamentVisibility.PRIVATE, 2, 2, 2, 1500,
                now.minusSeconds(30), now.plusSeconds(60), start == TournamentStartMode.SCHEDULED ? now.plusSeconds(120) : null,
                code, UUID.randomUUID());
        tournament.transitionTo(TournamentStatus.REGISTRATION_OPEN);
        var saved = tournaments.save(tournament);
        schedules.save(new TournamentBlindSchedule(saved.getId(), List.of(FIRST)));
        return saved;
    }

    private Tournament event(Tournament tournament) { return tournaments.findById(tournament.getId()).orElseThrow(); }
    private long count(Tournament event, Class<?> type) {
        return mongo.count(Query.query(Criteria.where("tournamentId").is(event.getId())), type);
    }
    private DefaultTournamentRegistrationService registrar() {
        return new DefaultTournamentRegistrationService(transactions, tournaments, entrants, users, assignments, true);
    }
    private TournamentStartService worker(Instant now, TournamentSeatingService service) {
        return new TournamentStartService(mongo, transactions, tournaments, service, Clock.fixed(now, ZoneOffset.UTC));
    }
    private RestClient client() { return RestClient.create("http://localhost:" + port); }
    private AuthResponse guest() { return client().post().uri("/api/auth/guest").retrieve().body(AuthResponse.class); }
    private void rejected(String url, AuthResponse user, int status) {
        assertEquals(status, assertThrows(RestClientResponseException.class, () -> client().get().uri(url)
                .headers(h -> h.setBearerAuth(user.token())).retrieve().toBodilessEntity()).getStatusCode().value());
    }
}
