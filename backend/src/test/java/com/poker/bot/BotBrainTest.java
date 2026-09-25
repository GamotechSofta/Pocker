package com.poker.bot;

import com.poker.game.engine.ActionType;
import com.poker.game.engine.GameStatus;
import com.poker.game.engine.LegalActions;
import com.poker.game.engine.PlayerState;
import com.poker.game.engine.PlayerStateFixtures;
import com.poker.game.model.Card;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotBrainTest {

    private final BotBrain brain = new BotBrain();

    @Test
    void preflopPrefersPremiumPairs() {
        double aa = brain.preflopStrength(List.of(Card.parse("AS"), Card.parse("AH")));
        double sevenDeuce = brain.preflopStrength(List.of(Card.parse("7C"), Card.parse("2D")));
        assertTrue(aa > 0.7);
        assertTrue(aa > sevenDeuce + 0.25);
    }

    @Test
    void foldsTrashFacingLargeBet() {
        PlayerState self = PlayerStateFixtures.withHoles("bot-1", "bot_ace", 0, 1000, "7C", "2D");
        LegalActions legal = new LegalActions(
                EnumSet.of(ActionType.FOLD, ActionType.CALL, ActionType.RAISE),
                200,
                400,
                980
        );
        var action = brain.choose(self, legal, GameStatus.PRE_FLOP, List.of(), 40);
        assertEquals("FOLD", action.action());
    }

    @Test
    void betsOrChecksWithStrongMadeHand() {
        PlayerState self = PlayerStateFixtures.withHoles("bot-1", "bot_ace", 0, 1000, "AS", "AH");
        LegalActions legal = new LegalActions(
                EnumSet.of(ActionType.CHECK, ActionType.BET, ActionType.ALL_IN),
                0,
                20,
                980
        );
        var action = brain.choose(
                self,
                legal,
                GameStatus.FLOP,
                List.of(Card.parse("AD"), Card.parse("KH"), Card.parse("2C")),
                60
        );
        assertNotEquals("FOLD", action.action());
    }

    @Test
    void pineappleDiscardsWeakestSingleton() {
        PlayerState self = PlayerStateFixtures.withHoles("bot-1", "bot_ace", 0, 1000, "AS", "KH", "2C");
        LegalActions legal = new LegalActions(EnumSet.of(ActionType.DISCARD), 0, 0, 2);
        var action = brain.choose(self, legal, GameStatus.DISCARD, List.of(), 0);
        assertEquals("DISCARD", action.action());
        assertEquals(2, action.amount());
    }

    @Test
    void drawKeepsPairs() {
        List<Integer> discard = BotBrain.drawDiscardIndices(
                List.of(Card.parse("AS"), Card.parse("AH"), Card.parse("7C"), Card.parse("3D"), Card.parse("2H")),
                3
        );
        assertTrue(discard.stream().noneMatch(i -> i == 0 || i == 1));
        assertTrue(discard.size() <= 3);
    }
}
