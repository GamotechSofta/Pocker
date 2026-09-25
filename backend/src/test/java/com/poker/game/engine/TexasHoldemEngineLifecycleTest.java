package com.poker.game.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexasHoldemEngineLifecycleTest {

    @Test
    void nextHandKeepsStacksAndIncrementsTheHandNumber() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        assertEquals(1, engine.state().getHandNumber());
        engine.apply(PlayerAction.fold("a"));
        engine.apply(PlayerAction.fold("b"));
        assertEquals(GameStatus.NEXT_HAND, engine.state().getStatus());
        assertEquals(1010, EngineHands.chips(engine, "c"));

        engine.startHand();
        assertEquals(2, engine.state().getHandNumber());
        assertEquals(1, engine.state().getDealerSeat());
        assertEquals(2, engine.state().player("a").getHoleCards().size());
        assertEquals(BettingRound.PRE_FLOP, engine.state().getBettingRound());
        assertTrue(engine.state().getCommunityCards().isEmpty());
    }

    @Test
    void cannotStartASecondHandWhileBettingIsActive() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        GameActionException ex = assertThrows(GameActionException.class, engine::startHand);
        assertEquals("GAME_ALREADY_STARTED", ex.getCode());
    }

    @Test
    void tableSettingsComeFromConfiguration() {
        TableConfig config = new TableConfig(5, 10, 250, 4);
        TexasHoldemEngine engine = new TexasHoldemEngine(config);
        engine.sitPlayer("a", "Alice", 0);
        engine.sitPlayer("b", "Bob", 1);
        engine.startHand();
        assertEquals(5, engine.state().getTableConfig().smallBlind());
        assertEquals(245, EngineHands.chips(engine, "a"));
        assertEquals(240, EngineHands.chips(engine, "b"));
        assertEquals(15, engine.state().getPot());
    }
}
