package com.hpsmiles.golfsim.history

import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.data.entity.GameResultEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class GameHistorySortingTest {

    @Test
    fun `groups by game mode preserving type order`() {
        val results = listOf(
            result(GameModes.BREAK_PANE, 10, 1000),
            result(GameModes.TARGET_PRACTICE, 80, 2000),
        )
        val sorted = GameHistorySorting.sorted(results, GameHistorySorting.Order.RECENCY)
        assertEquals(listOf(GameModes.TARGET_PRACTICE, GameModes.BREAK_PANE), sorted.keys.toList())
        assertEquals(listOf(80), sorted[GameModes.TARGET_PRACTICE]!!.map { it.score })
        assertEquals(listOf(10), sorted[GameModes.BREAK_PANE]!!.map { it.score })
    }

    @Test
    fun `recency order newest first with id tiebreak`() {
        val results = listOf(
            result(GameModes.TARGET_PRACTICE, 50, 1000, id = 1),
            result(GameModes.TARGET_PRACTICE, 60, 1000, id = 2),
        )
        val sorted = GameHistorySorting.sorted(results, GameHistorySorting.Order.RECENCY)
        assertEquals(listOf(2L, 1L), sorted[GameModes.TARGET_PRACTICE]!!.map { it.id })
    }

    @Test
    fun `best order target practice descending score`() {
        val results = listOf(
            result(GameModes.TARGET_PRACTICE, 50, 1000),
            result(GameModes.TARGET_PRACTICE, 90, 500),
            result(GameModes.TARGET_PRACTICE, 70, 2000),
        )
        val sorted = GameHistorySorting.sorted(results, GameHistorySorting.Order.SCORE)
        assertEquals(listOf(90, 70, 50), sorted[GameModes.TARGET_PRACTICE]!!.map { it.score })
    }

    @Test
    fun `best order break pane ascending score`() {
        val results = listOf(
            result(GameModes.BREAK_PANE, 15, 1000),
            result(GameModes.BREAK_PANE, 8, 500),
            result(GameModes.BREAK_PANE, 12, 2000),
        )
        val sorted = GameHistorySorting.sorted(results, GameHistorySorting.Order.SCORE)
        assertEquals(listOf(8, 12, 15), sorted[GameModes.BREAK_PANE]!!.map { it.score })
    }

    private fun result(mode: String, score: Int, playedAt: Long, id: Long = 0) = GameResultEntity(
        id = id,
        mode = mode,
        difficulty = null,
        distanceBin = 100,
        score = score,
        source = 0,
        playedAtEpochMs = playedAt,
    )
}
