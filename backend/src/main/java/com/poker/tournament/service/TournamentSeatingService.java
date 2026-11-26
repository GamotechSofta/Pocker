package com.poker.tournament.service;

import com.poker.entity.PokerTable;
import com.poker.entity.PokerTablePlayer;
import com.poker.entity.User;
import com.poker.game.engine.TableConfig;
import com.poker.tournament.entity.BlindLevel;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentEntrant;
import com.poker.tournament.entity.TournamentTableAssignment;
import com.poker.tournament.entity.TournamentTableMode;
import com.poker.tournament.entity.TournamentStartMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TournamentSeatingService {
    private final SecureRandom random = new SecureRandom();
    private final TournamentChipService chips;

    public TournamentSeatingService(TournamentChipService chips) {
        this.chips = chips;
    }

    public static boolean supported(Tournament tournament) {
        int seats = tournament.getSeatsPerTable();
        int maximum = tournament.getMaximumEntrants();
        boolean single = tournament.getTableMode() == TournamentTableMode.SINGLE_TABLE;
        boolean multi = tournament.getTableMode() == TournamentTableMode.MULTI_TABLE
                && tournament.getStartMode() == TournamentStartMode.SCHEDULED && seats >= 3 && maximum > seats && maximum <= 90;
        return (single && maximum == seats || multi) && seats >= 2 && seats <= 10
                && tournament.getMinimumEntrants() >= 2 && tournament.getMinimumEntrants() <= maximum
                && tournament.getStartingVirtualChips() > 0
                && tournament.getStartingVirtualChips() <= Integer.MAX_VALUE / maximum;
    }

    public TableConfig configuration(Tournament tournament, List<BlindLevel> levels) {
        if (!supported(tournament) || levels.isEmpty()) {
            throw new IllegalArgumentException("Unsupported table configuration or missing blind schedule.");
        }
        var first = levels.getFirst();
        if (first.levelNumber() != 1 || first.durationSeconds() < 1
                || first.bigBlind() > tournament.getStartingVirtualChips()) {
            throw new IllegalArgumentException("Initial blind level does not fit the configured stack.");
        }
        return new TableConfig(first.smallBlind(), first.bigBlind(), Math.toIntExact(tournament.getStartingVirtualChips()),
                tournament.getSeatsPerTable(), 5, first.ante());
    }

    public PokerTable prepare(MongoTemplate tx, Tournament tournament, List<TournamentEntrant> entrants,
                              Map<UUID, User> players, TableConfig config, BlindLevel first, Instant now) {
        var shuffled = new ArrayList<>(entrants);
        Collections.shuffle(shuffled, random);
        return prepareTable(tx, tournament, shuffled, players, config, first, now, 1);
    }

    public List<PokerTable> prepareField(MongoTemplate tx, Tournament tournament, List<TournamentEntrant> entrants,
                              Map<UUID, User> players, TableConfig config, BlindLevel first, Instant now) {
        if (tournament.getTableMode() == TournamentTableMode.SINGLE_TABLE) {
            return List.of(prepare(tx, tournament, entrants, players, config, first, now));
        }
        var shuffled = new ArrayList<>(entrants);
        Collections.shuffle(shuffled, random);
        int tablesNeeded = Math.ceilDiv(shuffled.size(), config.maxPlayers());
        var tables = new ArrayList<PokerTable>(tablesNeeded);
        int offset = 0;
        for (int index = 0; index < tablesNeeded; index++) {
            int seats = shuffled.size() / tablesNeeded + (index < shuffled.size() % tablesNeeded ? 1 : 0);
            tables.add(prepareTable(tx, tournament, shuffled.subList(offset, offset + seats), players, config, first, now, index + 1));
            offset += seats;
        }
        long total = shuffled.stream().mapToLong(TournamentEntrant::getCurrentVirtualChips).sum();
        if (total != (long) shuffled.size() * config.startingChips()) {
            throw new IllegalStateException("Prepared tournament chip total does not match the field.");
        }
        return tables;
    }

    private PokerTable prepareTable(MongoTemplate tx, Tournament tournament, List<TournamentEntrant> entrants,
                                    Map<UUID, User> players, TableConfig config, BlindLevel first, Instant now, int number) {
        var table = PokerTable.prepareTournament(tournament.getId(), tournament.getCreatorId(), tournament.getName(), config, first, now, number);
        for (int seat = 0; seat < entrants.size(); seat++) {
            var entrant = entrants.get(seat);
            table.addPlayer(PokerTablePlayer.tournamentSeat(players.get(entrant.getUserId()), seat, config.startingChips()));
        }
        tx.insert(table);
        for (int seat = 0; seat < entrants.size(); seat++) {
            var entrant = entrants.get(seat);
            tx.insert(new TournamentTableAssignment(tournament.getId(), entrant.getId(), entrant.getUserId(), table.getId(), seat, now));
            chips.issueInitialStack(tx, entrant, table.getId(), seat, config.startingChips(), now);
        }
        if (entrants.stream().mapToLong(TournamentEntrant::getCurrentVirtualChips).sum() != (long) entrants.size() * config.startingChips()) {
            throw new IllegalStateException("Prepared table chip total does not match its seats.");
        }
        return table;
    }
}
