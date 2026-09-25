package com.poker.game.engine;

import com.poker.exception.ErrorCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexasHoldemEngineSeatingTest {

    @Test
    void playersSitInChosenSeatsWithConfiguredStacks() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        assertEquals(3, engine.state().getPlayers().size());
        assertEquals(1000, EngineHands.chips(engine, "a"));
        assertEquals(0, engine.state().player("a").getSeat());
        assertEquals(GameStatus.WAITING, engine.state().getStatus());
    }

    @Test
    void autoSeatFillsTheFirstEmptySeat() {
        TexasHoldemEngine engine = new TexasHoldemEngine();
        engine.sitPlayer("a", "Alice");
        engine.sitPlayer("b", "Bob");
        assertEquals(0, engine.state().player("a").getSeat());
        assertEquals(1, engine.state().player("b").getSeat());
    }

    @Test
    void takenSeatAndDuplicatePlayerAreRejected() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        GameActionException seat = assertThrows(
                GameActionException.class,
                () -> engine.sitPlayer("d", "Dana", 1)
        );
        assertEquals(ErrorCodes.SEAT_UNAVAILABLE, seat.getCode());
        GameActionException duplicate = assertThrows(
                GameActionException.class,
                () -> engine.sitPlayer("a", "Alice-again", 3)
        );
        assertEquals(ErrorCodes.INVALID_GAME_ACTION, duplicate.getCode());
    }

    @Test
    void cannotSitAfterTheTableIsFull() {
        TexasHoldemEngine engine = new TexasHoldemEngine(new TableConfig(10, 20, 1000, 2));
        engine.sitPlayer("a", "Alice");
        engine.sitPlayer("b", "Bob");
        GameActionException ex = assertThrows(GameActionException.class, () -> engine.sitPlayer("c", "Carol"));
        assertEquals(ErrorCodes.SEAT_UNAVAILABLE, ex.getCode());
    }

    @Test
    void leaveRemovesThePlayerBetweenHands() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.leaveSeat("b");
        assertNull(engine.state().player("b"));
        assertEquals(2, engine.state().getPlayers().size());
    }

    @Test
    void cannotLeaveOrSitDuringAHand() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        assertTrue(engine.state().isBettingActive());
        GameActionException leave = assertThrows(GameActionException.class, () -> engine.leaveSeat("a"));
        assertEquals(ErrorCodes.GAME_ALREADY_STARTED, leave.getCode());
        GameActionException sit = assertThrows(GameActionException.class, () -> engine.sitPlayer("d", "Dana", 3));
        assertEquals(ErrorCodes.GAME_ALREADY_STARTED, sit.getCode());
    }

    @Test
    void abandoningDuringAHandFoldsPlayerAndAdvancesTheirTurn() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        int leavingSeat = engine.state().getCurrentTurnSeat();
        PlayerState leaving = engine.state().playerAt(leavingSeat);

        engine.abandonSeat(leaving.getPlayerId());

        assertTrue(leaving.isFolded());
        assertFalse(leaving.isConnected());
        assertTrue(engine.state().getCurrentTurnSeat() != leavingSeat);
        assertTrue(
                engine.state().getEvents().stream()
                        .anyMatch(event -> "PLAYER_LEFT".equals(event.type()))
        );
    }

    @Test
    void disconnectDoesNotFoldAndReconnectAddsAnEvent() {
        TexasHoldemEngine engine = EngineHands.threePlayers();
        engine.startHand();
        assertTrue(engine.state().player("a").isConnected());
        engine.setConnected("a", false);
        assertTrue(engine.state().isBettingActive());
        assertFalse(engine.state().player("a").isFolded());
        assertFalse(engine.state().player("a").isConnected());
        engine.setConnected("a", true);
        assertTrue(engine.state().player("a").isConnected());
        assertTrue(
                engine.state().getEvents().stream()
                        .anyMatch(event -> "PLAYER_RECONNECTED".equals(event.type()))
        );
    }

    @Test
    void startingRequiresTwoPlayersWithChips() {
        TexasHoldemEngine engine = new TexasHoldemEngine();
        engine.sitPlayer("a", "Alice", 0);
        GameActionException ex = assertThrows(GameActionException.class, engine::startHand);
        assertEquals(ErrorCodes.INVALID_GAME_ACTION, ex.getCode());
        assertTrue(ex.getMessage().contains("two players"));
    }
}
