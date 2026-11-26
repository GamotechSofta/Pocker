package com.poker.tournament.entity;

import com.poker.entity.BaseDocument;
import com.poker.game.model.GameMode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Document(collection = "tournaments")
public class Tournament extends BaseDocument {

    private String name;
    private String description;
    private String banner;
    private TournamentStartMode startMode;
    private TournamentTableMode tableMode;
    private TournamentVisibility visibility;
    private GameMode gameMode = GameMode.TOURNAMENT;
    private int maximumEntrants;
    private int minimumEntrants;
    private int seatsPerTable;
    private long startingVirtualChips;
    private Instant registrationOpeningAt;
    private Instant registrationClosingAt;
    private Instant scheduledStartAt;
    @Indexed(useGeneratedName = true, unique = true, sparse = true)
    private String privateInviteCode;
    @Indexed(useGeneratedName = true)
    private UUID creatorId;
    private TournamentStatus status = TournamentStatus.DRAFT;
    private Instant startedAt;
    private Instant completedAt;
    private TournamentInitialization initialization;
    private TournamentBlindClock blindClock;
    private TournamentStandingCheckpoint standings;
    private TournamentTopology topology;
    private TournamentFinalization finalization;
    private TournamentCompletion completion;

    protected Tournament() {
    }

    public Tournament(
            String name,
            String description,
            String banner,
            TournamentStartMode startMode,
            TournamentTableMode tableMode,
            TournamentVisibility visibility,
            int maximumEntrants,
            int minimumEntrants,
            int seatsPerTable,
            long startingVirtualChips,
            Instant registrationOpeningAt,
            Instant registrationClosingAt,
            Instant scheduledStartAt,
            String privateInviteCode,
            UUID creatorId
    ) {
        ensureId();
        this.name = name;
        this.description = description;
        this.banner = banner;
        this.startMode = startMode;
        this.tableMode = tableMode;
        this.visibility = visibility;
        this.maximumEntrants = maximumEntrants;
        this.minimumEntrants = minimumEntrants;
        this.seatsPerTable = seatsPerTable;
        this.startingVirtualChips = startingVirtualChips;
        this.registrationOpeningAt = registrationOpeningAt;
        this.registrationClosingAt = registrationClosingAt;
        this.scheduledStartAt = scheduledStartAt;
        this.privateInviteCode = privateInviteCode;
        this.creatorId = creatorId;
        validateConfiguration();
    }

