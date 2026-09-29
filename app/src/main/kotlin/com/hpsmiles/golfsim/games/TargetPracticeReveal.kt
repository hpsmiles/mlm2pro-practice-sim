// app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticeReveal.kt
package com.hpsmiles.golfsim.games

/**
 * Reveal timing for the Target Practice point indicators. The HUD must not
 * show a shot's score until its animation has run to FollowCam.endFraction,
 * because from the player's seat the ball is still rolling and revealing the
 * rest-based score early looks like it was scored from the landing position.
 */
object TargetPracticeReveal {

    /** True if the shot at [index] should have its points revealed. */
    fun isRevealed(index: Int, revealedCount: Int): Boolean = index < revealedCount

    /** How many indicators should display '-' (hidden). */
    fun hiddenCount(shots: Int, revealedCount: Int): Int = shots - revealedCount.coerceIn(0, shots)

    /** Sum of points only for revealed shots. HIDDEN shots contribute zero. */
    fun revealedTotal(shots: List<GameShot>, revealedCount: Int): Int {
        return shots.take(revealedCount.coerceIn(0, shots.size)).sumOf { it.points }
    }
}
