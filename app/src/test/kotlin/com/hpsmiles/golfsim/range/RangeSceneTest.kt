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
        assertFalse(RangeScene.isOnFairway(0.0, 340.0))
        assertFalse(RangeScene.isOnFairway(0.0, 5.0))
        // Width ~40 m at tee, widening slightly toward the fairway end.
        assertTrue(abs(RangeScene.fairwayHalfWidth(10.0) - 20.0) < 0.5)
        assertTrue(RangeScene.fairwayHalfWidth(330.0) > 20.0)
    }

    @Test
    fun `green oval membership geometry`() {
        val g = RangeScene.Green(0.0, 100.0, 7.0, 9.5)
        assertTrue(g.isSurface(0.0, 100.0))
        assertFalse(g.isSurface(0.0, 110.0))
        assertTrue(g.isFringe(0.0, 108.0))
        assertFalse(g.isFringe(0.0, 105.0))
        assertFalse(g.isSurface(0.0, 60.0))
    }

    @Test
    fun `stripe parity alternates every 10 m`() {
        assertEquals(RangeScene.stripeIsLight(0.0, 10.0), !RangeScene.stripeIsLight(0.0, 0.0))
        assertEquals(RangeScene.stripeIsLight(0.0, 20.0), RangeScene.stripeIsLight(0.0, 0.0))
    }
}
