package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightSolverTest {

    private fun driverShot() = Triple(
        Vec3(0.0, 65.0, 13.0),                       // velocity: down-range 65, up 13
        Vec3(2545.0 * 2.0 * Math.PI / 60.0, 0.0, 0.0), // backspin around +x
        11.3,                                        // launch angle for layer scaling
    )

    @Test
    fun repeatSolvesAreBitIdentical() {
        val (v0, s0, vla) = driverShot()
        val a = FlightSolver.solve(v0, s0, vla, Environment())
        val b = FlightSolver.solve(v0, s0, vla, Environment())
        assertEquals(a, b)
    }

    @Test
    fun landsExactlyAtGroundZero() {
        val (v0, s0, vla) = driverShot()
        val landing = FlightSolver.solve(v0, s0, vla, Environment())
        assertEquals(0.0, landing.position.z, 1e-9)
    }

    @Test
    fun headwindShortensCarry() {
        val (v0, s0, vla) = driverShot()
        val still = FlightSolver.solve(v0, s0, vla, Environment())
        val headwind = FlightSolver.solve(v0, s0, vla, Environment(windYmps = -5.0))
        assertTrue(headwind.position.y < still.position.y)
    }

    @Test
    fun backspinCarriesFartherThanNoSpin() {
        val (v0, _, vla) = driverShot()
        val spun = FlightSolver.solve(v0, Vec3(266.0, 0.0, 0.0), vla, Environment())
        val plain = FlightSolver.solve(v0, Vec3(0.0, 0.0, 0.0), vla, Environment())
        assertTrue(spun.position.y > plain.position.y)
        assertTrue(spun.apexM > plain.apexM)
    }

    @Test
    fun apexAndFlightTimeArePositive() {
        val (v0, s0, vla) = driverShot()
        val landing = FlightSolver.solve(v0, s0, vla, Environment())
        assertTrue(landing.apexM > 10.0)
        assertTrue(landing.flightTimeSec > 3.0)
    }
}
