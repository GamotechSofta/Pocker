package com.poker.exception;

import org.springframework.http.HttpStatus;

public class NotPlayerTurnException extends ApiException {

    public NotPlayerTurnException() {
        super(HttpStatus.BAD_REQUEST, ErrorCodes.NOT_PLAYER_TURN, "It is not your turn.");
    }
}
