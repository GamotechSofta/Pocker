package com.poker.game.engine;

import com.poker.game.evaluator.HandEvaluation;
import com.poker.game.model.Card;
import com.poker.game.model.Deck;
import com.poker.game.variants.PokerVariant;
import com.poker.game.variants.TexasHoldemVariant;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class TexasHoldemEngine {
    public record Snapshot(GameState.Snapshot state, List<Card> remainingDeck) {
        public Snapshot {
            Objects.requireNonNull(state, "state is required");
            remainingDeck = remainingDeck == null ? null : List.copyOf(remainingDeck);
        }
    }
    public Snapshot snapshot() { return new Snapshot(state.snapshot(), deck == null ? null : deck.snapshotCards()); }
    public static TexasHoldemEngine restore(Snapshot s) {
        GameState.Snapshot saved = s.state();
        TexasHoldemEngine engine = new TexasHoldemEngine(saved.gameId(), saved.tableConfig(), com.poker.game.variants.Variants.create(saved.variant()), new SecureRandom());
        engine.state.restoreFrom(saved); engine.deck = s.remainingDeck() == null ? null : Deck.ofCards(s.remainingDeck());
        return engine;
    }
    public TexasHoldemEngine copy() { return restore(snapshot()); }

    private final TableConfig config;
    private final PokerVariant variant;
    private final Random random;
    private final PotManager potManager;
    private final TurnManager turnManager;
    private final GameState state;
    private Deck deck;

    public TexasHoldemEngine() {
        this(TableConfig.developmentDefaults());
    }

    public TexasHoldemEngine(TableConfig config) {
        this(java.util.UUID.randomUUID().toString(), config, new TexasHoldemVariant(), new SecureRandom());
    }

    public TexasHoldemEngine(String gameId, TableConfig config) {
        this(gameId, config, new TexasHoldemVariant(), new SecureRandom());
    }

    public TexasHoldemEngine(String gameId, TableConfig config, PokerVariant variant) {
        this(gameId, config, variant, new SecureRandom());
    }

    public TexasHoldemEngine(TableConfig config, Random random) {
        this(java.util.UUID.randomUUID().toString(), config, new TexasHoldemVariant(), random);
    }

    public TexasHoldemEngine(TableConfig config, PokerVariant variant, Random random) {
        this(java.util.UUID.randomUUID().toString(), config, variant, random);
    }

    public TexasHoldemEngine(String gameId, TableConfig config, PokerVariant variant, Random random) {
        this.config = Objects.requireNonNull(config, "config is required");
        this.variant = Objects.requireNonNull(variant, "variant is required");
        this.random = Objects.requireNonNull(random, "random is required");
        this.potManager = new PotManager();
        this.turnManager = new TurnManager(config.maxPlayers());
        this.state = new GameState(gameId, variant.getName(), config);
    }

    public GameState state() {
        return state;
    }

    public TableConfig config() {
        return config;
    }

    public void sitPlayer(String playerId, String username) {
        int seat = firstEmptySeat();
        if (seat < 0) {
            throw GameActionException.seatUnavailable();
        }
        sitPlayer(playerId, username, seat, config.startingChips());
    }

    public void sitPlayer(String playerId, String username, int seat) {
        sitPlayer(playerId, username, seat, config.startingChips());
    }

    public void sitPlayer(String playerId, String username, int seat, int chips) {
        if (chips <= 0) {
            throw GameActionException.invalid("Players must sit with a positive chip stack.");
        }
        restoreSeat(playerId, username, seat, chips);
    }

    /** Restores a persisted seat, including a zero-stack seat that will sit out. */
    public void restoreSeat(String playerId, String username, int seat, int chips) {
        requireBetweenHands();
        if (seat < 0 || seat >= config.maxPlayers()) {
            throw GameActionException.seatUnavailable();
        }
        if (state.playerAt(seat) != null) {
            throw GameActionException.seatUnavailable();
        }
        if (state.player(playerId) != null) {
            throw GameActionException.invalid("Player is already seated.");
        }
        if (chips < 0) {
            throw GameActionException.invalid("A restored stack cannot be negative.");
        }
        state.putPlayer(new PlayerState(playerId, username, seat, chips));
        state.addEvent("PLAYER_JOINED", username + " sat in seat " + seat);
    }

    /** Restores the sequence and button before starting a persisted hand. */
    public void restoreHandBoundary(int completedHandNumber, int previousDealerSeat) {
        if (state.getStatus() != GameStatus.WAITING || state.getHandNumber() != 0 || completedHandNumber < 0
                || previousDealerSeat < -1 || previousDealerSeat >= config.maxPlayers()) {
            throw new IllegalArgumentException("Invalid hand boundary.");
        }
        state.setHandNumber(completedHandNumber);
        state.setDealerSeat(previousDealerSeat);
    }

    public void setConnected(String playerId, boolean connected) {
        PlayerState player = state.player(playerId);
        if (player == null) {
            return;
        }
        boolean wasConnected = player.isConnected();
        player.setConnected(connected);
        if (connected && !wasConnected) {
            state.addEvent("PLAYER_RECONNECTED", player.getUsername() + " reconnected");
        }
    }

    public void leaveSeat(String playerId) {
        requireBetweenHands();
        PlayerState player = requirePlayer(playerId);
        removeSeat(player);
    }

    public void abandonSeat(String playerId) {
        PlayerState player = requirePlayer(playerId);
        boolean active = state.isHandInProgress()
                || state.isBettingActive()
                || state.isDiscardActive()
                || state.isDrawActive();
        if (!active) {
            removeSeat(player);
            return;
        }

        boolean wasCurrentTurn = state.getCurrentTurnSeat() == player.getSeat();
        player.fold();
        player.setConnected(false);
        state.addEvent("PLAYER_LEFT", player.getUsername() + " left the table and folded");

        if (turnManager.playersStillContending(state) <= 1) {
            winByFold();
        } else if (wasCurrentTurn && state.isDiscardActive()) {
            advanceDiscardTurn(player.getSeat());
        } else if (wasCurrentTurn && state.isDrawActive()) {
            advanceDrawTurn(player.getSeat());
        } else if (wasCurrentTurn && state.isBettingActive()) {
            afterAction();
        }
    }

    /** Credits chips for a wallet-funded (or house-funded bot) rebuy between hands. */
    public void rebuy(String playerId, int chips, String detail) {
        if (chips <= 0) {
            throw new IllegalArgumentException("Rebuy chips must be positive");
        }
        PlayerState player = requirePlayer(playerId);
        player.credit(chips);
        state.addEvent("PLAYER_REBUY", detail != null ? detail : player.getUsername() + " rebought");
    }

    private void removeSeat(PlayerState player) {
        state.removeSeat(player.getSeat());
        state.addEvent("PLAYER_LEFT", player.getUsername() + " left the table");
        if (state.getPlayers().isEmpty()) {
            state.setDealerSeat(-1);
            state.setStatus(GameStatus.WAITING);
        }
    }

    public void startHand() {
        Deck fresh = Deck.createDeck(random);
        fresh.shuffle();
        startHand(fresh);
    }

    public void startHand(Deck preparedDeck) {
        this.deck = Objects.requireNonNull(preparedDeck, "deck is required");
        // Rebuys are funded by TableService from the player wallet (bots are house-funded).
        List<PlayerState> eligible = playersWithChips();
        if (eligible.size() < 2) {
            throw GameActionException.invalid("Need at least two players with chips to start a hand.");
        }
        if (state.isHandInProgress() || state.isBettingActive()) {
            throw GameActionException.alreadyStarted();
        }

        state.setStatus(GameStatus.STARTING);
        state.setHandNumber(state.getHandNumber() + 1);
        state.clearHandResults();
        state.clearCommunityCards();
        for (PlayerState player : state.getPlayers()) {
            player.resetForNewHand();
        }

        moveDealerButton(eligible);
        assignBlinds(eligible);
        dealHoleCards(eligible);
        postAntes(eligible);
        postBlinds();
        state.addEvent("HAND_STARTED", "Hand " + state.getHandNumber());
        if (variant.requiresDiscardBeforeFlop()) {
            beginDiscardPhase(eligible);
            return;
        }
        if (variant.requiresStudStreets()) {
            beginBettingRound(BettingRound.THIRD_STREET, GameStatus.THIRD_STREET, true);
            int first = turnManager.firstToActPreFlop(state);
            state.setCurrentTurnSeat(first);
            if (turnManager.playersStillContending(state) <= 1) {
                winByFold();
            } else if (first < 0 || turnManager.bettingRoundComplete(state) || shouldRunOutBoard()) {
                advanceFromCompletedRound();
            }
            return;
        }
        beginBettingRound(BettingRound.PRE_FLOP, GameStatus.PRE_FLOP, true);
        int first = turnManager.firstToActPreFlop(state);
        state.setCurrentTurnSeat(first);
        if (turnManager.playersStillContending(state) <= 1) {
            winByFold();
        } else if (first < 0 || turnManager.bettingRoundComplete(state) || shouldRunOutBoard()) {
            advanceFromCompletedRound();
        }
    }

    public void apply(PlayerAction action) {
        Objects.requireNonNull(action, "action is required");
        if (state.isDiscardActive()) {
            applyDiscard(action);
            return;
        }
        if (state.isDrawActive()) {
            applyDraw(action);
            return;
        }
        if (!state.isBettingActive()) {
            throw GameActionException.invalid("Betting is not active.");
        }
        PlayerState actor = requirePlayer(action.playerId());
        if (state.getCurrentTurnSeat() != actor.getSeat()) {
            throw GameActionException.notPlayerTurn();
        }
        LegalActions legal = legalActions(action.playerId());
        if (!legal.allows(action.type())) {
            throw GameActionException.invalid("Action " + action.type() + " is not legal.");
        }

        switch (action.type()) {
            case FOLD -> fold(actor);
            case CHECK -> check(actor);
            case CALL -> call(actor);
            case BET -> bet(actor, action.amount());
            case RAISE -> raise(actor, action.amount());
            case ALL_IN -> allIn(actor);
            case DISCARD -> throw GameActionException.invalid("Discard is only allowed during discard or draw.");
        }
        actor.markActed();
        state.addEvent("PLAYER_ACTION", actor.getUsername() + " " + action.type().name().toLowerCase());
        afterAction();
    }

    public LegalActions legalActions(String playerId) {
        PlayerState player = state.player(playerId);
        if (player == null || state.getCurrentTurnSeat() != player.getSeat()) {
            return LegalActions.none();
        }
        if (state.isDiscardActive()) {
            if (player.isFolded() || player.getHoleCards().size() != variant.getInitialHoleCards()) {
                return LegalActions.none();
            }
            return new LegalActions(EnumSet.of(ActionType.DISCARD), 0, 0, player.getHoleCards().size() - 1);
        }
        if (state.isDrawActive()) {
            if (!needsDraw(player)) {
                return LegalActions.none();
            }
            return new LegalActions(EnumSet.of(ActionType.DISCARD), 0, 0, config.maxDrawCount());
        }
        if (!state.isBettingActive()) {
            return LegalActions.none();
        }
        int callAmount = Math.max(0, state.getCurrentBet() - player.getBetThisRound());
        int maxTo = player.getBetThisRound() + player.getChipStack();
        Set<ActionType> actions = EnumSet.noneOf(ActionType.class);
        actions.add(ActionType.FOLD);

        boolean facingBet = callAmount > 0;
        int allInTo = player.getBetThisRound() + player.getChipStack();
        if (!facingBet) {
            actions.add(ActionType.CHECK);
            if (player.getChipStack() > 0) {
                actions.add(ActionType.ALL_IN);
                if (allInTo >= config.bigBlind()) {
                    actions.add(ActionType.BET);
                }
            }
        } else {
            actions.add(ActionType.CALL);
            boolean allInIsRaise = allInTo > state.getCurrentBet();
            if (player.getChipStack() > 0 && (player.isRaiseAllowed() || !allInIsRaise)) {
                actions.add(ActionType.ALL_IN);
            }
            long minRaiseTo = (long) state.getCurrentBet() + state.getMinimumRaise();
            if (player.isRaiseAllowed() && player.getChipStack() > callAmount && maxTo >= minRaiseTo) {
                actions.add(ActionType.RAISE);
            }
        }

        int minTo = facingBet
                ? (int) Math.min(Integer.MAX_VALUE, (long) state.getCurrentBet() + state.getMinimumRaise())
                : config.bigBlind();
        return new LegalActions(actions, callAmount, minTo, maxTo);
    }

    private void fold(PlayerState player) {
        player.fold();
    }

    private void beginDiscardPhase(List<PlayerState> eligible) {
        state.setBettingRound(BettingRound.DISCARD);
        state.setStatus(GameStatus.DISCARD);
        state.addEvent("DISCARD_PHASE", "Discard one hole card before the flop");
        int first = turnManager.nextSeat(state, state.getDealerSeat(), eligible::contains);
        state.setCurrentTurnSeat(first);
        if (first < 0 || !needsDiscard(state.playerAt(first))) {
            advanceDiscardTurn(first);
        }
    }

    private void applyDiscard(PlayerAction action) {
        if (action.type() != ActionType.DISCARD) {
            throw GameActionException.invalid("Only discard is allowed before the flop.");
        }
        PlayerState actor = requirePlayer(action.playerId());
        if (state.getCurrentTurnSeat() != actor.getSeat()) {
            throw GameActionException.notPlayerTurn();
        }
        if (!needsDiscard(actor)) {
            throw GameActionException.invalid("You have already discarded.");
        }
        actor.discardHoleCard(action.amount());
        state.addEvent("PLAYER_DISCARD", actor.getUsername() + " discarded a hole card");
        advanceDiscardTurn(actor.getSeat());
    }

    private void advanceDiscardTurn(int fromSeat) {
        if (playersNeedingDiscard().isEmpty()) {
            beginPreFlopAfterDiscard();
            return;
        }
        int next = nextDiscardSeat(fromSeat);
        if (next < 0) {
            beginPreFlopAfterDiscard();
            return;
        }
        state.setCurrentTurnSeat(next);
    }

    private void beginPreFlopAfterDiscard() {
        beginBettingRound(BettingRound.PRE_FLOP, GameStatus.PRE_FLOP, true);
        int first = turnManager.firstToActPreFlop(state);
        state.setCurrentTurnSeat(first);
        if (turnManager.playersStillContending(state) <= 1) {
            winByFold();
        } else if (first < 0 || turnManager.bettingRoundComplete(state) || shouldRunOutBoard()) {
            advanceFromCompletedRound();
        }
    }

    private void beginDrawPhase() {
        state.setBettingRound(BettingRound.DRAW);
        state.setStatus(GameStatus.DRAW);
        state.setCurrentBet(0);
        state.setLastRaiseAmount(config.bigBlind());
        state.setMinimumRaise(config.bigBlind());
        state.addEvent("DRAW_PHASE", "Discard up to " + config.maxDrawCount() + " cards and draw replacements");
        List<PlayerState> needing = playersNeedingDraw();
        int first = turnManager.nextSeat(state, state.getDealerSeat(), needing::contains);
        state.setCurrentTurnSeat(first);
        if (first < 0 || needing.isEmpty()) {
            beginSecondBettingAfterDraw();
        }
    }

    private void applyDraw(PlayerAction action) {
        if (action.type() != ActionType.DISCARD) {
            throw GameActionException.invalid("Only discard/draw is allowed during the draw phase.");
        }
        PlayerState actor = requirePlayer(action.playerId());
        if (state.getCurrentTurnSeat() != actor.getSeat()) {
            throw GameActionException.notPlayerTurn();
        }
        if (!needsDraw(actor)) {
            throw GameActionException.invalid("You have already drawn.");
        }
        List<Integer> indices = normalizeDrawIndices(action.indices(), actor.getHoleCards().size());
        if (indices.size() > config.maxDrawCount()) {
            throw GameActionException.invalid("You may discard at most " + config.maxDrawCount() + " cards.");
        }
        List<Card> removed = new ArrayList<>();
        List<Integer> descending = indices.stream().sorted((a, b) -> Integer.compare(b, a)).toList();
        for (int index : descending) {
            removed.add(actor.discardHoleCard(index));
        }
        for (int i = 0; i < removed.size(); i++) {
            actor.addHoleCard(deck.draw());
        }
        actor.markDrawCompleted();
        if (removed.isEmpty()) {
            state.addEvent("PLAYER_DRAW", actor.getUsername() + " stands pat");
        } else {
            state.addEvent("PLAYER_DRAW", actor.getUsername() + " discarded " + removed.size()
                    + " and drew " + removed.size());
        }
        advanceDrawTurn(actor.getSeat());
    }

    private List<Integer> normalizeDrawIndices(List<Integer> raw, int holeCount) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Integer> unique = new ArrayList<>();
        for (Integer index : raw) {
            if (index == null || index < 0 || index >= holeCount) {
                throw GameActionException.invalid("Choose hole cards within your hand to discard.");
            }
            if (unique.contains(index)) {
                throw GameActionException.invalid("Each card can only be discarded once.");
            }
            unique.add(index);
        }
        return unique;
    }

    private void advanceDrawTurn(int fromSeat) {
        if (playersNeedingDraw().isEmpty()) {
            beginSecondBettingAfterDraw();
            return;
        }
        int next = nextDrawSeat(fromSeat);
        if (next < 0) {
            beginSecondBettingAfterDraw();
            return;
        }
        state.setCurrentTurnSeat(next);
    }

    private void beginSecondBettingAfterDraw() {
        if (turnManager.playersStillContending(state) <= 1) {
            winByFold();
            return;
        }
        beginBettingRound(BettingRound.SECOND_BET, GameStatus.SECOND_BET, false);
        continueOrSkipBetting();
    }

    private boolean needsDraw(PlayerState player) {
        return player != null
                && !player.isFolded()
                && player.isInHand()
                && !player.hasCompletedDraw();
    }

    private List<PlayerState> playersNeedingDraw() {
        return state.getPlayers().stream().filter(this::needsDraw).toList();
    }

    private int nextDrawSeat(int fromSeat) {
        List<PlayerState> needing = playersNeedingDraw();
        if (needing.isEmpty()) {
            return -1;
        }
        return turnManager.nextSeat(state, fromSeat, needing::contains);
    }

    private boolean needsDiscard(PlayerState player) {
        return player != null
                && !player.isFolded()
                && player.getHoleCards().size() == variant.getInitialHoleCards();
    }

    private List<PlayerState> playersNeedingDiscard() {
        return state.getPlayers().stream().filter(this::needsDiscard).toList();
    }

    private int nextDiscardSeat(int fromSeat) {
        List<PlayerState> needing = playersNeedingDiscard();
        if (needing.isEmpty()) {
            return -1;
        }
        return turnManager.nextSeat(state, fromSeat, needing::contains);
    }

    private void check(PlayerState player) {
        if (player.getBetThisRound() != state.getCurrentBet()) {
            throw GameActionException.invalid("You cannot check when facing a bet.");
        }
    }

    private void call(PlayerState player) {
        int needed = state.getCurrentBet() - player.getBetThisRound();
        if (needed <= 0) {
            throw GameActionException.invalid("Nothing to call.");
        }
        potManager.collectBet(player, Math.min(needed, player.getChipStack()));
    }

    private void bet(PlayerState player, int toAmount) {
        if (state.getCurrentBet() != 0) {
            throw GameActionException.invalid("You cannot bet when a bet already exists.");
        }
        putRaise(player, toAmount, true);
    }

    private void raise(PlayerState player, int toAmount) {
        if (state.getCurrentBet() <= 0) {
            throw GameActionException.invalid("There is no bet to raise.");
        }
        putRaise(player, toAmount, false);
    }

    private void allIn(PlayerState player) {
        int toAmount = player.getBetThisRound() + player.getChipStack();
        if (toAmount <= state.getCurrentBet()) {
            potManager.collectBet(player, player.getChipStack());
            return;
        }
        putRaise(player, toAmount, state.getCurrentBet() == 0);
    }

    private void putRaise(PlayerState player, int toAmount, boolean openingBet) {
        int needed = toAmount - player.getBetThisRound();
        if (needed <= 0) {
            throw GameActionException.invalidRaise("Amount must increase the bet.");
        }
        if (needed > player.getChipStack()) {
            throw GameActionException.insufficientChips();
        }
        int raiseBy = toAmount - state.getCurrentBet();
        boolean shortAllIn = needed == player.getChipStack();
        long minimumTo = openingBet ? config.bigBlind() : (long) state.getCurrentBet() + state.getMinimumRaise();
        boolean fullRaise = openingBet
                ? toAmount >= config.bigBlind()
                : raiseBy >= state.getMinimumRaise();
        if (!shortAllIn && toAmount < minimumTo) {
            throw GameActionException.invalidRaise(
                    openingBet ? "Bet is below the minimum." : "Raise is below the minimum."
            );
        }
        potManager.collectBet(player, needed);
        int previousBet = state.getCurrentBet();
        state.setCurrentBet(toAmount);
        if (fullRaise) {
            int increment = openingBet ? toAmount : raiseBy;
            state.setLastRaiseAmount(increment);
            state.setMinimumRaise(Math.max(increment, config.bigBlind()));
            reopenAction(player);
        } else {
            reopenForUnmatchedOnly(player, previousBet);
        }
    }

    private void reopenAction(PlayerState actor) {
        for (PlayerState other : state.getPlayers()) {
            if (other == actor || other.isFolded() || other.isAllIn()) {
                continue;
            }
            other.clearActed();
            other.setRaiseAllowed(true);
        }
    }

    private void reopenForUnmatchedOnly(PlayerState actor, int previousBet) {
        for (PlayerState other : state.getPlayers()) {
            if (other == actor || other.isFolded() || other.isAllIn()) {
                continue;
            }
            if (other.getBetThisRound() < state.getCurrentBet()) {
                boolean alreadyClosed = other.hasActedThisRound() && other.getBetThisRound() == previousBet;
                other.clearActed();
                other.setRaiseAllowed(!alreadyClosed);
            }
        }
    }

    private void afterAction() {
        if (turnManager.playersStillContending(state) <= 1) {
            winByFold();
            return;
        }
        if (!turnManager.bettingRoundComplete(state)) {
            int next = turnManager.nextCanActSeat(state, state.getCurrentTurnSeat());
            if (next >= 0) {
                state.setCurrentTurnSeat(next);
                return;
            }
        }
        advanceFromCompletedRound();
    }

    private void advanceFromCompletedRound() {
        if (variant.requiresDrawPhase()) {
            switch (state.getBettingRound()) {
                case PRE_FLOP -> beginDrawPhase();
                case SECOND_BET -> showdown();
                default -> showdown();
            }
            return;
        }
        if (variant.requiresStudStreets()) {
            if (shouldRunOutBoard()) {
                runOutRemainingStudCards();
                showdown();
                return;
            }
            switch (state.getBettingRound()) {
                case THIRD_STREET -> dealStudCardAndBet(BettingRound.FOURTH_STREET, GameStatus.FOURTH_STREET);
                case FOURTH_STREET -> dealStudCardAndBet(BettingRound.FIFTH_STREET, GameStatus.FIFTH_STREET);
                case FIFTH_STREET -> dealStudCardAndBet(BettingRound.SIXTH_STREET, GameStatus.SIXTH_STREET);
                case SIXTH_STREET -> dealStudCardAndBet(BettingRound.SEVENTH_STREET, GameStatus.SEVENTH_STREET);
                case SEVENTH_STREET -> showdown();
                default -> showdown();
            }
            return;
        }
        if (shouldRunOutBoard()) {
            runOutRemainingBoard();
            showdown();
            return;
        }
        switch (state.getBettingRound()) {
            case PRE_FLOP -> dealFlopAndBet();
            case FLOP -> dealTurnAndBet();
            case TURN -> dealRiverAndBet();
            case RIVER -> showdown();
            default -> showdown();
        }
    }

    private void dealStudCardAndBet(BettingRound round, GameStatus status) {
        dealOneStudCardToContenders();
        state.addEvent("STUD_DEAL", "Dealt to " + cardsDealtLabel() + " cards");
        beginBettingRound(round, status, false);
        continueOrSkipBetting();
    }

    private void runOutRemainingStudCards() {
        int target = variant.studTotalCards();
        while (true) {
            List<PlayerState> needing = state.getPlayers().stream()
                    .filter(player -> !player.isFolded() && player.isInHand() && player.getHoleCards().size() < target)
                    .toList();
            if (needing.isEmpty()) {
                break;
            }
            dealOneStudCardToContenders();
            state.addEvent("STUD_DEAL", "Run out to " + cardsDealtLabel() + " cards");
        }
        state.setCurrentTurnSeat(-1);
    }

    private void dealOneStudCardToContenders() {
        List<PlayerState> contenders = state.getPlayers().stream()
                .filter(player -> !player.isFolded() && player.isInHand())
                .sorted((a, b) -> Integer.compare(a.getSeat(), b.getSeat()))
                .toList();
        if (contenders.isEmpty()) {
            return;
        }
        int firstSeat = turnManager.nextSeat(state, state.getDealerSeat(), contenders::contains);
        int seat = firstSeat;
        for (int i = 0; i < contenders.size(); i++) {
            PlayerState player = state.playerAt(seat);
            if (player.getHoleCards().size() < variant.studTotalCards()) {
                player.addHoleCard(deck.draw());
            }
            if (i < contenders.size() - 1) {
                seat = turnManager.nextSeat(state, seat, contenders::contains);
            }
        }
    }

    private String cardsDealtLabel() {
        return state.getPlayers().stream()
                .filter(player -> !player.isFolded() && player.isInHand())
                .map(player -> String.valueOf(player.getHoleCards().size()))
                .findFirst()
                .orElse("0");
    }

    private boolean shouldRunOutBoard() {
        if (turnManager.playersStillContending(state) < 2) {
            return false;
        }
        List<PlayerState> canAct = state.getPlayers().stream().filter(PlayerState::canAct).toList();
        if (canAct.size() >= 2) {
            return false;
        }
        if (canAct.isEmpty()) {
            return true;
        }
        PlayerState last = canAct.getFirst();
        return last.getBetThisRound() >= state.getCurrentBet();
    }

    private void dealFlopAndBet() {
        burn();
        state.addCommunityCards(deck.drawMultiple(variant.flopCount()));
        state.addEvent("FLOP", cards(state.getCommunityCards()));
        beginBettingRound(BettingRound.FLOP, GameStatus.FLOP, false);
        continueOrSkipBetting();
    }

    private void dealTurnAndBet() {
        burn();
        state.addCommunityCards(deck.drawMultiple(variant.turnCount()));
        state.addEvent("TURN", cards(state.getCommunityCards()));
        beginBettingRound(BettingRound.TURN, GameStatus.TURN, false);
        continueOrSkipBetting();
    }

    private void dealRiverAndBet() {
        burn();
        state.addCommunityCards(deck.drawMultiple(variant.riverCount()));
        state.addEvent("RIVER", cards(state.getCommunityCards()));
        beginBettingRound(BettingRound.RIVER, GameStatus.RIVER, false);
        continueOrSkipBetting();
    }

    private void continueOrSkipBetting() {
        if (shouldRunOutBoard() || turnManager.bettingRoundComplete(state)) {
            advanceFromCompletedRound();
            return;
        }
        int first = turnManager.firstToActPostFlop(state);
        state.setCurrentTurnSeat(first);
        if (first < 0) {
            advanceFromCompletedRound();
        }
    }

    private void runOutRemainingBoard() {
        while (state.getCommunityCards().size() < 5) {
            int remaining = 5 - state.getCommunityCards().size();
            if (remaining > 2) {
                burn();
                state.addCommunityCards(deck.drawMultiple(variant.flopCount()));
                state.addEvent("FLOP", cards(state.getCommunityCards()));
                state.setBettingRound(BettingRound.FLOP);
                state.setStatus(GameStatus.FLOP);
            } else if (remaining == 2) {
                burn();
                state.addCommunityCards(deck.drawMultiple(variant.turnCount()));
                state.addEvent("TURN", cards(state.getCommunityCards()));
                state.setBettingRound(BettingRound.TURN);
                state.setStatus(GameStatus.TURN);
            } else {
                burn();
                state.addCommunityCards(deck.drawMultiple(variant.riverCount()));
                state.addEvent("RIVER", cards(state.getCommunityCards()));
                state.setBettingRound(BettingRound.RIVER);
                state.setStatus(GameStatus.RIVER);
            }
        }
        state.setCurrentTurnSeat(-1);
    }

    private void winByFold() {
        PlayerState winner = state.getPlayers().stream()
                .filter(player -> !player.isFolded() && player.isInHand())
                .findFirst()
                .orElseThrow(() -> GameActionException.invalid("No remaining player."));
        state.setStatus(GameStatus.PLAYER_WON_BY_FOLD);
        state.setFoldWinnerId(winner.getPlayerId());
        state.setCurrentTurnSeat(-1);
        state.addEvent("PLAYER_WON_BY_FOLD", winner.getUsername() + " wins uncontested");
        List<Payout> payouts = potManager.awardEntirePot(state.getPlayers(), winner.getPlayerId());
        applyPayouts(payouts);
    }

    private void showdown() {
        state.setStatus(GameStatus.SHOWDOWN);
        state.setCurrentTurnSeat(-1);
        Map<String, HandEvaluation> evaluations = new LinkedHashMap<>();
        List<ShowdownResult> results = new ArrayList<>();
        for (PlayerState player : state.getPlayers()) {
            if (player.isFolded() || !player.isInHand()) {
                continue;
            }
            HandEvaluation evaluation = variant.evaluateHand(player.getHoleCards(), state.getCommunityCards());
            evaluations.put(player.getPlayerId(), evaluation);
            results.add(new ShowdownResult(player.getPlayerId(), player.getHoleCards(), evaluation));
        }
        state.setShowdown(results);
        List<SidePot> pots = potManager.calculateSidePots(state.getPlayers());
        state.setSidePots(pots);
        List<Payout> payouts = potManager.distributePot(
                pots,
                evaluations,
                turnManager.clockwiseIdsFromLeftOfDealer(state)
        );
        state.addEvent("SHOWDOWN", "Hands revealed");
        applyPayouts(payouts);
    }

    private void applyPayouts(List<Payout> payouts) {
        state.setStatus(GameStatus.PAYOUT);
        for (Payout payout : payouts) {
            PlayerState player = requirePlayer(payout.playerId());
            player.credit(payout.amount());
            state.addEvent("PAYOUT", player.getUsername() + " collected " + payout.amount());
        }
        for (PlayerState player : state.getPlayers()) {
            player.clearContributions();
        }
        state.setPayouts(payouts);
        state.setCurrentTurnSeat(-1);
        state.setBettingRound(BettingRound.NONE);
        state.setStatus(GameStatus.NEXT_HAND);
    }

    private void beginBettingRound(BettingRound round, GameStatus status, boolean preflop) {
        state.setBettingRound(round);
        state.setStatus(status);
        if (!preflop) {
            for (PlayerState player : state.getPlayers()) {
                player.resetForBettingRound();
            }
            state.setCurrentBet(0);
            state.setLastRaiseAmount(config.bigBlind());
            state.setMinimumRaise(config.bigBlind());
        }
    }

    private void dealHoleCards(List<PlayerState> eligible) {
        int first = turnManager.nextSeat(state, state.getDealerSeat(), eligible::contains);
        for (int round = 0; round < variant.getInitialHoleCards(); round++) {
            int seat = first;
            for (int i = 0; i < eligible.size(); i++) {
                PlayerState player = state.playerAt(seat);
                player.addHoleCard(deck.draw());
                if (i < eligible.size() - 1) {
                    seat = turnManager.nextSeat(state, seat, eligible::contains);
                }
            }
        }
        state.addEvent("CARDS_DEALT", "Hole cards dealt");
    }

    private void postBlinds() {
        PlayerState small = state.playerAt(state.getSmallBlindSeat());
        PlayerState big = state.playerAt(state.getBigBlindSeat());
        potManager.collectBet(small, Math.min(config.smallBlind(), small.getChipStack()));
        potManager.collectBet(big, Math.min(config.bigBlind(), big.getChipStack()));
        state.setCurrentBet(Math.max(small.getBetThisRound(), big.getBetThisRound()));
        if (big.getBetThisRound() < config.bigBlind() && small.getBetThisRound() > big.getBetThisRound()) {
            state.setCurrentBet(small.getBetThisRound());
        }
        if (big.getBetThisRound() >= config.bigBlind()) {
            state.setCurrentBet(Math.max(state.getCurrentBet(), config.bigBlind()));
        }
        state.setLastRaiseAmount(config.bigBlind());
        state.setMinimumRaise(config.bigBlind());
        state.addEvent("BLINDS_POSTED", "SB " + small.getUsername() + ", BB " + big.getUsername());
    }

    private void postAntes(List<PlayerState> eligible) {
        if (config.ante() == 0) return;
        for (var player : eligible) {
            potManager.collectBet(player, Math.min(config.ante(), player.getChipStack()));
            // Antes build the pot without counting toward the current street's live bet.
            player.resetForBettingRound();
        }
        state.addEvent("ANTES_POSTED", "Ante " + config.ante());
    }

    private void moveDealerButton(List<PlayerState> eligible) {
        if (state.getDealerSeat() < 0) {
            state.setDealerSeat(eligible.getFirst().getSeat());
            return;
        }
        int next = turnManager.nextSeat(state, state.getDealerSeat(), eligible::contains);
        state.setDealerSeat(next);
    }

    private void assignBlinds(List<PlayerState> eligible) {
        if (eligible.size() == 2) {
            state.setSmallBlindSeat(state.getDealerSeat());
            state.setBigBlindSeat(turnManager.nextSeat(state, state.getDealerSeat(), eligible::contains));
            return;
        }
        int small = turnManager.nextSeat(state, state.getDealerSeat(), eligible::contains);
        int big = turnManager.nextSeat(state, small, eligible::contains);
        state.setSmallBlindSeat(small);
        state.setBigBlindSeat(big);
    }

    private void burn() {
        if (variant.burnBeforeCommunityDeals()) {
            deck.draw();
        }
    }

    private List<PlayerState> playersWithChips() {
        return state.getPlayers().stream()
                .filter(player -> player.getChipStack() > 0)
                .sorted((a, b) -> Integer.compare(a.getSeat(), b.getSeat()))
                .toList();
    }

    private int firstEmptySeat() {
        for (int seat = 0; seat < config.maxPlayers(); seat++) {
            if (state.playerAt(seat) == null) {
                return seat;
            }
        }
        return -1;
    }

    private void requireBetweenHands() {
        if (state.isHandInProgress()
                || state.isBettingActive()
                || state.getStatus() == GameStatus.PAYOUT) {
            throw GameActionException.alreadyStarted();
        }
    }

    private PlayerState requirePlayer(String playerId) {
        PlayerState player = state.player(playerId);
        if (player == null) {
            throw GameActionException.playerNotFound();
        }
        return player;
    }

    private static String cards(List<Card> cards) {
        return String.join(" ", cards.stream().map(Card::code).toList());
    }
}
