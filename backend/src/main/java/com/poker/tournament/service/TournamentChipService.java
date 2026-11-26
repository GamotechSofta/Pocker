package com.poker.tournament.service;

import com.poker.tournament.entity.TournamentEntrant;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.UUID;

/** Initial virtual-chip issuance runs in the caller's table-preparation transaction. */
public interface TournamentChipService {
    void issueInitialStack(MongoTemplate transaction, TournamentEntrant entrant, UUID tableId,
                           int seat, int stack, Instant now);
}
