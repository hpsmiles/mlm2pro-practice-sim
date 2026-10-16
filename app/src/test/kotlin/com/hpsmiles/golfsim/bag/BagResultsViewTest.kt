package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagResultsViewTest {

    private fun shot(carryM: Double, totalM: Double) = BagMappingShotEntity(
        sessionId = 1L,
        clubName = "7i",
        clubType = "IRON",
        timestampMs = 0L,
        clubHeadSpeedMps = 35.0,
        ballSpeedMps = 45.0,
        launchAngleDeg = 20.0,
        launchDirDeg = 0.0,
        spinAxisDeg = 0.0,
        totalSpinRpm = 6000,
        carryM = carryM,
        totalM = totalM,
    )

    @Test
    fun `distanceFor selects the persisted cache for the basis`() {
        val s = shot(carryM = 140.0, totalM = 152.0)
        assertEquals(140.0, s.distanceFor(DistanceBasis.CARRY), 1e-9)
        assertEquals(152.0, s.distanceFor(DistanceBasis.TOTAL), 1e-9)
    }

    @Test
    fun `view enum has side as the first entry`() {
        assertEquals(BagResultsView.SIDE, BagResultsView.entries.first())
    }

    @Test
    fun `an all-inverted gap list keeps the strip instead of the no-gaps message`() {
        // 2026-10-16 round 4 (finding A): the strip's empty state must gate on
        // the gap COUNT, not on the aggregates — an all-INVERTED session has
        // null aggregates but real (inverted) gaps, and the panel's gap block
        // renders red lines for them. A 2-club bag whose longer type carries
        // less: 3w 175 m, hybrid 180 m → one entry, gapM -5 → INVERTED.
        val gaps = listOf(
            GapEntry("3w", "hybrid", 5.0, GapFlag.INVERTED),
        )
        val summary = summarizeGaps(gaps)
        assertNull(summary.averageGapM) // inverted pairs never aggregate…
        assertFalse(summaryHasNoGaps(gaps.size)) // …but gaps exist → keep the strip
        assertTrue(summaryIsAllInverted(summary, gaps.size))
        // The genuinely-no-gaps case keeps the old message path.
        assertTrue(summaryHasNoGaps(0))
        assertFalse(summaryIsAllInverted(summarizeGaps(emptyList()), 0))
        // A healthy session is neither empty nor all-inverted.
        val healthy = listOf(GapEntry("7i", "6i", 8.0, GapFlag.HEALTHY))
        assertFalse(summaryIsAllInverted(summarizeGaps(healthy), healthy.size))
    }
}
