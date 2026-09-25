package com.poker.game.engine;

import com.poker.game.model.Card;

/** Test helper — package access to seat hole cards. */
public final class PlayerStateFixtures {

    private PlayerStateFixtures() {
    }

    public static PlayerState withHoles(String playerId, String username, int seat, int chips, String... codes) {
        PlayerState player = new PlayerState(playerId, username, seat, chips);
        for (String code : codes) {
            player.addHoleCard(Card.parse(code));
        }
        return player;
    }
}
