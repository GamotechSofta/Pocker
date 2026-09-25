package com.poker;

import com.poker.dto.AuthResponse;
import com.poker.dto.TableDetailResponse;
import com.poker.dto.TableStateResponse;
import com.poker.dto.TableSummaryResponse;
import com.poker.support.AbstractMongoTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TableApiIntegrationTest extends AbstractMongoTest {

    @LocalServerPort
    private int port;

    @Test
    void tablesRequireAuthentication() {
        RestClientResponseException ex = assertThrows(RestClientResponseException.class, () ->
                client().get().uri("/api/tables").retrieve().toBodilessEntity()
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void createListJoinStartAndSanitizedState() {
        RestClient client = client();
        String tokenA = register(client, uniqueName());
        String tokenB = register(client, uniqueName());

        ResponseEntity<TableDetailResponse> created = client.post()
                .uri("/api/tables")
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Hold'em %s"}
                        """.formatted(uniqueName()))
                .retrieve()
                .toEntity(TableDetailResponse.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        TableDetailResponse table = created.getBody();
        assertNotNull(table);
        assertEquals("TEXAS_HOLDEM", table.variant());
        assertEquals(10, table.smallBlind());
        assertEquals(20, table.bigBlind());
        assertEquals(6, table.maxPlayers());
        assertEquals("WAITING", table.status());
        assertTrue(table.players().isEmpty());
        UUID tableId = table.id();

        TableDetailResponse fetched = client.get()
                .uri("/api/tables/{id}", tableId)
                .header("Authorization", bearer(tokenA))
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(fetched);
        assertEquals(table.name(), fetched.name());

        List<TableSummaryResponse> listed = client.get()
                .uri("/api/tables")
                .header("Authorization", bearer(tokenA))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assertNotNull(listed);
        assertTrue(listed.stream().anyMatch(item -> item.id().equals(tableId) && item.playerCount() == 0));

        TableDetailResponse joinedA = client.post()
                .uri("/api/tables/{id}/join", tableId)
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(joinedA);
        assertEquals(1, joinedA.players().size());
        assertEquals(0, joinedA.players().getFirst().seat());
        assertEquals(1000, joinedA.players().getFirst().chipStack());

        TableDetailResponse joinedB = client.post()
                .uri("/api/tables/{id}/join", tableId)
                .header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"seat\":2}")
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(joinedB);
        assertEquals(2, joinedB.players().size());

        TableStateResponse started = client.post()
                .uri("/api/tables/{id}/start", tableId)
                .header("Authorization", bearer(tokenA))
                .retrieve()
                .body(TableStateResponse.class);
        assertNotNull(started);
        assertEquals("PRE_FLOP", started.status());
        assertEquals(2, started.players().size());
        assertTrue(started.communityCards().isEmpty());

        var viewer = started.players().stream()
                .filter(player -> player.playerId().equals(started.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, viewer.holeCards().size());
        assertFalse(viewer.holeCards().contains("HIDDEN"));

        var opponent = started.players().stream()
                .filter(player -> !player.playerId().equals(started.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("HIDDEN", "HIDDEN"), opponent.holeCards());
        assertTrue(started.legalActions().actions().contains("FOLD"));

        TableStateResponse fromB = client.get()
                .uri("/api/tables/{id}/state", tableId)
                .header("Authorization", bearer(tokenB))
                .retrieve()
                .body(TableStateResponse.class);
        assertNotNull(fromB);
        var bOwn = fromB.players().stream()
                .filter(player -> player.playerId().equals(fromB.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        var aHidden = fromB.players().stream()
                .filter(player -> !player.playerId().equals(fromB.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        assertFalse(bOwn.holeCards().contains("HIDDEN"));
        assertEquals(List.of("HIDDEN", "HIDDEN"), aHidden.holeCards());
        assertFalse(bOwn.holeCards().equals(viewer.holeCards()));
    }

    @Test
    void omahaDealsFourHoleCardsAndHidesOpponents() {
        RestClient client = client();
        String tokenA = register(client, uniqueName());
        String tokenB = register(client, uniqueName());

        TableDetailResponse table = client.post()
                .uri("/api/tables")
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Omaha %s","variant":"OMAHA"}
                        """.formatted(uniqueName()))
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(table);
        assertEquals("OMAHA", table.variant());

        client.post()
                .uri("/api/tables/{id}/join", table.id())
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();
        client.post()
                .uri("/api/tables/{id}/join", table.id())
                .header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();

        TableStateResponse started = client.post()
                .uri("/api/tables/{id}/start", table.id())
                .header("Authorization", bearer(tokenA))
                .retrieve()
                .body(TableStateResponse.class);
        assertNotNull(started);
        assertEquals("OMAHA", started.variant());
        assertEquals("PRE_FLOP", started.status());

        var viewer = started.players().stream()
                .filter(player -> player.playerId().equals(started.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        var opponent = started.players().stream()
                .filter(player -> !player.playerId().equals(started.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        assertEquals(4, viewer.holeCards().size());
        assertFalse(viewer.holeCards().contains("HIDDEN"));
        assertEquals(List.of("HIDDEN", "HIDDEN", "HIDDEN", "HIDDEN"), opponent.holeCards());
    }

    @Test
    void pineappleDealsThreeAndEntersDiscardPhase() {
        RestClient client = client();
        String tokenA = register(client, uniqueName());
        String tokenB = register(client, uniqueName());

        TableDetailResponse table = client.post()
                .uri("/api/tables")
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Pineapple %s","variant":"PINEAPPLE"}
                        """.formatted(uniqueName()))
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(table);
        assertEquals("PINEAPPLE", table.variant());

        client.post()
                .uri("/api/tables/{id}/join", table.id())
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();
        client.post()
                .uri("/api/tables/{id}/join", table.id())
                .header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();

        TableStateResponse started = client.post()
                .uri("/api/tables/{id}/start", table.id())
                .header("Authorization", bearer(tokenA))
                .retrieve()
                .body(TableStateResponse.class);
        assertNotNull(started);
        assertEquals("PINEAPPLE", started.variant());
        assertEquals("DISCARD", started.status());

        var viewer = started.players().stream()
                .filter(player -> player.playerId().equals(started.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        var opponent = started.players().stream()
                .filter(player -> !player.playerId().equals(started.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        assertEquals(3, viewer.holeCards().size());
        assertFalse(viewer.holeCards().contains("HIDDEN"));
        assertEquals(List.of("HIDDEN", "HIDDEN", "HIDDEN"), opponent.holeCards());
        if (viewer.isTurn()) {
            assertTrue(started.legalActions().actions().contains("DISCARD"));
        }
    }

    @Test
    void fiveCardDrawDealsFiveAndStartsFirstBetting() {
        RestClient client = client();
        String tokenA = register(client, uniqueName());
        String tokenB = register(client, uniqueName());

        TableDetailResponse table = client.post()
                .uri("/api/tables")
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Draw %s","variant":"FIVE_CARD_DRAW"}
                        """.formatted(uniqueName()))
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(table);
        assertEquals("FIVE_CARD_DRAW", table.variant());

        client.post()
                .uri("/api/tables/{id}/join", table.id())
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();
        client.post()
                .uri("/api/tables/{id}/join", table.id())
                .header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();

        TableStateResponse started = client.post()
                .uri("/api/tables/{id}/start", table.id())
                .header("Authorization", bearer(tokenA))
                .retrieve()
                .body(TableStateResponse.class);
        assertNotNull(started);
        assertEquals("FIVE_CARD_DRAW", started.variant());
        assertEquals("PRE_FLOP", started.status());
        assertTrue(started.communityCards().isEmpty());

        var viewer = started.players().stream()
                .filter(player -> player.playerId().equals(started.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        var opponent = started.players().stream()
                .filter(player -> !player.playerId().equals(started.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        assertEquals(5, viewer.holeCards().size());
        assertFalse(viewer.holeCards().contains("HIDDEN"));
        assertEquals(List.of("HIDDEN", "HIDDEN", "HIDDEN", "HIDDEN", "HIDDEN"), opponent.holeCards());
    }

    @Test
    void razzDealsThreeAndStartsThirdStreet() {
        RestClient client = client();
        String tokenA = register(client, uniqueName());
        String tokenB = register(client, uniqueName());

        TableDetailResponse table = client.post()
                .uri("/api/tables")
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Razz %s","variant":"RAZZ"}
                        """.formatted(uniqueName()))
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(table);
        assertEquals("RAZZ", table.variant());

        client.post()
                .uri("/api/tables/{id}/join", table.id())
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();
        client.post()
                .uri("/api/tables/{id}/join", table.id())
                .header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();

        TableStateResponse started = client.post()
                .uri("/api/tables/{id}/start", table.id())
                .header("Authorization", bearer(tokenA))
                .retrieve()
                .body(TableStateResponse.class);
        assertNotNull(started);
        assertEquals("RAZZ", started.variant());
        assertEquals("THIRD_STREET", started.status());
        assertTrue(started.communityCards().isEmpty());

        var viewer = started.players().stream()
                .filter(player -> player.playerId().equals(started.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        var opponent = started.players().stream()
                .filter(player -> !player.playerId().equals(started.viewerPlayerId()))
                .findFirst()
                .orElseThrow();
        assertEquals(3, viewer.holeCards().size());
        assertFalse(viewer.holeCards().contains("HIDDEN"));
        assertEquals(List.of("HIDDEN", "HIDDEN", "HIDDEN"), opponent.holeCards());
    }

    @Test
    void unknownVariantIsRejected() {
        RestClient client = client();
        String token = register(client, uniqueName());
        RestClientResponseException ex = assertThrows(RestClientResponseException.class, () ->
                client.post()
                        .uri("/api/tables")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"name":"Unknown table","variant":"BAD_VARIANT"}
                                """)
                        .retrieve()
                        .toBodilessEntity()
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getResponseBodyAsString().contains("INVALID_GAME_ACTION"));
        assertTrue(ex.getResponseBodyAsString().contains("OMAHA"));
    }

    @Test
    void validationAndTableRules() {
        RestClient client = client();
        String token = register(client, uniqueName());

        RestClientResponseException invalid = assertThrows(RestClientResponseException.class, () ->
                client.post()
                        .uri("/api/tables")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"name":""}
                                """)
                        .retrieve()
                        .toBodilessEntity()
        );
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        assertTrue(invalid.getResponseBodyAsString().contains("VALIDATION_ERROR"));

        RestClientResponseException missing = assertThrows(RestClientResponseException.class, () ->
                client.get()
                        .uri("/api/tables/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(token))
                        .retrieve()
                        .toBodilessEntity()
        );
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertTrue(missing.getResponseBodyAsString().contains("GAME_NOT_FOUND"));

        TableDetailResponse table = client.post()
                .uri("/api/tables")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Solo %s"}
                        """.formatted(uniqueName()))
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(table);

        client.post()
                .uri("/api/tables/{id}/join", table.id())
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();

        RestClientResponseException again = assertThrows(RestClientResponseException.class, () ->
                client.post()
                        .uri("/api/tables/{id}/join", table.id())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}")
                        .retrieve()
                        .toBodilessEntity()
        );
        assertEquals(HttpStatus.BAD_REQUEST, again.getStatusCode());
        assertTrue(again.getResponseBodyAsString().contains("INVALID_GAME_ACTION"));

        RestClientResponseException startAlone = assertThrows(RestClientResponseException.class, () ->
                client.post()
                        .uri("/api/tables/{id}/start", table.id())
                        .header("Authorization", bearer(token))
                        .retrieve()
                        .toBodilessEntity()
        );
        assertEquals(HttpStatus.BAD_REQUEST, startAlone.getStatusCode());
        assertTrue(startAlone.getResponseBodyAsString().contains("two players"));

        TableDetailResponse left = client.post()
                .uri("/api/tables/{id}/leave", table.id())
                .header("Authorization", bearer(token))
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(left);
        assertTrue(left.players().isEmpty());
    }

    @Test
    void takenSeatAndSecondStartAreRejected() {
        RestClient client = client();
        String tokenA = register(client, uniqueName());
        String tokenB = register(client, uniqueName());

        TableDetailResponse table = client.post()
                .uri("/api/tables")
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Seats %s"}
                        """.formatted(uniqueName()))
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(table);

        client.post()
                .uri("/api/tables/{id}/join", table.id())
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"seat\":1}")
                .retrieve()
                .toBodilessEntity();

        RestClientResponseException seatTaken = assertThrows(RestClientResponseException.class, () ->
                client.post()
                        .uri("/api/tables/{id}/join", table.id())
                        .header("Authorization", bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"seat\":1}")
                        .retrieve()
                        .toBodilessEntity()
        );
        assertEquals(HttpStatus.CONFLICT, seatTaken.getStatusCode());
        assertTrue(seatTaken.getResponseBodyAsString().contains("SEAT_UNAVAILABLE"));

        client.post()
                .uri("/api/tables/{id}/join", table.id())
                .header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"seat\":2}")
                .retrieve()
                .toBodilessEntity();

        client.post()
                .uri("/api/tables/{id}/start", table.id())
                .header("Authorization", bearer(tokenA))
                .retrieve()
                .toBodilessEntity();

        RestClientResponseException already = assertThrows(RestClientResponseException.class, () ->
                client.post()
                        .uri("/api/tables/{id}/start", table.id())
                        .header("Authorization", bearer(tokenA))
                        .retrieve()
                        .toBodilessEntity()
        );
        assertEquals(HttpStatus.CONFLICT, already.getStatusCode());
        assertTrue(already.getResponseBodyAsString().contains("GAME_ALREADY_STARTED"));
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
    }

    private String register(RestClient client, String username) {
        AuthResponse body = client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"%s","email":"%s@example.com","password":"password1"}
                        """.formatted(username, username))
                .retrieve()
                .body(AuthResponse.class);
        assertNotNull(body);
        return body.token();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueName() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
