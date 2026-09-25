package com.poker.game.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexasHoldemEngineFoldWinTest {

    @Test
    void lastRemainingPlayerWinsThePotWithoutShowdown() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        int total = EngineHands.totalChips(engine);
        engine.startHand();
        engine.apply(PlayerAction.fold("a"));
        engine.apply(PlayerAction.fold("b"));

        assertEquals(GameStatus.NEXT_HAND, engine.state().getStatus());
        assertEquals("c", engine.state().getFoldWinnerId());
        assertTrue(engine.state().getShowdown().isEmpty());
        assertEquals(0, engine.state().getPot());
        assertEquals(1000, EngineHands.chips(engine, "a"));
        assertEquals(990, EngineHands.chips(engine, "b"));
        assertEquals(1010, EngineHands.chips(engine, "c"));
        assertEquals(total, EngineHands.totalChips(engine));
        assertEquals(1, engine.state().getPayouts().size());
        assertEquals(30, engine.state().getPayouts().getFirst().amount());
    }

    @Test
    void foldAfterARaiseAwardsTheRaisedPot() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        engine.apply(PlayerAction.raise("a", 60));
        engine.apply(PlayerAction.fold("b"));
        engine.apply(PlayerAction.fold("c"));
        assertEquals("a", engine.state().getFoldWinnerId());
        assertEquals(1030, EngineHands.chips(engine, "a"));
        assertEquals(990, EngineHands.chips(engine, "b"));
        assertEquals(980, EngineHands.chips(engine, "c"));
        assertEquals(3000, EngineHands.totalChips(engine));
    }
}
