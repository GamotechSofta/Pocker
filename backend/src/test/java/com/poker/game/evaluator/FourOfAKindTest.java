package com.poker.game.evaluator;

import org.junit.jupiter.api.Test;

import static com.poker.game.evaluator.Hands.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FourOfAKindTest {

    @Test
    void fourKings() {
        HandEvaluation hand = evaluate("KS", "KH", "KD", "KC", "7H");
        assertEquals(HandRank.FOUR_OF_A_KIND, hand.getRank());
        assertEquals("Four of a Kind, Kings", hand.getDescription());
        assertEquals(13, hand.getRankingValues().getFirst());
        assertEquals(7, hand.getRankingValues().get(1));
    }

    @Test
    void higherQuadsWin() {
        assertTrue(evaluate("AS", "AH", "AD", "AC", "2C")
                .beats(evaluate("KS", "KH", "KD", "KC", "AC")));
    }

    @Test
    void kickerBreaksQuadTies() {
        assertTrue(evaluate("KS", "KH", "KD", "KC", "AH")
                .beats(evaluate("KS", "KH", "KD", "KC", "QH")));
    }
}
