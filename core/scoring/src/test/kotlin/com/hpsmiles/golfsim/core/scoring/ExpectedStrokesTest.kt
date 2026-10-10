package com.hpsmiles.golfsim.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpectedStrokesTest {

    @Test
    fun `exact rows are preserved`() {
        assertEquals(2.80, expectedStrokes(SkillBand.TOUR, Lie.FAIRWAY, 91.4), 1e-9)
    }

    @Test
    fun `relabel series is rejected`() {
        // series 2 would read putt 5 m as 1.23; series 1 gives 1.857
        assertEquals(1.857, expectedStrokes(SkillBand.SCRATCH, Lie.GREEN, 5.0), 0.002)
    }

    @Test
    fun `interpolation between rows matches the verified values`() {
        assertEquals(3.0267, expectedStrokes(SkillBand.SCRATCH, Lie.FAIRWAY, 100.0), 1e-3)
        assertEquals(3.3854, expectedStrokes(SkillBand.HCP10, Lie.FAIRWAY, 100.0), 1e-3)
        assertEquals(2.8274, expectedStrokes(SkillBand.TOUR, Lie.FAIRWAY, 100.0), 1e-3)
        assertEquals(3.9835, expectedStrokes(SkillBand.HCP18, Lie.FAIRWAY, 100.0), 1e-3)
    }

    @Test
    fun `values clamp at the grid ends`() {
        assertEquals(
            expectedStrokes(SkillBand.SCRATCH, Lie.GREEN, 0.30),
            expectedStrokes(SkillBand.SCRATCH, Lie.GREEN, 0.10),
            1e-12,
        )
        assertEquals(
            expectedStrokes(SkillBand.SCRATCH, Lie.TEE, 636.0),
            expectedStrokes(SkillBand.SCRATCH, Lie.TEE, 700.0),
            1e-12,
        )
    }

    @Test
    fun `strokesGained is start minus end minus one`() {
        // 100 m fairway to 10 m green, scratch band
        val sg = strokesGained(SkillBand.SCRATCH, Lie.FAIRWAY, 100.0, Lie.GREEN, 10.0)
        assertEquals(-0.070, sg, 0.005)
    }
}
