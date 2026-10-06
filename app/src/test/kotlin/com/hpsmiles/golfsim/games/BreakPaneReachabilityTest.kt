package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.Surface
import com.hpsmiles.golfsim.core.physics.UniformSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reachability tests for the retuned Break-the-Pane geometry.
 *
 * These assert the skill being protected: a realistic 8i can hit every row by
 * changing launch angle and can hit the side columns by starting the ball
 * offline with launch direction. Spin axis is only used to curve the ball back
 * toward the target after it has crossed a side pane.
 *
 * The real-engine probe values below centre each shot in its row/column at
 * target 140 m:
 *   - high 8i, la = 24.0 deg  -> z ~14.48 m (top row centre ~14.74 m)
 *   - flat 8i, la = 18.0 deg -> z ~10.32 m (bottom row centre ~10.26 m)
 *   - side start, ld = +3.5 deg -> x ~+2.14 m (right column)
 * The high/flat probes happen to pass under BOTH the old and new constants,
 * which is acceptable; the side-column probe and the exact PaneGeom constants
 * still guard against a revert to the wider old cells.
 */
class BreakPaneReachabilityTest {

    companion object {
        private const val STOCK_BALL_SPEED = 44.0
        private const val STOCK_SPIN = 6500
        private const val STOCK_LAUNCH_ANGLE = 20.0

        private const val HIGH_LA = 24.0
        private const val FLAT_LA = 18.0
        private const val RIGHT_LD = 3.5
    }

    private fun simulate(
        ballSpeedMps: Double = STOCK_BALL_SPEED,
        launchAngleDeg: Double = STOCK_LAUNCH_ANGLE,
        spinRpm: Int = STOCK_SPIN,
        spinAxisDeg: Double = 0.0,
        launchDirDeg: Double = 0.0,
    ) = BallFlightEngine.simulate(
        LaunchConditions(
            ballSpeedMps = ballSpeedMps,
            launchAngleDeg = launchAngleDeg,
            spinRpm = spinRpm,
            spinAxisDeg = spinAxisDeg,
            launchDirDeg = launchDirDeg,
        ),
        Environment(),
        UniformSurface(Surface.FAIRWAY_NORMAL),
    )

    private fun cellFor(
        targetM: Double,
        paneDistanceM: Double? = null,
        ballSpeedMps: Double = STOCK_BALL_SPEED,
        launchAngleDeg: Double = STOCK_LAUNCH_ANGLE,
        spinRpm: Int = STOCK_SPIN,
        spinAxisDeg: Double = 0.0,
        launchDirDeg: Double = 0.0,
    ): Int {
        val result = simulate(ballSpeedMps, launchAngleDeg, spinRpm, spinAxisDeg, launchDirDeg)
        return PaneGeom(targetM, paneDistanceM).firstCrossing(result.samples)?.cell
            ?: error("probe shot did not cross the pane plane")
    }

    @Test
    fun `stock 8i breaks the middle cell at 140m`() {
        assertEquals(4, cellFor(targetM = 140.0))
    }

    @Test
    fun `stock 8i breaks the middle cell at 125m`() {
        assertEquals(4, cellFor(targetM = 125.0))
    }

    @Test
    fun `high 8i breaks the top row at 140m`() {
        val cell = cellFor(targetM = 140.0, launchAngleDeg = HIGH_LA)
        assertTrue(
            "expected top row (6..8) but got cell=$cell for la=$HIGH_LA",
            cell in 6..8,
        )
    }

    @Test
    fun `flat 8i breaks the bottom row at 140m`() {
        val cell = cellFor(targetM = 140.0, launchAngleDeg = FLAT_LA)
        assertTrue(
            "expected bottom row (0..2) but got cell=$cell for la=$FLAT_LA",
            cell in 0..2,
        )
    }

    @Test
    fun `off-line start line crosses into the right column at 140m`() {
        val cell = cellFor(targetM = 140.0, launchDirDeg = RIGHT_LD)
        assertTrue(
            "expected right column (2,5,8) but got cell=$cell for ld=$RIGHT_LD",
            cell in listOf(2, 5, 8),
        )
    }

    @Test
    fun `stock shot does not break a side column at 140m`() {
        val cell = cellFor(targetM = 140.0)
        assertTrue("stock shot should stay in the middle column", cell in 3..5)
    }

    @Test
    fun `stock 8i crosses the pane at the 10 percent custom distance`() {
        // Target 125 keeps the stock 8i (20 deg / 6500 rpm) inside the reference trajectory's
        // profile bracket, so the shot approximates the target-carrying reference the
        // middle-row anchor assumes.
        // We assert the middle column plus an in-band crossing only; the exact row is
        // calibration-sensitive, so the probes fail on anchoring regressions without
        // pinning row centering.
        val cell = cellFor(targetM = 125.0, paneDistanceM = 12.5) // 10% of 125
        assertTrue("expected middle column (3..5) but got cell=$cell", cell in 3..5)
    }

    @Test
    fun `stock 8i crosses the pane at the 50 percent custom distance`() {
        val cell = cellFor(targetM = 125.0, paneDistanceM = 62.5) // 50% of 125
        assertTrue("expected middle column (3..5) but got cell=$cell", cell in 3..5)
    }

    @Test
    fun `under-carrying club still crosses the pane near the player`() {
        // 140 m target with the same 8i (~119 m carry): near the player (10% plane) the
        // trajectory has not diverged from the reference yet, so the shot still crosses mid-pane.
        val cell = cellFor(targetM = 140.0, paneDistanceM = 14.0) // 10% of 140
        assertTrue("expected middle column (3..5) but got cell=$cell", cell in 3..5)
    }
}
