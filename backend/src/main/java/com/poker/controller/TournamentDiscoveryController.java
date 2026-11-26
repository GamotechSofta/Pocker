package com.poker.controller;

import com.poker.tournament.dto.TournamentDiscovery;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.service.TournamentDiscoveryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentDiscoveryController {
    private final TournamentDiscoveryService discovery;

    public TournamentDiscoveryController(TournamentDiscoveryService discovery) {
        this.discovery = discovery;
    }

    @GetMapping
    public ResponseEntity<TournamentDiscovery.Catalogue> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) List<TournamentStatus> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return privateResponse(discovery.list(UUID.fromString(jwt.getSubject()), status, page, size, false));
    }

    @GetMapping("/my")
    public ResponseEntity<TournamentDiscovery.Catalogue> mine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) List<TournamentStatus> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return privateResponse(discovery.list(UUID.fromString(jwt.getSubject()), status, page, size, true));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TournamentDiscovery.Details> details(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return privateResponse(discovery.details(id, UUID.fromString(jwt.getSubject())));
    }

    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
