package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetPracticeGameTest {

    /** Injects a synthetic rest position so scoring is deterministic on the JVM. */
    private fun gameWithRest(restX: Double, restY: Double): TargetPracticeGame {
        val game = TargetPracticeGame()
        game.simulator = { _ ->
            ShotResult(
                carryM = 138.0, rolloutM = 2.0, totalM = 140.0, sideM = restX,
                apexM = 25.0, flightTimeSec = 6.0,
                samples = emptyList(),
                restX = restX, restY = restY,
            )
        }
        game.start(140.0, Difficulty.MEDIUM)
        return game
    }

    private fun ball() = BallData(33.0, 44.0, 0.0, 21.0, 0.0, 7500, 5, 10)

    @Test
    fun `five accepted shots complete the game with summed points`() {
        val game = gameWithRest(restX = 0.0, restY = 141.5) // 1.5 m miss -> 25 pts (MEDIUM)
        repeat(5) { assertTrue(game.add(ball()) != null) }
        assertTrue(game.complete)
        assertEquals(5, game.shots.size)
        assertEquals(125, game.totalPoints)
    }

    @Test
    fun `add after completion returns null`() {
        val game = gameWithRest(0.0, 141.5)
        repeat(5) { game.add(ball()) }
        assertNull(game.add(ball()))
        assertEquals(5, game.shots.size)
    }

    @Test
    fun `guard-violating ball data is ignored`() {
        val game = gameWithRest(0.0, 141.5)
        // ball speed 0.1 m/s violates LaunchConditions require(0.5..100)
        assertNull(game.add(BallData(1.0, 0.1, 0.0, 21.0, 0.0, 7500, 5, 10)))
        assertEquals(0, game.shots.size)
    }

    @Test
    fun `miss distance uses rest position not carry`() {
        val game = gameWithRest(restX = 3.0, restY = 145.0) // miss = sqrt(9+25) = 5.831 -> 10 pts (MEDIUM)
        game.add(ball())
        assertEquals(10, game.shots.single().points)
        assertEquals(kotlin.math.hypot(3.0, 5.0), game.shots.single().missM, 1e-9)
    }

    @Test
    fun `takeResult returns exactly once then null`() {
        val game = gameWithRest(0.0, 141.5)
        repeat(5) { game.add(ball()) }
        val first = game.takeResult()
        assertEquals(GameModes.TARGET_PRACTICE, first!!.mode)
        assertEquals("MEDIUM", first.difficulty)
        assertEquals(140.0, first.targetM, 1e-9)
        assertEquals(125, first.score)
        assertNull(game.takeResult())
        // PLAY AGAIN resets everything
        game.start(140.0, Difficulty.MEDIUM)
        assertFalse(game.complete)
        assertEquals(0, game.shots.size)
        assertNull(game.takeResult())
    }

    @Test
    fun `green radius scales with target`() {
        val game = TargetPracticeGame()
        game.start(280.0, Difficulty.MEDIUM)
        assertEquals(12.0, game.greenRadiusM(), 1e-9)
    }
}
