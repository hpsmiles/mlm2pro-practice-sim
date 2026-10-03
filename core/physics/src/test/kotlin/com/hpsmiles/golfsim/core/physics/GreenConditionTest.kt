package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenConditionTest {

    @Test
    fun normalMatchesHistoricalGreenNormal() {
        // The default selection must be a no-op change vs the shipped surface.
        assertEquals(Surface.GREEN_NORMAL, GreenCondition.NORMAL.surface())
    }

    @Test
    fun firmerGreensRollFartherBounceMoreButGripLess() {
        val surfaces = listOf(
            GreenCondition.SOFT,
            GreenCondition.NORMAL,
            GreenCondition.SANDBELT,
            GreenCondition.ROYAL_MELBOURNE,
        ).map { it.surface() }

        for (i in 0 until surfaces.size - 1) {
            val softer = surfaces[i]
            val firmer = surfaces[i + 1]
            assertTrue(
                "roll decel must fall as greens firm (${softer.name} vs ${firmer.name})",
                softer.rollDecelMps2 > firmer.rollDecelMps2,
            )
            assertTrue(
                "COR must rise as greens firm (${softer.name} vs ${firmer.name})",
                softer.cor < firmer.cor,
            )
            assertTrue(
                "spin-back grip must fall as greens firm (${softer.name} vs ${firmer.name})",
                softer.spinbackScale > firmer.spinbackScale,
            )
        }
    }

    @Test
    fun unknownNameFallsBackToNormal() {
        assertEquals(GreenCondition.NORMAL, GreenCondition.fromName(null))
        assertEquals(GreenCondition.NORMAL, GreenCondition.fromName("nonsense"))
        assertEquals(GreenCondition.SANDBELT, GreenCondition.fromName("SANDBELT"))
    }
}
