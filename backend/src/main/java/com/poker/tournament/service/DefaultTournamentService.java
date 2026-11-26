package com.poker.tournament.service;

import com.poker.tournament.dto.BlindLevelRequest;
import com.poker.tournament.dto.CreateTournamentRequest;
import com.poker.tournament.dto.TournamentResponse;
import com.poker.tournament.entity.BlindLevel;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentBlindSchedule;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.repository.TournamentBlindScheduleRepository;
import com.poker.tournament.repository.TournamentRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DefaultTournamentService implements TournamentService {
    private final TournamentRepository tournamentRepository;
    private final TournamentBlindScheduleRepository blindScheduleRepository;

    public DefaultTournamentService(TournamentRepository tournamentRepository,
                                    TournamentBlindScheduleRepository blindScheduleRepository) {
        this.tournamentRepository = tournamentRepository;
        this.blindScheduleRepository = blindScheduleRepository;
    }

    @Override
    public TournamentResponse create(UUID creatorId, CreateTournamentRequest request) {
        if (creatorId == null || request == null) {
            throw new IllegalArgumentException("Creator and tournament request are required.");
        }
        Tournament tournament = new Tournament(
                request.name().trim(), request.description(), request.banner(), request.startMode(), request.tableMode(),
                request.visibility(), request.maximumEntrants(), request.minimumEntrants(), request.seatsPerTable(),
                request.startingVirtualChips(), request.registrationOpeningAt(), request.registrationClosingAt(),
                request.scheduledStartAt(), request.privateInviteCode(), creatorId
        );
        Tournament saved = tournamentRepository.save(tournament);
        blindScheduleRepository.save(new TournamentBlindSchedule(
                saved.getId(), request.blindLevels().stream().map(DefaultTournamentService::toBlindLevel).toList()
        ));
        return toResponse(saved);
    }

    @Override
    public TournamentResponse get(UUID tournamentId) {
        return toResponse(tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Tournament was not found.")));
    }

    @Override
    public TournamentResponse transition(UUID tournamentId, TournamentStatus target) {
        if (target == TournamentStatus.COMPLETED) throw new IllegalStateException("Completion requires authoritative finalization.");
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Tournament was not found."));
        tournament.transitionTo(target);
        return toResponse(tournamentRepository.save(tournament));
    }

    private static BlindLevel toBlindLevel(BlindLevelRequest request) {
        return new BlindLevel(request.levelNumber(), request.smallBlind(), request.bigBlind(), request.ante(),
                request.durationSeconds(), request.breakAfter());
    }

    private static TournamentResponse toResponse(Tournament t) {
        return new TournamentResponse(t.getId(), t.getName(), t.getDescription(), t.getBanner(), t.getStartMode(),
                t.getTableMode(), t.getVisibility(), t.getGameMode(), t.getMaximumEntrants(), t.getMinimumEntrants(),
                t.getSeatsPerTable(), t.getStartingVirtualChips(), t.getRegistrationOpeningAt(),
                t.getRegistrationClosingAt(), t.getScheduledStartAt(), t.getPrivateInviteCode(), t.getCreatorId(),
                t.getStatus(), t.getCreatedAt(), t.getUpdatedAt(), t.getStartedAt(), t.getCompletedAt());
    }
}
