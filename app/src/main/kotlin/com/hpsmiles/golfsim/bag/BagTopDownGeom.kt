package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Distance-window snap, metres — mirrors CombineTopDownView. */
const val TOP_DOWN_SNAP_M = 25.0

/** Smallest distance window, metres — keeps pxPerMM sane on a thin session. */
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
 * invariant at the full [RangeScene.FAIRWAY_END_Y] range: at 1:1 the vertical
 * reach is span · heightPx / (2 · plotWidth), and the span can never exceed
 * the range cap, so containment for the widest lateral dot requires
 *     heightPx >= (maxAbsSideM + margin) * 2 * plotWidthPx / FAIRWAY_END_Y
 * The page (BagMappingResult) reserves exactly this height for the chart pane
 * when the panel is open; the fit grows its window toward the same bound.
 */
fun minPaneHeightPx(maxAbsSideM: Double, plotWidthPx: Float): Float =
    ((maxAbsSideM + TOP_DOWN_LATERAL_MARGIN_M) * 2.0 * plotWidthPx /
        RangeScene.FAIRWAY_END_Y).toFloat()

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
 * Fits the session's content: the distance window is cropped to the actual
 * points and snapped outward to [TOP_DOWN_SNAP_M] (floor
 * [TOP_DOWN_MIN_WINDOW_M]) so empty range either side of the action is trimmed
 * away. The window then grows (zoom out only, re-snapped to the grid) until
 * the true-scale vertical reach covers the widest lateral content; if the
 * range cap makes that impossible, the vertical scale falls back to the
 * largest value that contains the data (see the fallback in the body). Null
 * when there is nothing to draw.
 */
fun computeBagTopDownFit(
    widthPx: Float,
    heightPx: Float,
    clubs: List<ClubDots>,
    // Callers pass density-scaled px of TOP_DOWN_LEFT_PAD_DP / _RIGHT_PAD_DP
    // (see BagTopDownView); the px defaults below only serve unit tests.
    leftPadPx: Float = TOP_DOWN_LEFT_PAD_DP.toFloat(),
    rightPadPx: Float = TOP_DOWN_RIGHT_PAD_DP.toFloat(),
): BagTopDownFit? {
    val points = clubs.flatMap { it.points }
    if (points.isEmpty()) return null
    val minDistance = points.minOf { it.second }
    val maxDistance = points.maxOf { it.second }
    val maxAbsSide = points.maxOf { abs(it.first) }

    var windowMinM = (floor(minDistance / TOP_DOWN_SNAP_M) * TOP_DOWN_SNAP_M).coerceAtLeast(0.0)
    var windowMaxM = (ceil(maxDistance / TOP_DOWN_SNAP_M) * TOP_DOWN_SNAP_M)
        .coerceAtMost(RangeScene.FAIRWAY_END_Y)
    if (windowMaxM - windowMinM < TOP_DOWN_MIN_WINDOW_M) {
        val centre = (windowMinM + windowMaxM) / 2.0
        windowMinM = (centre - TOP_DOWN_MIN_WINDOW_M / 2.0).coerceAtLeast(0.0)
        windowMaxM = (windowMinM + TOP_DOWN_MIN_WINDOW_M).coerceAtMost(RangeScene.FAIRWAY_END_Y)
        windowMinM = windowMaxM - TOP_DOWN_MIN_WINDOW_M
    }

    // TRUE SCALE with a containment guarantee (2026-10-16 review round 2).
    // The vertical scale equals the horizontal one (pxPerMSide = pxPerMM) —
    // an independent per-axis fit made rings read as tall, narrow ellipses
    // that contradicted the tick spacing. At parity the vertical reach is
    //     (heightPx / 2) / pxPerMM  =  span * heightPx / (2 * plotWidth)
    // metres, so on a short pane (panel open) a wide lateral spread overflows
    // the bottom. Fix: grow the span — pure zoom-out, parity untouched —
    // until reach >= maxAbsSide + margin, re-snapping outward to the grid
    // (and shifting the window's start down if the range end clamps it):
    //     span >= (maxAbsSide + margin) * 2 * plotWidth / heightPx
    val plotWidth = max(widthPx - leftPadPx - rightPadPx, 1f)
    // Same containment arithmetic as [minPaneHeightPx], solved for the span:
    // requiredSpan = minPaneHeightPx * FAIRWAY_END_Y / heightPx. The height
    // is floored at 1 px everywhere (H) so a zero-height pane can never
    // yield pxPerMSide = 0 (every dot collapsing onto y = 0).
    val safeHeight = max(heightPx, 1f)
    val requiredSpan = minPaneHeightPx(maxAbsSide, plotWidth) *
        RangeScene.FAIRWAY_END_Y / safeHeight
    var span = max(windowMaxM - windowMinM, 1.0)
    if (span < requiredSpan) {
        val grown = ceil(requiredSpan / TOP_DOWN_SNAP_M) * TOP_DOWN_SNAP_M
        windowMaxM = (windowMinM + grown).coerceAtMost(RangeScene.FAIRWAY_END_Y)
        windowMinM = (windowMaxM - grown).coerceAtLeast(0.0)
        span = max(windowMaxM - windowMinM, 1.0)
    }
    var pxPerMSide = (plotWidth / span)
    // FALLBACK — the ONE deviation from strict 1:1 (2026-10-16 round 3):
    // when the 330 m range cap has already limited the span, the true-scale
    // vertical reach (span * heightPx / (2 * plotWidth)) can still fall short
    // of the widest dot — i.e. exactly when the pane is shorter than
    //     (maxAbsSide + margin) * 2 * plotWidth / FAIRWAY_END_Y
    // (an extreme lateral spread on a short pane). Squash the vertical scale
    // to the largest value that still contains maxAbsSide + margin: the ring
    // shapes read slightly flat in that degenerate case, which beats both
    // clipping (hides data) and drawing outside the pane.
    val reachM = span * safeHeight / (2.0 * plotWidth)
    if (reachM < maxAbsSide + TOP_DOWN_LATERAL_MARGIN_M) {
        pxPerMSide = (safeHeight / 2f) / (maxAbsSide + TOP_DOWN_LATERAL_MARGIN_M)
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
