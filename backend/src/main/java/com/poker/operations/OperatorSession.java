package com.poker.operations;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "operator_sessions")
public class OperatorSession {
    @Id private String id;
    @Indexed(unique = true) private String playerId;
    private String token;
    private String partnerCode;
    private String gid;
    private String gameCode;
    private String username;
    private String userId;
    private String currency;
    private String lang;
    private String extsess;
    private String closeUrl;
    private boolean wageringBlocked;
    private Instant updatedAt = Instant.now();

    protected OperatorSession() { }

    public OperatorSession(String playerId, String token, String partnerCode, String gid, String closeUrl) {
        this(playerId, token, partnerCode, gid, null, null, null, "INR", "en", null, closeUrl);
    }

    public OperatorSession(String playerId, String token, String partnerCode, String gid,
                           String gameCode, String username, String userId, String currency,
                           String lang, String extsess, String closeUrl) {
        this.id = playerId;
        this.playerId = playerId;
        this.token = token;
        this.partnerCode = partnerCode;
        this.gid = gid;
        this.gameCode = gameCode;
        this.username = username;
        this.userId = userId;
        this.currency = currency;
        this.lang = lang;
        this.extsess = extsess;
        this.closeUrl = closeUrl;
    }

    public String getPlayerId() { return playerId; }
    public String getToken() { return token; }
    public String getPartnerCode() { return partnerCode; }
    public String getGid() { return gid; }
    public String getGameCode() { return gameCode; }
    public String getUsername() { return username; }
    public String getUserId() { return userId; }
    public String getCurrency() { return currency == null || currency.isBlank() ? "INR" : currency; }
    public String getLang() { return lang == null || lang.isBlank() ? "en" : lang; }
    public String getExtsess() { return extsess; }
    public String getCloseUrl() { return closeUrl; }
    public boolean isWageringBlocked() { return wageringBlocked; }
    public OperatorSession blockWagering() { wageringBlocked = true; updatedAt = Instant.now(); return this; }
}
