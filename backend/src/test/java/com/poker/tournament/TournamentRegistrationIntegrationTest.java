package com.poker.tournament;

import com.poker.dto.AuthResponse;
import com.poker.support.AbstractMongoTest;
import com.poker.tournament.dto.TournamentRegistration;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentEntrantStatus;
import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentTableMode;
import com.poker.tournament.entity.TournamentVisibility;
import com.poker.tournament.repository.TournamentEntrantRepository;
import com.poker.tournament.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TournamentRegistrationIntegrationTest extends AbstractMongoTest {
    private static final String API = "/api/tournaments";
    private static final TournamentEntrantStatus CANCELLED = TournamentEntrantStatus.CANCELLED;

    @LocalServerPort private int port;
    @Autowired private TournamentRepository tournaments;
    @Autowired private TournamentEntrantRepository entrants;
    @Autowired private MongoTemplate mongo;

    @Test
    void authenticationAndOwnershipProtectWaitingRoom() {
        var event = open(2);
        var person = guest();
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(RestClientResponseException.class,
                () -> client().post().uri(API + "/" + event.getId() + "/register").retrieve().toBodilessEntity()).getStatusCode());
        assertEquals(404, getStatus(event, "/waiting-room", person));
        assertEquals(0, snapshot(event, person).tournament().registeredCount());
        assertEquals(200, postStatus(event, "/register", person));
        assertEquals(404, getStatus(event, "/waiting-room", guest()));
    }

    @Test
    void duplicateRegistrationAndWithdrawalPreserveOneUnfundedEntrant() {
        var event = open(2);
        var person = guest();
        assertEquals(List.of(200, 200), race(
                () -> postStatus(event, "/register", person), () -> postStatus(event, "/register", person)));
        var original = entrants.findByTournamentIdAndUserId(event.getId(), person.user().id()).orElseThrow();
        assertEquals(0, original.getCurrentVirtualChips());
        assertEquals(0, original.getInitialVirtualChips());
        assertEquals(1, snapshot(event, person).tournament().registeredCount());
        assertEquals(200, postStatus(event, "/unregister", person));
        assertEquals(200, postStatus(event, "/unregister", person));
        assertEquals(0, snapshot(event, person).tournament().registeredCount());
        assertEquals(200, postStatus(event, "/register", person));
        assertEquals(original.getId(), entrants.findByTournamentIdAndUserId(event.getId(), person.user().id()).orElseThrow().getId());
    }

    @Test
    void simultaneousLastSeatRequestsHaveOneWinner() {
        var event = open(2);
        var first = guest();
        var second = guest();
        var third = guest();
        assertEquals(200, postStatus(event, "/register", first));
        var responses = race(() -> postStatus(event, "/register", second), () -> postStatus(event, "/register", third));
        assertEquals(List.of(200, 409), responses.stream().sorted().toList());
        var room = request(event, "/waiting-room", first).retrieve().body(TournamentRegistration.WaitingRoom.class);
        assertEquals(2, room.details().tournament().registeredCount());
        assertEquals(2, room.details().registeredPlayers().size());
        assertEquals(0, room.remainingSeats());
    }

    @Test
    void withdrawalCompetingWithRegistrationNeverOverfills() {
        var event = open(2);
        var first = guest();
        var second = guest();
        var third = guest();
        assertEquals(200, postStatus(event, "/register", first));
        assertEquals(200, postStatus(event, "/register", second));
        var responses = race(() -> postStatus(event, "/unregister", first), () -> postStatus(event, "/register", third));
        assertEquals(200, responses.getFirst());
        assertTrue(List.of(200, 409).contains(responses.getLast()));
        assertEquals(200, postStatus(event, "/register", third));
        assertEquals(2, snapshot(event, third).tournament().registeredCount());
        assertEquals(CANCELLED, snapshot(event, first).tournament().participationStatus());
    }

    @Test
    void lifecycleAndWindowGuardRejectMutationsAndRetainMembershipOnCancellation() {
        var now = Instant.now();
        var person = guest();
        var early = tournament(2, null, now.plusSeconds(60), now.plusSeconds(120), TournamentStatus.REGISTRATION_OPEN);
        var late = tournament(2, null, now.minusSeconds(120), now.minusSeconds(60), TournamentStatus.REGISTRATION_OPEN);
        var draft = tournament(2, null, now.minusSeconds(120), now.plusSeconds(60), TournamentStatus.DRAFT);
        assertEquals(409, postStatus(early, "/register", person));
        assertEquals(409, postStatus(late, "/register", person));
        assertEquals(404, postStatus(draft, "/register", person));
        var event = open(2);
        assertEquals(200, postStatus(event, "/register", person));
        var contender = guest();
        var responses = race(() -> postStatus(event, "/register", contender), () -> {
            mongo.updateFirst(Query.query(Criteria.where("id").is(event.getId())),
                    new Update().set("status", TournamentStatus.CANCELLED), Tournament.class);
            return 200;
        });
        assertTrue(List.of(200, 409).contains(responses.getFirst()));
        assertEquals(409, postStatus(event, "/register", guest()));
        assertEquals(409, postStatus(event, "/unregister", person));
        var room = request(event, "/waiting-room", person).retrieve().body(TournamentRegistration.WaitingRoom.class);
        assertEquals(TournamentStatus.CANCELLED, room.details().tournament().status());
        assertFalse(room.withdrawalAllowed());
        assertEquals(responses.getFirst() == 200 ? 2 : 1, room.details().tournament().registeredCount());
    }

    @Test
    void privateInvitesAuthorizeTheExactEventAndExpiredOrInvalidCodesAreIndistinguishable() {
        var now = Instant.now();
        var person = guest();
        var code = "Phase5-" + UUID.randomUUID();
        var event = tournament(2, code, now.minusSeconds(60), now.plusSeconds(600), TournamentStatus.REGISTRATION_OPEN);
        var expired = tournament(2, "Phase5-expired", now.minusSeconds(120), now.minusSeconds(60), TournamentStatus.REGISTRATION_OPEN);
        assertEquals(404, postStatus(event, "/register", person));
        assertEquals(404, getStatus(event, "/waiting-room", person));
        var wrong = invite(person, "invalid", null, "/resolve");
        var closed = invite(person, expired.getPrivateInviteCode(), null, "/resolve");
        assertEquals(404, wrong.getStatusCode().value());
        assertEquals(404, closed.getStatusCode().value());
        assertTrue(wrong.getResponseBodyAsString().contains("INVITE_UNAVAILABLE"));
        assertTrue(closed.getResponseBodyAsString().contains("INVITE_UNAVAILABLE"));
        assertEquals(404, invite(person, code, expired.getId(), "/register").getStatusCode().value());
        var resolution = privateRequest(person, "  " + code.toUpperCase(java.util.Locale.ROOT) + "  ", null, "/resolve")
                .retrieve().body(String.class);
        assertTrue(resolution.contains(event.getId().toString()));
        assertFalse(resolution.contains(code));
        assertEquals(200, privateRequest(person, code, event.getId(), "/register").retrieve().toBodilessEntity().getStatusCode().value());
        assertEquals(200, getStatus(event, "/waiting-room", person));
        assertEquals(200, postStatus(event, "/unregister", person));
        assertEquals(404, getStatus(event, "/waiting-room", person));
    }

    @Test
    void repeatedInviteAttemptsAreRateLimited() {
        var person = guest();
        for (int attempt = 0; attempt < 23; attempt++) {
            var response = invite(person, "invalid", null, "/resolve");
            if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                assertTrue(response.getResponseBodyAsString().contains("INVITE_RATE_LIMIT"));
                return;
            }
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
        throw new AssertionError("Invite requests were never throttled");
    }

    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) {
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var replies = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (var request : List.of(first, second)) replies.add(threads.submit(() -> { start.await(); return request.call(); }));
            start.countDown();
            return List.of(replies.getFirst().get(), replies.getLast().get());
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private Tournament open(int seats) {
        var now = Instant.now();
        return tournament(seats, null, now.minusSeconds(60), now.plusSeconds(600), TournamentStatus.REGISTRATION_OPEN);
    }

    private Tournament tournament(int seats, String code, Instant start, Instant end, TournamentStatus status) {
        var tournament = new Tournament("Registration " + UUID.randomUUID(), null, null, TournamentStartMode.SIT_AND_GO,
                TournamentTableMode.SINGLE_TABLE, code == null ? TournamentVisibility.PUBLIC : TournamentVisibility.PRIVATE,
                seats, 2, seats, 8000, start, end, null, code, UUID.randomUUID());
        tournament.transitionTo(status);
        return tournaments.save(tournament);
    }

    private RestClient client() { return RestClient.create("http://localhost:" + port); }
    private AuthResponse guest() { return client().post().uri("/api/auth/guest").retrieve().body(AuthResponse.class); }

    private RestClient.RequestHeadersSpec<?> request(Tournament event, String suffix, AuthResponse user) {
        return client().get().uri(API + "/" + event.getId() + suffix).headers(headers -> headers.setBearerAuth(user.token()));
    }

    private int getStatus(Tournament event, String suffix, AuthResponse user) {
        return request(event, suffix, user).exchange((req, res) -> res.getStatusCode().value());
    }

    private int postStatus(Tournament event, String suffix, AuthResponse user) {
        return client().post().uri(API + "/" + event.getId() + suffix).headers(headers -> headers.setBearerAuth(user.token()))
                .exchange((req, res) -> res.getStatusCode().value());
    }

    private TournamentRegistration.Status snapshot(Tournament event, AuthResponse user) {
        return request(event, "/registration", user).retrieve().body(TournamentRegistration.Status.class);
    }

    private RestClient.RequestHeadersSpec<?> privateRequest(AuthResponse user, String code, UUID id, String suffix) {
        return client().post().uri(API + "/private" + suffix).headers(headers -> headers.setBearerAuth(user.token()))
                .contentType(MediaType.APPLICATION_JSON).body(new TournamentRegistration.InviteRequest(code, id));
    }

    private RestClientResponseException invite(AuthResponse user, String code, UUID id, String suffix) {
        return assertThrows(RestClientResponseException.class, () -> privateRequest(user, code, id, suffix).retrieve().toBodilessEntity());
    }
}
