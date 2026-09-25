package com.poker.exception;

public final class ErrorCodes {

    public static final String GAME_NOT_FOUND = "GAME_NOT_FOUND";
    public static final String PLAYER_NOT_FOUND = "PLAYER_NOT_FOUND";
    public static final String INVALID_GAME_ACTION = "INVALID_GAME_ACTION";
    public static final String NOT_PLAYER_TURN = "NOT_PLAYER_TURN";
    public static final String INSUFFICIENT_CHIPS = "INSUFFICIENT_CHIPS";
    public static final String INVALID_RAISE = "INVALID_RAISE";
    public static final String GAME_ALREADY_STARTED = "GAME_ALREADY_STARTED";
    public static final String SEAT_UNAVAILABLE = "SEAT_UNAVAILABLE";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String USERNAME_TAKEN = "USERNAME_TAKEN";
    public static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {
    }
}
