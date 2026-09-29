package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceTrajectoryTest {

    @Test
    fun `converges for every spec distance bracket`() {
        for (target in listOf(50.0, 100.0, 140.0, 200.0)) {
            val shot = ReferenceTrajectory.stockShot(target)
            assertEquals("target $target", target, shot.carryM, 1.0)
        }
    }

    @Test
    fun `deterministic`() {
        val a = ReferenceTrajectory.stockShot(140.0)
        val b = ReferenceTrajectory.stockShot(140.0)
        assertEquals(a.carryM, b.carryM, 0.0)
        assertEquals(a.samples, b.samples)
    }

    @Test
    fun `pane crossing height inside reachable band for 140m`() {
        // Spec section 2: 140 m target -> crossing band ~10.3-15.0 m, mid row ~= 12.6 m.
        val z = ReferenceTrajectory.paneCrossingHeightM(140.0)
        assertTrue("zRef $z outside [10, 16]", z in 10.0..16.0)
    }
}
