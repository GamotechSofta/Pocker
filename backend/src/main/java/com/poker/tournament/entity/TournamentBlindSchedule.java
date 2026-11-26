package com.poker.tournament.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document(collection = "tournament_blind_schedules")
public class TournamentBlindSchedule {
    @Id
    private UUID id;
    @Indexed(useGeneratedName = true, unique = true)
    private UUID tournamentId;
    private List<BlindLevel> levels;
    private Instant createdAt;

    protected TournamentBlindSchedule() { }

    public TournamentBlindSchedule(UUID tournamentId, List<BlindLevel> levels) {
        if (tournamentId == null || levels == null || levels.isEmpty()) {
            throw new IllegalArgumentException("Tournament and at least one blind level are required.");
        }
        this.id = UUID.randomUUID();
        this.tournamentId = tournamentId;
        this.levels = List.copyOf(levels);
        this.createdAt = Instant.now();
        for (int i = 0; i < this.levels.size(); i++) {
            if (this.levels.get(i).levelNumber() != i + 1) {
                throw new IllegalArgumentException("Blind levels must be contiguous and start at level 1.");
            }
        }
    }

    public UUID getId() { return id; }
    public UUID getTournamentId() { return tournamentId; }
    public List<BlindLevel> getLevels() { return levels; }
    public Instant getCreatedAt() { return createdAt; }
}
