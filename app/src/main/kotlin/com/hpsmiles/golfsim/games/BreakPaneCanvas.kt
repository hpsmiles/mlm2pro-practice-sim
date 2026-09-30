package com.hpsmiles.golfsim.games

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera

/** POV canvas for Break the Pane: ground, green, pin, 3x3 glass pane, tracer. */
@Composable
fun BreakPaneCanvas(
    game: BreakThePaneGame,
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
            game.pane?.let { pane ->
                drawGreen(camera, w, h, 0.0, game.targetM, game.greenRadiusM())
                drawPin(camera, w, h, 0.0, game.targetM)
                // Pane cells, top row first so nearer (lower) cells draw on top.
                for (cell in 8 downTo 0) {
                    val broken = cell in game.brokenCells
                    val clipped = PaneClip.clip(
                        pane.cellCorners(cell).map { (x, y, z) -> PaneClip.Vertex(x, y, z) },
                        camera,
                    )
                    if (clipped.size < 3) continue
                    val path = Path()
                    var first = true
                    for (v in clipped) {
                        val p = PovProjector.project(camera, v.x, v.y, v.z) ?: continue
                        val s = GameScene.toScreen(w, h, p)
                        if (first) { path.moveTo(s.x, s.y); first = false } else path.lineTo(s.x, s.y)
                    }
                    path.close()
                    if (first) continue
                    if (broken) {
                        drawPath(path, GolfColors.Teal.copy(alpha = 0.03f))
                        drawPath(path, GolfColors.Teal.copy(alpha = 0.25f), style = Stroke(1f))
                    } else {
                        drawPath(path, GolfColors.Teal.copy(alpha = 0.18f))
                        drawPath(path, GolfColors.Teal, style = Stroke(2f))
                    }
                }
            }
            project(camera, w, h, 0.0, 0.0, 0.05)?.let { drawCircle(GolfColors.TextPrimary, 4f, it) }
            game.shots.lastOrNull()?.let { shot ->
                val result = shot.shot.shotResult
                val apexVMin = FollowCam.RAW_APEX_VMIN
                drawTracer(camera, w, h, result, playFraction, apexVMin)
                game.pane?.let { pane ->
                    val mark = PaneIntersection.mark(result, pane.planeYM, apexVMin)
                    if (mark != null && playFraction >= mark.revealFraction) {
                        project(camera, w, h, mark.xM, pane.planeYM, mark.zM)?.let { pt ->
                            drawCircle(GolfColors.TextPrimary, 6f, pt)
                            drawCircle(color = GolfColors.Amber, radius = 6f, center = pt, style = Stroke(2f))
                        }
                    }
                }
            }
        }
    }
}
