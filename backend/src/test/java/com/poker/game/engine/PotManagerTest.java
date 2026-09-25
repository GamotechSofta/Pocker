package com.poker.game.engine;

import com.poker.game.evaluator.HandEvaluation;
import com.poker.game.evaluator.HandEvaluator;
import com.poker.game.model.Card;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotManagerTest {

    private final PotManager pots = new PotManager();

    @Test
    void calculatePotSumsContributions() {
        PlayerState a = player("a", 0, 100);
        PlayerState b = player("b", 1, 100);
        a.collect(40);
        b.collect(40);
        assertEquals(80, pots.calculatePot(List.of(a, b)));
    }

    @Test
    void equalContributionsMakeOneMainPot() {
        PlayerState a = player("a", 0, 100);
        PlayerState b = player("b", 1, 100);
        a.collect(50);
        b.collect(50);
        List<SidePot> sidePots = pots.calculateSidePots(List.of(a, b));
        assertEquals(1, sidePots.size());
        assertEquals(100, sidePots.getFirst().amount());
        assertEquals(List.of("a", "b"), sidePots.getFirst().eligiblePlayerIds());
    }

    @Test
    void allInCreatesSidePotAndFoldedMoneyStaysInThePot() {
        PlayerState shortStack = player("short", 0, 30);
        PlayerState big1 = player("big1", 1, 200);
        PlayerState big2 = player("big2", 2, 200);
        PlayerState folded = player("fold", 3, 200);
        shortStack.collect(30);
        big1.collect(100);
        big2.collect(100);
        folded.collect(20);
        folded.fold();

        List<SidePot> sidePots = pots.calculateSidePots(List.of(shortStack, big1, big2, folded));
        assertEquals(2, sidePots.size());
        assertEquals(110, sidePots.get(0).amount());
        assertEquals(List.of("short", "big1", "big2"), sidePots.get(0).eligiblePlayerIds());
        assertEquals(140, sidePots.get(1).amount());
        assertEquals(List.of("big1", "big2"), sidePots.get(1).eligiblePlayerIds());
        assertEquals(250, pots.calculatePot(List.of(shortStack, big1, big2, folded)));
    }

    @Test
    void splitPotGivesOddChipToThePlayerLeftOfTheDealer() {
        HandEvaluator evaluator = new HandEvaluator();
        HandEvaluation boardPair = evaluator.evaluate(cards("AS", "AH", "2C", "3D", "8S"));
        Map<String, HandEvaluation> tied = Map.of("left", boardPair, "right", boardPair);

        List<Payout> payouts = pots.distributePot(
                List.of(new SidePot(101, List.of("left", "right"))),
                tied,
                List.of("left", "right")
        );

        assertEquals(2, payouts.size());
        Payout left = payouts.stream().filter(payout -> payout.playerId().equals("left")).findFirst().orElseThrow();
        Payout right = payouts.stream().filter(payout -> payout.playerId().equals("right")).findFirst().orElseThrow();
        assertEquals(51, left.amount());
        assertEquals(50, right.amount());
        assertEquals(101, left.amount() + right.amount());
    }

    @Test
    void betterHandTakesTheWholePot() {
        HandEvaluator evaluator = new HandEvaluator();
        Map<String, HandEvaluation> ranks = Map.of(
                "aces", evaluator.evaluate(cards("AS", "AH", "2C", "3D", "9S", "4H", "8C")),
                "kings", evaluator.evaluate(cards("KS", "KH", "2C", "3D", "9S", "4H", "8C"))
        );
        List<Payout> payouts = pots.distributePot(
                List.of(new SidePot(80, List.of("aces", "kings"))),
                ranks,
                List.of("kings", "aces")
        );
        assertEquals(1, payouts.size());
        assertEquals("aces", payouts.getFirst().playerId());
        assertEquals(80, payouts.getFirst().amount());
    }

    @Test
    void awardEntirePotGoesToTheFoldWinner() {
        PlayerState winner = player("winner", 0, 100);
        PlayerState folded = player("folded", 1, 100);
        winner.collect(20);
        folded.collect(10);
        folded.fold();
        List<Payout> payouts = pots.awardEntirePot(List.of(winner, folded), "winner");
        assertEquals(List.of(new Payout("winner", 30)), payouts);
        assertTrue(folded.isFolded());
    }

    private static PlayerState player(String id, int seat, int chips) {
        return new PlayerState(id, id, seat, chips);
    }

    private static List<Card> cards(String... codes) {
        return java.util.Arrays.stream(codes).map(Card::parse).toList();
    }
}
