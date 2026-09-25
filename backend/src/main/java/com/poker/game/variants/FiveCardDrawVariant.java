package com.poker.game.variants;

import com.poker.game.evaluator.HandEvaluation;
import com.poker.game.evaluator.HandEvaluator;
import com.poker.game.model.Card;

import java.util.List;

public class FiveCardDrawVariant implements PokerVariant {

    public static final String NAME = "FIVE_CARD_DRAW";

    private final HandEvaluator evaluator;

    public FiveCardDrawVariant() {
        this(new HandEvaluator());
    }

    public FiveCardDrawVariant(HandEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getInitialHoleCards() {
        return 5;
    }

    @Override
    public boolean usesCommunityCards() {
        return false;
    }

    @Override
    public int flopCount() {
        return 0;
    }

    @Override
    public int turnCount() {
        return 0;
    }

    @Override
    public int riverCount() {
        return 0;
    }

    @Override
    public boolean burnBeforeCommunityDeals() {
        return false;
    }

    @Override
    public boolean requiresDrawPhase() {
        return true;
    }

    @Override
    public HandEvaluation evaluateHand(List<Card> holeCards, List<Card> communityCards) {
        if (holeCards == null || holeCards.size() != getInitialHoleCards()) {
            throw new IllegalArgumentException("5-Card Draw showdown requires 5 hole cards");
        }
        if (communityCards != null && !communityCards.isEmpty()) {
            throw new IllegalArgumentException("5-Card Draw has no community cards");
        }
        return evaluator.evaluate(holeCards);
    }
}
