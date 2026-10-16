package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.record.ClubType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden cases for adjacent-club gap flags. 2026-10-16: the healthy band is
 * proportional — 4–10% of the LONGER club's carry, clamped to 3–20 m —
 * replacing the fixed 8–20 m band, and the driver sits outside the gap
 * chain (specialist tee club).
 */
class GapAnalysisTest {

    // --- proportional band anchors (research: 9–14 m between irons for an
    // average player, tighter in wedges / wider at the long end, scaling
    // with speed; >20 m is a hole at every speed) ----------------------------

    @Test
    fun `band anchors at research speeds`() {
        assertEquals(3.8, GapAnalysis.bandLowM(95.0), 1e-9)
        assertEquals(9.5, GapAnalysis.bandHighM(95.0), 1e-9)
        assertEquals(5.6, GapAnalysis.bandLowM(140.0), 1e-9)
        assertEquals(14.0, GapAnalysis.bandHighM(140.0), 1e-9)
        assertEquals(6.6, GapAnalysis.bandLowM(165.0), 1e-9)
        assertEquals(16.5, GapAnalysis.bandHighM(165.0), 1e-9)
        // At driver distance the 10% high bound would be 23.7 — the 20 m
        // "hole" cap binds.
        assertEquals(9.48, GapAnalysis.bandLowM(237.0), 1e-9)
        assertEquals(20.0, GapAnalysis.bandHighM(237.0), 1e-9)
        // Floor: a short wedge never gets a sub-3 m target.
        assertEquals(3.0, GapAnalysis.bandLowM(60.0), 1e-9)
    }

    @Test
    fun `band boundaries - just below low is tight, at the bounds is healthy, above high is wide`() {
        // L=140 → band 5.6–14.0 (compared against bandLowM/bandHighM itself —
        // the fractional products carry float fuzz, so literal 5.6 is not
        // guaranteed to sit on the boundary).
        assertEquals(GapFlag.TIGHT, GapAnalysis.flagFor(140.0, GapAnalysis.bandLowM(140.0) - 0.1))
        assertEquals(GapFlag.HEALTHY, GapAnalysis.flagFor(140.0, GapAnalysis.bandLowM(140.0)))
        assertEquals(GapFlag.HEALTHY, GapAnalysis.flagFor(140.0, GapAnalysis.bandHighM(140.0)))
        assertEquals(GapFlag.WIDE, GapAnalysis.flagFor(140.0, GapAnalysis.bandHighM(140.0) + 0.1))
        // Zero/negative gap is inverted regardless of the band.
        assertEquals(GapFlag.INVERTED, GapAnalysis.flagFor(140.0, 0.0))
        assertEquals(GapFlag.INVERTED, GapAnalysis.flagFor(140.0, -3.0))
    }

    // --- analyze (generic long→short list tests, unchanged contract shape) --

    @Test
    fun `healthy gap`() {
        val gaps = GapAnalysis.analyze(listOf("D" to 200.0, "3W" to 190.0))
        assertEquals(1, gaps.size)
        assertEquals(GapFlag.HEALTHY, gaps.single().flag)
        assertEquals(10.0, gaps.single().gapM, 1e-9)
        assertEquals("D", gaps.single().longerClub)
        assertEquals("3W", gaps.single().shorterClub)
        // The pair's own band rides on the entry for the UI.
        assertEquals(8.0, gaps.single().bandLowM, 1e-9)
        assertEquals(20.0, gaps.single().bandHighM, 1e-9)
    }

    @Test
    fun `tight below the proportional floor`() {
        // L=140 → band low 5.6; a 5 m gap is too tight (was: fixed 8 m floor).
        assertEquals(GapFlag.TIGHT, GapAnalysis.analyze(listOf("7i" to 140.0, "8i" to 135.0)).single().flag)
    }

    @Test
    fun `wide above the proportional ceiling`() {
        // L=201 → band high min(20, 20.1) = 20 (cap binds); 21 m is a hole.
        val gap = GapAnalysis.analyze(listOf("D" to 201.0, "3W" to 180.0)).single()
        assertEquals(GapFlag.WIDE, gap.flag)
        assertEquals(20.0, gap.bandHighM, 1e-9)
    }