    public void validateConfiguration() {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("Tournament name must be between 1 and 100 characters.");
        }
        if (description != null && description.length() > 2_000) {
            throw new IllegalArgumentException("Tournament description cannot exceed 2000 characters.");
        }
        if (banner != null && banner.length() > 512) {
            throw new IllegalArgumentException("Tournament banner cannot exceed 512 characters.");
        }
        if (startMode == null || tableMode == null || visibility == null || creatorId == null) {
            throw new IllegalArgumentException("Tournament mode, visibility, and creator are required.");
        }
        if (maximumEntrants < 2 || minimumEntrants < 2 || minimumEntrants > maximumEntrants) {
            throw new IllegalArgumentException("Entrant limits must be at least 2 and minimum cannot exceed maximum.");
        }
        if (seatsPerTable < 2 || seatsPerTable > 10 || seatsPerTable > maximumEntrants) {
            throw new IllegalArgumentException("Seats per table must be between 2 and 10 and fit the field.");
        }
        if (startingVirtualChips <= 0) {
            throw new IllegalArgumentException("Starting virtual chips must be positive.");
        }
        if (registrationOpeningAt == null || registrationClosingAt == null
                || !registrationOpeningAt.isBefore(registrationClosingAt)) {
            throw new IllegalArgumentException("Registration opening must be before registration closing.");
        }
        if (startMode == TournamentStartMode.SCHEDULED
                && (scheduledStartAt == null || scheduledStartAt.isBefore(registrationClosingAt))) {
            throw new IllegalArgumentException("Scheduled tournaments require a start time after registration closes.");
        }
        if (startMode == TournamentStartMode.SIT_AND_GO && scheduledStartAt != null) {
            throw new IllegalArgumentException("Sit & Go tournaments cannot define a scheduled start time.");
        }
        if (visibility == TournamentVisibility.PRIVATE
                && (privateInviteCode == null || privateInviteCode.isBlank())) {
            throw new IllegalArgumentException("Private tournaments require an invite code.");
        }
        if (visibility == TournamentVisibility.PUBLIC && privateInviteCode != null && !privateInviteCode.isBlank()) {
            throw new IllegalArgumentException("Public tournaments cannot define a private invite code.");
        }
        if (tableMode == TournamentTableMode.SINGLE_TABLE && maximumEntrants > seatsPerTable) {
            throw new IllegalArgumentException("Single-table tournaments cannot exceed seats per table.");
        }
    }

    public void transitionTo(TournamentStatus target) {
        if (target == null || target == status) {
            return;
        }
        boolean allowed = switch (status) {
            case DRAFT -> target == TournamentStatus.REGISTRATION_OPEN || target == TournamentStatus.CANCELLED;
            case REGISTRATION_OPEN -> target == TournamentStatus.REGISTRATION_CLOSED
                    || target == TournamentStatus.STARTING || target == TournamentStatus.CANCELLED;
            case REGISTRATION_CLOSED -> target == TournamentStatus.STARTING || target == TournamentStatus.CANCELLED;
            case STARTING -> target == TournamentStatus.RUNNING || target == TournamentStatus.CANCELLED;
            case RUNNING -> target == TournamentStatus.PAUSED
                    || target == TournamentStatus.COMPLETED || target == TournamentStatus.CANCELLED;
            case PAUSED -> target == TournamentStatus.RUNNING || target == TournamentStatus.CANCELLED
                    || (target == TournamentStatus.COMPLETED && completion != null);
            case COMPLETED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Invalid tournament status transition: " + status + " -> " + target);
        }
        status = target;
        Instant now = Instant.now();
        if (target == TournamentStatus.RUNNING && startedAt == null) {
            startedAt = now;
        }
        if (target == TournamentStatus.COMPLETED && completedAt == null) {
            completedAt = now;
        }
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getBanner() { return banner; }
    public TournamentStartMode getStartMode() { return startMode; }
    public TournamentTableMode getTableMode() { return tableMode; }
    public TournamentVisibility getVisibility() { return visibility; }
    public GameMode getGameMode() { return gameMode; }
    public int getMaximumEntrants() { return maximumEntrants; }
    public int getMinimumEntrants() { return minimumEntrants; }
    public int getSeatsPerTable() { return seatsPerTable; }
    public long getStartingVirtualChips() { return startingVirtualChips; }
    public Instant getRegistrationOpeningAt() { return registrationOpeningAt; }
    public Instant getRegistrationClosingAt() { return registrationClosingAt; }
    public Instant getScheduledStartAt() { return scheduledStartAt; }
    public String getPrivateInviteCode() { return privateInviteCode; }
    public UUID getCreatorId() { return creatorId; }
    public TournamentStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public TournamentInitialization getInitialization() { return initialization; }
    public TournamentTopology getTopology() { return topology; }
    public TournamentBlindClock getBlindClock() { return blindClock; }
    public TournamentStandingCheckpoint getStandings() { return standings; }
    public TournamentFinalization getFinalization() { return finalization; }
    public TournamentCompletion getCompletion() { return completion; }

    public void complete(TournamentCompletion result) {
        if (status != TournamentStatus.PAUSED || completion != null || finalization == null
                || finalization.stage() != TournamentFinalization.Stage.CLAIMED || result == null
                || !finalization.operationId().equals(result.operationId())) {
            throw new IllegalStateException("A stopped tournament requires a validated completion claim.");
        }
        completion = result;
        transitionTo(TournamentStatus.COMPLETED);
        completedAt = result.completedAt();
        finalization = finalization.withStage(TournamentFinalization.Stage.COMPLETED, null);
    }
}
