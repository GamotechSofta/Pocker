package com.poker.game.engine;

import com.poker.game.evaluator.HandRank;
import com.poker.game.variants.PineappleVariant;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PineappleEngineTest {

    @Test
    void dealsThreeHoleCardsThenRequiresDiscardBeforeFlop() {
        TexasHoldemEngine engine = pineappleTwoPlayers();
        engine.startHand();
        assertEquals(GameStatus.DISCARD, engine.state().getStatus());
        assertEquals("PINEAPPLE", engine.state().getVariant());
        Set<String> codes = new HashSet<>();
        for (PlayerState player : engine.state().getPlayers()) {
            assertEquals(3, player.getHoleCards().size());
            player.getHoleCards().forEach(card -> assertTrue(codes.add(card.code())));
        }
        assertEquals(6, codes.size());
        assertTrue(engine.state().getCommunityCards().isEmpty());
        assertTrue(engine.legalActions(EngineHands.currentId(engine)).allows(ActionType.DISCARD));
    }

    @Test
    void discardedCardNeverAppearsOnTheBoard() {
        TexasHoldemEngine engine = pineappleTwoPlayers();
        engine.startHand(EngineHands.firstHandDeck(
                List.of(0, 1),
                Map.of(
                        0, List.of("AS", "KH", "2C"),
                        1, List.of("QD", "JC", "TD")
                ),
                List.of("3H", "4S", "5D", "6C", "7H")
        ));
        assertEquals(GameStatus.DISCARD, engine.state().getStatus());
        // seat 0 is dealer/SB in heads-up first hand; first discard is left of dealer = seat 1 (Bob)
        assertEquals("b", EngineHands.currentId(engine));
        engine.apply(PlayerAction.discard("b", 2)); // discard TD
        assertEquals("a", EngineHands.currentId(engine));
        engine.apply(PlayerAction.discard("a", 2)); // discard 2C

        assertEquals(GameStatus.PRE_FLOP, engine.state().getStatus());
        assertEquals(2, engine.state().player("a").getHoleCards().size());
        assertEquals(2, engine.state().player("b").getHoleCards().size());
        assertEquals("AS", engine.state().player("a").getHoleCards().get(0).code());
        assertEquals("KH", engine.state().player("a").getHoleCards().get(1).code());
        assertTrue(engine.state().getEvents().stream()
                .filter(event -> "PLAYER_DISCARD".equals(event.type()))
                .noneMatch(event -> event.detail().contains("2C") || event.detail().contains("TD")));

        engine.apply(PlayerAction.call("a"));
        engine.apply(PlayerAction.check("b"));
        for (int street = 0; street < 3; street++) {
            engine.apply(PlayerAction.check("b"));
            engine.apply(PlayerAction.check("a"));
        }

        List<String> board = engine.state().getCommunityCards().stream().map(card -> card.code()).toList();
        assertEquals(List.of("3H", "4S", "5D", "6C", "7H"), board);
        assertTrue(board.stream().noneMatch(code -> code.equals("2C") || code.equals("TD")));
        assertEquals(GameStatus.NEXT_HAND, engine.state().getStatus());
        assertEquals(HandRank.STRAIGHT, showdownOf(engine, "a"));
        assertEquals(HandRank.STRAIGHT, showdownOf(engine, "b"));
    }

    @Test
    void rejectDiscardOutOfTurnOrWrongIndex() {
        TexasHoldemEngine engine = pineappleTwoPlayers();
        engine.startHand();
        String actor = EngineHands.currentId(engine);
        String other = actor.equals("a") ? "b" : "a";
        assertThrows(GameActionException.class, () -> engine.apply(PlayerAction.discard(other, 0)));
        assertThrows(GameActionException.class, () -> engine.apply(PlayerAction.discard(actor, 3)));
        assertThrows(GameActionException.class, () -> engine.apply(PlayerAction.fold(actor)));
    }

    private static TexasHoldemEngine pineappleTwoPlayers() {
        TexasHoldemEngine engine = new TexasHoldemEngine(
                TableConfig.developmentDefaults(),
                new PineappleVariant(),
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
}
