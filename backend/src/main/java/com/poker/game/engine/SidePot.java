package com.poker.game.engine;

import java.util.List;

public record SidePot(int amount, List<String> eligiblePlayerIds) {

    public SidePot {
        if (amount < 0) {
            throw new IllegalArgumentException("pot amount cannot be negative");
        }
        eligiblePlayerIds = List.copyOf(eligiblePlayerIds);
    }
}
