package com.poker.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class GameNotFoundException extends ApiException {

    public GameNotFoundException(UUID gameId) {
        super(HttpStatus.NOT_FOUND, ErrorCodes.GAME_NOT_FOUND, "Game not found: " + gameId);
    }
}
