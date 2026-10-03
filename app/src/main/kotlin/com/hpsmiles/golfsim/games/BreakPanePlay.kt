package com.hpsmiles.golfsim.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.RangeCamera

/** Break the Pane play view: canvas, minimap, feedback, metrics panel, overlay. */
@Composable
fun BreakPanePlay(
    game: BreakThePaneGame,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playFraction by remember { mutableFloatStateOf(1f) }
    var speedMult by remember { mutableStateOf(GameSpeedMult.X15) }

    // Runs past 1 through the follow-cam landing hold (FollowCam.endFraction),
    // same as the range playback, so the camera snaps back to the STATIC
    // ready view after the hold instead of parking at the landing frame
    // (user report 2026-09-29).
    LaunchedEffect(game.tick.intValue, speedMult) {
        val shot = game.shots.lastOrNull() ?: return@LaunchedEffect
        val result = shot.shot.shotResult
        if (result.samples.isEmpty()) {
            playFraction = FollowCam.endFraction(result).toFloat()
            return@LaunchedEffect
        }
        if (result.flightTimeSec <= 0.0) return@LaunchedEffect
        val durationMs = result.flightTimeSec * 1000.0 / speedMult.divisor
        val end = FollowCam.endFraction(result).toFloat()
        var last = withFrameNanos { it }
        playFraction = 0f
        while (playFraction < end) {
            val now = withFrameNanos { it }
            playFraction = (playFraction + ((now - last) / 1_000_000.0 / durationMs).toFloat()).coerceAtMost(end)
            last = now
        }
    }

    val showResultOverlay = rememberGameResultOverlayGate(
        complete = game.complete,
        lastShotResult = game.shots.lastOrNull()?.shot?.shotResult,
        playFraction = playFraction,
    )

    Box(modifier = modifier.fillMaxSize().background(GolfColors.Base)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Camera and canvas both use the raw clamp so the follow cam
            // frames the exact same tracer path the pane is drawn in.
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                val apexVMin = FollowCam.RAW_APEX_VMIN
                val camera = when (val shot = game.shots.lastOrNull()) {
                    null -> RangeCamera.STATIC
                    else -> FollowCam.cameraAt(
                        shot.shot.shotResult,
                        playFraction,
                        apexVMin,
                    )
                }
                BreakPaneCanvas(
                    game = game,
                    playFraction = playFraction,
                    camera = camera,
                    modifier = Modifier.fillMaxSize(),
                )

                // HUD: pane-state minimap (3x3, filled teal = broken), shots counter, feedback.
                Column(
                    modifier = Modifier.align(Alignment.TopStart).padding(GolfSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SpeedChipRow(
                        selected = speedMult,
                        onSelect = { speedMult = it },
                        modifier = Modifier.padding(bottom = GolfSpacing.Xs),
                    )
                    for (row in 2 downTo 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (col in 0..2) {
                                val cell = row * 3 + col
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(if (cell in game.brokenCells) GolfColors.Teal else GolfColors.Panel)
                                        .border(1.dp, GolfColors.Line, RoundedCornerShape(2.dp)),
                                )
                            }
                        }
                    }
                    Text("SHOTS ${game.shotCount}", style = GolfTypography.MetricValue, color = GolfColors.TextPrimary)
                }
                if (game.lastFeedback.isNotBlank()) {
                    Text(
                        text = game.lastFeedback,
                        style = GolfTypography.Status,
                        color = GolfColors.Teal,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(GolfSpacing.Md),
                    )
                }

                // PANE BROKEN overlay appears only after the last shot has
                // finished rolling and the camera snapped back to STATIC.
                if (showResultOverlay) {
                    SectionCard(
                        title = "PANE BROKEN",
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(GolfSpacing.Xl),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                            Text("Total shots: ${game.shotCount}", style = GolfTypography.MetricValue, color = GolfColors.Teal)
                            game.record.value?.let { rec ->
                                Text(
                                    text = GameRecord.headline(rec, lowerIsBetter = true),
                                    style = GolfTypography.MetricValue,
                                    color = if (rec.outcome == RecordOutcome.NEW_RECORD) GolfColors.Teal else GolfColors.TextSecondary,
                                )
                                Text(
                                    text = GameRecord.detail(rec, lowerIsBetter = true),
                                    style = GolfTypography.Body,
                                    color = GolfColors.TextSecondary,
                                )
                            }
                            Text("Lower is better.", style = GolfTypography.Body, color = GolfColors.TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                Button(onClick = { game.start(game.targetM, game.difficulty) }) { Text("PLAY AGAIN") }
                                OutlinedButton(onClick = onBack) { Text("BACK") }
                            }
                        }
                    }
                }

                // Celebrate a NEW high score on top of the HUD and result card.
                if (showResultOverlay && game.record.value?.outcome == RecordOutcome.NEW_RECORD) {
                    ConfettiBurst(modifier = Modifier.matchParentSize())
                }
            }

            GameMetricsPanel(
                currentShot = game.shots.lastOrNull()?.shot,
                sessionShots = game.shots.map { it.shot },
            )
        }
    }
}
