package com.hpsmiles.golfsim.core.ble

/**
 * Parse outcome for a MEASUREMENT payload. Runtime data problems never
 * throw — they land here as sealed variants a live session can log and skip.
 */
sealed interface BallDataResult {
    data class Shot(val data: BallData) : BallDataResult
    data object Misread : BallDataResult
    data class Malformed(val reason: String) : BallDataResult
}
