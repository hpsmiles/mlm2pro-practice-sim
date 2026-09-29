package com.hpsmiles.golfsim.history

import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.data.entity.GameResultEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHistoryFormatsTest {

    @Test
    fun `mode labels are readable`() {
        assertEquals("TARGET PRACTICE", GameHistoryFormats.modeLabel(GameModes.TARGET_PRACTICE))
        assertEquals("BREAK THE PANE", GameHistoryFormats.modeLabel(GameModes.BREAK_PANE))
    }

    @Test
    fun `score labels match game meaning`() {
        val target = GameResultEntity(
            mode = GameModes.TARGET_PRACTICE, difficulty = "MEDIUM",
            distanceBin = 100, score = 75, source = 0, playedAtEpochMs = 0,
        )
        assertTrue(GameHistoryFormats.scoreLabel(target).contains("75 / 125 pts"))
        val pane = GameResultEntity(
            mode = GameModes.BREAK_PANE, difficulty = null,
            distanceBin = 100, score = 12, source = 0, playedAtEpochMs = 0,
        )
        assertTrue(GameHistoryFormats.scoreLabel(pane).contains("12 shots"))
    }

    @Test
    fun `target distance and datetime format`() {
        assertEquals("120 m", GameHistoryFormats.targetDistanceM(120))
        assertTrue(GameHistoryFormats.dateTime(1727580000000L).matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")))
    }
}
