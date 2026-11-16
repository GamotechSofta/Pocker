package com.poker.wallet;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Escrowed buy-in / rebuy. Id equals the wallet debit idempotency key (Rummy-parity).
 */
@Document(collection = "stake_escrows")
public class StakeEscrow {

    @Id
    private String id;
    private String playerId;
    private long amount;
    private String status;
    private String holderNode;
    private String tableId;
    @Indexed
    private String gameId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;
    @Indexed(expireAfter = "0s")
    private Instant expireAt;

    public StakeEscrow() {
    }

    public StakeEscrow(String id, String playerId, long amount, String status, String holderNode, String tableId, String gameId) {
        this.id = id;
        this.playerId = playerId;
        this.amount = amount;
        this.status = status;
        this.holderNode = holderNode;
        this.tableId = tableId;
        this.gameId = gameId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHolderNode() {
        return holderNode;
    }

    public void setHolderNode(String holderNode) {
        this.holderNode = holderNode;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt;
    }
}
