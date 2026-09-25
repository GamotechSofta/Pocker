package com.poker.controller;

import com.poker.dto.CreateTableRequest;
import com.poker.dto.JoinTableRequest;
import com.poker.dto.TableDetailResponse;
import com.poker.dto.TableStateResponse;
import com.poker.dto.TableSummaryResponse;
import com.poker.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tables")
@Tag(name = "Tables")
@SecurityRequirement(name = "bearer-jwt")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    @Operation(summary = "List lobby tables")
    public List<TableSummaryResponse> list() {
        return tableService.listTables();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a table")
    public TableDetailResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTableRequest request
    ) {
        return tableService.createTable(userId(jwt), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a table")
    public TableDetailResponse get(@PathVariable UUID id) {
        return tableService.getTable(id);
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "Join a table")
    public TableDetailResponse join(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) JoinTableRequest request
    ) {
        return tableService.joinTable(id, userId(jwt), request);
    }

    @PostMapping("/{id}/leave")
    @Operation(summary = "Leave a table")
    public TableDetailResponse leave(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return tableService.leaveTable(id, userId(jwt));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start the current hand")
    public TableStateResponse start(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return tableService.startTable(id, userId(jwt));
    }

    @GetMapping("/{id}/state")
    @Operation(summary = "Get sanitized table state for the current player")
    public TableStateResponse state(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return tableService.getState(id, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
