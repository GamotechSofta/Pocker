package com.poker.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poker.dto.TableStateResponse;
import com.poker.tournament.dto.TournamentClockResponse;
import com.poker.tournament.dto.TournamentAssignment;
import com.poker.tournament.dto.TournamentLeaderboard;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsMessage(
        String type,
        UUID tableId,
        TableStateResponse state,
        String code,
        String message,
        TournamentClockResponse clock,
        TournamentLeaderboard leaderboard,
        TournamentAssignment assignment
) {

    public static WsMessage of(WsEventType type, UUID tableId, TableStateResponse state) {
        return new WsMessage(type.name(), tableId, state, null, null, null, null, null);
    }

    public static WsMessage error(UUID tableId, String code, String message) {
        return new WsMessage(WsEventType.ERROR.name(), tableId, null, code, message, null, null, null);
    }

    public static WsMessage clock(WsEventType type, UUID tableId, TournamentClockResponse clock) {
        return new WsMessage(type.name(), tableId, null, null, null, clock, null, null);
    }

    public static WsMessage leaderboard(UUID tableId, TournamentLeaderboard snapshot) {
        var type = snapshot.candidateEntrantId() != null ? WsEventType.TOURNAMENT_AWAITING_FINALIZATION
                : snapshot.recentEliminations().isEmpty() ? WsEventType.LEADERBOARD_UPDATED : WsEventType.PLAYER_ELIMINATED;
        return new WsMessage(type.name(), tableId, null, null, null, null, snapshot, null);
    }

    public static WsMessage assignment(UUID tableId, TournamentAssignment snapshot) {
        var transfer = snapshot.transfer();
        var type = snapshot.completion() != null ? WsEventType.TOURNAMENT_COMPLETED
                : "CLAIMED".equals(snapshot.finalizationStage()) ? WsEventType.TOURNAMENT_FINALIZING
                : snapshot.finalTable() ? WsEventType.FINAL_TABLE_READY
                : snapshot.finalTablePreparing() && transfer == null ? WsEventType.FINAL_TABLE_PREPARING
                : transfer == null ? WsEventType.TABLE_ASSIGNMENT_CHANGED
                : switch (transfer.status()) {
                    case "WAITING_FOR_HANDS" -> WsEventType.TABLE_TRANSFER_PENDING;
                    case "DESTINATION_RESERVED" -> WsEventType.DESTINATION_RESERVED;
                    case "RECOVERY_REQUIRED" -> WsEventType.TABLE_TRANSFER_RECOVERY_REQUIRED;
                    default -> WsEventType.TABLE_ASSIGNMENT_CHANGED;
                };
        return new WsMessage(type.name(), tableId, null, null, null, null, null, snapshot);
    }
}
