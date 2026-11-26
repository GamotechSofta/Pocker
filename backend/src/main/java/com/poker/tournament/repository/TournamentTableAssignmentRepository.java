package com.poker.tournament.repository;

import com.poker.tournament.entity.TournamentTableAssignment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentTableAssignmentRepository extends MongoRepository<TournamentTableAssignment, UUID> {
    Optional<TournamentTableAssignment> findByTournamentIdAndEntrantId(UUID tournamentId, UUID entrantId);
    List<TournamentTableAssignment> findAllByTournamentIdAndTableId(UUID tournamentId, UUID tableId);
    List<TournamentTableAssignment> findAllByTournamentId(UUID tournamentId);
}
