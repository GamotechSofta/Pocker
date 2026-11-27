package com.poker.operations;

import com.poker.exception.ApiException;
import com.poker.exception.ErrorCodes;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class OperatorTransferService {
    private final OperatorTransferRepository repository;
    private final OperationsWalletClient client;
    private final OperatorSessionRepository sessions;
    private final Object[] locks = new Object[128];
    public OperatorTransferService(OperatorTransferRepository repository, OperationsWalletClient client, OperatorSessionRepository sessions) {
        this.repository = repository; this.client = client; this.sessions = sessions;
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
    }
    public Optional<OperatorTransfer> find(String tranid) { return repository.findById(tranid); }
    public List<OperatorTransfer> transfersForRound(String playerId, String roundId) { return repository.findByPlayerIdAndRoundIdOrderByCreatedAtAsc(playerId, roundId); }
    public List<OperatorTransfer> pendingForPlayer(String playerId) { return repository.findByPlayerIdAndStateOrderByCreatedAtAsc(playerId, OperatorTransfer.State.PENDING, PageRequest.of(0, 50)); }
    public OperatorTransfer execute(String playerId, OperatorSession session, BigDecimal bet, BigDecimal win, String gameType, String tranid, String roundId) {
        if (session == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OPERATOR_SESSION_EXPIRED", "Your play session has expired. Return to your operator lobby.");
        }
        if (session.isWageringBlocked() && bet != null && bet.signum() > 0 && !"refund".equalsIgnoreCase(gameType)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OPERATOR_SESSION_EXPIRED", "Your play session has expired. Return to your operator lobby.");
        }
        return submit(new OperatorTransfer(tranid, playerId, session, bet, win, gameType, roundId));
    }
    private OperatorTransfer submit(OperatorTransfer candidate) {
        synchronized (lock(candidate.getPlayerId())) {
            Optional<OperatorTransfer> found = repository.findById(candidate.getId());
            if (found.isPresent()) found.get().assertSameRequest(candidate);
            else {
                if (!pendingForPlayer(candidate.getPlayerId()).isEmpty()) throw pending();
                try { repository.insert(candidate); }
                catch (DuplicateKeyException raced) { required(candidate.getId()).assertSameRequest(candidate); }
            }
            return recover(candidate.getId());
        }
    }
    public OperatorTransfer recover(String tranid) {
        OperatorTransfer original = required(tranid);
        synchronized (lock(original.getPlayerId())) {
            original = required(tranid);
            if (original.isConfirmed()) return original;
            if (original.getState() == OperatorTransfer.State.REJECTED) throw original.rejection();
            try {
                BigDecimal balance = client.transfer(original.token(), original.partnerCode(), original.gid(), original.getBet(), original.getWin(), original.getGameType(), original.getId(), OperatorGameType.from(original.getGameType()).transferType(), original.getRoundId());
                return recordResult(tranid, balance, null);
            } catch (ApiException failure) {
                if (failure.getStatus() == HttpStatus.BAD_REQUEST || "OPERATOR_SESSION_EXPIRED".equals(failure.getCode())) {
                    OperatorTransfer result = recordResult(tranid, null, failure);
                    if (result.isConfirmed()) return result;
                    if ("OPERATOR_SESSION_EXPIRED".equals(failure.getCode())) sessions.findByPlayerId(original.getPlayerId()).ifPresent(session -> sessions.save(session.blockWagering()));
                }
                throw failure;
            }
        }
    }
    public OperatorTransfer refund(String originalTranid, String refundTranid) {
        OperatorTransfer original = required(originalTranid);
        synchronized (lock(original.getPlayerId())) {
            original = recover(originalTranid);
            original.refund(refundTranid);
            for (int attempt = 0; attempt < 8; attempt++) {
                original = required(originalTranid); original.claimRefund(refundTranid);
                try { original = repository.save(original); return submit(original.refund(refundTranid)); }
                catch (OptimisticLockingFailureException raced) { }
            }
            throw pending();
        }
    }
    private OperatorTransfer recordResult(String tranid, BigDecimal balance, ApiException failure) {
        for (int attempt = 0; attempt < 8; attempt++) {
            OperatorTransfer current = required(tranid);
            if (current.isConfirmed()) return current;
            if (current.getState() == OperatorTransfer.State.REJECTED) throw current.rejection();
            if (failure == null) current.confirm(balance); else current.reject(failure);
            try { return repository.save(current); } catch (OptimisticLockingFailureException raced) { }
        }
        throw pending();
    }
    private OperatorTransfer required(String tranid) { return repository.findById(tranid).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_TRANSFER_NOT_FOUND", "The wallet operation could not be found.")); }
    private Object lock(String playerId) { return locks[Math.floorMod(playerId.hashCode(), locks.length)]; }
    private static ApiException pending() { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.WALLET_UNAVAILABLE, "Wallet confirmation is pending. Retry the same operation."); }
}
