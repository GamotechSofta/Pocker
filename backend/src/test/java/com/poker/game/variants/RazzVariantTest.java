package com.poker.game.variants;

import com.poker.game.evaluator.HandRank;
import com.poker.game.model.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RazzVariantTest {

    private final RazzVariant variant = new RazzVariant();

    @Test
    void razzDealsThreeThenStudsToSeven() {
        assertEquals("RAZZ", variant.getName());
        assertEquals(3, variant.getInitialHoleCards());
        assertEquals(7, variant.studTotalCards());
        assertTrue(variant.requiresStudStreets());
        assertFalse(variant.usesCommunityCards());
        assertFalse(variant.requiresDrawPhase());
    }

    @Test
    void evaluatesLowFromSevenHoleCards() {
        List<Card> hole = List.of(
                Card.parse("AC"), Card.parse("2D"), Card.parse("3H"), Card.parse("4S"),
                Card.parse("5C"), Card.parse("KD"), Card.parse("QH")
        );
        assertEquals(HandRank.RAZZ_LOW, variant.evaluateHand(hole, List.of()).getRank());
        assertEquals("5-high (wheel)", variant.evaluateHand(hole, List.of()).getDescription());
    }

    @Test
    void rejectsCommunityOrTooFewCards() {
        assertThrows(IllegalArgumentException.class, () ->
                variant.evaluateHand(List.of(Card.parse("AC"), Card.parse("2D"), Card.parse("3H")), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                variant.evaluateHand(
                        List.of(
                                Card.parse("AC"), Card.parse("2D"), Card.parse("3H"),
                                Card.parse("4S"), Card.parse("5C")
                        ),
                        List.of(Card.parse("9S"))
                ));
    }
}
