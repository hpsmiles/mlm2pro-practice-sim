package com.hpsmiles.golfsim.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SgTablesDataTest {

    @Test
    fun `grid lengths match the source series 1`() {
        assertEquals(60, SgTables.PUTT_DISTANCES.size)
        assertEquals(120, SgTables.FAIRWAY_DISTANCES.size)
        assertEquals(120, SgTables.ROUGH_DISTANCES.size)
        assertEquals(120, SgTables.TEE_DISTANCES.size)
    }

    @Test
    fun `grid endpoints match the source`() {
        assertEquals(0.3, SgTables.PUTT_DISTANCES.first(), 1e-9)
        assertEquals(18.29, SgTables.PUTT_DISTANCES.last(), 1e-9)
        assertEquals(4.6, SgTables.FAIRWAY_DISTANCES.first(), 1e-9)
        assertEquals(548.6, SgTables.FAIRWAY_DISTANCES.last(), 1e-9)
        assertEquals(91.0, SgTables.TEE_DISTANCES.first(), 1e-9)
        assertEquals(636.0, SgTables.TEE_DISTANCES.last(), 1e-9)
    }

    @Test
    fun `distance grids ascend strictly`() {
        for (grid in listOf(
            SgTables.PUTT_DISTANCES, SgTables.FAIRWAY_DISTANCES,
            SgTables.ROUGH_DISTANCES, SgTables.TEE_DISTANCES,
        )) {
            for (i in 1 until grid.size) {
                assertTrue("grid[$i] not ascending", grid[i] > grid[i - 1])
            }
        }
    }

    @Test
    fun `values are finite and band ordering holds at every point`() {
        val perLie = mapOf(
            SgTables.FAIRWAY_DISTANCES to listOf(
                SgTables.FAIRWAY_TOUR, SgTables.FAIRWAY_SCRATCH,
                SgTables.FAIRWAY_HCP10, SgTables.FAIRWAY_HCP18,
            ),
            SgTables.PUTT_DISTANCES to listOf(
                SgTables.PUTT_TOUR, SgTables.PUTT_SCRATCH,
                SgTables.PUTT_HCP10, SgTables.PUTT_HCP18,
            ),
            SgTables.ROUGH_DISTANCES to listOf(
                SgTables.ROUGH_TOUR, SgTables.ROUGH_SCRATCH,
                SgTables.ROUGH_HCP10, SgTables.ROUGH_HCP18,
            ),
            SgTables.TEE_DISTANCES to listOf(
                SgTables.TEE_TOUR, SgTables.TEE_SCRATCH,
                SgTables.TEE_HCP10, SgTables.TEE_HCP18,
            ),
        )
        for ((grid, bands) in perLie) {
            for (band in bands) assertEquals(grid.size, band.size)
            for (i in grid.indices) {
                val v = bands.map { it[i] }
                v.forEach { assertTrue("non-finite value", it.isFinite()) }
                assertTrue("tour <= scratch at $i", v[0] <= v[1])
                assertTrue("scratch <= hcp10 at $i", v[1] <= v[2])
                assertTrue("hcp10 <= hcp18 at $i", v[2] <= v[3])
            }
        }
    }

    @Test
    fun `exact source rows are preserved`() {
        // tour/fairway 91.4 m (100 y) is an exact CSV row
        val index = SgTables.FAIRWAY_DISTANCES.indexOfFirst { it == 91.4 }
        assertTrue(index >= 0)
        assertEquals(2.80, SgTables.FAIRWAY_TOUR[index], 1e-9)
    }
}
