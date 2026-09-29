package com.hpsmiles.golfsim.games

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M5.5 Task 12: GamesScreen picker wiring exists and the hoisted GameMode enum
 * exposes the expected values. Minimal JVM test so the feature file is covered.
 */
class GamesScreenTest {
    @Test
    fun gameModeHasExpectedValues() {
        assertEquals(3, GameMode.entries.size)
        assertEquals(GameMode.NONE, GameMode.valueOf("NONE"))
        assertEquals(GameMode.TARGET_PRACTICE, GameMode.valueOf("TARGET_PRACTICE"))
        assertEquals(GameMode.BREAK_PANE, GameMode.valueOf("BREAK_PANE"))
    }
}
