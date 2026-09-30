package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceTrajectoryTest {

    @Test
    fun `converges for every spec distance bracket`() {
        for (target in listOf(50.0, 100.0, 140.0, 200.0, 300.0)) {
            val shot = ReferenceTrajectory.stockShot(target)
            assertEquals("target $target", target, shot.carryM, 1.0)
        }
    }

    @Test
    fun `deterministic`() {
        val a = ReferenceTrajectory.stockShot(140.0)
        val b = ReferenceTrajectory.stockShot(140.0)
        assertEquals(a, b)
        assertEquals(a.carryM, b.carryM, 0.0)
        assertEquals(a.samples, b.samples)
    }

    @Test
    fun `pane crossing height inside reachable band for 140m`() {
        val z = ReferenceTrajectory.paneCrossingHeightM(140.0)
        assertTrue("zRef $z outside [12, 13]", z in 12.0..13.0)
    }

    @Test
    fun `recalibrated mid bracket anchors the pane middle at iron distances`() {
        // 2026-09-30 recalibration: mid/iron bracket 20 deg / 6500 rpm.
        // refCross(125) ~= 10.78 m, refCross(140) ~= 12.50 m (calibration sweep).
        val z125 = ReferenceTrajectory.paneCrossingHeightM(125.0)
        val z140 = ReferenceTrajectory.paneCrossingHeightM(140.0)
        assertTrue("zRef(125)=$z125", z125 in 10.3..11.3)
        assertTrue("zRef(140)=$z140", z140 in 12.0..13.0)
    }
}
