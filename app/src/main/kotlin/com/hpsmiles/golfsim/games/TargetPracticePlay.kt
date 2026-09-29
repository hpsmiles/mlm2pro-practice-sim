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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.util.Locale
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

/** Target Practice play view: canvas, HUD, result overlay (spec section 4). */
@Composable
fun TargetPracticePlay(
    game: TargetPracticeGame,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playFraction by remember { mutableFloatStateOf(1f) }

    // Restart the flight animation each time a shot lands (tick bump).
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
        TargetPracticeCanvas(
            game = game,
            current = game.shots.lastOrNull(),
            playFraction = playFraction,
            camera = camera,
            modifier = Modifier.fillMaxSize(),
        )

        // HUD: per-shot point indicators + running total.
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(GolfSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(TargetPracticeScoring.SHOTS_PER_GAME) { i ->
                val pts = game.shots.getOrNull(i)?.points
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
                text = "TOTAL ${game.totalPoints}",
                style = GolfTypography.MetricValue,
                color = GolfColors.TextPrimary,
                modifier = Modifier.padding(start = GolfSpacing.Sm),
            )
        }

        // Result overlay after shot 5.
        if (game.complete) {
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
}
