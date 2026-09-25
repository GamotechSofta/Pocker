package com.poker.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, ErrorCodes.INVALID_CREDENTIALS, "Invalid username or password.");
    }
}
