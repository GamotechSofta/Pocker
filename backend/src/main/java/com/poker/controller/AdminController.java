package com.poker.controller;

import com.poker.service.TableService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TableService tableService;
    private final Instant startTime = Instant.now();

    public AdminController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<Map<String, Object>> getDiagnostics() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("serverInstanceId", "poker-node-1");
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
    public ResponseEntity<Map<String, Object>> setDrainMode(@RequestParam(defaultValue = "true") boolean drain) {
        tableService.setDraining(drain);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "serverInstanceId", "poker-node-1",
                "isDraining", drain,
                "liveTables", tableService.listTables().size(),
                "message", drain ? "Node is draining: no new tables; live games will finish." : "Drain cancelled; node accepting traffic."
        ));
    }
}

