package com.poker.game.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexasHoldemEngineSidePotTest {

    @Test
    void allInPlayerCanOnlyWinTheMainPot() {
        TexasHoldemEngine engine = EngineHands.seated(
                new EngineHands.Seat("a", "Alice", 0, 40),
                new EngineHands.Seat("b", "Bob", 1, 200),
                new EngineHands.Seat("c", "Carol", 2, 200)
        );
        engine.startHand(EngineHands.firstHandDeck(
                List.of(0, 1, 2),
                Map.of(
                        0, List.of("AC", "AH"),
                        1, List.of("KC", "KD"),
                        2, List.of("2C", "7D")
                ),
                List.of("3C", "8D", "9H", "TS", "2S")
        ));

        engine.apply(PlayerAction.allIn("a"));
        engine.apply(PlayerAction.allIn("b"));
        engine.apply(PlayerAction.call("c"));

        assertEquals(GameStatus.NEXT_HAND, engine.state().getStatus());
        assertEquals(2, engine.state().getSidePots().size());
        assertEquals(120, engine.state().getSidePots().get(0).amount());
        assertEquals(List.of("a", "b", "c"), engine.state().getSidePots().get(0).eligiblePlayerIds());
        assertEquals(320, engine.state().getSidePots().get(1).amount());
        assertEquals(List.of("b", "c"), engine.state().getSidePots().get(1).eligiblePlayerIds());

        int alice = EngineHands.chips(engine, "a");
        int bob = EngineHands.chips(engine, "b");
        int carol = EngineHands.chips(engine, "c");
        assertEquals(120, alice);
        assertTrue(bob > carol);
        assertEquals(440, alice + bob + carol);
    }

    @Test
    void chipsAreConservedThroughEveryBettingAction() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        int total = EngineHands.totalChips(engine);
        engine.startHand();
        assertEquals(total, EngineHands.totalChips(engine));
        engine.apply(PlayerAction.raise("a", 80));
        assertEquals(total, EngineHands.totalChips(engine));
        engine.apply(PlayerAction.call("b"));
        engine.apply(PlayerAction.fold("c"));
        assertEquals(total, EngineHands.totalChips(engine));
        engine.apply(PlayerAction.bet("b", 40));
        engine.apply(PlayerAction.raise("a", 100));
        engine.apply(PlayerAction.fold("b"));
        assertEquals(total, EngineHands.totalChips(engine));
        assertEquals(0, engine.state().getPot());
    }
}
