package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeSurfaceTest {

    @Test
    fun `range turf is firm fairway`() {
        assertEquals("fairway (firm)", RangeSession.RANGE_SURFACE.name)
        assertTrue(RangeSession.RANGE_SURFACE.rollDecelMps2 < Surface.FAIRWAY_NORMAL.rollDecelMps2)
        assertTrue(RangeSession.RANGE_SURFACE.cor > Surface.FAIRWAY_NORMAL.cor)
    }

    @Test
    fun `no custom green means firm fairway everywhere`() {
        val provider = surfaceProviderFor(null)
        assertEquals(RangeSession.RANGE_SURFACE, provider.surfaceAt(0.0, 120.0))
        assertEquals(RangeSession.RANGE_SURFACE, provider.surfaceAt(50.0, 300.0))
    }

    @Test
    fun `custom green is green inside the oval and firm fairway outside`() {
        val provider = surfaceProviderFor(RangeScene.Green(0.0, 120.0, 8.0, 10.8))
        assertEquals(Surface.GREEN_NORMAL, provider.surfaceAt(0.0, 120.0))
        assertEquals(Surface.GREEN_NORMAL, provider.surfaceAt(5.0, 123.0))
        assertEquals(RangeSession.RANGE_SURFACE, provider.surfaceAt(0.0, 140.0))
    }
}
