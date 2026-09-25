package com.poker.game.variants;

public final class Variants {

    private Variants() {
    }

    public static PokerVariant create(String name) {
        String key = name == null || name.isBlank() ? TexasHoldemVariant.NAME : name.trim().toUpperCase();
        return switch (key) {
            case TexasHoldemVariant.NAME -> new TexasHoldemVariant();
            case OmahaVariant.NAME -> new OmahaVariant();
            case PineappleVariant.NAME -> new PineappleVariant();
            case FiveCardDrawVariant.NAME -> new FiveCardDrawVariant();
            case RazzVariant.NAME -> new RazzVariant();
            default -> throw new IllegalArgumentException(
                    "Supported variants are TEXAS_HOLDEM, OMAHA, PINEAPPLE, FIVE_CARD_DRAW, and RAZZ."
            );
        };
    }
}
