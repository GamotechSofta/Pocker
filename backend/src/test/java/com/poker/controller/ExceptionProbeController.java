package com.poker.controller;

import com.poker.exception.GameNotFoundException;
import com.poker.exception.NotPlayerTurnException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/__probe")
public class ExceptionProbeController {

    @GetMapping("/game-not-found")
    void gameNotFound() {
        throw new GameNotFoundException(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @GetMapping("/not-player-turn")
    void notPlayerTurn() {
        throw new NotPlayerTurnException();
    }

    @PostMapping("/validate")
    void validate(@Valid @RequestBody ProbeRequest request) {
        request.name();
    }

    record ProbeRequest(
            @NotBlank String name,
            @Min(1) int amount
    ) {
    }
}
