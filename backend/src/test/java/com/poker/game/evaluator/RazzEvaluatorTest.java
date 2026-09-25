package com.poker.game.evaluator;

import com.poker.game.model.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RazzEvaluatorTest {

    private final HandEvaluator evaluator = new HandEvaluator();

    @Test
    void wheelBeatsSevenHigh() {
        HandEvaluation wheel = evaluator.evaluateRazz(List.of(
                Card.parse("AC"), Card.parse("2D"), Card.parse("3H"), Card.parse("4S"), Card.parse("5C")
        ));
        HandEvaluation sevenHigh = evaluator.evaluateRazz(List.of(
                Card.parse("7C"), Card.parse("6D"), Card.parse("5H"), Card.parse("4D"), Card.parse("2S")
        ));
        assertTrue(wheel.beats(sevenHigh));
        assertEquals("5-high (wheel)", wheel.getDescription());
        assertEquals(HandRank.RAZZ_LOW, wheel.getRank());
    }

    @Test
    void unpairedBeatsAnyPair() {
        HandEvaluation unpaired = evaluator.evaluateRazz(List.of(
                Card.parse("KC"), Card.parse("QD"), Card.parse("JH"), Card.parse("TS"), Card.parse("9C")
        ));
        HandEvaluation pair = evaluator.evaluateRazz(List.of(
                Card.parse("2C"), Card.parse("2D"), Card.parse("3H"), Card.parse("4S"), Card.parse("5C")
        ));
        assertTrue(unpaired.beats(pair));
    }

    @Test
    void choosesBestFiveOfSeven() {
        HandEvaluation eval = evaluator.evaluateRazz(List.of(
                Card.parse("AC"), Card.parse("2D"), Card.parse("3H"), Card.parse("4S"),
                Card.parse("5C"), Card.parse("KD"), Card.parse("QH")
        ));
        assertEquals("5-high (wheel)", eval.getDescription());
    }

    @Test
    void aceIsLowNotHigh() {
        HandEvaluation aceLow = evaluator.evaluateRazz(List.of(
                Card.parse("AC"), Card.parse("6D"), Card.parse("5H"), Card.parse("4S"), Card.parse("3C")
        ));
        HandEvaluation sixHighNoAce = evaluator.evaluateRazz(List.of(
                Card.parse("6C"), Card.parse("5D"), Card.parse("4H"), Card.parse("3S"), Card.parse("2C")
        ));
        // 6-5-4-3-A vs 6-5-4-3-2: ace-low loses the last kicker (A=1 < 2), so six-high no ace is worse... 
        // Compare high-card down: both high=6, then 5,4,3, then A(1) vs 2 → 2 is higher so A-low is better (lower).
        assertTrue(aceLow.beats(sixHighNoAce));
    }

    @Test
    void straightsAndFlushesDoNotHelpOrHurtAsCategories() {
        HandEvaluation wheelFlush = evaluator.evaluateRazz(List.of(
                Card.parse("AC"), Card.parse("2C"), Card.parse("3C"), Card.parse("4C"), Card.parse("5C")
        ));
        HandEvaluation wheelMixed = evaluator.evaluateRazz(List.of(
                Card.parse("AD"), Card.parse("2H"), Card.parse("3S"), Card.parse("4D"), Card.parse("5H")
        ));
        assertTrue(wheelFlush.ties(wheelMixed));
    }
}
