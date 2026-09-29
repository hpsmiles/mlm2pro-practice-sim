package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.physics.FlightCrossing
import com.hpsmiles.golfsim.core.physics.ReferenceTrajectory
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import kotlin.math.floor

/**
 * Break-the-Pane geometry, pure config + crossing math (spec section 5).
 * Pane floats at y = 25% of target, centred on the aim line. Cells are
 * 2% of target tall / 4% wide; the middle row is centred on the
 * ReferenceTrajectory crossing height so all rows stay reachable by flighting.
 *
 * Cell indices: row (bottom-up) * 3 + col (left-right) -> 0..8, middle = 4.
 * Boundary rule: inclusive upward -- a point exactly on a row top belongs to
 * the row above; exactly on a col edge belongs to the col right of it
 * (floor semantics on the cell-local coordinate).
 */
class PaneGeom(val targetM: Double) {

    val planeYM: Double = 0.25 * targetM
    val cellHM: Double = 0.02 * targetM
    val cellWM: Double = 0.04 * targetM
    val zRefM: Double = ReferenceTrajectory.paneCrossingHeightM(targetM)
    val bottomZM: Double = zRefM - 1.5 * cellHM
    val topZM: Double = bottomZM + 3 * cellHM
    val leftXM: Double = -1.5 * cellWM
    val rightXM: Double = 1.5 * cellWM

    /** Cell index for a crossing point, or null when outside the pane. */
    fun cellAt(xM: Double, zM: Double): Int? {
        val col = floor((xM - leftXM) / cellWM).toInt()
        val row = floor((zM - bottomZM) / cellHM).toInt()
        if (col !in 0..2 || row !in 0..2) return null
        return row * 3 + col
    }

    /**
     * First forward crossing of the pane plane -- reported even when outside
     * the pane ([PaneCrossing.cell] null) so the caller can say under/over/left/right.
     */
    fun firstCrossing(samples: List<TrajectorySample>): PaneCrossing? =
        FlightCrossing.firstForwardCrossing(samples, planeYM)?.let { (x, z) ->
            PaneCrossing(x, z, cellAt(x, z))
        }

    /** 4 world corners (x, y=plane, z), order BL -> BR -> TR -> TL. */
    fun cellCorners(cell: Int): List<Triple<Double, Double, Double>> {
        val row = cell / 3
        val col = cell % 3
        val xL = leftXM + col * cellWM
        val zB = bottomZM + row * cellHM
        val zT = zB + cellHM
        return listOf(
            Triple(xL, planeYM, zB),
            Triple(xL + cellWM, planeYM, zB),
            Triple(xL + cellWM, planeYM, zT),
            Triple(xL, planeYM, zT),
        )
    }
}

data class PaneCrossing(val xM: Double, val zM: Double, val cell: Int?)
