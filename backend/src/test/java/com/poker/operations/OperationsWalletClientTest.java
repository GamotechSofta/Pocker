package com.poker.operations;

import com.poker.config.OperationsProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationsWalletClientTest {

    private HttpServer server;
    private String responseBody = "{\"status\":true,\"message\":1000}";
    private int statusCode = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/wallet/balance", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesNumericBalanceFromOperationsResponse() {
        OperationsProperties props = new OperationsProperties();
        props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.setInternalKey("test-key");

        OperationsWalletClient client = new OperationsWalletClient(props);
        BigDecimal balance = client.balance("tok_123", "PARTNER_A");
        assertEquals(new BigDecimal("1000"), balance);
    }
}
