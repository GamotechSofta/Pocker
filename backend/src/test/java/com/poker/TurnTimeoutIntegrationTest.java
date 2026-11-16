package com.poker;

import com.poker.dto.CreateTableRequest;
import com.poker.dto.JoinTableRequest;
import com.poker.entity.User;
import com.poker.game.engine.PlayerState;
import com.poker.repository.UserRepository;
import com.poker.service.TableService;
import com.poker.support.AbstractMongoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "app.table.turn-seconds=1")
@ActiveProfiles("test")
class TurnTimeoutIntegrationTest extends AbstractMongoTest {

    @Autowired
    private TableService tableService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void playerWhoRunsOutOfTimeIsFoldedAndTheClockMovesOn() throws InterruptedException {
        User first = human();
        User second = human();
        UUID tableId = tableService.createTable(first.getId(), new CreateTableRequest(
                "Clock IT", "TEXAS_HOLDEM", 5, 10, 2, 1000
        )).id();
        tableService.joinTable(tableId, first.getId(), new JoinTableRequest(null));
        tableService.joinTable(tableId, second.getId(), new JoinTableRequest(null));
        tableService.startTable(tableId, first.getId());

        Instant deadline = tableService.turnDeadline(tableId).orElseThrow();
        // First turn of a hand also waits for the deal animation, then one second of clock.
        assertTrue(deadline.isAfter(Instant.now().plusMillis(1_000)));
        assertFalse(tableService.enforceTurnTimeout(tableId), "nothing happens before the deadline");

        PlayerState actor = tableService.engineSnapshot(tableId).state().currentPlayer();
        Thread.sleep(Math.max(0, Duration.between(Instant.now(), deadline).toMillis()) + 50);

        assertTrue(tableService.enforceTurnTimeout(tableId));
        var state = tableService.engineSnapshot(tableId).state();
        // Heads-up preflop the small blind owes chips, so the timeout folds and the other player wins.
        assertTrue(state.player(actor.getPlayerId()).isFolded());
        assertFalse(state.isBettingActive());
        assertEquals(1, state.getPayouts().size());
        assertTrue(tableService.turnDeadline(tableId).isEmpty(), "no clock once the hand is over");
    }

    private User human() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new User("p_" + tag, "p_" + tag + "@example.com", passwordEncoder.encode("password1")));
    }
}
