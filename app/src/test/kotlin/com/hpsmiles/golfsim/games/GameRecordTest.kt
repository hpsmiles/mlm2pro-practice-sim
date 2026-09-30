package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.data.entity.GameModes
import org.junit.Assert.assertEquals
import org.junit.Test

class GameRecordTest {

    @Test
    fun `no prior best is first score - no celebration`() {
        val c = GameRecord.compare(prevBest = null, score = 90, lowerIsBetter = false)
        assertEquals(RecordOutcome.FIRST_SCORE, c.outcome)
        assertEquals(null, c.best)
        assertEquals(0, c.delta)
    }

    @Test
    fun `higher is better detects a new record and its margin`() {
        val c = GameRecord.compare(prevBest = 90, score = 105, lowerIsBetter = false)
        assertEquals(RecordOutcome.NEW_RECORD, c.outcome)
        assertEquals(90, c.best)
        assertEquals(15, c.delta)
    }

    @Test
    fun `higher is better off-record reports the shortfall`() {
        val c = GameRecord.compare(prevBest = 105, score = 90, lowerIsBetter = false)
        assertEquals(RecordOutcome.OFF_RECORD, c.outcome)
        assertEquals(105, c.best)
        assertEquals(15, c.delta)
    }

    @Test
    fun `a tie is not a new record`() {
        val c = GameRecord.compare(prevBest = 90, score = 90, lowerIsBetter = false)
        assertEquals(RecordOutcome.OFF_RECORD, c.outcome)
        assertEquals(0, c.delta)
    }

    @Test
    fun `lower is better detects a new record and its margin`() {
        val c = GameRecord.compare(prevBest = 14, score = 11, lowerIsBetter = true)
        assertEquals(RecordOutcome.NEW_RECORD, c.outcome)
        assertEquals(14, c.best)
        assertEquals(3, c.delta)
    }

    @Test
    fun `lower is better off-record reports the excess shots`() {
        val c = GameRecord.compare(prevBest = 11, score = 14, lowerIsBetter = true)
        assertEquals(RecordOutcome.OFF_RECORD, c.outcome)
        assertEquals(3, c.delta)
    }

    @Test
    fun `break pane is lower-is-better, target practice is not`() {
        assertEquals(true, GameRecord.lowerIsBetter(GameModes.BREAK_PANE))
        assertEquals(false, GameRecord.lowerIsBetter(GameModes.TARGET_PRACTICE))
    }

    @Test
    fun `overlay copy names the unit per game`() {
        val tpOff = RecordComparison(RecordOutcome.OFF_RECORD, 105, 15)
        assertEquals("15 points off your best (105).", GameRecord.detail(tpOff, lowerIsBetter = false))
        val bpOff = RecordComparison(RecordOutcome.OFF_RECORD, 11, 3)
        assertEquals("3 shots off your best (11).", GameRecord.detail(bpOff, lowerIsBetter = true))
        val new = RecordComparison(RecordOutcome.NEW_RECORD, 90, 12)
        assertEquals("Beat your best (90) by 12 points.", GameRecord.detail(new, lowerIsBetter = false))
        assertEquals("NEW HIGH SCORE", GameRecord.headline(new, lowerIsBetter = false))
    }
}
