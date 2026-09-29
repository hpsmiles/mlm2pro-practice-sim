package com.hpsmiles.golfsim.games

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera
import com.hpsmiles.golfsim.range.RangeScene

/** Archery-inspired target face palette (outer to inner). Amber is reserved. */
private val BullseyeOuter = Color(0xFFDCE3E9)      // white-ish
private val BullseyeBlack = Color(0xFF2A3038)      // dark slate
private val BullseyeBlue = Color(0xFF3C6E9C)       // muted blue
private val BullseyeRed = Color(0xFFC23B3B)        // red
private val BullseyeGold = Color(0xFFD4A72D)       // distinct gold, not amber
private val BullseyeRings = listOf(BullseyeOuter, BullseyeBlack, BullseyeBlue, BullseyeRed)

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
            val radiusM = game.greenRadiusM()
            drawGreen(camera, w, h, 0.0, game.targetM, radiusM)
            val bands = TargetPracticeScoring.bands(game.difficulty)
            // Archery-style scoring bands, drawn largest first so the smaller
            // (inner, higher-value) rings overwrite the larger ones.
            bands.asReversed().forEachIndexed { i, band ->
                groundPath(camera, w, h, RangeScene.circleOutline(0.0, game.targetM, band.maxMissM))?.let {
                    val color = BullseyeRings.getOrElse(i) { BullseyeGold }
                    draw.drawPath(it, color.copy(alpha = 0.82f))
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
        typeface = Typeface.DEFAULT_BOLD
    }
    val focalPx = w * 1.10f
    val v0Px = h * 0.30f
    val centerX = w / 2f
    // Fixed bearing for labels: slightly left/down from centre (7 o'clock).
    val bearingRad = Math.toRadians(210.0)
    val cosB = kotlin.math.cos(bearingRad)
    val sinB = kotlin.math.sin(bearingRad)

    fun radiusForBand(band: TargetPracticeScoring.Band, prevMaxMissM: Double?): Double {
        val inner = prevMaxMissM ?: 0.0
        return (inner + band.maxMissM) / 2.0
    }

    var prevMaxMissM: Double? = null
    bands.forEachIndexed { index, band ->
        val color = BullseyeRings.getOrElse(bands.size - 1 - index) { BullseyeGold }
        val r = radiusForBand(band, prevMaxMissM)
        val worldX = r * cosB
        val worldY = targetM + r * sinB
        val proj = PovProjector.project(cam, worldX, worldY, 0.0) ?: return@forEachIndexed
        val sx = centerX + (proj.u * focalPx).toFloat()
        val sy = v0Px + (proj.v * focalPx).toFloat()
        // Text size scales with the projected scale at that depth so labels
        // grow/shrink consistently as the target distance changes.
        labelPaint.textSize = (34.0 * proj.scale * focalPx).toFloat().coerceIn(14f, 28f)
        // Light text on dark rings, dark text on light rings.
        labelPaint.color = (if (color == BullseyeOuter) GolfColors.TextPrimary else GolfColors.Base).toArgb()
        draw.drawContext.canvas.nativeCanvas.drawText(
            "${band.points}",
            sx,
            sy - (labelPaint.textSize * 0.35f),
            labelPaint,
        )
        prevMaxMissM = band.maxMissM
    }
}
