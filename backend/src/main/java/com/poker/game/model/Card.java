package com.poker.game.model;

import java.util.Objects;

public record Card(Rank rank, Suit suit) {

    public Card {
        Objects.requireNonNull(rank, "rank is required");
        Objects.requireNonNull(suit, "suit is required");
    }

    public String code() {
        return rank.getSymbol() + suit.getSymbol();
    }

    public static Card parse(String code) {
        if (code == null || code.length() != 2) {
            throw new IllegalArgumentException("Card code must be two characters, for example AS");
        }
        return new Card(
                Rank.fromSymbol(code.substring(0, 1)),
                Suit.fromSymbol(code.substring(1, 2))
        );
    }

    @Override
    public String toString() {
        return code();
    }
}
