package com.poker.controller;

import com.poker.dto.MatchmakingRequest;
import com.poker.dto.MatchmakingTicketResponse;
import com.poker.matchmaking.MatchmakingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/matchmaking")
@Tag(name = "Matchmaking")
@SecurityRequirement(name = "bearer-jwt")
public class MatchmakingController {

    private final MatchmakingService matchmakingService;

    public MatchmakingController(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @PostMapping("/join")
    public MatchmakingTicketResponse join(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MatchmakingRequest request
    ) {
        return matchmakingService.enqueue(userId(jwt), request);
    }

    @GetMapping("/tickets/{ticketId}")
    public MatchmakingTicketResponse ticket(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return matchmakingService.get(ticketId, userId(jwt));
    }

    @PostMapping("/tickets/{ticketId}/cancel")
    public MatchmakingTicketResponse cancel(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return matchmakingService.cancel(ticketId, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
