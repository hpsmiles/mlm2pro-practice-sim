package com.hpsmiles.golfsim.range

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeDecorationsTest {

    @Test
    fun `stripe band count covers tee to fairway end`() {
        assertTrue(
            RangeScene.stripeBandCount() >=
                ((RangeScene.FAIRWAY_END_Y - RangeScene.FAIRWAY_TEE_Y) / RangeScene.STRIPE_WIDTH_M).toInt(),
        )
    }

    @Test
    fun `fairway nearest depth is finite and non-negative at static rig`() {
        val nearest = RangeDecorations.fairwayNearestDepth(RangeCamera.STATIC)
        assertTrue(nearest >= 0.0)
        assertTrue(nearest.isFinite())
    }

    @Test
    fun `stripe nearest depth reads front edge`() {
        val d = RangeDecorations.stripeNearestDepth(RangeCamera.STATIC, 20.0, 30.0)
        assertTrue(d >= 0.0)
        assertTrue(d.isFinite())
    }

    @Test
    fun `stripe light parity matches range scene`() {
        assertEquals(RangeScene.stripeIsLight(0.0, 10.0), !RangeScene.stripeIsLight(0.0, 0.0))
        assertEquals(RangeScene.stripeIsLight(0.0, 20.0), RangeScene.stripeIsLight(0.0, 0.0))
    }
}
