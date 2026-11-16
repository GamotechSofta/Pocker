package com.poker.game.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnManagerTest {

    private final TurnManager turns = new TurnManager(6);

    @Test
    void nextSeatSkipsEmptySeatsClockwise() {
        GameState state = table();
        assertEquals(2, turns.nextOccupiedSeat(state, 0));
        assertEquals(5, turns.nextOccupiedSeat(state, 2));
        assertEquals(0, turns.nextOccupiedSeat(state, 5));
    }

    @Test
    void nextCanActSeatSkipsFoldedAndAllInPlayers() {
        GameState state = table();
        state.playerAt(2).fold();
        state.playerAt(5).collect(1000);
        assertTrue(state.playerAt(5).isAllIn());
        assertEquals(0, turns.nextCanActSeat(state, 5));
        assertEquals(0, turns.nextCanActSeat(state, 2));
    }

    @Test
    void fullBotTableTurnsGoClockwiseLikeEveryOtherTable() {
        GameState state = new GameState(TableConfig.developmentDefaults());
        state.putPlayer(new PlayerState("guest", "Guest", 0, 1000));
        state.putPlayer(new PlayerState("aarav", "bot_aarav", 1, 1000));
        state.putPlayer(new PlayerState("vihaan", "bot_vihaan", 2, 1000));
        state.putPlayer(new PlayerState("arjun", "bot_arjun", 3, 1000));
        state.putPlayer(new PlayerState("kabir", "bot_kabir", 4, 1000));
        state.putPlayer(new PlayerState("ishaan", "bot_ishaan", 5, 1000));

        for (int seat = 0; seat < 6; seat++) {
            assertEquals((seat + 1) % 6, turns.nextOccupiedSeat(state, seat));
        }
    }

    @Test
    void headsUpPreflopStartsWithTheSmallBlind() {
        TexasHoldemEngine engine = EngineHands.seated(
                new EngineHands.Seat("a", "Alice", 0, 1000),
                new EngineHands.Seat("b", "Bob", 1, 1000)
        );
        engine.startHand();
        assertEquals(0, engine.state().getDealerSeat());
        assertEquals(0, engine.state().getSmallBlindSeat());
        assertEquals(1, engine.state().getBigBlindSeat());
        assertEquals(0, engine.state().getCurrentTurnSeat());
    }

    @Test
    void threeHandedPreflopStartsLeftOfTheBigBlind() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        assertEquals(0, engine.state().getDealerSeat());
        assertEquals(1, engine.state().getSmallBlindSeat());
        assertEquals(2, engine.state().getBigBlindSeat());
        assertEquals("a", EngineHands.currentId(engine));
    }

    @Test
    void bettingRoundIsNotCompleteUntilEveryLivePlayerHasActedAndMatched() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        GameState state = engine.state();
        assertFalse(turns.bettingRoundComplete(state));
        engine.apply(PlayerAction.call("a"));
        engine.apply(PlayerAction.call("b"));
        assertFalse(turns.bettingRoundComplete(state));
        assertEquals("c", EngineHands.currentId(engine));
    }

    private static GameState table() {
        GameState state = new GameState(TableConfig.developmentDefaults());
        state.putPlayer(new PlayerState("a", "Alice", 0, 1000));
        state.putPlayer(new PlayerState("b", "Bob", 2, 1000));
        state.putPlayer(new PlayerState("c", "Carol", 5, 1000));
        return state;
    }
}
