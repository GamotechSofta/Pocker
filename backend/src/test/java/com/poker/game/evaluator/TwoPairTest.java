package com.poker.game.evaluator;

import org.junit.jupiter.api.Test;

import static com.poker.game.evaluator.Hands.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoPairTest {

    @Test
    void acesAndSevens() {
        HandEvaluation hand = evaluate("AS", "AH", "7D", "7C", "KS");
        assertEquals(HandRank.TWO_PAIR, hand.getRank());
        assertEquals("Two Pair, Aces and Sevens", hand.getDescription());
        assertEquals(14, hand.getRankingValues().getFirst());
        assertEquals(7, hand.getRankingValues().get(1));
        assertEquals(13, hand.getRankingValues().get(2));
    }

    @Test
    void higherTopPairWins() {
        assertTrue(evaluate("AS", "AH", "2D", "2C", "3S")
                .beats(evaluate("KS", "KH", "QD", "QC", "AS")));
    }

    @Test
    void kickerBreaksTwoPairTies() {
        assertTrue(evaluate("AS", "AH", "7D", "7C", "KS")
                .beats(evaluate("AD", "AC", "7H", "7S", "QS")));
    }
}
