package com.poker.game.engine;

import com.poker.game.evaluator.HandRank;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexasHoldemEngineShowdownTest {

    @Test
    void highestHandWinsAtShowdown() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand(EngineHands.firstHandDeck(
                List.of(0, 1, 2),
                Map.of(
                        0, List.of("AC", "AH"),
                        1, List.of("5C", "5D"),
                        2, List.of("7C", "7D")
                ),
                List.of("2C", "3D", "8H", "9S", "KD")
        ));
        engine.apply(PlayerAction.call("a"));
        engine.apply(PlayerAction.call("b"));
        engine.apply(PlayerAction.check("c"));
        for (int street = 0; street < 3; street++) {
            engine.apply(PlayerAction.check("b"));
            engine.apply(PlayerAction.check("c"));
            engine.apply(PlayerAction.check("a"));
        }

        assertEquals(GameStatus.NEXT_HAND, engine.state().getStatus());
        assertEquals(5, engine.state().getCommunityCards().size());
        assertEquals(3, engine.state().getShowdown().size());
        assertEquals("a", engine.state().getPayouts().getFirst().playerId());
        assertEquals(60, engine.state().getPayouts().getFirst().amount());
        assertEquals(1040, EngineHands.chips(engine, "a"));
        assertEquals(980, EngineHands.chips(engine, "b"));
        assertEquals(980, EngineHands.chips(engine, "c"));
        assertEquals(HandRank.ONE_PAIR, showdownOf(engine, "a"));
        assertEquals(3000, EngineHands.totalChips(engine));
    }

    @Test
    void tiedHandsSplitThePot() {
        TexasHoldemEngine engine = EngineHands.seated(
                new EngineHands.Seat("a", "Alice", 0, 1000),
                new EngineHands.Seat("b", "Bob", 1, 1000)
        );
        engine.startHand(EngineHands.firstHandDeck(
                List.of(0, 1),
                Map.of(
                        0, List.of("2C", "3D"),
                        1, List.of("2H", "3S")
                ),
                List.of("TS", "JS", "QS", "KS", "AS")
        ));
        engine.apply(PlayerAction.call("a"));
        engine.apply(PlayerAction.check("b"));
        for (int street = 0; street < 3; street++) {
            engine.apply(PlayerAction.check("b"));
            engine.apply(PlayerAction.check("a"));
        }

        assertEquals(2, engine.state().getPayouts().size());
        assertEquals(1000, EngineHands.chips(engine, "a"));
        assertEquals(1000, EngineHands.chips(engine, "b"));
        assertTrue(engine.state().getShowdown().stream().allMatch(result ->
                result.evaluation().getRank() == HandRank.ROYAL_FLUSH));
    }

    private static HandRank showdownOf(TexasHoldemEngine engine, String playerId) {
        return engine.state().getShowdown().stream()
                .filter(result -> result.playerId().equals(playerId))
                .findFirst()
                .orElseThrow()
                .evaluation()
                .getRank();
    }
}
