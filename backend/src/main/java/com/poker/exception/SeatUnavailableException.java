package com.poker.exception;

import org.springframework.http.HttpStatus;

public class SeatUnavailableException extends ApiException {

    public SeatUnavailableException() {
        super(HttpStatus.CONFLICT, ErrorCodes.SEAT_UNAVAILABLE, "That seat is not available.");
    }
}
