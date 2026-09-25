package com.poker.game.engine;

import com.poker.game.evaluator.HandRank;
import com.poker.game.variants.FiveCardDrawVariant;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiveCardDrawEngineTest {

    @Test
    void dealsFiveHoleCardsThenFirstBettingWithoutCommunity() {
        TexasHoldemEngine engine = drawTwoPlayers();
        engine.startHand();
        assertEquals(GameStatus.PRE_FLOP, engine.state().getStatus());
        assertEquals("FIVE_CARD_DRAW", engine.state().getVariant());
        Set<String> codes = new HashSet<>();
        for (PlayerState player : engine.state().getPlayers()) {
            assertEquals(5, player.getHoleCards().size());
            player.getHoleCards().forEach(card -> assertTrue(codes.add(card.code())));
        }
        assertEquals(10, codes.size());
        assertTrue(engine.state().getCommunityCards().isEmpty());
    }

    @Test
    void drawThenSecondBettingThenShowdown() {
        TexasHoldemEngine engine = drawTwoPlayers();
        engine.startHand(EngineHands.fiveCardDrawDeck(
                List.of(0, 1),
                Map.of(
                        0, List.of("AS", "AH", "2C", "3D", "4H"),
                        1, List.of("KD", "KH", "KC", "9S", "8D")
                ),
                List.of("AD", "AC")
        ));
        assertEquals(GameStatus.PRE_FLOP, engine.state().getStatus());
        // HU: dealer/SB = a acts first
        engine.apply(PlayerAction.call("a"));
        engine.apply(PlayerAction.check("b"));
        assertEquals(GameStatus.DRAW, engine.state().getStatus());

        // first to draw is left of dealer = b
        assertEquals("b", EngineHands.currentId(engine));
        engine.apply(PlayerAction.draw("b", List.of())); // stand pat
        assertEquals("a", EngineHands.currentId(engine));
        engine.apply(PlayerAction.draw("a", List.of(2, 3))); // discard 2C, 3D → AD, AC

        assertEquals(GameStatus.SECOND_BET, engine.state().getStatus());
        assertEquals(5, engine.state().player("a").getHoleCards().size());
        assertEquals("AS", engine.state().player("a").getHoleCards().get(0).code());
        assertEquals("AH", engine.state().player("a").getHoleCards().get(1).code());
        assertEquals("4H", engine.state().player("a").getHoleCards().get(2).code());
        assertEquals("AD", engine.state().player("a").getHoleCards().get(3).code());
        assertEquals("AC", engine.state().player("a").getHoleCards().get(4).code());

        engine.apply(PlayerAction.check("b"));
        engine.apply(PlayerAction.check("a"));

        assertEquals(GameStatus.NEXT_HAND, engine.state().getStatus());
        assertTrue(engine.state().getCommunityCards().isEmpty());
        assertEquals(HandRank.FOUR_OF_A_KIND, showdownOf(engine, "a"));
        assertEquals(HandRank.THREE_OF_A_KIND, showdownOf(engine, "b"));
    }

    @Test
    void rejectsTooManyDiscardsOrOutOfTurnDraw() {
        TexasHoldemEngine engine = new TexasHoldemEngine(
                new TableConfig(10, 20, 1000, 6, 2),
                new FiveCardDrawVariant(),
                new SecureRandom()
        );
        engine.sitPlayer("a", "Alice", 0, 1000);
        engine.sitPlayer("b", "Bob", 1, 1000);
        engine.startHand();
        engine.apply(PlayerAction.call(EngineHands.currentId(engine)));
        String next = EngineHands.currentId(engine);
        engine.apply(PlayerAction.check(next));
        assertEquals(GameStatus.DRAW, engine.state().getStatus());

        String actor = EngineHands.currentId(engine);
        String other = actor.equals("a") ? "b" : "a";
        assertThrows(GameActionException.class, () -> engine.apply(PlayerAction.draw(other, List.of())));
        assertThrows(GameActionException.class, () ->
                engine.apply(PlayerAction.draw(actor, List.of(0, 1, 2))));
        assertThrows(GameActionException.class, () -> engine.apply(PlayerAction.fold(actor)));
    }

    private static TexasHoldemEngine drawTwoPlayers() {
        TexasHoldemEngine engine = new TexasHoldemEngine(
                TableConfig.developmentDefaults(),
                new FiveCardDrawVariant(),
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
