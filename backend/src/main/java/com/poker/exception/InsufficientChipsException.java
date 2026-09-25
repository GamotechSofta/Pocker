package com.poker.exception;

import org.springframework.http.HttpStatus;

public class InsufficientChipsException extends ApiException {

    public InsufficientChipsException() {
        super(HttpStatus.BAD_REQUEST, ErrorCodes.INSUFFICIENT_CHIPS, "Not enough chips for this action.");
    }
}
