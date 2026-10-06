package com.hpsmiles.golfsim.bag

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
}
