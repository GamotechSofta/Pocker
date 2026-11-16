package com.poker.wallet;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends MongoRepository<WalletTransaction, String> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<WalletTransaction> findByPlayerIdOrderByCreatedAtDesc(String playerId, Pageable pageable);

    List<WalletTransaction> findByGameId(String gameId);
}
