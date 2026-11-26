package com.poker.game.model;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class Deck {

    public static final int STANDARD_SIZE = 52;

    private final List<Card> cards = new ArrayList<>(STANDARD_SIZE);
    private final Random random;

    public Deck() {
        this(new SecureRandom());
    }

    public Deck(Random random) {
        this.random = Objects.requireNonNull(random, "random is required");
        reset();
    }

    public static Deck createDeck() {
        return new Deck();
    }

    public static Deck createDeck(Random random) {
        return new Deck(random);
    }

    /**
     * Loads a supplied draw order, including a previously persisted shuffle.
     */
    public static Deck ofCards(List<Card> cards) {
        Deck deck = new Deck(new Random(0L));
        deck.cards.clear();
        deck.cards.addAll(Objects.requireNonNull(cards, "cards is required"));
        return deck;
    }

    public void shuffle() {
        Collections.shuffle(cards, random);
    }

    public Card draw() {
        if (cards.isEmpty()) {
            throw new EmptyDeckException();
        }
        return cards.removeFirst();
    }

    public List<Card> drawMultiple(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Cannot draw a negative number of cards");
        }
        if (count > cards.size()) {
            throw new EmptyDeckException();
        }
        List<Card> drawn = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            drawn.add(draw());
        }
        return List.copyOf(drawn);
    }

    public int remainingCards() {
        return cards.size();
    }

    public void reset() {
        cards.clear();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
    }
}
