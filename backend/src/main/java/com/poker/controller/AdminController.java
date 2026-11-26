package com.poker.controller;

import com.poker.service.TableService;
import com.poker.exception.ApiException;
import com.poker.tournament.service.TournamentOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TableService tableService;
    private final Instant startTime = Instant.now();
    private final String key;
    private final TournamentOperations tournaments;

    public AdminController(TableService tableService, TournamentOperations tournaments,
                           @Value("${ADMIN_API_KEY:}") String key) {
        this.tableService = tableService;
        this.tournaments = tournaments;
        this.key = key;
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<Map<String, Object>> getDiagnostics(@RequestHeader(value = "X-Admin-Key", required = false) String supplied) {
        authorize(supplied);
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("serverInstanceId", tournaments.nodeId());
        diag.put("uptimeSeconds", (Instant.now().toEpochMilli() - startTime.toEpochMilli()) / 1000);
        diag.put("jvmUptimeMillis", ManagementFactory.getRuntimeMXBean().getUptime());
        diag.put("activeTables", tableService.activeEngineCount());
        diag.put("liveTables", tableService.listTables().size());
        diag.put("matchmakingQueueSize", 0);
        diag.put("isDraining", tableService.isDraining());
        diag.put("usedMemoryMb", usedMemory / (1024 * 1024));
        diag.put("totalMemoryMb", totalMemory / (1024 * 1024));
        diag.put("maxMemoryMb", maxMemory / (1024 * 1024));
        diag.put("availableProcessors", runtime.availableProcessors());
        diag.put("complianceMode", "FREE_PLAY_ONLY");
        diag.put("fairPlayCompliant", true);

        return ResponseEntity.ok(diag);
    }

    @PostMapping("/drain")
    public ResponseEntity<Map<String, Object>> setDrainMode(@RequestParam(defaultValue = "true") boolean drain,
            @RequestHeader(value = "X-Admin-Key", required = false) String supplied) {
        authorize(supplied);
        tableService.setDraining(drain);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "serverInstanceId", tournaments.nodeId(),
                "isDraining", drain,
                "liveTables", tableService.listTables().size(),
                "message", drain ? "Node is draining: no new tables; live games will finish." : "Drain cancelled; node accepting traffic."
        ));
    }

    @GetMapping("/tournaments/operations")
    public Map<String, Object> tournamentOperations(@RequestHeader(value = "X-Admin-Key", required = false) String supplied) {
        authorize(supplied);
        return tournaments.snapshot();
    }

    private void authorize(String supplied) {
        byte[] expected = key.getBytes(StandardCharsets.UTF_8);
        if (expected.length < 32 || supplied == null || !MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OPERATIONS_ADMIN_REQUIRED", "Operations access is unavailable to this caller.");
        }
    }
}

