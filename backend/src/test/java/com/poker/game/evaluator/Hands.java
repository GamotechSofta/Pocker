package com.poker.game.evaluator;

import com.poker.game.model.Card;

import java.util.Arrays;
import java.util.List;

final class Hands {

    private Hands() {
    }

    static List<Card> cards(String... codes) {
        return Arrays.stream(codes).map(Card::parse).toList();
    }

    static HandEvaluation evaluate(String... codes) {
        return new HandEvaluator().evaluate(cards(codes));
    }
}
