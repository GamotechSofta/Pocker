package com.poker.controller;

import com.poker.exception.ApiException;
import com.poker.tournament.dto.TournamentClockResponse;
import com.poker.tournament.service.TournamentGameplayService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@RestController
public class TournamentClockController {
    private final TournamentGameplayService gameplay;
    private final String administrativeKey;

    public TournamentClockController(TournamentGameplayService gameplay,
                                     @Value("${TOURNAMENT_CLOCK_ADMIN_KEY:}") String administrativeKey) {
        this.gameplay = gameplay;
        this.administrativeKey = administrativeKey;
    }

    @GetMapping("/api/tournaments/{id}/clock")
    public TournamentClockResponse player(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return gameplay.clock(id, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/api/admin/tournaments/{id}/clock")
    public TournamentClockResponse admin(@PathVariable UUID id, @RequestHeader(value = "X-Tournament-Clock-Key", required = false) String key) {
        requireAdministrativeKey(key);
        return gameplay.clock(id, null);
    }

    @PostMapping("/api/admin/tournaments/{id}/clock/{operation}")
    public TournamentClockResponse change(@PathVariable UUID id, @PathVariable String operation,
                                          @RequestHeader(value = "X-Tournament-Clock-Key", required = false) String key) {
        requireAdministrativeKey(key);
        return switch (operation) {
            case "pause" -> gameplay.pauseClock(id, true);
            case "resume" -> gameplay.pauseClock(id, false);
            default -> throw new ApiException(HttpStatus.NOT_FOUND, "CLOCK_OPERATION_NOT_FOUND", "Unknown clock operation.");
        };
    }

    void requireAdministrativeKey(String key) {
        if (administrativeKey.length() < 32 || key == null || !MessageDigest.isEqual(
                administrativeKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLOCK_ADMIN_REQUIRED", "Tournament clock administration is unavailable to this caller.");
        }
    }
}
