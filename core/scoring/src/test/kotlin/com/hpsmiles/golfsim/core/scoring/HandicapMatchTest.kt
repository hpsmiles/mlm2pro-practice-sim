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
}
