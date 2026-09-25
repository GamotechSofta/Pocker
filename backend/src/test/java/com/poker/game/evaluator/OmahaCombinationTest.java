package com.poker.game.evaluator;

import com.poker.game.model.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OmahaCombinationTest {

    private final HandEvaluator evaluator = new HandEvaluator();

    @Test
    void cannotPlayTheBoard() {
        List<Card> hole = Hands.cards("2C", "3D", "4H", "5S");
        List<Card> board = Hands.cards("TS", "JS", "QS", "KS", "AS");
        assertEquals(HandRank.HIGH_CARD, evaluator.evaluateOmaha(hole, board).getRank());
    }

    @Test
    void cannotUseThreeHoleCardsForAFlush() {
        List<Card> hole = Hands.cards("AH", "KH", "QH", "JH");
        List<Card> board = Hands.cards("2H", "3C", "4D", "5S", "9C");
        assertEquals(HandRank.HIGH_CARD, evaluator.evaluateOmaha(hole, board).getRank());
    }

    @Test
    void twoHoleAndThreeBoardCannotMakeAcesFullOfKingsFromFourHoleCards() {
        List<Card> hole = Hands.cards("AS", "AH", "KD", "KH");
        List<Card> board = Hands.cards("AD", "2C", "3D", "4S", "5H");
        assertEquals(HandRank.THREE_OF_A_KIND, evaluator.evaluateOmaha(hole, board).getRank());
    }

    @Test
    void usesExactlyTwoHoleHeartsAndThreeBoardHeartsForAFlush() {
        List<Card> hole = Hands.cards("AH", "KH", "2C", "3D");
        List<Card> board = Hands.cards("QH", "JH", "9H", "4S", "5C");
        assertEquals(HandRank.FLUSH, evaluator.evaluateOmaha(hole, board).getRank());
    }

    @Test
    void rejectsWrongCounts() {
        assertThrows(IllegalArgumentException.class, () ->
                evaluator.evaluateOmaha(Hands.cards("AS", "KH"), Hands.cards("2C", "3D", "4H", "5S", "9C")));
        assertThrows(IllegalArgumentException.class, () ->
                evaluator.evaluateOmaha(Hands.cards("AS", "AH", "KD", "KH"), Hands.cards("2C", "3D")));
    }
}
