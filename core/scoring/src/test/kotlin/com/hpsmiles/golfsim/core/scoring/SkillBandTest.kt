package com.hpsmiles.golfsim.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class SkillBandTest {

    @Test
    fun `nearestTo clamps non-finite and out-of-range values`() {
        assertEquals(SkillBand.TOUR, SkillBand.nearestTo(-9.0))
        assertEquals(SkillBand.TOUR, SkillBand.nearestTo(-5.84))
        assertEquals(SkillBand.HCP18, SkillBand.nearestTo(21.83))
        assertEquals(SkillBand.HCP18, SkillBand.nearestTo(Double.POSITIVE_INFINITY))
        assertEquals(SkillBand.HCP18, SkillBand.nearestTo(Double.NaN))
        assertEquals(SkillBand.TOUR, SkillBand.nearestTo(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `nearestTo picks the nearest anchor`() {
        assertEquals(SkillBand.SCRATCH, SkillBand.nearestTo(-1.3))
        assertEquals(SkillBand.SCRATCH, SkillBand.nearestTo(4.9))
        assertEquals(SkillBand.HCP10, SkillBand.nearestTo(10.0))
        assertEquals(SkillBand.HCP18, SkillBand.nearestTo(17.9))
    }

    @Test
    fun `nearestTo ties resolve to the better band`() {
        // Tie 0 vs 10 -> SCRATCH (earlier entry).
        assertEquals(SkillBand.SCRATCH, SkillBand.nearestTo(5.0))
        // Tie 10 vs 18 -> HCP10 (earlier entry).
        assertEquals(SkillBand.HCP10, SkillBand.nearestTo(14.0))
    }
}
