package com.poker.mapper;

import com.poker.game.engine.PlayerState;
import com.poker.game.engine.TableConfig;
import com.poker.game.engine.TexasHoldemEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableMapperTest {

    @Test
    void viewerSeesOwnHoleCardsAndOpponentsStayHidden() {
        TexasHoldemEngine engine = new TexasHoldemEngine(TableConfig.developmentDefaults());
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        engine.sitPlayer(alice.toString(), "Alice", 0);
        engine.sitPlayer(bob.toString(), "Bob", 1);
        engine.startHand();

        PlayerState aliceState = engine.state().player(alice.toString());
        PlayerState bobState = engine.state().player(bob.toString());

        List<String> own = TableMapper.holeCardsForViewer(aliceState, alice.toString(), false, Set.of());
        List<String> opponent = TableMapper.holeCardsForViewer(bobState, alice.toString(), false, Set.of());
        List<String> spectator = TableMapper.holeCardsForViewer(aliceState, UUID.randomUUID().toString(), false, Set.of());

        assertEquals(2, own.size());
        assertFalse(own.contains(TableMapper.HIDDEN));
        assertEquals(List.of(TableMapper.HIDDEN, TableMapper.HIDDEN), opponent);
        assertEquals(List.of(TableMapper.HIDDEN, TableMapper.HIDDEN), spectator);
    }

    @Test
    void showdownRevealsEligibleOpponentCards() {
        TexasHoldemEngine engine = new TexasHoldemEngine(TableConfig.developmentDefaults());
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        engine.sitPlayer(alice.toString(), "Alice", 0);
        engine.sitPlayer(bob.toString(), "Bob", 1);
        engine.startHand();
        PlayerState bobState = engine.state().player(bob.toString());
        String bobId = bob.toString();

        List<String> hidden = TableMapper.holeCardsForViewer(bobState, alice.toString(), false, Set.of());
        List<String> revealed = TableMapper.holeCardsForViewer(bobState, alice.toString(), true, Set.of(bobId));

        assertEquals(List.of(TableMapper.HIDDEN, TableMapper.HIDDEN), hidden);
        assertEquals(2, revealed.size());
        assertTrue(revealed.stream().noneMatch(TableMapper.HIDDEN::equals));
        assertEquals(bobState.getHoleCards().get(0).code(), revealed.get(0));
    }

    @Test
    void lobbyStatusIsWaitingBetweenHands() {
        assertEquals("WAITING", TableMapper.lobbyStatus(com.poker.game.engine.GameStatus.WAITING).name());
        assertEquals("WAITING", TableMapper.lobbyStatus(com.poker.game.engine.GameStatus.NEXT_HAND).name());
        assertEquals("PLAYING", TableMapper.lobbyStatus(com.poker.game.engine.GameStatus.PRE_FLOP).name());
    }
}
