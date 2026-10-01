// app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeRolloutTest.kt
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.GroundHop
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

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

    /** Builds a hop-chain shot ending at (sideM, totalM) like the engine does. */
    private fun hopShot(
        carryM: Double,
        totalM: Double,
        vararg hops: GroundHop,
    ): ShotResult = ShotResult(
        carryM = carryM,
        rolloutM = totalM - carryM,
        totalM = totalM,
        sideM = 0.0,
        apexM = 20.0,
        flightTimeSec = 5.0,
        restX = 0.0,
        restY = totalM,
        groundHops = hops.toList(),
    )

    @Test
    fun hopChainRendersArcsAndEndsExactlyAtRest() {
        val s = hopShot(
            carryM = 100.0,
            totalM = 103.0,
            GroundHop(1.2, 1.2, 0.45, 0.55),
            GroundHop(2.1, 2.1, 0.22, 0.40),
        )
        val roll = RangeRollout.samples(s)
        assertTrue(roll.isNotEmpty())
        // Starts at the carry point, ends exactly at the modelled rest.
        assertTrue("first sample must start at carry, got ${roll.first().py}", roll.first().py >= s.carryM)
        assertEquals(s.totalM, roll.last().py, 1e-9)
        assertEquals(s.sideM, roll.last().px, 1e-9)
        assertEquals(0.0, roll.last().pz, 1e-9)
        assertEquals(s.flightTimeSec + RangeRollout.durationSec(s), roll.last().tSec, 1e-9)
        // Hops leave the ground and come back: airborne samples exist and the
        // apex never exceeds the largest recorded hop apex.
        val maxPz = roll.maxOf { it.pz }
        assertTrue("expected airborne samples, max pz $maxPz", maxPz > 0.0)
        assertTrue("pz $maxPz exceeds hop apex", maxPz <= 0.45 + 1e-9)
        for (sample in roll) {
            assertTrue("pz must stay >= 0, got ${sample.pz}", sample.pz >= 0.0)
            assertTrue("ground sample must postdate the flight", sample.tSec > s.flightTimeSec)
        }
    }

    @Test
    fun hopTimingIsMonotonicAndCapped() {
        // Hop air times (3.6 s total) plus the start-speed-capped roll play
        // un-compressed: chain ends at (3, 103), roll dRoll = hypot(3, 1),
        // raw total 3.6 + max(sqrt(2*3.1623/5), 3.1623/4) = 4.7247 s.
        val s = hopShot(
            carryM = 100.0,
            totalM = 104.0,
            GroundHop(1.0, 1.0, 0.5, 1.4),
            GroundHop(2.0, 2.0, 0.3, 1.2),
            GroundHop(3.0, 3.0, 0.15, 1.0),
        )
        assertEquals(4.7247, RangeRollout.durationSec(s), 1e-3)
        val roll = RangeRollout.samples(s)
        var prevT = Double.NEGATIVE_INFINITY
        for (sample in roll) {
            assertTrue("tSec must strictly increase", sample.tSec > prevT)
            if (prevT != Double.NEGATIVE_INFINITY) {
                assertTrue("gaps must not exceed STEP", sample.tSec - prevT <= 0.05 + 1e-9)
            }
            prevT = sample.tSec
        }
        assertEquals(s.flightTimeSec + RangeRollout.durationSec(s), roll.last().tSec, 1e-9)
    }

    @Test
    fun spinBackRendersBackwardHopsAboveGround() {
        // Wedge spin-back: hop touches land behind carry, arcs stay above ground.
        val s = hopShot(
            carryM = 100.0,
            totalM = 98.5,
            GroundHop(1.5, -0.8, 0.35, 0.6),
            GroundHop(0.5, -1.5, 0.18, 0.45),
        )
        val roll = RangeRollout.samples(s)
        assertTrue(roll.isNotEmpty())
        assertTrue("expected airborne samples", roll.maxOf { it.pz } > 0.0)
        // Net ground phase is backward: rest lands behind the carry point.
        assertEquals(s.totalM, roll.last().py, 1e-9)
        assertTrue("rest must be behind carry", roll.last().py < s.carryM)
        for (sample in roll) {
            assertTrue("pz must stay >= 0, got ${sample.pz}", sample.pz >= 0.0)
        }
    }

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
        // 30 m roll: start-speed cap wants 7.5 s, clamped to the 6.0 s max.
        assertEquals(6.0, RangeRollout.durationSec(s), 1e-9)
        assertEquals(s.flightTimeSec + RangeRollout.durationSec(s), roll.last().tSec, 1e-9)
    }

    @Test
    fun hopChainAnchorsAtTheFlightLandingPoint() {
        // Sliced shot: the flight's last sample ends LEFT of the rest x
        // because the ground kick displaces the run sideways after touchdown.
        // The ground phase must join that landing point, not teleport to the
        // rest-side anchor.
        val landing = TrajectorySample(-2.0, 100.0, 0.0, 5.0)
        val s = ShotResult(
            carryM = 100.0,
            rolloutM = 3.0,
            totalM = 103.0,
            sideM = 0.0,
            apexM = 20.0,
            flightTimeSec = 5.0,
            samples = listOf(
                TrajectorySample(-2.0, 99.5, 0.4, 4.95),
                landing,
            ),
            restX = 0.0,
            restY = 103.0,
            groundHops = listOf(GroundHop(2.0, 2.0, 0.30, 0.60)),
        )
        val roll = RangeRollout.samples(s)
        assertTrue(roll.isNotEmpty())
        val first = roll.first()
        val jumpM = hypot(first.px - landing.px, first.py - landing.py)
        assertTrue("first ground sample must join the landing point, jump ${jumpM}m", jumpM < 1.0)
        // The chain must not double back: forward roll only past touchdown.
        for (sample in roll) {
            assertTrue("py ${sample.py} dips below landing", sample.py >= landing.py - 1e-9)
        }
        assertEquals(s.sideM, roll.last().px, 1e-9)
        assertEquals(s.totalM, roll.last().py, 1e-9)
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
