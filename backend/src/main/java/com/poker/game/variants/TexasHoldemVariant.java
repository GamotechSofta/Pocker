package com.poker.game.variants;

import com.poker.game.evaluator.HandEvaluation;
import com.poker.game.evaluator.HandEvaluator;
import com.poker.game.model.Card;

import java.util.ArrayList;
import java.util.List;

public class TexasHoldemVariant implements PokerVariant {

    public static final String NAME = "TEXAS_HOLDEM";

    private final HandEvaluator evaluator;

    public TexasHoldemVariant() {
        this(new HandEvaluator());
    }

    public TexasHoldemVariant(HandEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getInitialHoleCards() {
        return 2;
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
            throw new IllegalArgumentException("Texas Hold'em requires 2 hole cards");
        }
        if (communityCards == null || communityCards.size() != 5) {
            throw new IllegalArgumentException("Texas Hold'em showdown requires 5 community cards");
        }
        List<Card> combined = new ArrayList<>(7);
        combined.addAll(holeCards);
        combined.addAll(communityCards);
        return evaluator.evaluate(combined);
    }
}
