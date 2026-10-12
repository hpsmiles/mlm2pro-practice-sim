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

    @Test
    fun `fairway and rough relief is nonnegative from 3m`() {
        for (band in bands) {
            var d = 3.0
            while (d <= 60.0 + 1e-9) {
                for (lie in listOf(Lie.FAIRWAY, Lie.ROUGH)) {
                    val relief = expectedStrokes(band, lie, d) -
                        SimExpectedStrokes.simEndValue(band, lie, d)
                    assertTrue("negative relief at $lie d=$d band=$band", relief >= -1e-9)
                }
                d += 1.0
            }
        }
    }

    @Test
    fun `end values are monotone per lie and floored at 1`() {
        for (band in bands) {
            for (lie in listOf(Lie.FAIRWAY, Lie.ROUGH)) {
                var prev = 0.0
                var d = 3.0
                while (d <= 200.0 + 1e-9) {
                    val e = SimExpectedStrokes.simEndValue(band, lie, d)
                    assertTrue("non-monotone $lie at $d", e >= prev - 1e-9)
                    assertTrue("below floor $lie at $d", e >= 1.0 - 1e-9)
                    prev = e
                    d += 5.0
                }
            }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `tee is not priced`() {
        SimExpectedStrokes.simEndValue(SkillBand.SCRATCH, Lie.TEE, 100.0)
    }

    @Test
    fun `outcome grid probabilities sum to one`() {
        for (band in bands) {
            for (start in SimExpectedStrokes.START_DISTANCES) {
                val total = SimExpectedStrokes.outcomeGrid(band, start).sumOf { it.probability }
                assertEquals("prob sum band=$band start=$start", 1.0, total, 1e-9)
            }
        }
    }

    @Test
    fun `start value is monotone in distance and band ordered`() {
        for (band in bands) {
            var prev = 0.0
            var d = 50.0
            while (d <= 150.0 + 1e-9) {
                val e = SimExpectedStrokes.simStartValue(band, d)
                assertTrue("start non-monotone at $d", e >= prev - 1e-9)
                prev = e
                d += 5.0
            }
        }
        for (d in listOf(50.0, 70.0, 90.0, 100.0, 150.0)) {
            for (i in 0 until bands.size - 1) {
                assertTrue(
                    "start band order at $d",
                    SimExpectedStrokes.simStartValue(bands[i], d) <=
                        SimExpectedStrokes.simStartValue(bands[i + 1], d) + 1e-9,
                )
            }
        }
    }

    @Test
    fun `drive sg is zero at the scratch typical-drive reference`() {
        // DRIVER_HOLE_M 340 − SCRATCH typicalDriveM 239.6 = 100.4 (reuses public const from ExpectedStrokes.kt)
        assertEquals(
            0.0,
            SimExpectedStrokes.simDrivingStrokesGained(SkillBand.SCRATCH, Lie.FAIRWAY, 100.4),
            1e-9,
        )
    }

    @Test
    fun `drive sg punishes rough more than fairway and decreases with distance`() {
        for (band in bands) {
            var d = 50.0
            while (d <= 300.0 + 1e-9) {
                val fair = SimExpectedStrokes.simDrivingStrokesGained(band, Lie.FAIRWAY, d)
                val rough = SimExpectedStrokes.simDrivingStrokesGained(band, Lie.ROUGH, d)
                assertTrue("rough not punished at $d band=$band", rough <= fair + 1e-9)
                d += 10.0
            }
            var prev = Double.MAX_VALUE
            d = 50.0
            while (d <= 300.0 + 1e-9) {
                val sg = SimExpectedStrokes.simDrivingStrokesGained(band, Lie.FAIRWAY, d)
                assertTrue("drive sg not decreasing at $d", sg <= prev + 1e-9)
                prev = sg
                d += 10.0
            }
        }
    }
}
