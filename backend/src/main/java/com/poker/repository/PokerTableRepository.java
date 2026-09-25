package com.poker.repository;

import com.poker.entity.PokerTable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PokerTableRepository extends MongoRepository<PokerTable, UUID> {

    List<PokerTable> findAllByOrderByCreatedAtDesc();

    boolean existsByPlayersUserId(UUID userId);

    Optional<PokerTable> findFirstByPlayersUserId(UUID userId);
}
