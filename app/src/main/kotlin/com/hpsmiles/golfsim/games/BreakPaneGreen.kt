package com.hpsmiles.golfsim.games

/**
 * Break the Pane green radius per difficulty, at the 140 m reference target
 * (item 6 amended 2026-10-01: raised on ALL difficulties). Scaled by target
 * in [BreakThePaneGame.greenRadiusM].
 */
object BreakPaneGreen {

    fun radiusAt140m(difficulty: Difficulty): Double = when (difficulty) {
        Difficulty.EASY -> 12.0
        Difficulty.MEDIUM -> 10.0
        Difficulty.HARD -> 8.0
    }
}
