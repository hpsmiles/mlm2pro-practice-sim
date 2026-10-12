package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie
import com.hpsmiles.golfsim.core.scoring.SkillBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance gate (spec §6): stored 2026-10-12 session must solve to the owner's anchors. */
class CombineScoringGateTest {

    private val approach = CombineCatalog.byId("APPROACH")!!

    // Session 2026-10-12 20:24 — (shotIndex, restLie, restDistanceM) per shot, as stored.
    internal val sessionFacts = listOf(
        CombineShotFacts(0, Lie.GREEN, 5.7467795104),
        CombineShotFacts(1, Lie.GREEN, 2.5404337667),
        CombineShotFacts(2, Lie.GREEN, 1.1771882365),
        CombineShotFacts(3, Lie.GREEN, 4.8562422345),
        CombineShotFacts(4, Lie.FAIRWAY, 100.4606075935),
        CombineShotFacts(5, Lie.FAIRWAY, 104.4336680986),
        CombineShotFacts(6, Lie.GREEN, 6.8259315358),
        CombineShotFacts(7, Lie.GREEN, 5.8023789631),
        CombineShotFacts(8, Lie.GREEN, 12.7218677946),
        CombineShotFacts(9, Lie.GREEN, 6.9508719257),
        CombineShotFacts(10, Lie.ROUGH, 111.7846085790),
        CombineShotFacts(11, Lie.FAIRWAY, 99.8080235322),
        CombineShotFacts(12, Lie.GREEN, 8.7002514811),
        CombineShotFacts(13, Lie.GREEN, 2.8138289139),
        CombineShotFacts(14, Lie.GREEN, 5.7762255659),
        CombineShotFacts(15, Lie.GREEN, 9.0652470737),
        CombineShotFacts(16, Lie.ROUGH, 111.2628087007),
        CombineShotFacts(17, Lie.FAIRWAY, 94.7890870845),
    )

    @Test
    fun `calibration session solves to declared anchors`() {
        val results = combineResults(approach, sessionFacts)
        assertEquals(3.0, results.stations[0].estimate.est, 1.0)  // 100 m
        assertEquals(-1.0, results.stations[1].estimate.est, 1.0) // 150 m
        assertEquals(8.0, results.stations[2].estimate.est, 1.0)  // DRIVER
    }

    @Test
    fun `calibration session overall is emergent 2_5`() {
        assertEquals(2.5, combineResults(approach, sessionFacts).overall.est, 1.0)
    }

    @Test
    fun `calibration session locks to scratch`() {
        assertEquals(SkillBand.SCRATCH, combineResults(approach, sessionFacts).lockedBand)
    }

    @Test
    fun `wedges combine still produces finite results`() {
        val wedges = CombineCatalog.byId("WEDGES")!!
        val facts = (0 until 18).map { i ->
            CombineShotFacts(i, if (i % 2 == 0) Lie.GREEN else Lie.FAIRWAY, if (i % 2 == 0) 4.0 else 20.0)
        }
        val results = combineResults(wedges, facts)
        assertEquals(3, results.stations.size)
        results.stations.forEach { assertTrue(it.estimate.est.isFinite()) }
        assertTrue(results.overall.est.isFinite())
    }
}
