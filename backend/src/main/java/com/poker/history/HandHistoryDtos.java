package com.poker.history;

import java.time.Instant;
import java.util.List;

public final class HandHistoryDtos {

    private HandHistoryDtos() {
    }

    public record Summary(
            long totalHands,
            long handsWon,
            long handsLost,
            long handsFolded,
            Double winRatePercent,
            long netChips,
            int biggestWin
    ) {
    }

    public record ParticipantItem(
            String playerId,
            String username,
            String outcome,
            int wonAmount,
            int netChips,
            String handDescription,
            List<String> holeCards
    ) {
    }

    public record HandItem(
            String id,
            String tableId,
            String tableName,
            String variant,
            int handNumber,
            int smallBlind,
            int bigBlind,
            String outcome,
            int wonAmount,
            int netChips,
            int potAmount,
            List<String> playerHoleCards,
            List<String> communityCards,
            String handDescription,
            String yourHandDescription,
            List<String> winnerNames,
            int playerCount,
            List<ParticipantItem> participants,
            Instant finishedAt
    ) {
    }

    public record HistoryPage(
            String variant,
            String result,
            Summary summary,
            List<HandItem> rows,
            int page,
            int size,
            long total,
            long totalPages
    ) {
    }
}
