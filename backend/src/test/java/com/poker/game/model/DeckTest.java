package com.poker.game.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckTest {

    @Test
    void newDeckContainsExactlyFiftyTwoUniqueCards() {
        Deck deck = Deck.createDeck(new Random(1L));
        assertEquals(Deck.STANDARD_SIZE, deck.remainingCards());
        assertEquals(52, uniqueDrawn(deck).size());
    }

    @Test
    void newDeckContainsEveryRankAndSuit() {
        Set<Card> cards = uniqueDrawn(Deck.createDeck(new Random(1L)));
        assertEquals(13, cards.stream().map(Card::rank).distinct().count());
        assertEquals(4, cards.stream().map(Card::suit).distinct().count());
    }

    @Test
    void shuffleKeepsSizeAndUniqueness() {
        Deck deck = Deck.createDeck(new Random(7L));
        deck.shuffle();
        assertEquals(52, deck.remainingCards());
        assertEquals(52, uniqueDrawn(deck).size());
    }

    @Test
    void shuffleChangesOrderButIsDeterministicForTheSameSeed() {
        Deck unshuffled = Deck.createDeck(new Random(1L));
        List<Card> originalOrder = drawAll(unshuffled);

        Deck first = Deck.createDeck(new Random(99L));
        Deck second = Deck.createDeck(new Random(99L));
        first.shuffle();
        second.shuffle();
        List<Card> shuffledOnce = drawAll(first);
        List<Card> shuffledAgain = drawAll(second);

        assertEquals(shuffledOnce, shuffledAgain);
        assertNotEquals(originalOrder, shuffledOnce);
    }

    @Test
    void drawRemovesOneCardFromTheTop() {
        Deck deck = Deck.createDeck(new Random(1L));
        Card first = deck.draw();
        assertEquals(51, deck.remainingCards());
        Set<Card> remaining = uniqueDrawn(deck);
        assertEquals(51, remaining.size());
        assertTrue(remaining.stream().noneMatch(card -> card.equals(first)));
    }

    @Test
    void drawMultipleReturnsRequestedCount() {
        Deck deck = Deck.createDeck(new Random(1L));
        List<Card> hole = deck.drawMultiple(2);
        assertEquals(2, hole.size());
        assertEquals(50, deck.remainingCards());
        assertNotEquals(hole.get(0), hole.get(1));
    }

    @Test
    void drawMultipleZeroReturnsEmptyList() {
        Deck deck = Deck.createDeck(new Random(1L));
        assertEquals(List.of(), deck.drawMultiple(0));
        assertEquals(52, deck.remainingCards());
    }

    @Test
    void drawingMoreThanRemainingThrows() {
        Deck deck = Deck.createDeck(new Random(1L));
        deck.drawMultiple(50);
        assertThrows(EmptyDeckException.class, () -> deck.drawMultiple(3));
        assertThrows(IllegalArgumentException.class, () -> deck.drawMultiple(-1));
    }

    @Test
    void drawingFromEmptyDeckThrows() {
        Deck deck = Deck.createDeck(new Random(1L));
        drawAll(deck);
        assertEquals(0, deck.remainingCards());
        assertThrows(EmptyDeckException.class, deck::draw);
    }

    @Test
    void resetRestoresAFullUniqueDeck() {
        Deck deck = Deck.createDeck(new Random(3L));
        deck.shuffle();
        deck.drawMultiple(10);
        deck.reset();
        assertEquals(52, deck.remainingCards());
        assertEquals(52, uniqueDrawn(deck).size());
    }

    private static Set<Card> uniqueDrawn(Deck deck) {
        return new HashSet<>(drawAll(deck));
    }

    private static List<Card> drawAll(Deck deck) {
        List<Card> drawn = new ArrayList<>();
        while (deck.remainingCards() > 0) {
            drawn.add(deck.draw());
        }
        return drawn;
    }
}
