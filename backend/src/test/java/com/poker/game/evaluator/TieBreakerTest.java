package com.poker.game.evaluator;

import org.junit.jupiter.api.Test;

import static com.poker.game.evaluator.Hands.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TieBreakerTest {

    @Test
    void categoryOrderIsRespected() {
        HandEvaluation highCard = evaluate("AS", "KD", "8C", "5H", "2S");
        HandEvaluation pair = evaluate("JS", "JH", "8D", "5C", "2H");
        HandEvaluation twoPair = evaluate("AS", "AH", "7D", "7C", "KS");
        HandEvaluation trips = evaluate("TS", "TH", "TD", "7C", "2H");
        HandEvaluation straight = evaluate("9S", "8H", "7D", "6C", "5S");
        HandEvaluation flush = evaluate("AD", "JD", "8D", "5D", "2D");
        HandEvaluation fullHouse = evaluate("QS", "QH", "QD", "8C", "8H");
        HandEvaluation quads = evaluate("KS", "KH", "KD", "KC", "7H");
        HandEvaluation straightFlush = evaluate("9H", "8H", "7H", "6H", "5H");
        HandEvaluation royal = evaluate("AS", "KS", "QS", "JS", "TS");

        assertTrue(pair.beats(highCard));
        assertTrue(twoPair.beats(pair));
        assertTrue(trips.beats(twoPair));
        assertTrue(straight.beats(trips));
        assertTrue(flush.beats(straight));
        assertTrue(fullHouse.beats(flush));
        assertTrue(quads.beats(fullHouse));
        assertTrue(straightFlush.beats(quads));
        assertTrue(royal.beats(straightFlush));
    }

    @Test
    void identicalHandsTie() {
        HandEvaluation first = evaluate("AS", "AH", "KD", "8C", "5H");
        HandEvaluation second = evaluate("AD", "AC", "KH", "8S", "5D");
        assertTrue(first.ties(second));
        assertEquals(0, first.compareTo(second));
    }

    @Test
    void sevenCardHoldEmUsesBestFiveIncludingPlayingTheBoard() {
        HandEvaluation withWheelBoard = evaluate("2C", "7D", "AS", "3S", "4H", "5C", "9D");
        assertEquals(HandRank.STRAIGHT, withWheelBoard.getRank());
        assertEquals(5, withWheelBoard.getRankingValues().getFirst());
    }

    @Test
    void sevenCardKickerIsChosenFromAllSeven() {
        HandEvaluation kingKicker = evaluate("AS", "5C", "AH", "KD", "9C", "8H", "2D");
        HandEvaluation queenKicker = evaluate("AD", "5D", "AC", "QH", "9S", "8C", "2H");
        assertTrue(kingKicker.beats(queenKicker));
    }

    @Test
    void rejectsTooFewCardsOrDuplicates() {
        HandEvaluator evaluator = new HandEvaluator();
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(Hands.cards("AS", "KS", "QS", "JS")));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(Hands.cards("AS", "AS", "KS", "QS", "JS")));
    }
}
