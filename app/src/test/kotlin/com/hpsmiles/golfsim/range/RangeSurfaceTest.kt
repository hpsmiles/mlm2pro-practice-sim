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
}
