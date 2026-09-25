package com.poker.game.variants;

import com.poker.game.evaluator.HandRank;
import com.poker.game.model.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexasHoldemVariantTest {

    private final TexasHoldemVariant variant = new TexasHoldemVariant();

    @Test
    void holdEmUsesTwoHoleCardsAndFiveCommunityCards() {
        assertEquals("TEXAS_HOLDEM", variant.getName());
        assertEquals(2, variant.getInitialHoleCards());
        assertTrue(variant.usesCommunityCards());
        assertEquals(3, variant.flopCount());
        assertEquals(1, variant.turnCount());
        assertEquals(1, variant.riverCount());
        assertTrue(variant.burnBeforeCommunityDeals());
    }

    @Test
    void evaluatesBestFiveOfSeven() {
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
    void rejectsWrongCardCounts() {
        assertThrows(IllegalArgumentException.class, () -> variant.evaluateHand(List.of(), List.of()));
    }
}
