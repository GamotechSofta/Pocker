package com.poker.history;

import com.poker.entity.PokerTable;
import com.poker.exception.ApiException;
import com.poker.exception.ErrorCodes;
import com.poker.game.engine.GameState;
import com.poker.game.engine.GameStatus;
import com.poker.game.engine.Payout;
import com.poker.game.engine.PlayerState;
import com.poker.game.engine.ShowdownResult;
import com.poker.game.model.Card;
import com.poker.game.variants.Variants;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class HandHistoryService {

    public static final int HISTORY_SCAN_LIMIT = 500;
    private static final int MAX_PAGE_SIZE = 50;

    public record DepartedParticipant(
            String playerId,
            String username,
            int finalChipStack,
            int netChips,
            int potAtLeave,
            List<String> holeCards,
            List<String> communityCards
    ) {
        public DepartedParticipant {
            holeCards = holeCards == null ? List.of() : List.copyOf(holeCards);
            communityCards = communityCards == null ? List.of() : List.copyOf(communityCards);
        }
    }

    private final PlayerHandHistoryRepository repository;

    public HandHistoryService(PlayerHandHistoryRepository repository) {
        this.repository = repository;
    }

    public void recordMidHandLeave(PokerTable table, int handNumber, DepartedParticipant departed, boolean isHuman) {
        if (!isHuman || table == null || handNumber <= 0 || departed == null) {
            return;
        }
        String tableId = table.getId().toString();
        String id = historyId(tableId, handNumber, departed.playerId());
        PlayerHandHistory.Participant self = new PlayerHandHistory.Participant(
                departed.playerId(),
                departed.username(),
                "FOLDED",
                0,
                departed.netChips(),
                "Left table mid-hand",
                departed.holeCards()
        );
        PlayerHandHistory doc = new PlayerHandHistory(
                id,
                departed.playerId(),
                tableId,
                table.getName(),
                table.getVariant(),
                handNumber,
                table.getSmallBlind(),
                table.getBigBlind(),
                "FOLDED",
                0,
                departed.netChips(),
                departed.potAtLeave(),
                departed.holeCards(),
                departed.communityCards(),
                "Left table before hand ended",
                "Left table",
                List.of(),
                Math.max(1, table.getPlayers().size() + 1),
                List.of(self),
                Instant.now()
        );
        repository.save(doc);
    }

    public void recordCompletedHand(
            PokerTable table,
            GameState state,
            Map<String, Integer> startStacks,
            List<DepartedParticipant> departed,
            Set<String> humanPlayerIds
    ) {
        if (table == null || state == null || state.getHandNumber() <= 0 || state.getStatus() != GameStatus.NEXT_HAND) {
            return;
        }
        if (humanPlayerIds == null || humanPlayerIds.isEmpty()) {
            return;
        }

        String tableId = table.getId().toString();
        int handNumber = state.getHandNumber();
        Instant now = Instant.now();

        Map<String, Integer> payoutByPlayer = new HashMap<>();
        int payoutSum = 0;
        for (Payout payout : state.getPayouts()) {
            payoutByPlayer.merge(payout.playerId(), payout.amount(), Integer::sum);
            payoutSum += payout.amount();
        }
        int potAmount = payoutSum > 0 ? payoutSum : state.getPot();

        Map<String, ShowdownResult> showdownByPlayer = new LinkedHashMap<>();
        for (ShowdownResult result : state.getShowdown()) {
            showdownByPlayer.put(result.playerId(), result);
        }

        Set<String> winnerIds = new LinkedHashSet<>();
        for (var entry : payoutByPlayer.entrySet()) {
            if (entry.getValue() > 0) {
                winnerIds.add(entry.getKey());
            }
        }
        boolean splitPot = winnerIds.size() > 1;

        List<String> winnerNames = new ArrayList<>();
        for (PlayerState player : state.getPlayers()) {
            if (winnerIds.contains(player.getPlayerId())) {
                winnerNames.add(player.getUsername());
            }
        }

        String winningHandDescription = resolveWinningDescription(showdownByPlayer, winnerIds);
        List<String> boardCards = toCardCodes(state.getCommunityCards());

        Map<String, Integer> safeStartStacks = startStacks == null ? Map.of() : startStacks;
        List<HandRecord> records = new ArrayList<>();
        Set<String> seenPlayers = new LinkedHashSet<>();

        for (PlayerState player : state.getPlayers()) {
            String playerId = player.getPlayerId();
            if (!seenPlayers.add(playerId)) {
                continue;
            }
            int won = payoutByPlayer.getOrDefault(playerId, 0);
            String outcome = won > 0 ? (splitPot ? "SPLIT" : "WON") : (player.isFolded() ? "FOLDED" : "LOST");
            int startStack = safeStartStacks.getOrDefault(playerId, player.getChipStack());
            int netChips = player.getChipStack() - startStack;
            ShowdownResult shown = showdownByPlayer.get(playerId);
            String yourDesc = shown != null
                    ? shown.evaluation().getDescription()
                    : (won > 0 ? "Won by fold" : (player.isFolded() ? "Folded" : null));
            List<String> ownCards = shown != null ? toCardCodes(shown.holeCards()) : toCardCodes(player.getHoleCards());
            List<String> publicCards = shown != null ? toCardCodes(shown.holeCards()) : List.of();
            records.add(new HandRecord(playerId, player.getUsername(), outcome, won, netChips, yourDesc, ownCards, publicCards));
        }

        if (departed != null) {
            for (DepartedParticipant left : departed) {
                if (!seenPlayers.add(left.playerId())) {
                    continue;
                }
                records.add(new HandRecord(
                        left.playerId(),
                        left.username(),
                        "FOLDED",
                        0,
                        left.netChips(),
                        "Left table",
                        left.holeCards(),
                        List.of()
                ));
            }
        }

        for (HandRecord target : records) {
            if (!humanPlayerIds.contains(target.playerId())) {
                continue;
            }
            List<PlayerHandHistory.Participant> participants = records.stream()
                    .map(entry -> new PlayerHandHistory.Participant(
                            entry.playerId(),
                            entry.username(),
                            entry.outcome(),
                            entry.wonAmount(),
                            entry.netChips(),
                            entry.handDescription(),
                            entry.playerId().equals(target.playerId()) ? entry.ownCards() : entry.publicCards()
                    ))
                    .toList();

            PlayerHandHistory doc = new PlayerHandHistory(
                    historyId(tableId, handNumber, target.playerId()),
                    target.playerId(),
                    tableId,
                    table.getName(),
                    table.getVariant(),
                    handNumber,
                    table.getSmallBlind(),
                    table.getBigBlind(),
                    target.outcome(),
                    target.wonAmount(),
                    target.netChips(),
                    potAmount,
                    target.ownCards(),
                    boardCards,
                    winningHandDescription,
                    target.handDescription(),
                    winnerNames,
                    records.size(),
                    participants,
                    now
            );
            repository.save(doc);
        }
    }

    public HandHistoryDtos.HistoryPage getPlayerHistory(
            String playerId,
            String variantParam,
            String resultParam,
            int page,
            int size
    ) {
        String variant = normalizeVariant(variantParam);
        String resultFilter = normalizeResult(resultParam);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));

        PageRequest scan = PageRequest.of(0, HISTORY_SCAN_LIMIT);
        List<PlayerHandHistory> scoped = "ALL".equals(variant)
                ? repository.findByPlayerIdOrderByFinishedAtDesc(playerId, scan)
                : repository.findByPlayerIdAndVariantOrderByFinishedAtDesc(playerId, variant, scan);

        long totalHands = scoped.size();
        long handsWon = 0;
        long handsLost = 0;
        long handsFolded = 0;
        long netChips = 0;
        int biggestWin = 0;

        for (PlayerHandHistory row : scoped) {
            netChips += row.getNetChips();
            if (row.getNetChips() > biggestWin) {
                biggestWin = row.getNetChips();
            }
            String out = row.getOutcome();
            if ("WON".equals(out) || "SPLIT".equals(out)) {
                handsWon++;
            } else if ("FOLDED".equals(out)) {
                handsFolded++;
            } else {
                handsLost++;
            }
        }

        Double winRate = totalHands == 0 ? null : Math.round((handsWon * 1000.0) / totalHands) / 10.0;
        HandHistoryDtos.Summary summary = new HandHistoryDtos.Summary(
                totalHands,
                handsWon,
                handsLost,
                handsFolded,
                winRate,
                netChips,
                biggestWin
        );

        List<PlayerHandHistory> filtered = scoped.stream()
                .filter(row -> matchesResult(row.getOutcome(), resultFilter))
                .toList();

        long totalFiltered = filtered.size();
        long totalPages = totalFiltered == 0 ? 0 : Math.ceilDiv(totalFiltered, safeSize);
        int fromIndex = Math.min((int) totalFiltered, safePage * safeSize);
        int toIndex = Math.min((int) totalFiltered, fromIndex + safeSize);

        List<HandHistoryDtos.HandItem> rows = filtered.subList(fromIndex, toIndex).stream()
                .map(HandHistoryService::toItem)
                .toList();

        return new HandHistoryDtos.HistoryPage(
                variant,
                resultFilter,
                summary,
                rows,
                safePage,
                safeSize,
                totalFiltered,
                totalPages
        );
    }

    private static String normalizeVariant(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) {
            return "ALL";
        }
        try {
            return Variants.create(raw.trim()).getName();
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private static String normalizeResult(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ALL";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ALL", "WON", "LOST", "FOLDED" -> normalized;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST,
                    "Unsupported history filter. Use ALL, WON, LOST, or FOLDED.");
        };
    }

    private static boolean matchesResult(String outcome, String filter) {
        return switch (filter) {
            case "ALL" -> true;
            case "WON" -> "WON".equals(outcome) || "SPLIT".equals(outcome);
            case "LOST" -> "LOST".equals(outcome);
            case "FOLDED" -> "FOLDED".equals(outcome);
            default -> true;
        };
    }

    private static String resolveWinningDescription(Map<String, ShowdownResult> showdownByPlayer, Set<String> winnerIds) {
        for (String winnerId : winnerIds) {
            ShowdownResult res = showdownByPlayer.get(winnerId);
            if (res != null && res.evaluation() != null && res.evaluation().getDescription() != null) {
                return res.evaluation().getDescription();
            }
        }
        if (!showdownByPlayer.isEmpty()) {
            ShowdownResult any = showdownByPlayer.values().iterator().next();
            if (any.evaluation() != null && any.evaluation().getDescription() != null) {
                return any.evaluation().getDescription();
            }
        }
        return "Won uncontested (all folded)";
    }

    private static List<String> toCardCodes(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        return cards.stream().map(Card::code).toList();
    }

    private static HandHistoryDtos.HandItem toItem(PlayerHandHistory row) {
        List<HandHistoryDtos.ParticipantItem> participants = row.getParticipants().stream()
                .map(p -> new HandHistoryDtos.ParticipantItem(
                        p.playerId(),
                        p.username(),
                        p.outcome(),
                        p.wonAmount(),
                        p.netChips(),
                        p.handDescription(),
                        p.holeCards()
                ))
                .toList();
        return new HandHistoryDtos.HandItem(
                row.getId(),
                row.getTableId(),
                row.getTableName(),
                row.getVariant(),
                row.getHandNumber(),
                row.getSmallBlind(),
                row.getBigBlind(),
                row.getOutcome(),
                row.getWonAmount(),
                row.getNetChips(),
                row.getPotAmount(),
                row.getPlayerHoleCards(),
                row.getCommunityCards(),
                row.getHandDescription(),
                row.getYourHandDescription(),
                row.getWinnerNames(),
                row.getPlayerCount(),
                participants,
                row.getFinishedAt()
        );
    }

    private static String historyId(String tableId, int handNumber, String playerId) {
        return tableId + "_" + handNumber + "_" + playerId;
    }

    private record HandRecord(
            String playerId,
            String username,
            String outcome,
            int wonAmount,
            int netChips,
            String handDescription,
            List<String> ownCards,
            List<String> publicCards
    ) {
    }
}
