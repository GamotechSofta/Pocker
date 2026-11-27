package com.poker.operations;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
public interface OperatorSessionRepository extends MongoRepository<OperatorSession, String> { Optional<OperatorSession> findByPlayerId(String playerId); }
