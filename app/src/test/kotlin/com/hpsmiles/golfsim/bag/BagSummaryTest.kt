package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BagSummaryTest {

    private fun entry(longer: String, shorter: String, gapM: Double, flag: GapFlag) =
        GapEntry(longerClub = longer, shorterClub = shorter, gapM = gapM, flag = flag,
            bandLowM = 0.0, bandHighM = 0.0)

    @Test
    fun `average, closest, farthest and the counts`() {
        val gaps = listOf(
            entry("7i", "6i", 6.0, GapFlag.TIGHT),
            entry("6i", "5i", 10.0, GapFlag.HEALTHY),
            entry("5i", "4i", 4.0, GapFlag.TIGHT),
            entry("4i", "3i", 22.0, GapFlag.WIDE),
            entry("3i", "5w", 12.0, GapFlag.HEALTHY),
        )
        val summary = summarizeGaps(gaps)
        assertEquals(10.8, summary.averageGapM!!, 1e-9)
        assertEquals(4.0, summary.closest!!.gapM, 1e-9)
        assertEquals("4i", summary.closest!!.shorterClub)
        assertEquals(22.0, summary.farthest!!.gapM, 1e-9)
        assertEquals("4i", summary.farthest!!.longerClub)
        assertEquals(2, summary.goodCount)
        assertEquals(1, summary.wideCount)
        assertEquals(2, summary.tightCount)
    }

    @Test
    fun `no gaps yields a null-bearing summary`() {
        val summary = summarizeGaps(emptyList())
        assertNull(summary.averageGapM)
        assertNull(summary.closest)
        assertNull(summary.farthest)
        assertEquals(0, summary.goodCount)
        assertEquals(0, summary.wideCount)
        assertEquals(0, summary.tightCount)
    }

    @Test
    fun `inverted gaps are counted in no bucket`() {
        val summary = summarizeGaps(listOf(entry("7i", "6i", 0.0, GapFlag.INVERTED)))
        assertEquals(0, summary.goodCount + summary.wideCount + summary.tightCount)
    }
}
