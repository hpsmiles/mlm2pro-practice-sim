package com.hpsmiles.golfsim.bag

import kotlin.math.max

/**
 * Distance-window snap, metres — VESTIGIAL (2026-10-16, shared-axis decision):
 * the TOP-DOWN window is now the page's shared [BagDistanceAxis] (min = 0,
 * max = the axis max), so there is nothing left to snap. Kept so the round-2/3
 * history stays greppable; the containment machinery that consumed it survives
 * as the squash fallback in [computeBagTopDownFit].
 */
const val TOP_DOWN_SNAP_M = 25.0

/**
 * Smallest distance window, metres — VESTIGIAL for the same reason as
 * [TOP_DOWN_SNAP_M]: the shared axis spans ≥ 1.06 × the largest plotted
 * distance, so a thin session can no longer produce a degenerate window.
 */
const val TOP_DOWN_MIN_WINDOW_M = 50.0

/** Lateral breathing room the true-scale window must cover past the widest dot, metres. */
const val TOP_DOWN_LATERAL_MARGIN_M = 2.0

/**
 * The canvas pads (dp) the TOP-DOWN canvas insets its plot by — the single
 * source for both the view's own insets and the page-level pane floor
 * ([minPaneHeightPx]), so the floor can never silently under-reserve if the
 * view's pads change (2026-10-16 round 4, finding D).
 */
const val TOP_DOWN_LEFT_PAD_DP = 26
const val TOP_DOWN_RIGHT_PAD_DP = 12

/**
 * Minimum chart-pane height (px) that satisfies the true-scale containment
 * invariant on the SHARED axis span: at 1:1 the vertical reach is
 * span · heightPx / (2 · plotWidth), so containment for the widest lateral
 * dot requires
 *     heightPx >= (maxAbsSideM + margin) * 2 * plotWidthPx / spanM
 * Recomputed against the shared span (2026-10-16 shared-axis decision) —
 * no longer the full [RangeScene.FAIRWAY_END_Y] range. The page
 * (BagMappingResult) reserves exactly this height for the chart pane when
 * the panel is open; below it, the fit falls back to the squash.
 */
fun minPaneHeightPx(maxAbsSideM: Double, plotWidthPx: Float, spanM: Double): Float =
    ((maxAbsSideM + TOP_DOWN_LATERAL_MARGIN_M) * 2.0 * plotWidthPx / spanM).toFloat()

/**
 * The TOP-DOWN view's fit. One instance drives both the canvas and the tap
 * hit-test, so a dot can never be drawn somewhere it cannot be tapped.
 */
data class BagTopDownFit(
    val windowMinM: Double,
    val windowMaxM: Double,
    val pxPerMM: Float,
    val leftPadPx: Float,
    val pxPerMSide: Float,
    val midY: Float,
) {
    /** Screen x of a downrange distance. */
    fun sx(distanceM: Double): Float = leftPadPx + ((distanceM - windowMinM) * pxPerMM).toFloat()

    /** Screen y of a lateral offset (+ = right of target = down the screen; sign verified 2026-10-16). */
    fun sy(sideM: Double): Float = midY + (sideM * pxPerMSide).toFloat()
}

/**
 * Fits the session's content onto the page's SHARED distance axis
 * (2026-10-16, user decision): the window IS the axis — min 0 (the tee), max
 * [BagDistanceAxis.maxM] — identical to the SIDE chart and the box-plot
 * matrix, so ticks land at the same metre values everywhere. The old 25 m
 * data snap is vestigial ([TOP_DOWN_SNAP_M]); of the round-2/3 containment
 * machinery only the SQUASH fallback survives: at strict 1:1 parity
 * (pxPerMSide = pxPerMM) the vertical reach is span * heightPx / (2 *
 * plotWidth); when the shared span cannot make that cover the widest lateral
 * dot (a short pane), the vertical scale squashes to the largest value that
 * still contains maxAbsSide + margin — the ONE deviation from strict 1:1
 * (containment wins over parity). The height is floored at 1 px everywhere
 * (round-4 finding H) so a zero-height pane can never yield pxPerMSide = 0
 * (every dot collapsing onto y = 0).
 */
fun computeBagTopDownFit(
    widthPx: Float,
    heightPx: Float,
    maxAbsSideM: Double,
    axis: BagDistanceAxis,
    // Callers pass density-scaled px of TOP_DOWN_LEFT_PAD_DP / _RIGHT_PAD_DP
    // (see BagTopDownView); the px defaults below only serve unit tests.
    leftPadPx: Float = TOP_DOWN_LEFT_PAD_DP.toFloat(),
    rightPadPx: Float = TOP_DOWN_RIGHT_PAD_DP.toFloat(),
): BagTopDownFit {
    val windowMinM = axis.minM
    val windowMaxM = axis.maxM
    val span = maxOf(windowMaxM - windowMinM, 1.0)
    val plotWidth = max(widthPx - leftPadPx - rightPadPx, 1f)
    val safeHeight = max(heightPx, 1f)
    var pxPerMSide = plotWidth / span
    // FALLBACK — the ONE deviation from strict 1:1 (2026-10-16 round 3),
    // recomputed against the SHARED span: when the pane is too short for the
    // true-scale vertical reach to cover the widest dot, squash the vertical
    // scale to the largest value that still contains maxAbsSide + margin.
    val reachM = span * safeHeight / (2.0 * plotWidth)
    if (reachM < maxAbsSideM + TOP_DOWN_LATERAL_MARGIN_M) {
        pxPerMSide = (safeHeight / 2f) / (maxAbsSideM + TOP_DOWN_LATERAL_MARGIN_M)
    }
    return BagTopDownFit(
        windowMinM = windowMinM,
        windowMaxM = windowMaxM,
        pxPerMM = (plotWidth / span).toFloat(),
        leftPadPx = leftPadPx,
        pxPerMSide = pxPerMSide.toFloat(),
        midY = safeHeight / 2f,
    )
}
