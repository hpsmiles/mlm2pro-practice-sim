// app/src/main/kotlin/com/hpsmiles/golfsim/history/GameHistorySorting.kt
package com.hpsmiles.golfsim.history

import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.data.entity.GameResultEntity

/** In-memory sorting for the game history list (kept pure + testable). */
object GameHistorySorting {

    enum class Order(val label: String) {
        RECENCY("RECENT"),
        SCORE("BEST"),
    }

    /** Group by mode, then sort each group by the chosen order. */
    fun sorted(
        results: List<GameResultEntity>,
        order: Order,
    ): Map<String, List<GameResultEntity>> {
        val grouped = results.groupBy { it.mode }
        val targetOrder = listOf(GameModes.TARGET_PRACTICE, GameModes.BREAK_PANE)
        return targetOrder.associateWith { mode ->
            grouped[mode]?.let { sortList(it, order) } ?: emptyList()
        }
    }

    private fun sortList(list: List<GameResultEntity>, order: Order): List<GameResultEntity> {
        return when (order) {
            Order.RECENCY -> list.sortedWith(compareByDescending<GameResultEntity> { it.playedAtEpochMs }.thenByDescending { it.id })
            Order.SCORE -> list.sortedWith(
                compareByDescending<GameResultEntity> { it.scoreForSort() }
                    .thenByDescending { it.playedAtEpochMs }
                    .thenByDescending { it.id },
            )
        }
    }

    /** Higher is better for Target Practice, lower is better for Break the Pane. */
    private fun GameResultEntity.scoreForSort(): Int = when (mode) {
        GameModes.TARGET_PRACTICE -> score
        GameModes.BREAK_PANE -> -score
        else -> score
    }
}
