package com.hpsmiles.golfsim.fitting

import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.max
import kotlin.math.min

/** Per-side axis buffer as a fraction of that axis's own span (device ruling 2026-10-08: "5 % of span"). */
const val BBOX_BUFFER_FRACTION = 0.05

/**
 * World→pixel mapping for the M7 top-down pane (design notes §2.3). World
 * frame: x lateral (side, metres), y down-range (rest position = total,
 * metres). Screen: origin at (originX, originY) = the world origin; a world
 * point (x, y) maps to (originX + x·pxPerM, originY − y·pxPerM).
 */
data class TopDownFit(
    /** World metres → screen px. Isotropic — one scale for both axes, rings stay circular. */
    val pxPerM: Double,
    /** Screen x of the world x = 0 axis (lateral centre). */
    val originX: Double,
    /** Screen y of the world y = 0 origin (the mat/tee). */
    val originY: Double,
)

/**
 * Buffered kept-shot bounding box (device ruling 2026-10-08): the KEPT
 * shots' (side, total) extremes, each axis expanded by 5 % of its own span
 * per side. The buffered rectangle IS the dispersion metric everywhere —
 * its area is the AREA column's number and its outline is the top-down ring
 * (the 2σ ellipse is superseded; raw bbox area has no industry namesake, so
 * it is documented as our own metric). Metres in, metres out; no framework
 * types.
 */
data class DispersionBox(
    val minSideM: Double, val maxSideM: Double,
    val minTotalM: Double, val maxTotalM: Double,
) {
    val widthM: Double get() = maxSideM - minSideM
    val depthM: Double get() = maxTotalM - minTotalM

    /** Axis span grown by 5 % per side; zero-span axes stay zero. */
    fun bufferedWidthM(): Double = widthM * (1.0 + 2.0 * BBOX_BUFFER_FRACTION)
    fun bufferedDepthM(): Double = depthM * (1.0 + 2.0 * BBOX_BUFFER_FRACTION)

    /** The number shown in the AREA column: area of the buffered box. */
    fun bufferedAreaM2(): Double = bufferedWidthM() * bufferedDepthM()

    /** Ring extents for auto-fit: the buffered box itself. */
    fun bufferedMinSideM(): Double = minSideM - widthM * BBOX_BUFFER_FRACTION
    fun bufferedMaxSideM(): Double = maxSideM + widthM * BBOX_BUFFER_FRACTION
    fun bufferedMinTotalM(): Double = minTotalM - depthM * BBOX_BUFFER_FRACTION
    fun bufferedMaxTotalM(): Double = maxTotalM + depthM * BBOX_BUFFER_FRACTION

    companion object {
        /** From kept shots; null below the 3-shot minimum (existing convention). */
        fun fromKept(points: List<Pair<Double, Double>>): DispersionBox? {
            if (points.size < 3) return null
            val sides = points.map { it.first }; val totals = points.map { it.second }
            return DispersionBox(sides.min(), sides.max(), totals.min(), totals.max())
        }
    }
}

/**
 * Computes the world→pixel mapping that fits the kept shots AND their
 * 5 %-buffered-box ring extents plus a buffer into the canvas (device
 * feedback 2026-10-08: "scaled to fit the shots and rings plus a small
 * buffer" — 0–150 m of empty range is useless on a driver fitting).
 * Isotropic; falls back to the full-range mapping when there is no data.
 *
 * [minY]/[maxY] are the min/max rest-distance (down-range, metres) and
 * [maxAbsX] the max |side| (lateral, metres) over the kept shots INCLUDING
 * each club's buffered-box outer extents. [hasData] is false when there are
 * no kept shots — the mapping then matches the pre-fix full-range view
 * (`height / GROUND_END_Y`, origin [bottomMarginPx] above the canvas bottom).
 *
 * Behaviour:
 *  - ySpan = maxY − minY; a minimum span of [MIN_SPAN_M] is enforced by
 *    expanding symmetrically around the centre (prevents absurd zoom on 1–2
 *    clustered shots). pad = max([MIN_PAD_M], 0.06·ySpan) metres each side.
 *  - pxPerM = min(yFit, xFit) with yFit = height / (ySpan + 2·pad) and
 *    xFit = (width/2 − [lateralPadPx]) / maxAbsX (the x constraint is skipped
 *    when maxAbsX ≈ 0).
 *  - The y-window [minY − pad, maxY + pad] fills the canvas height when the
 *    lateral constraint doesn't bind; x stays centred (originX = width/2).
 */
fun computeTopDownFit(
    width: Double,
    height: Double,
    minY: Double,
    maxY: Double,
    maxAbsX: Double,
    hasData: Boolean,
    bottomMarginPx: Double = 0.0,
    lateralPadPx: Double = DEFAULT_LATERAL_PAD_PX,
): TopDownFit {
    val originX = width / 2.0
    if (!hasData) {
        // Exact pre-fix mapping: the whole ground extent fitted to the height,
        // origin a fixed [bottomMarginPx] above the canvas bottom — the empty
        // state looks unchanged.
        return TopDownFit(height / RangeScene.GROUND_END_Y, originX, height - bottomMarginPx)
    }
    var lo = minY
    var hi = maxY
    if (hi - lo < MIN_SPAN_M) {
        val centre = (lo + hi) / 2.0
        lo = centre - MIN_SPAN_M / 2.0
        hi = centre + MIN_SPAN_M / 2.0
    }
    val pad = max(MIN_PAD_M, 0.06 * (hi - lo))
    val yFit = height / (hi - lo + 2.0 * pad)
    var pxPerM = yFit
    if (maxAbsX > X_EPSILON) {
        pxPerM = min(pxPerM, (width / 2.0 - lateralPadPx) / maxAbsX)
    }
    // Window bottom [lo − pad] maps to the canvas bottom (y = height); the
    // world origin sits pxPerM·(lo − pad) below it (off-screen when zoomed).
    val originY = height + (lo - pad) * pxPerM
    return TopDownFit(pxPerM, originX, originY)
}

/** Smallest padded y-window, metres (1–2 clustered shots must not zoom absurdly). */
private const val MIN_SPAN_M = 30.0

/** Minimum vertical buffer each side of the y-window, metres. */
private const val MIN_PAD_M = 10.0

/** Side-buffer reserved on each lateral edge of the fitted x-extent, px. */
private const val DEFAULT_LATERAL_PAD_PX = 48.0

/** Below this |side| the lateral constraint is skipped (shots on the centre line). */
private const val X_EPSILON = 1e-9
