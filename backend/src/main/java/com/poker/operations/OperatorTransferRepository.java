package com.poker.operations;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
public interface OperatorTransferRepository extends MongoRepository<OperatorTransfer, String> {
    List<OperatorTransfer> findByPlayerIdAndRoundIdOrderByCreatedAtAsc(String playerId, String roundId);
    List<OperatorTransfer> findByPlayerIdAndStateOrderByCreatedAtAsc(String playerId, OperatorTransfer.State state, Pageable pageable);
}
