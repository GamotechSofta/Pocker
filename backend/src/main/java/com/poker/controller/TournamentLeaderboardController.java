package com.poker.controller;

import com.poker.tournament.dto.TournamentLeaderboard;
import com.poker.tournament.service.TournamentStandingsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class TournamentLeaderboardController {
    private final TournamentStandingsService standings;
    private final TournamentClockController adminAccess;

    public TournamentLeaderboardController(TournamentStandingsService standings, TournamentClockController adminAccess) {
        this.standings = standings;
        this.adminAccess = adminAccess;
    }

    @GetMapping("/api/tournaments/{id}/leaderboard")
    public TournamentLeaderboard player(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return standings.player(id, UUID.fromString(jwt.getSubject()), page, size);
    }

    @GetMapping("/api/admin/tournaments/{id}/leaderboard")
    public TournamentLeaderboard admin(@PathVariable UUID id, @RequestHeader(value = "X-Tournament-Clock-Key", required = false) String key,
                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        adminAccess.requireAdministrativeKey(key);
        return standings.admin(id, page, size);
    }
}
