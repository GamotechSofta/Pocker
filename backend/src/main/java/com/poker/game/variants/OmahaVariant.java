package com.poker.game.variants;

import com.poker.game.evaluator.HandEvaluation;
import com.poker.game.evaluator.HandEvaluator;
import com.poker.game.model.Card;

import java.util.List;

public class OmahaVariant implements PokerVariant {

    public static final String NAME = "OMAHA";

    private final HandEvaluator evaluator;

    public OmahaVariant() {
        this(new HandEvaluator());
    }

    public OmahaVariant(HandEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getInitialHoleCards() {
        return 4;
    }

    @Override
    public boolean usesCommunityCards() {
        return true;
    }

    @Override
    public int flopCount() {
        return 3;
    }

    @Override
    public int turnCount() {
        return 1;
    }

    @Override
    public int riverCount() {
        return 1;
    }

    @Override
    public boolean burnBeforeCommunityDeals() {
        return true;
    }

    @Override
    public HandEvaluation evaluateHand(List<Card> holeCards, List<Card> communityCards) {
        if (holeCards == null || holeCards.size() != getInitialHoleCards()) {
            throw new IllegalArgumentException("Omaha requires 4 hole cards");
        }
        if (communityCards == null || communityCards.size() != 5) {
            throw new IllegalArgumentException("Omaha showdown requires 5 community cards");
        }
        return evaluator.evaluateOmaha(holeCards, communityCards);
    }
}
