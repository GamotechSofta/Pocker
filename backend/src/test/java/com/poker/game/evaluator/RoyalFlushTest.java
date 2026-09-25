package com.poker.game.evaluator;

import org.junit.jupiter.api.Test;

import static com.poker.game.evaluator.Hands.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoyalFlushTest {

    @Test
    void aceToTenSuitedIsARoyalFlush() {
        HandEvaluation hand = evaluate("AS", "KS", "QS", "JS", "TS");
        assertEquals(HandRank.ROYAL_FLUSH, hand.getRank());
        assertEquals("Royal Flush", hand.getDescription());
    }

    @Test
    void royalFlushBeatsStraightFlush() {
        HandEvaluation royal = evaluate("AH", "KH", "QH", "JH", "TH");
        HandEvaluation straightFlush = evaluate("9S", "8S", "7S", "6S", "5S");
        assertTrue(royal.beats(straightFlush));
    }

    @Test
    void sevenCardsCanMakeARoyalUsingTheBoard() {
        HandEvaluation hand = evaluate("2C", "3D", "AS", "KS", "QS", "JS", "TS");
        assertEquals(HandRank.ROYAL_FLUSH, hand.getRank());
    }
}
