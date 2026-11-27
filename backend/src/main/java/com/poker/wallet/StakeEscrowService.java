package com.poker.wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable buy-in escrow fence (Rummy StakeEscrowService parity, single-node).
 */
@Service
public class StakeEscrowService {

    private static final Logger log = LoggerFactory.getLogger(StakeEscrowService.class);
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final String NODE = "poker-local";

    public enum Status {
        OPEN,
        CLOSING,
        SETTLED,
        REFUNDED
    }

    private final StakeEscrowRepository repository;
    private final WalletService wallet;

    public StakeEscrowService(StakeEscrowRepository repository, WalletService wallet) {
        this.repository = repository;
        this.wallet = wallet;
    }

    public void open(String escrowId, String playerId, long amount, String tableId, String gameId) {
        StakeEscrow doc = new StakeEscrow(escrowId, playerId, amount, Status.OPEN.name(), NODE, tableId, gameId);
        try {
            repository.insert(doc);
        } catch (DuplicateKeyException ignored) {
            // Retried open for the same debit key.
        }
    }

    /**
     * Opens escrow and debits the player. Returns the escrow/idempotency id.
     * On debit failure the escrow is discarded and the exception propagates.
     */
    public String reserveEntry(String playerId, long amount, String description) {
        Objects.requireNonNull(playerId, "playerId");
        if (amount <= 0) {
            throw new IllegalArgumentException("Buy-in amount must be positive");
        }
        // Retry with the same transaction ID if an unassigned buy-in is awaiting wallet confirmation.
        String existingPendingKey = findOpenForPlayer(playerId).stream()
                .filter(e -> e.getTableId() == null && e.getAmount() == amount)
                .map(StakeEscrow::getId)
                .filter(wallet::hasPendingOrConfirmedOperatorTransfer)
                .findFirst()
                .orElse(null);
        String key = existingPendingKey != null ? existingPendingKey : "STAKE_" + java.util.UUID.randomUUID();
        return reserveEntry(playerId, amount, description, key);
    }

