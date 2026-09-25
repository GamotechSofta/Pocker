package com.poker.game.evaluator;

import com.poker.game.model.Card;

import java.util.List;
import java.util.Objects;

public final class HandEvaluation implements Comparable<HandEvaluation> {

    private final HandRank rank;
    private final List<Integer> rankingValues;
    private final List<Card> cards;
    private final String description;

    public HandEvaluation(HandRank rank, List<Integer> rankingValues, List<Card> cards, String description) {
        this.rank = Objects.requireNonNull(rank);
        this.rankingValues = List.copyOf(rankingValues);
        this.cards = List.copyOf(cards);
        this.description = Objects.requireNonNull(description);
    }

    public HandRank getRank() {
        return rank;
    }

    public List<Integer> getRankingValues() {
        return rankingValues;
    }

    public List<Card> getCards() {
        return cards;
    }

    public String getDescription() {
        return description;
    }

    public boolean beats(HandEvaluation other) {
        return compareTo(other) > 0;
    }

    public boolean ties(HandEvaluation other) {
        return compareTo(other) == 0;
    }

    @Override
    public int compareTo(HandEvaluation other) {
        int rankCompare = Integer.compare(rank.getStrength(), other.rank.getStrength());
        if (rankCompare != 0) {
            return rankCompare;
        }
        int length = Math.min(rankingValues.size(), other.rankingValues.size());
        for (int i = 0; i < length; i++) {
            int valueCompare = Integer.compare(rankingValues.get(i), other.rankingValues.get(i));
            if (valueCompare != 0) {
                return valueCompare;
            }
        }
        return Integer.compare(rankingValues.size(), other.rankingValues.size());
    }

    @Override
    public String toString() {
        return description;
    }
}
