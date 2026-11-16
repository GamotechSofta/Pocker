package com.poker;

import com.poker.dto.AuthResponse;
import com.poker.dto.GameActionRequest;
import com.poker.dto.TableDetailResponse;
import com.poker.support.AbstractMongoTest;
import com.poker.websocket.WsMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GameWsIntegrationTest extends AbstractMongoTest {

    @LocalServerPort
    private int port;

    @Test
    void connectRequiresJwt() {
        WebSocketStompClient stompClient = stompClient();
        assertThrows(Exception.class, () -> stompClient.connectAsync(
                "ws://127.0.0.1:" + port + "/ws",
                new StompSessionHandlerAdapter() {
                }
        ).get(5, TimeUnit.SECONDS));
    }

    @Test
    void sanitizedStateIsPushedAndActionsAreValidated() throws Exception {
        RestClient http = http();
        AuthResponse userA = register(http, uniqueName());
        AuthResponse userB = register(http, uniqueName());

        TableDetailResponse table = http.post()
                .uri("/api/tables")
                .header("Authorization", bearer(userA.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Live %s"}
                        """.formatted(uniqueName()))
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(table);
        UUID tableId = table.id();

        http.post()
                .uri("/api/tables/{id}/join", tableId)
                .header("Authorization", bearer(userA.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();
        http.post()
                .uri("/api/tables/{id}/join", tableId)
                .header("Authorization", bearer(userB.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();

        StompSession sessionA = connect(userA.token());
        StompSession sessionB = connect(userB.token());
        BlockingQueue<WsMessage> fromA = subscribe(sessionA, tableId);
        BlockingQueue<WsMessage> fromB = subscribe(sessionB, tableId);
        BlockingQueue<WsMessage> errorsB = subscribeErrors(sessionB);

        assertEquals("GAME_STATE", take(fromA).type());
        assertEquals("GAME_STATE", take(fromB).type());

        http.post()
                .uri("/api/tables/{id}/start", tableId)
                .header("Authorization", bearer(userA.token()))
                .retrieve()
                .toBodilessEntity();

        WsMessage startedA = take(fromA);
        WsMessage startedB = take(fromB);
        assertEquals("HAND_STARTED", startedA.type());
        assertEquals("HAND_STARTED", startedB.type());
        assertNotNull(startedA.state());
        assertEquals("PRE_FLOP", startedA.state().status());

        var aOwn = startedA.state().players().stream()
                .filter(player -> player.playerId().equals(userA.user().id()))
                .findFirst()
                .orElseThrow();
        var aOpp = startedA.state().players().stream()
                .filter(player -> player.playerId().equals(userB.user().id()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, aOwn.holeCards().size());
        assertFalse(aOwn.holeCards().contains("HIDDEN"));
        assertEquals(List.of("HIDDEN", "HIDDEN"), aOpp.holeCards());
        assertTrue(aOwn.connected());

        var bOpp = startedB.state().players().stream()
                .filter(player -> player.playerId().equals(userA.user().id()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("HIDDEN", "HIDDEN"), bOpp.holeCards());

        sessionB.send("/app/game/" + tableId + "/action", new GameActionRequest("FOLD", null, null, null));
        assertEquals("ERROR", take(fromB).type());
        WsMessage error = take(errorsB);
        assertEquals("ERROR", error.type());
        assertEquals("NOT_PLAYER_TURN", error.code());

        sessionA.send("/app/game/" + tableId + "/action", new GameActionRequest("FOLD", null, null, "fold-1"));
        WsMessage completedA = take(fromA);
        WsMessage completedB = take(fromB);
        assertEquals("HAND_COMPLETED", completedA.type());
        assertEquals("HAND_COMPLETED", completedB.type());
        var stillHidden = completedB.state().players().stream()
                .filter(player -> player.playerId().equals(userA.user().id()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("HIDDEN", "HIDDEN"), stillHidden.holeCards());

        sessionA.send("/app/game/" + tableId + "/action", new GameActionRequest("FOLD", null, null, "fold-1"));
        assertTrue(fromA.poll(400, TimeUnit.MILLISECONDS) == null);

        sessionA.disconnect();
        sessionB.disconnect();
    }

    @Test
    void reconnectResubscribesAndReceivesCurrentState() throws Exception {
        RestClient http = http();
        AuthResponse user = register(http, uniqueName());
        TableDetailResponse table = http.post()
                .uri("/api/tables")
                .header("Authorization", bearer(user.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Reconnect %s"}
                        """.formatted(uniqueName()))
                .retrieve()
                .body(TableDetailResponse.class);
        assertNotNull(table);
        UUID tableId = table.id();
        http.post()
                .uri("/api/tables/{id}/join", tableId)
                .header("Authorization", bearer(user.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toBodilessEntity();

        StompSession session = connect(user.token());
        BlockingQueue<WsMessage> first = subscribe(session, tableId);
        assertEquals("GAME_STATE", take(first).type());
        session.disconnect();

        StompSession again = connect(user.token());
        BlockingQueue<WsMessage> second = subscribe(again, tableId);
        WsMessage restored = takeType(second, "GAME_STATE");
        assertNotNull(restored.state());
        var self = restored.state().players().stream()
                .filter(player -> player.playerId().equals(user.user().id()))
                .findFirst()
                .orElseThrow();
        assertTrue(self.connected());
        again.disconnect();
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return stompClient().connectAsync(
                "ws://127.0.0.1:" + port + "/ws",
                new org.springframework.web.socket.WebSocketHttpHeaders(),
                headers,
                new StompSessionHandlerAdapter() {
                }
        ).get(5, TimeUnit.SECONDS);
    }

    private static BlockingQueue<WsMessage> subscribe(StompSession session, UUID tableId) {
        BlockingQueue<WsMessage> queue = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/game/" + tableId, handler(queue));
        return queue;
    }

    private static BlockingQueue<WsMessage> subscribeErrors(StompSession session) {
        BlockingQueue<WsMessage> queue = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/errors", handler(queue));
        return queue;
    }

    private static StompFrameHandler handler(BlockingQueue<WsMessage> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return WsMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((WsMessage) payload);
            }
        };
    }

    private static WsMessage take(BlockingQueue<WsMessage> queue) throws InterruptedException {
        WsMessage message = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(message, "timed out waiting for a WebSocket message");
        return message;
    }

    private static WsMessage takeType(BlockingQueue<WsMessage> queue, String type) throws InterruptedException {
        for (int i = 0; i < 8; i++) {
            WsMessage message = take(queue);
            if (type.equals(message.type())) {
                return message;
            }
        }
        throw new AssertionError("did not receive " + type);
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        // Jackson 3, like the server: game state carries java.time values such as turnDeadline.
        client.setMessageConverter(new JacksonJsonMessageConverter());
        return client;
    }

    private RestClient http() {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
    }

    private AuthResponse register(RestClient client, String username) {
        AuthResponse body = client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"%s","email":"%s@example.com","password":"password1"}
                        """.formatted(username, username))
                .retrieve()
                .body(AuthResponse.class);
        assertNotNull(body);
        return body;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueName() {
        return "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
