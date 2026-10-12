package com.hpsmiles.golfsim.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimExpectedStrokesTest {

    private val bands = SkillBand.entries

    @Test
    fun `green end value is monotone in distance`() {
        for (band in bands) {
            var prev = 0.0
            var d = 0.3
            while (d <= 15.0 + 1e-9) {
                val e = SimExpectedStrokes.simEndValue(band, Lie.GREEN, d)
                assertTrue("non-monotone at d=$d band=$band", e >= prev - 1e-9)
                prev = e
                d += 0.1
            }
        }
    }

    @Test
    fun `green end value respects band ordering`() {
        for (d in listOf(0.3, 1.0, 2.0, 3.2, 5.49, 9.0, 15.0)) {
            for (i in 0 until bands.size - 1) {
                val better = SimExpectedStrokes.simEndValue(bands[i], Lie.GREEN, d)
                val worse = SimExpectedStrokes.simEndValue(bands[i + 1], Lie.GREEN, d)
                assertTrue("band order violated at d=$d", better <= worse + 1e-9)
            }
        }
    }

    @Test
    fun `green relief versus real table is positive at mid range`() {
        // Seed sanity (comments only — knobs are fitting vars):
        // E_scratch(5.49)=1.787, E_tour(5.49)=1.750, E_hcp18(5.49)=1.855, E_scratch(0.3)=1.086
        for (band in bands) {
            val relief = expectedStrokes(band, Lie.GREEN, 5.49) -
                SimExpectedStrokes.simEndValue(band, Lie.GREEN, 5.49)
            assertTrue("relief must be positive at 5.49 for $band", relief > 0.0)
        }
    }

    @Test
    fun `tap-in green value stays near 1`() {
        for (band in bands) {
            val e = SimExpectedStrokes.simEndValue(band, Lie.GREEN, 0.3)
            assertTrue("tap-in out of range for $band: $e", e > 1.0 && e <= 1.15)
        }
    }
}
