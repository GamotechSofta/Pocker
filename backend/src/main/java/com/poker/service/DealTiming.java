package com.poker.service;

/**
 * Mirrors the client's dealer-hand deal animation (one card per seat, round robin),
 * so the first turn and its clock only begin once every card has landed.
 */
public final class DealTiming {

    /** Client waits two animation frames and a network hop before the first card leaves. */
    static final long LEAD_MS = 400;
    static final long CARD_STAGGER_MS = 120;
    static final long CARD_FLIGHT_MS = 460;
    static final long FINISH_MS = 280;
    static final long SETTLE_MS = 400;

    private DealTiming() {
    }

    public static long dealAnimationMs(int players, int holeCardsPerPlayer) {
        int dealtCards = Math.max(2, players) * Math.max(1, holeCardsPerPlayer);
        return LEAD_MS
                + Math.max(0, dealtCards - 1L) * CARD_STAGGER_MS
                + CARD_FLIGHT_MS
                + FINISH_MS
                + SETTLE_MS;
    }
}
