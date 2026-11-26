package com.poker.tournament.service;

import com.poker.tournament.entity.TournamentChipLedgerEntry;
import com.poker.tournament.entity.TournamentEntrant;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class DefaultTournamentChipService implements TournamentChipService {
    @Override
    public void issueInitialStack(MongoTemplate tx, TournamentEntrant entrant, UUID tableId, int seat, int stack, Instant now) {
        entrant.prepareSeat(tableId, seat, stack, now);
        tx.save(entrant);
        tx.insert(new TournamentChipLedgerEntry(entrant.getTournamentId(), entrant.getId(),
                TournamentChipLedgerEntry.EntryType.INITIAL_STACK, stack, stack,
                "initial:" + entrant.getId(), tableId));
    }
}
