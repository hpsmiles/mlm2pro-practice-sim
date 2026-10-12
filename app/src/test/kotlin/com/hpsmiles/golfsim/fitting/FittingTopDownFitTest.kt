package com.hpsmiles.golfsim.fitting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ground-truth tests for the M7 horizontal top-down fit (HISTORY-nav /
 * fitting-results rework, Task 3): the tee sits at the LEFT and distance grows
 * to the right, with an independent (non-isotropic) lateral scale. All values
 * hand-computed for W = 1000, H = 400.
 */
class FittingTopDownFitTest {

    private companion object {
        const val W = 1000.0
        const val H = 400.0
    }

    @Test
    fun noDataFallbackCoversFairway() {
        val fit = computeTopDownFitH(W, H, 0.0, 0.0, 0.0, hasData = false)
        val px = (W - 26.0 - 12.0) / (330.0 + 2 * 19.8)   // span 330, pad = 0.06 * 330
        assertEquals(px, fit.pxPerMX, 1e-9)
        assertEquals(26.0 + 19.8 * px, fit.originX, 1e-9)  // world 0 sits one pad in from the left
        assertEquals((H / 2 - 12.0) / 35.0, fit.pxPerMY, 1e-9)
        assertEquals(H / 2, fit.midY, 1e-9)
    }

    @Test
    fun distanceWindowExpandsAndPads() {
        val fit = computeTopDownFitH(W, H, 150.0, 170.0, 5.0, hasData = true)
        val px = (W - 38.0) / 50.0                          // span 20 → 30, centre 160, lo 145, pad 10
        assertEquals(px, fit.pxPerMX, 1e-9)
        assertEquals(26.0 - 135.0 * px, fit.originX, 1e-9)
        assertEquals(35.0, (H / 2 - 12.0) / fit.pxPerMY, 1e-9)  // corridor floor (14 m × 2.5)
    }

    @Test
    fun lateralExpandsForWideDispersion() {
        val fit = computeTopDownFitH(W, H, 100.0, 100.0, 40.0, hasData = true)
        assertEquals(42.0, (H / 2 - 12.0) / fit.pxPerMY, 1e-9)  // 40 + 2 m content margin
    }

    @Test
    fun windowEdgesMapToPads() {
        val fit = computeTopDownFitH(W, H, 150.0, 170.0, 5.0, hasData = true)
        assertEquals(26.0, fit.originX + 135.0 * fit.pxPerMX, 1e-9)       // lo − pad at the left pad
        assertEquals(W - 12.0, fit.originX + 185.0 * fit.pxPerMX, 1e-9)   // hi + pad at the right pad
    }
}

/**
 * Ground-truth tests for the rotated minimum-enclosing ellipse (MVEE) of the
 * KEPT shots (device ruling 2026-10-08): Khachiyan's algorithm on the lifted
 * points, both semi-axes scaled ×1.05 about the centre. The ellipse's area is
 * what the AREA column shows and its outline is what the top-down ring draws.
 * Expected values hand-computed (diamond → circle r 2; 4×2 rectangle →
 * semi-axes (2√2, √2); right triangle → Steiner circumellipse).
 */
class DispersionEllipseTest {

    /** Ellipse-equation residual of a point in the ellipse's rotated frame. */
    private fun residual(ell: DispersionEllipse, x: Double, y: Double): Double {
        val dx = x - ell.centreSideM
        val dy = y - ell.centreTotalM
        val c = cos(ell.angleRad)
        val s = sin(ell.angleRad)
        val u = dx * c + dy * s
        val v = -dx * s + dy * c
        val ru = u / ell.semiAxisM
        val rv = v / ell.semiCrossM
        return ru * ru + rv * rv
    }

    /** Containment: every point satisfies (u/a)² + (v/b)² ≤ 1 + tol in the ellipse's rotated frame (device ruling 2026-10-08: the ring must actually contain the dots). */
    private fun assertContainsAll(ell: DispersionEllipse, points: List<Pair<Double, Double>>, tol: Double = 1e-9) {
        points.forEach { (x, y) ->
            assertTrue(
                "point ($x, $y) outside ellipse (residual ${residual(ell, x, y)})",
                residual(ell, x, y) <= 1.0 + tol,
            )
        }
    }

    /** Angle normalized into [0, π) — an ellipse is unchanged by 180° rotation. */
    private fun normAngle(angle: Double): Double = ((angle % PI) + PI) % PI

    @Test
    fun `fromKept - null below 3 points`() {
        assertNull(DispersionEllipse.fromKept(emptyList()))
        assertNull(DispersionEllipse.fromKept(listOf(1.0 to 1.0)))
        assertNull(DispersionEllipse.fromKept(listOf(1.0 to 1.0, 2.0 to 2.0)))
    }

