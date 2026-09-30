package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.data.entity.GameModes

/** Cross-game high-score comparison outcome (spec 2026-09-30 §3.2). */
enum class RecordOutcome { FIRST_SCORE, NEW_RECORD, OFF_RECORD }

/** A completed game's score compared against its record key's prior best. */
data class RecordComparison(val outcome: RecordOutcome, val best: Int?, val delta: Int)

/**
 * Pure high-score comparison + overlay copy. Records are derived from the
 * existing game_results table (no schema change). TP scores points
 * (higher-is-better); BP scores shots taken (lower-is-better).
 */
object GameRecord {

    /** BP scores shots-taken (lower is better); TP scores points (higher is better). */
    fun lowerIsBetter(mode: String): Boolean = mode == GameModes.BREAK_PANE

    /**
     * Compares [score] against [prevBest]. Null [prevBest] means no matching
     * prior row (first score at this key) -> FIRST_SCORE, no celebration.
     * [delta] is the improvement (NEW_RECORD) or the shortfall (OFF_RECORD),
     * always >= 0.
     */
    fun compare(prevBest: Int?, score: Int, lowerIsBetter: Boolean): RecordComparison =
        when {
            prevBest == null -> RecordComparison(RecordOutcome.FIRST_SCORE, null, 0)
            better(score, prevBest, lowerIsBetter) ->
                RecordComparison(RecordOutcome.NEW_RECORD, prevBest, kotlin.math.abs(prevBest - score))
            else ->
                RecordComparison(RecordOutcome.OFF_RECORD, prevBest, kotlin.math.abs(score - prevBest))
        }

    /** Short overlay headline. */
    fun headline(comparison: RecordComparison, lowerIsBetter: Boolean): String = when (comparison.outcome) {
        RecordOutcome.FIRST_SCORE -> "FIRST SCORE"
        RecordOutcome.NEW_RECORD -> "NEW HIGH SCORE"
        RecordOutcome.OFF_RECORD -> if (lowerIsBetter) "SHOTS OFF YOUR BEST" else "PTS OFF YOUR BEST"
    }

    /** Second overlay line: the margin vs the prior best. */
    fun detail(comparison: RecordComparison, lowerIsBetter: Boolean): String {
        val unit = if (lowerIsBetter) "shots" else "points"
        return when (comparison.outcome) {
            RecordOutcome.FIRST_SCORE -> "First score at this distance and difficulty."
            RecordOutcome.NEW_RECORD -> "Beat your best (${comparison.best}) by ${comparison.delta} $unit."
            RecordOutcome.OFF_RECORD -> "${comparison.delta} $unit off your best (${comparison.best})."
        }
    }

    private fun better(score: Int, best: Int, lowerIsBetter: Boolean): Boolean =
        if (lowerIsBetter) score < best else score > best
}
