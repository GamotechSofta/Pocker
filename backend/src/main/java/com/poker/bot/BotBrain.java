package com.poker.bot;

import com.poker.dto.GameActionRequest;
import com.poker.game.engine.ActionType;
import com.poker.game.engine.GameStatus;
import com.poker.game.engine.LegalActions;
import com.poker.game.engine.PlayerState;
import com.poker.game.evaluator.HandEvaluator;
import com.poker.game.evaluator.HandRank;
import com.poker.game.model.Card;
import com.poker.game.model.Rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Heuristic "trained" bot: pot-odds aware calling, Chen-style preflop strength,
 * made-hand aggression postflop, and sensible discard/draw choices.
 */
public class BotBrain {

    private final HandEvaluator evaluator = new HandEvaluator();

    public GameActionRequest choose(PlayerState self, LegalActions legal, GameStatus status, List<Card> board, int pot) {
        if (legal == null || legal.actions().isEmpty()) {
            throw new IllegalStateException("No legal actions for bot");
        }
        if (legal.allows(ActionType.DISCARD)) {
            return chooseDiscardOrDraw(self, legal, status);
        }
        return chooseBetting(self, legal, status, board, pot);
    }

    private GameActionRequest chooseDiscardOrDraw(PlayerState self, LegalActions legal, GameStatus status) {
        List<Card> holes = self.getHoleCards();
        if (status == GameStatus.DISCARD) {
            // Pineapple: discard exactly one — drop the weakest singleton
            int worst = weakestIndex(holes);
            return new GameActionRequest("DISCARD", worst, null, requestId());
        }
        // 5-Card Draw: discard 0..max of the weakest unpaired / low cards
        int maxDraw = legal.maxBetOrRaiseTo();
        List<Integer> discard = drawDiscardIndices(holes, maxDraw);
        return new GameActionRequest("DISCARD", null, discard, requestId());
    }

    private GameActionRequest chooseBetting(
            PlayerState self,
            LegalActions legal,
            GameStatus status,
            List<Card> board,
            int pot
    ) {
        double strength = handStrength(self.getHoleCards(), board, status);
        boolean facingBet = legal.callAmount() > 0;
        double potOdds = facingBet ? legal.callAmount() / (double) Math.max(1, pot + legal.callAmount()) : 0;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        if (!facingBet) {
            if (strength >= 0.72 && legal.allows(ActionType.BET)) {
                int to = sizedBet(legal, 0.55 + rng.nextDouble() * 0.25);
                return new GameActionRequest("BET", to, null, requestId());
            }
            if (strength >= 0.55 && legal.allows(ActionType.BET) && rng.nextDouble() < 0.45) {
                int to = sizedBet(legal, 0.4 + rng.nextDouble() * 0.2);
                return new GameActionRequest("BET", to, null, requestId());
            }
            if (legal.allows(ActionType.CHECK)) {
                return new GameActionRequest("CHECK", null, null, requestId());
            }
            if (legal.allows(ActionType.BET)) {
                return new GameActionRequest("BET", legal.minBetOrRaiseTo(), null, requestId());
            }
        } else {
            boolean strong = strength >= potOdds + 0.08 || strength >= 0.65;
            boolean medium = strength >= potOdds - 0.02 || strength >= 0.42;
            if (strong && legal.allows(ActionType.RAISE) && rng.nextDouble() < 0.55) {
                int to = sizedBet(legal, 0.65 + rng.nextDouble() * 0.2);
                return new GameActionRequest("RAISE", to, null, requestId());
            }
            if ((strong || medium) && legal.allows(ActionType.CALL)) {
                return new GameActionRequest("CALL", null, null, requestId());
            }
            if (legal.allows(ActionType.FOLD)) {
                return new GameActionRequest("FOLD", null, null, requestId());
            }
            if (legal.allows(ActionType.CALL)) {
                return new GameActionRequest("CALL", null, null, requestId());
            }
            if (legal.allows(ActionType.ALL_IN) && strength >= 0.75) {
                return new GameActionRequest("ALL_IN", null, null, requestId());
            }
        }

        if (legal.allows(ActionType.CHECK)) {
            return new GameActionRequest("CHECK", null, null, requestId());
        }
        if (legal.allows(ActionType.CALL)) {
            return new GameActionRequest("CALL", null, null, requestId());
        }
        if (legal.allows(ActionType.FOLD)) {
            return new GameActionRequest("FOLD", null, null, requestId());
        }
        ActionType any = legal.actions().iterator().next();
        return new GameActionRequest(any.name(), null, null, requestId());
    }

