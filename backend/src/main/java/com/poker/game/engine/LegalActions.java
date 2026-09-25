package com.poker.game.engine;

import java.util.Set;

public record LegalActions(
        Set<ActionType> actions,
        int callAmount,
        int minBetOrRaiseTo,
        int maxBetOrRaiseTo
) {

    public LegalActions {
        actions = Set.copyOf(actions);
    }

    public boolean allows(ActionType type) {
        return actions.contains(type);
    }

    public static LegalActions none() {
        return new LegalActions(Set.of(), 0, 0, 0);
    }
}
