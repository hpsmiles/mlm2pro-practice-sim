package com.hpsmiles.golfsim.range

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Painted-range scene definition (pure data + geometry, no rendering).
 * All coordinates are the shared metres world frame: x lateral, y down-range,
 * z up. Topography seam: [groundHeight] returns 0 this phase; future terrain
 * is a data change here, not a renderer rewrite.
 */
object RangeScene {

    const val FAIRWAY_TEE_Y = 10.0
    const val FAIRWAY_END_Y = 330.0
    const val GROUND_END_Y = 350.0
    const val STRIPE_WIDTH_M = 10.0

    private const val HALF_WIDTH_TEE = 20.0
    private const val HALF_WIDTH_END = 23.0

    // Practice grid identity (tunable on tablet review).
    const val CENTER_LINE_HALF_WIDTH_M = 0.2
    const val CENTER_LINE_ALPHA = 0.55f
    const val GUIDE_LINE_HALF_WIDTH_M = 0.1
    const val GUIDE_LINE_ALPHA = 0.30f
    val GUIDE_LINE_LATERALS_M = listOf(-20.0, -10.0, 10.0, 20.0)
    const val GUIDE_DASH_ON_M = 4.0
    const val GUIDE_DASH_OFF_M = 2.0
    const val GUIDE_DASH_CYCLE_M = GUIDE_DASH_ON_M + GUIDE_DASH_OFF_M

    /** Green with fringe (matching target-oval positions). */
    data class Green(
        val lateralM: Double,
        val distanceM: Double,
        val radiusM: Double,
        val fringeRadiusM: Double,
    ) {
        fun isFringe(x: Double, y: Double): Boolean {
            val dx = x - lateralM
            val dy = y - distanceM
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            return r <= fringeRadiusM && r > radiusM
        }

        fun isSurface(x: Double, y: Double): Boolean {
            val dx = x - lateralM
            val dy = y - distanceM
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            return r <= radiusM
        }
    }

    val greens = listOf(
        Green(-12.0, 75.0, 7.0, 9.5),
        Green(0.0, 100.0, 7.0, 9.5),
        Green(12.0, 150.0, 7.0, 9.5),
    )

    /** Topography seam — flat this phase (spec non-goal: no topography yet). */
    fun groundHeight(x: Double, y: Double): Double = 0.0

    /** ~40 m wide at the tee, widening to ~46 m at the fairway end. */
    fun fairwayHalfWidth(y: Double): Double {
        val t = ((y - FAIRWAY_TEE_Y) / (FAIRWAY_END_Y - FAIRWAY_TEE_Y)).coerceIn(0.0, 1.0)
        return HALF_WIDTH_TEE + (HALF_WIDTH_END - HALF_WIDTH_TEE) * t
    }

    fun isOnFairway(x: Double, y: Double): Boolean =
        y in FAIRWAY_TEE_Y..FAIRWAY_END_Y && abs(x) <= fairwayHalfWidth(y)

    /** Even bands from the tee; true = lighter mow stripe. */
    fun stripeIsLight(x: Double, y: Double): Boolean =
        kotlin.math.floor((y - FAIRWAY_TEE_Y) / STRIPE_WIDTH_M).toInt() % 2 == 0

    /** Fairway outline as world vertices (closed as polygon by caller). */
    fun fairwayOutline(): List<Pair<Double, Double>> {
        val steps = 17
        val out = ArrayList<Pair<Double, Double>>(steps * 2)
        for (i in 0..steps) {
            val y = FAIRWAY_TEE_Y + (FAIRWAY_END_Y - FAIRWAY_TEE_Y) * i / steps
            out.add(fairwayHalfWidth(y) to y)
        }
        for (i in steps downTo 0) {
            val y = FAIRWAY_TEE_Y + (FAIRWAY_END_Y - FAIRWAY_TEE_Y) * i / steps
            out.add(-fairwayHalfWidth(y) to y)
        }
        return out
    }

    /** Circle outline as world vertices (n points), projected as a ground polygon. */
    fun circleOutline(cx: Double, cy: Double, r: Double, n: Int = 40): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>(n)
        for (i in 0 until n) {
            val a = 2.0 * PI * i / n
            out.add(cx + r * cos(a) to cy + r * sin(a))
        }
        return out
    }

    /** Stripe band trapezoid on the fairway between yFrom and yTo (clipped to fairway extent). */
    fun stripeBand(index: Int): Pair<Double, Double> {
        val yFrom = FAIRWAY_TEE_Y + index * STRIPE_WIDTH_M
        val yTo = minOf(yFrom + STRIPE_WIDTH_M, FAIRWAY_END_Y)
        return yFrom to yTo
    }

    /** Number of full/partial 10 m stripe bands needed to cover the fairway. */
    fun stripeBandCount(): Int =
        kotlin.math.ceil((FAIRWAY_END_Y - FAIRWAY_TEE_Y) / STRIPE_WIDTH_M).toInt()

    /**
     * World-space dash segments spanning the whole painted ground from tee to
     * [GROUND_END_Y]. Alternating 4 m on / 2 m off along y; each segment is
     * (yFrom, yTo). The final segment is clipped so it never runs past the
     * ground end. Callers pair each segment with a lateral offset.
     */
    fun guideDashSegments(): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        var y = FAIRWAY_TEE_Y
        while (y < GROUND_END_Y) {
            val yTo = minOf(y + GUIDE_DASH_ON_M, GROUND_END_Y)
            out.add(y to yTo)
            y += GUIDE_DASH_CYCLE_M
        }
        return out
    }
}
