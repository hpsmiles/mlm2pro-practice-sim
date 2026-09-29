package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightCrossingTest {

    @Test
    fun `interpolates first forward crossing`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.5, 30.0, 8.0, 1.0),
            TrajectorySample(0.8, 40.0, 14.0, 2.0),
        )
        val crossing = FlightCrossing.firstForwardCrossing(samples, planeY = 35.0)!!
        assertEquals(0.65, crossing.first, 1e-9)  // 0.5 + 0.5*(0.8-0.5)
        assertEquals(11.0, crossing.second, 1e-9) // 8 + 0.5*(14-8)
    }

    @Test
    fun `backward crossing ignored`() {
        // Ball starts beyond the plane and comes back through it - not forward.
        val samples = listOf(
            TrajectorySample(0.0, 40.0, 10.0, 0.0),
            TrajectorySample(0.0, 30.0, 5.0, 1.0),
        )
        assertNull(FlightCrossing.firstForwardCrossing(samples, planeY = 35.0))
    }

    @Test
    fun `no crossing returns null`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.0, 10.0, 2.0, 1.0),
        )
        assertNull(FlightCrossing.firstForwardCrossing(samples, planeY = 35.0))
    }
}
