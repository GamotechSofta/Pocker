package com.poker.tournament.service;

import com.poker.entity.User;
import com.poker.exception.ApiException;
import com.poker.repository.UserRepository;
import com.poker.tournament.dto.TournamentRegistration;
import com.poker.tournament.dto.TournamentDiscovery;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentBlindSchedule;
import com.poker.tournament.entity.TournamentEntrant;
import com.poker.tournament.entity.TournamentEntrantStatus;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentVisibility;
import com.poker.tournament.repository.TournamentEntrantRepository;
import com.poker.tournament.repository.TournamentRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DefaultTournamentRegistrationService implements TournamentRegistrationService {
    private static final TournamentEntrantStatus WITHDRAWN = TournamentEntrantStatus.CANCELLED;
    private final TournamentTransactions transactions;
    private final boolean autoStart;
    private final TournamentRepository tournaments;
    private final TournamentEntrantRepository entrants;
    private final UserRepository users;
    private final TournamentAssignmentService assignments;

    public DefaultTournamentRegistrationService(TournamentTransactions transactions,
            TournamentRepository tournaments, TournamentEntrantRepository entrants, UserRepository users,
            TournamentAssignmentService assignments,
            @Value("${app.tournament.auto-start-enabled:true}") boolean autoStart) {
        this.transactions = transactions;
        this.autoStart = autoStart;
        this.tournaments = tournaments;
        this.entrants = entrants;
        this.users = users;
        this.assignments = assignments;
    }

    @Override
    public TournamentRegistration.Status register(UUID id, UUID user) {
        return change(id, user, null, false);
    }

    @Override
    public TournamentRegistration.Status registerPrivate(UUID id, UUID user, String inviteCode) {
        String code = normalize(inviteCode);
        var invited = findInvitation(code);
        if (id == null || !id.equals(invited.getId())) throw invalidInvite();
        return change(id, user, code, false);
    }

    @Override
    public TournamentRegistration.Status unregister(UUID id, UUID user) {
        return change(id, user, null, true);
    }

    @Override
    public TournamentRegistration.Invitation resolve(String inviteCode) {
        var event = findInvitation(normalize(inviteCode));
        var now = Instant.now();
        if (!isOpen(event, now)) throw invalidInvite();
        long count = entrants.countByTournamentIdAndStatusNot(event.getId(), WITHDRAWN);
        if (count >= event.getMaximumEntrants()) throw invalidInvite();
        return new TournamentRegistration.Invitation(TournamentDiscoveryService.card(event, count, null),
                event.getDescription(), event.getSeatsPerTable(), now);
    }

    @Override
    public TournamentRegistration.Status registration(UUID id, UUID user) {
        var event = tournaments.findById(id).orElseThrow(DefaultTournamentRegistrationService::notFound);
        var status = entrants.findByTournamentIdAndUserId(id, user).map(TournamentEntrant::getStatus).orElse(null);
        if (event.getStatus() == TournamentStatus.DRAFT || (requiresMembership(event) && !active(status))) throw notFound();
        return snapshot(event, status, entrants.countByTournamentIdAndStatusNot(id, WITHDRAWN), Instant.now());
    }

    @Override
    public TournamentRegistration.WaitingRoom waitingRoom(UUID id, UUID user) {
        return transactions.run(tx -> {
            var event = tx.findById(id, Tournament.class);
            var entry = tx.findOne(Query.query(Criteria.where("tournamentId").is(id).and("userId").is(user)), TournamentEntrant.class);
            if (event == null || event.getStatus() == TournamentStatus.DRAFT || entry == null || !active(entry.getStatus())) {
                throw notFound();
            }
            var registered = tx.find(activeEntries(id).with(Sort.by("registeredAt", "id")).limit(50), TournamentEntrant.class);
            long count = count(tx, id);
            var ids = registered.stream().map(TournamentEntrant::getUserId).toList();
            var names = tx.find(Query.query(Criteria.where("id").in(ids)), User.class).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
            var schedule = tx.findOne(Query.query(Criteria.where("tournamentId").is(id)), TournamentBlindSchedule.class);
            var now = Instant.now();
            var status = snapshot(event, entry.getStatus(), count, now);
            var details = new TournamentDiscovery.Details(status.tournament(), event.getDescription(), event.getSeatsPerTable(),
                    event.getCompletedAt(), schedule == null ? List.of() : schedule.getLevels(),
                    TournamentDiscoveryService.players(registered, names, user), List.of(), now);
            return new TournamentRegistration.WaitingRoom(details, status.remainingSeats(), status.withdrawalAllowed(),
                    assignments.snapshot(tx, event, entry, now));
        });
    }

    private TournamentRegistration.Status change(UUID id, UUID user, String code, boolean withdraw) {
        if (!users.existsById(user)) throw new ApiException(HttpStatus.FORBIDDEN, "PLAYER_INELIGIBLE", "Player account is unavailable.");
        return transactions.run(tx -> changeInTransaction(tx, id, user, code, withdraw));
    }

    private TournamentRegistration.Status changeInTransaction(MongoTemplate tx, UUID id, UUID user, String code, boolean withdraw) {
        var event = tx.findById(id, Tournament.class);
        var entry = tx.findOne(Query.query(Criteria.where("tournamentId").is(id).and("userId").is(user)), TournamentEntrant.class);
        if (event == null || event.getStatus() == TournamentStatus.DRAFT) throw code == null ? notFound() : invalidInvite();
        var state = entry == null ? null : entry.getStatus();
        if (code != null && (event.getVisibility() != TournamentVisibility.PRIVATE
                || !code.equalsIgnoreCase(event.getPrivateInviteCode()))) throw invalidInvite();
        if (event.getVisibility() == TournamentVisibility.PRIVATE && code == null && (!withdraw || entry == null)) {
            throw notFound();
        }
        if (withdraw && !active(state)) {
            if (event.getStatus() == TournamentStatus.CANCELLED) throw notFound();
            return snapshot(event, state, count(tx, id), Instant.now());
        }
        Instant now = Instant.now();
        if (!isOpen(event, now)) throw closed(event, now, code != null);
        if (withdraw && state != TournamentEntrantStatus.REGISTERED) {
            throw new ApiException(HttpStatus.CONFLICT, "WITHDRAWAL_CLOSED", "This entry can no longer be withdrawn.");
        }

        // The write conflict on this shared document serializes Java entrants and Express lifecycle changes.
        var window = new Document("_id", id).append("status", TournamentStatus.REGISTRATION_OPEN.name())
                .append("$expr", new Document("$and", List.of(
                        new Document("$lte", List.of("$registrationOpeningAt", "$$NOW")),
                        new Document("$gt", List.of("$registrationClosingAt", "$$NOW")))));
        var locked = tx.findAndModify(new BasicQuery(window), new Update().inc("registrationRevision", 1),
                FindAndModifyOptions.options().returnNew(true), Tournament.class);
        if (locked == null) throw closed(event, Instant.now(), code != null);

        long registered = count(tx, id);
        Instant changedAt = Instant.now();
        if (withdraw) {
            entry.withdraw(changedAt);
            tx.save(entry);
            registered--;
            state = WITHDRAWN;
        } else if (!active(state)) {
            if (registered >= locked.getMaximumEntrants()) {
                throw new ApiException(HttpStatus.CONFLICT, "TOURNAMENT_FULL", "Tournament registration is full.");
            }
            if (entry == null) tx.insert(TournamentEntrant.registration(id, user, changedAt));
            else {
                entry.registerAgain(changedAt);
                tx.save(entry);
            }
            registered++;
            state = TournamentEntrantStatus.REGISTERED;
            if (autoStart && registered == locked.getMaximumEntrants() && locked.getStartMode() == TournamentStartMode.SIT_AND_GO
                    && TournamentSeatingService.supported(locked)) {
                locked.transitionTo(TournamentStatus.REGISTRATION_CLOSED);
                tx.updateFirst(Query.query(Criteria.where("id").is(id)),
                        new Update().set("status", locked.getStatus()).set("updatedAt", changedAt), Tournament.class);
            }
        }
        return snapshot(locked, state, registered, changedAt);
    }

    private Tournament findInvitation(String code) {
        var matches = tournaments.findFirst2ByVisibilityAndPrivateInviteCodeIgnoreCase(TournamentVisibility.PRIVATE, code);
        if (matches.size() != 1) throw invalidInvite();
        return matches.getFirst();
    }

    private static String normalize(String value) {
        String code = value == null ? "" : value.trim();
        if (code.length() < 4 || code.length() > 4096 || code.chars().anyMatch(Character::isISOControl)) throw invalidInvite();
        return code;
    }

    private static long count(MongoTemplate tx, UUID id) {
        return tx.count(activeEntries(id), TournamentEntrant.class);
    }

    private static Query activeEntries(UUID id) {
        return Query.query(Criteria.where("tournamentId").is(id).and("status").ne(WITHDRAWN));
    }

    private static boolean isOpen(Tournament event, Instant now) {
        return event.getStatus() == TournamentStatus.REGISTRATION_OPEN && !now.isBefore(event.getRegistrationOpeningAt())
                && now.isBefore(event.getRegistrationClosingAt());
    }

    private static boolean active(TournamentEntrantStatus status) {
        return status != null && status != WITHDRAWN;
    }

    private static boolean requiresMembership(Tournament event) {
        return event.getVisibility() != TournamentVisibility.PUBLIC || event.getStatus() == TournamentStatus.CANCELLED;
    }

    private static TournamentRegistration.Status snapshot(Tournament event, TournamentEntrantStatus status, long count, Instant now) {
        return new TournamentRegistration.Status(TournamentDiscoveryService.card(event, count, status),
                remaining(event.getMaximumEntrants(), count), status == TournamentEntrantStatus.REGISTERED && isOpen(event, now), now);
    }

    private static int remaining(int capacity, long count) {
        return (int) Math.max(0, capacity - count);
    }

    private static ApiException closed(Tournament event, Instant now, boolean invite) {
        if (invite) return invalidInvite();
        if (event.getStatus() == TournamentStatus.CANCELLED) {
            return new ApiException(HttpStatus.CONFLICT, "TOURNAMENT_CANCELLED", "Tournament was cancelled.");
        }
        if (event.getStatus() == TournamentStatus.REGISTRATION_OPEN && now.isBefore(event.getRegistrationOpeningAt())) {
            return new ApiException(HttpStatus.CONFLICT, "REGISTRATION_NOT_OPEN", "Registration has not opened yet.");
        }
        return new ApiException(HttpStatus.CONFLICT, "REGISTRATION_CLOSED", "Tournament registration is closed.");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TOURNAMENT_NOT_FOUND", "Tournament not found.");
    }

    private static ApiException invalidInvite() {
        return new ApiException(HttpStatus.NOT_FOUND, "INVITE_UNAVAILABLE", "Invite code is invalid or no longer available.");
    }
}
