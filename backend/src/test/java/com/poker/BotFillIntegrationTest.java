package com.poker;

import com.poker.bot.BotIdentity;
import com.poker.bot.BotService;
import com.poker.dto.GameActionRequest;
import com.poker.dto.JoinTableRequest;
import com.poker.entity.User;
import com.poker.game.engine.GameStatus;
import com.poker.game.engine.TexasHoldemEngine;
import com.poker.repository.UserRepository;
import com.poker.service.TableService;
import com.poker.support.AbstractMongoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BotFillIntegrationTest extends AbstractMongoTest {

    @Autowired
    private TableService tableService;
    @Autowired
    private BotService botService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void fillsBotAutoStartsAndBotActs() {
        User human = userRepository.save(new User(
                "p_" + UUID.randomUUID().toString().substring(0, 8),
                "p_" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("password1")
        ));
        var created = tableService.createTable(human.getId(), new com.poker.dto.CreateTableRequest(
                "Bot IT",
                "TEXAS_HOLDEM",
                5,
                10,
                6,
                1000
        ));
        UUID tableId = created.id();
        tableService.joinTable(tableId, human.getId(), new JoinTableRequest(null));

        botService.fillAndMaybeStart(tableId);

        TexasHoldemEngine engine = tableService.engineSnapshot(tableId);
        assertEquals(6, engine.state().getPlayers().size());
        assertEquals(
                5,
                engine.state().getPlayers().stream()
                        .filter(p -> BotIdentity.isBotUsername(p.getUsername()))
                        .count()
        );
        assertEquals(GameStatus.PRE_FLOP, engine.state().getStatus());

        // Act if the human happens to be first; otherwise let the current bot act.
        String humanId = human.getId().toString();
        if (engine.state().currentPlayer() != null
                && humanId.equals(engine.state().currentPlayer().getPlayerId())) {
            tableService.applyAction(tableId, human.getId(), new GameActionRequest("CALL", null, null, "it-call"));
        }

        botService.maybeAct(tableId);
        engine = tableService.engineSnapshot(tableId);
        // The current bot should have acted: call/check/raise or advance the street.
        assertTrue(
                engine.state().getPot() > 15
                        || engine.state().getStatus() != GameStatus.PRE_FLOP
                        || engine.state().getCommunityCards().size() > 0
                        || (engine.state().currentPlayer() != null
                        && humanId.equals(engine.state().currentPlayer().getPlayerId())),
                "expected bot to complete BB option or advance street"
        );
    }
}
