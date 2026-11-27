package com.poker.history;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PlayerHandHistoryRepository extends MongoRepository<PlayerHandHistory, String> {

    List<PlayerHandHistory> findByPlayerIdOrderByFinishedAtDesc(String playerId, Pageable pageable);

    List<PlayerHandHistory> findByPlayerIdAndVariantOrderByFinishedAtDesc(String playerId, String variant, Pageable pageable);
}
