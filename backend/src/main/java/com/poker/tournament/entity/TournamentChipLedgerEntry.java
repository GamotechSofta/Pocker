package com.poker.tournament.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "tournament_chip_ledger")
@CompoundIndex(name = "chip_ledger_idempotency_unique", def = "{'tournamentId': 1, 'idempotencyKey': 1}", unique = true)
public class TournamentChipLedgerEntry {
    public enum EntryType { INITIAL_STACK, HAND_RESULT, ELIMINATION, CORRECTION, FINAL_SCORE }

    @Id
    private UUID id;
    @Indexed
    private UUID tournamentId;
    @Indexed
    private UUID entrantId;
    private EntryType type;
    private long delta;
    private long resultingBalance;
    private String idempotencyKey;
    private UUID tableId;
    private Instant createdAt;

    protected TournamentChipLedgerEntry() { }

    public TournamentChipLedgerEntry(UUID tournamentId, UUID entrantId, EntryType type, long delta,
                                     long resultingBalance, String idempotencyKey, UUID tableId) {
        if (tournamentId == null || entrantId == null || type == null || idempotencyKey == null
                || idempotencyKey.isBlank() || resultingBalance < 0) {
            throw new IllegalArgumentException("Invalid tournament chip ledger entry.");
        }
        this.id = UUID.randomUUID();
        this.tournamentId = tournamentId;
        this.entrantId = entrantId;
        this.type = type;
        this.delta = delta;
        this.resultingBalance = resultingBalance;
        this.idempotencyKey = idempotencyKey;
        this.tableId = tableId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTournamentId() { return tournamentId; }
    public UUID getEntrantId() { return entrantId; }
    public EntryType getType() { return type; }
    public long getDelta() { return delta; }
    public long getResultingBalance() { return resultingBalance; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getTableId() { return tableId; }
    public Instant getCreatedAt() { return createdAt; }
}
