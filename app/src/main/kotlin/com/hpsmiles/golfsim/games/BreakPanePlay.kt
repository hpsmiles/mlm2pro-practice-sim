package com.hpsmiles.golfsim.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfMotion
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.RangeCamera

/** Break the Pane play view: canvas, pane-state minimap, feedback, overlay (spec 5). */
@Composable
fun BreakPanePlay(
    game: BreakThePaneGame,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playFraction by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(game.tick.intValue) {
        if (game.shots.isEmpty()) return@LaunchedEffect
        val durationMs = GolfMotion.TracerDrawMs.toFloat()
        var last = withFrameNanos { it }
        playFraction = 0f
        while (playFraction < 1f) {
            val now = withFrameNanos { it }
            playFraction = (playFraction + ((now - last) / 1_000_000.0 / durationMs).toFloat()).coerceAtMost(1f)
            last = now
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(GolfColors.Base)) {
        val camera = when (val shot = game.shots.lastOrNull()) {
            null -> RangeCamera.STATIC
            else -> FollowCam.cameraAt(shot.shot.shotResult, playFraction, GameScene.apexVMin(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
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

        if (game.complete) {
            SectionCard(
                title = "PANE BROKEN",
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(GolfSpacing.Xl),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                    Text("Total shots: ${game.shotCount}", style = GolfTypography.MetricValue, color = GolfColors.Teal)
                    Text("Lower is better.", style = GolfTypography.Body, color = GolfColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                        Button(onClick = { game.start(game.targetM) }) { Text("PLAY AGAIN") }
                        OutlinedButton(onClick = onBack) { Text("BACK") }
                    }
                }
            }
        }
    }
}
