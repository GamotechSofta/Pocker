package com.poker.tournament.service;

import com.poker.entity.PokerTable;
import com.poker.exception.ApiException;
import com.poker.tournament.dto.TournamentClockResponse;
import com.poker.tournament.entity.BlindLevel;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentBlindClock;
import com.poker.tournament.entity.TournamentBlindSchedule;
import com.poker.tournament.entity.TournamentHand;
import com.poker.tournament.entity.TournamentStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class TournamentBlindClockService {
    static final String ADMIN = "ADMINISTRATIVE";

    public TournamentBlindSchedule schedule(MongoTemplate tx, Tournament tournament) {
        var schedule = tx.findOne(Query.query(Criteria.where("tournamentId").is(tournament.getId())), TournamentBlindSchedule.class);
        if (schedule == null || schedule.getLevels() == null || schedule.getLevels().isEmpty() || schedule.getLevels().size() > 100) {
            throw new IllegalArgumentException("A tournament requires 1 through 100 blind levels.");
        }
        for (int i = 0; i < schedule.getLevels().size(); i++) {
            var level = schedule.getLevels().get(i);
            if (level.levelNumber() != i + 1 || level.bigBlind() > 1_000_000_000 || level.ante() > tournament.getStartingVirtualChips()) {
                throw new IllegalArgumentException("Invalid ordered blind schedule or amount.");
            }
        }
        return schedule;
    }

    public TournamentBlindClock initialize(TournamentBlindSchedule schedule, Instant now) {
        var levels = schedule.getLevels();
        return new TournamentBlindClock(schedule.getId(), levels, now, 1, now,
                levels.size() == 1 ? null : now.plusSeconds(levels.getFirst().durationSeconds()),
                0, null, null, null, 1, "CLOCK_INITIALIZED");
    }

    public TournamentBlindClock current(MongoTemplate tx, Tournament event, PokerTable table, Instant now) {
        var existing = event.getBlindClock();
        if (existing != null) return existing;
        // Phase 7 tables did not store a clock. Their first hand still replays with the original blinds.
        var started = event.getStartedAt() != null ? event.getStartedAt() : table.getGameplay().activatedAt();
        var value = initialize(schedule(tx, event), started);
        if (event.getStatus() == TournamentStatus.PAUSED) {
            var stopped = event.getUpdatedAt() == null ? now : event.getUpdatedAt();
            value = freeze(advance(value, stopped), stopped, table.getGameplay().reason() == null ? "RECOVERY" : table.getGameplay().reason());
        }
        return value;
    }

    public TournamentBlindClock advance(TournamentBlindClock value, Instant now) {
        if (value.pausedAt() != null || value.levelEndsAt() == null || now.isBefore(value.levelEndsAt())) return value;
        int number = value.scheduledLevel();
        Instant start = value.levelStartedAt();
        Instant end = value.levelEndsAt();
        while (end != null && !now.isBefore(end)) {
            number++;
            start = end;
            end = number == value.levels().size() ? null : start.plusSeconds(value.levels().get(number - 1).durationSeconds());
        }
        return new TournamentBlindClock(value.scheduleId(), value.levels(), value.startedAt(), number, start, end,
                value.pausedMillis(), null, null, value.pauseRequestedAt(), value.version() + 1, "BLIND_LEVEL_ADVANCED");
    }

    public TournamentBlindClock requestPause(TournamentBlindClock value, Instant now) {
        if (value.pauseRequestedAt() != null) return value;
        return new TournamentBlindClock(value.scheduleId(), value.levels(), value.startedAt(), value.scheduledLevel(), value.levelStartedAt(),
                value.levelEndsAt(), value.pausedMillis(), null, null, now, value.version() + 1, "PAUSE_REQUESTED");
    }

    public TournamentBlindClock freeze(TournamentBlindClock value, Instant now, String reason) {
        return new TournamentBlindClock(value.scheduleId(), value.levels(), value.startedAt(), value.scheduledLevel(), value.levelStartedAt(),
                value.levelEndsAt(), value.pausedMillis(), value.pausedAt() == null ? now : value.pausedAt(), reason, null,
                value.version() + 1, "TOURNAMENT_PAUSED");
    }

    public TournamentBlindClock resume(TournamentBlindClock value, Instant now) {
        if (!ADMIN.equals(value.pauseReason())) {
            throw new ApiException(HttpStatus.CONFLICT, "TOURNAMENT_CANNOT_RESUME", "This gameplay pause requires recovery review.");
        }
        long paused = millis(value.pausedAt(), now);
        return new TournamentBlindClock(value.scheduleId(), value.levels(), value.startedAt(), value.scheduledLevel(),
                value.levelStartedAt().plusMillis(paused), value.levelEndsAt() == null ? null : value.levelEndsAt().plusMillis(paused),
                value.pausedMillis() + paused, null, null, null, value.version() + 1, "TOURNAMENT_RESUMED");
    }

    /** Writes the same tournament document used by the scheduler, pause, and hand boundary transactions. */
    public void persist(MongoTemplate tx, UUID tournamentId, TournamentBlindClock value, TournamentStatus status, Instant now) {
        tx.updateFirst(Query.query(Criteria.where("id").is(tournamentId)), new Update().set("blindClock", value)
                .set("status", status).set("updatedAt", now).inc("registrationRevision", 1), Tournament.class);
    }

    public TournamentClockResponse snapshot(UUID id, TournamentStatus status, TournamentBlindClock value, PokerTable table, Instant now) {
        var applied = table.getGameplay().appliedBlind(table.getInitialBlind());
        Instant time = value.pausedAt() == null ? now : value.pausedAt();
        Long remaining = value.levelEndsAt() == null ? null : millis(time, value.levelEndsAt());
        String mode = value.pausedAt() == null ? value.pauseRequestedAt() == null ? "ACTIVE" : "PAUSE_PENDING" : "PAUSED";
        return new TournamentClockResponse(id, status, mode, value.startedAt(), now, value.levelStartedAt(), value.levelEndsAt(),
                remaining, Math.max(0, millis(value.startedAt(), time) - value.pausedMillis()),
                value.pausedMillis() + (value.pausedAt() == null ? 0 : millis(value.pausedAt(), now)), value.pausedAt(),
                value.pauseRequestedAt(), value.pauseReason(), value.current(), applied, value.next(), value.levels(),
                applied != null && value.scheduledLevel() > applied.levelNumber(), value.next() == null, false, value.version());
    }

    private static long millis(Instant from, Instant to) { return Math.max(0, to.toEpochMilli() - from.toEpochMilli()); }
}
