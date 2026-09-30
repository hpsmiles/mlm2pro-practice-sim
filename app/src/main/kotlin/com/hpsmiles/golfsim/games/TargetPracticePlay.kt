package com.hpsmiles.golfsim.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.RangeCamera

/** Target Practice play view: canvas, HUD, metrics panel, result overlay. */
@Composable
fun TargetPracticePlay(
    game: TargetPracticeGame,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playFraction by remember { mutableFloatStateOf(1f) }
    var speedMult by remember { mutableStateOf(GameSpeedMult.X15) }
    // How many shot scores are revealed in the HUD. A shot's score only
    // appears once its animation reaches FollowCam.endFraction (ball rolled
    // out and camera snapped back). Reset when a new game starts.
    var revealedCount by remember { mutableIntStateOf(0) }

    // Restart the flight animation each time a shot lands (tick bump).
    // Runs past 1 through the follow-cam landing hold (FollowCam.endFraction),
    // same as the range playback, so the camera snaps back to the STATIC
    // ready view after the hold instead of parking at the landing frame
    // (user report 2026-09-29).
    LaunchedEffect(game.tick.intValue, speedMult) {
        val shotIndex = game.shots.lastIndex
        val shot = game.shots.lastOrNull() ?: return@LaunchedEffect
        val result = shot.shot.shotResult
        if (result.samples.isEmpty()) {
            playFraction = FollowCam.endFraction(result).toFloat()
            revealedCount = revealedCount.coerceAtLeast(shotIndex + 1)
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
        revealedCount = revealedCount.coerceAtLeast(shotIndex + 1)
    }

    // Reset revealed count when a new game begins (shots cleared).
    LaunchedEffect(game.shots.size) {
        if (game.shots.isEmpty()) revealedCount = 0
    }

    val showResultOverlay = rememberGameResultOverlayGate(
        complete = game.complete,
        lastShotResult = game.shots.lastOrNull()?.shot?.shotResult,
        playFraction = playFraction,
    )

    BoxWithConstraints(modifier.fillMaxSize().background(GolfColors.Base)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // The camera clamp must cover the same box the canvas draws in so
            // the follow cam frames the exact tracer path (GameScene.drawTracer contract).
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val wPx = constraints.maxWidth.toFloat()
                    val hPx = constraints.maxHeight.toFloat()
                    val apexVMin = GameScene.apexVMin(wPx, hPx)
                    val camera = when (val shot = game.shots.lastOrNull()) {
                        null -> RangeCamera.STATIC
                        else -> FollowCam.cameraAt(
                            shot.shot.shotResult,
                            playFraction,
                            apexVMin,
                        )
                    }
                    TargetPracticeCanvas(
                        game = game,
                        current = game.shots.lastOrNull(),
                        playFraction = playFraction,
                        camera = camera,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (showResultOverlay && game.record.value?.outcome == RecordOutcome.NEW_RECORD) {
                    ConfettiBurst(modifier = Modifier.matchParentSize())
                }

                // HUD: per-shot point indicators + running total.
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(GolfSpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeedChipRow(
                        selected = speedMult,
                        onSelect = { speedMult = it },
                        modifier = Modifier.padding(end = GolfSpacing.Sm),
                    )

                    repeat(TargetPracticeScoring.SHOTS_PER_GAME) { i ->
                        val revealed = TargetPracticeReveal.isRevealed(i, revealedCount)
                        val pts = if (revealed) game.shots.getOrNull(i)?.points else null
                        Box(
                            modifier = Modifier
                                .background(if (pts != null) GolfColors.Teal55 else GolfColors.Panel)
                                .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
                                .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
                        ) {
                            Text(text = pts?.toString() ?: "-", style = GolfTypography.MetricValue, color = GolfColors.TextPrimary)
                        }
                    }
                    Text(
                        text = "TOTAL ${TargetPracticeReveal.revealedTotal(game.shots.toList(), revealedCount)}",
                        style = GolfTypography.MetricValue,
                        color = GolfColors.TextPrimary,
                        modifier = Modifier.padding(start = GolfSpacing.Sm),
                    )
                }

                // Result overlay after shot 5, once the ball has finished rolling
                // and the camera has snapped back to STATIC.
                if (showResultOverlay) {
                    val resultShots = game.shots.toList()
                    SectionCard(
                        title = "RESULT",
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(GolfSpacing.Xl),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                            resultShots.forEachIndexed { i, s ->
                                Text(
                                    text = "${i + 1}.  ${s.points} pts   miss ${String.format(Locale.US, "%.1f", s.missM)} m",
                                    style = GolfTypography.Body,
                                    color = GolfColors.TextPrimary,
                                )
                            }
                            Text(
                                text = "TOTAL ${game.totalPoints} / ${TargetPracticeScoring.MAX_SCORE}",
                                style = GolfTypography.MetricValue,
                                color = GolfColors.Teal,
                            )
                            game.record.value?.let { rec ->
                                Text(
                                    text = GameRecord.headline(rec, lowerIsBetter = false),
                                    style = GolfTypography.MetricValue,
                                    color = if (rec.outcome == RecordOutcome.NEW_RECORD) GolfColors.Teal else GolfColors.TextSecondary,
                                )
                                Text(
                                    text = GameRecord.detail(rec, lowerIsBetter = false),
                                    style = GolfTypography.Body,
                                    color = GolfColors.TextSecondary,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                Button(onClick = { game.start(game.targetM, game.difficulty) }) {
                                    Text("PLAY AGAIN")
                                }
                                OutlinedButton(onClick = onBack) { Text("BACK") }
                            }
                        }
                    }
                }
            }

            GameMetricsPanel(
                currentShot = game.shots.lastOrNull()?.shot,
                sessionShots = game.shots.map { it.shot },
            )
        }
    }
}
