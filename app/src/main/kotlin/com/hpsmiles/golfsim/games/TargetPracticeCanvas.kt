package com.hpsmiles.golfsim.games

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera
import com.hpsmiles.golfsim.range.RangeScene

/** POV canvas for Target Practice: green, scoring rings + labels, pin, tracer, rest dot. */
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
        val draw = this
        val w = size.width
        val h = size.height
        with(GameScene) {
            drawGameGround(camera, w, h, labelPaint)
            drawGreen(camera, w, h, 0.0, game.targetM, game.greenRadiusM())
            val bands = TargetPracticeScoring.bands(game.difficulty)
            // Shaded scoring rings, densest at the centre (draw largest first).
            bands.asReversed().forEachIndexed { i, band ->
                groundPath(camera, w, h, RangeScene.circleOutline(0.0, game.targetM, band.maxMissM))?.let {
                    draw.drawPath(it, GolfColors.Teal.copy(alpha = 0.08f + 0.06f * i))
                }
            }
            drawRingScoreLabels(draw, camera, w, h, game.targetM, bands, labelPaint)
            drawPin(camera, w, h, 0.0, game.targetM)
            project(camera, w, h, 0.0, 0.0, 0.05)?.let { draw.drawCircle(GolfColors.TextPrimary, 4f, it) }
            current?.let { drawTracer(camera, w, h, it.shot.shotResult, playFraction, GameScene.apexVMin(w, h)) }
        }
    }
}

/**
 * Integer point labels for each scoring band. Placed at mid-band radius along
 * a consistent bearing (7 o'clock) so they read clearly from the static tee
 * view without overlapping the pin.
 */
private fun drawRingScoreLabels(
    draw: androidx.compose.ui.graphics.drawscope.DrawScope,
    cam: RangeCamera,
    w: Float,
    h: Float,
    targetM: Double,
    bands: List<TargetPracticeScoring.Band>,
    labelPaint: Paint,
) {
    labelPaint.apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        color = GolfColors.Teal.toArgb()
    }
    val focalPx = w * 1.10f
    val v0Px = h * 0.30f
    val centerX = w / 2f
    // Fixed bearing for labels: slightly left/down from centre (7 o'clock).
    val bearingRad = Math.toRadians(210.0)
    val cosB = kotlin.math.cos(bearingRad)
    val sinB = kotlin.math.sin(bearingRad)

    fun radiusForBand(band: TargetPracticeScoring.Band, prevMaxMissM: Double?): Double {
        val inner = prevMaxMissM ?: gameGreenRadiusAt(targetM)
        return (inner + band.maxMissM) / 2.0
    }

    var prevMaxMissM: Double? = null
    for (band in bands) {
        val r = radiusForBand(band, prevMaxMissM)
        val worldX = r * cosB
        val worldY = targetM + r * sinB
        val proj = PovProjector.project(cam, worldX, worldY, 0.0) ?: continue
        val sx = centerX + (proj.u * focalPx).toFloat()
        val sy = v0Px + (proj.v * focalPx).toFloat()
        // Text size scales with the projected scale at that depth so labels
        // grow/shrink consistently as the target distance changes.
        labelPaint.textSize = (34.0 * proj.scale * focalPx).toFloat().coerceIn(14f, 28f)
        draw.drawContext.canvas.nativeCanvas.drawText(
            "${band.points}",
            sx,
            sy - (labelPaint.textSize * 0.35f),
            labelPaint,
        )
        prevMaxMissM = band.maxMissM
    }
}

private fun gameGreenRadiusAt(targetM: Double): Double = 6.0 * targetM / 140.0
