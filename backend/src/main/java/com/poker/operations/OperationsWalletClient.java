package com.poker.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.poker.config.OperationsProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Only the game backend calls the Operations wallet API. Never expose the internal key to a browser. */
@Service
public class OperationsWalletClient {
    private final OperationsProperties properties;
    private final RestClient client = RestClient.create();

    public OperationsWalletClient(OperationsProperties properties) {
        this.properties = properties;
    }

    public BigDecimal balance(String token, String partnerCode) {
        return post("/api/v1/wallet/balance", Map.of("token", token, "partner_code", partnerCode));
    }

    public BigDecimal transfer(String token, String partnerCode, String gid, BigDecimal bet, BigDecimal win,
                               String gameType, String transactionId, int transferType, String roundId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("partner_code", partnerCode);
        body.put("gid", gid);
        body.put("bet", bet);
        body.put("win", win);
        body.put("game_type", gameType);
        body.put("tranid", transactionId);
        body.put("ttype", transferType);
        body.put("roundid", roundId);
        return post("/api/v1/wallet/credit-transfer", body);
    }

    private BigDecimal post(String path, Map<String, ?> body) {
        if (!properties.isConfigured()) throw new IllegalStateException("Operations wallet is not configured.");
        try {
            String url = properties.getBaseUrl().replaceAll("/+$", "") + path;
            JsonNode response = client.post().uri(url)
                    .header("X-Internal-Key", properties.getInternalKey())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body).retrieve().body(JsonNode.class);
            if (response == null || !response.path("status").asBoolean(false)) {
                throw new IllegalStateException(response == null ? "Operations wallet did not respond." : response.path("message").asText("Wallet operation rejected."));
            }
            JsonNode message = response.path("message");
            if (message.isMissingNode() || message.isNull()) {
                throw new IllegalStateException("Operations wallet did not return a balance.");
            }
            return new BigDecimal(message.asText());
        } catch (Exception error) {
            throw new IllegalStateException("Operations wallet request failed: " + error.getMessage(), error);
        }
    }
}
