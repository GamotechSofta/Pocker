package com.poker.game.engine;

import com.poker.game.evaluator.HandEvaluation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public class PotManager {

    public int collectBet(PlayerState player, int amount) {
        Objects.requireNonNull(player, "player is required");
        player.collect(amount);
        return amount;
    }

    public int calculatePot(Iterable<PlayerState> players) {
        int total = 0;
        for (PlayerState player : players) {
            total += player.getHandContribution();
        }
        return total;
    }

    public List<SidePot> calculateSidePots(Iterable<PlayerState> players) {
        List<PlayerState> contributors = new ArrayList<>();
        for (PlayerState player : players) {
            if (player.getHandContribution() > 0) {
                contributors.add(player);
            }
        }
        if (contributors.isEmpty()) {
            return List.of();
        }

        TreeSet<Integer> levels = new TreeSet<>();
        for (PlayerState player : contributors) {
            levels.add(player.getHandContribution());
        }

        List<SidePot> pots = new ArrayList<>();
        int previous = 0;
        for (int level : levels) {
            int layer = level - previous;
            List<PlayerState> involved = contributors.stream()
                    .filter(player -> player.getHandContribution() >= level)
                    .toList();
            int amount = layer * involved.size();
            if (amount == 0) {
                continue;
            }
            List<String> eligible = involved.stream()
                    .filter(player -> !player.isFolded())
                    .map(PlayerState::getPlayerId)
                    .toList();
            pots.add(new SidePot(amount, eligible));
            previous = level;
        }
        return mergeIdenticalEligibility(pots);
    }

    public List<Payout> distributePot(
            List<SidePot> pots,
            Map<String, HandEvaluation> evaluations,
            List<String> clockwiseFromLeftOfDealer
    ) {
        Map<String, Integer> winnings = new LinkedHashMap<>();
        for (SidePot pot : pots) {
            if (pot.amount() == 0 || pot.eligiblePlayerIds().isEmpty()) {
                continue;
            }
            List<String> winners = bestEligible(pot.eligiblePlayerIds(), evaluations);
            split(winnings, winners, pot.amount(), clockwiseFromLeftOfDealer);
        }
        return winnings.entrySet().stream()
                .map(entry -> new Payout(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<Payout> awardEntirePot(Iterable<PlayerState> players, String winnerId) {
        int pot = calculatePot(players);
        if (pot == 0) {
            return List.of();
        }
        return List.of(new Payout(winnerId, pot));
    }

    private static List<SidePot> mergeIdenticalEligibility(List<SidePot> pots) {
        if (pots.size() <= 1) {
            return List.copyOf(pots);
        }
        List<SidePot> merged = new ArrayList<>();
        SidePot current = pots.getFirst();
        for (int i = 1; i < pots.size(); i++) {
            SidePot next = pots.get(i);
            if (current.eligiblePlayerIds().equals(next.eligiblePlayerIds())) {
                current = new SidePot(current.amount() + next.amount(), current.eligiblePlayerIds());
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    private static List<String> bestEligible(List<String> eligible, Map<String, HandEvaluation> evaluations) {
        HandEvaluation best = null;
        List<String> winners = new ArrayList<>();
        for (String playerId : eligible) {
            HandEvaluation evaluation = evaluations.get(playerId);
            if (evaluation == null) {
                continue;
            }
            if (best == null || evaluation.beats(best)) {
                best = evaluation;
                winners.clear();
                winners.add(playerId);
            } else if (evaluation.ties(best)) {
                winners.add(playerId);
            }
        }
        if (winners.isEmpty()) {
            return List.copyOf(eligible);
        }
        return List.copyOf(winners);
    }

    private static void split(
            Map<String, Integer> winnings,
            List<String> winners,
            int amount,
            List<String> clockwiseFromLeftOfDealer
    ) {
        int share = amount / winners.size();
        int remainder = amount % winners.size();
        for (String winner : winners) {
            winnings.merge(winner, share, Integer::sum);
        }
        if (remainder == 0) {
            return;
        }
        List<String> oddChipOrder = clockwiseFromLeftOfDealer.stream()
                .filter(winners::contains)
                .toList();
        if (oddChipOrder.isEmpty()) {
            oddChipOrder = winners.stream().sorted(Comparator.naturalOrder()).toList();
        }
        for (int i = 0; i < remainder; i++) {
            String recipient = oddChipOrder.get(i % oddChipOrder.size());
            winnings.merge(recipient, 1, Integer::sum);
        }
    }
}
