package com.hpsmiles.golfsim.games

/**
 * Break the Pane green radius per difficulty, at the 140 m reference target
 * (spec 2026-09-30 §5). Scaled by target in [BreakThePaneGame.greenRadiusM].
 */
object BreakPaneGreen {

    fun radiusAt140m(difficulty: Difficulty): Double = when (difficulty) {
        Difficulty.EASY -> 10.0
        Difficulty.MEDIUM -> 8.0
        Difficulty.HARD -> 6.0
    }
}
