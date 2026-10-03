package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Rest position is the bounce/roll end position — the scored position in both games. */
class RestPositionTest {

    private fun stock8i(): LaunchConditions = LaunchConditions(44.0, 21.0, 7500)

    @Test
    fun `rest position consistent with sideM and totalM`() {
        val result = BallFlightEngine.simulate(stock8i())
        assertEquals(result.sideM, result.restX, 1e-9)
        assertEquals(result.totalM, hypot(result.restX, result.restY), 1e-9)
        assertEquals(result.carryM + result.rolloutM, result.totalM, 1e-9)
    }

    @Test
    fun `rest stays within carry and total on fairway`() {
        val result = BallFlightEngine.simulate(stock8i())
        val restFromTee = hypot(result.restX, result.restY)
        assertTrue("rest $restFromTee must be >= carry ${result.carryM}", restFromTee >= result.carryM - 1e-9)
        assertTrue("rest $restFromTee must be <= total ${result.totalM}", restFromTee <= result.totalM + 1e-9)
    }

    @Test
    fun `engine rest matches spec 140m table - stock 8i lands about 20m short of pin`() {
        // Spec §2 calc table: 44 m/s / 21° / 7500 rpm → carry 118.1, rest ~19.5 m from a 140 m pin.
        val surfaces = GreenZoneSurfaceProvider(centerX = 0.0, centerY = 140.0, radiusM = 6.0)
        val result = BallFlightEngine.simulate(stock8i(), Environment(), surfaces)
        assertEquals(118.1, result.carryM, 0.5)
        val restFromPin = hypot(result.restX, result.restY - 140.0)
        assertEquals(19.5, restFromPin, 1.0)
    }
}
