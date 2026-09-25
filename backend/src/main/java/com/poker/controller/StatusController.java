package com.poker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "System")
public class StatusController {

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Application status",
            description = "Lightweight process check. Use /actuator/health for database readiness."
    )
    public Map<String, String> status() {
        return Map.of(
                "status", "ok",
                "service", "poker-backend",
                "timestamp", Instant.now().toString()
        );
    }
}
