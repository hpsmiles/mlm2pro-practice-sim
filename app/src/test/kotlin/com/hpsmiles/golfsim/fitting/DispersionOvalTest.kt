package com.hpsmiles.golfsim.fitting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class DispersionOvalTest {

    @Test
    fun `axis-aligned cloud - principal axes aligned with x and y`() {
        // (±2, 0), (0, ±1): var_x = 2, var_y = 0.5, cov = 0
        val oval = DispersionOval.compute(listOf(2.0 to 0.0, -2.0 to 0.0, 0.0 to 1.0, 0.0 to -1.0))!!
        assertEquals(0.0, oval.cx, 1e-9)
        assertEquals(0.0, oval.cy, 1e-9)
        assertEquals(sqrt(2.0), oval.a, 1e-9)
        assertEquals(sqrt(0.5), oval.b, 1e-9)
        assertEquals(0.0, oval.angleRad, 1e-9)
    }

    @Test
    fun `rotated cloud - angle follows the principal axis`() {
        // The aligned cloud rotated by 45°: angle → π/4, same axes.
        val rotated = listOf(
            1.4142135623730951 to 1.4142135623730951,
            -1.4142135623730951 to -1.4142135623730951,
            -0.7071067811865476 to 0.7071067811865476,
            0.7071067811865476 to -0.7071067811865476,
        )
        val oval = DispersionOval.compute(rotated)!!
        assertEquals(sqrt(2.0), oval.a, 1e-9)
        assertEquals(sqrt(0.5), oval.b, 1e-9)
        assertEquals(PI / 4, oval.angleRad, 1e-9)
    }

    @Test
    fun `degenerate inputs - null`() {
        assertNull(DispersionOval.compute(listOf(1.0 to 1.0, 2.0 to 2.0)))          // < 3 points
        assertNull(DispersionOval.compute(listOf(1.0 to 1.0, 1.0 to 1.0, 1.0 to 1.0))) // zero variance
    }

    @Test
    fun `area - sigma squared scaling`() {
        // Unit-ish circle (a = b = 1) at 2σ → π·4·1·1 = 4π ≈ 12.566.
        val circle = DispersionOval.Oval(0.0, 0.0, 1.0, 1.0, 0.0)
        assertEquals(4.0 * PI, DispersionOval.area(circle, 2.0), 1e-9)
        // Ellipse a=2, b=1 at 2σ → π·4·2·1 = 8π; at 1σ → π·2·1 = 2π.
        val ellipse = DispersionOval.Oval(0.0, 0.0, 2.0, 1.0, 0.0)
        assertEquals(8.0 * PI, DispersionOval.area(ellipse, 2.0), 1e-9)
        assertEquals(2.0 * PI, DispersionOval.area(ellipse, 1.0), 1e-9)
    }

    @Test
    fun `polygon - sigma scaling and vertex placement`() {
        val pts = DispersionOval.polygon(DispersionOval.Oval(0.0, 0.0, 1.0, 1.0, 0.0), sigmaScale = 2.0, segments = 4)
        // t = 0, π/2, π, 3π/2 on a radius-2 circle.
        val expected = listOf(2.0 to 0.0, 0.0 to 2.0, -2.0 to 0.0, 0.0 to -2.0)
        for (i in expected.indices) {
            assertEquals(expected[i].first, pts[i].first, 1e-9)
            assertEquals(expected[i].second, pts[i].second, 1e-9)
        }
    }
}
