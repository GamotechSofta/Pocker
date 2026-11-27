package com.poker.entity;

import java.util.UUID;

/** Embedded seat on a {@link PokerTable} document. */
public class PokerTablePlayer {

    private UUID id;
    private UUID userId;
    private String username;
    private String email;
    private int seat;
    private int chipStack;
    /** Active buy-in/rebuy escrow id (survives process restart). */
    private String escrowId;

    protected PokerTablePlayer() {
    }

    public PokerTablePlayer(User user, int seat, int chipStack) {
        this(user, seat, chipStack, null);
    }

    public PokerTablePlayer(User user, int seat, int chipStack, String escrowId) {
        this(user.getId(), user.getDisplayName(), user.getEmail(), seat, chipStack, escrowId);
    }

    public static PokerTablePlayer tournamentSeat(User user, int seat, int stack) {
        return new PokerTablePlayer(user.getId(), user.getDisplayName(), null, seat, stack, null);
    }

    public PokerTablePlayer transferTournamentSeat(int destination) {
        if (escrowId != null || chipStack <= 0 || destination < 0) throw new IllegalStateException("Invalid tournament transfer seat.");
        return new PokerTablePlayer(userId, username, null, destination, chipStack, null);
    }

    private PokerTablePlayer(UUID userId, String username, String email, int seat, int chipStack, String escrowId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.seat = seat;
        this.chipStack = chipStack;
        this.escrowId = escrowId;
    }

    public PokerTablePlayer copy() {
        PokerTablePlayer cloned = new PokerTablePlayer();
        cloned.id = this.id;
        cloned.userId = this.userId;
        cloned.username = this.username;
        cloned.email = this.email;
        cloned.seat = this.seat;
        cloned.chipStack = this.chipStack;
        cloned.escrowId = this.escrowId;
        return cloned;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public int getSeat() {
        return seat;
    }

    public int getChipStack() {
        return chipStack;
    }

    public void setChipStack(int chipStack) {
        this.chipStack = chipStack;
    }

    public String getEscrowId() {
        return escrowId;
    }

    public void setEscrowId(String escrowId) {
        this.escrowId = escrowId;
    }
}
