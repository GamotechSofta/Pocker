package com.poker.tournament.repository;

import com.poker.tournament.entity.TournamentEntrant;
import com.poker.tournament.entity.TournamentEntrantStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentEntrantRepository extends MongoRepository<TournamentEntrant, UUID> {
    record RegistrationCount(@Id UUID tournamentId, long count) { }

    @Aggregation(pipeline = {
            "{ '$match': { 'tournamentId': { '$in': ?0 }, 'status': { '$ne': 'CANCELLED' } } }",
            "{ '$group': { '_id': '$tournamentId', 'count': { '$sum': 1 } } }"
    })
    List<RegistrationCount> registrationCounts(Collection<UUID> tournamentIds);

    List<TournamentEntrant> findByUserIdAndTournamentIdIn(UUID userId, Collection<UUID> tournamentIds);
    List<TournamentEntrant> findByUserIdAndStatusNot(UUID userId, TournamentEntrantStatus status);
    List<TournamentEntrant> findByTournamentIdAndStatusNot(UUID tournamentId, TournamentEntrantStatus status, Pageable page);
    Optional<TournamentEntrant> findByTournamentIdAndUserId(UUID tournamentId, UUID userId);
    List<TournamentEntrant> findAllByTournamentIdOrderByFinishPositionAsc(UUID tournamentId);
    long countByTournamentIdAndStatusNot(UUID tournamentId, TournamentEntrantStatus status);
    boolean existsByTournamentIdAndUserId(UUID tournamentId, UUID userId);
}
