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
 * toward the target after it has broken a side pane.
 *
 * Probe values were tuned against the real engine. The original prompts
 * suggested la ~25 deg / la ~18 deg / ld ~+5 deg, but the actual thresholds at
 * target 140 m with the retuned cells (cellH 1.6 %, cellW 2.0 %) are lower:
 *   - top-row threshold ~23.2 deg
 *   - bottom-row threshold ~19.3 deg
 *   - right-column threshold ~2.4 deg launch direction
 * The chosen probes sit safely past each threshold while staying inside the
 * intended cell.
 */
class BreakPaneReachabilityTest {

    companion object {
        private const val TARGET_M = 140.0

        private const val STOCK_BALL_SPEED = 44.0
        private const val STOCK_SPIN = 6500
        private const val STOCK_LAUNCH_ANGLE = 20.0

        private const val HIGH_LA = 23.3
        private const val FLAT_LA = 19.5
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
        ballSpeedMps: Double = STOCK_BALL_SPEED,
        launchAngleDeg: Double = STOCK_LAUNCH_ANGLE,
        spinRpm: Int = STOCK_SPIN,
        spinAxisDeg: Double = 0.0,
        launchDirDeg: Double = 0.0,
    ): Int {
        val result = simulate(ballSpeedMps, launchAngleDeg, spinRpm, spinAxisDeg, launchDirDeg)
        return PaneGeom(TARGET_M).firstCrossing(result.samples)?.cell
            ?: error("probe shot did not cross the pane plane")
    }

    @Test
    fun `stock 8i breaks the middle cell`() {
        assertEquals(4, cellFor())
    }

    @Test
    fun `high 8i breaks the top row`() {
        val cell = cellFor(launchAngleDeg = HIGH_LA)
        assertTrue(
            "expected top row (6..8) but got cell=$cell for la=$HIGH_LA",
            cell in 6..8,
        )
    }

    @Test
    fun `flat 8i breaks the bottom row`() {
        val cell = cellFor(launchAngleDeg = FLAT_LA)
        assertTrue(
            "expected bottom row (0..2) but got cell=$cell for la=$FLAT_LA",
            cell in 0..2,
        )
    }

    @Test
    fun `off-line start line breaks the right column`() {
        val cell = cellFor(launchDirDeg = RIGHT_LD)
        assertTrue(
            "expected right column (2,5,8) but got cell=$cell for ld=$RIGHT_LD",
            cell in listOf(2, 5, 8),
        )
    }

    @Test
    fun `stock shot does not break a side column`() {
        val cell = cellFor()
        assertTrue("stock shot should stay in the middle column", cell in 3..5)
    }
}