    @Test
    fun `inverted - shorter club median reaches the longer club`() {
        val gap = GapAnalysis.analyze(listOf("7i" to 140.0, "8i" to 143.0)).single()
        assertEquals(GapFlag.INVERTED, gap.flag)
        assertEquals(3.0, gap.gapM, 1e-9)
        // Exactly equal medians also read as inverted (spec: shorter ≥ longer).
        assertEquals(GapFlag.INVERTED, GapAnalysis.analyze(listOf("7i" to 140.0, "8i" to 140.0)).single().flag)
    }

    @Test
    fun `single club - no gaps, partial bag - only mapped clubs passed in`() {
        assertTrue(GapAnalysis.analyze(listOf("7i" to 140.0)).isEmpty())
        assertTrue(GapAnalysis.analyze(emptyList()).isEmpty())
        // 9i at 128 → gap 5 < 5.32 (4% of 133) → TIGHT; 200 m club → 67 m hole → WIDE.
        val gaps = GapAnalysis.analyze(listOf("D" to 200.0, "8i" to 133.0, "9i" to 128.0))
        assertEquals(2, gaps.size)
        assertEquals(GapFlag.WIDE, gaps[0].flag)
        assertEquals(GapFlag.TIGHT, gaps[1].flag)
    }

    // --- analyzeByClubIdentity (2026-10-16: caller fed the persisted test
    // order — short→long for WEDGE_FIRST — so every gap read INVERTED) ------

    /** Live-tablet medians (2026-10-16 session), in WEDGE_FIRST test order. */
    private val liveMedians = listOf(
        Triple("LW", ClubType.WEDGE, 76.0),
        Triple("SW", ClubType.WEDGE, 90.0),
        Triple("GW", ClubType.WEDGE, 112.0),
        Triple("PW", ClubType.WEDGE, 121.0),
        Triple("9i", ClubType.IRON, 129.0),
        Triple("8i", ClubType.IRON, 149.0),
        Triple("7i", ClubType.IRON, 164.0),
        Triple("6i", ClubType.IRON, 164.0),
        Triple("5i", ClubType.IRON, 172.0),
        Triple("4i", ClubType.IRON, 180.0),
        Triple("3i", ClubType.IRON, 189.0),
        Triple("5w", ClubType.WOOD, 211.0),
        Triple("D", ClubType.DRIVER, 237.0),
    )

    /**
     * The device dump's chained gaps (13.8, 21.7, 9.5, 7.8, 19.9, 10.9, 5.8,
     * 9.9, 4.4, 9.2, 21.6) back-solved to consistent medians — these differ
     * slightly from the rounded "~164/~172" approximations but reproduce the
     * dump's verdicts exactly: 5 good, 2 too tight, 4 big gap.
     */
    private val deviceMedians = listOf(
        Triple("LW", ClubType.WEDGE, 76.5),
        Triple("SW", ClubType.WEDGE, 90.3),
        Triple("GW", ClubType.WEDGE, 112.0),
        Triple("PW", ClubType.WEDGE, 121.5),
        Triple("9i", ClubType.IRON, 129.3),
        Triple("8i", ClubType.IRON, 149.2),
        Triple("7i", ClubType.IRON, 160.1),
        Triple("6i", ClubType.IRON, 165.9),
        Triple("5i", ClubType.IRON, 175.8),
        Triple("4i", ClubType.IRON, 180.2),
        Triple("3i", ClubType.IRON, 189.4),
        Triple("5w", ClubType.WOOD, 211.0),
        Triple("D", ClubType.DRIVER, 237.0),
    )

    @Test
    fun `club-identity analysis is independent of input order`() {
        // Tie-free variant: exact median ties leave the name order to the
        // stable sort, which IS input-order dependent — but flags and gaps
        // are not, see the live test.
        val tieFree = liveMedians.map { (n, t, m) -> Triple(n, t, if (n == "6i") 163.0 else m) }
        val wedgeFirst = GapAnalysis.analyzeByClubIdentity(tieFree)
        val driverFirst = GapAnalysis.analyzeByClubIdentity(tieFree.asReversed())
        val shuffled = GapAnalysis.analyzeByClubIdentity(
            listOf(tieFree[7], tieFree[0], tieFree[12], tieFree[3], tieFree[9],
                tieFree[2], tieFree[11], tieFree[5], tieFree[1], tieFree[10],
                tieFree[4], tieFree[8], tieFree[6]),
        )
        assertEquals(wedgeFirst, driverFirst)
        assertEquals(wedgeFirst, shuffled)
    }

