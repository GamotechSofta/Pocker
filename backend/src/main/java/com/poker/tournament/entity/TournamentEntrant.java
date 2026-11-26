package com.poker.tournament.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "tournament_entrants")
@CompoundIndex(useGeneratedName = true, def = "{'tournamentId': 1, 'userId': 1}", unique = true)
@CompoundIndex(name = "tournament_finished_rank_unique", def = "{'tournamentId': 1, 'finishPosition': 1}",
        unique = true, partialFilter = "{'finishPosition': {'$gte': 1}}")
public class TournamentEntrant {
    @Id
    private UUID id;
    @Indexed(useGeneratedName = true)
    private UUID tournamentId;
    @Indexed(useGeneratedName = true)
    private UUID userId;
    private TournamentEntrantStatus status;
    private long currentVirtualChips;
    private long initialVirtualChips;
    private UUID tableId;
    private Integer seat;
    private Integer finishPosition;
    private String eliminationReason;
    private TournamentElimination elimination;
    private Instant registeredAt;
    private Instant eliminatedAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected TournamentEntrant() { }

    public static TournamentEntrant registration(UUID tournamentId, UUID userId, Instant now) {
        var entry = new TournamentEntrant();
        entry.id = UUID.randomUUID();
        entry.tournamentId = tournamentId;
        entry.userId = userId;
        entry.registerAgain(now);
        return entry;
    }

    public void registerAgain(Instant now) {
        requireUnranked();
        status = TournamentEntrantStatus.REGISTERED;
        registeredAt = now;
        updatedAt = now;
    }

    public void withdraw(Instant now) {
        requireUnranked();
        status = TournamentEntrantStatus.CANCELLED;
        updatedAt = now;
    }

    public TournamentEntrant(UUID tournamentId, UUID userId, long initialVirtualChips) {
        if (tournamentId == null || userId == null || initialVirtualChips <= 0) {
            throw new IllegalArgumentException("Tournament, user, and positive initial chips are required.");
        }
        this.id = UUID.randomUUID();
        this.tournamentId = tournamentId;
        this.userId = userId;
        this.status = TournamentEntrantStatus.REGISTERED;
        this.currentVirtualChips = initialVirtualChips;
        this.initialVirtualChips = initialVirtualChips;
        this.registeredAt = Instant.now();
        this.updatedAt = this.registeredAt;
    }

    public UUID getId() { return id; }
    public UUID getTournamentId() { return tournamentId; }
    public UUID getUserId() { return userId; }
    public TournamentEntrantStatus getStatus() { return status; }
    public long getCurrentVirtualChips() { return currentVirtualChips; }
    public long getInitialVirtualChips() { return initialVirtualChips; }
    public UUID getTableId() { return tableId; }
    public Integer getSeat() { return seat; }
    public Integer getFinishPosition() { return finishPosition; }
    public String getEliminationReason() { return eliminationReason; }
    public TournamentElimination getElimination() { return elimination; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getEliminatedAt() { return eliminatedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    public void prepareSeat(UUID tableId, int seat, int stack, Instant now) {
        if (status != TournamentEntrantStatus.REGISTERED || this.tableId != null || initialVirtualChips != 0
                || currentVirtualChips != 0 || stack <= 0) {
            throw new IllegalStateException("Entrant already has a stack or cannot be assigned.");
        }
        assign(tableId, seat);
        initialVirtualChips = stack;
        currentVirtualChips = stack;
        updatedAt = now;
    }

    public void assign(UUID tableId, int seat) {
        requireUnranked();
        if (tableId == null || seat < 0) throw new IllegalArgumentException("Valid table assignment is required.");
        this.tableId = tableId;
        this.seat = seat;
        this.status = TournamentEntrantStatus.SEATED;
        this.updatedAt = Instant.now();
    }

    public void activate() { requireUnranked(); this.status = TournamentEntrantStatus.ACTIVE; this.updatedAt = Instant.now(); }

    public void transfer(UUID table, int destination, Instant now) {
        requireUnranked();
        if (currentVirtualChips <= 0 || tableId == null || tableId.equals(table) || table == null || destination < 0
                || (status != TournamentEntrantStatus.ACTIVE && status != TournamentEntrantStatus.SEATED)) {
            throw new IllegalStateException("Only an existing positive tournament stack can transfer.");
        }
        tableId = table;
        seat = destination;
        updatedAt = now;
    }

    public void settleStack(long chips, Instant now) {
        if (chips < 0 || (status != TournamentEntrantStatus.SEATED && status != TournamentEntrantStatus.ACTIVE)) {
            throw new IllegalStateException("Cannot settle an invalid tournament stack or seat.");
        }
        this.currentVirtualChips = chips;
        this.status = TournamentEntrantStatus.ACTIVE;
        this.updatedAt = now;
    }
    public void disconnect() { requireUnranked(); this.status = TournamentEntrantStatus.DISCONNECTED; this.updatedAt = Instant.now(); }
    public void reconnect() { requireUnranked(); this.status = TournamentEntrantStatus.ACTIVE; this.updatedAt = Instant.now(); }

    private void requireUnranked() {
        if (status == TournamentEntrantStatus.ELIMINATED || finishPosition != null) {
            throw new IllegalStateException("A finalized entrant cannot re-enter the tournament.");
        }
    }

    public void eliminate(int finishPosition, TournamentElimination evidence, Instant now) {
        if (status != TournamentEntrantStatus.ACTIVE || currentVirtualChips != 0 || this.finishPosition != null
                || elimination != null || finishPosition < 2 || evidence == null || evidence.openingStack() <= 0) {
            throw new IllegalStateException("The settled entrant is not eligible for a new finishing position.");
        }
        this.status = TournamentEntrantStatus.ELIMINATED;
        this.finishPosition = finishPosition;
        this.eliminationReason = "ZERO_SETTLED_STACK";
        this.elimination = evidence;
        this.eliminatedAt = now;
        this.updatedAt = now;
    }

    public void completeAsChampion(Instant now) {
        requireUnranked();
        if (status != TournamentEntrantStatus.ACTIVE || currentVirtualChips <= 0 || tableId == null || elimination != null) {
            throw new IllegalStateException("The champion must be the validated active entrant.");
        }
        finishPosition = 1;
        status = TournamentEntrantStatus.COMPLETED;
        updatedAt = now;
    }
}
