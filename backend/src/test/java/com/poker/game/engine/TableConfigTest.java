package com.poker.game.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableConfigTest {

    @Test
    void developmentDefaultsMatchTheSpec() {
        TableConfig config = TableConfig.developmentDefaults();
        assertEquals(10, config.smallBlind());
        assertEquals(20, config.bigBlind());
        assertEquals(1000, config.startingChips());
        assertEquals(6, config.maxPlayers());
        assertEquals(5, config.maxDrawCount());
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new TableConfig(0, 20, 1000, 6));
        assertThrows(IllegalArgumentException.class, () -> new TableConfig(20, 10, 1000, 6));
        assertThrows(IllegalArgumentException.class, () -> new TableConfig(10, 20, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> new TableConfig(10, 20, 1000, 1));
        assertThrows(IllegalArgumentException.class, () -> new TableConfig(10, 20, 1000, 6, 6));
    }
}
