package com.poker.game.variants;

import com.poker.game.evaluator.HandEvaluation;
import com.poker.game.model.Card;

import java.util.List;

public interface PokerVariant {

    String getName();

    int getInitialHoleCards();

    boolean usesCommunityCards();

    int flopCount();

    int turnCount();

    int riverCount();

    boolean burnBeforeCommunityDeals();

    HandEvaluation evaluateHand(List<Card> holeCards, List<Card> communityCards);

    /** Pineapple: discard one hole card before pre-flop betting. */
    default boolean requiresDiscardBeforeFlop() {
        return false;
    }

    /** 5-Card Draw: discard and replace after the first betting round. */
    default boolean requiresDrawPhase() {
        return false;
    }

    /** Razz / 7-card stud: deal more private cards between betting streets up to studTotalCards(). */
    default boolean requiresStudStreets() {
        return false;
    }

    default int studTotalCards() {
        return getInitialHoleCards();
    }
}
