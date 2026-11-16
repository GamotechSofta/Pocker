package com.poker.wallet;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletAccountRepository extends MongoRepository<WalletAccount, String> {

    Optional<WalletAccount> findByPlayerId(String playerId);

    boolean existsByPlayerId(String playerId);
}
