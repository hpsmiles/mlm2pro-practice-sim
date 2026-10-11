package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombineDispersionTest {

    private val approach = CombineCatalog.byId("APPROACH")!!

    @Test
    fun `group stats are hand-computed for the 100 m station`() {
        // Station 0 (100 m): shot indices 0,1,6,7. Finish points (lateral restX, down-range restY).
        val coords = listOf(
            0 to (0.0 to 100.0),
            1 to (2.0 to 104.0),
            6 to (-2.0 to 96.0),
            7 to (0.0 to 100.0),
        )
        val rests = listOf(5.0, 9.0, 7.0, 3.0)
        val facts = coords.mapIndexed { i, (index, xy) ->
            CombineShotFacts(index, Lie.GREEN, rests[i], xy.first, xy.second)
        }

        val groups = CombineDispersion.groups(approach, facts)
        assertEquals(1, groups.size)
        val g = groups.single()
        assertEquals(0, g.stationIndex)
        assertEquals(4, g.count)
        assertEquals(0.0, g.centroidX, 1e-9)
        assertEquals(100.0, g.centroidY, 1e-9)
        // Population variances: lateral 8/4 = 2, down-range 32/4 = 8.
        assertEquals(sqrt(2.0), g.sigmaX, 1e-9)
        assertEquals(sqrt(8.0), g.sigmaY, 1e-9)
        assertEquals(8.0, g.longShortSpreadM, 1e-9)
        assertEquals(4.0, g.leftRightSpreadM, 1e-9)
        assertEquals(6.0, g.avgRestM, 1e-9)
        assertTrue(g.hasRing)
    }

    @Test
    fun `a lone finish has no ring and coordinate-less shots are ignored`() {
        val facts = listOf(
            CombineShotFacts(0, Lie.GREEN, 5.0, 1.0, 99.0), // station 0, has coords
            CombineShotFacts(1, Lie.GREEN, 6.0, null, null), // station 0, no coords -> ignored
            CombineShotFacts(2, Lie.FAIRWAY, 20.0, null, null), // station 1, no coords -> no group
        )
        val groups = CombineDispersion.groups(approach, facts)
        assertEquals(1, groups.size)
        val g = groups.single()
        assertEquals(0, g.stationIndex)
        assertEquals(1, g.count)
        assertFalse(g.hasRing)
        assertEquals(1.0, g.centroidX, 1e-9)
        assertEquals(99.0, g.centroidY, 1e-9)
        assertEquals(0.0, g.sigmaX, 1e-9)
        assertEquals(0.0, g.sigmaY, 1e-9)
        assertEquals(0.0, g.longShortSpreadM, 1e-9)
        assertEquals(0.0, g.leftRightSpreadM, 1e-9)
        assertEquals(5.0, g.avgRestM, 1e-9)
    }

    @Test
    fun `no coordinates yields no groups`() {
        val facts = listOf(0, 1, 6, 7).map { CombineShotFacts(it, Lie.GREEN, 5.0) }
        assertTrue(CombineDispersion.groups(approach, facts).isEmpty())
    }

    @Test
    fun `groups are ascending by station index`() {
        val facts = listOf(
            CombineShotFacts(2, Lie.FAIRWAY, 20.0, 1.0, 149.0), // station 1
            CombineShotFacts(0, Lie.GREEN, 5.0, 0.5, 100.0), // station 0
            CombineShotFacts(4, Lie.FAIRWAY, 60.0, -3.0, 285.0), // driver station 2
        )
        val groups = CombineDispersion.groups(approach, facts)
        assertEquals(listOf(0, 1, 2), groups.map { it.stationIndex })
    }
}
