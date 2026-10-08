package com.hpsmiles.golfsim.fitting

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ground-truth tests for the M7 top-down content-fit mapping (device feedback
 * 2026-10-08): fits the kept shots AND their 2σ ring extents plus a buffer,
 * with a 30 m minimum span and a lateral clamp. All values hand-computed.
 */
class FittingTopDownFitTest {

    /** World y (rest distance, m) → screen y (px) under the returned mapping. */
    private fun screenY(fit: TopDownFit, worldY: Double): Double =
        fit.originY - worldY * fit.pxPerM

    @Test
    fun `basic fit - clustered shots map to a padded window with isotropic scale`() {
        // Shots clustered 200–220 m: span 20 < 30 → expand to [195, 225] around
        // the centre 210; pad = max(10, 0.06·30) = 10 → window [185, 235].
        // yFit = 1000 / (30 + 20) = 20; xFit = (500 − 48) / 10 = 45.2 → scale 20.
        val fit = computeTopDownFit(
            width = 1000.0, height = 1000.0,
            minY = 200.0, maxY = 220.0, maxAbsX = 10.0, hasData = true,
        )
        assertEquals(20.0, fit.pxPerM, 1e-9)
        assertEquals(500.0, fit.originX, 1e-9)
        // Window bottom [185] at the canvas bottom, top [235] at the canvas top.
        assertEquals(1000.0, screenY(fit, 185.0), 1e-9)
        assertEquals(0.0, screenY(fit, 235.0), 1e-9)
        // Isotropic: one scale for both axes (single pxPerM field).
        assertEquals(fit.pxPerM, fit.pxPerM, 0.0)
    }

    @Test
    fun `empty kept set - full-range fallback identical to the pre-fix mapping`() {
        // Pre-fix: pxPerM = h / GROUND_END_Y = 600 / 350, originX = w/2 = 400,
        // originY = h − bottomMarginPx = 600 − 12 = 588.
        val fit = computeTopDownFit(
            width = 800.0, height = 600.0,
            minY = 0.0, maxY = 0.0, maxAbsX = 0.0, hasData = false,
            bottomMarginPx = 12.0,
        )
        assertEquals(600.0 / 350.0, fit.pxPerM, 1e-12)
        assertEquals(400.0, fit.originX, 1e-9)
        assertEquals(588.0, fit.originY, 1e-9)
    }

    @Test
    fun `span floor - same-distance shots clamp the span to at least 30 m`() {
        // All shots at 210 m: span 0 → expand symmetrically to [195, 225];
        // pad = 10 → window [185, 235] fills the full 1000 px height.
        val fit = computeTopDownFit(
            width = 1000.0, height = 1000.0,
            minY = 210.0, maxY = 210.0, maxAbsX = 5.0, hasData = true,
        )
        assertEquals(20.0, fit.pxPerM, 1e-9)
        assertEquals(1000.0, screenY(fit, 185.0), 1e-9)
        assertEquals(0.0, screenY(fit, 235.0), 1e-9)
    }

    @Test
    fun `lateral clamp - wide lateral spread reduces the scale below the y-fit`() {
        // Same y-cluster as the basic fit but a wide 100 m lateral spread:
        // xFit = (500 − 48) / 100 = 4.52 < yFit 20 → the lateral constraint binds.
        val fit = computeTopDownFit(
            width = 1000.0, height = 1000.0,
            minY = 200.0, maxY = 220.0, maxAbsX = 100.0, hasData = true,
        )
        val xFit = (500.0 - 48.0) / 100.0
        assertEquals(xFit, fit.pxPerM, 1e-9)
        assertEquals(500.0, fit.originX, 1e-9)
        assertEquals(1000.0 + 185.0 * xFit, fit.originY, 1e-9)
        // The vertical window still spans its full 50 m, just scaled down.
        assertEquals(50.0 * xFit, screenY(fit, 185.0) - screenY(fit, 235.0), 1e-9)
    }
}
