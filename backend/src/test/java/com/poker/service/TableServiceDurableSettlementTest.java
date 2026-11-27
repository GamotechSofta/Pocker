package com.poker.service;

import com.poker.config.TableProperties;
import com.poker.config.WalletProperties;
import com.poker.dto.CreateTableRequest;
import com.poker.dto.GameActionRequest;
import com.poker.dto.JoinTableRequest;
import com.poker.dto.TableDetailResponse;
import com.poker.dto.TableStateResponse;
import com.poker.entity.PokerTable;
import com.poker.entity.TableStatus;
import com.poker.entity.User;
import com.poker.exception.ApiException;
import com.poker.exception.ErrorCodes;
import com.poker.game.engine.GameStatus;
import com.poker.game.engine.PlayerState;
import com.poker.game.engine.TexasHoldemEngine;
import com.poker.operations.OperationsWalletClient;
import com.poker.operations.OperatorSession;
import com.poker.operations.OperatorSessionRepository;
import com.poker.operations.OperatorTransfer;
import com.poker.operations.OperatorTransferRepository;
import com.poker.operations.OperatorTransferService;
import com.poker.repository.PokerTableRepository;
import com.poker.repository.UserRepository;
import com.poker.wallet.StakeEscrow;
import com.poker.wallet.StakeEscrowRepository;
import com.poker.wallet.StakeEscrowService;
import com.poker.wallet.WalletAccount;
import com.poker.wallet.WalletAccountRepository;
import com.poker.wallet.WalletService;
import com.poker.wallet.WalletTransaction;
import com.poker.wallet.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TableServiceDurableSettlementTest {

    private final Map<UUID, PokerTable> tables = new ConcurrentHashMap<>();
    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    private final Map<String, WalletAccount> accounts = new ConcurrentHashMap<>();
    private final Map<String, WalletTransaction> walletTxs = new ConcurrentHashMap<>();
    private final Map<String, StakeEscrow> escrows = new ConcurrentHashMap<>();
    private final Map<String, OperatorSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, OperatorTransfer> opTransfers = new ConcurrentHashMap<>();

    private OperationsWalletClient operationsWalletClient;
    private OperatorTransferService operatorTransferService;
    private WalletService walletService;
    private StakeEscrowService stakeEscrowService;
    private TableService tableService;

    private User player1;
    private User player2;

    @BeforeEach
    void setUp() {
        tables.clear();
        users.clear();
        accounts.clear();
        walletTxs.clear();
        escrows.clear();
        sessions.clear();
        opTransfers.clear();

        PokerTableRepository tableRepo = mock(PokerTableRepository.class);
        when(tableRepo.save(any(PokerTable.class))).thenAnswer(inv -> {
            PokerTable t = inv.getArgument(0);
            t.ensureId();
            tables.put(t.getId(), t);
            return t;
        });
        when(tableRepo.findById(any(UUID.class))).thenAnswer(inv -> Optional.ofNullable(tables.get(inv.<UUID>getArgument(0))));
        when(tableRepo.findAllByOrderByCreatedAtDesc()).thenAnswer(inv -> new ArrayList<>(tables.values()));
        when(tableRepo.findCashTablesByPlayersUserId(any(UUID.class))).thenAnswer(inv -> {
            UUID uid = inv.getArgument(0);
            return tables.values().stream()
                    .filter(t -> !t.isTournament() && t.getPlayers().stream().anyMatch(p -> uid.equals(p.getUserId())))
                    .toList();
        });
        when(tableRepo.findByRoomCodeIgnoreCase(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return tables.values().stream().filter(t -> code.equalsIgnoreCase(t.getRoomCode())).findFirst();
        });
        when(tableRepo.existsByRoomCodeIgnoreCase(anyString())).thenReturn(false);

        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(any(UUID.class))).thenAnswer(inv -> Optional.ofNullable(users.get(inv.<UUID>getArgument(0))));

        WalletAccountRepository accountRepo = mock(WalletAccountRepository.class);
        when(accountRepo.findByPlayerId(anyString())).thenAnswer(inv -> Optional.ofNullable(accounts.get(inv.<String>getArgument(0))));
        when(accountRepo.insert(any(WalletAccount.class))).thenAnswer(inv -> {
            WalletAccount a = inv.getArgument(0);
            accounts.put(a.getPlayerId(), a);
            return a;
        });
        when(accountRepo.save(any(WalletAccount.class))).thenAnswer(inv -> {
            WalletAccount a = inv.getArgument(0);
            accounts.put(a.getPlayerId(), a);
            return a;
        });

        WalletTransactionRepository txRepo = mock(WalletTransactionRepository.class);
        when(txRepo.findByIdempotencyKey(anyString())).thenAnswer(inv -> Optional.ofNullable(walletTxs.get(inv.<String>getArgument(0))));
        when(txRepo.existsByIdempotencyKey(anyString())).thenAnswer(inv -> walletTxs.containsKey(inv.<String>getArgument(0)));
        when(txRepo.insert(any(WalletTransaction.class))).thenAnswer(inv -> {
            WalletTransaction tx = inv.getArgument(0);
            walletTxs.put(tx.getIdempotencyKey(), tx);
            return tx;
        });
        when(txRepo.save(any(WalletTransaction.class))).thenAnswer(inv -> {
            WalletTransaction tx = inv.getArgument(0);
            walletTxs.put(tx.getIdempotencyKey(), tx);
            return tx;
        });

        StakeEscrowRepository escrowRepo = mock(StakeEscrowRepository.class);
        when(escrowRepo.save(any(StakeEscrow.class))).thenAnswer(inv -> {
            StakeEscrow e = inv.getArgument(0);
            escrows.put(e.getId(), e);
            return e;
        });
        when(escrowRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(escrows.get(inv.<String>getArgument(0))));
        when(escrowRepo.findByIdAndStatus(anyString(), anyString())).thenAnswer(inv -> {
            StakeEscrow e = escrows.get(inv.<String>getArgument(0));
            return (e != null && inv.<String>getArgument(1).equals(e.getStatus())) ? Optional.of(e) : Optional.empty();
        });
        when(escrowRepo.findByPlayerIdAndStatus(anyString(), anyString())).thenAnswer(inv -> {
            String pid = inv.getArgument(0);
            String st = inv.getArgument(1);
            return escrows.values().stream().filter(e -> pid.equals(e.getPlayerId()) && st.equals(e.getStatus())).toList();
        });
        when(escrowRepo.findByTableIdAndStatus(anyString(), anyString())).thenAnswer(inv -> {
            String tid = inv.getArgument(0);
            String st = inv.getArgument(1);
            return escrows.values().stream().filter(e -> tid.equals(e.getTableId()) && st.equals(e.getStatus())).toList();
        });
        when(escrowRepo.findByStatus(anyString())).thenAnswer(inv -> {
            String st = inv.getArgument(0);
            return escrows.values().stream().filter(e -> st.equals(e.getStatus())).toList();
        });

        OperatorSessionRepository sessionRepo = mock(OperatorSessionRepository.class);
        when(sessionRepo.findByPlayerId(anyString())).thenAnswer(inv -> Optional.ofNullable(sessions.get(inv.<String>getArgument(0))));
        when(sessionRepo.save(any(OperatorSession.class))).thenAnswer(inv -> {
            OperatorSession s = inv.getArgument(0);
            sessions.put(s.getPlayerId(), s);
            return s;
        });

        OperatorTransferRepository transferRepo = mock(OperatorTransferRepository.class);
        when(transferRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(opTransfers.get(inv.<String>getArgument(0))));
        when(transferRepo.insert(any(OperatorTransfer.class))).thenAnswer(inv -> {
            OperatorTransfer t = inv.getArgument(0);
            opTransfers.put(t.getId(), t);
            return t;
        });
        when(transferRepo.save(any(OperatorTransfer.class))).thenAnswer(inv -> {
            OperatorTransfer t = inv.getArgument(0);
            opTransfers.put(t.getId(), t);
            return t;
        });
        when(transferRepo.findByPlayerIdAndStateOrderByCreatedAtAsc(anyString(), any(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    String pid = inv.getArgument(0);
                    OperatorTransfer.State st = inv.getArgument(1);
                    return opTransfers.values().stream()
                            .filter(t -> pid.equals(t.getPlayerId()) && t.getState() == st)
                            .toList();
                });

        operationsWalletClient = mock(OperationsWalletClient.class);
        operatorTransferService = new OperatorTransferService(transferRepo, operationsWalletClient, sessionRepo);
        walletService = new WalletService(accountRepo, txRepo, new WalletProperties(), sessionRepo, operationsWalletClient, operatorTransferService);
        stakeEscrowService = new StakeEscrowService(escrowRepo, walletService);

        TableProperties tableProps = new TableProperties();
        tableProps.setSmallBlind(10);
        tableProps.setBigBlind(20);
        tableProps.setStartingChips(200);
        tableProps.setMaxPlayers(6);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        tableService = new TableService(tableRepo, userRepo, tableProps, events, walletService, stakeEscrowService);

        player1 = new User(UUID.randomUUID(), "alice", "alice@operator.local", "hash");
        player2 = new User(UUID.randomUUID(), "bob", "bob@operator.local", "hash");
        users.put(player1.getId(), player1);
        users.put(player2.getId(), player2);

        sessions.put(player1.getId().toString(), new OperatorSession(
                player1.getId().toString(), "tok-alice", "DPBOSS", "42", "POKER_HOLDEM",
                "alice", "ext-alice", "INR", "en", null, null));
        sessions.put(player2.getId().toString(), new OperatorSession(
                player2.getId().toString(), "tok-bob", "DPBOSS", "42", "POKER_HOLDEM",
                "bob", "ext-bob", "INR", "en", null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEXAS_HOLDEM", "OMAHA", "PINEAPPLE", "FIVE_CARD_DRAW", "RAZZ"})
    void holdsFoldWinAndSeatExitUntilWalletConfirmsThenRetriesSameCashOutTranId(String variant) {
        AtomicBoolean failCashout = new AtomicBoolean(true);
        List<String> cashoutTranIds = new ArrayList<>();
        when(operationsWalletClient.transfer(
                anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> {
                    String tranId = inv.getArgument(6);
                    if (tranId.startsWith("CASHOUT_")) {
                        cashoutTranIds.add(tranId);
                        if (failCashout.get()) {
                            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.WALLET_UNAVAILABLE, "gateway down");
                        }
                    }
                    return new BigDecimal("1000");
                });

        TableDetailResponse created = tableService.createTable(
                player1.getId(),
                new CreateTableRequest("Table " + variant, variant, 10, 20, 6, 200, false));
        UUID tableId = created.id();

        tableService.joinTable(tableId, player1.getId(), new JoinTableRequest(0));
        tableService.joinTable(tableId, player2.getId(), new JoinTableRequest(1));
        tableService.startTable(tableId, player1.getId());

        TexasHoldemEngine beforeLeave = tableService.engineSnapshot(tableId);
        GameStatus activeStatus = beforeLeave.state().getStatus();
        assertNotEquals(GameStatus.WAITING, activeStatus);
        assertNotEquals(GameStatus.PLAYER_WON_BY_FOLD, activeStatus);

        UUID seatId = tables.get(tableId).getPlayers().stream()
                .filter(p -> player1.getId().equals(p.getUserId()))
                .findFirst().orElseThrow().getId();
        String expectedCashoutTranId = "CASHOUT_" + tableId + "_" + seatId;

        ApiException leaveError = assertThrows(ApiException.class, () -> tableService.leaveTable(tableId, player1.getId()));
        assertEquals(ErrorCodes.WALLET_UNAVAILABLE, leaveError.getCode());

        // Seat removal and fold-win state transition must be rolled back until wallet confirmation arrives
        assertEquals(2, tables.get(tableId).getPlayers().size());
        assertEquals(activeStatus, tableService.engineSnapshot(tableId).state().getStatus());
        assertEquals(OperatorTransfer.State.PENDING, opTransfers.get(expectedCashoutTranId).getState());

        // Now allow wallet confirmation and retry leaving -> must reuse exact same CASHOUT tranid
        failCashout.set(false);
        TableDetailResponse left = tableService.leaveTable(tableId, player1.getId());
        assertEquals(1, left.players().size());
        assertEquals(1, tables.get(tableId).getPlayers().size());
        assertEquals(OperatorTransfer.State.CONFIRMED, opTransfers.get(expectedCashoutTranId).getState());
        assertEquals(List.of(expectedCashoutTranId, expectedCashoutTranId), cashoutTranIds);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEXAS_HOLDEM", "OMAHA", "PINEAPPLE", "FIVE_CARD_DRAW", "RAZZ"})
    void blocksBettingActionsOnExpiredOperatorSessionWhileAllowingFold(String variant) {
        when(operationsWalletClient.transfer(
                anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new BigDecimal("1000"));

        UUID tableId = tableService.createTable(
                player1.getId(),
                new CreateTableRequest("Blocked " + variant, variant, 10, 20, 6, 200, false)).id();
        tableService.joinTable(tableId, player1.getId(), new JoinTableRequest(0));
        tableService.joinTable(tableId, player2.getId(), new JoinTableRequest(1));
        TableStateResponse started = tableService.startTable(tableId, player1.getId());
        int discSeq = 0;
        while (tableService.engineSnapshot(tableId).state().isDiscardActive()) {
            PlayerState discardActor = tableService.engineSnapshot(tableId).state().currentPlayer();
            started = tableService.applyAction(
                    tableId,
                    UUID.fromString(discardActor.getPlayerId()),
                    new GameActionRequest("DISCARD", null, List.of(0), "disc-" + (++discSeq)));
        }

        PlayerState current = tableService.engineSnapshot(tableId).state().currentPlayer();
        UUID actingUserId = UUID.fromString(current.getPlayerId());
        int actingBeforeChips = current.getChipStack();

        sessions.get(actingUserId.toString()).blockWagering();

        // Any wagering action (CALL/BET/RAISE/ALL_IN) must be rejected with OPERATOR_SESSION_EXPIRED and leave state intact
        var legal = started.legalActions().actions();
        String attemptedAction = legal.contains("CALL") ? "CALL"
                : legal.contains("RAISE") ? "RAISE"
                : legal.contains("BET") ? "BET" : "ALL_IN";
        Integer attemptedAmount = ("RAISE".equals(attemptedAction) || "BET".equals(attemptedAction))
                ? started.legalActions().minBetOrRaiseTo()
                : null;

        ApiException blocked = assertThrows(ApiException.class, () -> tableService.applyAction(
                tableId,
                actingUserId,
                new GameActionRequest(attemptedAction, attemptedAmount, null, "req-1")));
        assertEquals(HttpStatus.UNAUTHORIZED, blocked.getStatus());
        assertEquals("OPERATOR_SESSION_EXPIRED", blocked.getCode());
        assertEquals(actingBeforeChips, tableService.engineSnapshot(tableId).state().player(actingUserId.toString()).getChipStack());

        // Folding remains allowed so an expired seat cannot stall or deadlock the table
        TableStateResponse folded = tableService.applyAction(
                tableId, actingUserId, new GameActionRequest("FOLD", null, null, "req-fold"));
        assertTrue(folded.players().stream().anyMatch(p -> p.playerId().equals(actingUserId) && p.folded()));
    }

    @Test
    void holdsNextHandUntilRebuyWalletConfirmsAndRetriesSameRebuyTranId() {
        AtomicBoolean failRebuy = new AtomicBoolean(true);
        List<String> rebuyTranIds = new ArrayList<>();
        when(operationsWalletClient.transfer(
                anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> {
                    String tranId = inv.getArgument(6);
                    if (tranId.startsWith("REBUY_")) {
                        rebuyTranIds.add(tranId);
                        if (failRebuy.get()) {
                            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.WALLET_UNAVAILABLE, "rebuy pending");
                        }
                    }
                    return new BigDecimal("1000");
                });

        UUID tableId = tableService.createTable(
                player1.getId(),
                new CreateTableRequest("Rebuy Holdem", "TEXAS_HOLDEM", 10, 20, 6, 200, false)).id();
        tableService.joinTable(tableId, player1.getId(), new JoinTableRequest(0));
        tableService.joinTable(tableId, player2.getId(), new JoinTableRequest(1));
        tableService.startTable(tableId, player1.getId());

        // Drive an all-in showdown until one player reaches 0 chips (max 5 hands in case of exact split pot)
        for (int attempt = 0; attempt < 5; attempt++) {
            TexasHoldemEngine eng = tableService.engineSnapshot(tableId);
            if (eng.state().getStatus() == GameStatus.NEXT_HAND) {
                if (eng.state().getPlayers().stream().anyMatch(p -> p.getChipStack() == 0)) {
                    break;
                }
                assertTrue(tableService.maybeStartNextHand(tableId));
                eng = tableService.engineSnapshot(tableId);
            }
            UUID firstActor = UUID.fromString(eng.state().currentPlayer().getPlayerId());
            tableService.applyAction(tableId, firstActor, new GameActionRequest("ALL_IN", null, null, "allin-" + attempt));
            eng = tableService.engineSnapshot(tableId);
            if (eng.state().isBettingActive()) {
                UUID secondActor = UUID.fromString(eng.state().currentPlayer().getPlayerId());
                tableService.applyAction(tableId, secondActor, new GameActionRequest("CALL", null, null, "call-" + attempt));
            }
            if (tableService.engineSnapshot(tableId).state().getPlayers().stream().anyMatch(p -> p.getChipStack() == 0)) {
                break;
            }
        }

        TexasHoldemEngine settled = tableService.engineSnapshot(tableId);
        PlayerState busted = settled.state().getPlayers().stream()
                .filter(p -> p.getChipStack() == 0)
                .findFirst().orElseThrow();
        int settledHandNumber = settled.state().getHandNumber();
        int targetNextHand = settledHandNumber + 1;
        String expectedRebuyTranId = "REBUY_" + tableId + "_" + busted.getPlayerId() + "_" + settledHandNumber;

        // First attempt to start next hand fails rebuy transfer -> must NOT advance handNumber or start PLAYING hand
        assertFalse(tableService.maybeStartNextHand(tableId));
        TexasHoldemEngine stillHeld = tableService.engineSnapshot(tableId);
        assertEquals(settledHandNumber, stillHeld.state().getHandNumber());
        assertEquals(GameStatus.NEXT_HAND, stillHeld.state().getStatus());
        assertEquals(OperatorTransfer.State.PENDING, opTransfers.get(expectedRebuyTranId).getState());

        // Second attempt after wallet recovers must reuse exact same REBUY tranid and start hand cleanly
        failRebuy.set(false);
        assertTrue(tableService.maybeStartNextHand(tableId));
        TexasHoldemEngine nextHand = tableService.engineSnapshot(tableId);
        assertEquals(targetNextHand, nextHand.state().getHandNumber());
        assertEquals(GameStatus.PRE_FLOP, nextHand.state().getStatus());
        PlayerState rebought = nextHand.state().player(busted.getPlayerId());
        assertEquals(200, rebought.getChipStack() + rebought.getBetThisRound());
        assertEquals(OperatorTransfer.State.CONFIRMED, opTransfers.get(expectedRebuyTranId).getState());
        assertEquals(List.of(expectedRebuyTranId, expectedRebuyTranId), rebuyTranIds);
    }

    @Test
    void blocksJoinTableWhenOperatorSessionWageringIsBlocked() {
        UUID tableId = tableService.createTable(
                player1.getId(),
                new CreateTableRequest("Blocked Join", "TEXAS_HOLDEM", 10, 20, 6, 200, false)).id();
        sessions.get(player1.getId().toString()).blockWagering();

        ApiException ex = assertThrows(ApiException.class, () ->
                tableService.joinTable(tableId, player1.getId(), new JoinTableRequest(0)));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("OPERATOR_SESSION_EXPIRED", ex.getCode());
        assertEquals(0, tables.get(tableId).getPlayers().size());
    }
}