    @Test
    fun `live session medians - driver excluded, chain stops at 5w`() {
        val gaps = GapAnalysis.analyzeByClubIdentity(liveMedians)
        // D is a specialist tee club — no gap line involves it. The chain's
        // top pair is the longest two non-driver clubs.
        assertTrue(gaps.none { it.longerClub == "D" || it.shorterClub == "D" })
        assertEquals("5w", gaps.first().longerClub)
        assertEquals("3i", gaps.first().shorterClub)
        // Integer-median fixture (5w 211 → 3i 189 … SW 90 → LW 76):
        // 5w–3i 22 WIDE, 3i–4i 9 GOOD, 4i–5i 8 GOOD, 5i–7i 8 GOOD,
        // 7i–6i 0 INVERTED, 6i–8i 15 GOOD, 8i–9i 20 WIDE (> 14.9 band),
        // 9i–PW 8 GOOD, PW–GW 9 GOOD, GW–SW 22 WIDE, SW–LW 14 WIDE (> 9.0).
        assertEquals(listOf(
            GapFlag.WIDE, GapFlag.HEALTHY, GapFlag.HEALTHY, GapFlag.HEALTHY,
            GapFlag.INVERTED, GapFlag.HEALTHY, GapFlag.WIDE, GapFlag.HEALTHY,
            GapFlag.HEALTHY, GapFlag.WIDE, GapFlag.WIDE,
        ), gaps.map { it.flag })
        assertEquals(11, gaps.size)
        assertEquals(22.0, gaps.first().gapM, 1e-9)
        assertEquals("SW", gaps.last().longerClub)
        assertEquals("LW", gaps.last().shorterClub)
        assertEquals(14.0, gaps.last().gapM, 1e-9)
    }

    @Test
    fun `live device-dump gaps - 5 good, 2 too tight, 4 big gap, ending at 3i - 5w`() {
        val gaps = GapAnalysis.analyzeByClubIdentity(deviceMedians)
        // Shortest first (card order = analyze's long→short order reversed),
        // ending at 3i – 5w (driver excluded).
        assertEquals(11, gaps.size)
        assertEquals(
            listOf(
                "LW" to "SW", "SW" to "GW", "GW" to "PW", "PW" to "9i", "9i" to "8i",
                "8i" to "7i", "7i" to "6i", "6i" to "5i", "5i" to "4i", "4i" to "3i", "3i" to "5w",
            ),
            gaps.asReversed().map { it.shorterClub to it.longerClub },
        )
        // Flags and gaps for the device dump (longer → shorter):
        // LW–SW 13.8 WIDE, SW–GW 21.7 WIDE, GW–PW 9.5 GOOD, PW–9i 7.8 GOOD,
        // 9i–8i 19.9 WIDE, 8i–7i 10.9 GOOD, 7i–6i 5.8 TIGHT, 6i–5i 9.9 GOOD,
        // 5i–4i 4.4 TIGHT, 4i–3i 9.2 GOOD, 3i–5w 21.6 WIDE.
        assertEquals(
            listOf(
                GapFlag.WIDE, GapFlag.WIDE, GapFlag.HEALTHY, GapFlag.HEALTHY, GapFlag.WIDE,
                GapFlag.HEALTHY, GapFlag.TIGHT, GapFlag.HEALTHY, GapFlag.TIGHT, GapFlag.HEALTHY,
                GapFlag.WIDE,
            ),
            gaps.asReversed().map { it.flag },
        )
        assertEquals(13.8, gaps.last().gapM, 1e-9)
        assertEquals(21.6, gaps.first().gapM, 1e-9)
        assertEquals(3.612, gaps.last().bandLowM, 1e-9)  // 4% of 90.3
        assertEquals(9.03, gaps.last().bandHighM, 1e-9)
        assertEquals(20.0, gaps.first().bandHighM, 1e-9) // cap binds at L=211
    }

