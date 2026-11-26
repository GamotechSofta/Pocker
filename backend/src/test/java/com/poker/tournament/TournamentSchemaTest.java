package com.poker.tournament;

import com.poker.game.model.GameMode;
import com.poker.tournament.entity.BlindLevel;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentBlindSchedule;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentTableMode;
import com.poker.tournament.entity.TournamentVisibility;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TournamentSchemaTest {
    private static final Instant OPEN = Instant.parse("2030-01-01T10:00:00Z");
    private static final Instant CLOSE = Instant.parse("2030-01-01T11:00:00Z");

    @Test
    void validatesIndependentConfigurationAndDefaultsToTournamentMode() {
        Tournament tournament = new Tournament("Weekend", "Free event", null,
                TournamentStartMode.SCHEDULED, TournamentTableMode.MULTI_TABLE, TournamentVisibility.PRIVATE,
                100, 2, 6, 10_000, OPEN, CLOSE, Instant.parse("2030-01-01T12:00:00Z"), "ABCD23", UUID.randomUUID());

        assertEquals(GameMode.TOURNAMENT, tournament.getGameMode());
        assertEquals(TournamentStatus.DRAFT, tournament.getStatus());
        assertThrows(IllegalArgumentException.class, () -> new Tournament("bad", null, null,
                TournamentStartMode.SIT_AND_GO, TournamentTableMode.SINGLE_TABLE, TournamentVisibility.PUBLIC,
                6, 2, 6, 1000, OPEN, CLOSE, CLOSE, null, UUID.randomUUID()));
    }

    @Test
    void rejectsInvalidBlindScheduleShape() {
        assertThrows(IllegalArgumentException.class, () -> new BlindLevel(1, 100, 50, 0, 60, false));
        assertThrows(IllegalArgumentException.class, () -> new TournamentBlindSchedule(UUID.randomUUID(), List.of(
                new BlindLevel(2, 10, 20, 0, 60, false))));
    }

    @Test
    void permitsOnlyValidLifecycleTransitionsAndSetsTimestamps() {
        Tournament tournament = new Tournament("Event", null, null, TournamentStartMode.SIT_AND_GO,
                TournamentTableMode.SINGLE_TABLE, TournamentVisibility.PUBLIC, 6, 2, 6, 1000,
                OPEN, CLOSE, null, null, UUID.randomUUID());

        tournament.transitionTo(TournamentStatus.REGISTRATION_OPEN);
        tournament.transitionTo(TournamentStatus.REGISTRATION_CLOSED);
        tournament.transitionTo(TournamentStatus.STARTING);
        tournament.transitionTo(TournamentStatus.RUNNING);
        assertEquals(TournamentStatus.RUNNING, tournament.getStatus());
        assertNotNull(tournament.getStartedAt());
        assertThrows(IllegalStateException.class, () -> tournament.transitionTo(TournamentStatus.DRAFT));
    }
}
