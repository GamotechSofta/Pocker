package com.poker.game.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class TurnManager {

    private static final List<Integer> SIX_SEAT_BOT_RING = List.of(2, 1, 0, 3, 5, 4);

    private final int maxPlayers;
    private final List<Integer> seatRing;

    public TurnManager(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        this.seatRing = java.util.stream.IntStream.range(0, maxPlayers).boxed().toList();
    }

    public int nextSeat(GameState state, int fromSeat, Predicate<PlayerState> predicate) {
        List<Integer> activeRing = activeSeatRing(state);
        int fromIndex = activeRing.indexOf(fromSeat);
        if (fromIndex < 0) {
            fromIndex = activeRing.size() - 1;
        }
        for (int i = 0; i < maxPlayers; i++) {
            int seat = activeRing.get((fromIndex + i + 1) % activeRing.size());
            PlayerState player = state.playerAt(seat);
            if (player != null && predicate.test(player)) {
                return seat;
            }
        }
        return -1;
    }

    private List<Integer> activeSeatRing(GameState state) {
        boolean fullBotTable = maxPlayers == 6
                && state.getPlayers().size() == 6
                && state.getPlayers().stream()
                        .anyMatch(player -> player.getUsername().toLowerCase().startsWith("bot_"));
        return fullBotTable ? SIX_SEAT_BOT_RING : seatRing;
    }

    public int nextOccupiedSeat(GameState state, int fromSeat) {
        return nextSeat(state, fromSeat, player -> true);
    }

    public int nextPlayerWithChips(GameState state, int fromSeat) {
        return nextSeat(state, fromSeat, player -> player.getChipStack() > 0 || player.isInHand());
    }

    public int nextCanActSeat(GameState state, int fromSeat) {
        return nextSeat(state, fromSeat, PlayerState::canAct);
    }

    public int nextInHandSeat(GameState state, int fromSeat) {
        return nextSeat(state, fromSeat, player -> !player.isFolded() && player.isInHand());
    }

    public List<PlayerState> clockwiseFromLeftOfDealer(GameState state) {
        List<PlayerState> ordered = new ArrayList<>();
        int seat = state.getDealerSeat();
        for (int i = 0; i < maxPlayers; i++) {
            seat = (seat + 1) % maxPlayers;
            PlayerState player = state.playerAt(seat);
            if (player != null) {
                ordered.add(player);
            }
        }
        return ordered;
    }

    public List<String> clockwiseIdsFromLeftOfDealer(GameState state) {
        return clockwiseFromLeftOfDealer(state).stream().map(PlayerState::getPlayerId).toList();
    }

    public int firstToActPreFlop(GameState state) {
        int inHand = (int) state.getPlayers().stream().filter(PlayerState::isInHand).count();
        if (inHand == 2) {
            PlayerState small = state.playerAt(state.getSmallBlindSeat());
            if (small != null && small.canAct()) {
                return small.getSeat();
            }
            return nextCanActSeat(state, state.getSmallBlindSeat());
        }
        return nextCanActSeat(state, state.getBigBlindSeat());
    }

    public int firstToActPostFlop(GameState state) {
        int first = nextCanActSeat(state, state.getDealerSeat());
        if (first >= 0) {
            return first;
        }
        return -1;
    }

    public boolean bettingRoundComplete(GameState state) {
        List<PlayerState> inHand = state.getPlayers().stream()
                .filter(player -> !player.isFolded() && player.isInHand())
                .toList();
        if (inHand.size() <= 1) {
            return true;
        }
        List<PlayerState> toAct = inHand.stream().filter(PlayerState::canAct).toList();
        if (toAct.isEmpty()) {
            return true;
        }
        for (PlayerState player : toAct) {
            if (!player.hasActedThisRound()) {
                return false;
            }
            if (player.getBetThisRound() != state.getCurrentBet()) {
                return false;
            }
        }
        return true;
    }

    public long playersStillContending(GameState state) {
        return state.getPlayers().stream()
                .filter(player -> !player.isFolded() && player.isInHand())
                .count();
    }
}
