package com.poker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.table")
public class TableProperties {

    private int smallBlind = 10;
    private int bigBlind = 20;
    private int startingChips = 1000;
    private int maxPlayers = 6;
    private int maxDrawCount = 5;
    /** Seconds a player has to act before the server checks or folds for them. */
    private int turnSeconds = 30;

    public int getTurnSeconds() {
        return turnSeconds;
    }

    public void setTurnSeconds(int turnSeconds) {
        this.turnSeconds = turnSeconds;
    }

    public int getSmallBlind() {
        return smallBlind;
    }

    public void setSmallBlind(int smallBlind) {
        this.smallBlind = smallBlind;
    }

    public int getBigBlind() {
        return bigBlind;
    }

    public void setBigBlind(int bigBlind) {
        this.bigBlind = bigBlind;
    }

    public int getStartingChips() {
        return startingChips;
    }

    public void setStartingChips(int startingChips) {
        this.startingChips = startingChips;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int getMaxDrawCount() {
        return maxDrawCount;
    }

    public void setMaxDrawCount(int maxDrawCount) {
        this.maxDrawCount = maxDrawCount;
    }
}
