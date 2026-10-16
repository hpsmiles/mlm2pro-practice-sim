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

/**
 * Fits the SIDE chart onto the page's SHARED distance axis (2026-10-16, user
 * decision): the span IS the axis max — zero origin, ticks at the axis's step
 * — so the metre values on this chart's scale match the TOP-DOWN and the
 * distribution matrix exactly. The old private 1.06 headroom moved into
 * [BagDistanceAxis.HEADROOM], where it is applied once over all plotted
 * distances (carries AND totals, kept AND filtered).
 */
fun computeSideFit(
    widthPx: Double,
    heightPx: Double,
    sharedAxisMaxM: Double,
    maxApexM: Double,
    leftPadPx: Double = 44.0,
    rightPadPx: Double = 16.0,
    bottomPadPx: Double = 18.0,
): SideFit {
    val span = maxOf(sharedAxisMaxM, 1.0)
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
 * clubs' labels apart without rotating the text. [labelRow] is the alternating
 * primitive over a club's RANK in ascending landing order.
 */
fun labelRow(rank: Int): Int = rank.mod(2)

/**
 * Row per club, assigned by each club's rank in ASCENDING landing order —
 * computed here, never by the caller's iteration order: `traces` arrive in
 * persisted test order (WEDGE_FIRST short→long looks right by accident;
 * RANDOM is a shuffled permutation, under which two clubs landing a few
 * metres apart would share a row and collide — 2026-10-16 round 4, finding
 * B). A stable sort keeps equal distances in input order, so the assignment
 * is deterministic.
 */
fun labelRowsFor(distances: List<Double>): List<Int> {
    val rows = IntArray(distances.size)
    distances
        .withIndex()
        .sortedBy { it.value } // stable: equal distances keep input order
        .forEachIndexed { rank, (index, _) -> rows[index] = labelRow(rank) }
    return rows.toList()
}
