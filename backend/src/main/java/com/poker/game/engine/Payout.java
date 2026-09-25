package com.poker.game.engine;

public record Payout(String playerId, int amount) {

    public Payout {
        if (amount < 0) {
            throw new IllegalArgumentException("payout cannot be negative");
        }
    }
}
