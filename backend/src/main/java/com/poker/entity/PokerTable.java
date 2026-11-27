package com.poker.entity;

import com.poker.game.model.GameMode;
import com.poker.game.engine.TableConfig;
import com.poker.game.variants.TexasHoldemVariant;
import com.poker.tournament.entity.BlindLevel;
import com.poker.tournament.entity.TournamentHand;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Document(collection = "poker_tables")
public class PokerTable extends BaseDocument {

    private String name;
    private String variant;
    private int smallBlind;
    private int bigBlind;
    private int maxPlayers;
    private int startingChips;
    /** Cash tables remain the default; tournament tables use an explicit isolated mode. */
    private GameMode gameMode = GameMode.CASH;
    private UUID tournamentId;
    private Integer tableNumber;
    private Instant preparedAt;
    private BlindLevel initialBlind;
    private TournamentHand gameplay;
    private Instant tournamentClosedAt;
    private boolean tournamentRosterChanged;
    private TableStatus status = TableStatus.WAITING;
    /** Invite code for private rooms (null for public / matchmaking tables). */
    @Indexed(unique = true, sparse = true)
    private String roomCode;
    private boolean privateRoom;

    @Indexed
    private UUID createdById;

    private List<PokerTablePlayer> players = new ArrayList<>();

    protected PokerTable() {
    }

    public static PokerTable prepareTournament(UUID tournamentId, UUID organizer, String name,
                                               TableConfig config, BlindLevel initialBlind, Instant preparedAt) {
        return prepareTournament(tournamentId, organizer, name, config, initialBlind, preparedAt, 1);
    }

    public static PokerTable prepareTournament(UUID tournamentId, UUID organizer, String name,
                                               TableConfig config, BlindLevel initialBlind, Instant preparedAt, int number) {
        var table = new PokerTable();
        table.ensureId();
        table.gameMode = GameMode.TOURNAMENT;
        table.tournamentId = tournamentId;
        table.tableNumber = number;
        table.preparedAt = preparedAt;
        table.initialBlind = initialBlind;
        table.createdById = organizer;
        table.name = name;
        table.variant = TexasHoldemVariant.NAME;
        table.smallBlind = config.smallBlind();
        table.bigBlind = config.bigBlind();
        table.maxPlayers = config.maxPlayers();
        table.startingChips = config.startingChips();
        return table;
    }

    public PokerTable(
            String name,
            String variant,
            int smallBlind,
            int bigBlind,
            int maxPlayers,
            int startingChips,
            User createdBy
    ) {
        this(name, variant, smallBlind, bigBlind, maxPlayers, startingChips, createdBy, null, false);
    }

    public PokerTable(
            String name,
            String variant,
            int smallBlind,
            int bigBlind,
            int maxPlayers,
            int startingChips,
            User createdBy,
            String roomCode,
            boolean privateRoom
    ) {
        ensureId();
        this.name = name;
        this.variant = variant;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.maxPlayers = maxPlayers;
        this.startingChips = startingChips;
        this.createdById = createdBy.getId();
        this.status = TableStatus.WAITING;
        this.roomCode = roomCode;
        this.privateRoom = privateRoom;
    }

    public String getName() {
        return name;
    }

    public String getVariant() {
        return variant;
    }

    public int getSmallBlind() {
        return smallBlind;
    }

    public int getBigBlind() {
        return bigBlind;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getStartingChips() {
        return startingChips;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public boolean isTournament() { return gameMode == GameMode.TOURNAMENT; }
    public UUID getTournamentId() { return tournamentId; }
    public Integer getTableNumber() { return tableNumber; }
    public Instant getPreparedAt() { return preparedAt; }
    public BlindLevel getInitialBlind() { return initialBlind; }
    public TournamentHand getGameplay() { return gameplay; }
    public Instant getTournamentClosedAt() { return tournamentClosedAt; }
    public boolean isTournamentRosterChanged() { return tournamentRosterChanged; }

    public void changeTournamentRoster(Instant closedAt) {
        if (!isTournament() || gameplay == null || gameplay.settledAt() == null || tournamentClosedAt != null) {
            throw new IllegalStateException("Tournament seating changes require a settled, open table.");
        }
        tournamentRosterChanged = true;
        tournamentClosedAt = closedAt;
    }

    public void closeCompletedTournament(Instant now) {
        if (!isTournament() || tournamentClosedAt != null || gameplay == null || gameplay.settledAt() == null
                || gameplay.phase() != TournamentHand.Phase.PAUSED || gameplay.nextHandAt() != null || gameplay.turnDeadline() != null) {
            throw new IllegalStateException("Only a stopped tournament table can close at completion.");
        }
        tournamentClosedAt = now;
    }

    public void recordTournamentHand(TournamentHand hand) {
        if (!isTournament()) throw new IllegalStateException("Only tournament tables can record a tournament hand.");
        if (hand.phase() == TournamentHand.Phase.ACTIVE) tournamentRosterChanged = false;
        gameplay = hand;
        status = hand.phase() == TournamentHand.Phase.ACTIVE ? TableStatus.PLAYING : TableStatus.WAITING;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode == null ? GameMode.CASH : gameMode;
    }

    public TableStatus getStatus() {
        return status;
    }

    public void setStatus(TableStatus status) {
        this.status = status;
    }

    public UUID getCreatedById() {
        return createdById;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public boolean isPrivateRoom() {
        return privateRoom;
    }

    public void setPrivateRoom(boolean privateRoom) {
        this.privateRoom = privateRoom;
    }

    public List<PokerTablePlayer> getPlayers() {
        return players;
    }

    public void addPlayer(PokerTablePlayer player) {
        players.add(player);
        players.sort(Comparator.comparingInt(PokerTablePlayer::getSeat));
    }

    public void removePlayer(PokerTablePlayer player) {
        players.remove(player);
    }

    public List<PokerTablePlayer> snapshotPlayers() {
        List<PokerTablePlayer> copy = new ArrayList<>(players.size());
        for (PokerTablePlayer player : players) {
            copy.add(player.copy());
        }
        return copy;
    }

    public void restoreSnapshot(TableStatus savedStatus, List<PokerTablePlayer> savedPlayers) {
        this.status = savedStatus;
        this.players.clear();
        if (savedPlayers != null) {
            this.players.addAll(savedPlayers);
            this.players.sort(Comparator.comparingInt(PokerTablePlayer::getSeat));
        }
    }
}
