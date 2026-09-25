package com.poker.game.engine;

import com.poker.exception.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexasHoldemEngineBettingTest {

    @Test
    void outOfTurnActionIsRejected() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        GameActionException ex = assertThrows(GameActionException.class, () -> engine.apply(PlayerAction.fold("c")));
        assertEquals(ErrorCodes.NOT_PLAYER_TURN, ex.getCode());
    }

    @Test
    void cannotCheckWhenFacingABet() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        GameActionException ex = assertThrows(GameActionException.class, () -> engine.apply(PlayerAction.check("a")));
        assertEquals(ErrorCodes.INVALID_GAME_ACTION, ex.getCode());
    }

    @Test
    void callMatchesTheBigBlind() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        engine.apply(PlayerAction.call("a"));
        assertEquals(20, engine.state().player("a").getBetThisRound());
        assertEquals(980, EngineHands.chips(engine, "a"));
        assertEquals(50, engine.state().getPot());
        assertEquals("b", EngineHands.currentId(engine));
    }

    @Test
    void raiseMustMeetTheMinimumAndReopensAction() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        LegalActions legal = engine.legalActions("a");
        assertTrue(legal.allows(ActionType.RAISE));
        assertEquals(40, legal.minBetOrRaiseTo());
        engine.apply(PlayerAction.raise("a", 60));
        assertEquals(60, engine.state().getCurrentBet());
        assertEquals(40, engine.state().getMinimumRaise());
        assertEquals("b", EngineHands.currentId(engine));
        engine.apply(PlayerAction.fold("b"));
        engine.apply(PlayerAction.call("c"));
        assertEquals(GameStatus.FLOP, engine.state().getStatus());
        assertEquals(130, engine.state().getPot());
    }

    @Test
    void raiseBelowMinimumIsRejected() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        GameActionException ex = assertThrows(
                GameActionException.class,
                () -> engine.apply(PlayerAction.raise("a", 30))
        );
        assertEquals(ErrorCodes.INVALID_RAISE, ex.getCode());
    }

    @Test
    void betIsAllowedOnTheFlopWhenNoBetExists() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        engine.apply(PlayerAction.call("a"));
        engine.apply(PlayerAction.call("b"));
        engine.apply(PlayerAction.check("c"));
        assertEquals("b", EngineHands.currentId(engine));
        LegalActions legal = engine.legalActions("b");
        assertEquals(Set.of(ActionType.FOLD, ActionType.CHECK, ActionType.BET, ActionType.ALL_IN), legal.actions());
        engine.apply(PlayerAction.bet("b", 40));
        assertEquals(40, engine.state().getCurrentBet());
        assertFalse(engine.legalActions("c").allows(ActionType.CHECK));
        assertTrue(engine.legalActions("c").allows(ActionType.CALL));
        engine.apply(PlayerAction.fold("c"));
        engine.apply(PlayerAction.call("a"));
        assertEquals(GameStatus.TURN, engine.state().getStatus());
    }

    @Test
    void allInPutsTheRemainingStack() {
        TexasHoldemEngine engine = EngineHands.seated(
                new EngineHands.Seat("a", "Alice", 0, 1000),
                new EngineHands.Seat("b", "Bob", 1, 1000),
                new EngineHands.Seat("c", "Carol", 2, 1000)
        );
        engine.startHand();
        engine.apply(PlayerAction.allIn("a"));
        assertTrue(engine.state().player("a").isAllIn());
        assertEquals(0, EngineHands.chips(engine, "a"));
        assertEquals(1000, engine.state().getCurrentBet());
        assertEquals(1030, engine.state().getPot());
    }

    @Test
    void incompleteAllInRaiseDoesNotAllowAReRaiseFromAPlayerWhoAlreadyActed() {
        TexasHoldemEngine engine = EngineHands.seated(
                new EngineHands.Seat("a", "Alice", 0, 1000),
                new EngineHands.Seat("b", "Bob", 1, 50),
                new EngineHands.Seat("c", "Carol", 2, 1000)
        );
        engine.startHand();
        engine.apply(PlayerAction.raise("a", 40));
        engine.apply(PlayerAction.allIn("b"));
        assertEquals(50, engine.state().getCurrentBet());
        assertEquals("c", EngineHands.currentId(engine));
        LegalActions carol = engine.legalActions("c");
        assertTrue(carol.allows(ActionType.CALL));
        assertTrue(carol.allows(ActionType.RAISE));
        engine.apply(PlayerAction.call("c"));
        assertEquals("a", EngineHands.currentId(engine));
        LegalActions alice = engine.legalActions("a");
        assertTrue(alice.allows(ActionType.CALL));
        assertFalse(alice.allows(ActionType.RAISE));
        assertFalse(alice.allows(ActionType.ALL_IN));
    }
}
