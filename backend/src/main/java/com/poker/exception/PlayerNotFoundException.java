package com.poker.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class PlayerNotFoundException extends ApiException {

    public PlayerNotFoundException(UUID playerId) {
        super(HttpStatus.NOT_FOUND, ErrorCodes.PLAYER_NOT_FOUND, "Player not found: " + playerId);
    }
}
