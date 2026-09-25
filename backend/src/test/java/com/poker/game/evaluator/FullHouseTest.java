package com.poker.game.evaluator;

import org.junit.jupiter.api.Test;

import static com.poker.game.evaluator.Hands.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullHouseTest {

    @Test
    void queensFullOfEights() {
        HandEvaluation hand = evaluate("QS", "QH", "QD", "8C", "8H");
        assertEquals(HandRank.FULL_HOUSE, hand.getRank());
        assertEquals("Full House, Queens full of Eights", hand.getDescription());
    }

    @Test
    void acesFullOfKingsBeatsQueensFullOfAces() {
        HandEvaluation acesFull = evaluate("AS", "AD", "AC", "KS", "KD");
        HandEvaluation queensFull = evaluate("QH", "QC", "QD", "AH", "AC");
        assertTrue(acesFull.beats(queensFull));
    }

    @Test
    void tripsRankBeatsPairRankWhenTripsTieWouldMatter() {
        assertTrue(evaluate("KS", "KH", "KD", "2C", "2D")
                .beats(evaluate("QS", "QH", "QD", "AS", "AH")));
    }
}
