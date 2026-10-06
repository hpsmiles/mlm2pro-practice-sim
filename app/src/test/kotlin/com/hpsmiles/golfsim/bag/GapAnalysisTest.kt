package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden cases for adjacent-club gap flags (spec §6: tight <8, healthy 8–20, wide >20, inverted). */
class GapAnalysisTest {

    @Test
    fun `healthy gap`() {
        val gaps = GapAnalysis.analyze(listOf("D" to 200.0, "3W" to 190.0))
        assertEquals(1, gaps.size)
        assertEquals(GapFlag.HEALTHY, gaps.single().flag)
        assertEquals(10.0, gaps.single().gapM, 1e-9)
        assertEquals("D", gaps.single().longerClub)
        assertEquals("3W", gaps.single().shorterClub)
    }

    @Test
    fun `tight below eight metres`() {
        assertEquals(GapFlag.TIGHT, GapAnalysis.analyze(listOf("7i" to 140.0, "8i" to 133.0)).single().flag)
    }

    @Test
    fun `boundaries - exactly 8 and exactly 20 are healthy`() {
        assertEquals(GapFlag.HEALTHY, GapAnalysis.analyze(listOf("A" to 148.0, "B" to 140.0)).single().flag)
        assertEquals(GapFlag.HEALTHY, GapAnalysis.analyze(listOf("A" to 160.0, "B" to 140.0)).single().flag)
    }

    @Test
    fun `wide over twenty metres`() {
        assertEquals(GapFlag.WIDE, GapAnalysis.analyze(listOf("D" to 201.0, "3W" to 180.0)).single().flag)
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
        val gaps = GapAnalysis.analyze(listOf("D" to 200.0, "8i" to 133.0, "9i" to 126.0))
        assertEquals(2, gaps.size)
        assertEquals(GapFlag.WIDE, gaps[0].flag)
        assertEquals(GapFlag.TIGHT, gaps[1].flag)
    }
}
