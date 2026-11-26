package com.poker.tournament.entity;

import java.util.UUID;

/** Evidence behind the entrant's official finishing position. Seat and time remain on the entrant. */
public record TournamentElimination(UUID handId, int handNumber, long openingStack, UUID settlementId, Long settlementSequence) { }
