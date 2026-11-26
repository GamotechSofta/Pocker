package com.poker.tournament.dto;

import java.time.Instant;
import java.util.UUID;

public final class TournamentRegistration {
    private TournamentRegistration() { }

    public record InviteRequest(String code, UUID tournamentId) { }

    public record Invitation(TournamentDiscovery.Card tournament, String description, int seatsPerTable, Instant serverTime) { }

    public record Status(TournamentDiscovery.Card tournament, int remainingSeats, boolean withdrawalAllowed, Instant serverTime) { }

    public record WaitingRoom(TournamentDiscovery.Details details, int remainingSeats, boolean withdrawalAllowed,
                              TournamentAssignment assignment) { }
}
