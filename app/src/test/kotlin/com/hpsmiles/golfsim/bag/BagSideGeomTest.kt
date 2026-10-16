package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BagSideGeomTest {

    private val fit = computeSideFit(
        widthPx = 1000.0,
        heightPx = 500.0,
        sharedAxisMaxM = 237.0,
        maxApexM = 30.0,
    )

    @Test
    fun `origin sits at the left pad and the max distance lands on the right pad edge`() {
        // The span IS the shared axis max (2026-10-16 shared-axis decision) —
        // no per-view headroom anymore — so the largest plotted distance
        // reaches exactly the right pad edge; the 1.06 headroom lives in
        // BagDistanceAxis, applied over ALL plotted distances.
        assertEquals(44.0f, fit.x(0.0), 1e-3f)
        assertEquals(1000f - 16f, fit.x(237.0), 1e-3f)
    }

    @Test
    fun `x is linear and monotonic`() {
        assertEquals((fit.x(0.0) + fit.x(200.0)) / 2f, fit.x(100.0), 1e-3f)
        assertTrue(fit.x(100.0) < fit.x(200.0))
    }

    @Test
    fun `baseline sits above the bottom pad and height maps upward`() {
        assertEquals(482.0f, fit.baselineY.toFloat(), 1e-3f)
        assertEquals(482.0f, fit.y(0.0), 1e-3f)
        assertTrue(fit.y(30.0) < fit.y(0.0))
    }

    @Test
    fun `the tallest apex fills the height fraction of the plot`() {
        val usable = 500.0 - 18.0
        assertEquals(SIDE_HEIGHT_FRACTION.toFloat(), (fit.y(0.0) - fit.y(30.0)) / usable.toFloat(), 1e-4f)
    }

    @Test
    fun `a flat bag does not divide by zero`() {
        val flat = computeSideFit(1000.0, 500.0, sharedAxisMaxM = 0.0, maxApexM = 0.0)
        assertTrue(flat.pxPerMM.isFinite())
        assertTrue(flat.pxPerMHeight.isFinite())
        assertTrue(flat.pxPerMHeight > 0.0)
    }

    @Test
    fun `label rows alternate`() {
        assertEquals(0, labelRow(0))
        assertEquals(1, labelRow(1))
        assertEquals(0, labelRow(2))
        assertEquals(1, labelRow(13))
    }

    @Test
    fun `label rows follow ascending landing order regardless of input order, stable for ties`() {
        // 2026-10-16 round 4 (finding B): the rows are assigned INSIDE
        // labelRowsFor by rank in ascending distance — the caller's order
        // (persisted test order, possibly RANDOM) must not matter, and equal
        // distances keep input order (stable sort) so the assignment is
        // deterministic. Unsorted input with a duplicate:
        val rows = labelRowsFor(listOf(150.0, 120.0, 150.0, 90.0))
        // Ascending, stable: 90 (idx 3), 120 (idx 1), 150 (idx 0), 150 (idx 2)
        // get ranks 0..3 → rows 0,1,0,1 by rank, written back by index.
        assertEquals(listOf(0, 1, 1, 0), rows)
        // The invariant is over ASCENDING landing order (that is what keeps
        // neighbours apart), so re-sort and check the alternation there.
        val ascendingRows = listOf(90.0, 120.0, 150.0, 150.0).let { labelRowsFor(it) }
        assertEquals(listOf(0, 1, 0, 1), ascendingRows)
    }
}
