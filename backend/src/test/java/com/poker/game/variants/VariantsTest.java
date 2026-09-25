package com.poker.game.variants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VariantsTest {

    @Test
    void createsAllSupportedVariants() {
        assertInstanceOf(TexasHoldemVariant.class, Variants.create("TEXAS_HOLDEM"));
        assertInstanceOf(OmahaVariant.class, Variants.create("omaha"));
        assertInstanceOf(PineappleVariant.class, Variants.create("PINEAPPLE"));
        assertInstanceOf(FiveCardDrawVariant.class, Variants.create("FIVE_CARD_DRAW"));
        assertInstanceOf(RazzVariant.class, Variants.create("RAZZ"));
        assertEquals(TexasHoldemVariant.NAME, Variants.create(null).getName());
    }

    @Test
    void rejectsUnknownVariants() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Variants.create("BAD_VARIANT"));
        assertTrueMessage(ex);
    }

    private static void assertTrueMessage(IllegalArgumentException ex) {
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("TEXAS_HOLDEM"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("OMAHA"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("PINEAPPLE"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("FIVE_CARD_DRAW"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("RAZZ"));
    }
}
