package com.hpsmiles.golfsim.bag

import kotlin.math.ceil
import kotlin.math.round

/**
 * ONE shared distance axis for the whole BAG results page (2026-10-16, user
 * decision): the SIDE chart, the TOP-DOWN dispersion view and the box-plot
 * distribution matrix all range over [min, max] with the same tick step, so a
 * given metre value sits at the same fractional position (and ticks land at
 * identical metre values) in all three views.
 *
 * Construction (tracker requirements 2-4):
 *  - min = 0 (the tee; SIDE needs a zero origin);
 *  - step = the existing 1/2/5×10^k nice-step ladder ([BoxPlotGeom.niceStep])
 *    at ~[TARGET_TICKS] target ticks over the raw headroom span — reused, not
 *    re-implemented;
 *  - max = a whole multiple of the step ≥ [HEADROOM] × the largest distance
 *    fed in — the caller must feed EVERY plotted distance: all carries AND
 *    totals, kept AND filtered (the matrix plots filtered carries too).
 */
data class BagDistanceAxis(val stepM: Double, val maxM: Double) {

    /** min = 0 — the tee. */
    val minM: Double = 0.0

    /**
     * Tick values at the shared step: 0, step, 2·step … maxM. maxM is a whole
     * multiple of the step by construction, so the last tick IS the axis end.
     * Fuzz-snapped like [BoxPlotGeom.niceTicks] so labels compare cleanly.
     */
    val ticksM: List<Double> = buildList {
        val n = round(maxM / stepM).toInt()
        for (i in 0..n) add(round(i * stepM * 1e6) / 1e6)
    }

    companion object {
        /** Distance headroom past the largest plotted distance (was SIDE-only). */
        const val HEADROOM = 1.06

        /** Target tick count for the step ladder (BoxPlotGeom's default). */
        const val TARGET_TICKS = 10

        /**
         * Axis over every distance that will be drawn anywhere on the page.
         * Null when there is nothing to plot (no shots, or only non-positive
         * distances) — the views render their empty states.
         */
        fun fromDistances(distances: List<Double>): BagDistanceAxis? {
            val largest = distances.maxOrNull() ?: return null
            if (largest <= 0.0 || !largest.isFinite()) return null
            val rawSpanM = largest * HEADROOM
            val stepM = BoxPlotGeom.niceStep(rawSpanM, TARGET_TICKS)
            val maxM = ceil(rawSpanM / stepM) * stepM
            return BagDistanceAxis(stepM = stepM, maxM = maxM)
        }
    }
}
