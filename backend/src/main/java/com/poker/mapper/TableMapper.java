package com.poker.mapper;

import com.poker.dto.GameEventResponse;
import com.poker.dto.LegalActionsResponse;
import com.poker.dto.PayoutResponse;
import com.poker.dto.PlayerStateResponse;
import com.poker.dto.SeatedPlayerResponse;
import com.poker.dto.ShowdownPlayerResponse;
import com.poker.dto.TableDetailResponse;
import com.poker.dto.TableStateResponse;
import com.poker.dto.TournamentGameInfo;
import com.poker.dto.TableSummaryResponse;
import com.poker.entity.PokerTable;
import com.poker.entity.PokerTablePlayer;
import com.poker.entity.TableStatus;
import com.poker.game.engine.GameState;
import com.poker.game.engine.GameStatus;
import com.poker.game.engine.LegalActions;
import com.poker.game.engine.PlayerState;
import com.poker.game.engine.ShowdownResult;
import com.poker.game.engine.TexasHoldemEngine;
import com.poker.game.model.Card;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TableMapper {

    public static final String HIDDEN = "HIDDEN";

    private TableMapper() {
    }

    public static TableSummaryResponse toSummary(PokerTable table) {
        return new TableSummaryResponse(
                table.getId(),
                table.getName(),
                table.getVariant(),
                table.getSmallBlind(),
                table.getBigBlind(),
                table.getMaxPlayers(),
                table.getPlayers().size(),
                table.getStatus().name(),
                table.isPrivateRoom(),
                table.getRoomCode()
        );
    }

    public static TableDetailResponse toDetail(PokerTable table) {
        List<SeatedPlayerResponse> players = table.getPlayers().stream()
                .map(TableMapper::toSeatedPlayer)
                .toList();
        return new TableDetailResponse(
                table.getId(),
                table.getName(),
                table.getVariant(),
                table.getSmallBlind(),
                table.getBigBlind(),
                table.getStartingChips(),
                table.getMaxPlayers(),
                table.getStatus().name(),
                table.getCreatedById(),
                table.getCreatedAt(),
                players,
                table.getRoomCode(),
                table.isPrivateRoom()
        );
    }

    public static TableStateResponse toSanitizedState(PokerTable table, TexasHoldemEngine engine, UUID viewerId) {
        return toSanitizedState(table, engine, viewerId, null);
    }

    public static TableStateResponse toSanitizedState(
            PokerTable table,
            TexasHoldemEngine engine,
            UUID viewerId,
            Instant turnDeadline
    ) {
        GameState state = engine.state();
        String viewerKey = viewerId.toString();
        boolean revealShowdown = !table.isTournament() && !state.getShowdown().isEmpty();
        Set<String> revealedIds = state.getShowdown().stream()
                .map(ShowdownResult::playerId)
                .collect(Collectors.toSet());

        List<PlayerStateResponse> players = state.getPlayers().stream()
                .map(player -> toPlayerView(player, state, viewerKey, revealShowdown, revealedIds))
                .toList();

        LegalActions legal = engine.legalActions(viewerKey);
        return new TableStateResponse(
                table.getId(),
                table.getName(),
                state.getVariant(),
                state.getStatus().name(),
                state.getHandNumber(),
                state.getDealerSeat(),
                state.getSmallBlindSeat(),
                state.getBigBlindSeat(),
                state.getCurrentTurnSeat(),
                state.getPot(),
                state.getCurrentBet(),
                state.getMinimumRaise(),
                state.getBettingRound().name(),
                state.getTableConfig().smallBlind(),
                state.getTableConfig().bigBlind(),
                state.getCommunityCards().stream().map(Card::code).toList(),
                players,
                state.getEvents().stream()
                        .map(event -> new GameEventResponse(event.type(), event.detail()))
                        .toList(),
                state.getPayouts().stream()
                        .map(payout -> new PayoutResponse(UUID.fromString(payout.playerId()), payout.amount()))
                        .toList(),
                state.getShowdown().stream()
                        .map(result -> new ShowdownPlayerResponse(
                                UUID.fromString(result.playerId()),
                                table.isTournament() && !result.playerId().equals(viewerKey)
                                        ? List.of() : result.holeCards().stream().map(Card::code).toList(),
                                result.evaluation().getRank().name(),
                                result.evaluation().getDescription()
                        ))
                        .toList(),
                new LegalActionsResponse(
                        legal.actions().stream().map(Enum::name).collect(Collectors.toSet()),
                        legal.callAmount(),
                        legal.minBetOrRaiseTo(),
                        legal.maxBetOrRaiseTo()
                ),
                viewerId,
                turnDeadline,
                tournamentInfo(table)
        );
    }

    private static TournamentGameInfo tournamentInfo(PokerTable table) {
        var hand = table.getGameplay();
        if (!table.isTournament() || hand == null) return null;
        var applied = hand.appliedBlind(table.getInitialBlind());
        return new TournamentGameInfo(table.getTournamentId(), table.getTableNumber(), applied == null ? table.getInitialBlind().levelNumber() : applied.levelNumber(),
                hand.revision(), hand.phase().name(), hand.reason(), hand.nextHandAt());
    }

    public static TableStatus lobbyStatus(GameStatus status) {
        return switch (status) {
            case WAITING, NEXT_HAND -> TableStatus.WAITING;
            default -> TableStatus.PLAYING;
        };
    }

    static List<String> holeCardsForViewer(
            PlayerState player,
            String viewerId,
            boolean revealShowdown,
            Set<String> revealedIds
    ) {
        List<Card> holeCards = player.getHoleCards();
        if (holeCards.isEmpty()) {
            return List.of();
        }
        if (player.getPlayerId().equals(viewerId)) {
            return holeCards.stream().map(Card::code).toList();
        }
        if (revealShowdown && revealedIds.contains(player.getPlayerId()) && !player.isFolded()) {
            return holeCards.stream().map(Card::code).toList();
        }
        return holeCards.stream().map(card -> HIDDEN).toList();
    }

    private static SeatedPlayerResponse toSeatedPlayer(PokerTablePlayer player) {
        return new SeatedPlayerResponse(
                player.getUserId(),
                player.getUsername(),
                player.getSeat(),
                player.getChipStack()
        );
    }

    private static PlayerStateResponse toPlayerView(
            PlayerState player,
            GameState state,
            String viewerId,
            boolean revealShowdown,
            Set<String> revealedIds
    ) {
        return new PlayerStateResponse(
                UUID.fromString(player.getPlayerId()),
                player.getUsername(),
                player.getSeat(),
                player.getChipStack(),
                player.getBetThisRound(),
                player.getStatus(state.isHandInProgress()).name(),
                player.isFolded(),
                player.isAllIn(),
                player.getSeat() == state.getCurrentTurnSeat(),
                player.isConnected(),
                holeCardsForViewer(player, viewerId, revealShowdown, revealedIds)
        );
    }
}
