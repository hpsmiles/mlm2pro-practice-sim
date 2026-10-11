package com.hpsmiles.golfsim.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingStrokesGainedTest {

    /** Six demo rests (metres) from the driver station, all resting on the fairway. */
    private val demoRests = listOf(74.48, 138.55, 184.30, 205.57, 102.32, 169.54)

    private fun demoPerShot(): List<Map<SkillBand, Double>> =
        demoRests.map { rest ->
            SkillBand.entries.associateWith { drivingStrokesGained(it, Lie.FAIRWAY, rest) }
        }

    private fun identicalDrives(count: Int, driveM: Double): List<Map<SkillBand, Double>> {
        val rest = DRIVER_HOLE_M - driveM
        return List(count) {
            SkillBand.entries.associateWith { drivingStrokesGained(it, Lie.FAIRWAY, rest) }
        }
    }

    @Test
    fun `calibration - an 18-band typical drive scores neutral at 18`() {
        // Six identical 193.9 m drives leave 146.1 m, exactly the HCP18 reference position,
        // so the estimate lands precisely on the 18 anchor with no clamps.
        val estimate = handicapMatchSg(identicalDrives(6, 193.9))
        assertEquals(18.0, estimate.est, 1e-6)
        assertFalse(estimate.clampedLow)
        assertFalse(estimate.clampedHigh)
    }

    @Test
    fun `demo rests reproduce the pinned band means`() {
        val estimate = handicapMatchSg(demoPerShot())
        assertEquals(-0.3045, estimate.perBandSg.getValue(SkillBand.TOUR), 1e-3)
        assertEquals(-0.2526, estimate.perBandSg.getValue(SkillBand.SCRATCH), 1e-3)
        assertEquals(-0.1567, estimate.perBandSg.getValue(SkillBand.HCP10), 1e-3)
        assertEquals(-0.0508, estimate.perBandSg.getValue(SkillBand.HCP18), 1e-3)
    }

    @Test
    fun `demo rests produce the pinned estimate with no clamps`() {
        val estimate = handicapMatchSg(demoPerShot())
        assertEquals(21.83, estimate.est, 0.1)
        assertFalse(estimate.clampedLow)
        assertFalse(estimate.clampedHigh)
    }

    @Test
    fun `longer drives produce lower estimates`() {
        val estimates = listOf(180.0, 200.0, 220.0, 250.0).map { drive ->
            handicapMatchSg(identicalDrives(6, drive)).est
        }
        for (i in 1 until estimates.size) {
            assertTrue(
                "estimate for a longer drive must be lower: ${estimates[i - 1]} -> ${estimates[i]}",
                estimates[i] < estimates[i - 1],
            )
        }
    }
}