    public String reserveEntry(String playerId, long amount, String description, String explicitKey) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(explicitKey, "explicitKey");
        if (amount <= 0) {
            throw new IllegalArgumentException("Buy-in amount must be positive");
        }
        open(explicitKey, playerId, amount, null, null);
        try {
            wallet.debit(
                    playerId,
                    BigDecimal.valueOf(amount),
                    WalletService.TX_GAME_ENTRY_STAKE,
                    explicitKey,
                    null,
                    description != null ? description : "Table buy-in",
                    Map.of("amount", amount)
            );
            return explicitKey;
        } catch (RuntimeException ex) {
            if (!wallet.hasPendingOrConfirmedOperatorTransfer(explicitKey)) {
                discard(explicitKey);
            }
            throw ex;
        }
    }

    public String reserveRebuy(String playerId, long amount, String tableId) {
        return reserveRebuy(playerId, amount, tableId, "REBUY_" + tableId + "_" + playerId + "_" + java.util.UUID.randomUUID());
    }

    public String reserveRebuy(String playerId, long amount, String tableId, String explicitKey) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(explicitKey, "explicitKey");
        if (amount <= 0) {
            throw new IllegalArgumentException("Rebuy amount must be positive");
        }
        open(explicitKey, playerId, amount, tableId, null);
        try {
            wallet.debit(
                    playerId,
                    BigDecimal.valueOf(amount),
                    WalletService.TX_REBUY,
                    explicitKey,
                    tableId,
                    "Table rebuy",
                    Map.of("tableId", tableId)
            );
            return explicitKey;
        } catch (RuntimeException ex) {
            if (!wallet.hasPendingOrConfirmedOperatorTransfer(explicitKey)) {
                discard(explicitKey);
            }
            throw ex;
        }
    }

    public void discard(String escrowId) {
        repository.findById(escrowId).ifPresent(doc -> {
            if (Status.OPEN.name().equals(doc.getStatus())) {
                repository.delete(doc);
            }
        });
    }

    public void assignToTable(String escrowId, String tableId, String gameId) {
        Instant now = Instant.now();
        repository.findById(escrowId).ifPresent(doc -> {
            if (Status.OPEN.name().equals(doc.getStatus())) {
                doc.setHolderNode(NODE);
                doc.setTableId(tableId);
                doc.setGameId(gameId);
                doc.setUpdatedAt(now);
                repository.save(doc);
            }
        });
    }

    public boolean claimRefund(String escrowId) {
        return transition(escrowId, Status.OPEN, Status.REFUNDED);
    }

    public void reopen(String escrowId) {
        repository.findById(escrowId).ifPresent(doc -> {
            if (Status.REFUNDED.name().equals(doc.getStatus())) {
                doc.setStatus(Status.OPEN.name());
                doc.setClosedAt(null);
                doc.setExpireAt(null);
                doc.setUpdatedAt(Instant.now());
                repository.save(doc);
            }
        });
    }

    public boolean beginSettlement(String escrowId) {
        return transition(escrowId, Status.OPEN, Status.CLOSING);
    }

    public void completeSettlement(String escrowId) {
        Instant now = Instant.now();
        repository.findById(escrowId).ifPresent(doc -> {
            if (Status.CLOSING.name().equals(doc.getStatus()) || Status.OPEN.name().equals(doc.getStatus())) {
                doc.setStatus(Status.SETTLED.name());
                doc.setClosedAt(now);
                doc.setUpdatedAt(now);
                doc.setExpireAt(now.plus(RETENTION));
                repository.save(doc);
            }
        });
    }

    public Optional<StakeEscrow> find(String escrowId) {
        return repository.findById(escrowId);
    }

    public List<StakeEscrow> findOpenForPlayer(String playerId) {
        return repository.findByPlayerIdAndStatus(playerId, Status.OPEN.name());
    }

    public List<StakeEscrow> findOpenForTable(String tableId) {
        return repository.findByTableIdAndStatus(tableId, Status.OPEN.name());
    }

    public Optional<StakeEscrow> findOpenForPlayerAtTable(String playerId, String tableId) {
        return findOpenForPlayer(playerId).stream()
                .filter(e -> tableId != null && tableId.equals(e.getTableId()))
                .findFirst();
    }

    public List<StakeEscrow> findAllOpen() {
        return repository.findByStatus(Status.OPEN.name());
    }

    /**
     * Refunds an OPEN escrow if the original debit exists. Idempotent via refund key.
     */
    public boolean refundStake(String escrowId, String reason) {
        Objects.requireNonNull(escrowId, "escrowId");
        Optional<StakeEscrow> open = repository.findById(escrowId);
        if (open.isEmpty()) {
            return false;
        }
        StakeEscrow escrow = open.get();
        if (!Status.OPEN.name().equals(escrow.getStatus())) {
            return false;
        }
        if (!wallet.verifyOrRecoverDebitForRefund(escrowId)) {
            discard(escrowId);
            return false;
        }
        if (!claimRefund(escrowId)) {
            return false;
        }
        String refundKey = "REFUND_" + escrowId;
        try {
            wallet.credit(
                    escrow.getPlayerId(),
                    BigDecimal.valueOf(escrow.getAmount()),
                    WalletService.TX_GAME_ENTRY_REFUND,
                    refundKey,
                    escrow.getGameId(),
                    reason != null ? reason : "Entry returned",
                    Map.of("escrowId", escrowId)
            );
            Instant now = Instant.now();
            repository.findById(escrowId).ifPresent(doc -> {
                doc.setExpireAt(now.plus(RETENTION));
                doc.setClosedAt(now);
                doc.setUpdatedAt(now);
                repository.save(doc);
            });
            return true;
        } catch (RuntimeException ex) {
            log.error("[Escrow] Refund credit failed for {}: {}", escrowId, ex.getMessage());
            reopen(escrowId);
            throw ex;
        }
    }

    private boolean transition(String escrowId, Status from, Status to) {
        Optional<StakeEscrow> found = repository.findById(escrowId);
        if (found.isEmpty()) {
            return false;
        }
        StakeEscrow doc = found.get();
        if (!from.name().equals(doc.getStatus())) {
            return false;
        }
        Instant now = Instant.now();
        doc.setStatus(to.name());
        doc.setUpdatedAt(now);
        if (to == Status.REFUNDED || to == Status.SETTLED) {
            doc.setClosedAt(now);
            doc.setExpireAt(now.plus(RETENTION));
        }
        repository.save(doc);
        return true;
    }
}
