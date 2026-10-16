package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BagSideGeomTest {

    private val fit = computeSideFit(
        widthPx = 1000.0,
        heightPx = 500.0,
        maxDistanceM = 237.0,
        maxApexM = 30.0,
    )

    @Test
    fun `origin sits at the left pad and the max distance near the right edge`() {
        assertEquals(44.0f, fit.x(0.0), 1e-3f)
        // 6 % headroom on the span, so the longest club stops short of the edge.
        assertTrue(fit.x(237.0) < 1000f - 16f)
        assertTrue(fit.x(237.0) > 1000f - 100f)
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
        val flat = computeSideFit(1000.0, 500.0, maxDistanceM = 0.0, maxApexM = 0.0)
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
}
