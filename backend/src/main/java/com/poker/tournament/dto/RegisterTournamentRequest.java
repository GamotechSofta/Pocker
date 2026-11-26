package com.poker.tournament.dto;

import jakarta.validation.constraints.Size;

public record RegisterTournamentRequest(@Size(max = 64) String requestId) { }
