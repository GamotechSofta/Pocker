package com.poker.history;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
@Tag(name = "History")
@SecurityRequirement(name = "bearer-jwt")
public class HandHistoryController {

    private final HandHistoryService historyService;

    public HandHistoryController(HandHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    @Operation(summary = "Get the signed-in player's hand history and summary stats, optionally scoped to one variant")
    public HandHistoryDtos.HistoryPage history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String variant,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return historyService.getPlayerHistory(jwt.getSubject(), variant, result, page, size);
    }
}
