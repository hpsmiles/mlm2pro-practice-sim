package com.hpsmiles.golfsim.bag

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/**
 * Pure value→pixel mapping for the box-plot carry matrix painter (spec §6).
 * Plain Float pixels — no Compose types — so it stays JVM-testable.
 */
object BoxPlotGeom {

    /** Shared carry axis: [minM, maxM] → [leftPadPx, leftPadPx + plotWidthPx]. */
    data class Axis(val minM: Double, val maxM: Double, val leftPadPx: Float, val plotWidthPx: Float)

    /**
     * Auto-ranged axis over EVERY value that will be drawn (kept whiskers
     * AND filtered hollow dots — spec §6), padded 8% of the span each side.
     * Zero-width data (single distinct value) pads by max(1 m, 5% of the
     * value) so the plot never degenerates. Null when there is nothing to draw.
     */
    fun axis(values: List<Double>, leftPadPx: Float, plotWidthPx: Float): Axis? {
        if (values.isEmpty() || plotWidthPx <= 0f) return null
        val lo = values.min()
        val hi = values.max()
        val pad = if (hi - lo <= 0.0) maxOf(1.0, abs(lo) * 0.05) else (hi - lo) * 0.08
        return Axis(lo - pad, hi + pad, leftPadPx, plotWidthPx)
    }

    /** Pixel x for a carry value; values outside the axis clamp to the plot edges. */
    fun x(valueM: Double, axis: Axis): Float {
        val t = ((valueM - axis.minM) / (axis.maxM - axis.minM)).coerceIn(0.0, 1.0)
        return axis.leftPadPx + (t * axis.plotWidthPx).toFloat()
    }

    private fun abs(v: Double): Double = if (v < 0.0) -v else v

    /**
     * Round tick values for the metre scale above the matrix: 1/2/5×10^k
     * steps aimed at roughly [targetCount] labels across [minM, maxM].
     * Default 10 (2026-10-16, user: "finer ticks, 25 m" — the 1/2/5 ladder
     * can't hit 25; a ~200 m bag span gives 20 m steps, so the wedge region
     * 60–120 m carries 4 labelled ticks). Ticks are whole metres (or one
     * decimal for sub-metre steps) so labels stay short. Empty for
     * degenerate spans (empty axis, minM == maxM).
     */
    fun niceTicks(minM: Double, maxM: Double, targetCount: Int = 10): List<Double> {
        val span = maxM - minM
        if (targetCount <= 0 || span <= 0.0 || !span.isFinite()) return emptyList()
        val rawStep = span / targetCount
        val mag = 10.0.pow(floor(log10(rawStep)))
        val norm = rawStep / mag
        val step = mag * when {
            norm < 1.5 -> 1.0
            norm < 3.0 -> 2.0
            norm < 7.0 -> 5.0
            else -> 10.0
        }
        val ticks = mutableListOf<Double>()
        var v = ceil(minM / step) * step
        while (v <= maxM + 1e-9) {
            // Snap away float fuzz so tick labels compare cleanly.
            ticks += round(v * 1e6) / 1e6
            v += step
        }
        return ticks
    }
}
