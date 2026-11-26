package com.poker.tournament.service;

import com.poker.tournament.entity.TournamentElimination;
import com.poker.tournament.entity.TournamentEntrant;
import com.poker.tournament.entity.TournamentHand;
import com.poker.tournament.entity.TournamentStandingCheckpoint;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class TournamentEliminationService {
    record Settled(TournamentEntrant entrant, long openingStack, UUID ledgerId) { }

    TournamentStandingCheckpoint rank(TournamentHand hand, List<Settled> field, int otherActive, int sequence, Instant now) {
        var survivors = field.stream().map(Settled::entrant).filter(entry -> entry.getCurrentVirtualChips() > 0).toList();
        if (survivors.isEmpty()) throw new IllegalStateException("A settled tournament hand must have a surviving stack.");

        // A pre-Phase 9 zero stack may not have evidence for its original elimination hand.
        if (field.stream().anyMatch(row -> row.openingStack() == 0)) {
            return new TournamentStandingCheckpoint(sequence, hand.id(), sequence, now, List.of(), null, true);
        }

        var busted = field.stream().filter(row -> row.entrant().getCurrentVirtualChips() == 0)
                .sorted(Comparator.comparingLong(Settled::openingStack).reversed().thenComparingInt(row -> row.entrant().getSeat()))
                .toList();
        var eliminated = new ArrayList<UUID>(busted.size());
        int position = otherActive + survivors.size() + 1;
        for (var row : busted) {
            var entry = row.entrant();
            entry.eliminate(position++, new TournamentElimination(hand.id(), hand.number(), row.openingStack(), row.ledgerId(), (long) sequence), now);
            eliminated.add(entry.getId());
        }
        var candidate = otherActive + survivors.size() == 1 ? survivors.getFirst() : null;
        return new TournamentStandingCheckpoint(sequence, hand.id(), sequence, now, List.copyOf(eliminated),
                candidate == null ? null : new TournamentStandingCheckpoint.Candidate(candidate.getId(), candidate.getUserId(), candidate.getCurrentVirtualChips()), false);
    }
}
