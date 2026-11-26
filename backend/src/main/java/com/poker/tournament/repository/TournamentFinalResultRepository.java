package com.poker.tournament.repository;

import com.poker.tournament.entity.TournamentFinalResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TournamentFinalResultRepository extends MongoRepository<TournamentFinalResult, UUID> {
    List<TournamentFinalResult> findAllByTournamentIdOrderByFinishPositionAsc(UUID tournamentId);
    List<TournamentFinalResult> findByTournamentIdOrderByFinishPositionAsc(UUID tournamentId, Pageable page);
    boolean existsByTournamentIdAndEntrantId(UUID tournamentId, UUID entrantId);
}
