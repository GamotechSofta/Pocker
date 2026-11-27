package com.poker.service;

import com.poker.bot.BotService;
import com.poker.config.TableProperties;
import com.poker.dto.CreateTableRequest;
import com.poker.dto.GameActionRequest;
import com.poker.dto.JoinTableRequest;
import com.poker.dto.TableDetailResponse;
import com.poker.dto.TableStateResponse;
import com.poker.dto.TableSummaryResponse;
import com.poker.entity.PokerTable;
import com.poker.entity.PokerTablePlayer;
import com.poker.entity.TableStatus;
import com.poker.entity.User;
import com.poker.exception.ApiException;
import com.poker.exception.ErrorCodes;
import com.poker.exception.GameNotFoundException;
import com.poker.exception.InvalidGameActionException;
import com.poker.exception.PlayerNotFoundException;
import com.poker.game.engine.ActionType;
import com.poker.game.engine.GameActionException;
import com.poker.game.engine.GameState;
import com.poker.game.engine.GameStatus;
import com.poker.game.engine.LegalActions;
import com.poker.game.engine.PlayerAction;
import com.poker.game.engine.PlayerState;
import com.poker.game.engine.TableConfig;
import com.poker.game.engine.TexasHoldemEngine;
import com.poker.game.variants.PokerVariant;
import com.poker.game.variants.TexasHoldemVariant;
import com.poker.game.variants.Variants;
import com.poker.mapper.TableMapper;
import com.poker.repository.PokerTableRepository;
import com.poker.repository.UserRepository;
import com.poker.wallet.InsufficientBalanceException;
import com.poker.wallet.StakeEscrowService;
import com.poker.wallet.WalletService;
import com.poker.websocket.TableUpdatedEvent;
import com.poker.websocket.WsEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class TableService {

    private static final Logger log = LoggerFactory.getLogger(TableService.class);
    private static final int MAX_PROCESSED_REQUEST_IDS = 10_000;

    private record TurnTimer(String signature, int handNumber, Instant deadline) {
    }

    private final Map<UUID, TurnTimer> turnTimers = new ConcurrentHashMap<>();

    private final PokerTableRepository tableRepository;
    private final UserRepository userRepository;
    private final TableProperties tableProperties;
    private final ApplicationEventPublisher events;
    private final WalletService walletService;
    private final StakeEscrowService stakeEscrowService;
    private final Map<UUID, TexasHoldemEngine> engines = new ConcurrentHashMap<>();
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();
    /** tableId:userId -> active buy-in/rebuy escrow id */
    private final Map<String, String> seatEscrows = new ConcurrentHashMap<>();
    private final Set<String> processedRequestIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<String> processedRequestIdOrder = new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicBoolean draining = new java.util.concurrent.atomic.AtomicBoolean(false);

    public boolean isDraining() {
        return draining.get();
    }

    public void setDraining(boolean drain) {
        this.draining.set(drain);
    }

    public int activeEngineCount() {
        return engines.size();
    }

    public TableService(
            PokerTableRepository tableRepository,
            UserRepository userRepository,
            TableProperties tableProperties,
            ApplicationEventPublisher events,
            WalletService walletService,
            StakeEscrowService stakeEscrowService
    ) {
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.tableProperties = tableProperties;
        this.events = events;
        this.walletService = walletService;
        this.stakeEscrowService = stakeEscrowService;
    }

    public List<TableSummaryResponse> listTables() {
        return tableRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TableMapper::toSummary)
                .toList();
    }

    public java.util.Optional<UUID> seatedTableId(UUID userId) {
        return tableRepository.findCashTablesByPlayersUserId(userId).stream().findFirst().map(PokerTable::getId);
    }

    public TableDetailResponse createTable(UUID userId, CreateTableRequest request) {
        if (draining.get()) {
            throw new InvalidGameActionException("Server is currently draining for maintenance; no new tables can be created.");
        }
        User creator = requireUser(userId);
        String variant = request.variant() == null || request.variant().isBlank()
                ? TexasHoldemVariant.NAME
                : request.variant().trim().toUpperCase();
        PokerVariant pokerVariant;
        try {
            pokerVariant = Variants.create(variant);
        } catch (IllegalArgumentException ex) {
            throw new InvalidGameActionException(ex.getMessage());
        }

        int smallBlind = valueOr(request.smallBlind(), tableProperties.getSmallBlind());
        int bigBlind = valueOr(request.bigBlind(), tableProperties.getBigBlind());
        int maxPlayers = valueOr(request.maxPlayers(), tableProperties.getMaxPlayers());
        int startingChips = valueOr(request.startingChips(), tableProperties.getStartingChips());
        int maxDrawCount = tableProperties.getMaxDrawCount();
        TableConfig config;
        try {
            config = new TableConfig(smallBlind, bigBlind, startingChips, maxPlayers, maxDrawCount);
        } catch (IllegalArgumentException ex) {
            throw new InvalidGameActionException(ex.getMessage());
        }

        boolean privateRoom = Boolean.TRUE.equals(request.privateRoom());
        String roomCode = privateRoom ? allocateRoomCode() : null;
        PokerTable table = tableRepository.save(new PokerTable(
                request.name().trim(),
                pokerVariant.getName(),
                config.smallBlind(),
                config.bigBlind(),
                config.maxPlayers(),
                config.startingChips(),
                creator,
                roomCode,
                privateRoom
        ));
        engines.put(table.getId(), new TexasHoldemEngine(table.getId().toString(), config, pokerVariant));
        return TableMapper.toDetail(table);
    }

    public TableDetailResponse getTable(UUID tableId) {
        return TableMapper.toDetail(requireTable(tableId));
    }

    public TableDetailResponse joinByRoomCode(UUID userId, String rawCode) {
        String code = normalizeRoomCode(rawCode);
        PokerTable table = tableRepository.findByRoomCodeIgnoreCase(code)
                .orElseThrow(() -> new InvalidGameActionException("No room found for code " + code + "."));
        if (table.isTournament() || !table.isPrivateRoom()) {
            throw new InvalidGameActionException("That table is not joinable by invite code.");
        }
        if (table.getStatus() != com.poker.entity.TableStatus.WAITING
                && table.getStatus() != com.poker.entity.TableStatus.PLAYING) {
            throw new InvalidGameActionException("This room is no longer accepting players.");
        }
        if (table.getPlayers().size() >= table.getMaxPlayers()
                && findSeated(table, userId).isEmpty()) {
            throw new InvalidGameActionException("This room is full.");
        }
        if (findSeated(table, userId).isPresent()) {
            return TableMapper.toDetail(table);
        }
        return joinTable(table.getId(), userId, new JoinTableRequest(null, null));
    }

    private String allocateRoomCode() {
        for (int attempt = 0; attempt < 24; attempt++) {
            String code = randomRoomCode();
            if (!tableRepository.existsByRoomCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new InvalidGameActionException("Could not allocate a room code. Try again.");
    }

    private static String randomRoomCode() {
        // Avoid ambiguous 0/O/1/I characters for easy sharing.
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(6);
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            sb.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String normalizeRoomCode(String raw) {
        if (raw == null) {
            throw new InvalidGameActionException("Room code is required.");
        }
        String code = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (code.length() < 4 || code.length() > 12) {
            throw new InvalidGameActionException("Enter a valid room code.");
        }
        return code;
    }

    public TableDetailResponse joinTable(UUID tableId, UUID userId, JoinTableRequest request) {
        synchronized (lockFor(tableId)) {
            PokerTable table = requireTable(tableId);
            User user = requireUser(userId);
            if (findSeated(table, userId).isPresent()) {
                throw new InvalidGameActionException("You are already seated at this table.");
            }
            var otherTableId = seatedTableId(userId);
            if (otherTableId.isPresent() && !otherTableId.get().equals(tableId)) {
                try {
                    leaveTable(otherTableId.get(), userId);
                } catch (RuntimeException ignored) {
                }
            }

            boolean bot = BotService.isBot(user);
            TexasHoldemEngine engine = engineFor(table);
            if (!bot) {
                assertCanWager(userId);
                if (engine.state().isHandInProgress() || engine.state().isBettingActive()
                        || engine.state().getStatus() == GameStatus.PAYOUT) {
                    throw GameActionException.alreadyStarted();
                }
                if (table.getPlayers().size() >= table.getMaxPlayers()) {
                    throw GameActionException.seatUnavailable();
                }
                if (request != null && request.seat() != null) {
                    int seat = request.seat();
                    if (seat < 0 || seat >= table.getMaxPlayers() || engine.state().playerAt(seat) != null) {
                        throw GameActionException.seatUnavailable();
                    }
                }
            }

            if (otherTableId.isPresent() && !otherTableId.get().equals(tableId)) {
                try {
                    leaveTable(otherTableId.get(), userId);
                } catch (ApiException ex) {
                    if (ErrorCodes.WALLET_UNAVAILABLE.equals(ex.getCode())
                            || "OPERATOR_SESSION_EXPIRED".equals(ex.getCode())) {
                        throw ex;
                    }
                } catch (RuntimeException ignored) {
                }
            }

            String escrowId = request != null ? request.prepaidEscrowId() : null;
            boolean reservedHere = !bot && (escrowId == null || escrowId.isBlank());
            if (!bot) {
                if (reservedHere) {
                    escrowId = stakeEscrowService.reserveEntry(
                            userId.toString(),
                            table.getStartingChips(),
                            "Table buy-in"
                    );
                }
                stakeEscrowService.assignToTable(escrowId, tableId.toString(), null);
                seatEscrows.put(seatKey(tableId, userId), escrowId);
            }

            TexasHoldemEngine.Snapshot beforeSit = engine.snapshot();
            List<PokerTablePlayer> playersBeforeSit = table.snapshotPlayers();
            TableStatus statusBeforeSit = table.getStatus();
            try {
                if (request != null && request.seat() != null) {
                    engine.sitPlayer(userId.toString(), user.getDisplayName(), request.seat(), table.getStartingChips());
                } else {
                    engine.sitPlayer(userId.toString(), user.getDisplayName());
                }

                PlayerState seated = engine.state().player(userId.toString());
                PokerTablePlayer seatedPlayer = new PokerTablePlayer(user, seated.getSeat(), seated.getChipStack(), escrowId);
                table.addPlayer(seatedPlayer);
                tableRepository.save(table);
            } catch (RuntimeException ex) {
                engines.put(tableId, TexasHoldemEngine.restore(beforeSit));
                table.restoreSnapshot(statusBeforeSit, playersBeforeSit);
                seatEscrows.remove(seatKey(tableId, userId));
                if (reservedHere && escrowId != null) {
                    try {
                        stakeEscrowService.refundStake(escrowId, "Join failed, entry returned");
                    } catch (RuntimeException refundEx) {
                        log.warn("Refund after failed join {} failed: {}", escrowId, refundEx.getMessage());
                    }
                }
                throw ex;
            }

            // Private rooms: when the selected max seats are full, deal automatically.
            // Host can still start earlier once the poker minimum (2) is seated.
            if (table.isPrivateRoom()
                    && table.getPlayers().size() >= table.getMaxPlayers()
                    && engine.state().getHandNumber() == 0
                    && (engine.state().getStatus() == GameStatus.WAITING
                        || engine.state().getStatus() == GameStatus.NEXT_HAND)) {
                TexasHoldemEngine.Snapshot beforeAutoStart = engine.snapshot();
                List<PokerTablePlayer> playersBeforeAutoStart = table.snapshotPlayers();
                TableStatus statusBeforeAutoStart = table.getStatus();
                try {
                    removeDepartedEnginePlayers(table, engine);
                    if (evictExpiredOperatorPlayers(table, engine)) {
                        fundRebuys(table, engine);
                        if (engine.state().getPlayers().size() >= 2) {
                            engine.startHand();
                            syncStatusAndStacks(table, engine);
                            tableRepository.save(table);
                            publish(tableId, WsEventType.PLAYER_JOINED);
                            publish(tableId, WsEventType.HAND_STARTED);
                            return TableMapper.toDetail(requireTable(tableId));
                        }
                    }
                } catch (RuntimeException ex) {
                    engines.put(tableId, TexasHoldemEngine.restore(beforeAutoStart));
                    table.restoreSnapshot(statusBeforeAutoStart, playersBeforeAutoStart);
                    log.warn("Private room auto-start held at {} pending wallet confirmation: {}", tableId, ex.getMessage());
                }
            }

            TableDetailResponse detail = TableMapper.toDetail(requireTable(tableId));
            publish(tableId, WsEventType.PLAYER_JOINED);
            return detail;
        }
    }

    public TableDetailResponse leaveTable(UUID tableId, UUID userId) {
        synchronized (lockFor(tableId)) {
            PokerTable table = requireTable(tableId);
            PokerTablePlayer seated = findSeated(table, userId)
                    .orElseThrow(() -> new InvalidGameActionException("You are not seated at this table."));
            TexasHoldemEngine engine = engineFor(table);
            TexasHoldemEngine.Snapshot engineBefore = engine.snapshot();
            List<PokerTablePlayer> playersBefore = table.snapshotPlayers();
            TableStatus statusBefore = table.getStatus();
            String seatEscrowKey = seatKey(tableId, userId);
            String trackedEscrowBefore = seatEscrows.get(seatEscrowKey);

            try {
                PlayerState live = engine.state().player(userId.toString());
                int cashOut = live != null ? live.getChipStack() : seated.getChipStack();
                engine.abandonSeat(userId.toString());
                table.removePlayer(seated);
                String cashOutTranId = "CASHOUT_" + tableId + "_" + seated.getId();
                settleSeatWallet(tableId, userId, cashOut, BotService.isBot(seated), seated.getEscrowId(), cashOutTranId);
                syncStatusAndStacks(table, engine);
                tableRepository.save(table);
                TableDetailResponse detail = TableMapper.toDetail(requireTable(tableId));
                publish(tableId, WsEventType.PLAYER_LEFT);
                return detail;
            } catch (RuntimeException ex) {
                engines.put(tableId, TexasHoldemEngine.restore(engineBefore));
                table.restoreSnapshot(statusBefore, playersBefore);
                if (trackedEscrowBefore != null) {
                    seatEscrows.put(seatEscrowKey, trackedEscrowBefore);
                }
                throw ex;
            }
        }
    }

    public TableStateResponse startTable(UUID tableId, UUID userId) {
        synchronized (lockFor(tableId)) {
            PokerTable table = requireTable(tableId);
            if (findSeated(table, userId).isEmpty()) {
                throw new InvalidGameActionException("You must be seated to start the table.");
            }
            assertCanWager(userId);
            TexasHoldemEngine engine = engineFor(table);
            TexasHoldemEngine.Snapshot engineBefore = engine.snapshot();
            List<PokerTablePlayer> playersBefore = table.snapshotPlayers();
            TableStatus statusBefore = table.getStatus();
            try {
                removeDepartedEnginePlayers(table, engine);
                if (!evictExpiredOperatorPlayers(table, engine)) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "OPERATOR_SESSION_EXPIRED",
                            "A seated operator session has expired and cannot wager.");
                }
                fundRebuys(table, engine);
                if (table.isPrivateRoom()) {
                    if (!userId.equals(table.getCreatedById())) {
                        throw new InvalidGameActionException("Only the host can start this room.");
                    }
                    if (engine.state().getPlayers().size() < 2) {
                        throw new InvalidGameActionException("Need at least 2 players to start.");
                    }
                }
                engine.startHand();
                syncStatusAndStacks(table, engine);
                tableRepository.save(table);
                TableStateResponse state = TableMapper.toSanitizedState(table, engine, userId, syncTurnTimer(table, engine));
                publish(tableId, WsEventType.HAND_STARTED);
                return state;
            } catch (RuntimeException ex) {
                engines.put(tableId, TexasHoldemEngine.restore(engineBefore));
                table.restoreSnapshot(statusBefore, playersBefore);
                throw ex;
            }
        }
    }

    public boolean maybeStartNextHand(UUID tableId) {
        synchronized (lockFor(tableId)) {
            PokerTable table = tableRepository.findById(tableId).orElse(null);
            if (table == null || table.isTournament()) {
                return false;
            }
            TexasHoldemEngine engine = engineFor(table);
            GameStatus status = engine.state().getStatus();
            if (status != GameStatus.WAITING && status != GameStatus.NEXT_HAND) {
                return false;
            }
            TexasHoldemEngine.Snapshot engineBefore = engine.snapshot();
            List<PokerTablePlayer> playersBefore = table.snapshotPlayers();
            TableStatus statusBefore = table.getStatus();
            try {
                removeDepartedEnginePlayers(table, engine);
                if (!evictExpiredOperatorPlayers(table, engine)) {
                    engine.state().setStatus(GameStatus.WAITING);
                    syncStatusAndStacks(table, engine);
                    tableRepository.save(table);
                    return false;
                }
                fundRebuys(table, engine);
                if (engine.state().getPlayers().size() < 2) {
                    engine.state().setStatus(GameStatus.WAITING);
                    syncStatusAndStacks(table, engine);
                    tableRepository.save(table);
                    return false;
                }
                engine.startHand();
                syncStatusAndStacks(table, engine);
                tableRepository.save(table);
                syncTurnTimer(table, engine);
                publish(tableId, WsEventType.HAND_STARTED);
                return true;
            } catch (RuntimeException ex) {
                engines.put(tableId, TexasHoldemEngine.restore(engineBefore));
                table.restoreSnapshot(statusBefore, playersBefore);
                log.debug("Auto-start next hand held for {}: {}", tableId, ex.getMessage());
                return false;
            }
        }
    }

    /** Removes expired-session players before dealing; returns false if any expired player with chips could not cash out. */
    private boolean evictExpiredOperatorPlayers(PokerTable table, TexasHoldemEngine engine) {
        List<PokerTablePlayer> blocked = new ArrayList<>();
        for (PokerTablePlayer seated : table.getPlayers()) {
            if (!BotService.isBot(seated) && walletService.isOperatorWageringBlocked(seated.getUserId().toString())) {
                blocked.add(seated);
            }
        }
        boolean allCleared = true;
        for (PokerTablePlayer seated : blocked) {
            PlayerState live = engine.state().player(seated.getUserId().toString());
            int cashOut = live != null ? live.getChipStack() : seated.getChipStack();
            String cashOutTranId = "CASHOUT_" + table.getId() + "_" + seated.getId();
            try {
                if (cashOut > 0) {
                    settleSeatWallet(table.getId(), seated.getUserId(), cashOut, false, seated.getEscrowId(), cashOutTranId);
                } else {
                    settleSeatWallet(table.getId(), seated.getUserId(), 0, false, seated.getEscrowId(), cashOutTranId);
                }
                engine.abandonSeat(seated.getUserId().toString());
                table.removePlayer(seated);
                publish(table.getId(), WsEventType.PLAYER_LEFT);
            } catch (RuntimeException ex) {
                log.warn("Expired operator player {} could not be cashed out yet at {}: {}", seated.getUsername(), table.getId(), ex.getMessage());
                allCleared = false;
            }
        }
        return allCleared;
    }

    private void fundRebuys(PokerTable table, TexasHoldemEngine engine) {
        int buyIn = table.getStartingChips();
        List<PokerTablePlayer> toRemove = new ArrayList<>();
        for (PokerTablePlayer seated : table.getPlayers()) {
            PlayerState live = engine.state().player(seated.getUserId().toString());
            if (live == null || live.getChipStack() > 0) {
                continue;
            }
            if (BotService.isBot(seated)) {
                engine.rebuy(seated.getUserId().toString(), buyIn, live.getUsername() + " received a house rebuy");
                continue;
            }
            String rebuyTranId = "REBUY_" + table.getId() + "_" + seated.getUserId() + "_" + engine.state().getHandNumber();
            try {
                String escrowId = stakeEscrowService.reserveRebuy(
                        seated.getUserId().toString(),
                        buyIn,
                        table.getId().toString(),
                        rebuyTranId
                );
                stakeEscrowService.assignToTable(escrowId, table.getId().toString(), null);
                seatEscrows.put(seatKey(table.getId(), seated.getUserId()), escrowId);
                seated.setEscrowId(escrowId);
                engine.rebuy(seated.getUserId().toString(), buyIn, live.getUsername() + " rebought for ₹" + buyIn);
            } catch (InsufficientBalanceException ex) {
                log.info("Player {} cannot rebuy at {}: {}", seated.getUsername(), table.getId(), ex.getMessage());
                toRemove.add(seated);
            } catch (ApiException ex) {
                if ("OPERATOR_SESSION_EXPIRED".equals(ex.getCode())) {
                    log.info("Removing player {} from {} after expired session rebuy rejection", seated.getUsername(), table.getId());
                    toRemove.add(seated);
                } else {
                    throw ex;
                }
            }
        }
        for (PokerTablePlayer seated : toRemove) {
            int cashOut = 0;
            engine.abandonSeat(seated.getUserId().toString());
            table.removePlayer(seated);
            settleSeatWallet(table.getId(), seated.getUserId(), cashOut, false, seated.getEscrowId(), "CASHOUT_" + table.getId() + "_" + seated.getId());
            publish(table.getId(), WsEventType.PLAYER_LEFT);
        }
    }

    private void settleSeatWallet(UUID tableId, UUID userId, int cashOutChips, boolean bot, String knownEscrowId, String cashOutTranId) {
        if (bot) {
            seatEscrows.remove(seatKey(tableId, userId));
            return;
        }
        if (cashOutChips > 0) {
            walletService.credit(
                    userId.toString(),
                    BigDecimal.valueOf(cashOutChips),
                    WalletService.TX_TABLE_CASH_OUT,
                    cashOutTranId,
                    tableId.toString(),
                    "Table cash-out",
                    Map.of("tableId", tableId.toString(), "chips", cashOutChips)
            );
        }
        seatEscrows.remove(seatKey(tableId, userId));
        // Close every OPEN escrow for this seat (buy-in + rebuys) only after wallet confirmation.
        for (var escrow : stakeEscrowService.findOpenForPlayer(userId.toString())) {
            boolean sameTable = tableId.toString().equals(escrow.getTableId());
            boolean known = knownEscrowId != null && knownEscrowId.equals(escrow.getId());
            if (sameTable || known) {
                closeEscrowSettled(escrow.getId());
            }
        }
    }

    private void closeEscrowSettled(String escrowId) {
        if (escrowId == null) {
            return;
        }
        if (stakeEscrowService.beginSettlement(escrowId) || stakeEscrowService.find(escrowId).isPresent()) {
            stakeEscrowService.completeSettlement(escrowId);
        }
    }

    /** Refunds OPEN escrows whose player is no longer seated (restart / abandoned tables). */
    public int recoverOrphanEscrows() {
        var seatedKeys = tableRepository.findAllByOrderByCreatedAtDesc().stream()
                .flatMap(t -> t.getPlayers().stream().map(p -> t.getId() + ":" + p.getUserId()))
                .collect(java.util.stream.Collectors.toSet());
        int refunded = 0;
        for (var escrow : stakeEscrowService.findAllOpen()) {
            String seat = escrow.getTableId() + ":" + escrow.getPlayerId();
            boolean seated = escrow.getTableId() != null && seatedKeys.contains(seat);
            if (!seated) {
                try {
                    if (stakeEscrowService.refundStake(escrow.getId(), "Orphan seat recovered, entry returned")) {
                        refunded++;
                    }
                } catch (RuntimeException ex) {
                    log.warn("Orphan escrow refund failed for {}: {}", escrow.getId(), ex.getMessage());
                }
            }
        }
        if (refunded > 0) {
            log.info("Recovered {} orphan stake escrow(s)", refunded);
        }
        return refunded;
    }

    private static String seatKey(UUID tableId, UUID userId) {
        return tableId + ":" + userId;
    }

    public TableStateResponse getState(UUID tableId, UUID userId) {
        PokerTable table = requireTable(tableId);
        requireUser(userId);
        synchronized (lockFor(tableId)) {
            TexasHoldemEngine engine = engineFor(table);
            return TableMapper.toSanitizedState(table, engine, userId, syncTurnTimer(table, engine));
        }
    }

    /** When the player to act must move by; empty when nobody is on the clock. */
    public Optional<Instant> turnDeadline(UUID tableId) {
        synchronized (lockFor(tableId)) {
            PokerTable table = tableRepository.findById(tableId).orElse(null);
            if (table == null || table.isTournament()) {
                turnTimers.remove(tableId);
                return Optional.empty();
            }
            return Optional.ofNullable(syncTurnTimer(table, engineFor(table)));
        }
    }

    /**
     * Acts for a player whose turn clock ran out: check when free, otherwise fold
     * (or keep every card in a draw / throw the first card in Pineapple).
     */
    public boolean enforceTurnTimeout(UUID tableId) {
        synchronized (lockFor(tableId)) {
            PokerTable table = tableRepository.findById(tableId).orElse(null);
            TurnTimer timer = turnTimers.get(tableId);
            if (table == null || table.isTournament() || timer == null || Instant.now().isBefore(timer.deadline())) {
                return false;
            }
            TexasHoldemEngine engine = engineFor(table);
            GameState state = engine.state();
            PlayerState actor = state.currentPlayer();
            if (actor == null || !timer.signature().equals(turnSignature(state))) {
                return false;
            }
            PlayerAction action = timeoutAction(actor.getPlayerId(), engine.legalActions(actor.getPlayerId()), state);
            if (action == null) {
                return false;
            }
            GameStatus before = state.getStatus();
            int communityBefore = state.getCommunityCards().size();
            try {
                engine.apply(action);
            } catch (RuntimeException ex) {
                log.warn("Turn timeout action failed at {}: {}", tableId, ex.getMessage());
                turnTimers.remove(tableId);
                return false;
            }
            syncStatusAndStacks(table, engine);
            tableRepository.save(table);
            syncTurnTimer(table, engine);
            publish(tableId, eventAfterAction(before, engine, communityBefore));
            return true;
        }
    }

    private static PlayerAction timeoutAction(String playerId, LegalActions legal, GameState state) {
        if (legal.allows(ActionType.CHECK)) {
            return PlayerAction.check(playerId);
        }
        if (legal.allows(ActionType.DISCARD)) {
            return state.isDrawActive() ? PlayerAction.draw(playerId, List.of()) : PlayerAction.discard(playerId, 0);
        }
        if (legal.allows(ActionType.FOLD)) {
            return PlayerAction.fold(playerId);
        }
        return null;
    }

    private Instant syncTurnTimer(PokerTable table, TexasHoldemEngine engine) {
        GameState state = engine.state();
        boolean waitingOnPlayer = state.isBettingActive() || state.isDiscardActive() || state.isDrawActive();
        if (!waitingOnPlayer || state.currentPlayer() == null) {
            turnTimers.remove(table.getId());
            return null;
        }
        String signature = turnSignature(state);
        TurnTimer current = turnTimers.get(table.getId());
        if (current != null && current.signature().equals(signature)) {
            return current.deadline();
        }
        long dealLeadMs = current == null || current.handNumber() != state.getHandNumber()
                ? DealTiming.dealAnimationMs(state.getPlayers().size(), Variants.create(table.getVariant()).getInitialHoleCards())
                : 0;
        Instant deadline = Instant.now().plusMillis(dealLeadMs + tableProperties.getTurnSeconds() * 1000L);
        turnTimers.put(table.getId(), new TurnTimer(signature, state.getHandNumber(), deadline));
        return deadline;
    }

    private static String turnSignature(GameState state) {
        return state.getHandNumber() + ":" + state.getCurrentTurnSeat() + ":" + state.getStatus() + ":" + state.getEvents().size();
    }

    public TableStateResponse applyAction(UUID tableId, UUID userId, GameActionRequest request) {
        synchronized (lockFor(tableId)) {
            PokerTable table = requireTable(tableId);
            User actor = requireUser(userId);
            if (findSeated(table, userId).isEmpty()) {
                throw new InvalidGameActionException("You must be seated to act.");
            }
            PlayerAction action = toPlayerAction(userId, request);
            if (!BotService.isBot(actor)) {
                if (walletService.isOperatorWageringBlocked(userId.toString()) && action.type() != ActionType.FOLD) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "OPERATOR_SESSION_EXPIRED",
                            "This game session has ended. Return to your operator lobby.");
                }
                if (isWageringAction(action.type()) && walletService.hasPendingOperatorTransfer(userId.toString())) {
                    walletService.recoverPendingOperatorTransfers(userId.toString());
                }
            }
            String requestKey = null;
            if (request.requestId() != null && !request.requestId().isBlank()) {
                requestKey = tableId + ":" + userId + ":" + request.requestId().trim();
                if (processedRequestIds.contains(requestKey)) {
                    TexasHoldemEngine engine = engineFor(table);
                    return TableMapper.toSanitizedState(table, engine, userId, syncTurnTimer(table, engine));
                }
            }
            TexasHoldemEngine engine = engineFor(table);
            TexasHoldemEngine.Snapshot engineBefore = engine.snapshot();
            List<PokerTablePlayer> playersBefore = table.snapshotPlayers();
            TableStatus statusBefore = table.getStatus();
            GameStatus before = engine.state().getStatus();
            int communityBefore = engine.state().getCommunityCards().size();
            try {
                engine.apply(action);
                syncStatusAndStacks(table, engine);
                tableRepository.save(table);
            } catch (RuntimeException ex) {
                engines.put(tableId, TexasHoldemEngine.restore(engineBefore));
                table.restoreSnapshot(statusBefore, playersBefore);
                throw ex;
            }
            if (requestKey != null) {
                rememberRequestId(requestKey);
            }
            TableStateResponse state = TableMapper.toSanitizedState(table, engine, userId, syncTurnTimer(table, engine));
            publish(tableId, eventAfterAction(before, engine, communityBefore));
            return state;
        }
    }

    private void assertCanWager(UUID userId) {
        String playerId = userId.toString();
        if (walletService.isOperatorWageringBlocked(playerId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OPERATOR_SESSION_EXPIRED",
                    "This game session has ended. Return to your operator lobby.");
        }
        if (walletService.hasPendingOperatorTransfer(playerId)) {
            walletService.recoverPendingOperatorTransfers(playerId);
        }
    }

    private static boolean isWageringAction(ActionType type) {
        return type == ActionType.BET || type == ActionType.RAISE
                || type == ActionType.CALL || type == ActionType.ALL_IN;
    }

    private void rememberRequestId(String key) {
        if (!processedRequestIds.add(key)) {
            return;
        }
        processedRequestIdOrder.add(key);
        while (processedRequestIds.size() > MAX_PROCESSED_REQUEST_IDS) {
            String oldest = processedRequestIdOrder.poll();
            if (oldest == null) {
                break;
            }
            processedRequestIds.remove(oldest);
        }
    }

    public boolean markConnected(UUID tableId, UUID userId, boolean connected) {
        synchronized (lockFor(tableId)) {
            PokerTable table = tableRepository.findById(tableId).orElse(null);
            if (table == null || table.isTournament()) {
                return false;
            }
            TexasHoldemEngine engine = engineFor(table);
            PlayerState live = engine.state().player(userId.toString());
            boolean reconnect = connected && live != null && !live.isConnected();
            engine.setConnected(userId.toString(), connected);
            return reconnect;
        }
    }

    public List<UUID> seatedUserIds(UUID tableId) {
        PokerTable table = tableRepository.findById(tableId).orElse(null);
        if (table == null || table.isTournament()) {
            return List.of();
        }
        return table.getPlayers().stream()
                .map(PokerTablePlayer::getUserId)
                .toList();
    }

    /** Live engine for bot decision-making (caller must not mutate except via applyAction). */
    public TexasHoldemEngine engineSnapshot(UUID tableId) {
        synchronized (lockFor(tableId)) {
            return engineFor(requireTable(tableId));
        }
    }

    private void publish(UUID tableId, WsEventType type) {
        events.publishEvent(new TableUpdatedEvent(tableId, type));
    }

    private static PlayerAction toPlayerAction(UUID userId, GameActionRequest request) {
        ActionType type;
        try {
            type = ActionType.valueOf(request.action().trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ex) {
            throw new InvalidGameActionException("Unknown action.");
        }
        int amount = request.amount() == null ? 0 : request.amount();
        List<Integer> indices = request.indices() == null ? List.of() : List.copyOf(request.indices());
        return new PlayerAction(userId.toString(), type, amount, indices);
    }

    private static WsEventType eventAfterAction(GameStatus before, TexasHoldemEngine engine, int communityBefore) {
        GameStatus after = engine.state().getStatus();
        int communityAfter = engine.state().getCommunityCards().size();
        if (!engine.state().getShowdown().isEmpty() && before != GameStatus.SHOWDOWN && after != GameStatus.PRE_FLOP) {
            return WsEventType.SHOWDOWN;
        }
        if (after == GameStatus.PLAYER_WON_BY_FOLD || after == GameStatus.PAYOUT || after == GameStatus.NEXT_HAND) {
            return WsEventType.HAND_COMPLETED;
        }
        if (communityAfter > communityBefore) {
            return WsEventType.COMMUNITY_CARDS;
        }
        return WsEventType.PLAYER_ACTION;
    }

    private TexasHoldemEngine engineFor(PokerTable table) {
        return engines.computeIfAbsent(table.getId(), id -> {
            TableConfig config = new TableConfig(
                    table.getSmallBlind(),
                    table.getBigBlind(),
                    table.getStartingChips(),
                    table.getMaxPlayers(),
                    tableProperties.getMaxDrawCount()
            );
            TexasHoldemEngine engine = new TexasHoldemEngine(id.toString(), config, Variants.create(table.getVariant()));
            for (PokerTablePlayer player : table.getPlayers()) {
                engine.sitPlayer(
                        player.getUserId().toString(),
                        player.getUsername(),
                        player.getSeat(),
                        Math.max(player.getChipStack(), table.getStartingChips())
                );
            }
            return engine;
        });
    }

    private void syncStatusAndStacks(PokerTable table, TexasHoldemEngine engine) {
        table.setStatus(TableMapper.lobbyStatus(engine.state().getStatus()));
        for (PokerTablePlayer seated : table.getPlayers()) {
            PlayerState live = engine.state().player(seated.getUserId().toString());
            if (live != null) {
                seated.setChipStack(live.getChipStack());
            }
        }
    }

    private void removeDepartedEnginePlayers(PokerTable table, TexasHoldemEngine engine) {
        Set<String> seatedIds = table.getPlayers().stream()
                .map(player -> player.getUserId().toString())
                .collect(java.util.stream.Collectors.toSet());
        List<String> departedIds = engine.state().getPlayers().stream()
                .map(PlayerState::getPlayerId)
                .filter(playerId -> !seatedIds.contains(playerId))
                .toList();
        departedIds.forEach(engine::abandonSeat);
    }

    private static java.util.Optional<PokerTablePlayer> findSeated(PokerTable table, UUID userId) {
        return table.getPlayers().stream()
                .filter(player -> player.getUserId().equals(userId))
                .findFirst();
    }

    private PokerTable requireTable(UUID tableId) {
        return tableRepository.findById(tableId)
                .filter(table -> !table.isTournament())
                .orElseThrow(() -> new GameNotFoundException(tableId));
    }

    public boolean isTournamentTable(UUID id) {
        return tableRepository.findById(id).filter(PokerTable::isTournament).isPresent();
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new PlayerNotFoundException(userId));
    }

    private Object lockFor(UUID tableId) {
        return locks.computeIfAbsent(tableId, id -> new Object());
    }

    private static int valueOr(Integer value, int fallback) {
        return value != null ? value : fallback;
    }
}
