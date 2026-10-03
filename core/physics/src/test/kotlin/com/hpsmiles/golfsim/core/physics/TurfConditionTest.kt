package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurfConditionTest {

    @Test
    fun firmMatchesHistoricalRangeSurface() {
        // Default selection must be a no-op vs the shipped range surface.
        assertEquals(
            Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM),
            TurfCondition.FIRM.surface(),
        )
    }

    @Test
    fun firmerTurfRollsFartherAndBouncesMore() {
        val surfaces = listOf(
            TurfCondition.SOFT,
            TurfCondition.NORMAL,
            TurfCondition.FIRM,
            TurfCondition.LINKS,
        ).map { it.surface() }

        for (i in 0 until surfaces.size - 1) {
            val softer = surfaces[i]
            val firmer = surfaces[i + 1]
            assertTrue(
                "roll decel must fall as turf firms (${softer.name} vs ${firmer.name})",
                softer.rollDecelMps2 > firmer.rollDecelMps2,
            )
            assertTrue(
                "COR must rise as turf firms (${softer.name} vs ${firmer.name})",
                softer.cor < firmer.cor,
            )
        }
    }

    @Test
    fun unknownNameFallsBackToDefault() {
        assertEquals(TurfCondition.FIRM, TurfCondition.fromName(null))
        assertEquals(TurfCondition.FIRM, TurfCondition.fromName("nonsense"))
        assertEquals(TurfCondition.LINKS, TurfCondition.fromName("LINKS"))
    }
}
