package com.poker.tournament.repository;

import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.poker.tournament.entity.TournamentVisibility;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TournamentRepository extends MongoRepository<Tournament, UUID> {
    Page<Tournament> findByVisibilityAndStatusIn(TournamentVisibility visibility, Collection<TournamentStatus> statuses, Pageable page);
    Page<Tournament> findByIdInAndStatusIn(Collection<UUID> ids, Collection<TournamentStatus> statuses, Pageable page);
    List<Tournament> findAllByStatusOrderByRegistrationOpeningAtAsc(TournamentStatus status);
    List<Tournament> findAllByStatusAndScheduledStartAtLessThanEqual(TournamentStatus status, Instant time);
    Optional<Tournament> findByPrivateInviteCodeIgnoreCase(String privateInviteCode);
    boolean existsByPrivateInviteCodeIgnoreCase(String privateInviteCode);
    List<Tournament> findFirst2ByVisibilityAndPrivateInviteCodeIgnoreCase(TournamentVisibility visibility, String privateInviteCode);
}
