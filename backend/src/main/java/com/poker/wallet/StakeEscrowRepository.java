package com.poker.wallet;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StakeEscrowRepository extends MongoRepository<StakeEscrow, String> {

    Optional<StakeEscrow> findByIdAndStatus(String id, String status);

    List<StakeEscrow> findByPlayerIdAndStatus(String playerId, String status);

    List<StakeEscrow> findByTableIdAndStatus(String tableId, String status);

    List<StakeEscrow> findByStatus(String status);
}
