package com.poker.operations;

import com.poker.config.OperationsProperties;
import com.poker.exception.ApiException;
import com.poker.exception.ErrorCodes;
import com.poker.wallet.InsufficientBalanceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Only the game backend calls the Operations wallet API. Never expose the internal key to a browser. */
@Service
public class OperationsWalletClient {
    private static final Logger log = LoggerFactory.getLogger(OperationsWalletClient.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final OperationsProperties properties;
    private final RestClient client;

    public OperationsWalletClient(OperationsProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public BigDecimal balance(String token, String partnerCode) {
        return post("/api/v1/wallet/balance", Map.of("token", token, "partner_code", partnerCode));
    }

    public BigDecimal transfer(String token, String partnerCode, String gid, BigDecimal bet, BigDecimal win,
                               String gameType, String transactionId, int transferType, String roundId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("partner_code", partnerCode);
        body.put("gid", numericGid(gid));
        body.put("bet", bet);
        body.put("win", win);
        body.put("game_type", gameType);
        body.put("tranid", transactionId);
        body.put("ttype", transferType);
        body.put("roundid", roundId);
        return post("/api/v1/wallet/credit-transfer", body);
    }

    public static long numericGid(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("gid must be a positive integer.");
        }
        String trimmed = raw.trim().replaceFirst("^0+(?=[1-9])", "");
        if (!trimmed.matches("[1-9][0-9]{0,17}")) {
            throw new IllegalArgumentException("gid must be a positive integer.");
        }
        return Long.parseLong(trimmed);
    }

    private BigDecimal post(String path, Map<String, ?> body) {
        if (!properties.isConfigured()) {
            log.error("Operations wallet endpoint {} called while app.operations.base-url or internal-key is unset", path);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.WALLET_UNAVAILABLE, "Operations wallet is not configured.");
        }
        String url = properties.getBaseUrl().trim().replaceAll("/+$", "") + path;
        UpstreamResponse upstream;
        try {
            upstream = client.post().uri(url)
                    .header("X-Internal-Key", properties.getInternalKey().trim())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .exchange((req, res) -> readUpstream(res.getStatusCode(), res.getBody().readAllBytes()));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception error) {
            log.error("Operations wallet request to {} failed: {}", path, error.getMessage(), error);
            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.WALLET_UNAVAILABLE, "Operations wallet is temporarily unreachable.");
        }

        JsonNode parsed = parseJson(upstream.body());
        if (upstream.status().isError() || parsed == null || !parsed.path("status").asBoolean(false)) {
            throw mapRejection(path, upstream.status(), parsed, upstream.body());
        }

        BigDecimal balance = extractBalance(parsed);
        if (balance == null) {
            log.error("Operations wallet {} succeeded without parseable balance: {}", path, upstream.body());
            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.WALLET_UNAVAILABLE, "Operations wallet did not return a balance.");
        }
        return balance;
    }

    private static UpstreamResponse readUpstream(HttpStatusCode status, byte[] bytes) throws IOException {
        return new UpstreamResponse(status, bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8));
    }

    private static JsonNode parseJson(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(rawBody);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ApiException mapRejection(String path, HttpStatusCode status, JsonNode parsed, String rawBody) {
        String upstreamMessage = extractTextMessage(parsed, rawBody);
        String normalized = upstreamMessage.toLowerCase(Locale.ROOT);
        log.warn("Operations wallet {} rejected with HTTP {}: {}", path, status.value(), upstreamMessage);

        if (normalized.contains("insufficient") || normalized.contains("low balance") || normalized.contains("not enough")) {
            return new InsufficientBalanceException(upstreamMessage);
        }
        if (normalized.contains("err-0004") || normalized.contains("unauthorized internal request")) {
            return new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.WALLET_UNAVAILABLE, upstreamMessage);
        }
        if (status.value() == 401 || status.value() == 403 || status.value() == 404
                || normalized.contains("err-0011") || normalized.contains("session token")
                || normalized.contains("session expired") || normalized.contains("expired")
                || normalized.contains("unauthorized")) {
            return new ApiException(HttpStatus.UNAUTHORIZED, "OPERATOR_SESSION_EXPIRED", upstreamMessage);
        }
        if (status.value() == 400 || status.value() == 422) {
            return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, upstreamMessage);
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.WALLET_UNAVAILABLE, upstreamMessage);
    }

    private static String extractTextMessage(JsonNode parsed, String rawBody) {
        if (parsed != null) {
            JsonNode msg = parsed.path("message");
            if (msg.isString() && !msg.asString("").isBlank()) {
                return msg.asString("").trim();
            }
            if (msg.isObject() && msg.path("message").isString()) {
                return msg.path("message").asString("").trim();
            }
            if (parsed.path("error").isString() && !parsed.path("error").asString("").isBlank()) {
                return parsed.path("error").asString("").trim();
            }
        }
        if (rawBody != null && !rawBody.isBlank()) {
            return rawBody.length() > 200 ? rawBody.substring(0, 200) : rawBody;
        }
        return "Operations wallet request rejected.";
    }

    private static BigDecimal extractBalance(JsonNode parsed) {
        BigDecimal direct = parseDecimalNode(parsed.path("message"));
        if (direct != null) {
            return direct;
        }
        BigDecimal msgNested = firstBalanceInObject(parsed.path("message"));
        if (msgNested != null) {
            return msgNested;
        }
        BigDecimal rootBalance = firstBalanceInObject(parsed);
        if (rootBalance != null) {
            return rootBalance;
        }
        return firstBalanceInObject(parsed.path("data"));
    }

    private static BigDecimal firstBalanceInObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : new String[]{"balance", "amount", "credit"}) {
            BigDecimal candidate = parseDecimalNode(node.path(key));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static BigDecimal parseDecimalNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isString()) {
            String text = node.asString("").trim();
            if (!text.isEmpty()) {
                try {
                    return new BigDecimal(text);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private record UpstreamResponse(HttpStatusCode status, String body) {
    }
}
