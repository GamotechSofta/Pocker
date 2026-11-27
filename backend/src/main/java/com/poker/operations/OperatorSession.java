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
    private String closeUrl;
    private Instant updatedAt = Instant.now();
    protected OperatorSession() { }
    public OperatorSession(String playerId, String token, String partnerCode, String gid, String closeUrl) { this.playerId=playerId; this.token=token; this.partnerCode=partnerCode; this.gid=gid; this.closeUrl=closeUrl; }
    public String getPlayerId(){ return playerId; } public String getToken(){ return token; } public String getPartnerCode(){ return partnerCode; } public String getGid(){ return gid; }
}
