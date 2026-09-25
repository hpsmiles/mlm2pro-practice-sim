// app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeRolloutTest.kt
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.ShotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the synthetic post-landing ground roll (spec 2026-09-25): the ball
 * decelerates from the carry point to its rest position inside the existing
 * follow-cam hold window, so the tracer grows through the roll instead of
 * the ball jumping to its final spot.
 */
class RangeRolloutTest {

    private fun shot(
        carryM: Double = 180.0,
        rolloutM: Double = 5.0,
        sideM: Double = 3.0,
        flightTimeSec: Double = 6.0,
    ): ShotResult = ShotResult(
        carryM,
        rolloutM,
        carryM + rolloutM,
        sideM,
        28.0,
        flightTimeSec,
    )

    @Test
    fun zeroRolloutProducesNoSamples() {
        val s = shot(rolloutM = 0.0)
        assertEquals(0.0, RangeRollout.durationSec(s), 1e-9)
        assertTrue(RangeRollout.samples(s).isEmpty())
    }

    @Test
    fun shortRolloutsAreStretchedToTheMinimum() {
        val s = shot(rolloutM = 0.1)
        assertEquals(0.3, RangeRollout.durationSec(s), 1e-9)
        val roll = RangeRollout.samples(s)
        assertEquals(s.flightTimeSec + 0.3, roll.last().tSec, 1e-9)
    }

    @Test
    fun longRolloutsAreCapped() {
        val s = shot(rolloutM = 30.0)
        val roll = RangeRollout.samples(s)
        assertEquals(1.8, RangeRollout.durationSec(s), 1e-9)
        assertEquals(s.flightTimeSec + RangeRollout.durationSec(s), roll.last().tSec, 1e-9)
    }

    @Test
    fun samplesStartAtCarryAndEndAtTotal() {
        val s = shot(carryM = 180.0, rolloutM = 5.0)
        val roll = RangeRollout.samples(s)
        assertTrue(roll.first().py >= s.carryM)
        assertTrue(roll.first().py <= s.carryM + s.rolloutM * 0.15)
        assertEquals(s.totalM, roll.last().py, 1e-9)
        assertEquals(s.flightTimeSec + RangeRollout.durationSec(s), roll.last().tSec, 1e-9)
    }

    @Test
    fun rolloutIsMonotonicAndDecelerating() {
        val s = shot(rolloutM = 5.0)
        val roll = RangeRollout.samples(s)
        var prevDelta = Double.POSITIVE_INFINITY
        for (i in 1 until roll.size) {
            val delta = roll[i].py - roll[i - 1].py
            assertTrue("py must strictly increase", delta > 0.0)
            assertTrue("deltas must not increase", delta <= prevDelta + 1e-9)
            prevDelta = delta
        }
    }

    @Test
    fun samplesStayOnTheGroundAndLaterallyFixed() {
        val s = shot(sideM = 3.0)
        val roll = RangeRollout.samples(s)
        var prevT = Double.NEGATIVE_INFINITY
        for (sample in roll) {
            assertEquals(0.0, sample.pz, 1e-9)
            assertEquals(s.sideM, sample.px, 1e-9)
            assertTrue("tSec must strictly increase", sample.tSec > prevT)
            if (prevT != Double.NEGATIVE_INFINITY) {
                assertTrue("gaps must not exceed STEP", sample.tSec - prevT <= 0.05 + 1e-9)
            }
            prevT = sample.tSec
        }
    }
}
