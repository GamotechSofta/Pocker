package com.poker.game.variants;

import com.poker.game.evaluator.HandRank;
import com.poker.game.model.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PineappleVariantTest {

    private final PineappleVariant variant = new PineappleVariant();

    @Test
    void pineappleDealsThreeAndRequiresDiscard() {
        assertEquals("PINEAPPLE", variant.getName());
        assertEquals(3, variant.getInitialHoleCards());
        assertTrue(variant.requiresDiscardBeforeFlop());
        assertTrue(variant.usesCommunityCards());
    }

    @Test
    void evaluatesLikeHoldemAfterDiscard() {
        List<Card> hole = List.of(Card.parse("AC"), Card.parse("KD"));
        List<Card> board = List.of(
                Card.parse("AH"),
                Card.parse("2C"),
                Card.parse("3D"),
                Card.parse("9S"),
                Card.parse("KH")
        );
        assertEquals(HandRank.TWO_PAIR, variant.evaluateHand(hole, board).getRank());
    }

    @Test
    void rejectsThreeHoleCardsAtShowdown() {
        assertThrows(IllegalArgumentException.class, () ->
                variant.evaluateHand(
                        List.of(Card.parse("AC"), Card.parse("KD"), Card.parse("2H")),
                        List.of(
                                Card.parse("AH"),
                                Card.parse("2C"),
                                Card.parse("3D"),
                                Card.parse("9S"),
                                Card.parse("KH")
                        )
                ));
    }
}
