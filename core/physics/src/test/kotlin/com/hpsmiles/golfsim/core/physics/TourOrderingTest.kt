package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tiered-gate ordering assertions (spec §8 clauses 2-4). Absolute vs-Tour
 * accuracy is a documented bias and is deliberately NOT asserted here.
 */
class TourOrderingTest {

    private fun shot(mph: Double, vla: Double, spin: Int): ShotResult =
        BallFlightEngine.simulate(LaunchConditions(mph * 0.44704, vla, spin))

    private val pgaDriver = shot(171.5, 10.4, 2545)
    private val pga3W = shot(162.0, 9.3, 3663)
    private val pga5i = shot(135.0, 14.8, 5280)
    private val pga7i = shot(123.0, 16.3, 7124)
    private val pgaPw = shot(102.0, 24.2, 9304)
    private val lpgaDriver = shot(140.0, 13.2, 2611)
    private val lpga7i = shot(99.5, 17.1, 6417)
    private val lpgaPw = shot(82.0, 24.6, 8525)

    @Test
    fun carryLadderIsStrictlyOrdered() {
        assertTrue(pgaDriver.carryM > pga3W.carryM)
        assertTrue(pga3W.carryM > pga5i.carryM)
        assertTrue(pga5i.carryM > pga7i.carryM)
        assertTrue(pga7i.carryM > pgaPw.carryM)
        assertTrue(lpgaDriver.carryM > lpga7i.carryM)
        assertTrue(lpga7i.carryM > lpgaPw.carryM)
    }

    @Test
    fun rolloutFamilyOrderingHolds() {
        val driverFamily = listOf(pgaDriver.rolloutM, pga3W.rolloutM)
        assertTrue(driverFamily.min() > pga5i.rolloutM)
        assertTrue(pga5i.rolloutM > pga7i.rolloutM)
        assertTrue(pga7i.rolloutM > pgaPw.rolloutM)
        assertTrue(lpgaDriver.rolloutM > lpga7i.rolloutM)
        assertTrue(lpga7i.rolloutM > lpgaPw.rolloutM)
    }

    @Test
    fun allRolloutsAreNonNegative() {
        listOf(pgaDriver, pga3W, pga5i, pga7i, pgaPw, lpgaDriver, lpga7i, lpgaPw).forEach {
            assertTrue("negative rollout on ${it.carryM}", it.rolloutM >= 0.0)
        }
    }

    @Test
    fun firmnessAndMirrorSymmetryHold() {
        // Iron-class shot: strict firm > normal > soft holds on roll-dominated
        // ground phases. (Driver-class shots can invert soft-vs-normal by < 1 m —
        // bounce-loop dominance; see BallFlightEngineTest KDoc and spec §10.)
        val launch = LaunchConditions(123.0 * 0.44704, 16.3, 7124)
        val normal = BallFlightEngine.simulate(launch)
        val firm = BallFlightEngine.simulate(
            launch, surfaces = UniformSurface(Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM)),
        )
        val soft = BallFlightEngine.simulate(
            launch, surfaces = UniformSurface(Surface.FAIRWAY_NORMAL.withFirmness(Firmness.SOFT)),
        )
        assertTrue(firm.totalM > normal.totalM)
        assertTrue(normal.totalM > soft.totalM)

        val draw = BallFlightEngine.simulate(LaunchConditions(70.0, 12.0, 3000, spinAxisDeg = 5.0))
        val fade = BallFlightEngine.simulate(LaunchConditions(70.0, 12.0, 3000, spinAxisDeg = -5.0))
        assertEquals(draw.sideM, -fade.sideM, 1e-9)
        assertEquals(draw.totalM, fade.totalM, 1e-9)
    }
}
