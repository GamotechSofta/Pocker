package com.poker.repository;

import com.poker.entity.PokerTable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PokerTableRepository extends MongoRepository<PokerTable, UUID> {

    @Query(value = "{ 'gameMode': { '$ne': 'TOURNAMENT' } }", sort = "{ 'createdAt': -1 }")
    List<PokerTable> findAllByOrderByCreatedAtDesc();

    boolean existsByPlayersUserId(UUID userId);

    @Query("{ 'players.userId': ?0, 'gameMode': { '$ne': 'TOURNAMENT' } }")
    List<PokerTable> findCashTablesByPlayersUserId(UUID userId);

    Optional<PokerTable> findByRoomCodeIgnoreCase(String roomCode);

    boolean existsByRoomCodeIgnoreCase(String roomCode);
}
