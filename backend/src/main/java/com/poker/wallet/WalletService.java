package com.poker.wallet;

import com.poker.config.WalletProperties;
import com.poker.operations.OperationsWalletClient;
import com.poker.operations.OperatorSession;
import com.poker.operations.OperatorSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mongo ledger with optimistic locking + idempotent keys (Rummy WalletService parity, platform-only).
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private static final int MAX_WRITE_ATTEMPTS = 8;
    private static final int TRANSACTION_HISTORY_LIMIT = 100;

    public static final String PLATFORM_TREASURY = "PLATFORM_TREASURY";
    public static final String TX_GAME_ENTRY_STAKE = "GAME_ENTRY_STAKE";
    public static final String TX_GAME_ENTRY_REFUND = "GAME_ENTRY_REFUND";
    public static final String TX_TABLE_CASH_OUT = "TABLE_CASH_OUT";
    public static final String TX_REBUY = "REBUY";
    public static final String TX_DEPOSIT = "DEPOSIT";
    public static final String TX_PLATFORM_RAKE = "PLATFORM_RAKE";

    private final WalletAccountRepository accountRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletProperties walletProperties;
    private final OperatorSessionRepository operatorSessions;
    private final OperationsWalletClient operationsWallet;

    public WalletService(
            WalletAccountRepository accountRepository,
            WalletTransactionRepository transactionRepository,
            WalletProperties walletProperties, OperatorSessionRepository operatorSessions, OperationsWalletClient operationsWallet
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.walletProperties = walletProperties;
        this.operatorSessions = operatorSessions;
        this.operationsWallet = operationsWallet;
    }

    public BigDecimal balanceOf(String playerId) {
        var session = operatorSessions.findByPlayerId(playerId);
        if (session.isPresent()) return syncOperatorBalance(playerId, session.get());
        return getOrCreateWallet(playerId).getBalance();
    }

    public WalletAccount getOrCreateWallet(String playerId) {
        WalletAccount account = loadOrCreate(playerId);
        if (operatorSessions.findByPlayerId(playerId).isPresent()) {
            return account;
        }
        ensureStartingBalance(playerId);
        return accountRepository.findByPlayerId(playerId).orElse(account);
    }

    private WalletAccount loadOrCreate(String playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<WalletAccount> existing = accountRepository.findByPlayerId(playerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return accountRepository.insert(new WalletAccount(playerId));
        } catch (DuplicateKeyException raced) {
            return accountRepository.findByPlayerId(playerId)
                    .orElseThrow(() -> new IllegalStateException("Wallet for " + playerId + " vanished after duplicate insert"));
        }
    }

    public boolean hasTransaction(String idempotencyKey) {
        return transactionRepository.existsByIdempotencyKey(idempotencyKey);
    }

    public WalletTransaction credit(
            String playerId,
            BigDecimal amount,
            String type,
            String idempotencyKey,
            String gameId,
            String description,
            Map<String, Object> meta
    ) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be strictly positive");
        }
        return applyOperatorChange(playerId, amount, type, idempotencyKey, gameId, description, meta, false)
                .orElseGet(() -> applyChange(playerId, amount, type, idempotencyKey, gameId, description, meta));
    }

    public WalletTransaction debit(
            String playerId,
            BigDecimal amount,
            String type,
            String idempotencyKey,
            String gameId,
            String description,
            Map<String, Object> meta
    ) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be strictly positive");
        }
        return applyOperatorChange(playerId, amount, type, idempotencyKey, gameId, description, meta, true)
                .orElseGet(() -> applyChange(playerId, amount.negate(), type, idempotencyKey, gameId, description, meta));
    }

    public List<WalletTransaction> getTransactions(String playerId) {
        getOrCreateWallet(playerId);
        return transactionRepository.findByPlayerIdOrderByCreatedAtDesc(
                playerId,
                PageRequest.of(0, TRANSACTION_HISTORY_LIMIT)
        );
    }

    public WalletTransaction addCash(String playerId, long amount) {
        long max = Math.max(1, walletProperties.getMaxAddCashAmount());
        long pack = amount > 0 ? amount : walletProperties.getAddCashAmount();
        if (pack > max) {
            throw new IllegalArgumentException("Add Cash amount exceeds maximum of " + max);
        }
        String key = "ADD_CASH_" + playerId + "_" + UUID.randomUUID();
        return credit(
                playerId,
                BigDecimal.valueOf(pack),
                TX_DEPOSIT,
                key,
                null,
                "Play-money Add Cash",
                Map.of("source", "cashier")
        );
    }

    private Optional<WalletTransaction> applyOperatorChange(String playerId, BigDecimal amount, String type, String key,
                                                             String gameId, String description, Map<String, Object> meta,
                                                             boolean debit) {
        Optional<OperatorSession> session = operatorSessions.findByPlayerId(playerId);
        if (session.isEmpty()) return Optional.empty();
        Optional<WalletTransaction> existing = transactionRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) return existing;
        boolean refund = type != null && type.contains("REFUND");
        BigDecimal remote = operationsWallet.transfer(session.get().getToken(), session.get().getPartnerCode(), session.get().getGid(),
                debit || refund ? amount : BigDecimal.ZERO, debit || refund ? BigDecimal.ZERO : amount,
                refund ? "refund" : "spin", key, refund ? 2 : 1, gameId == null ? key : gameId);
        WalletAccount account = loadOrCreate(playerId);
        BigDecimal before = account.getBalance();
        account.setBalance(remote); account.setDepositBalance(remote); account.setWinningsBalance(BigDecimal.ZERO); account.setBonusBalance(BigDecimal.ZERO); account.setUpdatedAt(Instant.now());
        accountRepository.save(account);
        WalletTransaction tx = new WalletTransaction(key, playerId, gameId, type, amount, before, remote,
                account.getCurrency(), description, meta);
        try { return Optional.of(transactionRepository.insert(tx)); }
        catch (DuplicateKeyException ignored) { return transactionRepository.findByIdempotencyKey(key); }
    }

    private BigDecimal syncOperatorBalance(String playerId, OperatorSession session) {
        BigDecimal remote = operationsWallet.balance(session.getToken(), session.getPartnerCode());
        WalletAccount account = loadOrCreate(playerId);
        account.setBalance(remote); account.setDepositBalance(remote); account.setWinningsBalance(BigDecimal.ZERO); account.setBonusBalance(BigDecimal.ZERO); account.setUpdatedAt(Instant.now());
        accountRepository.save(account);
        return remote;
    }

    private void ensureStartingBalance(String playerId) {
        long starting = walletProperties.getStartingBalance();
        if (starting <= 0 || PLATFORM_TREASURY.equals(playerId)) {
            return;
        }
        String key = "STARTING_" + playerId;
        if (transactionRepository.existsByIdempotencyKey(key)) {
            return;
        }
        try {
            credit(
                    playerId,
                    BigDecimal.valueOf(starting),
                    TX_DEPOSIT,
                    key,
                    null,
                    "Starting play-money balance",
                    Map.of("source", "starting_grant")
            );
        } catch (DuplicateKeyException ignored) {
            // Concurrent first login — already funded.
        }
    }

    private WalletTransaction applyChange(
            String playerId,
            BigDecimal delta,
            String type,
            String idempotencyKey,
            String gameId,
            String description,
            Map<String, Object> meta
    ) {
        Objects.requireNonNull(playerId, "playerId");
        String key = idempotencyKey != null ? idempotencyKey : type + "_" + UUID.randomUUID();

        Optional<WalletTransaction> existing = transactionRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return existing.get();
        }

        loadOrCreate(playerId);
        // Starting grant must land before the first debit (matchmaking can hit the wallet
        // before the lobby ever calls GET /balance).
        if (!key.startsWith("STARTING_")) {
            ensureStartingBalance(playerId);
        }

        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            try {
                WalletTransaction tx = writeChange(playerId, delta, type, key, gameId, description, meta);
                log.info(
                        "[Wallet] {} {} for player={} ({} -> {}) key={}",
                        delta.signum() >= 0 ? "Credited" : "Debited",
                        delta.abs(),
                        playerId,
                        tx.getBalanceBefore(),
                        tx.getBalanceAfter(),
                        key
                );
                return tx;
            } catch (OptimisticLockingFailureException e) {
                backoff(attempt);
            } catch (DuplicateKeyException e) {
                return transactionRepository.findByIdempotencyKey(key).orElseThrow(() -> e);
            }
        }
        throw new IllegalStateException("Wallet for player " + playerId + " is busy, please retry");
    }

    private WalletTransaction writeChange(
            String playerId,
            BigDecimal delta,
            String type,
            String key,
            String gameId,
            String description,
            Map<String, Object> meta
    ) {
        WalletAccount account = accountRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found for " + playerId));
        BigDecimal before = account.getBalance();
        BigDecimal after = before.add(delta);
        if (after.signum() < 0 && !PLATFORM_TREASURY.equals(playerId)) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Balance: ₹" + before + ", Required: ₹" + delta.negate()
            );
        }
        applyBalanceAdjustment(account, delta, type, playerId);
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        WalletTransaction tx = new WalletTransaction(
                key,
                playerId,
                gameId,
                type,
                delta.abs(),
                before,
                after,
                account.getCurrency(),
                description,
                meta
        );
        try {
            return transactionRepository.insert(tx);
        } catch (DuplicateKeyException e) {
            revertBalance(playerId, delta);
            throw e;
        }
    }

    private void revertBalance(String playerId, BigDecimal delta) {
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            try {
                WalletAccount account = accountRepository.findByPlayerId(playerId).orElseThrow();
                applyBalanceAdjustment(account, delta.negate(), "REVERT", playerId);
                account.setUpdatedAt(Instant.now());
                accountRepository.save(account);
                return;
            } catch (OptimisticLockingFailureException e) {
                backoff(attempt);
            }
        }
        log.error("[Wallet] RECONCILE REQUIRED: could not revert {} for player {}", delta, playerId);
    }

    private void applyBalanceAdjustment(WalletAccount account, BigDecimal delta, String type, String playerId) {
        if (PLATFORM_TREASURY.equals(playerId)) {
            account.setBalance(account.getBalance().add(delta));
            return;
        }

        if (delta.signum() >= 0) {
            BigDecimal amount = delta;
            if ("GAME_WIN".equalsIgnoreCase(type)
                    || "BOT_HOUSE_WIN".equalsIgnoreCase(type)
                    || TX_TABLE_CASH_OUT.equalsIgnoreCase(type)) {
                account.setWinningsBalance(account.getWinningsBalance().add(amount));
            } else if ("PROMOTIONAL_CREDIT".equalsIgnoreCase(type)
                    || (type != null && type.toUpperCase().contains("BONUS"))) {
                account.setBonusBalance(account.getBonusBalance().add(amount));
            } else {
                account.setDepositBalance(account.getDepositBalance().add(amount));
            }
        } else if ("WITHDRAWAL".equalsIgnoreCase(type)) {
            BigDecimal remaining = delta.abs();
            BigDecimal win = account.getWinningsBalance();
            if (win.signum() > 0) {
                BigDecimal deduct = win.min(remaining);
                account.setWinningsBalance(win.subtract(deduct));
                remaining = remaining.subtract(deduct);
            }
            if (remaining.signum() > 0) {
                BigDecimal dep = account.getDepositBalance();
                BigDecimal deduct = dep.min(remaining);
                account.setDepositBalance(dep.subtract(deduct));
                remaining = remaining.subtract(deduct);
            }
            if (remaining.signum() > 0) {
                account.setBonusBalance(account.getBonusBalance().subtract(remaining));
            }
        } else {
            BigDecimal remaining = delta.abs();
            BigDecimal bonus = account.getBonusBalance();
            if (bonus.signum() > 0) {
                BigDecimal deduct = bonus.min(remaining);
                account.setBonusBalance(bonus.subtract(deduct));
                remaining = remaining.subtract(deduct);
            }
            if (remaining.signum() > 0) {
                BigDecimal dep = account.getDepositBalance();
                BigDecimal deduct = dep.min(remaining);
                account.setDepositBalance(dep.subtract(deduct));
                remaining = remaining.subtract(deduct);
            }
            if (remaining.signum() > 0) {
                account.setWinningsBalance(account.getWinningsBalance().subtract(remaining));
            }
        }
        account.syncTotalBalance();
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(5L * attempt + ThreadLocalRandom.current().nextInt(8));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
