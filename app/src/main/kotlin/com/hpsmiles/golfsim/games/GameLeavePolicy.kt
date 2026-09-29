package com.hpsmiles.golfsim.games

/**
 * Whether leaving the GAMES tab with the given game state must prompt,
 * and whether the game must be relinquished outright.
 * Zero shots taken or an already-complete game is never "mid game".
 */
object GameLeavePolicy {
    fun confirmRequired(mode: GameMode, shotsTaken: Boolean, complete: Boolean): Boolean =
        mode != GameMode.NONE && shotsTaken && !complete
}
