package com.poker.game.engine;

import com.poker.game.variants.TexasHoldemVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleAuthorityAuditTest {

    private TexasHoldemEngine engine;
    private final TableConfig config = new TableConfig(10, 20, 1000, 6, 3);

    @BeforeEach
    void setUp() {
        engine = new TexasHoldemEngine("table-audit-1", config, new TexasHoldemVariant());
        engine.sitPlayer("p1", "Alice", 0, 1000);
        engine.sitPlayer("p2", "Bob", 1, 1000);
        engine.sitPlayer("p3", "Charlie", 2, 1000);
    }

    @Test
    void audit_unseatedPlayerCannotAct() {
        engine.startHand();
        GameActionException ex = assertThrows(GameActionException.class, () ->
                engine.apply(new PlayerAction("unseated-id", ActionType.FOLD, 0, List.of()))
        );
        assertEquals("PLAYER_NOT_FOUND", ex.getCode());
    }

    @Test
    void audit_outOfTurnActionIsRejected() {
        engine.startHand();
        // Dealer: Alice (0), SB: Bob (1), BB: Charlie (2), First to act: Alice (0)
        assertEquals(0, engine.state().getCurrentTurnSeat());

        // Bob (seat 1) attempts to act out of turn
        GameActionException ex = assertThrows(GameActionException.class, () ->
                engine.apply(new PlayerAction("p2", ActionType.CALL, 0, List.of()))
        );
        assertEquals("NOT_PLAYER_TURN", ex.getCode());
    }

    @Test
    void audit_cannotCheckWhenFacingBet() {
        engine.startHand();
        // Alice (0) is first to act facing BB of 20
        GameActionException ex = assertThrows(GameActionException.class, () ->
                engine.apply(new PlayerAction("p1", ActionType.CHECK, 0, List.of()))
        );
        assertEquals("INVALID_GAME_ACTION", ex.getCode());
    }

    @Test
    void audit_betBelowBigBlindIsRejected() {
        engine.startHand();
        // Alice calls 20, Bob calls 20 (adding 10 to SB 10), Charlie checks BB -> Flop
        engine.apply(new PlayerAction("p1", ActionType.CALL, 0, List.of()));
        engine.apply(new PlayerAction("p2", ActionType.CALL, 0, List.of()));
        engine.apply(new PlayerAction("p3", ActionType.CHECK, 0, List.of()));

        assertEquals(GameStatus.FLOP, engine.state().getStatus());
        int actorSeat = engine.state().getCurrentTurnSeat();
        PlayerState actor = engine.state().playerAt(actorSeat);

        // Attempt opening bet of 10 when BB is 20
        GameActionException ex = assertThrows(GameActionException.class, () ->
                engine.apply(new PlayerAction(actor.getPlayerId(), ActionType.BET, 10, List.of()))
        );
        assertEquals("INVALID_RAISE", ex.getCode());
    }

    @Test
    void audit_raiseBelowMinimumRaiseIsRejected() {
        engine.startHand();
        // Current bet is 20, min raise to is 40. Alice attempts raise to 30.
        GameActionException ex = assertThrows(GameActionException.class, () ->
                engine.apply(new PlayerAction("p1", ActionType.RAISE, 30, List.of()))
        );
        assertEquals("INVALID_RAISE", ex.getCode());
    }

    @Test
    void audit_betExceedingPlayerChipsIsRejected() {
        engine.startHand();
        // Alice has 1000 chips. Attempts to raise to 1500.
        GameActionException ex = assertThrows(GameActionException.class, () ->
                engine.apply(new PlayerAction("p1", ActionType.RAISE, 1500, List.of()))
        );
        assertEquals("INSUFFICIENT_CHIPS", ex.getCode());
    }

    @Test
    void audit_foldedPlayerCannotAct() {
        engine.startHand();
        // Alice folds
        engine.apply(new PlayerAction("p1", ActionType.FOLD, 0, List.of()));
        assertTrue(engine.state().player("p1").isFolded());

        // Alice attempts to act again
        GameActionException ex = assertThrows(GameActionException.class, () ->
                engine.apply(new PlayerAction("p1", ActionType.CALL, 0, List.of()))
        );
        assertEquals("NOT_PLAYER_TURN", ex.getCode());
    }

    @Test
    void audit_cannotActWhenHandIsInactive() {
        // Hand has not started (WAITING)
        assertFalse(engine.state().isHandInProgress());
        GameActionException ex = assertThrows(GameActionException.class, () ->
                engine.apply(new PlayerAction("p1", ActionType.FOLD, 0, List.of()))
        );
        assertEquals("INVALID_GAME_ACTION", ex.getCode());
    }

    @Test
    void audit_allInIsAllowedEvenIfShort() {
        TexasHoldemEngine shortEngine = new TexasHoldemEngine("table-short", config, new TexasHoldemVariant());
        shortEngine.sitPlayer("s1", "Shorty", 0, 15);
        shortEngine.sitPlayer("s2", "Deep", 1, 1000);

        shortEngine.startHand();
        // Heads up: SB is seat 0 (Shorty, posted 10, has 5 left), BB is seat 1 (Deep, posted 20)
        // Shorty goes ALL_IN with remaining 5 chips (total bet 15, which is < 20)
        shortEngine.apply(new PlayerAction("s1", ActionType.ALL_IN, 0, List.of()));
        assertTrue(shortEngine.state().player("s1").isAllIn());
        assertEquals(0, shortEngine.state().player("s1").getChipStack());
        assertEquals(15, shortEngine.state().player("s1").getBetThisRound());
    }
}
