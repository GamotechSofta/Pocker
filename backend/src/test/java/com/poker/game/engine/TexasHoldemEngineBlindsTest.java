package com.poker.game.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexasHoldemEngineBlindsTest {

    @Test
    void threeHandedPostsSmallAndBigBlindsFromTheDealer() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        GameState state = engine.state();
        assertEquals(1, state.getSmallBlindSeat());
        assertEquals(2, state.getBigBlindSeat());
        assertEquals(990, EngineHands.chips(engine, "b"));
        assertEquals(980, EngineHands.chips(engine, "c"));
        assertEquals(1000, EngineHands.chips(engine, "a"));
        assertEquals(30, state.getPot());
        assertEquals(20, state.getCurrentBet());
        assertEquals(20, state.getMinimumRaise());
        assertEquals(BettingRound.PRE_FLOP, state.getBettingRound());
        assertEquals(GameStatus.PRE_FLOP, state.getStatus());
    }

    @Test
    void headsUpDealerIsTheSmallBlind() {
        TexasHoldemEngine engine = EngineHands.seated(
                new EngineHands.Seat("a", "Alice", 0, 1000),
                new EngineHands.Seat("b", "Bob", 1, 1000)
        );
        engine.startHand();
        assertEquals(0, engine.state().getDealerSeat());
        assertEquals(0, engine.state().getSmallBlindSeat());
        assertEquals(1, engine.state().getBigBlindSeat());
        assertEquals(990, EngineHands.chips(engine, "a"));
        assertEquals(980, EngineHands.chips(engine, "b"));
        assertEquals("a", EngineHands.currentId(engine));
    }

    @Test
    void shortStackPostsAllInBlind() {
        TexasHoldemEngine engine = EngineHands.seated(
                new EngineHands.Seat("a", "Alice", 0, 1000),
                new EngineHands.Seat("b", "Bob", 1, 1000),
                new EngineHands.Seat("c", "Carol", 2, 15)
        );
        engine.startHand();
        assertTrue(engine.state().player("c").isAllIn());
        assertEquals(0, EngineHands.chips(engine, "c"));
        assertEquals(15, engine.state().player("c").getBetThisRound());
        assertEquals(25, engine.state().getPot());
    }

    @Test
    void dealerButtonMovesClockwiseOnTheNextHand() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        int firstDealer = engine.state().getDealerSeat();
        engine.apply(PlayerAction.fold("a"));
        engine.apply(PlayerAction.fold("b"));
        assertEquals(GameStatus.NEXT_HAND, engine.state().getStatus());
        engine.startHand();
        assertNotEquals(firstDealer, engine.state().getDealerSeat());
        assertEquals(1, engine.state().getDealerSeat());
        assertEquals(2, engine.state().getSmallBlindSeat());
        assertEquals(0, engine.state().getBigBlindSeat());
    }
}
