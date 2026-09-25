package com.poker.game.evaluator;

import org.junit.jupiter.api.Test;

import static com.poker.game.evaluator.Hands.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StraightFlushTest {

    @Test
    void nineHighStraightFlush() {
        HandEvaluation hand = evaluate("9H", "8H", "7H", "6H", "5H");
        assertEquals(HandRank.STRAIGHT_FLUSH, hand.getRank());
        assertEquals(9, hand.getRankingValues().getFirst());
        assertEquals("Straight Flush, Nine high", hand.getDescription());
    }

    @Test
    void aceLowStraightFlushIsFiveHighNotRoyal() {
        HandEvaluation wheel = evaluate("AS", "2S", "3S", "4S", "5S");
        assertEquals(HandRank.STRAIGHT_FLUSH, wheel.getRank());
        assertEquals(5, wheel.getRankingValues().getFirst());
        assertTrue(evaluate("6H", "5H", "4H", "3H", "2H").beats(wheel));
    }

    @Test
    void higherStraightFlushWins() {
        assertTrue(evaluate("9C", "8C", "7C", "6C", "5C")
                .beats(evaluate("8D", "7D", "6D", "5D", "4D")));
    }
}
