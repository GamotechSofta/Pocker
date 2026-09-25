package com.poker.game.variants;

import com.poker.game.evaluator.HandRank;
import com.poker.game.model.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmahaVariantTest {

    private final OmahaVariant variant = new OmahaVariant();

    @Test
    void omahaUsesFourHoleCardsAndFiveCommunityCards() {
        assertEquals("OMAHA", variant.getName());
        assertEquals(4, variant.getInitialHoleCards());
        assertTrue(variant.usesCommunityCards());
        assertEquals(3, variant.flopCount());
        assertEquals(1, variant.turnCount());
        assertEquals(1, variant.riverCount());
        assertTrue(variant.burnBeforeCommunityDeals());
    }

    @Test
    void evaluatesExactTwoHoleAndThreeBoard() {
        List<Card> hole = List.of(Card.parse("AS"), Card.parse("AH"), Card.parse("KD"), Card.parse("KH"));
        List<Card> board = List.of(
                Card.parse("AD"),
                Card.parse("2C"),
                Card.parse("3D"),
                Card.parse("4S"),
                Card.parse("5H")
        );
        assertEquals(HandRank.THREE_OF_A_KIND, variant.evaluateHand(hole, board).getRank());
    }

    @Test
    void rejectsWrongCardCounts() {
        assertThrows(IllegalArgumentException.class, () -> variant.evaluateHand(List.of(), List.of()));
    }
}
