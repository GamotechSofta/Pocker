package com.poker.tournament.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "tournament_final_results")
@CompoundIndex(useGeneratedName = true, def = "{'tournamentId': 1, 'entrantId': 1}", unique = true)
@CompoundIndex(useGeneratedName = true, def = "{'tournamentId': 1, 'finishPosition': 1}", unique = true)
@CompoundIndex(name = "tournament_player_result_history", def = "{'userId': 1, 'completedAt': -1, '_id': 1}")
public class TournamentFinalResult {
    @Id
    private UUID id;
    @Indexed(useGeneratedName = true)
    private UUID tournamentId;
    @Indexed(useGeneratedName = true)
    private UUID entrantId;
    private UUID userId;
    private int finishPosition;
    private long finalVirtualChips;
    private Instant completedAt;
    private String displayName;
    private TournamentEntrantStatus participationStatus;
    private Instant eliminatedAt;
    private UUID tableId;
    private Integer tableNumber;
    private UUID handId;
    private UUID settlementId;
    private Integer handsPlayed;
    private UUID operationId;
    private int resultVersion;

    protected TournamentFinalResult() { }

    public TournamentFinalResult(UUID tournamentId, UUID entrantId, UUID userId, int finishPosition, long finalVirtualChips) {
        if (tournamentId == null || entrantId == null || userId == null || finishPosition < 1 || finalVirtualChips < 0) {
            throw new IllegalArgumentException("Invalid tournament final result.");
        }
        this.id = UUID.randomUUID();
        this.tournamentId = tournamentId;
        this.entrantId = entrantId;
        this.userId = userId;
        this.finishPosition = finishPosition;
        this.finalVirtualChips = finalVirtualChips;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTournamentId() { return tournamentId; }
    public UUID getEntrantId() { return entrantId; }
    public UUID getUserId() { return userId; }
    public int getFinishPosition() { return finishPosition; }
    public long getFinalVirtualChips() { return finalVirtualChips; }
    public Instant getCompletedAt() { return completedAt; }
    public String getDisplayName() { return displayName; }
    public TournamentEntrantStatus getParticipationStatus() { return participationStatus; }
    public Instant getEliminatedAt() { return eliminatedAt; }
    public UUID getTableId() { return tableId; }
    public Integer getTableNumber() { return tableNumber; }
    public UUID getHandId() { return handId; }
    public UUID getSettlementId() { return settlementId; }
    public Integer getHandsPlayed() { return handsPlayed; }
    public UUID getOperationId() { return operationId; }
    public int getResultVersion() { return resultVersion; }

    public static TournamentFinalResult official(TournamentEntrant entry, UUID operation, String name, int finishingPosition,
                                                 int tableNumber, UUID hand, UUID settlement, int handsPlayed, Instant completedAt) {
        var row = new TournamentFinalResult(entry.getTournamentId(), entry.getId(), entry.getUserId(), finishingPosition, entry.getCurrentVirtualChips());
        row.completedAt = completedAt;
        row.displayName = name;
        row.participationStatus = finishingPosition == 1 ? TournamentEntrantStatus.COMPLETED : TournamentEntrantStatus.ELIMINATED;
        row.eliminatedAt = entry.getEliminatedAt();
        row.tableId = entry.getTableId();
        row.tableNumber = tableNumber;
        row.handId = hand;
        row.settlementId = settlement;
        row.handsPlayed = handsPlayed;
        row.operationId = operation;
        row.resultVersion = 1;
        return row;
    }
}