    @Test
    fun `fromKept - collinear points null`() {
        assertNull(DispersionEllipse.fromKept(listOf(0.0 to 0.0, 5.0 to 5.0, 10.0 to 10.0, -5.0 to -5.0)))
        assertNull(DispersionEllipse.fromKept(listOf(0.0 to 0.0, 0.0 to 0.0, 0.0 to 0.0))) // all identical
    }

    @Test
    fun `axis-aligned rectangle - known ellipse`() {
        // 4×2 rectangle centred at 0: MVEE semi-axes (2√2, √2) along x/y.
        val ell = DispersionEllipse.fromKept(listOf(2.0 to 1.0, 2.0 to -1.0, -2.0 to 1.0, -2.0 to -1.0))!!
        assertEquals(0.0, ell.centreSideM, 1e-9)
        assertEquals(0.0, ell.centreTotalM, 1e-9)
        val aRaw = 2.0 * sqrt(2.0) // 2.828…
        val bRaw = sqrt(2.0) // 1.414…
        assertEquals(aRaw * 1.05, ell.semiAxisM, 1e-6)
        assertEquals(bRaw * 1.05, ell.semiCrossM, 1e-6)
        assertEquals(0.0, normAngle(ell.angleRad), 1e-4) // long axis along x
        // Axis-aligned → projected extents equal the semi-axes.
        assertEquals(ell.semiAxisM, ell.projectedHalfSideM(), 1e-9)
        assertEquals(ell.semiCrossM, ell.projectedHalfTotalM(), 1e-9)
    }

    @Test
    fun `tilted rectangle - 45 degree ellipse contains the corner extremes`() {
        // Rotated (±2, ±1) by 45°: the corner points are DIAGONAL extremes the
        // axis-aligned inscribed ellipse could never contain — the regression
        // case the device flagged. The rotated MVEE contains them all.
        val c = sqrt(2.0) / 2.0
        fun rot(u: Double, v: Double): Pair<Double, Double> = (u * c - v * c) to (u * c + v * c)
        val pts = listOf(rot(2.0, 1.0), rot(2.0, -1.0), rot(-2.0, 1.0), rot(-2.0, -1.0))
        val ell = DispersionEllipse.fromKept(pts)!!
        assertEquals(0.0, ell.centreSideM, 1e-6)
        assertEquals(0.0, ell.centreTotalM, 1e-6)
        assertEquals(2.0 * sqrt(2.0) * 1.05, ell.semiAxisM, 1e-6)
        assertEquals(sqrt(2.0) * 1.05, ell.semiCrossM, 1e-6)
        assertEquals(PI / 4, normAngle(ell.angleRad), 1e-4) // long axis at 45° (mod π)
        assertContainsAll(ell, pts)
    }

    @Test
    fun `right triangle - steiner circumellipse`() {
        val ell = DispersionEllipse.fromKept(listOf(0.0 to 0.0, 10.0 to 0.0, 0.0 to 10.0))!!
        assertEquals(10.0 / 3.0, ell.centreSideM, 1e-6)
        assertEquals(10.0 / 3.0, ell.centreTotalM, 1e-6)
        assertEquals(1.0 / sqrt(0.015) * 1.05, ell.semiAxisM, 1e-6) // long axis ≈ 8.57
        assertEquals(1.0 / sqrt(0.045) * 1.05, ell.semiCrossM, 1e-6) // short axis ≈ 4.95
        // Long axis along the hypotenuse (45° to the axes): |sin(2θ)| = 1.
        assertEquals(1.0, abs(sin(2.0 * ell.angleRad)), 1e-4)
        assertContainsAll(ell, listOf(0.0 to 0.0, 10.0 to 0.0, 0.0 to 10.0))
    }

    @Test
    fun `containment - every point inside for mixed clouds`() {
        val sets = listOf(
            listOf(2.0 to 1.0, 2.0 to -1.0, -2.0 to 1.0, -2.0 to -1.0), // rectangle
            listOf(0.0 to 0.0, 10.0 to 0.0, 0.0 to 10.0), // triangle
            listOf(2.0 to 0.0, 0.0 to 2.0, -2.0 to 0.0, 0.0 to -2.0), // diamond → circle
            listOf(0.0 to 0.0, 8.0 to 0.0, 4.0 to 2.0, 2.0 to 5.0, 6.0 to -3.0), // asymmetric
        )
        sets.forEach { pts ->
            val ell = DispersionEllipse.fromKept(pts) ?: error("expected ellipse for $pts")
            assertContainsAll(ell, pts)
        }
    }

    @Test
    fun `buffer - area scales by 1 point 05 squared`() {
        // Raw MVEE of the 4×2 rectangle has area π·(2√2)·(√2) = 4π; both
        // semi-axes ×1.05 about the centre → area (1.05)²·4π.
        val ell = DispersionEllipse.fromKept(listOf(2.0 to 1.0, 2.0 to -1.0, -2.0 to 1.0, -2.0 to -1.0))!!
        assertEquals(1.05 * 1.05 * 4.0 * PI, ell.areaM2(), 1e-6)
    }
}
