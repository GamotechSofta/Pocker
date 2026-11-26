package com.poker.tournament.service;

import com.poker.tournament.entity.Tournament;
import java.util.UUID;

public interface TournamentLifecycleService {
    void processDueTournaments();
    Tournament start(UUID tournamentId);
}
