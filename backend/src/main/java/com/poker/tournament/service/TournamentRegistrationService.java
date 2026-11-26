package com.poker.tournament.service;

import com.poker.tournament.dto.TournamentRegistration;
import java.util.UUID;

public interface TournamentRegistrationService {
    TournamentRegistration.Status register(UUID tournamentId, UUID userId);
    TournamentRegistration.Status unregister(UUID tournamentId, UUID userId);
    TournamentRegistration.Status registration(UUID tournamentId, UUID userId);
    TournamentRegistration.WaitingRoom waitingRoom(UUID tournamentId, UUID userId);
    TournamentRegistration.Invitation resolve(String inviteCode);
    TournamentRegistration.Status registerPrivate(UUID tournamentId, UUID userId, String inviteCode);
}
