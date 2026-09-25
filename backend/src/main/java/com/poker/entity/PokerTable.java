package com.poker.entity;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Document(collection = "poker_tables")
public class PokerTable extends BaseDocument {

    private String name;
    private String variant;
    private int smallBlind;
    private int bigBlind;
    private int maxPlayers;
    private int startingChips;
    private TableStatus status = TableStatus.WAITING;

    @Indexed
    private UUID createdById;

    private List<PokerTablePlayer> players = new ArrayList<>();

    protected PokerTable() {
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
        ensureId();
        this.name = name;
        this.variant = variant;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.maxPlayers = maxPlayers;
        this.startingChips = startingChips;
        this.createdById = createdBy.getId();
        this.status = TableStatus.WAITING;
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

    public TableStatus getStatus() {
        return status;
    }

    public void setStatus(TableStatus status) {
        this.status = status;
    }

    public UUID getCreatedById() {
        return createdById;
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
}
