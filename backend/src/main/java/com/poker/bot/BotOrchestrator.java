package com.poker.bot;

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

    /** Seat a bot shortly after a human joins. */
    private static final long FILL_DELAY_MS = 500;
    /** Thinking time between bot actions — keeps play readable. */
    private static final long ACT_DELAY_MIN_MS = 1_800;
    private static final long ACT_DELAY_MAX_MS = 3_200;
    /** Hold the finished hand on screen so win/lose can be seen before next deal. */
    private static final long RESULT_HOLD_MS = 4_500;

    private final BotService botService;
    private final TaskScheduler taskScheduler;
    private final Map<UUID, ScheduledFuture<?>> pendingAct = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> pendingFill = new ConcurrentHashMap<>();

    public BotOrchestrator(BotService botService, @Qualifier("botTaskScheduler") TaskScheduler taskScheduler) {
        this.botService = botService;
        this.taskScheduler = taskScheduler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTableUpdated(TableUpdatedEvent event) {
        UUID tableId = event.tableId();
        WsEventType type = event.type();
        if (type == WsEventType.PLAYER_JOINED || type == WsEventType.PLAYER_LEFT) {
            scheduleFill(tableId);
            scheduleAct(tableId, FILL_DELAY_MS + 400);
            return;
        }
        if (type == WsEventType.HAND_COMPLETED || type == WsEventType.SHOWDOWN || type == WsEventType.POT_AWARDED) {
            // Do not refill/start immediately — give the client time to show win/lose.
            scheduleAct(tableId, RESULT_HOLD_MS);
            return;
        }
        if (type == WsEventType.HAND_STARTED) {
            // The engine is already ready, but players must first see the complete
            // clockwise deal. Start the first bot turn only after that animation.
            scheduleAct(tableId, botService.dealAnimationDelayMs(tableId));
            return;
        }
        // Betting / street / turn changes: only the bot needs to act (if it is their turn).
        scheduleAct(tableId, randomActDelay());
    }

    private void scheduleFill(UUID tableId) {
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
                Instant.now().plusMillis(FILL_DELAY_MS)
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
