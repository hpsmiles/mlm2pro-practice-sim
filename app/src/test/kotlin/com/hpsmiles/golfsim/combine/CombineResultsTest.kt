package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.data.CombineShotEntity
import com.hpsmiles.golfsim.core.scoring.HandicapEstimate
import com.hpsmiles.golfsim.core.scoring.Lie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombineResultsTest {

    private val approach = CombineCatalog.byId("APPROACH")!!

    @Test
    fun `golden fixture reproduces at the app layer`() {
        // 100 m station (indices 0,1,6,7,12,13), rests 1,6,7,8,10,12 m, all on green
        val rests = listOf(1.0, 6.0, 7.0, 8.0, 10.0, 12.0)
        val indices = listOf(0, 1, 6, 7, 12, 13)
        val facts = indices.zip(rests).map { (index, rest) ->
            CombineShotFacts(index, Lie.GREEN, rest)
        }
        val results = combineResults(approach, facts)
        assertEquals(1, results.stations.size)
        assertEquals(0, results.stations[0].stationIndex)
        assertEquals("100 m", results.stations[0].label)
        assertEquals(-5.84, results.overall.est, 0.05)
        assertTrue(results.overall.clampedLow)
        assertEquals(listOf("TOUR+", "SCR", "SCR", "SCR", "≈3", "≈7"), results.stations[0].chips)
        assertEquals("AVG TO PIN", results.stations[0].avgLabel)
        assertEquals(rests.average(), results.stations[0].avgValue, 1e-9)
    }

    @Test
    fun `facts partition across multiple stations`() {
        val station0 = listOf(0, 1, 6, 7, 12, 13).map { CombineShotFacts(it, Lie.GREEN, 5.0) }
        val station1 = listOf(2, 3, 8, 9, 14, 15).map { CombineShotFacts(it, Lie.FAIRWAY, 20.0) }
        val results = combineResults(approach, station0 + station1)

        assertEquals(2, results.stations.size)
        assertEquals(0, results.stations[0].stationIndex)
        assertEquals(1, results.stations[1].stationIndex)
        assertEquals("100 m", results.stations[0].label)
        assertEquals("150 m", results.stations[1].label)

        // Partition consistency: each station's estimate derives only from its own shots.
        assertEquals(
            combineResults(approach, station0).stations.single().estimate.est,
            results.stations[0].estimate.est,
            1e-9,
        )
        assertEquals(
            combineResults(approach, station1).stations.single().estimate.est,
            results.stations[1].estimate.est,
            1e-9,
        )
    }

    @Test
    fun `combineResults is order independent`() {
        val rests = listOf(1.0, 6.0, 7.0, 8.0, 10.0, 12.0)
        val indices = listOf(0, 1, 6, 7, 12, 13)
        val facts = indices.zip(rests).map { (index, rest) -> CombineShotFacts(index, Lie.GREEN, rest) }

        val forward = combineResults(approach, facts)
        val reversed = combineResults(approach, facts.reversed())

        assertEquals(forward.overall.est, reversed.overall.est, 0.0)
        assertEquals(forward.stations[0].chips, reversed.stations[0].chips)
        assertEquals(forward.stations[0].avgValue, reversed.stations[0].avgValue, 0.0)
    }

    @Test
    fun `overallEstimate matches combineResults overall`() {
        val facts = listOf(0, 1, 6, 7, 12, 13).map { CombineShotFacts(it, Lie.GREEN, 5.0) }
        assertEquals(combineResults(approach, facts).overall.est, overallEstimate(approach, facts), 0.0)
    }

    @Test
    fun `entity toFacts parses a garbage lie to fairway`() {
        val entity = CombineShotEntity(
            id = 0L, sessionId = 1L, shotIndex = 0, stationIndex = 0,
            restLie = "ZGARBAGE", restDistanceM = 5.0,
        )
        assertEquals(Lie.FAIRWAY, entity.toFacts().restLie)
    }

    @Test
    fun `driver average is distance hit toward the baseline`() {
        val indices = listOf(4, 5, 10, 11, 16, 17) // DRIVER station indices
        val facts = indices.map { CombineShotFacts(it, Lie.FAIRWAY, 41.23) }
        val results = combineResults(approach, facts)
        assertEquals(1, results.stations.size)
        assertEquals("AVG TOTAL", results.stations[0].avgLabel)
        assertEquals(340.0 - 41.23, results.stations[0].avgValue, 1e-6)
    }

    @Test
    fun `formats follow the display rules`() {
        assertEquals("tour-or-better", CombineFormats.formatHc(-5.5))
        assertEquals("+1.3", CombineFormats.formatHc(-1.3))
        assertEquals("6.2", CombineFormats.formatHc(6.2))
        assertEquals("25+", CombineFormats.formatHc(26.0))
        assertEquals("TOUR+", CombineFormats.chip(-6.0))
        assertEquals("SCR", CombineFormats.chip(-2.0))
        assertEquals("≈7", CombineFormats.chip(7.2))
    }

    @Test
    fun `record compare is lower-is-better`() {
        assertEquals(CombineRecordOutcome.FIRST_SCORE, CombineRecord.compare(null, 6.0).outcome)
        assertEquals(CombineRecordOutcome.NEW_RECORD, CombineRecord.compare(8.9, 6.0).outcome)
        assertEquals(CombineRecordOutcome.OFF_RECORD, CombineRecord.compare(5.0, 6.0).outcome)
        assertEquals(2.9, CombineRecord.compare(8.9, 6.0).delta, 1e-9)
    }

    // ---- Sanctioned extra coverage from T9 quality review ----

    @Test
    fun `formatHcWithUncertainty covers both branches`() {
        val noBand = HandicapEstimate(
            est = 6.2, low = null, high = null,
            perBandSg = emptyMap(), clampedLow = false, clampedHigh = false,
        )
        assertEquals("≈ 6.2", CombineFormats.formatHcWithUncertainty(noBand))

        val banded = HandicapEstimate(
            est = 6.2, low = 2.0, high = 10.4,
            perBandSg = emptyMap(), clampedLow = false, clampedHigh = false,
        )
        assertEquals("≈ 6.2 ± 4", CombineFormats.formatHcWithUncertainty(banded))
    }

    @Test
    fun `record headline and detail cover all three outcomes`() {
        val first = CombineRecord.compare(null, 6.0)
        assertEquals("FIRST SCORE", CombineRecord.headline(first))
        assertEquals("First completed combine", CombineRecord.detail(first))

        val newRecord = CombineRecord.compare(8.9, 6.0)
        assertEquals("NEW RECORD", CombineRecord.headline(newRecord))
        assertEquals("Previous best 8.9", CombineRecord.detail(newRecord))

        val off = CombineRecord.compare(5.0, 6.0)
        assertEquals("OFF YOUR BEST", CombineRecord.headline(off))
        assertEquals("Best 5.0 · 1.0 off", CombineRecord.detail(off))
    }

    @Test
    fun `formatHc exact boundaries`() {
        assertEquals("+5.0", CombineFormats.formatHc(-5.0))
        assertEquals("0.0", CombineFormats.formatHc(0.0))
        assertEquals("25.0", CombineFormats.formatHc(25.0))
        assertEquals("25+", CombineFormats.formatHc(25.1))
    }

    @Test
    fun `chip exact boundaries`() {
        assertEquals("TOUR+", CombineFormats.chip(-5.1))
        assertEquals("SCR", CombineFormats.chip(-5.0))
        assertEquals("≈0", CombineFormats.chip(0.0))
        assertEquals("≈25", CombineFormats.chip(25.0))
        assertEquals("25+", CombineFormats.chip(25.1))
    }

    @Test
    fun `chip rounds half up`() {
        assertEquals("≈8", CombineFormats.chip(7.5))
    }

    @Test
    fun `record factory corners`() {
        val first = CombineRecord.compare(null, 6.0)
        assertEquals(CombineRecordOutcome.FIRST_SCORE, first.outcome)
        assertNull(first.best)
        assertEquals(0.0, first.delta, 1e-9)

        val off = CombineRecord.compare(5.0, 6.0)
        assertEquals(CombineRecordOutcome.OFF_RECORD, off.outcome)
        assertEquals(5.0, off.best!!, 1e-9)
        assertEquals(1.0, off.delta, 1e-9)
    }
}
