package com.poker.game.engine;

import com.poker.game.evaluator.HandRank;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmahaEngineTest {

    @Test
    void dealsFourUniqueHoleCards() {
        TexasHoldemEngine engine = EngineHands.omahaTwoPlayers();
        engine.startHand();
        Set<String> codes = new HashSet<>();
        for (PlayerState player : engine.state().getPlayers()) {
            assertEquals(4, player.getHoleCards().size());
            player.getHoleCards().forEach(card -> assertTrue(codes.add(card.code())));
        }
        assertEquals(8, codes.size());
        assertEquals("OMAHA", engine.state().getVariant());
        assertTrue(engine.state().getCommunityCards().isEmpty());
    }

    @Test
    void showdownUsesExactlyTwoHoleAndThreeBoardCards() {
        TexasHoldemEngine engine = EngineHands.omahaTwoPlayers();
        engine.startHand(EngineHands.firstHandDeck(
                List.of(0, 1),
                Map.of(
                        0, List.of("AS", "AH", "KD", "KH"),
                        1, List.of("QC", "JC", "TC", "9C")
                ),
                List.of("AD", "2S", "3H", "4C", "5D")
        ));
        engine.apply(PlayerAction.call("a"));
        engine.apply(PlayerAction.check("b"));
        for (int street = 0; street < 3; street++) {
            engine.apply(PlayerAction.check("b"));
            engine.apply(PlayerAction.check("a"));
        }

        assertEquals(GameStatus.NEXT_HAND, engine.state().getStatus());
        assertEquals(HandRank.THREE_OF_A_KIND, showdownOf(engine, "a"));
        assertEquals("a", engine.state().getPayouts().getFirst().playerId());
        assertEquals(4, engine.state().player("a").getHoleCards().size());
        assertEquals(4, engine.state().player("b").getHoleCards().size());
    }

    private static HandRank showdownOf(TexasHoldemEngine engine, String playerId) {
        return engine.state().getShowdown().stream()
                .filter(result -> result.playerId().equals(playerId))
                .findFirst()
                .orElseThrow()
                .evaluation()
                .getRank();
    }
}
