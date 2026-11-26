package com.poker.tournament;

import com.poker.dto.AuthResponse;
import com.poker.support.AbstractMongoTest;
import com.poker.tournament.dto.TournamentDiscovery;
import com.poker.tournament.entity.BlindLevel;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentBlindSchedule;
import com.poker.tournament.entity.TournamentEntrant;
import com.poker.tournament.entity.TournamentFinalResult;
import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentTableMode;
import com.poker.tournament.entity.TournamentVisibility;
import com.poker.tournament.repository.TournamentBlindScheduleRepository;
import com.poker.tournament.repository.TournamentEntrantRepository;
import com.poker.tournament.repository.TournamentFinalResultRepository;
import com.poker.tournament.repository.TournamentRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TournamentDiscoveryIntegrationTest extends AbstractMongoTest {
    private static final String API = "/api/tournaments";
    private static final Instant OPEN = Instant.parse("2030-01-01T10:00:00Z");
    private static final Instant CLOSE = OPEN.plusSeconds(3_600);
    private static final List<BlindLevel> BLINDS = List.of(new BlindLevel(1, 10, 20, 1, 240, true));

    @LocalServerPort private int port;
    @Autowired private TournamentRepository tournaments;
    @Autowired private TournamentEntrantRepository entrants;
    @Autowired private TournamentBlindScheduleRepository schedules;
    @Autowired private TournamentFinalResultRepository results;
    @Autowired private MongoTemplate mongo;

    @Test
    void allDiscoveryEndpointsRequireAuthentication() {
        for (String path : List.of(API, API + "/my", API + "/" + UUID.randomUUID())) {
            assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(RestClientResponseException.class,
                    () -> client().get().uri(path).retrieve().toBodilessEntity()).getStatusCode());
        }
    }

    @Test
    void publicCatalogueFiltersAndPaginatesWithoutLeakingDraftsOrPrivateTournaments() {
        var viewer = guest();
        var first = tournament(TournamentVisibility.PUBLIC, TournamentStatus.PAUSED);
        var second = tournament(TournamentVisibility.PUBLIC, TournamentStatus.PAUSED);
        var privateTournament = tournament(TournamentVisibility.PRIVATE, TournamentStatus.PAUSED);
        var draft = tournament(TournamentVisibility.PUBLIC, TournamentStatus.DRAFT);
        var cancelledDraft = tournament(TournamentVisibility.PUBLIC, TournamentStatus.CANCELLED);
        // Pin both creation timestamps to exercise the deterministic UUID tiebreaker.
        var collection = mongo.getCollection("tournaments");
        collection.updateMany(new Document("_id", new Document("$in", List.of(first.getId(), second.getId()))),
                new Document("$set", new Document("createdAt", java.util.Date.from(OPEN))));
        var pageOne = get(API + "?status=PAUSED&size=1&page=0", viewer, TournamentDiscovery.Catalogue.class);
        var pageTwo = get(API + "?status=PAUSED&size=1&page=1", viewer, TournamentDiscovery.Catalogue.class);
        var repeated = get(API + "?status=PAUSED&size=1&page=0", viewer, TournamentDiscovery.Catalogue.class);
        UUID one = pageOne.tournaments().getFirst().id();
        UUID two = pageTwo.tournaments().getFirst().id();
        assertTrue(List.of(one, two).containsAll(List.of(first.getId(), second.getId())));
        assertEquals(one, repeated.tournaments().getFirst().id());
        assertEquals(2, pageOne.totalElements());
        assertNotNull(pageOne.serverTime());
        assertTrue(get(API + "?status=DRAFT,CANCELLED", viewer, TournamentDiscovery.Catalogue.class).tournaments().isEmpty());
        reject(API + "/" + privateTournament.getId(), viewer, HttpStatus.NOT_FOUND);
        reject(API + "/" + draft.getId(), viewer, HttpStatus.NOT_FOUND);
        reject(API + "/" + cancelledDraft.getId(), viewer, HttpStatus.NOT_FOUND);
    }

    @Test
    void detailsAndMineAuthorizePrivateEntriesAndReturnOnlyPlayerFields() {
        var viewer = guest();
        var other = guest();
        var event = tournament(TournamentVisibility.PRIVATE, TournamentStatus.COMPLETED);
        var entry = entrants.save(new TournamentEntrant(event.getId(), viewer.user().id(), 7200));
        results.save(new TournamentFinalResult(event.getId(), entry.getId(), viewer.user().id(), 1, 14400));
        reject(API + "/" + event.getId(), other, HttpStatus.NOT_FOUND);
        assertTrue(get(API + "/my", other, TournamentDiscovery.Catalogue.class).tournaments().isEmpty());

        var mine = get(API + "/my", viewer, TournamentDiscovery.Catalogue.class).tournaments().getFirst();
        assertEquals(event.getId(), mine.id());
        assertEquals(1, mine.registeredCount());
        assertEquals(entry.getStatus(), mine.participationStatus());
        var details = get(API + "/" + event.getId(), viewer, TournamentDiscovery.Details.class);
        assertEquals(OPEN, details.tournament().registrationOpeningAt());
        assertEquals(CLOSE.plusSeconds(3_600), details.tournament().scheduledStartAt());
        assertEquals(7200, details.tournament().startingVirtualChips());
        assertEquals(6, details.seatsPerTable());
        assertEquals(BLINDS, details.blindLevels());
        assertEquals(viewer.user().username(), details.registeredPlayers().getFirst().displayName());
        assertTrue(details.registeredPlayers().getFirst().you());
        assertEquals(1, details.results().getFirst().position());
        String json = get(API + "/" + event.getId(), viewer, String.class);
        for (String excluded : List.of("privateInviteCode", "creatorId", "passwordHash", "userId",
                viewer.user().email(), viewer.user().id().toString(), event.getPrivateInviteCode())) {
            assertFalse(json.contains(excluded), excluded);
        }
    }

    @Test
    void cancelledMembershipDoesNotAuthorizePrivateAccessOrContributeToCounts() {
        var viewer = guest();
        var other = guest();
        var hidden = tournament(TournamentVisibility.PRIVATE, TournamentStatus.REGISTRATION_OPEN);
        var event = tournament(TournamentVisibility.PUBLIC, TournamentStatus.REGISTRATION_OPEN);
        for (Tournament row : List.of(hidden, event)) {
            mongo.getCollection("tournament_entrants").insertOne(new Document("_id", UUID.randomUUID())
                    .append("tournamentId", row.getId()).append("userId", viewer.user().id()).append("status", "CANCELLED"));
        }
        entrants.save(new TournamentEntrant(event.getId(), other.user().id(), 7200));
        reject(API + "/" + hidden.getId(), viewer, HttpStatus.NOT_FOUND);
        var card = get(API + "?status=REGISTRATION_OPEN", viewer, TournamentDiscovery.Catalogue.class).tournaments()
                .stream().filter(row -> row.id().equals(event.getId())).findFirst().orElseThrow();
        assertEquals(1, card.registeredCount());
        assertEquals("CANCELLED", card.participationStatus().name());
        var details = get(API + "/" + event.getId(), viewer, TournamentDiscovery.Details.class);
        assertEquals(1, details.tournament().registeredCount());
        assertEquals(other.user().username(), details.registeredPlayers().getFirst().displayName());
    }

    @Test
    void invalidPaginationAndStatusAreBadRequests() {
        var viewer = guest();
        for (String query : List.of("?page=-1", "?size=0", "?size=51", "?status=UNKNOWN")) {
            reject(API + query, viewer, HttpStatus.BAD_REQUEST);
        }
    }

    private Tournament tournament(TournamentVisibility visibility, TournamentStatus target) {
        var id = UUID.randomUUID();
        var row = new Tournament("Discovery " + id, "Fixture", null, TournamentStartMode.SCHEDULED,
                TournamentTableMode.MULTI_TABLE, visibility, 18, 3, 6, 7200, OPEN, CLOSE, CLOSE.plusSeconds(3_600),
                visibility == TournamentVisibility.PRIVATE ? UUID.randomUUID().toString() : null, id);
        switch (target) {
            case DRAFT -> { }
            case CANCELLED, REGISTRATION_OPEN -> row.transitionTo(target);
            default -> {
                row.transitionTo(TournamentStatus.REGISTRATION_OPEN);
                row.transitionTo(TournamentStatus.STARTING);
                row.transitionTo(TournamentStatus.RUNNING);
                row.transitionTo(target);
            }
        }
        var saved = tournaments.save(row);
        schedules.save(new TournamentBlindSchedule(saved.getId(), BLINDS));
        return saved;
    }

    private RestClient client() { return RestClient.create("http://localhost:" + port); }

    private AuthResponse guest() {
        return client().post().uri("/api/auth/guest").retrieve().body(AuthResponse.class);
    }

    private <T> T get(String path, AuthResponse viewer, Class<T> type) {
        return client().get().uri(path).headers(headers -> headers.setBearerAuth(viewer.token())).retrieve().body(type);
    }

    private void reject(String path, AuthResponse viewer, HttpStatus status) {
        assertEquals(status, assertThrows(RestClientResponseException.class, () -> get(path, viewer, String.class)).getStatusCode());
    }
}
