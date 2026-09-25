package com.poker.game.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CardTest {

    @Test
    void standardDeckHasThirteenRanksAndFourSuits() {
        assertEquals(13, Rank.values().length);
        assertEquals(4, Suit.values().length);
    }

    @Test
    void ranksAreOrderedFromTwoToAce() {
        assertEquals(2, Rank.TWO.getValue());
        assertEquals(14, Rank.ACE.getValue());
        assertEquals("A", Rank.ACE.getSymbol());
        assertEquals("T", Rank.TEN.getSymbol());
    }

    @Test
    void codeUsesRankThenSuit() {
        assertEquals("AS", new Card(Rank.ACE, Suit.SPADES).code());
        assertEquals("KH", new Card(Rank.KING, Suit.HEARTS).code());
        assertEquals("TD", Card.parse("TD").code());
    }

    @Test
    void parseRoundTripsEveryCard() {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                Card card = new Card(rank, suit);
                assertEquals(card, Card.parse(card.code()));
            }
        }
    }

    @Test
    void equalCardsAreEqual() {
        assertEquals(new Card(Rank.ACE, Suit.SPADES), Card.parse("AS"));
        assertEquals(new Card(Rank.ACE, Suit.SPADES).hashCode(), Card.parse("AS").hashCode());
        assertNotEquals(Card.parse("AS"), Card.parse("AH"));
    }

    @Test
    void allFiftyTwoCombinationsAreUnique() {
        Set<Card> cards = new HashSet<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        assertEquals(52, cards.size());
    }

    @Test
    void rejectsInvalidCodes() {
        assertThrows(IllegalArgumentException.class, () -> Card.parse("A"));
        assertThrows(IllegalArgumentException.class, () -> Card.parse("XX"));
        assertThrows(NullPointerException.class, () -> new Card(null, Suit.SPADES));
        assertThrows(NullPointerException.class, () -> new Card(Rank.ACE, null));
    }
}
