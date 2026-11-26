package com.poker.tournament.entity;

public record BlindLevel(int levelNumber, int smallBlind, int bigBlind, int ante, int durationSeconds, boolean breakAfter) {
    public BlindLevel {
        if (levelNumber < 1 || smallBlind < 1 || bigBlind < smallBlind || ante < 0 || durationSeconds < 1) {
            throw new IllegalArgumentException("Invalid blind level configuration.");
        }
    }
}
