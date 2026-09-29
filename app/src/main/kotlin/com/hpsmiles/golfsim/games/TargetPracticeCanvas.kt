package com.hpsmiles.golfsim.games

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.range.RangeCamera
import com.hpsmiles.golfsim.range.RangeScene

/** POV canvas for Target Practice: green, scoring rings, pin, tracer, rest dot. */
@Composable
fun TargetPracticeCanvas(
    game: TargetPracticeGame,
    current: GameShot?,
    playFraction: Float,
    camera: RangeCamera,
    modifier: Modifier = Modifier,
) {
    val labelPaint = remember { Paint() }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        with(GameScene) {
            drawGameGround(camera, w, h, labelPaint)
            drawGreen(camera, w, h, 0.0, game.targetM, game.greenRadiusM())
            // Shaded scoring rings, densest at the centre (draw largest first).
            TargetPracticeScoring.bands(game.difficulty).asReversed().forEachIndexed { i, band ->
                groundPath(camera, w, h, RangeScene.circleOutline(0.0, game.targetM, band.maxMissM))?.let {
                    drawPath(it, GolfColors.Teal.copy(alpha = 0.08f + 0.06f * i))
                }
            }
            drawPin(camera, w, h, 0.0, game.targetM)
            project(camera, w, h, 0.0, 0.0, 0.05)?.let { drawCircle(GolfColors.TextPrimary, 4f, it) }
            current?.let { drawTracer(camera, w, h, it.shot.shotResult, playFraction, GameScene.apexVMin(w, h)) }
        }
    }
}
