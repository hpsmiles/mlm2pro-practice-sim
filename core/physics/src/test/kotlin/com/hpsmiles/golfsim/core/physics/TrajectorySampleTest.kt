package com.hpsmiles.golfsim.core.physics

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrajectorySampleTest {

    private fun launch() = LaunchConditions(
        ballSpeedMps = 70.0, launchAngleDeg = 13.0, launchDirDeg = 0.0, spinRpm = 3000, spinAxisDeg = 0.0,
    )

    @Test
    fun `samples start at launch and are monotonic in time`() {
        val s = FlightSolver.solve(Vec3(60.0, 10.0, 12.0), Vec3(300.0, 0.0, 0.0), 13.0, Environment())
        val samples = s.samples
        assertTrue(samples.isNotEmpty())
        assertEquals(0.0, samples[0].tSec, 1e-12)
        assertEquals(0.0, samples[0].px, 1e-12)
        assertEquals(0.0, samples[0].py, 1e-12)
        assertEquals(0.0, samples[0].pz, 1e-12)
        for (i in 1 until samples.size) {
            assertTrue(samples[i].tSec > samples[i - 1].tSec)
        }
    }

    @Test
    fun `sample count matches flight time at 10ms resolution`() {
        val s = FlightSolver.solve(Vec3(60.0, 10.0, 12.0), Vec3(300.0, 0.0, 0.0), 13.0, Environment())
        val dt = 0.01
        assertTrue(abs(s.samples.size - (s.flightTimeSec / dt + 1)) <= 3)
    }

    @Test
    fun `last sample is the interpolated landing point at z = 0`() {
        val s = FlightSolver.solve(Vec3(60.0, 10.0, 12.0), Vec3(300.0, 0.0, 0.0), 13.0, Environment())
        val last = s.samples.last()
        assertEquals(0.0, last.pz, 1e-9)
        assertEquals(s.flightTimeSec, last.tSec, 1e-12)
        assertEquals(s.position.x, last.px, 1e-9)
        assertEquals(s.position.y, last.py, 1e-9)
    }
}