    private int sizedBet(LegalActions legal, double fractionOfMax) {
        int min = legal.minBetOrRaiseTo();
        int max = legal.maxBetOrRaiseTo();
        if (max < min) {
            return min;
        }
        int target = (int) Math.round(min + (max - min) * Math.min(1.0, Math.max(0.0, fractionOfMax)));
        return Math.max(min, Math.min(max, target));
    }

    double handStrength(List<Card> holes, List<Card> board, GameStatus status) {
        if (holes == null || holes.isEmpty()) {
            return 0.2;
        }
        if (board == null || board.isEmpty() || isPreCommunity(status)) {
            return preflopStrength(holes);
        }
        try {
            List<Card> combined = new ArrayList<>(holes.size() + board.size());
            combined.addAll(holes);
            combined.addAll(board);
            if (combined.size() < 5) {
                return preflopStrength(holes) * 0.7 + 0.15;
            }
            var evaluation = evaluator.evaluate(combined);
            return rankToStrength(evaluation.getRank());
        } catch (RuntimeException ex) {
            return preflopStrength(holes) * 0.6;
        }
    }

    private static boolean isPreCommunity(GameStatus status) {
        return status == GameStatus.PRE_FLOP
                || status == GameStatus.DISCARD
                || status == GameStatus.THIRD_STREET
                || status == GameStatus.DRAW
                || status == GameStatus.STARTING;
    }

    private static double rankToStrength(HandRank rank) {
        return switch (rank) {
            case HIGH_CARD -> 0.28;
            case ONE_PAIR -> 0.48;
            case TWO_PAIR -> 0.62;
            case THREE_OF_A_KIND -> 0.74;
            case STRAIGHT -> 0.82;
            case FLUSH -> 0.86;
            case FULL_HOUSE -> 0.92;
            case FOUR_OF_A_KIND -> 0.97;
            case STRAIGHT_FLUSH, ROYAL_FLUSH -> 0.99;
            case RAZZ_LOW -> 0.5;
        };
    }

    /** Chen-inspired score normalized to 0..1 for 2+ hole cards. */
    public double preflopStrength(List<Card> holes) {
        if (holes.size() == 1) {
            return rankValue(holes.getFirst().rank()) / 14.0;
        }
        List<Card> sorted = holes.stream()
                .sorted(Comparator.comparingInt((Card c) -> rankValue(c.rank())).reversed())
                .toList();
        Card a = sorted.get(0);
        Card b = sorted.get(1);
        double score = rankValue(a.rank());
        if (a.rank() == b.rank()) {
            score *= 2;
            score = Math.max(score, 5);
        } else {
            score += rankValue(b.rank()) / 2.0;
            if (a.suit() == b.suit()) {
                score += 2;
            }
            int gap = Math.abs(rankValue(a.rank()) - rankValue(b.rank())) - 1;
            if (gap == 1) {
                score -= 1;
            } else if (gap == 2) {
                score -= 2;
            } else if (gap == 3) {
                score -= 4;
            } else if (gap >= 4) {
                score -= 5;
            }
            if (gap <= 1 && rankValue(a.rank()) < Rank.QUEEN.getValue()) {
                score += 1;
            }
        }
        // Extra hole cards (Omaha/Pineapple/Draw/Razz): boost by next best ranks
        for (int i = 2; i < Math.min(sorted.size(), 4); i++) {
            score += rankValue(sorted.get(i).rank()) * 0.15;
        }
        return Math.max(0.05, Math.min(0.98, score / 20.0));
    }

    static int weakestIndex(List<Card> holes) {
        int worst = 0;
        int worstScore = Integer.MAX_VALUE;
        for (int i = 0; i < holes.size(); i++) {
            int score = rankValue(holes.get(i).rank());
            if (score < worstScore) {
                worstScore = score;
                worst = i;
            }
        }
        return worst;
    }

    public static List<Integer> drawDiscardIndices(List<Card> holes, int maxDraw) {
        int[] counts = new int[15];
        for (Card card : holes) {
            counts[rankValue(card.rank())]++;
        }
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < holes.size(); i++) {
            int value = rankValue(holes.get(i).rank());
            if (counts[value] == 1 && value < Rank.JACK.getValue()) {
                candidates.add(i);
            }
        }
        candidates.sort(Comparator.comparingInt(i -> rankValue(holes.get(i).rank())));
        int take = Math.min(maxDraw, Math.min(candidates.size(), 3));
        return List.copyOf(candidates.subList(0, take));
    }

    private static int rankValue(Rank rank) {
        return rank.getValue();
    }

    private static String requestId() {
        return "bot-" + Long.toHexString(ThreadLocalRandom.current().nextLong());
    }
}
