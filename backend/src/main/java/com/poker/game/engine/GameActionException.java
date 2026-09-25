package com.poker.game.engine;

import com.poker.exception.ErrorCodes;

public class GameActionException extends RuntimeException {

    private final String code;

    public GameActionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static GameActionException notPlayerTurn() {
        return new GameActionException(ErrorCodes.NOT_PLAYER_TURN, "It is not your turn.");
    }

    public static GameActionException insufficientChips() {
        return new GameActionException(ErrorCodes.INSUFFICIENT_CHIPS, "Not enough chips for this action.");
    }

    public static GameActionException invalidRaise(String message) {
        return new GameActionException(ErrorCodes.INVALID_RAISE, message);
    }

    public static GameActionException invalid(String message) {
        return new GameActionException(ErrorCodes.INVALID_GAME_ACTION, message);
    }

    public static GameActionException seatUnavailable() {
        return new GameActionException(ErrorCodes.SEAT_UNAVAILABLE, "That seat is not available.");
    }

    public static GameActionException alreadyStarted() {
        return new GameActionException(ErrorCodes.GAME_ALREADY_STARTED, "The game has already started.");
    }

    public static GameActionException playerNotFound() {
        return new GameActionException(ErrorCodes.PLAYER_NOT_FOUND, "Player was not found at this table.");
    }
}
