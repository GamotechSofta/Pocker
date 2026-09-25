package com.poker.game.evaluator;

import org.junit.jupiter.api.Test;

import static com.poker.game.evaluator.Hands.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StraightTest {

    @Test
    void nineHighStraight() {
        HandEvaluation hand = evaluate("9S", "8H", "7D", "6C", "5S");
        assertEquals(HandRank.STRAIGHT, hand.getRank());
        assertEquals("Straight, Nine high", hand.getDescription());
        assertEquals(9, hand.getRankingValues().getFirst());
    }

    @Test
    void broadwayIsAceHighStraight() {
        HandEvaluation hand = evaluate("AS", "KH", "QD", "JC", "TS");
        assertEquals(HandRank.STRAIGHT, hand.getRank());
        assertEquals(14, hand.getRankingValues().getFirst());
    }

    @Test
    void aceLowStraightIsFiveHigh() {
        HandEvaluation wheel = evaluate("AS", "2H", "3D", "4C", "5S");
        assertEquals(HandRank.STRAIGHT, wheel.getRank());
        assertEquals(5, wheel.getRankingValues().getFirst());
        assertTrue(evaluate("6S", "5H", "4D", "3C", "2S").beats(wheel));
    }

    @Test
    void aceTwoThreeFourSixIsNotAStraight() {
        HandEvaluation hand = evaluate("AS", "2H", "3D", "4C", "6S");
        assertEquals(HandRank.HIGH_CARD, hand.getRank());
    }
}
