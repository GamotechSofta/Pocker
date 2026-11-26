package com.poker.tournament.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Private replay evidence for an MTT hand, including the seats that existed when it was played. */
@Document(collection = "tournament_settled_hands")
@CompoundIndex(name = "tournament_table_hand_unique", def = "{'tournamentId': 1, 'tableId': 1, 'hand.number': 1}", unique = true)
public record TournamentSettledHand(@Id UUID id, UUID tournamentId, UUID tableId, TournamentHand hand,
                                    List<Seat> seats, List<VacatedSeat> vacatedAssignments) {
    public record Seat(UUID userId, int seat, int settledStack) { }
    public record VacatedSeat(UUID assignmentId, UUID entrantId, UUID userId, int seat, Instant assignedAt, long assignmentVersion) { }
}
