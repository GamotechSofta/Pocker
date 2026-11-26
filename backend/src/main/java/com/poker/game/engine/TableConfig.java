package com.poker.game.engine;

public record TableConfig(int smallBlind, int bigBlind, int startingChips, int maxPlayers, int maxDrawCount, int ante) {

    public TableConfig(int smallBlind, int bigBlind, int startingChips, int maxPlayers) {
        this(smallBlind, bigBlind, startingChips, maxPlayers, 5);
    }

    public TableConfig(int smallBlind, int bigBlind, int startingChips, int maxPlayers, int maxDrawCount) {
        this(smallBlind, bigBlind, startingChips, maxPlayers, maxDrawCount, 0);
    }

    public TableConfig {
        if (smallBlind <= 0) {
            throw new IllegalArgumentException("smallBlind must be positive");
        }
        if (bigBlind < smallBlind) {
            throw new IllegalArgumentException("bigBlind must be at least smallBlind");
        }
        if (startingChips <= 0) {
            throw new IllegalArgumentException("startingChips must be positive");
        }
        if (maxPlayers < 2 || maxPlayers > 10) {
            throw new IllegalArgumentException("maxPlayers must be between 2 and 10");
        }
        if (maxDrawCount < 0 || maxDrawCount > 5) {
            throw new IllegalArgumentException("maxDrawCount must be between 0 and 5");
        }
        if (ante < 0 || ante > startingChips) {
            throw new IllegalArgumentException("ante must fit the starting stack");
        }
    }

    public static TableConfig developmentDefaults() {
        return new TableConfig(10, 20, 1000, 6, 5);
    }
}
