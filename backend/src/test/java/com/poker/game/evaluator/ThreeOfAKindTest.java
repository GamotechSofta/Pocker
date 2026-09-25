package com.poker.game.evaluator;

import org.junit.jupiter.api.Test;

import static com.poker.game.evaluator.Hands.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeOfAKindTest {

    @Test
    void threeTens() {
        HandEvaluation hand = evaluate("TS", "TH", "TD", "7C", "2H");
        assertEquals(HandRank.THREE_OF_A_KIND, hand.getRank());
        assertEquals("Three of a Kind, Tens", hand.getDescription());
    }

    @Test
    void higherTripsWin() {
        assertTrue(evaluate("QS", "QH", "QD", "3C", "2H")
                .beats(evaluate("JS", "JH", "JD", "AC", "KH")));
    }

    @Test
    void kickerBreaksTripTies() {
        assertTrue(evaluate("TS", "TH", "TD", "AC", "2H")
                .beats(evaluate("TS", "TH", "TD", "KC", "2D")));
    }
}
