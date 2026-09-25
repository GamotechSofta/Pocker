package com.poker.game.evaluator;

import com.poker.game.model.Card;
import com.poker.game.model.Rank;
import com.poker.game.model.Suit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HandEvaluator {

    public HandEvaluation evaluate(Collection<Card> cards) {
        List<Card> unique = validated(cards);
        if (unique.size() == 5) {
            return evaluateFive(unique);
        }
        HandEvaluation best = null;
        for (List<Card> combination : fiveCardCombinations(unique)) {
            HandEvaluation candidate = evaluateFive(combination);
            if (best == null || candidate.beats(best)) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Omaha: exactly 2 hole cards and exactly 3 community cards. Never the best 5 of 9.
     */
    public HandEvaluation evaluateOmaha(List<Card> holeCards, List<Card> communityCards) {
        if (holeCards == null || holeCards.size() != 4) {
            throw new IllegalArgumentException("Omaha requires 4 hole cards");
        }
        if (communityCards == null || communityCards.size() != 5) {
            throw new IllegalArgumentException("Omaha showdown requires 5 community cards");
        }
        Set<Card> unique = new HashSet<>(9);
        unique.addAll(holeCards);
        unique.addAll(communityCards);
        if (unique.size() != 9) {
            throw new IllegalArgumentException("Omaha cards must be unique");
        }
        HandEvaluation best = null;
        for (List<Card> holePair : combinations(holeCards, 2)) {
            for (List<Card> boardTriple : combinations(communityCards, 3)) {
                List<Card> five = new ArrayList<>(5);
                five.addAll(holePair);
                five.addAll(boardTriple);
                HandEvaluation candidate = evaluate(five);
                if (best == null || candidate.beats(best)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * Razz: best 5-card low from 5–7 hole cards. Aces low; straights and flushes do not count;
     * pairs are bad. Lower unpaired hands beat higher ones (compare high card downward).
     */
    public HandEvaluation evaluateRazz(Collection<Card> cards) {
        List<Card> unique = validated(cards);
        if (unique.size() < 5) {
            throw new IllegalArgumentException("Razz requires at least 5 cards");
        }
        if (unique.size() == 5) {
            return evaluateRazzFive(unique);
        }
        HandEvaluation best = null;
        for (List<Card> combination : fiveCardCombinations(unique)) {
            HandEvaluation candidate = evaluateRazzFive(combination);
            if (best == null || candidate.beats(best)) {
                best = candidate;
            }
        }
        return best;
    }

    private HandEvaluation evaluateRazzFive(List<Card> cards) {
        int[] counts = new int[14];
        for (Card card : cards) {
            counts[razzValue(card.rank())]++;
        }
        List<Integer> quads = razzRanksWithCount(counts, 4);
        List<Integer> trips = razzRanksWithCount(counts, 3);
        List<Integer> pairs = razzRanksWithCount(counts, 2);
        List<Integer> singles = razzRanksWithCount(counts, 1);

        int category;
        List<Integer> order = new ArrayList<>();
        String description;
        if (!quads.isEmpty()) {
            category = 0;
            order.add(quads.getFirst());
            order.addAll(singles);
            description = "Four " + razzPlural(quads.getFirst());
        } else if (!trips.isEmpty() && !pairs.isEmpty()) {
            category = 1;
            order.add(trips.getFirst());
            order.add(pairs.getFirst());
            description = razzName(trips.getFirst()) + "s full of " + razzPlural(pairs.getFirst());
        } else if (!trips.isEmpty()) {
            category = 2;
            order.add(trips.getFirst());
            order.addAll(singles);
            description = "Three " + razzPlural(trips.getFirst());
        } else if (pairs.size() >= 2) {
            category = 3;
            order.add(pairs.get(0));
            order.add(pairs.get(1));
            order.addAll(singles);
            description = "Two pair, " + razzPlural(pairs.get(0)) + " and " + razzPlural(pairs.get(1));
        } else if (pairs.size() == 1) {
            category = 4;
            order.add(pairs.getFirst());
            order.addAll(singles);
            description = "Pair of " + razzPlural(pairs.getFirst());
        } else {
            category = 5;
            order.addAll(singles);
            description = razzUnpairedDescription(singles);
        }

        List<Integer> rankingValues = new ArrayList<>(order.size() + 1);
        rankingValues.add(category);
        for (int value : order) {
            rankingValues.add(-value);
        }
        List<Card> ordered = cards.stream()
                .sorted(Comparator.comparingInt((Card card) -> razzValue(card.rank())).reversed())
                .toList();
        return new HandEvaluation(HandRank.RAZZ_LOW, rankingValues, ordered, description);
    }

    private static String razzUnpairedDescription(List<Integer> descending) {
        if (descending.equals(List.of(5, 4, 3, 2, 1))) {
            return "5-high (wheel)";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < descending.size(); i++) {
            if (i > 0) {
                text.append('-');
            }
            text.append(razzSymbol(descending.get(i)));
        }
        return text.toString();
    }

    private static int razzValue(Rank rank) {
        return rank == Rank.ACE ? 1 : rank.getValue();
    }

    private static List<Integer> razzRanksWithCount(int[] counts, int count) {
        List<Integer> ranks = new ArrayList<>();
        for (int rank = 13; rank >= 1; rank--) {
            if (counts[rank] == count) {
                ranks.add(rank);
            }
        }
        return ranks;
    }

    private static String razzName(int razzValue) {
        if (razzValue == 1) {
            return "Ace";
        }
        return Rank.values()[razzValue - 2].getName();
    }

    private static String razzPlural(int razzValue) {
        if (razzValue == 1) {
            return "Aces";
        }
        return Rank.values()[razzValue - 2].getPluralName();
    }

    private static String razzSymbol(int razzValue) {
        if (razzValue == 1) {
            return "A";
        }
        return Rank.values()[razzValue - 2].getSymbol();
    }

    private HandEvaluation evaluateFive(List<Card> cards) {
        int[] counts = new int[15];
        for (Card card : cards) {
            counts[card.rank().getValue()]++;
        }

        List<Integer> quads = ranksWithCount(counts, 4);
        List<Integer> trips = ranksWithCount(counts, 3);
        List<Integer> pairs = ranksWithCount(counts, 2);
        List<Integer> kickers = ranksWithCount(counts, 1);

        boolean flush = isFlush(cards);
        int straightHigh = straightHigh(counts);

        if (flush && straightHigh == Rank.ACE.getValue()) {
            return new HandEvaluation(
                    HandRank.ROYAL_FLUSH,
                    List.of(Rank.ACE.getValue()),
                    orderedByRank(cards),
                    HandRank.ROYAL_FLUSH.getDisplayName()
            );
        }
        if (flush && straightHigh > 0) {
            return new HandEvaluation(
                    HandRank.STRAIGHT_FLUSH,
                    List.of(straightHigh),
                    orderedStraight(cards, straightHigh),
                    HandRank.STRAIGHT_FLUSH.getDisplayName() + ", " + rankName(straightHigh) + " high"
            );
        }
        if (!quads.isEmpty()) {
            int quad = quads.getFirst();
            int kicker = kickers.getFirst();
            return new HandEvaluation(
                    HandRank.FOUR_OF_A_KIND,
                    List.of(quad, kicker),
                    cardsOfRanks(cards, List.of(quad, kicker)),
                    HandRank.FOUR_OF_A_KIND.getDisplayName() + ", " + plural(quad)
            );
        }
        if (!trips.isEmpty() && !pairs.isEmpty()) {
            int three = trips.getFirst();
            int pair = pairs.getFirst();
            return new HandEvaluation(
                    HandRank.FULL_HOUSE,
                    List.of(three, pair),
                    cardsOfRanks(cards, List.of(three, pair)),
                    HandRank.FULL_HOUSE.getDisplayName() + ", " + plural(three) + " full of " + plural(pair)
            );
        }
        if (flush) {
            List<Integer> values = rankValuesDescending(cards);
            return new HandEvaluation(
                    HandRank.FLUSH,
                    values,
                    orderedByRank(cards),
                    HandRank.FLUSH.getDisplayName() + ", " + rankName(values.getFirst()) + " high"
            );
        }
        if (straightHigh > 0) {
            return new HandEvaluation(
                    HandRank.STRAIGHT,
                    List.of(straightHigh),
                    orderedStraight(cards, straightHigh),
                    HandRank.STRAIGHT.getDisplayName() + ", " + rankName(straightHigh) + " high"
            );
        }
        if (!trips.isEmpty()) {
            int three = trips.getFirst();
            List<Integer> values = new ArrayList<>();
            values.add(three);
            values.addAll(kickers);
            return new HandEvaluation(
                    HandRank.THREE_OF_A_KIND,
                    values,
                    cardsOfRanks(cards, values),
                    HandRank.THREE_OF_A_KIND.getDisplayName() + ", " + plural(three)
            );
        }
        if (pairs.size() == 2) {
            int highPair = pairs.getFirst();
            int lowPair = pairs.get(1);
            int kicker = kickers.getFirst();
            List<Integer> values = List.of(highPair, lowPair, kicker);
            return new HandEvaluation(
                    HandRank.TWO_PAIR,
                    values,
                    cardsOfRanks(cards, values),
                    HandRank.TWO_PAIR.getDisplayName() + ", " + plural(highPair) + " and " + plural(lowPair)
            );
        }
        if (pairs.size() == 1) {
            int pair = pairs.getFirst();
            List<Integer> values = new ArrayList<>();
            values.add(pair);
            values.addAll(kickers);
            return new HandEvaluation(
                    HandRank.ONE_PAIR,
                    values,
                    cardsOfRanks(cards, values),
                    HandRank.ONE_PAIR.getDisplayName() + ", " + plural(pair)
            );
        }
        List<Integer> values = rankValuesDescending(cards);
        return new HandEvaluation(
                HandRank.HIGH_CARD,
                values,
                orderedByRank(cards),
                HandRank.HIGH_CARD.getDisplayName() + ", " + rankName(values.getFirst())
        );
    }

    private static List<Card> validated(Collection<Card> cards) {
        if (cards == null) {
            throw new IllegalArgumentException("cards is required");
        }
        if (cards.size() < 5 || cards.size() > 7) {
            throw new IllegalArgumentException("Evaluator requires 5 to 7 cards");
        }
        Set<Card> unique = new HashSet<>(cards);
        if (unique.size() != cards.size()) {
            throw new IllegalArgumentException("Cards must be unique");
        }
        return List.copyOf(cards);
    }

    private static boolean isFlush(List<Card> cards) {
        Suit suit = cards.getFirst().suit();
        return cards.stream().allMatch(card -> card.suit() == suit);
    }

    /**
     * Returns the high card of a 5-card straight, or 0 if the cards are not a straight.
     * Ace-low (A-2-3-4-5) is a five-high straight, not an Ace-high straight.
     */
    private static int straightHigh(int[] counts) {
        List<Integer> unique = new ArrayList<>();
        for (int rank = Rank.ACE.getValue(); rank >= Rank.TWO.getValue(); rank--) {
            if (counts[rank] == 1) {
                unique.add(rank);
            } else if (counts[rank] > 1) {
                return 0;
            }
        }
        if (unique.size() != 5) {
            return 0;
        }
        if (unique.contains(Rank.ACE.getValue())
                && unique.contains(5)
                && unique.contains(4)
                && unique.contains(3)
                && unique.contains(2)) {
            return 5;
        }
        if (unique.getFirst() - unique.getLast() == 4) {
            return unique.getFirst();
        }
        return 0;
    }

    private static List<Integer> ranksWithCount(int[] counts, int count) {
        List<Integer> ranks = new ArrayList<>();
        for (int rank = Rank.ACE.getValue(); rank >= Rank.TWO.getValue(); rank--) {
            if (counts[rank] == count) {
                ranks.add(rank);
            }
        }
        return ranks;
    }

    private static List<Integer> rankValuesDescending(List<Card> cards) {
        return cards.stream()
                .map(card -> card.rank().getValue())
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private static List<Card> orderedByRank(List<Card> cards) {
        return cards.stream()
                .sorted(Comparator.comparingInt((Card card) -> card.rank().getValue()).reversed())
                .toList();
    }

    private static List<Card> orderedStraight(List<Card> cards, int high) {
        if (high == 5) {
            return cards.stream()
                    .sorted(Comparator.comparingInt(card -> wheelOrder(card.rank().getValue())))
                    .toList();
        }
        return orderedByRank(cards);
    }

    private static int wheelOrder(int rankValue) {
        return rankValue == Rank.ACE.getValue() ? 1 : rankValue;
    }

    private static List<Card> cardsOfRanks(List<Card> cards, List<Integer> ranksInOrder) {
        List<Card> selected = new ArrayList<>(cards.size());
        for (int rank : ranksInOrder) {
            cards.stream()
                    .filter(card -> card.rank().getValue() == rank)
                    .sorted(Comparator.comparing(card -> card.suit().name()))
                    .forEach(selected::add);
        }
        return List.copyOf(selected);
    }

    private static List<List<Card>> fiveCardCombinations(List<Card> cards) {
        return combinations(cards, 5);
    }

    static List<List<Card>> combinations(List<Card> cards, int choose) {
        List<List<Card>> combinations = new ArrayList<>();
        int n = cards.size();
        for (int mask = 0; mask < (1 << n); mask++) {
            if (Integer.bitCount(mask) != choose) {
                continue;
            }
            List<Card> combination = new ArrayList<>(choose);
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    combination.add(cards.get(i));
                }
            }
            combinations.add(combination);
        }
        return combinations;
    }

    private static String rankName(int value) {
        return Rank.values()[value - 2].getName();
    }

    private static String plural(int value) {
        return Rank.values()[value - 2].getPluralName();
    }
}
