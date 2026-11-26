package com.poker.tournament.entity;

import com.poker.game.engine.ActionType;
import com.poker.game.engine.GameStatus;
import com.poker.websocket.WsEventType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Server-only journal for the current hand. Never serialize this record to a client. */
public record TournamentHand(int format, long revision, UUID id, int number, int previousDealer, int dealer,
                             List<Stack> openingStacks, List<String> deck, List<Move> moves,
                             Phase phase, GameStatus street, Instant turnDeadline, Instant nextHandAt,
                             Instant activatedAt, Instant settledAt, String reason, WsEventType event, BlindLevel blind) {
    public enum Phase { ACTIVE, BETWEEN_HANDS, PAUSED }

    public record Stack(UUID userId, int chips) { }

    public record Move(UUID userId, String requestId, ActionType action, int amount) { }

    public BlindLevel appliedBlind(BlindLevel initial) { return format == 1 ? initial : blind; }
}
