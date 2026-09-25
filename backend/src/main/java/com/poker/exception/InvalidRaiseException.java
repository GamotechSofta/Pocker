package com.poker.exception;

import org.springframework.http.HttpStatus;

public class InvalidRaiseException extends ApiException {

    public InvalidRaiseException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_RAISE, message);
    }
}
