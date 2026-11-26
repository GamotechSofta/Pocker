package com.poker.websocket;

import com.poker.dto.TableStateResponse;
import com.poker.service.TableService;
import com.poker.tournament.service.TournamentGameplayService;
import com.poker.tournament.service.TournamentAssignmentService;
import com.poker.tournament.service.TournamentStandingsService;
import com.poker.tournament.dto.TournamentLeaderboard;
import com.poker.tournament.entity.TournamentEntrantStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameBroadcaster {
    private static final Logger log = LoggerFactory.getLogger(GameBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final TableService tableService;
    private final GameSessionRegistry sessionRegistry;
    private final TournamentGameplayService tournament;
    private final TournamentStandingsService standings;
    private final TournamentAssignmentService assignments;
    private final ConcurrentHashMap<UUID, Long> watchedTournaments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ClockWatch> watchedClocks = new ConcurrentHashMap<>();
    private record ClockWatch(UUID tournamentId, long version, long standingsVersion, long topologyVersion, String finalizationStage) { }

    public GameBroadcaster(
            SimpMessagingTemplate messagingTemplate,
            TableService tableService,
            GameSessionRegistry sessionRegistry,
            TournamentGameplayService tournament,
            TournamentStandingsService standings,
            TournamentAssignmentService assignments
    ) {
        this.messagingTemplate = messagingTemplate;
        this.tableService = tableService;
        this.sessionRegistry = sessionRegistry;
        this.tournament = tournament;
        this.standings = standings;
        this.assignments = assignments;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTableUpdated(TableUpdatedEvent event) {
        broadcast(event.tableId(), event.type());
    }

    public void broadcast(UUID tableId, WsEventType type) {
        for (UUID userId : sessionRegistry.subscribers(tableId)) {
            sendToUser(tableId, userId, type);
        }
    }

    public void sendToUser(UUID tableId, UUID userId, WsEventType type) {
        TableStateResponse state = tableService.getState(tableId, userId);
        sendState(tableId, userId, type, state);
    }

    public void sendTournamentToUser(UUID tableId, UUID userId, WsEventType type) {
        var tournamentId = tournament.subscribedTournament(tableId, userId);
        var notice = tournament.notice(tableId);
        var snapshot = standings.player(tournamentId, userId, 0, 50);
        sendStandings(tableId, userId, snapshot);
        var clock = tournament.clock(tournamentId, userId);
        long topologyVersion = tournament.topologyVersion(tournamentId);
        var progress = tournament.finalizationStage(tournamentId);
        if (topologyVersion >= 0 || progress != null) sendAssignment(tableId, userId, assignments.get(tournamentId, userId));
        if (!isEliminated(snapshot)) {
            sendState(tableId, userId, type, tournament.state(tableId, userId));
            messagingTemplate.convertAndSendToUser(userId.toString(), destination(tableId), WsMessage.clock(WsEventType.CLOCK_INITIALIZED, tableId, clock));
        }
        watchedClocks.putIfAbsent(tableId, new ClockWatch(tournamentId, clock.version(), snapshot.version(), topologyVersion, progress));
        watchedTournaments.putIfAbsent(tableId, notice == null ? 0 : notice.revision());
    }

    /** The simple STOMP broker is local. Every instance follows committed revisions for its subscribers. */
    @Scheduled(fixedDelayString = "${app.tournament.broadcast-poll-ms:250}")
    public void broadcastTournamentRevisions() {
        watchedTournaments.forEach((tableId, known) -> {
            var audience = sessionRegistry.subscribers(tableId);
            if (audience.isEmpty()) {
                watchedTournaments.remove(tableId, known);
                watchedClocks.remove(tableId);
                return;
            }
            try {
                var notice = tournament.notice(tableId);
                var watch = watchedClocks.get(tableId);
                var progress = watch == null ? null : tournament.finalizationStage(watch.tournamentId());
                if ("COMPLETED".equals(progress)) {
                    for (UUID userId : audience) {
                        try {
                            var assignment = assignments.get(watch.tournamentId(), userId);
                            if (assignment.completion() != null) sendAssignment(tableId, userId, assignment);
                        } catch (com.poker.exception.ApiException ignored) { /* Membership is checked again after completion. */ }
                    }
                    sessionRegistry.releaseTable(tableId);
                    watchedClocks.remove(tableId);
                    watchedTournaments.remove(tableId);
                    return;
                }
                var clock = watch == null ? null : tournament.clockNotice(watch.tournamentId());
                long standingsVersion = watch == null ? 0 : standings.version(watch.tournamentId());
                long topologyVersion = watch == null || watch.topologyVersion() < 0 ? -1 : tournament.topologyVersion(watch.tournamentId());
                boolean gameChanged = notice != null && notice.revision() > known;
                boolean clockChanged = clock != null && clock.revision() > watch.version();
                boolean standingsChanged = watch != null && standingsVersion > watch.standingsVersion();
                boolean topologyChanged = watch != null && topologyVersion > watch.topologyVersion();
                boolean finalizationChanged = watch != null && !Objects.equals(progress, watch.finalizationStage());
                if (!gameChanged && !clockChanged && !standingsChanged && !topologyChanged && !finalizationChanged) return;
                for (UUID userId : audience) {
                    try {
                        if (standingsChanged || topologyChanged) {
                            var snapshot = standings.player(watch.tournamentId(), userId, 0, 50);
                            sendStandings(tableId, userId, snapshot);
                            if (isEliminated(snapshot)) continue;
                        }
                        if (finalizationChanged || topologyChanged || (topologyVersion >= 0 && gameChanged
                                && (notice.event() == WsEventType.HAND_COMPLETED || notice.event() == WsEventType.SHOWDOWN))) {
                            var assignment = assignments.get(watch.tournamentId(), userId);
                            sendAssignment(tableId, userId, assignment);
                            if (!tableId.equals(assignment.tableId())) continue;
                        }
                        if (gameChanged) sendState(tableId, userId, notice.event(), tournament.state(tableId, userId));
                        if (watch != null && (clockChanged || (gameChanged && notice.event() == WsEventType.BLINDS_APPLIED))) {
                            var snapshot = tournament.clock(watch.tournamentId(), userId);
                            var type = clockChanged ? clock.event() : WsEventType.BLINDS_APPLIED;
                            messagingTemplate.convertAndSendToUser(userId.toString(), destination(tableId), WsMessage.clock(type, tableId, snapshot));
                        }
                    } catch (com.poker.exception.ApiException ex) {
                        if (!"TOURNAMENT_ELIMINATED".equals(ex.getCode())) sendError(tableId, userId, ex.getCode(), ex.getMessage());
                    }
                }
                if (gameChanged) watchedTournaments.replace(tableId, known, notice.revision());
                if (clockChanged || standingsChanged || topologyChanged || finalizationChanged) watchedClocks.replace(tableId, watch, new ClockWatch(watch.tournamentId(),
                        clockChanged ? clock.revision() : watch.version(), standingsVersion, topologyVersion, progress));
            } catch (RuntimeException failed) {
                log.warn("Tournament update deferred for {}: {}", tableId, failed.getClass().getSimpleName());
            }
        });
    }

    private static boolean isEliminated(TournamentLeaderboard snapshot) {
        return snapshot.viewer() != null && snapshot.viewer().status() == TournamentEntrantStatus.ELIMINATED;
    }

    private void sendStandings(UUID tableId, UUID userId, TournamentLeaderboard snapshot) {
        messagingTemplate.convertAndSendToUser(userId.toString(), destination(tableId), WsMessage.leaderboard(tableId, snapshot));
    }

    private void sendAssignment(UUID tableId, UUID userId, com.poker.tournament.dto.TournamentAssignment snapshot) {
        messagingTemplate.convertAndSendToUser(userId.toString(), destination(tableId), WsMessage.assignment(tableId, snapshot));
    }

    private void sendState(UUID tableId, UUID userId, WsEventType type, TableStateResponse state) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                destination(tableId),
                WsMessage.of(type, tableId, state)
        );
    }

    public void sendError(UUID tableId, UUID userId, String code, String message) {
        WsMessage error = WsMessage.error(tableId, code, message);
        if (tableId != null) {
            messagingTemplate.convertAndSendToUser(userId.toString(), destination(tableId), error);
        }
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/errors", error);
    }

    static String destination(UUID tableId) {
        return "/queue/game/" + tableId;
    }
}
