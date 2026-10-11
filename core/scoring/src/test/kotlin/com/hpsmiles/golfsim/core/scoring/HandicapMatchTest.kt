package com.hpsmiles.golfsim.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandicapMatchTest {

    private fun goldenShots(): List<ShotInput> =
        listOf(1.0, 6.0, 7.0, 8.0, 10.0, 12.0).map {
            ShotInput(Lie.FAIRWAY, 100.0, Lie.GREEN, it)
        }

    @Test
    fun `golden fixture - per-band mean SG`() {
        val estimate = handicapMatch(goldenShots())
        assertEquals(0.021, estimate.perBandSg.getValue(SkillBand.TOUR), 0.005)
        assertEquals(0.148, estimate.perBandSg.getValue(SkillBand.SCRATCH), 0.005)
        assertEquals(0.411, estimate.perBandSg.getValue(SkillBand.HCP10), 0.005)
        assertEquals(0.865, estimate.perBandSg.getValue(SkillBand.HCP18), 0.005)
    }

    @Test
    fun `golden fixture - estimate is below tour and clamped`() {
        val estimate = handicapMatch(goldenShots())
        assertEquals(-5.84, estimate.est, 0.05)
        assertTrue(estimate.clampedLow)
        assertFalse(estimate.clampedHigh)
    }

    @Test
    fun `golden fixture - uncertainty from scratch-band spread`() {
        val estimate = handicapMatch(goldenShots())
        assertNotNull(estimate.low)
        assertNotNull(estimate.high)
        val uncertainty = (estimate.high!! - estimate.low!!) / 2.0
        assertEquals(6.5, uncertainty, 0.1)
        assertTrue(estimate.low!! < estimate.est)
        assertTrue(estimate.high!! > estimate.est)
    }

    @Test
    fun `single shot has no uncertainty range`() {
        val estimate = handicapMatch(listOf(ShotInput(Lie.FAIRWAY, 100.0, Lie.GREEN, 10.0)))
        assertNull(estimate.low)
        assertNull(estimate.high)
    }

    @Test
    fun `empty shots are rejected`() {
        try {
            handicapMatch(emptyList())
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `crossing branch - mid curve root`() {
        val estimate = handicapMatch(listOf(ShotInput(Lie.GREEN, 18.3, Lie.GREEN, 2.1)))
        assertEquals(2.0104, estimate.est, 0.01)
        assertFalse(estimate.clampedLow)
        assertFalse(estimate.clampedHigh)
        assertNull(estimate.low)
        assertNull(estimate.high) // n = 1
    }

    @Test
    fun `all-negative branch - beyond 18 clamp`() {
        val estimate = handicapMatch(listOf(ShotInput(Lie.GREEN, 5.5, Lie.GREEN, 4.0)))
        assertEquals(243.15, estimate.est, 0.1)
        assertTrue(estimate.clampedHigh)
        assertFalse(estimate.clampedLow)
    }

    @Test
    fun `worse than all bands - negative slope clamps high not low`() {
        // Real first-run defect (session 1, 100 m approach resting 148.73 m
        // from the pin): band means are all negative AND the [10,18] segment
        // is non-increasing, so the old extrapolation ran to ~-56 and was
        // wrongly shown as "TOUR+". It must clamp HIGH instead.
        val estimate = handicapMatch(listOf(ShotInput(Lie.FAIRWAY, 100.0, Lie.FAIRWAY, 148.73)))
        assertTrue(estimate.clampedHigh)
        assertFalse(estimate.clampedLow)
        assertTrue(estimate.est > 25.0)
        assertNull(estimate.low)
        assertNull(estimate.high)
    }

    @Test
    fun `worse than all bands - positive slope keeps extrapolation`() {
        // Regression pin for today's behaviour: a rising [10,18] segment still
        // extrapolates beyond 18 (band means -0.902 / -0.854 → est ≈ 161.0).
        val estimate = handicapMatch(listOf(ShotInput(Lie.FAIRWAY, 100.0, Lie.FAIRWAY, 85.45)))
        assertTrue(estimate.clampedHigh)
        assertEquals(161.0, estimate.est, 1.0)
    }

    @Test
    fun `worse than all bands - second negative slope pin`() {
        val estimate = handicapMatch(listOf(ShotInput(Lie.FAIRWAY, 100.0, Lie.FAIRWAY, 171.17)))
        assertTrue(estimate.clampedHigh)
        assertTrue(estimate.est > 25.0)
    }
}
