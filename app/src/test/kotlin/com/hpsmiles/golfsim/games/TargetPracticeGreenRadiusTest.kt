package com.hpsmiles.golfsim.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetPracticeGreenRadiusTest {

    @Test
    fun `green radius matches outer band scaled by target`() {
        val game = TargetPracticeGame()
        game.start(140.0, Difficulty.EASY)
        assertEquals(18.0, game.greenRadiusM(), 1e-9)
        game.start(140.0, Difficulty.MEDIUM)
        assertEquals(15.0, game.greenRadiusM(), 1e-9)
        game.start(140.0, Difficulty.HARD)
        assertEquals(10.0, game.greenRadiusM(), 1e-9)
        game.start(280.0, Difficulty.EASY)
        assertEquals(36.0, game.greenRadiusM(), 1e-9)
    }

    @Test
    fun `pre-start radius uses default fallback`() {
        val game = TargetPracticeGame()
        assertEquals(6.0 * 140.0 / 140.0, game.greenRadiusM(), 1e-9)
    }

    @Test
    fun `start flag is reset by start call`() {
        val game = TargetPracticeGame()
        game.start(140.0, Difficulty.HARD)
        assertTrue(game.greenRadiusM() < game.start(140.0, Difficulty.EASY).let { game.greenRadiusM() })
    }

    @Test
    fun `outer band radius constants`() {
        assertEquals(18.0, TargetPracticeScoring.outerBandRadiusM(Difficulty.EASY), 1e-9)
        assertEquals(15.0, TargetPracticeScoring.outerBandRadiusM(Difficulty.MEDIUM), 1e-9)
        assertEquals(10.0, TargetPracticeScoring.outerBandRadiusM(Difficulty.HARD), 1e-9)
    }
}
