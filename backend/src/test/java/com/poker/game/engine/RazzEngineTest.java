package com.poker.game.engine;

import com.poker.game.evaluator.HandRank;
import com.poker.game.variants.RazzVariant;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RazzEngineTest {

    @Test
    void dealsThreeThenThirdStreetBetting() {
        TexasHoldemEngine engine = razzTwoPlayers();
        engine.startHand();
        assertEquals(GameStatus.THIRD_STREET, engine.state().getStatus());
        assertEquals("RAZZ", engine.state().getVariant());
        Set<String> codes = new HashSet<>();
        for (PlayerState player : engine.state().getPlayers()) {
            assertEquals(3, player.getHoleCards().size());
            player.getHoleCards().forEach(card -> assertTrue(codes.add(card.code())));
        }
        assertEquals(6, codes.size());
        assertTrue(engine.state().getCommunityCards().isEmpty());
    }

    @Test
    void studStreetsToSevenThenLowShowdown() {
        TexasHoldemEngine engine = razzTwoPlayers();
        // deal order left of dealer (seat 0): b then a each round
        // holes: a=A23, b=789
        // streets 4-7 (b,a each): a gets 4,5,K,Q and b gets T,J,Q,K wait need unique cards
        // street deal order per street: left of dealer = seat 1 (b) first, then seat 0 (a)
        // 4 streets × 2 = 8 cards: b4,a4, b5,a5, b6,a6, b7,a7
        engine.startHand(EngineHands.razzDeck(
                List.of(0, 1),
                Map.of(
                        0, List.of("AS", "2H", "3C"),
                        1, List.of("7D", "8H", "9C")
                ),
                List.of(
                        "TD", "4D", // 4th: b, a
                        "JC", "5H", // 5th
                        "QD", "KC", // 6th — a gets K
                        "KH", "QH"  // 7th — a gets Q; a has A2345KQ → wheel; b has 789TJQK
                )
        ));
        assertEquals(GameStatus.THIRD_STREET, engine.state().getStatus());
        engine.apply(PlayerAction.call("a"));
        engine.apply(PlayerAction.check("b"));
        assertEquals(GameStatus.FOURTH_STREET, engine.state().getStatus());
        assertEquals(4, engine.state().player("a").getHoleCards().size());
        assertEquals(4, engine.state().player("b").getHoleCards().size());

        for (int street = 0; street < 4; street++) {
            String first = EngineHands.currentId(engine);
            engine.apply(PlayerAction.check(first));
            String second = EngineHands.currentId(engine);
            engine.apply(PlayerAction.check(second));
        }

        assertEquals(GameStatus.NEXT_HAND, engine.state().getStatus());
        assertEquals(7, engine.state().player("a").getHoleCards().size());
        assertEquals(7, engine.state().player("b").getHoleCards().size());
        assertTrue(engine.state().getCommunityCards().isEmpty());
        assertEquals(HandRank.RAZZ_LOW, showdownOf(engine, "a"));
        assertEquals("5-high (wheel)", descriptionOf(engine, "a"));
        assertTrue(EngineHands.chips(engine, "a") > EngineHands.chips(engine, "b"));
    }

    private static TexasHoldemEngine razzTwoPlayers() {
        TexasHoldemEngine engine = new TexasHoldemEngine(
                TableConfig.developmentDefaults(),
                new RazzVariant(),
                new SecureRandom()
        );
        engine.sitPlayer("a", "Alice", 0, 1000);
        engine.sitPlayer("b", "Bob", 1, 1000);
        return engine;
    }

    private static HandRank showdownOf(TexasHoldemEngine engine, String playerId) {
        return engine.state().getShowdown().stream()
                .filter(result -> result.playerId().equals(playerId))
                .findFirst()
                .orElseThrow()
                .evaluation()
                .getRank();
    }

    private static String descriptionOf(TexasHoldemEngine engine, String playerId) {
        return engine.state().getShowdown().stream()
                .filter(result -> result.playerId().equals(playerId))
                .findFirst()
                .orElseThrow()
                .evaluation()
                .getDescription();
    }
}
