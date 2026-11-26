package com.poker.tournament.repository;

import com.poker.tournament.entity.TournamentBlindSchedule;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface TournamentBlindScheduleRepository extends MongoRepository<TournamentBlindSchedule, UUID> {
    Optional<TournamentBlindSchedule> findByTournamentId(UUID tournamentId);
}
