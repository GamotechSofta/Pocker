package com.poker.tournament.repository;

import com.poker.tournament.entity.TournamentChipLedgerEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface TournamentChipLedgerEntryRepository extends MongoRepository<TournamentChipLedgerEntry, UUID> {
    Optional<TournamentChipLedgerEntry> findByTournamentIdAndIdempotencyKey(UUID tournamentId, String idempotencyKey);
}
