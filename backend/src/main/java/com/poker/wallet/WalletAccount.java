package com.poker.wallet;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Player bankroll ledger account (Rummy-parity). Segregated deposit / winnings / bonus pools.
 */
@Document(collection = "wallet_accounts")
public class WalletAccount {

    public static final String CURRENCY = "INR";

    @Id
    private String id;

    @Indexed(unique = true)
    private String playerId;

    private BigDecimal balance;
    private BigDecimal depositBalance;
    private BigDecimal winningsBalance;
    private BigDecimal bonusBalance;
    private String currency;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;

    public WalletAccount() {
        this.balance = BigDecimal.ZERO;
        this.depositBalance = BigDecimal.ZERO;
        this.winningsBalance = BigDecimal.ZERO;
        this.bonusBalance = BigDecimal.ZERO;
        this.currency = CURRENCY;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public WalletAccount(String playerId) {
        this();
        this.playerId = playerId;
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

    public BigDecimal getBalance() {
        if (balance != null) {
            return balance;
        }
        return getDepositBalance().add(getWinningsBalance()).add(getBonusBalance());
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getDepositBalance() {
        return depositBalance != null ? depositBalance : BigDecimal.ZERO;
    }

    public void setDepositBalance(BigDecimal depositBalance) {
        this.depositBalance = depositBalance;
    }

    public BigDecimal getWinningsBalance() {
        return winningsBalance != null ? winningsBalance : BigDecimal.ZERO;
    }

    public void setWinningsBalance(BigDecimal winningsBalance) {
        this.winningsBalance = winningsBalance;
    }

    public BigDecimal getBonusBalance() {
        return bonusBalance != null ? bonusBalance : BigDecimal.ZERO;
    }

    public void setBonusBalance(BigDecimal bonusBalance) {
        this.bonusBalance = bonusBalance;
    }

    public void syncTotalBalance() {
        this.balance = getDepositBalance().add(getWinningsBalance()).add(getBonusBalance());
    }

    public String getCurrency() {
        return currency != null ? currency : CURRENCY;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
