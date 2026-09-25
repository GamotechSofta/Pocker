package com.poker.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotIdentityTest {

    @Test
    void recognizesBotAccounts() {
        assertTrue(BotIdentity.isBotUsername("bot_ace"));
        assertTrue(BotIdentity.isBotEmail("bot_ace@bot.local"));
        assertFalse(BotIdentity.isBotUsername("player1"));
        assertEquals("Bot Ace", BotIdentity.displayName("bot_ace"));
    }
}
