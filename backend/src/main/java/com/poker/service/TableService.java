package com.poker.service;

import com.poker.config.TableProperties;
import com.poker.dto.CreateTableRequest;
import com.poker.dto.GameActionRequest;
import com.poker.dto.JoinTableRequest;
import com.poker.dto.TableDetailResponse;
import com.poker.dto.TableStateResponse;
import com.poker.dto.TableSummaryResponse;
import com.poker.entity.PokerTable;
import com.poker.entity.PokerTablePlayer;
import com.poker.entity.User;
import com.poker.exception.GameNotFoundException;
import com.poker.exception.InvalidGameActionException;
import com.poker.exception.PlayerNotFoundException;
import com.poker.game.engine.ActionType;
import com.poker.game.engine.GameStatus;
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
import com.poker.websocket.TableUpdatedEvent;
import com.poker.websocket.WsEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class TableService {

    private static final Logger log = LoggerFactory.getLogger(TableService.class);
    private static final int MAX_PROCESSED_REQUEST_IDS = 10_000;

    private final PokerTableRepository tableRepository;
    private final UserRepository userRepository;
    private final TableProperties tableProperties;
    private final ApplicationEventPublisher events;
    private final Map<UUID, TexasHoldemEngine> engines = new ConcurrentHashMap<>();
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();
    private final Set<String> processedRequestIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<String> processedRequestIdOrder = new ConcurrentLinkedQueue<>();

    public TableService(
            PokerTableRepository tableRepository,
            UserRepository userRepository,
            TableProperties tableProperties,
            ApplicationEventPublisher events
    ) {
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.tableProperties = tableProperties;
        this.events = events;
    }

    public List<TableSummaryResponse> listTables() {
        return tableRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TableMapper::toSummary)
                .toList();
    }

    public java.util.Optional<UUID> seatedTableId(UUID userId) {
        return tableRepository.findFirstByPlayersUserId(userId).map(PokerTable::getId);
    }

    public TableDetailResponse createTable(UUID userId, CreateTableRequest request) {
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

        PokerTable table = tableRepository.save(new PokerTable(
                request.name().trim(),
                pokerVariant.getName(),
                config.smallBlind(),
                config.bigBlind(),
                config.maxPlayers(),
                config.startingChips(),
                creator
        ));
        engines.put(table.getId(), new TexasHoldemEngine(table.getId().toString(), config, pokerVariant));
        return TableMapper.toDetail(table);
    }

    public TableDetailResponse getTable(UUID tableId) {
        return TableMapper.toDetail(requireTable(tableId));
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

            TexasHoldemEngine engine = engineFor(table);
            if (request != null && request.seat() != null) {
                engine.sitPlayer(userId.toString(), user.getUsername(), request.seat(), table.getStartingChips());
            } else {
                engine.sitPlayer(userId.toString(), user.getUsername());
            }

            PlayerState seated = engine.state().player(userId.toString());
            PokerTablePlayer seatedPlayer = new PokerTablePlayer(user, seated.getSeat(), seated.getChipStack());
            table.addPlayer(seatedPlayer);
            tableRepository.save(table);
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
            engine.abandonSeat(userId.toString());
            table.removePlayer(seated);
            syncStatusAndStacks(table, engine);
            tableRepository.save(table);
            TableDetailResponse detail = TableMapper.toDetail(requireTable(tableId));
            publish(tableId, WsEventType.PLAYER_LEFT);
            return detail;
        }
    }

    public TableStateResponse startTable(UUID tableId, UUID userId) {
        synchronized (lockFor(tableId)) {
            PokerTable table = requireTable(tableId);
            if (findSeated(table, userId).isEmpty()) {
                throw new InvalidGameActionException("You must be seated to start the table.");
            }
            TexasHoldemEngine engine = engineFor(table);
            removeDepartedEnginePlayers(table, engine);
            engine.startHand();
            syncStatusAndStacks(table, engine);
            tableRepository.save(table);
            TableStateResponse state = TableMapper.toSanitizedState(table, engine, userId);
            publish(tableId, WsEventType.HAND_STARTED);
            return state;
        }
    }

    public boolean maybeStartNextHand(UUID tableId) {
        synchronized (lockFor(tableId)) {
            PokerTable table = tableRepository.findById(tableId).orElse(null);
            if (table == null) {
                return false;
            }
            TexasHoldemEngine engine = engineFor(table);
            GameStatus status = engine.state().getStatus();
            if (status != GameStatus.WAITING && status != GameStatus.NEXT_HAND) {
                return false;
            }
            removeDepartedEnginePlayers(table, engine);
            if (engine.state().getPlayers().size() < 2) {
                engine.state().setStatus(GameStatus.WAITING);
                syncStatusAndStacks(table, engine);
                tableRepository.save(table);
                return false;
            }
            try {
                engine.startHand();
                syncStatusAndStacks(table, engine);
                tableRepository.save(table);
                publish(tableId, WsEventType.HAND_STARTED);
                return true;
            } catch (RuntimeException ex) {
                log.debug("Auto-start next hand skipped for {}: {}", tableId, ex.getMessage());
                return false;
            }
        }
    }

    public TableStateResponse getState(UUID tableId, UUID userId) {
        PokerTable table = requireTable(tableId);
        requireUser(userId);
        synchronized (lockFor(tableId)) {
            TexasHoldemEngine engine = engineFor(table);
            return TableMapper.toSanitizedState(table, engine, userId);
        }
    }

    public TableStateResponse applyAction(UUID tableId, UUID userId, GameActionRequest request) {
        synchronized (lockFor(tableId)) {
            PokerTable table = requireTable(tableId);
            requireUser(userId);
            if (findSeated(table, userId).isEmpty()) {
                throw new InvalidGameActionException("You must be seated to act.");
            }
            String requestKey = null;
            if (request.requestId() != null && !request.requestId().isBlank()) {
                requestKey = tableId + ":" + userId + ":" + request.requestId().trim();
                if (processedRequestIds.contains(requestKey)) {
                    return TableMapper.toSanitizedState(table, engineFor(table), userId);
                }
            }
            TexasHoldemEngine engine = engineFor(table);
            GameStatus before = engine.state().getStatus();
            int communityBefore = engine.state().getCommunityCards().size();
            engine.apply(toPlayerAction(userId, request));
            if (requestKey != null) {
                rememberRequestId(requestKey);
            }
            syncStatusAndStacks(table, engine);
            tableRepository.save(table);
            TableStateResponse state = TableMapper.toSanitizedState(table, engine, userId);
            publish(tableId, eventAfterAction(before, engine, communityBefore));
            return state;
        }
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
            if (table == null) {
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
        if (table == null) {
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
                .orElseThrow(() -> new GameNotFoundException(tableId));
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
