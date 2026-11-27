package com.poker.game.engine;

import com.poker.game.model.Card;
import com.poker.game.variants.TexasHoldemVariant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GameState {
    public record Snapshot(String gameId, String variant, TableConfig tableConfig, GameStatus status, int handNumber,
                           int dealerSeat, int smallBlindSeat, int bigBlindSeat, int currentTurnSeat,
                           List<PlayerState.Snapshot> players, List<Card> communityCards, int currentBet,
                           int minimumRaise, int lastRaiseAmount, BettingRound bettingRound, List<GameEvent> events,
                           List<Payout> payouts, List<ShowdownResult> showdown, List<SidePot> sidePots, String foldWinnerId) {
        public Snapshot {
            players = List.copyOf(players); communityCards = List.copyOf(communityCards); events = List.copyOf(events);
            payouts = List.copyOf(payouts); showdown = List.copyOf(showdown); sidePots = List.copyOf(sidePots);
        }
    }
    public Snapshot snapshot() {
        return new Snapshot(gameId, variant, tableConfig, status, handNumber, dealerSeat, smallBlindSeat, bigBlindSeat,
                currentTurnSeat, seats.values().stream().map(PlayerState::snapshot).toList(), communityCards,
                currentBet, minimumRaise, lastRaiseAmount, bettingRound, events, payouts, showdown, sidePots, foldWinnerId);
    }
    void restoreFrom(Snapshot s) {
        if (!gameId.equals(s.gameId()) || !variant.equals(s.variant()) || !tableConfig.equals(s.tableConfig())) throw new IllegalArgumentException("Snapshot belongs to another game.");
        status = s.status(); handNumber = s.handNumber(); dealerSeat = s.dealerSeat(); smallBlindSeat = s.smallBlindSeat();
        bigBlindSeat = s.bigBlindSeat(); currentTurnSeat = s.currentTurnSeat(); seats.clear();
        for (PlayerState.Snapshot p : s.players()) seats.put(p.seat(), PlayerState.restore(p));
        communityCards.clear(); communityCards.addAll(s.communityCards()); currentBet = s.currentBet();
        minimumRaise = s.minimumRaise(); lastRaiseAmount = s.lastRaiseAmount(); bettingRound = s.bettingRound();
        events.clear(); events.addAll(s.events()); payouts.clear(); payouts.addAll(s.payouts());
        showdown.clear(); showdown.addAll(s.showdown()); sidePots.clear(); sidePots.addAll(s.sidePots()); foldWinnerId = s.foldWinnerId();
    }

    private final String gameId;
    private final String variant;
    private final TableConfig tableConfig;
    private GameStatus status = GameStatus.WAITING;
    private int handNumber;
    private int dealerSeat = -1;
    private int smallBlindSeat = -1;
    private int bigBlindSeat = -1;
    private int currentTurnSeat = -1;
    private final Map<Integer, PlayerState> seats;
    private final List<Card> communityCards = new ArrayList<>();
    private int currentBet;
    private int minimumRaise;
    private int lastRaiseAmount;
    private BettingRound bettingRound = BettingRound.NONE;
    private final List<GameEvent> events = new ArrayList<>();
    private final List<Payout> payouts = new ArrayList<>();
    private final List<ShowdownResult> showdown = new ArrayList<>();
    private final List<SidePot> sidePots = new ArrayList<>();
    private String foldWinnerId;

    public GameState(TableConfig tableConfig) {
        this(UUID.randomUUID().toString(), TexasHoldemVariant.NAME, tableConfig);
    }

    public GameState(String gameId, String variant, TableConfig tableConfig) {
        this.gameId = gameId;
        this.variant = variant;
        this.tableConfig = tableConfig;
        this.seats = new LinkedHashMap<>();
    }

    public String getGameId() {
        return gameId;
    }

    public String getVariant() {
        return variant;
    }

    public TableConfig getTableConfig() {
        return tableConfig;
    }

    public GameStatus getStatus() {
        return status;
    }

    public int getHandNumber() {
        return handNumber;
    }

    public int getDealerSeat() {
        return dealerSeat;
    }

    public int getSmallBlindSeat() {
        return smallBlindSeat;
    }

    public int getBigBlindSeat() {
        return bigBlindSeat;
    }

    public int getCurrentTurnSeat() {
        return currentTurnSeat;
    }

    public List<PlayerState> getPlayers() {
        return List.copyOf(seats.values());
    }

    public List<Card> getCommunityCards() {
        return List.copyOf(communityCards);
    }

    public int getPot() {
        return seats.values().stream().mapToInt(PlayerState::getHandContribution).sum();
    }

    public int getCurrentBet() {
        return currentBet;
    }

    public int getMinimumRaise() {
        return minimumRaise;
    }

    public int getLastRaiseAmount() {
        return lastRaiseAmount;
    }

    public BettingRound getBettingRound() {
        return bettingRound;
    }

    public List<GameEvent> getEvents() {
        return List.copyOf(events);
    }

    public List<Payout> getPayouts() {
        return List.copyOf(payouts);
    }

    public List<ShowdownResult> getShowdown() {
        return List.copyOf(showdown);
    }

    public List<SidePot> getSidePots() {
        return List.copyOf(sidePots);
    }

    public String getFoldWinnerId() {
        return foldWinnerId;
    }

    public PlayerState playerAt(int seat) {
        return seats.get(seat);
    }

    public PlayerState player(String playerId) {
        return seats.values().stream()
                .filter(player -> player.getPlayerId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    public PlayerState currentPlayer() {
        return currentTurnSeat < 0 ? null : seats.get(currentTurnSeat);
    }

    public boolean isHandInProgress() {
        return status == GameStatus.STARTING
                || status == GameStatus.DISCARD
                || status == GameStatus.PRE_FLOP
                || status == GameStatus.DRAW
                || status == GameStatus.SECOND_BET
                || status == GameStatus.THIRD_STREET
                || status == GameStatus.FOURTH_STREET
                || status == GameStatus.FIFTH_STREET
                || status == GameStatus.SIXTH_STREET
                || status == GameStatus.SEVENTH_STREET
                || status == GameStatus.FLOP
                || status == GameStatus.TURN
                || status == GameStatus.RIVER
                || status == GameStatus.SHOWDOWN
                || status == GameStatus.PLAYER_WON_BY_FOLD;
    }

    public boolean isDiscardActive() {
        return status == GameStatus.DISCARD;
    }

    public boolean isDrawActive() {
        return status == GameStatus.DRAW;
    }

    public boolean isBettingActive() {
        return status == GameStatus.PRE_FLOP
                || status == GameStatus.SECOND_BET
                || status == GameStatus.THIRD_STREET
                || status == GameStatus.FOURTH_STREET
                || status == GameStatus.FIFTH_STREET
                || status == GameStatus.SIXTH_STREET
                || status == GameStatus.SEVENTH_STREET
                || status == GameStatus.FLOP
                || status == GameStatus.TURN
                || status == GameStatus.RIVER;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    void setHandNumber(int handNumber) {
        this.handNumber = handNumber;
    }

    void setDealerSeat(int dealerSeat) {
        this.dealerSeat = dealerSeat;
    }

    void setSmallBlindSeat(int smallBlindSeat) {
        this.smallBlindSeat = smallBlindSeat;
    }

    void setBigBlindSeat(int bigBlindSeat) {
        this.bigBlindSeat = bigBlindSeat;
    }

    void setCurrentTurnSeat(int currentTurnSeat) {
        this.currentTurnSeat = currentTurnSeat;
    }

    void putPlayer(PlayerState player) {
        seats.put(player.getSeat(), player);
    }

    PlayerState removeSeat(int seat) {
        return seats.remove(seat);
    }

    void addCommunityCards(List<Card> cards) {
        communityCards.addAll(cards);
    }

    void clearCommunityCards() {
        communityCards.clear();
    }

    void setCurrentBet(int currentBet) {
        this.currentBet = currentBet;
    }

    void setMinimumRaise(int minimumRaise) {
        this.minimumRaise = minimumRaise;
    }

    void setLastRaiseAmount(int lastRaiseAmount) {
        this.lastRaiseAmount = lastRaiseAmount;
    }

    void setBettingRound(BettingRound bettingRound) {
        this.bettingRound = bettingRound;
    }

    void addEvent(String type, String detail) {
        events.add(new GameEvent(type, detail));
    }

    void clearHandResults() {
        payouts.clear();
        showdown.clear();
        sidePots.clear();
        foldWinnerId = null;
    }

    void setPayouts(List<Payout> values) {
        payouts.clear();
        payouts.addAll(values);
    }

    void setShowdown(List<ShowdownResult> values) {
        showdown.clear();
        showdown.addAll(values);
    }

    void setSidePots(List<SidePot> values) {
        sidePots.clear();
        sidePots.addAll(values);
    }

    void setFoldWinnerId(String foldWinnerId) {
        this.foldWinnerId = foldWinnerId;
    }
}
