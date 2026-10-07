package com.hpsmiles.golfsim.range

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * Live POV range view: tracer playback, follow cam, previous-shot lines and
 * tracer controls. Extracted from RangeScreen so the BAG collecting view can
 * render the same shot visual (spec item 3) without duplicating rendering.
 *
 * Self-contained state: tracer/prev toggles + history limit live here
 * (default ON/ON, 8 lines). The shot restart is keyed on the last shot's
 * identity (tick-free — no session dependency); a restored shot (no flight
 * samples) parks at the follow-cam landing hold.
 */
@Composable
internal fun RangeLiveView(
    shots: List<DisplayShot>,
    modifier: Modifier = Modifier,
    speedMult: SpeedMult = SpeedMult.X15,
    showControls: Boolean = true,
    customGreen: RangeScene.Green? = null,
) {
    var playFraction by remember { mutableFloatStateOf(1f) }
    var showTracer by remember { mutableStateOf(true) }
    var showHistory by remember { mutableStateOf(true) }
    var historyLimit by remember { mutableFloatStateOf(8f) }
    val currentShot = shots.lastOrNull()

    // New shot: restart the tracer animation. Keyed on the shot itself
    // (identity key, no session tick) so any caller works.
    LaunchedEffect(currentShot) {
        playFraction = 0f
    }

    // Tracer playback: animate playFraction over the shot's real duration,
    // EXTENDED past 1 by the follow-cam landing hold (FollowCam.endFraction).
    // The speed multiplier scales the whole timeline, hold included —
    // consistent with slow-mo review.
    LaunchedEffect(currentShot, speedMult) {
        val shot = currentShot ?: return@LaunchedEffect
        // M5 restore: a shot with no trajectory samples is a restored
        // (pre-animated) one — park it in the COMPLETED state (landing dot
        // and resting ball render, nothing animates), at relaunch or on a
        // speed-mult change. Robust by construction: live shots always
        // carry flight samples, restored shots never do.
        if (shot.shotResult.samples.isEmpty()) {
            playFraction = FollowCam.endFraction(shot.shotResult).toFloat()
            return@LaunchedEffect
        }
        if (shot.shotResult.flightTimeSec <= 0.0) return@LaunchedEffect
        val durationMs = shot.shotResult.flightTimeSec * 1000.0 / speedMult.divisor
        val end = FollowCam.endFraction(shot.shotResult).toFloat()
        var lastNanos = withFrameNanos { it }
        while (playFraction < end) {
            val now = withFrameNanos { it }
            val deltaMs = (now - lastNanos) / 1_000_000.0
            lastNanos = now
            playFraction = (playFraction + (deltaMs / durationMs).toFloat()).coerceAtMost(end)
        }
    }

    // Previous-shot lines: faded, most recent first, limited by the slider.
    val previousShots = if (showHistory) {
        shots.dropLast(1).map { it.shotResult }.takeLast(historyLimit.toInt())
    } else {
        emptyList()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        // Same top-8% line the canvas derives (v0 = 0.30h, focal = 1.10w),
        // so the follow cam chases the exact drawn (apex-clamped) flight
        // on this geometry.
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val apexVMin = ((0.08f * hPx - 0.30f * hPx) / (1.10f * wPx)).toDouble()
        val camera = when (val shot = currentShot) {
            null -> RangeCamera.STATIC
            else -> FollowCam.cameraAt(shot.shotResult, playFraction, apexVMin)
        }
        // customGreen defaults to null (the bag sim is uniform fairway — no
        // green oval); the RANGE tab passes the practice-green through.
        PovRangeCanvas(
            currentShot?.shotResult, previousShots, playFraction, showTracer, showHistory,
            camera = camera,
            customGreen = customGreen,
            modifier = Modifier.fillMaxSize(),
        )
    }

    // Tracer controls: inside the view frame, top-left. Rendered for every
    // caller today (the bag collecting overlay keeps them too — they sit
    // top-left, clear of its bottom-left status card).
    if (showControls) {
        Column(
            modifier = Modifier.padding(GolfSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                OverlayChip("TRACER: ${if (showTracer) "ON" else "OFF"}", active = showTracer) {
                    showTracer = !showTracer
                }
                if (showTracer) {
                    OverlayChip("PREV: ${if (showHistory) "ON" else "OFF"}", active = showHistory) {
                        showHistory = !showHistory
                    }
                }
            }
            if (showTracer && showHistory) {
                Slider(
                    value = historyLimit,
                    onValueChange = { historyLimit = it },
                    valueRange = 0f..20f,
                    steps = 19,
                    modifier = Modifier.width(110.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = GolfColors.Teal,
                        activeTrackColor = GolfColors.Teal,
                        inactiveTrackColor = GolfColors.Line,
                    ),
                )
                Text(
                    "LAST ${historyLimit.toInt()} LINES",
                    color = GolfColors.TextMuted,
                    style = GolfTypography.Status,
                )
            }
        }
    }
}
