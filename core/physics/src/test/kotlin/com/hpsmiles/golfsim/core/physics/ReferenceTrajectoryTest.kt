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
        assertTrue("zRef $z outside [9.6, 10.6]", z in 9.6..10.6)
    }

    @Test
    fun `recalibrated mid bracket anchors the pane middle at iron distances`() {
        // 2026-09-30 recalibration: mid/iron bracket 20 deg / 6500 rpm.
        // Default pane fraction 0.20: refCross(125) ~= 8.79 m, refCross(140) ~= 10.12 m (calibration sweep).
        val z125 = ReferenceTrajectory.paneCrossingHeightM(125.0)
        val z140 = ReferenceTrajectory.paneCrossingHeightM(140.0)
        assertTrue("zRef(125)=$z125", z125 in 8.3..9.3)
        assertTrue("zRef(140)=$z140", z140 in 9.6..10.6)
    }
}
