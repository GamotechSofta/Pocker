package com.poker.bot;

import com.poker.service.TableService;
import com.poker.websocket.TableUpdatedEvent;
import com.poker.websocket.WsEventType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class BotOrchestrator {

    /** Bots take seats one by one, a little apart, so arrivals read like a filling lobby. */
    private static final long FILL_DELAY_MIN_MS = 900;
    private static final long FILL_DELAY_MAX_MS = 1_500;
    /** Thinking time between bot actions — keeps play readable. */
    private static final long ACT_DELAY_MIN_MS = 1_800;
    private static final long ACT_DELAY_MAX_MS = 3_200;
    /** Matches the client's result-page countdown before the next deal. */
    private static final long RESULT_HOLD_MS = 7_000;
    /** Grace after the deadline so the client's own timeout does not race the server. */
    private static final long TIMEOUT_GRACE_MS = 300;

    private final BotService botService;
    private final TableService tableService;
    private final TaskScheduler taskScheduler;
    private final Map<UUID, ScheduledFuture<?>> pendingAct = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> pendingFill = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> pendingTimeout = new ConcurrentHashMap<>();

    public BotOrchestrator(
            BotService botService,
            TableService tableService,
            @Qualifier("botTaskScheduler") TaskScheduler taskScheduler
    ) {
        this.botService = botService;
        this.tableService = tableService;
        this.taskScheduler = taskScheduler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTableUpdated(TableUpdatedEvent event) {
        UUID tableId = event.tableId();
        WsEventType type = event.type();
        scheduleTurnTimeout(tableId);
        if (type == WsEventType.PLAYER_JOINED || type == WsEventType.PLAYER_LEFT) {
            long fillDelay = ThreadLocalRandom.current().nextLong(FILL_DELAY_MIN_MS, FILL_DELAY_MAX_MS + 1);
            scheduleFill(tableId, fillDelay);
            scheduleAct(tableId, fillDelay + 400);
            return;
        }
        if (type == WsEventType.HAND_COMPLETED || type == WsEventType.SHOWDOWN || type == WsEventType.POT_AWARDED) {
            // Do not refill/start immediately — give the client time to show the result page.
            scheduleAct(tableId, RESULT_HOLD_MS);
            return;
        }
        if (type == WsEventType.HAND_STARTED) {
            // Players must first see the complete deal from the dealer's hands.
            scheduleAct(tableId, botService.dealAnimationDelayMs(tableId));
            return;
        }
        // Betting / street / turn changes: only the bot needs to act (if it is their turn).
        scheduleAct(tableId, randomActDelay());
    }

    private void scheduleTurnTimeout(UUID tableId) {
        cancel(pendingTimeout.remove(tableId));
        Instant deadline;
        try {
            deadline = tableService.turnDeadline(tableId).orElse(null);
        } catch (RuntimeException ex) {
            return;
        }
        if (deadline == null) {
            return;
        }
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> {
                    pendingTimeout.remove(tableId);
                    try {
                        tableService.enforceTurnTimeout(tableId);
                    } catch (RuntimeException ignored) {
                        // a later update reschedules the clock
                    }
                },
                deadline.plusMillis(TIMEOUT_GRACE_MS)
        );
        pendingTimeout.put(tableId, future);
    }

    private void scheduleFill(UUID tableId, long delayMs) {
        cancel(pendingFill.remove(tableId));
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> {
                    pendingFill.remove(tableId);
                    try {
                        botService.fillAndMaybeStart(tableId);
                    } catch (RuntimeException ignored) {
                        // next event retries
                    }
                },
                Instant.now().plusMillis(delayMs)
        );
        pendingFill.put(tableId, future);
    }

    private void scheduleAct(UUID tableId, long delayMs) {
        cancel(pendingAct.remove(tableId));
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> {
                    pendingAct.remove(tableId);
                    try {
                        botService.maybeAct(tableId);
                    } catch (RuntimeException ignored) {
                        // next event retries
                    }
                },
                Instant.now().plusMillis(delayMs)
        );
        pendingAct.put(tableId, future);
    }

    private static long randomActDelay() {
        return ThreadLocalRandom.current().nextLong(ACT_DELAY_MIN_MS, ACT_DELAY_MAX_MS + 1);
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }
}
