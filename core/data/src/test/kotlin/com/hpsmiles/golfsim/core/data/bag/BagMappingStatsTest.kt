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

    /** Richer constructor for the misread-gate fixtures (all the raw block fields). */
    private fun misreadShot(
        id: Long,
        clubName: String,
        chs: Double,
        ballSpeed: Double,
        launch: Double = 12.0,
        dir: Double = -1.0,
        axis: Double = -4.0,
        spin: Int = 8000,
    ) = BagMappingShotEntity(
        id = id, sessionId = 1, clubName = clubName, clubType = "IRON",
        timestampMs = id, clubHeadSpeedMps = chs, ballSpeedMps = ballSpeed,
        launchAngleDeg = launch, launchDirDeg = dir, spinAxisDeg = axis,
        totalSpinRpm = spin, carryM = ballSpeed * 2.2, totalM = ballSpeed * 2.35,
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

    // --- Misread gates (2026-10-16) ---------------------------------------

    @Test
    fun `misread gates dormant below three shots - everything kept`() {
        val verdicts = BagMappingStats.applyMisreadGates(
            listOf(misreadShot(1, "5i", 42.7, 62.0), misreadShot(2, "5i", 35.7, 58.4)),
        )
        assertEquals(listOf(false, false), verdicts.map { it.filtered })
        assertEquals(listOf(null, null), verdicts.map { it.reason })
    }

    /**
     * Real 2026-10-15 session, 5i shot id 43: CHS 35.7 m/s vs the club's
     * 42.0–44.0, smash 1.636. The smash is above the physical cap AND the CHS
     * trips the ±5% band — precedence must report MISREAD_SMASH.
     */
    @Test
    fun `5i misread - physically impossible smash wins precedence over chs`() {
        val fiveI = listOf(
            misreadShot(41, "5i", 42.7, 62.0, 14.0, 0.0, -4.0, 7000),
            misreadShot(42, "5i", 42.7, 62.0, 14.0, 0.0, -4.0, 7000),
            misreadShot(43, "5i", 35.7, 58.4, 14.0, 0.0, -4.0, 7000),
            misreadShot(44, "5i", 42.7, 62.0, 14.0, 0.0, -4.0, 7000),
            misreadShot(45, "5i", 42.7, 62.0, 14.0, 0.0, -4.0, 7000),
        )
        val verdicts = BagMappingStats.applyMisreadGates(fiveI)
        assertEquals(listOf(false, false, true, false, false), verdicts.map { it.filtered })
        assertEquals(BagMappingStats.REASON_MISREAD_SMASH, verdicts[2].reason)
    }

    /**
     * Real 2026-10-15 session, GW shot id 15: spin 4502 rpm vs the club's
     * 9675–10827 (median ~9843) → outside 0.5×–2.0×.
     */
    @Test
    fun `wedge misread - spin outlier flags MISREAD_SPIN`() {
        val gw = listOf(
            misreadShot(13, "GW", 30.0, 44.5, 26.0, 0.0, -6.0, 9843),
            misreadShot(14, "GW", 30.0, 44.5, 26.0, 0.0, -6.0, 10000),
            misreadShot(15, "GW", 30.0, 44.5, 26.0, 0.0, -6.0, 4502),
            misreadShot(16, "GW", 30.0, 44.5, 26.0, 0.0, -6.0, 9675),
            misreadShot(17, "GW", 30.0, 44.5, 26.0, 0.0, -6.0, 10827),
        )
        val verdicts = BagMappingStats.applyMisreadGates(gw)
        assertEquals(listOf(false, false, true, false, false), verdicts.map { it.filtered })
        assertEquals(BagMappingStats.REASON_MISREAD_SPIN, verdicts[2].reason)
    }

    /**
     * Real 2026-10-15 session, driver shots id 64 + id 65: id 64's spin axis
     * −27° vs the club's −11…+1 (median −6) → MISREAD_DIRECTION; id 65's CHS
     * 116.1 mph (51.9 m/s) vs the club median 110.1 mph (49.2 m/s) →
     * MISREAD_CHS, and nothing else.
     */
    @Test
    fun `driver misreads - spin axis outlier flags direction and chs outlier flags chs`() {
        val driver = listOf(
            misreadShot(61, "D", 49.2, 72.2, 10.0, -2.0, -11.0, 2200),
            misreadShot(62, "D", 49.2, 72.2, 11.0, 0.0, -6.0, 2400),
            misreadShot(63, "D", 49.2, 72.2, 12.0, 1.0, -1.0, 2600),
            misreadShot(64, "D", 49.2, 72.2, 11.0, 0.0, -27.0, 2500),
            misreadShot(65, "D", 51.9, 76.1, 13.0, 3.0, 1.0, 2800),
        )
        val verdicts = BagMappingStats.applyMisreadGates(driver)
        assertEquals(listOf(false, false, false, true, true), verdicts.map { it.filtered })
        assertEquals(BagMappingStats.REASON_MISREAD_DIRECTION, verdicts[3].reason)
        assertEquals(BagMappingStats.REASON_MISREAD_CHS, verdicts[4].reason)
    }

    /** The other 4 shots of each misread club must never flag. */
    @Test
    fun `only the flagged shots of each misread club are filtered`() {
        val fiveI = listOf(
            misreadShot(41, "5i", 42.7, 62.0, 14.0, 0.0, -4.0, 7000),
            misreadShot(42, "5i", 42.7, 62.0, 14.0, 0.0, -4.0, 7000),
            misreadShot(43, "5i", 35.7, 58.4, 14.0, 0.0, -4.0, 7000),
            misreadShot(44, "5i", 42.7, 62.0, 14.0, 0.0, -4.0, 7000),
            misreadShot(45, "5i", 42.7, 62.0, 14.0, 0.0, -4.0, 7000),
        )
        val gw = listOf(
            misreadShot(13, "GW", 30.0, 44.5, 26.0, 0.0, -6.0, 9843),
            misreadShot(14, "GW", 30.0, 44.5, 26.0, 0.0, -6.0, 9843),
            misreadShot(15, "GW", 30.0, 44.5, 26.0, 0.0, -6.0, 4502),
            misreadShot(16, "GW", 30.0, 44.5, 26.0, 0.0, -6.0, 9843),
            misreadShot(17, "GW", 30.0, 44.5, 26.0, 0.0, -6.0, 9843),
        )
        val driver = listOf(
            misreadShot(61, "D", 49.2, 72.2, 11.0, 0.0, -6.0, 2500),
            misreadShot(62, "D", 49.2, 72.2, 11.0, 0.0, -6.0, 2500),
            misreadShot(63, "D", 49.2, 72.2, 11.0, 0.0, -6.0, 2500),
            misreadShot(64, "D", 49.2, 72.2, 11.0, 0.0, -27.0, 2500),
            misreadShot(65, "D", 51.9, 76.1, 13.0, 3.0, 1.0, 2800),
        )
        // The gates are per-club (the repository calls shotsForClub), so the
        // median is over each club's own 5 shots.
        val flagged = (fiveI + gw + driver)
            .groupBy { it.clubName }
            .values
            .flatMap { BagMappingStats.applyMisreadGates(it) }
            .filter { it.filtered }
            .map { it.id }
        assertEquals(listOf(43L, 15L, 64L, 65L), flagged)
    }

    /** Real strikes with proportionally low ball speed keep a normal smash/CHS — never flagged. */
    @Test
    fun `real strike with low ball speed but normal chs is not a misread`() {
        // A thin strike ~10% short: CHS normal (40), ball speed 10% low (52 → smash 1.30,
        // still inside 0.85×–1.15× of the 1.45 median), spin/launch/direction normal.
        val sevenI = listOf(
            misreadShot(71, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(72, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(73, "7i", 40.0, 52.0, 17.0, 1.0, -2.0, 6800),
            misreadShot(74, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(75, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
        )
        val verdicts = BagMappingStats.applyMisreadGates(sevenI)
        assertEquals(listOf(false, false, false, false, false), verdicts.map { it.filtered })
    }

    /** Precedence: a shot failing BOTH spin and direction reports spin. */
    @Test
    fun `precedence - spin beats direction when both fail`() {
        val club = listOf(
            misreadShot(1, "PW", 33.0, 48.0, 24.0, 0.0, -6.0, 10000),
            misreadShot(2, "PW", 33.0, 48.0, 24.0, 0.0, -6.0, 10000),
            misreadShot(3, "PW", 33.0, 48.0, 24.0, 0.0, -40.0, 2500),
            misreadShot(4, "PW", 33.0, 48.0, 24.0, 0.0, -6.0, 10000),
            misreadShot(5, "PW", 33.0, 48.0, 24.0, 0.0, -6.0, 10000),
        )
        val verdicts = BagMappingStats.applyMisreadGates(club)
        assertEquals(BagMappingStats.REASON_MISREAD_SPIN, verdicts[2].reason)
    }

    /** Precedence: a shot failing BOTH chs and spin reports chs. */
    @Test
    fun `precedence - chs beats spin when both fail`() {
        val club = listOf(
            misreadShot(1, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(2, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(3, "7i", 36.0, 52.0, 18.0, 0.0, -4.0, 3000),
            misreadShot(4, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(5, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
        )
        val verdicts = BagMappingStats.applyMisreadGates(club)
        assertEquals(BagMappingStats.REASON_MISREAD_CHS, verdicts[2].reason)
    }

    /** Boundary: a shot just inside the band is kept, just outside flags (strict < and >). */
    @Test
    fun `boundary - chs just inside the 5-percent band is kept, just outside flags`() {
        // 41.9/40 = 1.0475 < 1.05 → kept; 42.1/40 = 1.0525 > 1.05 → MISREAD_CHS.
        val inside = listOf(
            misreadShot(1, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(2, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(3, "7i", 41.9, 60.8, 18.0, 0.0, -4.0, 7000),
            misreadShot(4, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(5, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
        )
        assertEquals(
            listOf(false, false, false, false, false),
            BagMappingStats.applyMisreadGates(inside).map { it.filtered },
        )
        val outside = listOf(
            misreadShot(1, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(2, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(3, "7i", 42.1, 61.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(4, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
            misreadShot(5, "7i", 40.0, 58.0, 18.0, 0.0, -4.0, 7000),
        )
        assertEquals(BagMappingStats.REASON_MISREAD_CHS, BagMappingStats.applyMisreadGates(outside)[2].reason)
    }

    /** applyAutoFilter: a misread reason beats a duff; a pure duff still reports the duff reason. */
    @Test
    fun `auto filter - misread beats duff and pure duff stays duff`() {
        // 5i-style club. id 43 is the known misread (smash 1.636 — not a duff:
        // ball 58.4 vs median 62). id 46 is a genuine duff (ball 52 vs median
        // 62 → under the 85 % cutoff) that keeps a normal smash/CHS (CHS 41,
        // smash 1.268 — inside the bands), so only the duff filter catches it.
        val fiveI = listOf(
            misreadShot(41, "5i", 42.7, 62.0, 14.0, 0.0, -4.0, 7000),
            misreadShot(42, "5i", 42.7, 62.0, 14.0, 0.0, -4.0, 7000),
            misreadShot(43, "5i", 35.7, 58.4, 14.0, 0.0, -4.0, 7000),
            misreadShot(44, "5i", 42.7, 62.0, 14.0, 0.0, -4.0, 7000),
            misreadShot(46, "5i", 41.0, 52.0, 14.0, 0.0, -4.0, 7000),
        )
        val verdicts = BagMappingStats.applyAutoFilter(fiveI)
        assertEquals(BagMappingStats.REASON_MISREAD_SMASH, verdicts[2].reason)
        assertEquals(BagMappingStats.REASON_DUFF_LOW_BALL_SPEED, verdicts[4].reason)
    }
}
