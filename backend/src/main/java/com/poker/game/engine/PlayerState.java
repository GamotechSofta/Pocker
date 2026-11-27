package com.poker.game.engine;

import com.poker.game.model.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PlayerState {
    public record Snapshot(String playerId, String username, int seat, int chipStack, List<Card> holeCards,
                           int betThisRound, int handContribution, boolean folded, boolean allIn,
                           boolean actedThisRound, boolean raiseAllowed, boolean connected, boolean drawCompleted) {
        public Snapshot { holeCards = List.copyOf(holeCards); }
    }
    public Snapshot snapshot() {
        return new Snapshot(playerId, username, seat, chipStack, holeCards, betThisRound, handContribution,
                folded, allIn, actedThisRound, raiseAllowed, connected, drawCompleted);
    }
    public static PlayerState restore(Snapshot s) {
        PlayerState p = new PlayerState(s.playerId(), s.username(), s.seat(), s.chipStack());
        p.holeCards.addAll(s.holeCards()); p.betThisRound = s.betThisRound(); p.handContribution = s.handContribution();
        p.folded = s.folded(); p.allIn = s.allIn(); p.actedThisRound = s.actedThisRound();
        p.raiseAllowed = s.raiseAllowed(); p.connected = s.connected(); p.drawCompleted = s.drawCompleted();
        return p;
    }

    private final String playerId;
    private final String username;
    private final int seat;
    private int chipStack;
    private final List<Card> holeCards = new ArrayList<>();
    private int betThisRound;
    private int handContribution;
    private boolean folded;
    private boolean allIn;
    private boolean actedThisRound;
    private boolean raiseAllowed = true;
    private boolean connected = true;
    private boolean drawCompleted;

    public PlayerState(String playerId, String username, int seat, int chipStack) {
        this.playerId = Objects.requireNonNull(playerId, "playerId is required");
        this.username = Objects.requireNonNull(username, "username is required");
        this.seat = seat;
        this.chipStack = chipStack;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getUsername() {
        return username;
    }

    public int getSeat() {
        return seat;
    }

    public int getChipStack() {
        return chipStack;
    }

    public List<Card> getHoleCards() {
        return List.copyOf(holeCards);
    }

    public int getBetThisRound() {
        return betThisRound;
    }

    public int getHandContribution() {
        return handContribution;
    }

    public boolean isFolded() {
        return folded;
    }

    public boolean isAllIn() {
        return allIn;
    }

    public boolean hasActedThisRound() {
        return actedThisRound;
    }

    public boolean isRaiseAllowed() {
        return raiseAllowed;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean hasCompletedDraw() {
        return drawCompleted;
    }

    public PlayerStatus getStatus(boolean handInProgress) {
        if (chipStack == 0 && !handInProgress) {
            return PlayerStatus.SITTING_OUT;
        }
        if (!handInProgress) {
            return PlayerStatus.WAITING;
        }
        if (folded) {
            return PlayerStatus.FOLDED;
        }
        if (allIn) {
            return PlayerStatus.ALL_IN;
        }
        return PlayerStatus.ACTIVE;
    }

    public boolean isInHand() {
        return !folded && !holeCards.isEmpty();
    }

    public boolean canAct() {
        return !folded && !allIn && chipStack > 0;
    }

    void addHoleCard(Card card) {
        holeCards.add(card);
    }

    Card discardHoleCard(int index) {
        if (index < 0 || index >= holeCards.size()) {
            throw GameActionException.invalid("Choose one of your hole cards to discard.");
        }
        return holeCards.remove(index);
    }

    void markDrawCompleted() {
        drawCompleted = true;
    }

    void collect(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot collect a negative amount");
        }
        if (amount > chipStack) {
            throw GameActionException.insufficientChips();
        }
        chipStack -= amount;
        betThisRound += amount;
        handContribution += amount;
        if (chipStack == 0) {
            allIn = true;
        }
    }

    void credit(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot credit a negative amount");
        }
        chipStack += amount;
    }

    void fold() {
        folded = true;
        actedThisRound = true;
    }

    void markActed() {
        actedThisRound = true;
    }

    void setRaiseAllowed(boolean raiseAllowed) {
        this.raiseAllowed = raiseAllowed;
    }

    void clearActed() {
        actedThisRound = false;
    }

    void clearContributions() {
        betThisRound = 0;
        handContribution = 0;
    }

    void resetForBettingRound() {
        betThisRound = 0;
        actedThisRound = false;
        raiseAllowed = true;
    }

    void resetForNewHand() {
        holeCards.clear();
        betThisRound = 0;
        handContribution = 0;
        folded = false;
        allIn = false;
        actedThisRound = false;
        raiseAllowed = true;
        drawCompleted = false;
    }

    void setConnected(boolean connected) {
        this.connected = connected;
    }
}
