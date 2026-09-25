package com.poker.game.evaluator;

import org.junit.jupiter.api.Test;

import static com.poker.game.evaluator.Hands.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlushTest {

    @Test
    void aceHighFlush() {
        HandEvaluation hand = evaluate("AD", "JD", "8D", "5D", "2D");
        assertEquals(HandRank.FLUSH, hand.getRank());
        assertEquals("Flush, Ace high", hand.getDescription());
    }

    @Test
    void flushBeatsStraight() {
        assertTrue(evaluate("AD", "JD", "8D", "5D", "2D")
                .beats(evaluate("9S", "8H", "7D", "6C", "5S")));
    }

    @Test
    void secondCardBreaksFlushTies() {
        assertTrue(evaluate("AS", "JS", "9S", "4S", "3S")
                .beats(evaluate("AH", "TH", "9H", "4H", "3H")));
    }
}
