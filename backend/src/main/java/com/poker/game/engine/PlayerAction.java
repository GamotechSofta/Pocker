package com.poker.game.engine;

import java.util.List;
import java.util.Objects;

public record PlayerAction(String playerId, ActionType type, int amount, List<Integer> indices) {

    public PlayerAction(String playerId, ActionType type, int amount) {
        this(playerId, type, amount, List.of());
    }

    public PlayerAction {
        Objects.requireNonNull(playerId, "playerId is required");
        Objects.requireNonNull(type, "type is required");
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        indices = indices == null ? List.of() : List.copyOf(indices);
    }

    public static PlayerAction fold(String playerId) {
        return new PlayerAction(playerId, ActionType.FOLD, 0);
    }

    public static PlayerAction check(String playerId) {
        return new PlayerAction(playerId, ActionType.CHECK, 0);
    }

    public static PlayerAction call(String playerId) {
        return new PlayerAction(playerId, ActionType.CALL, 0);
    }

    public static PlayerAction bet(String playerId, int toAmount) {
        return new PlayerAction(playerId, ActionType.BET, toAmount);
    }

    public static PlayerAction raise(String playerId, int toAmount) {
        return new PlayerAction(playerId, ActionType.RAISE, toAmount);
    }

    public static PlayerAction allIn(String playerId) {
        return new PlayerAction(playerId, ActionType.ALL_IN, 0);
    }

    /** amount is the hole-card index to discard (0-based). Pineapple. */
    public static PlayerAction discard(String playerId, int holeIndex) {
        return new PlayerAction(playerId, ActionType.DISCARD, holeIndex);
    }

    /** Discard zero or more hole cards by index (5-Card Draw). Empty = stand pat. */
    public static PlayerAction draw(String playerId, List<Integer> holeIndices) {
        return new PlayerAction(playerId, ActionType.DISCARD, 0, holeIndices);
    }
}
