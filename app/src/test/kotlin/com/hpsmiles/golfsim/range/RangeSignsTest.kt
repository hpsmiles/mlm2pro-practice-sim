package com.hpsmiles.golfsim.range

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeSignsTest {

    @Test
    fun `seven signs per 50m - alternating sides starting right`() {
        val plan = RangeSigns.signPlan()
        assertEquals(listOf(50, 100, 150, 200, 250, 300, 350), plan.map { it.distanceM })
        assertEquals(listOf(1, -1, 1, -1, 1, -1, 1), plan.map { it.side })
    }

    @Test
    fun `signs sit just outside the fairway edge and clear of the greens`() {
        RangeSigns.signPlan().forEach { s ->
            val expected = RangeScene.fairwayHalfWidth(s.distanceM.toDouble()) + RangeSigns.EDGE_OFFSET_M
            assertEquals(expected, kotlin.math.abs(s.xM), 1e-9)
            // The greens sit at ±12 m with fringe radius 9.5 (max extent 21.5).
            assertTrue("sign ${s.distanceM} overlaps a green", kotlin.math.abs(s.xM) > 21.5)
        }
    }
}
