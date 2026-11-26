package com.poker.tournament.service;

import com.poker.entity.User;
import com.poker.exception.ApiException;
import com.poker.game.engine.TableConfig;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentBlindSchedule;
import com.poker.tournament.entity.TournamentEntrant;
import com.poker.tournament.entity.TournamentEntrantStatus;
import com.poker.tournament.entity.TournamentInitialization;
import com.poker.tournament.entity.TournamentInitialization.Phase;
import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.repository.TournamentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.bson.Document;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TournamentStartService implements TournamentLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(TournamentStartService.class);
    private final MongoTemplate mongo;
    private final TournamentTransactions transactions;
    private final TournamentRepository tournaments;
    private final TournamentSeatingService seating;
    private final Clock clock;

    public TournamentStartService(MongoTemplate mongo, TournamentTransactions transactions, TournamentRepository tournaments,
                                  TournamentSeatingService seating, Clock clock) {
        this.mongo = mongo;
        this.transactions = transactions;
        this.tournaments = tournaments;
        this.seating = seating;
        this.clock = clock;
    }

    record DueId(@Id UUID id) { }

    @Override
    public void processDueTournaments() {
        Date now = Date.from(clock.instant());
        var closedWindow = new Document("registrationClosingAt", new Document("$lte", now));
        var available = new Document("initialization.phase", new Document("$nin", List.of("READY", "BLOCKED")));
        var filter = new Document("$and", List.of(available, new Document("$or", List.of(
                new Document("status", "STARTING").append("$or", List.of(
                        new Document("initialization.retryAt", null), new Document("initialization.retryAt", new Document("$lte", now)))),
                new Document("status", "REGISTRATION_CLOSED").append("$or", List.of(
                        new Document("startMode", "SIT_AND_GO"), new Document("scheduledStartAt", new Document("$lte", now)))),
                new Document("status", "REGISTRATION_OPEN").append("$or", List.of(closedWindow,
                        new Document("startMode", "SIT_AND_GO").append("registrationOpeningAt", new Document("$lte", now))))))));
        // Full events from before the registrar started closing the final seat are also recoverable.
        var lookup = new Document("from", "tournament_entrants").append("localField", "_id")
                .append("foreignField", "tournamentId").append("as", "eligible")
                .append("let", new Document("checkFull", new Document("$and", List.of(
                        new Document("$eq", List.of("$status", "REGISTRATION_OPEN")),
                        new Document("$eq", List.of("$startMode", "SIT_AND_GO"))))))
                .append("pipeline", List.of(new Document("$match", new Document("status", "REGISTERED")
                        .append("$expr", "$$checkFull")), new Document("$count", "size")));
        var ready = new Document("$or", List.of(new Document("status", new Document("$ne", "REGISTRATION_OPEN")),
                closedWindow, new Document("$expr", new Document("$gte", List.of(
                        new Document("$ifNull", List.of(new Document("$arrayElemAt", List.of("$eligible.size", 0)), 0)),
                        "$maximumEntrants")))));
        var due = mongo.aggregate(Aggregation.newAggregation(
                context -> new Document("$match", filter), context -> new Document("$lookup", lookup),
                context -> new Document("$match", ready),
                context -> new Document("$sort", new Document("createdAt", 1).append("_id", 1)),
                Aggregation.limit(32), context -> new Document("$project", new Document("_id", 1))),
                "tournaments", DueId.class).getMappedResults();
        for (var id : due) {
            try {
                start(id.id());
            } catch (RuntimeException failure) {
                log.warn("Tournament preparation deferred for {}: {}", id.id(), failure.getClass().getSimpleName());
            }
        }
    }

    /** Advances one durable stage. The scheduler may call this on any node, repeatedly. */
    @Override
    public Tournament start(UUID id) {
        try {
            var claim = transactions.run(tx -> advance(tx, id, clock.instant()));
            if (claim != null) log.atInfo().addKeyValue("tournamentId", id)
                    .addKeyValue("initializationOperationId", claim).log("Tournament initialization started");
        } catch (RuntimeException failure) {
            // Never overwrite READY or cancellation if another worker already committed.
            try {
                transactions.run(tx -> tx.updateFirst(Query.query(Criteria.where("id").is(id)
                                .and("status").is(TournamentStatus.STARTING)
                                .and("initialization.phase").in(Phase.CLAIMED, Phase.RETRYING)),
                        new Update().set("initialization.phase", Phase.RETRYING)
                                .set("initialization.reason", "PREPARATION_RETRY")
                                .set("initialization.retryAt", clock.instant().plusSeconds(10)), Tournament.class));
            } catch (RuntimeException markerFailure) {
                failure.addSuppressed(markerFailure);
            }
            throw failure;
        }
        return tournaments.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TOURNAMENT_NOT_FOUND", "Tournament not found."));
    }

    private UUID advance(MongoTemplate tx, UUID id, Instant now) {
        var event = tx.findById(id, Tournament.class);
        if (event == null || !List.of(TournamentStatus.REGISTRATION_OPEN, TournamentStatus.REGISTRATION_CLOSED,
                TournamentStatus.STARTING).contains(event.getStatus())) return null;
        var progress = event.getInitialization();
        if (progress != null && (progress.phase() == Phase.READY || progress.phase() == Phase.BLOCKED
                || (progress.retryAt() != null && now.isBefore(progress.retryAt())))) return null;

        boolean scheduled = event.getStartMode() == TournamentStartMode.SCHEDULED;
        if (event.getStatus() == TournamentStatus.REGISTRATION_OPEN) {
            if (now.isBefore(event.getRegistrationOpeningAt())) return null;
            boolean cutoff = !now.isBefore(event.getRegistrationClosingAt());
            if (!cutoff && (scheduled || tx.count(registered(id), TournamentEntrant.class) < event.getMaximumEntrants())) return null;
            if (!TournamentSeatingService.supported(event)) {
                savePhase(tx, event, new TournamentInitialization(null, Phase.BLOCKED, null, null, null, "UNSUPPORTED_TABLE", null), now);
                return null;
            }
            event.transitionTo(TournamentStatus.REGISTRATION_CLOSED);
            savePhase(tx, event, null, now);
            return null;
        }
        if (scheduled && (event.getScheduledStartAt() == null || now.isBefore(event.getScheduledStartAt()))) return null;
        if (!TournamentSeatingService.supported(event)) {
            savePhase(tx, event, new TournamentInitialization(null, Phase.BLOCKED, null, null, null, "UNSUPPORTED_TABLE", null), now);
            return null;
        }
        var entrants = tx.find(registered(id).limit(event.getMaximumEntrants() + 1), TournamentEntrant.class);
        int minimum = scheduled ? event.getMinimumEntrants() : event.getMaximumEntrants();
        if (entrants.size() < minimum) {
            event.transitionTo(TournamentStatus.CANCELLED);
            savePhase(tx, event, new TournamentInitialization(null, Phase.CANCELLED, null, null, null, "INSUFFICIENT_ENTRANTS", null), now);
            return null;
        }

        var schedule = tx.findOne(Query.query(Criteria.where("tournamentId").is(id)), TournamentBlindSchedule.class);
        TableConfig config;
        try {
            config = seating.configuration(event, schedule == null ? List.of() : schedule.getLevels());
        } catch (IllegalArgumentException invalid) {
            savePhase(tx, event, blocked(progress, "INVALID_INITIAL_BLINDS"), now);
            return null;
        }
        if (entrants.size() > event.getMaximumEntrants() || entrants.stream().anyMatch(e ->
                e.getTableId() != null || e.getCurrentVirtualChips() != 0 || e.getInitialVirtualChips() != 0)) {
            savePhase(tx, event, blocked(progress, "INVALID_ENTRANT_STATE"), now);
            return null;
        }
        if (progress == null || progress.operationId() == null) {
            event.transitionTo(TournamentStatus.STARTING);
            UUID operationId = UUID.randomUUID();
            savePhase(tx, event, new TournamentInitialization(operationId, Phase.CLAIMED, now, null, null, null, null), now);
            return operationId;
        }

        var users = tx.find(Query.query(Criteria.where("id").in(entrants.stream().map(TournamentEntrant::getUserId).toList())),
                User.class).stream().collect(Collectors.toMap(User::getId, user -> user));
        if (users.size() != entrants.size()) {
            savePhase(tx, event, blocked(progress, "INVALID_ENTRANT_STATE"), now);
            return null;
        }
        // Acquire the same write fence used by registration and Express cancellation before creating records.
        tx.updateFirst(byId(id), new Update().inc("registrationRevision", 1), Tournament.class);
        var tableIds = seating.prepareField(tx, event, entrants, users, config, schedule.getLevels().getFirst(), now)
                .stream().map(com.poker.entity.PokerTable::getId).toList();
        savePhase(tx, event, new TournamentInitialization(progress.operationId(), Phase.READY,
                progress.claimedAt(), tableIds.getFirst(), now, null, null, tableIds), now);
        return null;
    }

    private static TournamentInitialization blocked(TournamentInitialization prior, String reason) {
        return new TournamentInitialization(prior == null ? null : prior.operationId(), Phase.BLOCKED,
                prior == null ? null : prior.claimedAt(), null, null, reason, null);
    }

    private static Query registered(UUID id) {
        return Query.query(Criteria.where("tournamentId").is(id).and("status").is(TournamentEntrantStatus.REGISTERED));
    }

    private static Query byId(UUID id) { return Query.query(Criteria.where("id").is(id)); }

    private static void savePhase(MongoTemplate tx, Tournament event, TournamentInitialization progress, Instant now) {
        var update = new Update().set("status", event.getStatus()).set("updatedAt", now).inc("registrationRevision", 1);
        if (progress != null) update.set("initialization", progress);
        if (event.getStatus() == TournamentStatus.CANCELLED) update.set("completedAt", now);
        tx.updateFirst(byId(event.getId()), update, Tournament.class);
    }
}
