package com.poker.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record TableStateResponse(
        UUID tableId,
        String name,
        String variant,
        String status,
        int handNumber,
        int dealerSeat,
        int smallBlindSeat,
        int bigBlindSeat,
        int currentTurnSeat,
        int pot,
        int currentBet,
        int minimumRaise,
        String bettingRound,
        int smallBlind,
        int bigBlind,
        List<String> communityCards,
        List<PlayerStateResponse> players,
        List<GameEventResponse> events,
        List<PayoutResponse> payouts,
        List<ShowdownPlayerResponse> showdown,
        LegalActionsResponse legalActions,
        UUID viewerPlayerId,
        Instant turnDeadline
) {
}
