package com.poker.game.evaluator;

import org.junit.jupiter.api.Test;

import static com.poker.game.evaluator.Hands.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairTest {

    @Test
    void pairOfJacks() {
        HandEvaluation hand = evaluate("JS", "JH", "8D", "5C", "2H");
        assertEquals(HandRank.ONE_PAIR, hand.getRank());
        assertEquals("One Pair, Jacks", hand.getDescription());
    }

    @Test
    void acePairKingKickerBeatsAcePairQueenKicker() {
        HandEvaluation kingKicker = evaluate("AS", "AH", "KD", "8C", "5H");
        HandEvaluation queenKicker = evaluate("AD", "AC", "QH", "8S", "5D");
        assertTrue(kingKicker.beats(queenKicker));
    }

    @Test
    void higherPairWinsRegardlessOfKickers() {
        assertTrue(evaluate("KS", "KH", "2D", "3C", "4H")
                .beats(evaluate("QS", "QH", "AD", "JC", "TH")));
    }
}
