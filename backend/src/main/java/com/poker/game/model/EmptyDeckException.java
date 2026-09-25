package com.poker.game.model;

public class EmptyDeckException extends RuntimeException {

    public EmptyDeckException() {
        super("The deck has no cards remaining.");
    }
}
