package com.poker.game.evaluator;

import org.junit.jupiter.api.Test;

import static com.poker.game.evaluator.Hands.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighCardTest {

    @Test
    void aceHigh() {
        HandEvaluation hand = evaluate("AS", "JD", "8C", "5H", "2S");
        assertEquals(HandRank.HIGH_CARD, hand.getRank());
        assertEquals("High Card, Ace", hand.getDescription());
        assertEquals(14, hand.getRankingValues().getFirst());
    }

    @Test
    void secondCardBreaksHighCardTies() {
        assertTrue(evaluate("AS", "KD", "8C", "5H", "2S")
                .beats(evaluate("AH", "QD", "8S", "5C", "2H")));
    }
}
