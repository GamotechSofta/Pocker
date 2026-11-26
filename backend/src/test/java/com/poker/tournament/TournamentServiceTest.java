package com.poker.tournament;

import com.poker.game.model.GameMode;
import com.poker.tournament.dto.BlindLevelRequest;
import com.poker.tournament.dto.CreateTournamentRequest;
import com.poker.tournament.dto.TournamentResponse;
import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentTableMode;
import com.poker.tournament.entity.TournamentVisibility;
import com.poker.tournament.entity.TournamentBlindSchedule;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.repository.TournamentBlindScheduleRepository;
import com.poker.tournament.repository.TournamentRepository;
import com.poker.tournament.service.DefaultTournamentService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TournamentServiceTest {
    @Test
    void createsTournamentAndPersistsItsBlindScheduleWithoutWalletDependencies() {
        TournamentRepository tournaments = mock(TournamentRepository.class);
        TournamentBlindScheduleRepository schedules = mock(TournamentBlindScheduleRepository.class);
        when(tournaments.save(any(Tournament.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(schedules.save(any(TournamentBlindSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DefaultTournamentService service = new DefaultTournamentService(tournaments, schedules);

        CreateTournamentRequest request = new CreateTournamentRequest(
                "Sit & Go", "Free chips", null, TournamentStartMode.SIT_AND_GO,
                TournamentTableMode.SINGLE_TABLE, TournamentVisibility.PUBLIC, 6, 2, 6, 1_000,
                Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T11:00:00Z"), null, null,
                List.of(new BlindLevelRequest(1, 10, 20, 0, 300, false)));

        TournamentResponse response = service.create(UUID.randomUUID(), request);

        assertEquals(GameMode.TOURNAMENT, response.gameMode());
        assertEquals(TournamentStatus.DRAFT, response.status());
        verify(tournaments).save(any(Tournament.class));
        verify(schedules).save(any(TournamentBlindSchedule.class));
    }

    @Test
    void transitionsThroughServiceAndRejectsInvalidTransition() {
        TournamentRepository tournaments = mock(TournamentRepository.class);
        TournamentBlindScheduleRepository schedules = mock(TournamentBlindScheduleRepository.class);
        Tournament tournament = new Tournament("Event", null, null, TournamentStartMode.SIT_AND_GO,
                TournamentTableMode.SINGLE_TABLE, TournamentVisibility.PUBLIC, 6, 2, 6, 1000,
                Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T11:00:00Z"), null, null, UUID.randomUUID());
        when(tournaments.findById(tournament.getId())).thenReturn(java.util.Optional.of(tournament));
        when(tournaments.save(tournament)).thenReturn(tournament);
        DefaultTournamentService service = new DefaultTournamentService(tournaments, schedules);

        service.transition(tournament.getId(), TournamentStatus.REGISTRATION_OPEN);
        assertEquals(TournamentStatus.REGISTRATION_OPEN, service.get(tournament.getId()).status());
    }
}
