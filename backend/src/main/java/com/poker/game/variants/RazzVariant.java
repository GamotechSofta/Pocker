package com.poker.game.variants;

import com.poker.game.evaluator.HandEvaluation;
import com.poker.game.evaluator.HandEvaluator;
import com.poker.game.model.Card;

import java.util.List;

public class RazzVariant implements PokerVariant {

    public static final String NAME = "RAZZ";
    public static final int TOTAL_CARDS = 7;

    private final HandEvaluator evaluator;

    public RazzVariant() {
        this(new HandEvaluator());
    }

    public RazzVariant(HandEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getInitialHoleCards() {
        return 3;
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
    public boolean requiresStudStreets() {
        return true;
    }

    @Override
    public int studTotalCards() {
        return TOTAL_CARDS;
    }

    @Override
    public HandEvaluation evaluateHand(List<Card> holeCards, List<Card> communityCards) {
        if (holeCards == null || holeCards.size() < 5 || holeCards.size() > TOTAL_CARDS) {
            throw new IllegalArgumentException("Razz showdown requires 5 to 7 hole cards");
        }
        if (communityCards != null && !communityCards.isEmpty()) {
            throw new IllegalArgumentException("Razz has no community cards");
        }
        return evaluator.evaluateRazz(holeCards);
    }
}
