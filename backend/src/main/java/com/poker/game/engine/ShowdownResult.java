package com.poker.game.engine;

import com.poker.game.evaluator.HandEvaluation;
import com.poker.game.model.Card;

import java.util.List;

public record ShowdownResult(
        String playerId,
        List<Card> holeCards,
        HandEvaluation evaluation
) {

    public ShowdownResult {
        holeCards = List.copyOf(holeCards);
    }
}
