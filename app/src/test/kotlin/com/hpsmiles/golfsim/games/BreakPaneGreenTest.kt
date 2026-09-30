package com.hpsmiles.golfsim.games

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakPaneGreenTest {

    @Test
    fun `green radii at 140m per difficulty`() {
        assertEquals(10.0, BreakPaneGreen.radiusAt140m(Difficulty.EASY), 1e-9)
        assertEquals(8.0, BreakPaneGreen.radiusAt140m(Difficulty.MEDIUM), 1e-9)
        assertEquals(6.0, BreakPaneGreen.radiusAt140m(Difficulty.HARD), 1e-9)
    }

    @Test
    fun `game green radius scales the difficulty radius by target`() {
        val game = BreakThePaneGame()
        game.start(70.0, Difficulty.EASY)
        assertEquals(5.0, game.greenRadiusM(), 1e-9) // 10 * 70/140
        game.start(140.0, Difficulty.HARD)
        assertEquals(6.0, game.greenRadiusM(), 1e-9)
    }

    @Test
    fun `difficulty is persisted on the result`() {
        val game = BreakThePaneGame()
        game.start(140.0, Difficulty.HARD)
        assertEquals(Difficulty.HARD, game.difficulty)
    }
}
