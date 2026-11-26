package com.poker.dto;

import java.time.Instant;
import java.util.UUID;

public record TournamentGameInfo(UUID tournamentId, int tableNumber, int blindLevel, long actionVersion,
                                 String phase, String reason, Instant nextHandAt) { }
