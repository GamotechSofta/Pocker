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

    protected PokerTablePlayer() {
    }

    public PokerTablePlayer(User user, int seat, int chipStack) {
        this.id = UUID.randomUUID();
        this.userId = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.seat = seat;
        this.chipStack = chipStack;
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
}
