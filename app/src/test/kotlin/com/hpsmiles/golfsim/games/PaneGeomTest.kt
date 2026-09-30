package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.physics.TrajectorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaneGeomTest {

    private val pane = PaneGeom(140.0)
    // 140 m: plane y=35, cellH=2.24, cellW=2.80, zRef~=12.5, bottom~=9.14.

    @Test
    fun `geometry scales with target`() {
        assertEquals(35.0, pane.planeYM, 1e-9)
        assertEquals(2.24, pane.cellHM, 1e-9)
        assertEquals(2.80, pane.cellWM, 1e-9)
    }

    @Test
    fun `row boundaries inclusive upward`() {
        assertEquals(1, pane.cellAt(0.0, pane.bottomZM))                            // aim line, exact bottom
        assertEquals(1, pane.cellAt(0.0, pane.bottomZM + 0.5 * pane.cellHM))        // inside row 0
        assertEquals(4, pane.cellAt(0.0, pane.bottomZM + pane.cellHM + 1e-9))       // just above row 0 top -> row 1
        assertEquals(7, pane.cellAt(0.0, pane.bottomZM + 2 * pane.cellHM + 1e-9))   // just above row 1 top -> row 2
        assertEquals(7, pane.cellAt(0.0, pane.topZM - 1e-9))                        // just below top -> row 2
        assertNull(pane.cellAt(0.0, pane.topZM + 1e-9))                             // just above top -> outside
    }

    @Test
    fun `col boundaries inclusive rightward - aim line is middle col`() {
        assertEquals(4, pane.cellAt(0.0, pane.zRefM))
        assertEquals(3, pane.cellAt(pane.leftXM, pane.zRefM))              // exact left edge -> col 0
        assertNull(pane.cellAt(pane.rightXM, pane.zRefM))                  // exact right edge -> outside
    }

    @Test
    fun `reference crossing breaks middle cell`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.0, pane.planeYM, pane.zRefM, 1.0),
            TrajectorySample(0.0, 140.0, 0.0, 2.0),
        )
        val crossing = pane.firstCrossing(samples)!!
        assertEquals(4, crossing.cell) // middle row, middle col
    }

    @Test
    fun `crossing below pane reports point with null cell`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.0, 35.0, 2.0, 1.0),
            TrajectorySample(0.0, 100.0, 0.0, 2.0),
        )
        val crossing = pane.firstCrossing(samples)!!
        assertEquals(2.0, crossing.zM, 1e-9)
        assertNull(crossing.cell)
    }

    @Test
    fun `first forward crossing wins when samples dip through twice`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.0, 35.0, pane.zRefM, 1.0), // first plane crossing: middle row/col
            TrajectorySample(0.0, 36.0, pane.topZM + 1.0, 1.1), // rises steeply above the plane
            TrajectorySample(0.0, 37.0, pane.zRefM, 1.2), // continues forward - NOT a forward crossing
        )
        val crossing = pane.firstCrossing(samples)!!
        assertEquals(4, crossing.cell)
    }

    @Test
    fun `cell corners span the cell`() {
        val corners = pane.cellCorners(4) // middle
        assertEquals(4, corners.size)
        val (blx, bly, blz) = corners[0]
        assertEquals(pane.planeYM, bly, 1e-9)
        assertEquals(pane.bottomZM + pane.cellHM, blz, 1e-9) // middle row bottom = zRef - cellH/2
        assertEquals(-pane.cellWM / 2.0, blx, 1e-9)
        val (_, _, trz) = corners[2]
        assertEquals(pane.zRefM + pane.cellHM / 2.0, trz, 1e-9)
    }
}
