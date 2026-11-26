package com.poker.controller;

import com.poker.tournament.dto.TournamentResults;
import com.poker.tournament.service.TournamentResultsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentResultsController {
    private final TournamentResultsService service;

    public TournamentResultsController(TournamentResultsService service) { this.service = service; }

    @GetMapping("/{id}/results")
    public ResponseEntity<TournamentResults> results(@PathVariable UUID id, @AuthenticationPrincipal Jwt viewer,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.results(id, UUID.fromString(viewer.getSubject()), page, size));
    }

    @GetMapping("/history")
    public ResponseEntity<TournamentResults.History> history(@AuthenticationPrincipal Jwt viewer,
            @RequestParam(defaultValue = "ALL") TournamentResultsService.HistoryFilter filter,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.history(UUID.fromString(viewer.getSubject()), filter, page, size));
    }
}
