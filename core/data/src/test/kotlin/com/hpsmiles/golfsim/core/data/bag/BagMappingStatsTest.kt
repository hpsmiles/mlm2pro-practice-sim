package com.hpsmiles.golfsim.core.data.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

/**
 * Golden cases for the M6 duff filter + distributions (spec §5). The filter
 * is a pure function of the club's whole shot set — recomputed on every
 * write, so verdicts may change retroactively as shots accumulate.
 */
class BagMappingStatsTest {

    private fun shot(id: Long, ballSpeed: Double) = BagMappingShotEntity(
        id = id, sessionId = 1, clubName = "7i", clubType = "IRON",
        timestampMs = id, clubHeadSpeedMps = 33.0, ballSpeedMps = ballSpeed,
        launchAngleDeg = 12.0, launchDirDeg = -1.0, spinAxisDeg = -4.0,
        totalSpinRpm = 8000, carryM = 140.0, totalM = 150.0,
    )

    @Test
    fun `dormant below three shots - everything kept`() {
        val verdicts = BagMappingStats.applyDuffFilter(listOf(shot(1, 20.0), shot(2, 48.0)))
        assertEquals(listOf(false, false), verdicts.map { it.filtered })
        assertEquals(listOf(null, null), verdicts.map { it.reason })
    }

    @Test
    fun `three shots - low ball speed filtered against median of ALL shots`() {
        // median(20, 48, 46) = 46 → cutoff 39.1 → 20 is a duff
        val verdicts = BagMappingStats.applyDuffFilter(listOf(shot(1, 20.0), shot(2, 48.0), shot(3, 46.0)))
        assertEquals(listOf(true, false, false), verdicts.map { it.filtered })
        assertEquals(BagMappingStats.REASON_DUFF_LOW_BALL_SPEED, verdicts[0].reason)
    }

    @Test
    fun `recompute retroactively filters early shots`() {
        // Same three shots as above but inserted in the order 46, 48, 20:
        // after the 3rd write the FIRST row (id 3, 20 m/s) flips to filtered.
        val verdicts = BagMappingStats.applyDuffFilter(listOf(shot(3, 46.0), shot(2, 48.0), shot(1, 20.0)))
        assertEquals(listOf(false, false, true), verdicts.map { it.filtered })
    }

    @Test
    fun `poisoned baseline - filter blind spot is real, gate catches it`() {
        // Two duffs first → median sits low → 85% check cannot separate.
        // Documented blind spot (spec §5): the window condition of the
        // quality gate prompts "hit more", which pulls the median up.
        val verdicts = BagMappingStats.applyDuffFilter(listOf(shot(1, 20.0), shot(2, 22.0), shot(3, 48.0)))
        assertEquals(listOf(false, false, false), verdicts.map { it.filtered })
    }

    @Test
    fun `low side only - a very fast strike is never filtered`() {
        val verdicts = BagMappingStats.applyDuffFilter(listOf(shot(1, 48.0), shot(2, 50.0), shot(3, 70.0)))
        assertEquals(listOf(false, false, false), verdicts.map { it.filtered })
    }

    @Test
    fun `already-filtered rows re-judged identically - deterministic`() {
        val shots = listOf(shot(1, 20.0), shot(2, 48.0), shot(3, 46.0))
        val first = BagMappingStats.applyDuffFilter(shots)
        val second = BagMappingStats.applyDuffFilter(shots)
        assertEquals(first, second)
    }

    @Test
    fun `boundary - a shot at exactly the 85-percent cutoff is kept, just below is filtered`() {
        // median(42.5, 50, 100) = 50 → cutoff 42.5; equality is NOT a duff (strict <).
        val atCutoff = BagMappingStats.applyDuffFilter(listOf(shot(1, 100.0), shot(2, 50.0), shot(3, 42.5)))
        assertEquals(listOf(false, false, false), atCutoff.map { it.filtered })
        // median(42.499, 50, 100) = 50 → cutoff 42.5; just below IS a duff.
        val justBelow = BagMappingStats.applyDuffFilter(listOf(shot(1, 100.0), shot(2, 50.0), shot(3, 42.499)))
        assertEquals(listOf(false, false, true), justBelow.map { it.filtered })
        assertEquals(BagMappingStats.REASON_DUFF_LOW_BALL_SPEED, justBelow[2].reason)
    }

    @Test
    fun `median - odd and even sizes`() {
        assertEquals(3.0, BagMappingStats.median(listOf(1.0, 2.0, 3.0, 4.0, 5.0))!!, 1e-9)
        assertEquals(2.5, BagMappingStats.median(listOf(1.0, 2.0, 3.0, 4.0))!!, 1e-9)
        assertNull(BagMappingStats.median(emptyList()))
    }

    @Test
    fun `distribution - Tukey halves with median included for odd sizes`() {
        val d = BagMappingStats.distribution(listOf(1.0, 2.0, 3.0, 4.0, 5.0))!!
        assertEquals(3.0, d.median, 1e-9)
        assertEquals(3.0, d.mean, 1e-9)
        assertEquals(sqrt(2.0), d.sigma, 1e-9) // population σ of 1..5
        assertEquals(2.0, d.q1, 1e-9)          // median of [1,2,3]
        assertEquals(4.0, d.q3, 1e-9)          // median of [3,4,5]
        assertEquals(1.0, d.min, 1e-9)
        assertEquals(5.0, d.max, 1e-9)
        assertEquals(5, d.count)
        val d4 = BagMappingStats.distribution(listOf(1.0, 2.0, 3.0, 4.0))!!
        assertEquals(1.5, d4.q1, 1e-9)
        assertEquals(3.5, d4.q3, 1e-9)
        assertNull(BagMappingStats.distribution(emptyList()))
    }
}
