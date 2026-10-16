package com.hpsmiles.golfsim.bag

/**
 * The side view's axis fit: distance metres → screen x, height metres → screen
 * y. Pure geometry over doubles, in the spirit of [BoxPlotGeom] — the canvas
 * and the label layout both derive from one fit, so labels can never drift off
 * their landing points.
 */
data class SideFit(
    /** The right-hand end of the axis, metres (the longest club plus headroom). */
    val maxDistanceM: Double,
    val pxPerMM: Double,
    /** Screen x of distance 0 (the tee). */
    val originX: Double,
    /** Screen y of height 0 (the ground line). */
    val baselineY: Double,
    val pxPerMHeight: Double,
) {
    fun x(distanceM: Double): Float = (originX + distanceM * pxPerMM).toFloat()
    fun y(heightM: Double): Float = (baselineY - heightM * pxPerMHeight).toFloat()
}

/**
 * Fraction of the plot height the tallest apex is allowed to fill. Real
 * trajectories on a 260 m-wide axis would otherwise be flat lines. The shared
 * scale is applied over the traces exactly as [BagTrace.trace] delivered them —
 * each already uniformly scaled to its own authoritative distance — so it
 * preserves the relative heights those scaled traces carry, not the raw
 * simulation's. The y-axis is deliberately unlabelled ("height, not to scale").
 */
const val SIDE_HEIGHT_FRACTION = 0.85

/** Distance headroom past the longest club, so its marker is not on the edge. */
private const val SIDE_DISTANCE_HEADROOM = 1.06

fun computeSideFit(
    widthPx: Double,
    heightPx: Double,
    maxDistanceM: Double,
    maxApexM: Double,
    leftPadPx: Double = 44.0,
    rightPadPx: Double = 16.0,
    bottomPadPx: Double = 18.0,
): SideFit {
    val span = maxOf(maxDistanceM * SIDE_DISTANCE_HEADROOM, 1.0)
    val plotWidth = maxOf(widthPx - leftPadPx - rightPadPx, 1.0)
    val usableHeight = maxOf(heightPx - bottomPadPx, 1.0)
    val apex = maxOf(maxApexM, 1e-6)
    return SideFit(
        maxDistanceM = span,
        pxPerMM = plotWidth / span,
        originX = leftPadPx,
        baselineY = usableHeight,
        pxPerMHeight = SIDE_HEIGHT_FRACTION * usableHeight / apex,
    )
}

/**
 * Two-row stagger for the landing labels: alternating rows keep neighbouring
 * clubs' labels apart without rotating the text. The [index] is the club's
 * position in ascending landing order (bag order is descending distance, so the
 * caller sorts ascending by landing before indexing). Not a collision solver —
 * the accepted limitation is that two clubs ~48 px apart on the same row can
 * begin to touch with unusually long names.
 */
fun labelRow(index: Int): Int = index.mod(2)
