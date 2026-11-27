package com.poker.tournament.service;

import com.poker.entity.User;
import com.poker.exception.ApiException;
import com.poker.repository.UserRepository;
import com.poker.tournament.dto.TournamentDiscovery;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentBlindSchedule;
import com.poker.tournament.entity.TournamentEntrant;
import com.poker.tournament.entity.TournamentEntrantStatus;
import com.poker.tournament.entity.TournamentFinalResult;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentVisibility;
import com.poker.tournament.repository.TournamentBlindScheduleRepository;
import com.poker.tournament.repository.TournamentEntrantRepository;
import com.poker.tournament.repository.TournamentFinalResultRepository;
import com.poker.tournament.repository.TournamentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TournamentDiscoveryService {
    private static final Set<TournamentStatus> PUBLISHED = Set.of(
            TournamentStatus.REGISTRATION_OPEN, TournamentStatus.REGISTRATION_CLOSED, TournamentStatus.STARTING,
            TournamentStatus.RUNNING, TournamentStatus.PAUSED, TournamentStatus.COMPLETED, TournamentStatus.CANCELLED);
    private static final Sort CATALOGUE_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    private static final TournamentEntrantStatus CANCELLED = TournamentEntrantStatus.CANCELLED;

    private final TournamentRepository tournaments;
    private final TournamentEntrantRepository entrants;
    private final TournamentBlindScheduleRepository schedules;
    private final TournamentFinalResultRepository results;
    private final UserRepository users;

    public TournamentDiscoveryService(TournamentRepository tournaments, TournamentEntrantRepository entrants,
                                      TournamentBlindScheduleRepository schedules, TournamentFinalResultRepository results,
                                      UserRepository users) {
        this.tournaments = tournaments;
        this.entrants = entrants;
        this.schedules = schedules;
        this.results = results;
        this.users = users;
    }

    public TournamentDiscovery.Catalogue list(UUID viewer, List<TournamentStatus> filter, int page, int size, boolean mine) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Page must be nonnegative and size must be between 1 and 50.");
        }
        Instant now = Instant.now();
        var paging = PageRequest.of(page, size, CATALOGUE_ORDER);
        Set<TournamentStatus> statuses = new HashSet<>(PUBLISHED);
        // Drafts can be cancelled directly. Only existing participants can see cancelled records.
        if (!mine) statuses.remove(TournamentStatus.CANCELLED);
        if (filter != null && !filter.isEmpty()) {
            statuses.retainAll(filter);
        }
        if (statuses.isEmpty()) {
            return new TournamentDiscovery.Catalogue(List.of(), page, size, 0, 0, now);
        }

        Page<Tournament> found;
        if (mine) {
            var ids = entrants.findByUserIdAndStatusNot(viewer, CANCELLED).stream()
                    .map(TournamentEntrant::getTournamentId).toList();
            found = ids.isEmpty() ? Page.empty(paging) : tournaments.findByIdInAndStatusIn(ids, statuses, paging);
        } else {
            found = tournaments.findByVisibilityAndStatusIn(TournamentVisibility.PUBLIC, statuses, paging);
        }
        if (found.isEmpty()) {
            return new TournamentDiscovery.Catalogue(List.of(), page, size, found.getTotalElements(), found.getTotalPages(), now);
        }

        var ids = found.stream().map(Tournament::getId).toList();
        var counts = entrants.registrationCounts(ids).stream().collect(Collectors.toMap(
                TournamentEntrantRepository.RegistrationCount::tournamentId,
                TournamentEntrantRepository.RegistrationCount::count));
        var memberships = entrants.findByUserIdAndTournamentIdIn(viewer, ids).stream()
                .collect(Collectors.toMap(TournamentEntrant::getTournamentId, java.util.function.Function.identity()));
        var cards = found.stream().map(t -> participantCard(t, counts.getOrDefault(t.getId(), 0L), memberships.get(t.getId()))).toList();
        return new TournamentDiscovery.Catalogue(cards, page, size, found.getTotalElements(), found.getTotalPages(), now);
    }

    public TournamentDiscovery.Details details(UUID id, UUID viewer) {
        var tournament = tournaments.findById(id).filter(t -> PUBLISHED.contains(t.getStatus()))
                .orElseThrow(TournamentDiscoveryService::notFound);
        var entry = entrants.findByTournamentIdAndUserId(id, viewer).orElse(null);
        var participation = entry == null ? null : entry.getStatus();
        boolean requiresEntry = tournament.getVisibility() != TournamentVisibility.PUBLIC
                || tournament.getStatus() == TournamentStatus.CANCELLED;
        if (requiresEntry && (participation == null || participation == CANCELLED)) {
            throw notFound();
        }

        long count = entrants.countByTournamentIdAndStatusNot(id, CANCELLED);
        var registered = entrants.findByTournamentIdAndStatusNot(id, CANCELLED,
                PageRequest.of(0, 50, Sort.by("registeredAt", "id")));
        var standings = tournament.getStatus() == TournamentStatus.COMPLETED
                ? results.findByTournamentIdOrderByFinishPositionAsc(id, PageRequest.of(0, 20))
                : List.<TournamentFinalResult>of();
        Set<UUID> userIds = new HashSet<>();
        registered.forEach(entrant -> userIds.add(entrant.getUserId()));
        standings.forEach(result -> userIds.add(result.getUserId()));
        var names = userIds.isEmpty() ? Map.<UUID, String>of() : users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        var players = players(registered, names, viewer);
        var ranks = standings.stream().map(result -> new TournamentDiscovery.Result(result.getFinishPosition(),
                result.getDisplayName() == null ? names.getOrDefault(result.getUserId(), "Player") : result.getDisplayName(),
                result.getFinalVirtualChips(), viewer.equals(result.getUserId()))).toList();
        var blindLevels = schedules.findByTournamentId(id).map(TournamentBlindSchedule::getLevels).orElseGet(List::of);
        return new TournamentDiscovery.Details(participantCard(tournament, count, entry), tournament.getDescription(),
                tournament.getSeatsPerTable(), tournament.getCompletedAt(), blindLevels, players, ranks, Instant.now());
    }

    static List<TournamentDiscovery.RegisteredPlayer> players(List<TournamentEntrant> registered,
                                                              Map<UUID, String> names, UUID viewer) {
        return registered.stream().map(entrant -> new TournamentDiscovery.RegisteredPlayer(
                names.getOrDefault(entrant.getUserId(), "Player"), entrant.getStatus(), viewer.equals(entrant.getUserId()))).toList();
    }

    static TournamentDiscovery.Card card(Tournament t, long registeredCount, TournamentEntrantStatus participation) {
        return card(t, registeredCount, participation, null);
    }

    private static TournamentDiscovery.Card participantCard(Tournament t, long registeredCount, TournamentEntrant entry) {
        return card(t, registeredCount, entry == null ? null : entry.getStatus(), entry == null ? null : entry.getFinishPosition());
    }

    private static TournamentDiscovery.Card card(Tournament t, long registeredCount, TournamentEntrantStatus participation, Integer position) {
        return new TournamentDiscovery.Card(t.getId(), t.getName(), safeBanner(t.getBanner()), t.getStatus(),
                t.getStartMode(), t.getTableMode(), t.getVisibility(), t.getMinimumEntrants(), t.getMaximumEntrants(),
                t.getStartingVirtualChips(), registeredCount, t.getRegistrationOpeningAt(), t.getRegistrationClosingAt(),
                t.getScheduledStartAt(), participation, position, t.getStatus() == TournamentStatus.PAUSED
                        && t.getStandings() != null && t.getStandings().candidate() != null);
    }

    static String safeBanner(String banner) {
        if (banner == null || banner.isBlank()) return null;
        try {
            URI url = URI.create(banner);
            return "https".equalsIgnoreCase(url.getScheme()) && url.getHost() != null && url.getUserInfo() == null
                    ? banner : null;
        } catch (IllegalArgumentException invalidUrl) {
            return null;
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TOURNAMENT_NOT_FOUND", "Tournament not found.");
    }
}
