package com.hpsmiles.golfsim.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetPracticeRevealTest {

    private fun shot(points: Int) = GameShot(
        shot = com.hpsmiles.golfsim.core.ble.BallData(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0)
            .let { com.hpsmiles.golfsim.range.DisplayShot(it, mockLaunch(), mockResult(), 0L) },
        missM = 0.0,
        points = points,
    )

    private fun mockLaunch() = com.hpsmiles.golfsim.core.physics.LaunchConditions(
        ballSpeedMps = 50.0, launchAngleDeg = 10.0,
        spinRpm = 3000, spinAxisDeg = 0.0, launchDirDeg = 0.0,
    )

    private fun mockResult() = com.hpsmiles.golfsim.core.physics.ShotResult(
        carryM = 100.0, rolloutM = 0.0, totalM = 100.0,
        sideM = 0.0, apexM = 20.0, flightTimeSec = 5.0,
        samples = emptyList(), restX = 0.0, restY = 100.0,
    )

    @Test
    fun `isRevealed follows count`() {
        assertFalse(TargetPracticeReveal.isRevealed(0, 0))
        assertTrue(TargetPracticeReveal.isRevealed(0, 1))
        assertTrue(TargetPracticeReveal.isRevealed(1, 2))
        assertFalse(TargetPracticeReveal.isRevealed(2, 2))
    }

    @Test
    fun `revealedTotal sums only revealed shots`() {
        val shots = listOf(shot(25), shot(15), shot(10), shot(5))
        assertEquals(0, TargetPracticeReveal.revealedTotal(shots, 0))
        assertEquals(25, TargetPracticeReveal.revealedTotal(shots, 1))
        assertEquals(40, TargetPracticeReveal.revealedTotal(shots, 2))
        assertEquals(50, TargetPracticeReveal.revealedTotal(shots, 3))
    }
}
