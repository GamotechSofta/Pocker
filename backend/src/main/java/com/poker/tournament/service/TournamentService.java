package com.poker.tournament.service;

import com.poker.tournament.dto.CreateTournamentRequest;
import com.poker.tournament.dto.TournamentResponse;
import com.poker.tournament.entity.TournamentStatus;
import java.util.UUID;

public interface TournamentService {
    TournamentResponse create(UUID creatorId, CreateTournamentRequest request);
    TournamentResponse get(UUID tournamentId);
    TournamentResponse transition(UUID tournamentId, TournamentStatus target);
}
