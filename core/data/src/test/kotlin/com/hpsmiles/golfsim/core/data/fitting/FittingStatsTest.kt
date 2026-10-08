package com.hpsmiles.golfsim.core.data.fitting

import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.record.ClubType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FittingStatsTest {

    private fun shot(
        id: Long,
        clubId: Long = 1,
        name: String = "A",
        excluded: Boolean = false,
        chs: Double = 33.0,
        ball: Double = 48.0,
        side: Double = 0.0,
        carry: Double = 140.0,
        total: Double = 150.0,
        spin: Int = 8000,
    ) = FittingShotEntity(
        id = id, sessionId = 1, clubId = clubId, clubName = name, clubType = "DRIVER",
        clubWasTemp = false, timestampMs = id, clubHeadSpeedMps = chs, ballSpeedMps = ball,
        launchAngleDeg = 12.0, launchDirDeg = -1.0, spinAxisDeg = -4.0, totalSpinRpm = spin,
        carryM = carry, totalM = total, sideM = side, apexM = 27.0, flightTimeSec = 6.0,
        excluded = excluded,
    )

    @Test
    fun `clubOrder - distinct clubs in first-appearance order`() {
        val shots = listOf(shot(1, clubId = 2, name = "B"), shot(2, clubId = 1, name = "A"), shot(3, clubId = 2, name = "B"))
        val order = FittingStats.clubOrder(shots)
        assertEquals(listOf(2L, 1L), order.map { it.id })
        assertEquals("B", order[0].name)
        assertEquals(ClubType.DRIVER, order[0].type)
    }

    @Test
    fun `summarize - excluded shots drop from every aggregate`() {
        val shots = listOf(
            shot(1, carry = 140.0, total = 150.0, side = -2.0),
            shot(2, carry = 150.0, total = 160.0, side = 4.0),
            shot(3, carry = 90.0, total = 95.0, side = 0.0, excluded = true, ball = 20.0),
        )
        val s = FittingStats.summarize(FittingStats.ClubKey(1, "A", ClubType.DRIVER, false), shots)
        assertEquals(2, s.kept)
        assertEquals(1, s.excluded)
        assertEquals(145.0, s.carry!!, 1e-9)
        assertEquals(5.0, s.carrySigma!!, 1e-9)
        assertEquals(155.0, s.total!!, 1e-9)
        assertEquals(3.0, s.offlineAvg!!, 1e-9)     // avg(|−2|, |4|)
        assertEquals(4.0, s.offlineWorst!!, 1e-9)
        assertEquals(48.0 / 33.0, s.smash!!, 1e-9)  // per-shot smash averaged
    }

    @Test
    fun `delta - significant vs noise band`() {
        assertTrue(FittingStats.delta(other = 5.0, baseline = 0.0, noise = 2.0)!!.significant)
        assertFalse(FittingStats.delta(other = 1.5, baseline = 0.0, noise = 2.0)!!.significant)
        assertNull(FittingStats.delta(other = null, baseline = 1.0, noise = 2.0))
        assertEquals(-3.0, FittingStats.delta(other = 2.0, baseline = 5.0, noise = 1.0)!!.delta, 1e-9)
    }

    @Test
    fun `applyDuffFilter - low ball speed vs median of ALL shots, dormant under 3`() {
        // median(20, 48, 46) = 46 → cutoff 39.1 → 20 is a duff (bag rule)
        val verdicts = FittingStats.applyDuffFilter(
            listOf(shot(1, ball = 20.0), shot(2, ball = 48.0), shot(3, ball = 46.0)),
        )
        assertEquals(listOf(true, false, false), verdicts.map { it.filtered })
        // Dormant below 3 shots: everything kept.
        val dormant = FittingStats.applyDuffFilter(listOf(shot(1, ball = 20.0), shot(2, ball = 48.0)))
        assertEquals(listOf(false, false), dormant.map { it.filtered })
    }
}
