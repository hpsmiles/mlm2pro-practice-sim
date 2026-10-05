package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaneIntersectionTest {

    private val planeY = 35.0

    private fun flight(crossX: Double, crossZ: Double): ShotResult = ShotResult(
        carryM = 140.0, rolloutM = 0.0, totalM = 140.0, sideM = 0.0, apexM = 25.0, flightTimeSec = 6.0,
        samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(crossX, planeY, crossZ, 3.0),
            TrajectorySample(0.0, 140.0, 0.0, 6.0),
        ),
        restX = 0.0, restY = 140.0,
    )

    @Test
    fun `mark reports the crossing point and a reveal fraction inside zero to one`() {
        // apexVMin below the shot's apex v disables apex scaling, so the drawn
        // samples stay raw and z is unscaled (FollowCam.scaledSamples returns
        // shot.samples unchanged when apexScale >= 1).
        val mark = PaneIntersection.mark(flight(0.5, 10.0), planeY, apexVMin = -1.0)
        assertNotNull(mark)
        assertEquals(0.5, mark!!.xM, 1e-9)
        assertEquals(10.0, mark.zM, 1e-9)
        // Crossing is the 2nd of 3 samples -> fraction ~= 0.5.
        assertEquals(0.5f, mark.revealFraction, 1e-4f)
    }

    @Test
    fun `mark reports the crossing time in flight seconds`() {
        val mark = PaneIntersection.mark(flight(0.5, 10.0), planeY, apexVMin = -1.0)
        assertNotNull(mark)
        // The crossing sample sits at tSec = 3.0 in the synthetic flight.
        assertEquals(3.0, mark!!.tSec, 1e-9)
    }

    @Test
    fun `no forward crossing reports null`() {
        // py never reaches the 35 m plane.
        val below = ShotResult(
            carryM = 30.0, rolloutM = 0.0, totalM = 30.0, sideM = 0.0, apexM = 4.0, flightTimeSec = 3.0,
            samples = listOf(
                TrajectorySample(0.0, 0.0, 0.0, 0.0),
                TrajectorySample(0.0, 20.0, 2.0, 1.5),
                TrajectorySample(0.0, 30.0, 0.0, 3.0),
            ),
            restX = 0.0, restY = 30.0,
        )
        assertNull(PaneIntersection.mark(below, planeY, apexVMin = -1.0))
    }
}
