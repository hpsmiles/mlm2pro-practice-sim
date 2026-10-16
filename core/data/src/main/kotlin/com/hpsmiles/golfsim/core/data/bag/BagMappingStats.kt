package com.hpsmiles.golfsim.core.data.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import kotlin.math.abs
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

    // --- Misread gates (2026-10-16) --------------------------------------

    const val REASON_MISREAD_SMASH = "MISREAD_SMASH"
    const val REASON_MISREAD_CHS = "MISREAD_CHS"
    const val REASON_MISREAD_SPIN = "MISREAD_SPIN"
    const val REASON_MISREAD_LAUNCH_ANGLE = "MISREAD_LAUNCH_ANGLE"
    const val REASON_MISREAD_DIRECTION = "MISREAD_DIRECTION"

    /** Hard physical cap on smash factor — a ball cannot leave much faster than 1.5× CHS. */
    const val SMASH_PHYSICAL_MAX = 1.50

    /** Smash outside 0.85×–1.15× the club's median smash is a misread. */
    const val SMASH_MEDIAN_LOW = 0.85
    const val SMASH_MEDIAN_HIGH = 1.15

    /** CHS outside 0.95×–1.05× the club's median CHS (a ±5% band) is a misread. */
    const val CHS_MEDIAN_LOW = 0.95
    const val CHS_MEDIAN_HIGH = 1.05

    /** Spin outside 0.5×–2.0× the club's median spin is a misread. */
    const val SPIN_MEDIAN_LOW = 0.5
    const val SPIN_MEDIAN_HIGH = 2.0

    /** Launch angle outside 0.6×–1.5× the club's median launch is a misread. */
    const val LAUNCH_MEDIAN_LOW = 0.6
    const val LAUNCH_MEDIAN_HIGH = 1.5

    /** Direction misread: |launchDir − median| > 12° OR |spinAxis − median| > 20°. */
    const val DIRECTION_LAUNCH_DEG = 12.0
    const val DIRECTION_SPIN_AXIS_DEG = 20.0

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

    /**
     * Misread gates (2026-10-16): same shape as the duff filter — median over
     * ALL the club's shots (kept and filtered alike), dormant below
     * [DUFF_MIN_SHOTS]. One shot = one reason. When a shot fails several
     * gates the FIRST failing gate in this order wins:
     *
     *   MISREAD_SMASH > MISREAD_CHS > MISREAD_SPIN > MISREAD_LAUNCH_ANGLE > MISREAD_DIRECTION
     *
     * Smash leads because a value above [SMASH_PHYSICAL_MAX] is physically
     * impossible (a hardware misread) and the median-band check is the most
     * damning; CHS rides the same swing-speed trace, so it ranks just below
     * smash (a wildly wrong CHS usually distorts smash too — 5i #43 reports
     * MISREAD_SMASH even though its CHS also trips); spin is a clean optical
     * trace; launch follows; direction is the loosest, most common tolerance,
     * so it ranks last. The duff low-ball-speed rule is deliberately separate
     * — a duff (or a real strike with proportionally low CHS+ball speed)
     * keeps a normal smash and CHS ratio and must not trip these gates.
     */
    fun applyMisreadGates(shots: List<BagMappingShotEntity>): List<FilterVerdict> {
        if (shots.size < DUFF_MIN_SHOTS) {
            return shots.map { FilterVerdict(it.id, filtered = false, reason = null) }
        }
        val medianSmash = median(
            shots.filter { it.clubHeadSpeedMps > 0.0 }.map { it.ballSpeedMps / it.clubHeadSpeedMps },
        )
        val medianChs = median(shots.map { it.clubHeadSpeedMps })
        val medianSpin = median(shots.map { it.totalSpinRpm.toDouble() })
        val medianLaunch = median(shots.map { it.launchAngleDeg })
        val medianDir = median(shots.map { it.launchDirDeg })
        val medianAxis = median(shots.map { it.spinAxisDeg })
        return shots.map { shot ->
            val reason = misreadReason(shot, medianSmash, medianChs, medianSpin, medianLaunch, medianDir, medianAxis)
            FilterVerdict(shot.id, filtered = reason != null, reason = reason)
        }
    }

    /** First failing misread gate for one shot (precedence order above). */
    private fun misreadReason(
        shot: BagMappingShotEntity,
        medianSmash: Double?,
        medianChs: Double?,
        medianSpin: Double?,
        medianLaunch: Double?,
        medianDir: Double?,
        medianAxis: Double?,
    ): String? {
        val smash = if (shot.clubHeadSpeedMps > 0.0) shot.ballSpeedMps / shot.clubHeadSpeedMps else null
        if (smash != null) {
            if (smash > SMASH_PHYSICAL_MAX) return REASON_MISREAD_SMASH
            if (medianSmash != null &&
                (smash < medianSmash * SMASH_MEDIAN_LOW || smash > medianSmash * SMASH_MEDIAN_HIGH)
            ) {
                return REASON_MISREAD_SMASH
            }
        }
        if (medianChs != null) {
            val chs = shot.clubHeadSpeedMps
            if (chs < medianChs * CHS_MEDIAN_LOW || chs > medianChs * CHS_MEDIAN_HIGH) {
                return REASON_MISREAD_CHS
            }
        }
        if (medianSpin != null) {
            val spin = shot.totalSpinRpm.toDouble()
            if (spin < medianSpin * SPIN_MEDIAN_LOW || spin > medianSpin * SPIN_MEDIAN_HIGH) {
                return REASON_MISREAD_SPIN
            }
        }
        if (medianLaunch != null) {
            val launch = shot.launchAngleDeg
            if (launch < medianLaunch * LAUNCH_MEDIAN_LOW || launch > medianLaunch * LAUNCH_MEDIAN_HIGH) {
                return REASON_MISREAD_LAUNCH_ANGLE
            }
        }
        if (medianDir != null && abs(shot.launchDirDeg - medianDir) > DIRECTION_LAUNCH_DEG) {
            return REASON_MISREAD_DIRECTION
        }
        if (medianAxis != null && abs(shot.spinAxisDeg - medianAxis) > DIRECTION_SPIN_AXIS_DEG) {
            return REASON_MISREAD_DIRECTION
        }
        return null
    }

    /**
     * Full auto verdict: the duff filter PLUS the misread gates, one reason
     * per shot. A misread reason wins over a duff — a physically impossible
     * or wildly off reading is the more specific diagnosis than a low carry.
     * The repository persists this on every append; the effective inclusion
     * then layers the user's [BagMappingShotEntity.includeOverride] on top.
     */
    fun applyAutoFilter(shots: List<BagMappingShotEntity>): List<FilterVerdict> {
        val duff = applyDuffFilter(shots).associateBy { it.id }
        return applyMisreadGates(shots).map { gate ->
            if (gate.filtered) gate else duff[gate.id] ?: gate
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
