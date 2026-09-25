package com.poker.game.variants;

import com.poker.game.evaluator.HandRank;
import com.poker.game.model.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiveCardDrawVariantTest {

    private final FiveCardDrawVariant variant = new FiveCardDrawVariant();

    @Test
    void fiveCardDrawDealsFiveWithoutCommunity() {
        assertEquals("FIVE_CARD_DRAW", variant.getName());
        assertEquals(5, variant.getInitialHoleCards());
        assertTrue(variant.requiresDrawPhase());
        assertFalse(variant.usesCommunityCards());
        assertFalse(variant.requiresDiscardBeforeFlop());
        assertEquals(0, variant.flopCount());
    }

    @Test
    void evaluatesFiveHoleCardsOnly() {
        List<Card> hole = List.of(
                Card.parse("AC"),
                Card.parse("AD"),
                Card.parse("AH"),
                Card.parse("2C"),
                Card.parse("3D")
        );
        assertEquals(HandRank.THREE_OF_A_KIND, variant.evaluateHand(hole, List.of()).getRank());
    }

    @Test
    void rejectsWrongHoleCountOrCommunityCards() {
        assertThrows(IllegalArgumentException.class, () ->
                variant.evaluateHand(List.of(Card.parse("AC"), Card.parse("KD")), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                variant.evaluateHand(
                        List.of(
                                Card.parse("AC"),
                                Card.parse("AD"),
                                Card.parse("AH"),
                                Card.parse("2C"),
                                Card.parse("3D")
                        ),
                        List.of(Card.parse("9S"))
                ));
    }
}
