package com.hpsmiles.golfsim.fitting

import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.max
import kotlin.math.min

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
 * Computes the world→pixel mapping that fits the kept shots AND their
 * 2σ ring extents plus a buffer into the canvas (user request 2026-10-08:
 * "scaled to fit the shots and rings plus a small buffer" — 0–150 m of
 * empty range is useless on a driver fitting). Isotropic (rings stay
 * circular); falls back to the full-range mapping when there is no data.
 *
 * [minY]/[maxY] are the min/max rest-distance (down-range, metres) and
 * [maxAbsX] the max |side| (lateral, metres) over the kept shots INCLUDING
 * each club's 2σ ring outer extents. [hasData] is false when there are no
 * kept shots — the mapping then matches the pre-fix full-range view
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
