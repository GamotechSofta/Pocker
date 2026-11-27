package com.poker.operations;

import com.poker.exception.ApiException;
import com.poker.exception.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperatorTransferServiceTest {

    private final Map<String, OperatorTransfer> transfers = new ConcurrentHashMap<>();
    private final Map<String, OperatorSession> sessions = new ConcurrentHashMap<>();

    private OperatorTransferRepository transferRepo;
    private OperatorSessionRepository sessionRepo;
    private OperationsWalletClient walletClient;
    private OperatorTransferService service;
    private OperatorSession session;

    @BeforeEach
    void setUp() {
        transferRepo = mock(OperatorTransferRepository.class);
        sessionRepo = mock(OperatorSessionRepository.class);
        walletClient = mock(OperationsWalletClient.class);

        when(transferRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(transfers.get(inv.<String>getArgument(0))));
        when(transferRepo.insert(any(OperatorTransfer.class))).thenAnswer(inv -> {
            OperatorTransfer tx = inv.getArgument(0);
            transfers.put(tx.getId(), tx);
            return tx;
        });
        when(transferRepo.save(any(OperatorTransfer.class))).thenAnswer(inv -> {
            OperatorTransfer tx = inv.getArgument(0);
            transfers.put(tx.getId(), tx);
            return tx;
        });
        when(transferRepo.findByPlayerIdAndStateOrderByCreatedAtAsc(anyString(), any(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    String pid = inv.getArgument(0);
                    OperatorTransfer.State state = inv.getArgument(1);
                    return transfers.values().stream()
                            .filter(t -> pid.equals(t.getPlayerId()) && t.getState() == state)
                            .toList();
                });

        when(sessionRepo.findByPlayerId(anyString())).thenAnswer(inv -> Optional.ofNullable(sessions.get(inv.<String>getArgument(0))));
        when(sessionRepo.save(any(OperatorSession.class))).thenAnswer(inv -> {
            OperatorSession s = inv.getArgument(0);
            sessions.put(s.getPlayerId(), s);
            return s;
        });

        session = new OperatorSession(
                "player-1",
                "tok-live",
                "DPBOSS",
                "42",
                "POKER_HOLDEM",
                "alice",
                "ext-1",
                "INR",
                "en",
                null,
                "https://operator.example.com/lobby"
        );
        sessions.put("player-1", session);

        service = new OperatorTransferService(transferRepo, walletClient, sessionRepo);
    }

    @Test
    void keepsTransferPendingOnWalletUnavailableAndConfirmsRetryWithSameTranId() {
        when(walletClient.transfer(
                eq("tok-live"), eq("DPBOSS"), eq("42"),
                eq(new BigDecimal("100")), eq(BigDecimal.ZERO),
                eq("spin"), eq("CASHOUT-RETRY-1"), eq(1), eq("ROUND-1")))
                .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.WALLET_UNAVAILABLE, "timeout"))
                .thenReturn(new BigDecimal("900"));

        ApiException firstError = assertThrows(ApiException.class, () -> service.execute(
                "player-1", session, new BigDecimal("100"), BigDecimal.ZERO, "spin", "CASHOUT-RETRY-1", "ROUND-1"));
        assertEquals(ErrorCodes.WALLET_UNAVAILABLE, firstError.getCode());

        OperatorTransfer pending = transfers.get("CASHOUT-RETRY-1");
        assertEquals(OperatorTransfer.State.PENDING, pending.getState());

        OperatorTransfer confirmed = service.execute(
                "player-1", session, new BigDecimal("100"), BigDecimal.ZERO, "spin", "CASHOUT-RETRY-1", "ROUND-1");
        assertSame(pending, confirmed);
        assertEquals(OperatorTransfer.State.CONFIRMED, confirmed.getState());
        assertEquals(new BigDecimal("900"), confirmed.getBalance());

        // Subsequent call with same tranid is idempotent and does not hit operator HTTP again
        OperatorTransfer idempotent = service.execute(
                "player-1", session, new BigDecimal("100"), BigDecimal.ZERO, "spin", "CASHOUT-RETRY-1", "ROUND-1");
        assertSame(confirmed, idempotent);
        verify(walletClient, times(2)).transfer(
                anyString(), anyString(), anyString(), any(), any(), anyString(), eq("CASHOUT-RETRY-1"), anyInt(), anyString());
    }

    @Test
    void blocksNewSpinDebitWhenOperatorSessionWageringIsBlockedAndMarksSessionBlockedOnExpiry() {
        session.blockWagering();

        ApiException blockedEx = assertThrows(ApiException.class, () -> service.execute(
                "player-1", session, new BigDecimal("50"), BigDecimal.ZERO, "spin", "BUYIN-BLOCKED", "ROUND-2"));
        assertEquals(HttpStatus.UNAUTHORIZED, blockedEx.getStatus());
        assertEquals("OPERATOR_SESSION_EXPIRED", blockedEx.getCode());
        verifyNoInteractions(walletClient);

        // Unblock session, then simulate operator reporting session expiration on transfer
        OperatorSession active = new OperatorSession(
                "player-2", "tok-expired", "DPBOSS", "42", "POKER_HOLDEM",
                "bob", "ext-2", "INR", "en", null, null);
        sessions.put("player-2", active);
        when(walletClient.transfer(
                eq("tok-expired"), eq("DPBOSS"), eq("42"),
                any(), any(), eq("spin"), eq("BUYIN-EXP"), eq(1), eq("ROUND-3")))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "OPERATOR_SESSION_EXPIRED", "Expired"));

        ApiException expiredEx = assertThrows(ApiException.class, () -> service.execute(
                "player-2", active, new BigDecimal("50"), BigDecimal.ZERO, "spin", "BUYIN-EXP", "ROUND-3"));
        assertEquals("OPERATOR_SESSION_EXPIRED", expiredEx.getCode());
        assertEquals(OperatorTransfer.State.REJECTED, transfers.get("BUYIN-EXP").getState());
        assertEquals(true, active.isWageringBlocked());
    }

    @Test
    void recoverRetriesPendingTransferAndRefundIssuesType2Refund() {
        when(walletClient.transfer(
                eq("tok-live"), eq("DPBOSS"), eq("42"),
                eq(new BigDecimal("100")), eq(BigDecimal.ZERO),
                eq("spin"), eq("DEBIT-1"), eq(1), eq("ROUND-4")))
                .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.WALLET_UNAVAILABLE, "temporary"))
                .thenReturn(new BigDecimal("400"));

        assertThrows(ApiException.class, () -> service.execute(
                "player-1", session, new BigDecimal("100"), BigDecimal.ZERO, "spin", "DEBIT-1", "ROUND-4"));

        OperatorTransfer recovered = service.recover("DEBIT-1");
        assertEquals(OperatorTransfer.State.CONFIRMED, recovered.getState());

        when(walletClient.transfer(
                eq("tok-live"), eq("DPBOSS"), eq("42"),
                eq(BigDecimal.ZERO), eq(new BigDecimal("100")),
                eq("refund"), eq("REFUND-1"), eq(2), eq("ROUND-4")))
                .thenReturn(new BigDecimal("500"));

        OperatorTransfer refundTx = service.refund("DEBIT-1", "REFUND-1");
        assertEquals(OperatorTransfer.State.CONFIRMED, refundTx.getState());
        assertEquals("REFUND-1", transfers.get("DEBIT-1").getRefundTranid());
        assertEquals(List.of(), service.pendingForPlayer("player-1"));
    }
}
