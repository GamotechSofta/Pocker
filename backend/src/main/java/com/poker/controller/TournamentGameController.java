package com.poker.controller;

import com.poker.dto.TableDetailResponse;
import com.poker.dto.TableStateResponse;
import com.poker.tournament.service.TournamentGameplayService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/tournament-tables/{id}")
public class TournamentGameController {
    private final TournamentGameplayService gameplay;

    public TournamentGameController(TournamentGameplayService gameplay) { this.gameplay = gameplay; }

    @GetMapping
    public TableDetailResponse detail(@PathVariable UUID id, @AuthenticationPrincipal Jwt token) {
        return gameplay.detail(id, UUID.fromString(token.getSubject()));
    }

    @GetMapping("/state")
    public TableStateResponse state(@PathVariable UUID id, @AuthenticationPrincipal Jwt token) {
        return gameplay.state(id, UUID.fromString(token.getSubject()));
    }
}
