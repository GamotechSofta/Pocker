package com.poker.tournament.entity;

import com.poker.game.model.GameMode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "tournament_table_assignments")
@CompoundIndex(useGeneratedName = true, def = "{'tournamentId': 1, 'entrantId': 1}", unique = true)
@CompoundIndex(useGeneratedName = true, def = "{'tournamentId': 1, 'tableId': 1, 'seat': 1}", unique = true)
public class TournamentTableAssignment {
    @Id
    private UUID id;
    @Indexed(useGeneratedName = true)
    private UUID tournamentId;
    @Indexed(useGeneratedName = true)
    private UUID entrantId;
    private UUID userId;
    @Indexed(useGeneratedName = true)
    private UUID tableId;
    private int seat;
    private GameMode gameMode = GameMode.TOURNAMENT;
    private Instant assignedAt;
    private Instant releasedAt;
    private long assignmentVersion;

    protected TournamentTableAssignment() { }

    public TournamentTableAssignment(UUID tournamentId, UUID entrantId, UUID tableId, int seat) {
        this(tournamentId, entrantId, null, tableId, seat, Instant.now());
    }

    public TournamentTableAssignment(UUID tournamentId, UUID entrantId, UUID userId, UUID tableId, int seat, Instant assignedAt) {
        if (tournamentId == null || entrantId == null || tableId == null || seat < 0) {
            throw new IllegalArgumentException("Tournament, entrant, table, and non-negative seat are required.");
        }
        this.id = UUID.randomUUID();
        this.tournamentId = tournamentId;
        this.entrantId = entrantId;
        this.userId = userId;
        this.tableId = tableId;
        this.seat = seat;
        this.assignedAt = assignedAt;
    }

    public UUID getId() { return id; }
    public UUID getTournamentId() { return tournamentId; }
    public UUID getEntrantId() { return entrantId; }
    public UUID getUserId() { return userId; }
    public UUID getTableId() { return tableId; }
    public int getSeat() { return seat; }
    public GameMode getGameMode() { return gameMode; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public long getAssignmentVersion() { return assignmentVersion; }

    public void move(UUID table, int destination, Instant time) {
        if (releasedAt != null || table == null || table.equals(tableId) || destination < 0) {
            throw new IllegalStateException("Invalid active assignment transfer.");
        }
        tableId = table;
        seat = destination;
        assignedAt = time;
        assignmentVersion++;
    }
    public void release() { this.releasedAt = Instant.now(); }
    public void release(Instant time) { this.releasedAt = time; }
}
