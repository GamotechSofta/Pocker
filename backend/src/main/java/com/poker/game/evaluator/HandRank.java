package com.poker.game.evaluator;

public enum HandRank {
    HIGH_CARD(1, "High Card"),
    ONE_PAIR(2, "One Pair"),
    TWO_PAIR(3, "Two Pair"),
    THREE_OF_A_KIND(4, "Three of a Kind"),
    STRAIGHT(5, "Straight"),
    FLUSH(6, "Flush"),
    FULL_HOUSE(7, "Full House"),
    FOUR_OF_A_KIND(8, "Four of a Kind"),
    STRAIGHT_FLUSH(9, "Straight Flush"),
    ROYAL_FLUSH(10, "Royal Flush"),
    /** Dedicated Razz low; compare via rankingValues only (same strength). */
    RAZZ_LOW(0, "Razz Low");

    private final int strength;
    private final String displayName;

    HandRank(int strength, String displayName) {
        this.strength = strength;
        this.displayName = displayName;
    }

    public int getStrength() {
        return strength;
    }

    public String getDisplayName() {
        return displayName;
    }
}
