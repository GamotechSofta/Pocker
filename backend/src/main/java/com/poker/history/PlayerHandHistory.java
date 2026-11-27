package com.poker.history;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "player_hand_history")
@CompoundIndexes({
        @CompoundIndex(name = "player_finished_idx", def = "{'playerId': 1, 'finishedAt': -1}"),
        @CompoundIndex(name = "player_variant_finished_idx", def = "{'playerId': 1, 'variant': 1, 'finishedAt': -1}")
})
public class PlayerHandHistory {

    public record Participant(
            String playerId,
            String username,
            String outcome,
            int wonAmount,
            int netChips,
            String handDescription,
            List<String> holeCards
    ) {
        public Participant {
            holeCards = holeCards == null ? List.of() : List.copyOf(holeCards);
        }
    }

    @Id
    private String id;
    @Indexed
    private String playerId;
    private String tableId;
    private String tableName;
    @Indexed
    private String variant;
    private int handNumber;
    private int smallBlind;
    private int bigBlind;
    private String outcome;
    private int wonAmount;
    private int netChips;
    private int potAmount;
    private List<String> playerHoleCards = List.of();
    private List<String> communityCards = List.of();
    private String handDescription;
    private String yourHandDescription;
    private List<String> winnerNames = List.of();
    private int playerCount;
    private List<Participant> participants = List.of();
    @Indexed
    private Instant finishedAt;

    public PlayerHandHistory() {
    }

    public PlayerHandHistory(
            String id,
            String playerId,
            String tableId,
            String tableName,
            String variant,
            int handNumber,
            int smallBlind,
            int bigBlind,
            String outcome,
            int wonAmount,
            int netChips,
            int potAmount,
            List<String> playerHoleCards,
            List<String> communityCards,
            String handDescription,
            String yourHandDescription,
            List<String> winnerNames,
            int playerCount,
            List<Participant> participants,
            Instant finishedAt
    ) {
        this.id = id;
        this.playerId = playerId;
        this.tableId = tableId;
        this.tableName = tableName;
        this.variant = variant;
        this.handNumber = handNumber;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.outcome = outcome;
        this.wonAmount = wonAmount;
        this.netChips = netChips;
        this.potAmount = potAmount;
        this.playerHoleCards = playerHoleCards == null ? List.of() : List.copyOf(playerHoleCards);
        this.communityCards = communityCards == null ? List.of() : List.copyOf(communityCards);
        this.handDescription = handDescription;
        this.yourHandDescription = yourHandDescription;
        this.winnerNames = winnerNames == null ? List.of() : List.copyOf(winnerNames);
        this.playerCount = playerCount;
        this.participants = participants == null ? List.of() : List.copyOf(participants);
        this.finishedAt = finishedAt;
    }

    public String getId() {
        return id;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getTableId() {
        return tableId;
    }

    public String getTableName() {
        return tableName;
    }

    public String getVariant() {
        return variant;
    }

    public int getHandNumber() {
        return handNumber;
    }

    public int getSmallBlind() {
        return smallBlind;
    }

    public int getBigBlind() {
        return bigBlind;
    }

    public String getOutcome() {
        return outcome;
    }

    public int getWonAmount() {
        return wonAmount;
    }

    public int getNetChips() {
        return netChips;
    }

    public int getPotAmount() {
        return potAmount;
    }

    public List<String> getPlayerHoleCards() {
        return playerHoleCards;
    }

    public List<String> getCommunityCards() {
        return communityCards;
    }

    public String getHandDescription() {
        return handDescription;
    }

    public String getYourHandDescription() {
        return yourHandDescription;
    }

    public List<String> getWinnerNames() {
        return winnerNames;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