    @Test
    fun `driver exclusion - driver dropped wherever it appears in the input`() {
        // Driver first (driver-first test order)…
        val driverFirst = GapAnalysis.analyzeByClubIdentity(deviceMedians)
        assertTrue(driverFirst.none { it.longerClub == "D" || it.shorterClub == "D" })
        // …and alone with two irons: the chain is 8i – 9i, no D line.
        val onlyDriverAndIrons = GapAnalysis.analyzeByClubIdentity(
            listOf(Triple("D", ClubType.DRIVER, 237.0), Triple("8i", ClubType.IRON, 149.0), Triple("9i", ClubType.IRON, 129.0)),
        )
        assertEquals(listOf("9i" to "8i"), onlyDriverAndIrons.map { it.shorterClub to it.longerClub })
        // A lone driver produces no gaps at all.
        assertTrue(GapAnalysis.analyzeByClubIdentity(listOf(Triple("D", ClubType.DRIVER, 237.0))).isEmpty())
    }

    @Test
    fun `club-identity ordering - type beats carry, carry breaks type ties`() {
        // A wedge carrying 230 is still ordered long→short by TYPE: a 130 m
        // iron goes before (longer than) a 230 m wedge.
        val gaps = GapAnalysis.analyzeByClubIdentity(
            listOf(Triple("LW", ClubType.WEDGE, 230.0), Triple("8i", ClubType.IRON, 130.0)),
        )
        assertEquals(1, gaps.size)
        assertEquals("8i", gaps.single().longerClub)
        assertEquals("LW", gaps.single().shorterClub)
        // Within one type the LONGER median comes first (driver-first).
        val withinType = GapAnalysis.orderedByBag(
            listOf(Triple("PW", ClubType.WEDGE, 121.0), Triple("GW", ClubType.WEDGE, 112.0)),
        )
        assertEquals(listOf("PW" to 121.0, "GW" to 112.0), withinType)
    }

    // --- gapLine wording (round 6: verdict-aware rounding + per-pair band) --

    private fun entry(
        longer: String,
        shorter: String,
        gapM: Double,
        flag: GapFlag,
        bandLowM: Double,
        bandHighM: Double,
    ) = GapEntry(
        longerClub = longer, shorterClub = shorter, gapM = gapM, flag = flag,
        bandLowM = bandLowM, bandHighM = bandHighM,
    )

    @Test
    fun `gapLine - whole-metre gap formats without decimal`() {
        assertEquals("LW – SW  14 m · good", gapLine(entry("SW", "LW", 14.0, GapFlag.HEALTHY, 8.0, 20.0)))
    }

    @Test
    fun `gapLine - rounding survives the verdict at the device-dump gaps`() {
        // 13.8 → 14 stays WIDE → whole metres; 21.7 → 22 stays WIDE.
        assertEquals(
            "LW – SW  14 m · big gap (aim 4–9 m)",
            gapLine(entry("SW", "LW", 13.8, GapFlag.WIDE, 3.612, 9.03)),
        )
        assertEquals(
            "SW – GW  22 m · big gap (aim 4–11 m)",
            gapLine(entry("GW", "SW", 21.7, GapFlag.WIDE, 4.48, 11.2)),
        )
    }

    @Test
    fun `gapLine - rounding that would flip the verdict keeps one decimal`() {
        // L=140 → band 5.6–14.0. Gap 5.7 is TIGHT, but rounds to 6 (healthy)
        // — the decimal is required so number and word agree.
        assertEquals(
            "B – A  5.7 m · too tight (aim 6–14 m)",
            gapLine(entry("A", "B", 5.7, GapFlag.TIGHT, 5.6, 14.0)),
        )
        // Gap 14.4 is WIDE but rounds to 14 (healthy) — decimal again.
        assertEquals(
            "B – A  14.4 m · big gap (aim 6–14 m)",
            gapLine(entry("A", "B", 14.4, GapFlag.WIDE, 5.6, 14.0)),
        )
    }

    @Test
    fun `gapLine - good lines stay terse, no band suffix`() {
        assertEquals("GW – PW  10 m · good", gapLine(entry("PW", "GW", 9.5, GapFlag.HEALTHY, 4.86, 12.15)))
    }

    @Test
    fun `gapLine - inverted wording names the shorter club as the anomaly, no band`() {
        assertEquals(
            "6i – 7i  0 m · inverted — 6i carries as far as 7i",
            gapLine(entry("7i", "6i", 0.0, GapFlag.INVERTED, 6.636, 16.59)),
        )
        assertEquals(
            "8i – 9i  2.3 m · inverted — 8i carries further than 9i",
            gapLine(entry("9i", "8i", 2.3, GapFlag.INVERTED, 5.968, 14.92)),
        )
    }
}
