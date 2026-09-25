package com.poker.game.engine;

import com.poker.game.model.Card;
import com.poker.game.model.Deck;
import com.poker.game.model.Rank;
import com.poker.game.model.Suit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EngineHands {

    private EngineHands() {
    }

    static TexasHoldemEngine threePlayers() {
        return seated(
                new Seat("a", "Alice", 0, 1000),
                new Seat("b", "Bob", 1, 1000),
                new Seat("c", "Carol", 2, 1000)
        );
    }

    static TexasHoldemEngine seated(Seat... seats) {
        TexasHoldemEngine engine = new TexasHoldemEngine(TableConfig.developmentDefaults());
        for (Seat seat : seats) {
            engine.sitPlayer(seat.playerId, seat.username, seat.seat, seat.chips);
        }
        return engine;
    }

    static TexasHoldemEngine omahaTwoPlayers() {
        TexasHoldemEngine engine = new TexasHoldemEngine(
                TableConfig.developmentDefaults(),
                new com.poker.game.variants.OmahaVariant(),
                new java.security.SecureRandom()
        );
        engine.sitPlayer("a", "Alice", 0, 1000);
        engine.sitPlayer("b", "Bob", 1, 1000);
        return engine;
    }

    static Deck firstHandDeck(List<Integer> occupiedSeats, Map<Integer, List<String>> holesBySeat, List<String> board) {
        if (board.size() != 5) {
            throw new IllegalArgumentException("Board must be flop, turn, and river (5 cards)");
        }
        int dealer = occupiedSeats.stream().mapToInt(Integer::intValue).min().orElseThrow();
        List<Integer> dealOrder = clockwiseFromLeftOf(occupiedSeats, dealer);
        Set<Card> used = new HashSet<>();
        for (List<String> hole : holesBySeat.values()) {
            hole.stream().map(Card::parse).forEach(used::add);
        }
        board.stream().map(Card::parse).forEach(used::add);

        int holeCount = holesBySeat.values().iterator().next().size();
        List<Card> cards = new ArrayList<>();
        for (int round = 0; round < holeCount; round++) {
            for (int seat : dealOrder) {
                cards.add(Card.parse(holesBySeat.get(seat).get(round)));
            }
        }
        cards.add(unused(used));
        cards.add(Card.parse(board.get(0)));
        cards.add(Card.parse(board.get(1)));
        cards.add(Card.parse(board.get(2)));
        cards.add(unused(used));
        cards.add(Card.parse(board.get(3)));
        cards.add(unused(used));
        cards.add(Card.parse(board.get(4)));
        return Deck.ofCards(cards);
    }

    /** Hole cards only, then replacement cards in draw order (no community). */
    static Deck fiveCardDrawDeck(
            List<Integer> occupiedSeats,
            Map<Integer, List<String>> holesBySeat,
            List<String> replacements
    ) {
        int dealer = occupiedSeats.stream().mapToInt(Integer::intValue).min().orElseThrow();
        List<Integer> dealOrder = clockwiseFromLeftOf(occupiedSeats, dealer);
        Set<Card> used = new HashSet<>();
        for (List<String> hole : holesBySeat.values()) {
            if (hole.size() != 5) {
                throw new IllegalArgumentException("5-Card Draw deals 5 hole cards");
            }
            hole.stream().map(Card::parse).forEach(used::add);
        }
        replacements.stream().map(Card::parse).forEach(used::add);

        List<Card> cards = new ArrayList<>();
        for (int round = 0; round < 5; round++) {
            for (int seat : dealOrder) {
                cards.add(Card.parse(holesBySeat.get(seat).get(round)));
            }
        }
        for (String code : replacements) {
            cards.add(Card.parse(code));
        }
        return Deck.ofCards(cards);
    }

    /** 3 hole cards each, then street cards in deal order (left of dealer), no community. */
    static Deck razzDeck(
            List<Integer> occupiedSeats,
            Map<Integer, List<String>> holesBySeat,
            List<String> streetCardsInDealOrder
    ) {
        int dealer = occupiedSeats.stream().mapToInt(Integer::intValue).min().orElseThrow();
        List<Integer> dealOrder = clockwiseFromLeftOf(occupiedSeats, dealer);
        for (List<String> hole : holesBySeat.values()) {
            if (hole.size() != 3) {
                throw new IllegalArgumentException("Razz starts with 3 hole cards");
            }
        }
        List<Card> cards = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            for (int seat : dealOrder) {
                cards.add(Card.parse(holesBySeat.get(seat).get(round)));
            }
        }
        for (String code : streetCardsInDealOrder) {
            cards.add(Card.parse(code));
        }
        return Deck.ofCards(cards);
    }

    static int chips(TexasHoldemEngine engine, String playerId) {
        return engine.state().player(playerId).getChipStack();
    }

    static int totalChips(TexasHoldemEngine engine) {
        return engine.state().getPlayers().stream().mapToInt(PlayerState::getChipStack).sum()
                + engine.state().getPot();
    }

    static String currentId(TexasHoldemEngine engine) {
        PlayerState player = engine.state().currentPlayer();
        return player == null ? null : player.getPlayerId();
    }

    private static List<Integer> clockwiseFromLeftOf(List<Integer> seats, int dealer) {
        List<Integer> ordered = new ArrayList<>();
        int seat = dealer;
        int guard = 0;
        while (ordered.size() < seats.size()) {
            seat = (seat + 1) % 10;
            if (seats.contains(seat)) {
                ordered.add(seat);
            }
            if (++guard > 20) {
                throw new IllegalStateException("Could not order seats clockwise from dealer " + dealer);
            }
        }
        return ordered;
    }

    private static Card unused(Set<Card> used) {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                Card card = new Card(rank, suit);
                if (used.add(card)) {
                    return card;
                }
            }
        }
        throw new IllegalStateException("No unused cards remain");
    }

    record Seat(String playerId, String username, int seat, int chips) {
    }
}
