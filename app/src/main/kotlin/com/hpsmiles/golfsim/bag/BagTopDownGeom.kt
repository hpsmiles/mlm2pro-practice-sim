package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Distance-window snap, metres — mirrors CombineTopDownView. */
const val TOP_DOWN_SNAP_M = 25.0

/** Smallest distance window, metres — keeps pxPerMM sane on a thin session. */
const val TOP_DOWN_MIN_WINDOW_M = 50.0

/** Corridor visibility scale — the fitting top-down's constant. */
const val TOP_DOWN_CORRIDOR_VISIBILITY_SCALE = 1.15

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

    /** Screen y of a lateral offset (positive side drawn upward). */
    fun sy(sideM: Double): Float = midY - (sideM * pxPerMSide).toFloat()
}

/**
 * Fits the session's content: the distance window is cropped to the actual
 * points and snapped outward to [TOP_DOWN_SNAP_M] (floor
 * [TOP_DOWN_MIN_WINDOW_M]) so empty range either side of the action is trimmed
 * away; the lateral half-window fits the widest content. Null when there is
 * nothing to draw.
 */
fun computeBagTopDownFit(
    widthPx: Float,
    heightPx: Float,
    clubs: List<ClubDots>,
    leftPadPx: Float = 26f,
    rightPadPx: Float = 12f,
): BagTopDownFit? {
    val points = clubs.flatMap { it.points }
    if (points.isEmpty()) return null
    val minDistance = points.minOf { it.second }
    val maxDistance = points.maxOf { it.second }

    var windowMinM = (floor(minDistance / TOP_DOWN_SNAP_M) * TOP_DOWN_SNAP_M).coerceAtLeast(0.0)
    var windowMaxM = (ceil(maxDistance / TOP_DOWN_SNAP_M) * TOP_DOWN_SNAP_M)
        .coerceAtMost(RangeScene.FAIRWAY_END_Y)
    if (windowMaxM - windowMinM < TOP_DOWN_MIN_WINDOW_M) {
        val centre = (windowMinM + windowMaxM) / 2.0
        windowMinM = (centre - TOP_DOWN_MIN_WINDOW_M / 2.0).coerceAtLeast(0.0)
        windowMaxM = (windowMinM + TOP_DOWN_MIN_WINDOW_M).coerceAtMost(RangeScene.FAIRWAY_END_Y)
        windowMinM = windowMaxM - TOP_DOWN_MIN_WINDOW_M
    }

    val span = max(windowMaxM - windowMinM, 1.0)
    return BagTopDownFit(
        windowMinM = windowMinM,
        windowMaxM = windowMaxM,
        pxPerMM = (widthPx - leftPadPx - rightPadPx) / span.toFloat(),
        leftPadPx = leftPadPx,
        // TRUE SCALE (spec, 2026-10-16 review): one px per metre on BOTH
        // axes — the vertical scale is the horizontal one, never an
        // independent fit. The previous per-axis fit ((heightPx/2)/lateralHalf)
        // made rings read as tall, narrow ellipses that contradicted the
        // tick spacing (≈7.7 px/m across vs ≈14+ px/m up). Parity means a
        // very wide lateral spread can now overflow the pane's height on a
        // short window; that trade is accepted — an honest scale beats a
        // fitted one that misrepresents dispersion shape.
        pxPerMSide = ((widthPx - leftPadPx - rightPadPx) / span).toFloat(),
        midY = heightPx / 2f,
    )
}
