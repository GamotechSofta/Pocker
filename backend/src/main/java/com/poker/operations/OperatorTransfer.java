package com.poker.operations;

import com.poker.exception.ApiException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Frozen server-only wallet intent; Mongo _id provides mandatory uniqueness. */
@Document("operator_transfers")
public class OperatorTransfer {
    public enum State { PENDING, CONFIRMED, REJECTED }
    @Id private String id;
    @Version private Long version;
    private String playerId, token, partnerCode, gid, currency, gameType, roundId;
    private BigDecimal bet, win, balance;
    private State state = State.PENDING;
    private Instant createdAt = Instant.now();
    private Instant confirmedAt;
    private String rejectionCode, rejectionMessage, refundTranid;
    private int rejectionStatus;
    protected OperatorTransfer() { }
    public OperatorTransfer(String id, String playerId, OperatorSession session, BigDecimal bet, BigDecimal win, String gameType, String roundId) {
        this(id, playerId, session.getToken(), session.getPartnerCode(), session.getGid(), session.getCurrency(), bet, win, gameType, roundId);
    }
    private OperatorTransfer(String id, String playerId, String token, String partnerCode, String gid, String currency,
                             BigDecimal bet, BigDecimal win, String gameType, String roundId) {
        if (id == null || id.isBlank() || playerId == null || playerId.isBlank() || roundId == null || roundId.isBlank()
                || token == null || token.isBlank() || partnerCode == null || partnerCode.isBlank()) throw new IllegalArgumentException("Incomplete wallet intent.");
        OperatorGameType type = OperatorGameType.from(gameType);
        type.validate(bet, win, type.transferType());
        this.id = id; this.playerId = playerId; this.token = token; this.partnerCode = partnerCode;
        this.gid = Long.toString(OperationsWalletClient.numericGid(gid));
        this.currency = currency == null ? "INR" : currency;
        this.bet = bet; this.win = win; this.gameType = type.wireName(); this.roundId = roundId;
    }
    public void assertIntent(String playerId, BigDecimal bet, BigDecimal win, String gameType, String roundId) {
        OperatorGameType type = OperatorGameType.from(gameType);
        type.validate(bet, win, type.transferType());
        if (!Objects.equals(this.playerId, playerId) || this.bet.compareTo(bet) != 0 || this.win.compareTo(win) != 0
                || !Objects.equals(this.gameType, type.wireName()) || !Objects.equals(this.roundId, roundId)) throw conflict();
    }
    void assertSameRequest(OperatorTransfer other) {
        assertIntent(other.playerId, other.bet, other.win, other.gameType, other.roundId);
        if (!Objects.equals(token, other.token) || !Objects.equals(partnerCode, other.partnerCode) || !Objects.equals(gid, other.gid)) throw conflict();
    }
    void confirm(BigDecimal balance) { this.balance = balance; state = State.CONFIRMED; confirmedAt = Instant.now(); }
    void reject(ApiException failure) { state = State.REJECTED; rejectionCode = failure.getCode(); rejectionStatus = failure.getStatus().value(); rejectionMessage = failure.getMessage(); }
    ApiException rejection() { return new ApiException(HttpStatus.valueOf(rejectionStatus), rejectionCode, rejectionMessage); }
    void claimRefund(String tranid) { if (refundTranid != null && !refundTranid.equals(tranid)) throw conflict(); refundTranid = tranid; }
    OperatorTransfer refund(String tranid) {
        if (state != State.CONFIRMED || bet.signum() <= 0 || win.signum() != 0 || "refund".equals(gameType)) throw new IllegalArgumentException("Only a confirmed debit can be refunded.");
        return new OperatorTransfer(tranid, playerId, token, partnerCode, gid, currency, bet, BigDecimal.ZERO, "refund", roundId);
    }
    private static ApiException conflict() { return new ApiException(HttpStatus.CONFLICT, "WALLET_IDEMPOTENCY_CONFLICT", "This transaction ID belongs to a different wallet operation."); }
    String token() { return token; }
    String partnerCode() { return partnerCode; }
    String gid() { return gid; }
    public String getId() { return id; }
    public String getPlayerId() { return playerId; }
    public String getCurrency() { return currency; }
    public BigDecimal getBet() { return bet; }
    public BigDecimal getWin() { return win; }
    public String getGameType() { return gameType; }
    public String getRoundId() { return roundId; }
    public State getState() { return state; }
    public boolean isConfirmed() { return state == State.CONFIRMED; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public String getRefundTranid() { return refundTranid; }
}
