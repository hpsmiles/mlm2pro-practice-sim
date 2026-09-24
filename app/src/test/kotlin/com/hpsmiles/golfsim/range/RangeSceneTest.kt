package com.hpsmiles.golfsim.range

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeSceneTest {

    @Test
    fun `groundHeight is flat zero this phase`() {
        assertEquals(0.0, RangeScene.groundHeight(5.0, 100.0), 0.0)
        assertEquals(0.0, RangeScene.groundHeight(-30.0, 12.0), 0.0)
    }

    @Test
    fun `fairway bounds taper from tee to end`() {
        assertTrue(RangeScene.isOnFairway(0.0, 100.0))
        assertFalse(RangeScene.isOnFairway(30.0, 100.0))
        assertFalse(RangeScene.isOnFairway(0.0, 190.0))
        assertFalse(RangeScene.isOnFairway(0.0, 5.0))
        // Width ~40 m at tee, widening slightly toward y = 180.
        assertTrue(abs(RangeScene.fairwayHalfWidth(10.0) - 20.0) < 0.5)
        assertTrue(RangeScene.fairwayHalfWidth(180.0) > 20.0)
    }

    @Test
    fun `greens and fringes at target positions`() {
        assertEquals(3, RangeScene.greens.size)
        val g = RangeScene.greens.first { abs(it.lateralM) < 1e-9 }
        assertEquals(100.0, g.distanceM, 1e-9)
        assertEquals(7.0, g.radiusM, 1e-9)
        assertEquals(9.5, g.fringeRadiusM, 1e-9)
        assertTrue(g.isFringe(g.lateralM + 8.0, g.distanceM))   // 8 m off-centre: fringe
        assertFalse(g.isFringe(g.lateralM + 5.0, g.distanceM))  // inside surface
    }

    @Test
    fun `stripe parity alternates every 12 m`() {
        assertEquals(RangeScene.stripeIsLight(0.0, 12.0), !RangeScene.stripeIsLight(0.0, 0.0))
        assertEquals(RangeScene.stripeIsLight(0.0, 24.0), RangeScene.stripeIsLight(0.0, 0.0))
    }
}
