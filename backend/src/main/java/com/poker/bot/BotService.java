package com.poker.bot;

import com.poker.dto.GameActionRequest;
import com.poker.dto.JoinTableRequest;
import com.poker.entity.PokerTable;
import com.poker.entity.PokerTablePlayer;
import com.poker.entity.User;
import com.poker.game.engine.GameState;
import com.poker.game.engine.GameStatus;
import com.poker.game.engine.LegalActions;
import com.poker.game.engine.PlayerState;
import com.poker.game.engine.TexasHoldemEngine;
import com.poker.game.variants.Variants;
import com.poker.repository.PokerTableRepository;
import com.poker.repository.UserRepository;
import com.poker.service.TableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);
    private static final long SHUFFLE_ANIMATION_MS = 1_800;
    private static final long CARD_FLIGHT_MS = 650;
    private static final long CARD_STAGGER_MS = 680;
    private static final long DEAL_SETTLE_MS = 300;
    private static final String[] BOT_TAGS = {
            "aarav", "vihaan", "arjun", "kabir", "ishaan",
            "maya", "anaya", "diya", "sara", "riya"
    };

    private final TableService tableService;
    private final PokerTableRepository tableRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BotBrain brain = new BotBrain();

    public BotService(
            TableService tableService,
            PokerTableRepository tableRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tableService = tableService;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static boolean isBot(User user) {
        return user != null && (BotIdentity.isBotUsername(user.getUsername()) || BotIdentity.isBotEmail(user.getEmail()));
    }

    public static boolean isBot(PokerTablePlayer player) {
        return player != null
                && (BotIdentity.isBotUsername(player.getUsername()) || BotIdentity.isBotEmail(player.getEmail()));
    }

    public void fillAndMaybeStart(UUID tableId) {
        PokerTable table = tableRepository.findById(tableId).orElse(null);
        if (table == null) {
            return;
        }

        List<PokerTablePlayer> seated = new ArrayList<>(table.getPlayers());
        long humans = seated.stream().filter(player -> !isBot(player)).count();
        long bots = seated.stream().filter(BotService::isBot).count();

        if (humans >= 2) {
            removeBotsIfIdle(tableId, seated);
            return;
        }
        if (humans == 0) {
            return;
        }

        // A solo player gets a full table: one human plus bots in every open seat.
        int targetBots = Math.max(1, table.getMaxPlayers() - Math.toIntExact(humans));
        while (bots < targetBots && seated.size() < table.getMaxPlayers()) {
            User bot = acquireBotUser();
            try {
                tableService.joinTable(tableId, bot.getId(), new JoinTableRequest(null));
                bots++;
                seated = new ArrayList<>(tableRepository.findById(tableId).orElseThrow().getPlayers());
            } catch (RuntimeException ex) {
                log.debug("Could not seat bot {}: {}", bot.getUsername(), ex.getMessage());
                break;
            }
        }

        maybeAutoStart(tableId);
    }

    public void maybeAct(UUID tableId) {
        PokerTable table = tableRepository.findById(tableId).orElse(null);
        if (table == null) {
            return;
        }
        TexasHoldemEngine engine;
        try {
            engine = tableService.engineSnapshot(tableId);
        } catch (RuntimeException ex) {
            return;
        }
        GameState state = engine.state();
        if (!state.isBettingActive() && !state.isDiscardActive() && !state.isDrawActive()) {
            if (state.getStatus() == GameStatus.NEXT_HAND || state.getStatus() == GameStatus.WAITING) {
                fillAndMaybeStart(tableId);
            }
            return;
        }
        PlayerState actor = state.currentPlayer();
        if (actor == null || !BotIdentity.isBotUsername(actor.getUsername())) {
            return;
        }
        UUID botId = UUID.fromString(actor.getPlayerId());
        LegalActions legal = engine.legalActions(actor.getPlayerId());
        if (legal.actions().isEmpty()) {
            return;
        }
        GameActionRequest action = brain.choose(
                actor,
                legal,
                state.getStatus(),
                state.getCommunityCards(),
                state.getPot()
        );
        try {
            tableService.applyAction(tableId, botId, action);
        } catch (RuntimeException ex) {
            log.warn("Bot {} failed to act at {}: {}", actor.getUsername(), tableId, ex.getMessage());
            fallbackAct(tableId, botId, legal);
        }
    }

    long dealAnimationDelayMs(UUID tableId) {
        PokerTable table = tableRepository.findById(tableId).orElse(null);
        if (table == null) {
            return SHUFFLE_ANIMATION_MS + CARD_FLIGHT_MS + DEAL_SETTLE_MS;
        }
        int playerCount = Math.max(2, table.getPlayers().size());
        int cardsPerPlayer = Variants.create(table.getVariant()).getInitialHoleCards();
        int dealtCards = playerCount * cardsPerPlayer;
        return SHUFFLE_ANIMATION_MS
                + Math.max(0, dealtCards - 1L) * CARD_STAGGER_MS
                + CARD_FLIGHT_MS
                + DEAL_SETTLE_MS;
    }

    private void fallbackAct(UUID tableId, UUID botId, LegalActions legal) {
        try {
            if (legal.allows(com.poker.game.engine.ActionType.CHECK)) {
                tableService.applyAction(tableId, botId, new GameActionRequest("CHECK", null, null, "bot-fallback"));
            } else if (legal.allows(com.poker.game.engine.ActionType.FOLD)) {
                tableService.applyAction(tableId, botId, new GameActionRequest("FOLD", null, null, "bot-fallback"));
            } else if (legal.allows(com.poker.game.engine.ActionType.CALL)) {
                tableService.applyAction(tableId, botId, new GameActionRequest("CALL", null, null, "bot-fallback"));
            }
        } catch (RuntimeException ignored) {
            // leave for next tick
        }
    }

    private void removeBotsIfIdle(UUID tableId, List<PokerTablePlayer> seated) {
        TexasHoldemEngine engine;
        try {
            engine = tableService.engineSnapshot(tableId);
        } catch (RuntimeException ex) {
            return;
        }
        if (engine.state().isHandInProgress() || engine.state().isBettingActive()) {
            return;
        }
        for (PokerTablePlayer player : seated) {
            if (isBot(player)) {
                try {
                    tableService.leaveTable(tableId, player.getUserId());
                } catch (RuntimeException ex) {
                    log.debug("Could not remove bot: {}", ex.getMessage());
                }
            }
        }
    }

    private void maybeAutoStart(UUID tableId) {
        PokerTable table = tableRepository.findById(tableId).orElse(null);
        if (table == null || table.getPlayers().size() < 2) {
            return;
        }
        TexasHoldemEngine engine;
        try {
            engine = tableService.engineSnapshot(tableId);
        } catch (RuntimeException ex) {
            return;
        }
        GameStatus status = engine.state().getStatus();
        if (status != GameStatus.WAITING && status != GameStatus.NEXT_HAND) {
            return;
        }
        UUID starter = table.getPlayers().stream()
                .filter(player -> !isBot(player))
                .map(PokerTablePlayer::getUserId)
                .findFirst()
                .orElse(table.getPlayers().getFirst().getUserId());
        try {
            tableService.startTable(tableId, starter);
        } catch (RuntimeException ex) {
            log.debug("Auto-start skipped for {}: {}", tableId, ex.getMessage());
        }
    }

    private User acquireBotUser() {
        for (String tag : BOT_TAGS) {
            String username = BotIdentity.USERNAME_PREFIX + tag;
            User existing = userRepository.findByUsernameIgnoreCase(username).orElse(null);
            if (existing != null && !tableRepository.existsByPlayersUserId(existing.getId())) {
                return existing;
            }
            if (existing == null) {
                return userRepository.save(new User(
                        username,
                        username + BotIdentity.EMAIL_SUFFIX,
                        passwordEncoder.encode(UUID.randomUUID().toString())
                ));
            }
        }
        String username = BotIdentity.USERNAME_PREFIX + "x" + ThreadLocalRandom.current().nextInt(1000, 9999);
        return userRepository.save(new User(
                username.toLowerCase(Locale.ROOT),
                username.toLowerCase(Locale.ROOT) + BotIdentity.EMAIL_SUFFIX,
                passwordEncoder.encode(UUID.randomUUID().toString())
        ));
    }
}
