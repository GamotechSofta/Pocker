package com.poker.exception;

import org.springframework.http.HttpStatus;

public class GameAlreadyStartedException extends ApiException {

    public GameAlreadyStartedException() {
        super(HttpStatus.CONFLICT, ErrorCodes.GAME_ALREADY_STARTED, "The game has already started.");
    }
}
