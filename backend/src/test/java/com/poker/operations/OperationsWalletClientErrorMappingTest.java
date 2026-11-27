package com.poker.operations;

import com.poker.config.OperationsProperties;
import com.poker.exception.ApiException;
import com.poker.wallet.InsufficientBalanceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsWalletClientErrorMappingTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private HttpServer server;
    private OperationsWalletClient client;
    private final AtomicInteger statusCode = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{\"status\":true,\"balance\":500.0}");
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastKeyHeader.set(exchange.getRequestHeaders().getFirst("X-Internal-Key"));
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode.get(), payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();

        OperationsProperties props = new OperationsProperties();
        props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.setInternalKey("test-internal-key");
        client = new OperationsWalletClient(props);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsNumericGidAndExpectedTransferFields() throws Exception {
        statusCode.set(200);
        responseBody.set("{\"status\":true,\"balance\":350.50}");

        BigDecimal balance = client.transfer(
                "tok-1",
                "DPBOSS",
                "42",
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                "spin",
                "TRAN-1",
                1,
                "ROUND-1"
        );

        assertEquals(0, new BigDecimal("350.50").compareTo(balance));
        assertEquals("/api/v1/wallet/credit-transfer", lastPath.get());
        assertEquals("test-internal-key", lastKeyHeader.get());
        JsonNode sent = MAPPER.readTree(lastRequestBody.get());
        assertEquals(42L, sent.get("gid").asLong());
        assertTrue(sent.get("gid").isNumber());
        assertEquals("TRAN-1", sent.get("tranid").asString());
        assertEquals(1, sent.get("ttype").asInt());
        assertEquals("ROUND-1", sent.get("roundid").asString());
    }

    @Test
    void supportsNumericAndStringGidsWhileRejectingBlankOrControlChars() {
        assertEquals(42L, OperationsWalletClient.wireGid("0042"));
        assertEquals("POKERRAZZ", OperationsWalletClient.wireGid(" POKERRAZZ "));
        assertEquals("0", OperationsWalletClient.wireGid("0"));
        assertThrows(IllegalArgumentException.class, () -> OperationsWalletClient.normalizeGid(" "));
        assertThrows(IllegalArgumentException.class, () -> OperationsWalletClient.normalizeGid("bad\ngid"));
    }

    @Test
    void mapsErr0004UnauthorizedInternalRequestToWalletUnavailable502() {
        statusCode.set(401);
        responseBody.set("{\"status\":false,\"message\":\"ERR-0004: Unauthorized internal request.\"}");

        ApiException ex = assertThrows(ApiException.class, () -> client.balance("tok-1", "DPBOSS"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertEquals("WALLET_UNAVAILABLE", ex.getCode());
    }

    @Test
    void mapsErr0011InvalidSessionTokenAnd401SessionErrorsToOperatorSessionExpired() {
        statusCode.set(404);
        responseBody.set("{\"status\":false,\"message\":\"ERR-0011: Invalid session token.\"}");

        ApiException ex404 = assertThrows(ApiException.class, () -> client.balance("tok-1", "DPBOSS"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex404.getStatus());
        assertEquals("OPERATOR_SESSION_EXPIRED", ex404.getCode());

        statusCode.set(401);
        responseBody.set("{\"status\":false,\"message\":\"Session expired\"}");
        ApiException ex401 = assertThrows(ApiException.class, () -> client.balance("tok-1", "DPBOSS"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex401.getStatus());
        assertEquals("OPERATOR_SESSION_EXPIRED", ex401.getCode());
    }

    @Test
    void mapsInsufficientBalanceAnd5xxResponses() {
        statusCode.set(400);
        responseBody.set("{\"status\":false,\"message\":\"Insufficient balance for bet.\"}");
        assertThrows(InsufficientBalanceException.class, () -> client.transfer(
                "tok-1", "DPBOSS", "7", new BigDecimal("500"), BigDecimal.ZERO, "spin", "T-LOW", 1, "R-1"));

        statusCode.set(503);
        responseBody.set("{\"status\":false,\"message\":\"Gateway timeout\"}");
        ApiException ex5xx = assertThrows(ApiException.class, () -> client.balance("tok-1", "DPBOSS"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex5xx.getStatus());
        assertEquals("WALLET_UNAVAILABLE", ex5xx.getCode());
    }
}
