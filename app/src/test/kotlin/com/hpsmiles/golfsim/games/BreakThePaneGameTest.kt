package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakThePaneGameTest {

    private val target = 140.0

    /**
     * Simulator seam: builds a ShotResult whose samples cross the pane plane
     * exactly once at (crossX, crossZ) and rest at (restX, restY).
     */
    private fun syntheticShot(crossX: Double, crossZ: Double, restX: Double, restY: Double): (LaunchConditions) -> ShotResult =
        syntheticShotFor(PaneGeom(target), crossX, crossZ, restX, restY)

    /** Same seam, but the samples cross the plane of the given PaneGeom (custom distances). */
    private fun syntheticShotFor(
        pane: PaneGeom,
        crossX: Double,
        crossZ: Double,
        restX: Double,
        restY: Double,
    ): (LaunchConditions) -> ShotResult =
        { _ ->
            ShotResult(
                carryM = target, rolloutM = 1.0, totalM = target + 1.0, sideM = restX,
                apexM = crossZ + 10.0, flightTimeSec = 6.0,
                samples = listOf(
                    TrajectorySample(0.0, 0.0, 0.0, 0.0),
                    TrajectorySample(crossX * 0.5, pane.planeYM * 0.5, crossZ * 0.5, 1.0),
                    TrajectorySample(crossX, pane.planeYM, crossZ, 2.0),
                    TrajectorySample(restX, restY, 0.0, 6.0),
                ),
                restX = restX, restY = restY,
            )
        }

    private fun newGame(crossX: Double, crossZ: Double, restX: Double, restY: Double): BreakThePaneGame {
        val game = BreakThePaneGame()
        game.simulator = syntheticShot(crossX, crossZ, restX, restY)
        game.start(target)
        return game
    }

    private fun ball() = BallData(33.0, 46.0, 0.0, 21.0, 0.0, 7500, 5, 10)

    @Test
    fun `cell breaks when crossing unbroken cell and resting on green`() {
        val game = newGame(0.0, PaneGeom(target).zRefM, 0.0, target + 1.0)
        val shot = game.add(ball())
        assertNotNull(shot)
        assertEquals(BreakOutcomeKind.BROKE, shot!!.outcome.kind)
        assertEquals(4, shot.outcome.brokenCell)
        assertTrue(4 in game.brokenCells)
    }

    @Test
    fun `hit pane but off green breaks nothing`() {
        val game = newGame(0.0, PaneGeom(target).zRefM, 0.0, target - 20.0)
        val shot = game.add(ball())!!
        assertEquals(BreakOutcomeKind.HIT_PANE_MISSED_GREEN, shot.outcome.kind)
        assertEquals(0, game.brokenCells.size)
        assertEquals(1, game.shotCount)
    }

    @Test
    fun `on green but under the pane breaks nothing`() {
        val game = newGame(0.0, crossZ = 2.0, restX = 0.0, restY = target + 1.0)
        val shot = game.add(ball())!!
        assertEquals(BreakOutcomeKind.GREEN_MISSED_PANE, shot.outcome.kind)
        assertEquals(0, game.brokenCells.size)
    }

    @Test
    fun `missed both - short of green below pane`() {
        val game = newGame(0.0, crossZ = 2.0, restX = 0.0, restY = target - 20.0)
        val shot = game.add(ball())!!
        assertEquals(BreakOutcomeKind.MISSED_BOTH, shot.outcome.kind)
    }

    @Test
    fun `crossing an already broken cell breaks nothing`() {
        val game = BreakThePaneGame()
        game.simulator = syntheticShot(0.0, PaneGeom(target).zRefM, 0.0, target + 1.0)
        game.start(target)
        game.add(ball())
        // Same cell again (middle), different crossing point, still on green.
        game.simulator = syntheticShot(0.5, PaneGeom(target).zRefM + 0.2, 0.5, target + 1.5)
        val second = game.add(ball())!!
        assertEquals(BreakOutcomeKind.GREEN_MISSED_PANE, second.outcome.kind)
        assertEquals(1, game.brokenCells.size)
        assertEquals(2, game.shotCount)
    }

    @Test
    fun `completes after all nine cells broken and takeResult fires once`() {
        val game = BreakThePaneGame()
        game.start(target)
        val pane = game.pane!!
        // Nine deterministic shots: one per cell, each resting on the green.
        for (cell in 0 until 9) {
            val row = cell / 3
            val col = cell % 3
            val z = pane.bottomZM + (row + 0.5) * pane.cellHM
            val x = pane.leftXM + (col + 0.5) * pane.cellWM
            game.simulator = syntheticShot(x, z, restX = 0.0, restY = target + 1.0)
            val shot = game.add(ball())
            assertNotNull("cell $cell", shot)
            assertEquals("cell $cell", BreakOutcomeKind.BROKE, shot!!.outcome.kind)
        }
        assertTrue(game.complete)
        assertEquals(9, game.shotCount)
        val result = game.takeResult()
        assertEquals(GameModes.BREAK_PANE, result!!.mode)
        assertEquals("MEDIUM", result.difficulty)
        assertEquals(9, result.score) // shots taken
        assertNull(game.takeResult())
        game.start(target)
        assertFalse(game.complete)
        assertEquals(0, game.brokenCells.size)
    }

    @Test
    fun `feedback strings are populated per outcome`() {
        val game = newGame(0.0, PaneGeom(target).zRefM, 0.0, target + 1.0)
        game.add(ball())
        assertTrue(game.lastFeedback.isNotBlank())
    }

    @Test
    fun `missed both - long straight past green reports LONG`() {
        // Lateral crossing outside the pane, long past the green.
        val game = newGame(crossX = 10.0, crossZ = PaneGeom(target).zRefM, restX = 0.0, restY = target + 20.0)
        val shot = game.add(ball())!!
        assertEquals(BreakOutcomeKind.MISSED_BOTH, shot.outcome.kind)
        assertEquals("LONG", shot.outcome.feedback)
    }

    @Test
    fun `custom pane distance threads into the pane geometry`() {
        val customPane = PaneGeom(target, 42.0)
        val game = BreakThePaneGame()
        game.simulator = syntheticShotFor(customPane, 0.0, customPane.zRefM, 0.0, target + 1.0)
        game.start(target, paneDistanceM = 42.0)
        assertEquals(42.0, game.pane!!.planeYM, 1e-9)
        assertEquals(42.0, game.paneDistanceM!!, 1e-9)
        val shot = game.add(ball())!!
        assertEquals(BreakOutcomeKind.BROKE, shot.outcome.kind)
        assertEquals(4, shot.outcome.brokenCell)
    }

    @Test
    fun `default start keeps the 20 percent plane`() {
        val game = BreakThePaneGame()
        game.start(target)
        assertEquals(PaneGeom.PLANE_FRACTION * target, game.pane!!.planeYM, 1e-9)
        assertNull(game.paneDistanceM)
    }
}
