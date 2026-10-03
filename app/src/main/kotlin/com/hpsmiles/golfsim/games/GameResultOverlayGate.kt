// app/src/main/kotlin/com/hpsmiles/golfsim/games/GameResultOverlayGate.kt
package com.hpsmiles.golfsim.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.range.FollowCam

/**
 * Holds whether a completed game's result overlay should be visible. The
 * overlay must wait until the last shot's animation has run all the way to
 * [FollowCam.endFraction] (ball landed, rolled out, camera snapped back to
 * STATIC), not the moment game.complete flips at shot ingest.
 */
@Composable
fun rememberGameResultOverlayGate(
    complete: Boolean,
    lastShotResult: ShotResult?,
    playFraction: Float,
): Boolean {
    var ready by remember { mutableStateOf(false) }
    // Guards against a stale high playFraction from a previous shot flipping
    // ready=true before the current shot's animation has actually started.
    // We require observed progress (playFraction < end) before reaching end
    // is treated as genuine completion.
    var sawInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(complete, lastShotResult) {
        // A new completion starts hidden until the animation reaches endFraction.
        sawInProgress = false
        if (!complete) {
            ready = false
        } else {
            val result = lastShotResult
            if (result == null || result.samples.isEmpty() || result.flightTimeSec <= 0.0) {
                ready = true
            } else {
                // Animation loop will drive playFraction; keep ready false here
                // and let the second LaunchedEffect flip it when it reaches end.
                ready = false
            }
        }
    }

    LaunchedEffect(playFraction, complete) {
        if (!complete) return@LaunchedEffect
        val result = lastShotResult ?: return@LaunchedEffect
        if (result.samples.isEmpty() || result.flightTimeSec <= 0.0) {
            ready = true
            return@LaunchedEffect
        }
        val end = FollowCam.endFraction(result).toFloat()
        if (playFraction < end) {
            sawInProgress = true
        } else if (sawInProgress) {
            ready = true
        }
    }

    return ready
}
