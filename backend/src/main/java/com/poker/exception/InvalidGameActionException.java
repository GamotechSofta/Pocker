package com.poker.exception;

import org.springframework.http.HttpStatus;

public class InvalidGameActionException extends ApiException {

    public InvalidGameActionException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_GAME_ACTION, message);
    }
}
