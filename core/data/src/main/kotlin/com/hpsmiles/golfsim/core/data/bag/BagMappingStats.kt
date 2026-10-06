package com.hpsmiles.golfsim.core.data.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import kotlin.math.sqrt

/**
 * Pure M6 mapping math (spec §5): duff filter + per-club distributions.
 * No framework types; deterministic — same shots in, same verdicts out.
 * Lives in :core:data because the repository write path recomputes the
 * filter on every append; :app consumes it for rendering.
 */
object BagMappingStats {

    /** A shot below this fraction of the club's median ball speed is a duff. */
    const val DUFF_BALL_SPEED_FRACTION = 0.85

    /** The duff filter stays dormant until the club has this many shots. */
    const val DUFF_MIN_SHOTS = 3

    const val REASON_DUFF_LOW_BALL_SPEED = "DUFF_LOW_BALL_SPEED"

    /** One shot's recomputed filter state, keyed by row id. */
    data class FilterVerdict(val id: Long, val filtered: Boolean, val reason: String?)

    /**
     * Median over ALL stored shots of the club (kept and filtered alike —
     * spec worked example). Dormant below [DUFF_MIN_SHOTS]: everything kept.
     * From 3+ shots EVERY shot is judged (including the earliest): ball
     * speed < 85% of the median ball speed → DUFF_LOW_BALL_SPEED.
     * Low-side only — an unusually long strike is real data (spec §5).
     */
    fun applyDuffFilter(shots: List<BagMappingShotEntity>): List<FilterVerdict> {
        if (shots.size < DUFF_MIN_SHOTS) {
            return shots.map { FilterVerdict(it.id, filtered = false, reason = null) }
        }
        val medianBallSpeed = median(shots.map { it.ballSpeedMps })
            ?: return shots.map { FilterVerdict(it.id, filtered = false, reason = null) }
        val cutoff = medianBallSpeed * DUFF_BALL_SPEED_FRACTION
        return shots.map { shot ->
            if (shot.ballSpeedMps < cutoff) {
                FilterVerdict(shot.id, filtered = true, reason = REASON_DUFF_LOW_BALL_SPEED)
            } else {
                FilterVerdict(shot.id, filtered = false, reason = null)
            }
        }
    }

    /** Median: middle value, or mean of the two middle values. Null when empty. */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** Distribution summary over one club's kept carries (null when empty). */
    data class Distribution(
        val median: Double,
        val mean: Double,
        /** Population σ (descriptive, fixed set). */
        val sigma: Double,
        val q1: Double,
        val q3: Double,
        val min: Double,
        val max: Double,
        val count: Int,
    )

    fun distribution(values: List<Double>): Distribution? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mean = sorted.average()
        val variance = sorted.sumOf { (it - mean) * (it - mean) } / sorted.size
        return Distribution(
            median = median(sorted)!!,
            mean = mean,
            sigma = sqrt(variance),
            q1 = tukeyHalf(sorted, lower = true),
            q3 = tukeyHalf(sorted, lower = false),
            min = sorted.first(),
            max = sorted.last(),
            count = sorted.size,
        )
    }

    /**
     * Tukey halves (median INCLUDED in both halves for odd sizes — classic
     * box plot): Q1 = median of the lower half, Q3 = median of the upper half.
     */
    private fun tukeyHalf(sorted: List<Double>, lower: Boolean): Double {
        val halfSize = (sorted.size + 1) / 2
        val half = if (lower) {
            sorted.subList(0, halfSize)
        } else {
            sorted.subList(sorted.size - halfSize, sorted.size)
        }
        return median(half)!!
    }
}
