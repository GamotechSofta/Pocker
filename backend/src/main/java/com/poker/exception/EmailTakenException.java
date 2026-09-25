package com.poker.exception;

import org.springframework.http.HttpStatus;

public class EmailTakenException extends ApiException {

    public EmailTakenException() {
        super(HttpStatus.CONFLICT, ErrorCodes.EMAIL_TAKEN, "That email is already registered.");
    }
}
