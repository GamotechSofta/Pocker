package com.poker.game.engine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexasHoldemEngineDealingTest {

    @Test
    void eachPlayerReceivesTwoUniqueHoleCards() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        Set<String> codes = new HashSet<>();
        for (PlayerState player : engine.state().getPlayers()) {
            assertEquals(2, player.getHoleCards().size());
            player.getHoleCards().forEach(card -> assertTrue(codes.add(card.code())));
        }
        assertEquals(6, codes.size());
        assertTrue(engine.state().getCommunityCards().isEmpty());
    }

    @Test
    void flopTurnAndRiverAreDealtInOrderAfterBettingCompletes() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        List<Integer> seats = List.of(0, 1, 2);
        engine.startHand(EngineHands.firstHandDeck(
                seats,
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
        assertEquals(GameStatus.FLOP, engine.state().getStatus());
        assertEquals(List.of("2C", "3D", "8H"), engine.state().getCommunityCards().stream().map(card -> card.code()).toList());

        engine.apply(PlayerAction.check("b"));
        engine.apply(PlayerAction.check("c"));
        engine.apply(PlayerAction.check("a"));
        assertEquals(GameStatus.TURN, engine.state().getStatus());
        assertEquals("9S", engine.state().getCommunityCards().get(3).code());

        engine.apply(PlayerAction.check("b"));
        engine.apply(PlayerAction.check("c"));
        engine.apply(PlayerAction.check("a"));
        assertEquals(GameStatus.RIVER, engine.state().getStatus());
        assertEquals("KD", engine.state().getCommunityCards().get(4).code());
        assertEquals(5, engine.state().getCommunityCards().size());
    }

    @Test
    void scriptedHoleCardsGoToTheSeatsLeftOfTheDealerFirst() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand(EngineHands.firstHandDeck(
                List.of(0, 1, 2),
                Map.of(
                        0, List.of("AC", "KC"),
                        1, List.of("2H", "3H"),
                        2, List.of("4S", "5S")
                ),
                List.of("6C", "7D", "8H", "9S", "TD")
        ));
        assertEquals("2H", engine.state().player("b").getHoleCards().get(0).code());
        assertEquals("3H", engine.state().player("b").getHoleCards().get(1).code());
        assertEquals("4S", engine.state().player("c").getHoleCards().getFirst().code());
        assertEquals("AC", engine.state().player("a").getHoleCards().getFirst().code());
    }
}
