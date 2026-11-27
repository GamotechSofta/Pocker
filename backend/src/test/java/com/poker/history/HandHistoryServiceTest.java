package com.poker.history;

import com.poker.config.TableProperties;
import com.poker.config.WalletProperties;
import com.poker.dto.CreateTableRequest;
import com.poker.dto.GameActionRequest;
import com.poker.dto.JoinTableRequest;
import com.poker.entity.PokerTable;
import com.poker.entity.User;
import com.poker.exception.ApiException;
import com.poker.game.engine.PlayerState;
import com.poker.game.engine.TexasHoldemEngine;
import com.poker.operations.OperationsWalletClient;
import com.poker.operations.OperatorSessionRepository;
import com.poker.operations.OperatorTransferRepository;
import com.poker.operations.OperatorTransferService;
import com.poker.repository.PokerTableRepository;
import com.poker.repository.UserRepository;
import com.poker.service.TableService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandHistoryServiceTest {

    private final Map<UUID, PokerTable> tables = new ConcurrentHashMap<>();
    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    private final Map<String, WalletAccount> accounts = new ConcurrentHashMap<>();
    private final Map<String, WalletTransaction> walletTxs = new ConcurrentHashMap<>();
    private final Map<String, StakeEscrow> escrows = new ConcurrentHashMap<>();
    private final Map<String, PlayerHandHistory> handHistoryDocs = new ConcurrentHashMap<>();

    private HandHistoryService handHistoryService;
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
        handHistoryDocs.clear();

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

        StakeEscrowRepository escrowRepo = mock(StakeEscrowRepository.class);
        when(escrowRepo.save(any(StakeEscrow.class))).thenAnswer(inv -> {
            StakeEscrow e = inv.getArgument(0);
            escrows.put(e.getId(), e);
            return e;
        });
        when(escrowRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(escrows.get(inv.<String>getArgument(0))));
        when(escrowRepo.findByPlayerIdAndStatus(anyString(), anyString())).thenAnswer(inv -> {
            String pid = inv.getArgument(0);
            String st = inv.getArgument(1);
            return escrows.values().stream().filter(e -> pid.equals(e.getPlayerId()) && st.equals(e.getStatus())).toList();
        });

        PlayerHandHistoryRepository historyRepo = mock(PlayerHandHistoryRepository.class);
        when(historyRepo.save(any(PlayerHandHistory.class))).thenAnswer(inv -> {
            PlayerHandHistory doc = inv.getArgument(0);
            handHistoryDocs.put(doc.getId(), doc);
            return doc;
        });
        when(historyRepo.findByPlayerIdOrderByFinishedAtDesc(anyString(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    String pid = inv.getArgument(0);
                    return handHistoryDocs.values().stream()
                            .filter(d -> pid.equals(d.getPlayerId()))
                            .sorted(Comparator.comparing(PlayerHandHistory::getFinishedAt).reversed())
                            .toList();
                });
        when(historyRepo.findByPlayerIdAndVariantOrderByFinishedAtDesc(anyString(), anyString(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    String pid = inv.getArgument(0);
                    String variant = inv.getArgument(1);
                    return handHistoryDocs.values().stream()
                            .filter(d -> pid.equals(d.getPlayerId()) && variant.equals(d.getVariant()))
                            .sorted(Comparator.comparing(PlayerHandHistory::getFinishedAt).reversed())
                            .toList();
                });

        OperatorSessionRepository sessionRepo = mock(OperatorSessionRepository.class);
        when(sessionRepo.findByPlayerId(anyString())).thenReturn(Optional.empty());
        OperatorTransferRepository transferRepo = mock(OperatorTransferRepository.class);
        OperationsWalletClient opClient = mock(OperationsWalletClient.class);
        OperatorTransferService opTransferService = new OperatorTransferService(transferRepo, opClient, sessionRepo);
        WalletService walletService = new WalletService(accountRepo, txRepo, new WalletProperties(), sessionRepo, opClient, opTransferService);
        StakeEscrowService stakeEscrowService = new StakeEscrowService(escrowRepo, walletService);

        handHistoryService = new HandHistoryService(historyRepo);

        TableProperties tableProps = new TableProperties();
        tableProps.setSmallBlind(10);
        tableProps.setBigBlind(20);
        tableProps.setStartingChips(1000);
        tableProps.setMaxPlayers(6);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        tableService = new TableService(tableRepo, userRepo, tableProps, events, walletService, stakeEscrowService, handHistoryService);

        player1 = new User(UUID.randomUUID(), "alice", "alice@test.local", "hash");
        player2 = new User(UUID.randomUUID(), "bob", "bob@test.local", "hash");
        users.put(player1.getId(), player1);
        users.put(player2.getId(), player2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEXAS_HOLDEM", "OMAHA", "PINEAPPLE", "FIVE_CARD_DRAW", "RAZZ"})
    void recordsCompletedHandAcrossAllFiveVariantsAndMasksOpponentMuckedCards(String variant) {
        UUID tableId = tableService.createTable(
                player1.getId(),
                new CreateTableRequest("History " + variant, variant, 10, 20, 6, 1000, false)).id();

        tableService.joinTable(tableId, player1.getId(), new JoinTableRequest(0));
        tableService.joinTable(tableId, player2.getId(), new JoinTableRequest(1));
        tableService.startTable(tableId, player1.getId());

        while (tableService.engineSnapshot(tableId).state().isDiscardActive()) {
            PlayerState discardActor = tableService.engineSnapshot(tableId).state().currentPlayer();
            tableService.applyAction(
                    tableId,
                    UUID.fromString(discardActor.getPlayerId()),
                    new GameActionRequest("DISCARD", null, List.of(0), "d-" + discardActor.getPlayerId()));
        }

        TexasHoldemEngine eng = tableService.engineSnapshot(tableId);
        PlayerState firstActor = eng.state().currentPlayer();
        UUID folderId = UUID.fromString(firstActor.getPlayerId());
        UUID winnerId = folderId.equals(player1.getId()) ? player2.getId() : player1.getId();

        tableService.applyAction(tableId, folderId, new GameActionRequest("FOLD", null, null, "fold-1"));

        HandHistoryDtos.HistoryPage winnerPage = handHistoryService.getPlayerHistory(
                winnerId.toString(), variant, "ALL", 0, 20);
        HandHistoryDtos.HistoryPage folderPage = handHistoryService.getPlayerHistory(
                folderId.toString(), variant, "FOLDED", 0, 20);

        assertEquals(variant, winnerPage.variant());
        assertEquals(1, winnerPage.summary().totalHands());
        assertEquals(1, winnerPage.summary().handsWon());
        assertTrue(winnerPage.summary().netChips() > 0);
        assertEquals(1, winnerPage.rows().size());

        HandHistoryDtos.HandItem winnerRow = winnerPage.rows().get(0);
        assertEquals("WON", winnerRow.outcome());
        assertEquals(1, winnerRow.handNumber());
        assertEquals("Won uncontested (all folded)", winnerRow.handDescription());
        assertFalseEmpty(winnerRow.playerHoleCards());

        // Winner's participant list must NOT leak the folded opponent's hidden hole cards
        HandHistoryDtos.ParticipantItem opponentEntry = winnerRow.participants().stream()
                .filter(p -> p.playerId().equals(folderId.toString()))
                .findFirst().orElseThrow();
        assertTrue(opponentEntry.holeCards().isEmpty());

        assertEquals(1, folderPage.rows().size());
        HandHistoryDtos.HandItem folderRow = folderPage.rows().get(0);
        assertEquals("FOLDED", folderRow.outcome());
        assertTrue(folderRow.netChips() <= 0);
        assertFalseEmpty(folderRow.playerHoleCards());
    }

    @Test
    void recordsMidHandLeaveAndUpdatesOnceHandFinishesPlusValidatesVariantFilter() {
        User player3 = new User(UUID.randomUUID(), "charlie", "charlie@test.local", "hash");
        users.put(player3.getId(), player3);

        UUID tableId = tableService.createTable(
                player1.getId(),
                new CreateTableRequest("Omaha 3P", "OMAHA", 10, 20, 6, 1000, false)).id();
        tableService.joinTable(tableId, player1.getId(), new JoinTableRequest(0));
        tableService.joinTable(tableId, player2.getId(), new JoinTableRequest(1));
        tableService.joinTable(tableId, player3.getId(), new JoinTableRequest(2));
        tableService.startTable(tableId, player1.getId());

        UUID leaverId = UUID.fromString(tableService.engineSnapshot(tableId).state().currentPlayer().getPlayerId());
        tableService.leaveTable(tableId, leaverId);

        // Immediately visible in leaver's Omaha history even while remaining players continue
        HandHistoryDtos.HistoryPage immediate = handHistoryService.getPlayerHistory(leaverId.toString(), "OMAHA", "ALL", 0, 20);
        assertEquals(1, immediate.rows().size());
        assertEquals("FOLDED", immediate.rows().get(0).outcome());

        // Finish remaining hand between the 2 remaining players
        UUID actor2 = UUID.fromString(tableService.engineSnapshot(tableId).state().currentPlayer().getPlayerId());
        tableService.applyAction(tableId, actor2, new GameActionRequest("FOLD", null, null, "fold-end"));

        HandHistoryDtos.HistoryPage completed = handHistoryService.getPlayerHistory(leaverId.toString(), "OMAHA", "ALL", 0, 20);
        assertEquals(1, completed.rows().size());
        assertNotNull(completed.rows().get(0).winnerNames());
        assertEquals(1, completed.rows().get(0).winnerNames().size());

        // Hold'em query should be empty for this Omaha-only player
        HandHistoryDtos.HistoryPage holdemOnly = handHistoryService.getPlayerHistory(leaverId.toString(), "TEXAS_HOLDEM", "ALL", 0, 20);
        assertEquals(0, holdemOnly.summary().totalHands());
        assertEquals(0, holdemOnly.rows().size());

        ApiException badVariant = assertThrows(ApiException.class, () ->
                handHistoryService.getPlayerHistory(leaverId.toString(), "UNKNOWN_GAME", "ALL", 0, 20));
        assertEquals(HttpStatus.BAD_REQUEST, badVariant.getStatus());
    }

    private static void assertFalseEmpty(List<String> cards) {
        assertTrue(cards != null && !cards.isEmpty(), "Expected player's own cards to be recorded");
    }
}
