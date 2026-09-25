package com.poker.exception;

import org.springframework.http.HttpStatus;

public class UsernameTakenException extends ApiException {

    public UsernameTakenException() {
        super(HttpStatus.CONFLICT, ErrorCodes.USERNAME_TAKEN, "That username is already taken.");
    }
}
