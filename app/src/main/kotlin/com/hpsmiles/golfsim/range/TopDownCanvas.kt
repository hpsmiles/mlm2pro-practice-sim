package com.hpsmiles.golfsim.range

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors

/**
 * Top-down analytics view: range grid every 50 m, teal dispersion history
 * (55% alpha) and the amber last-shot landing.
 */
@Composable
fun TopDownCanvas(shots: List<DisplayShot>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(GolfColors.Base)

        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 10.sp.toPx()
            color = GolfColors.TextMuted.toArgb()
        }

        // World mapping: origin bottom-centre, x lateral ±60 m, y 0..250 m.
        val pxPerM = h / 260f
        val originX = w / 2f
        val originY = h - 8.sp.toPx()

        // Distance bands every 50 m across the full width.
        var distM = 50f
        while (distM <= 250f) {
            val y = originY - distM * pxPerM
            drawLine(
                GolfColors.Line.copy(alpha = 0.8f),
                Offset(0f, y), Offset(w, y), 1f,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${distM.toInt()} M", 8.sp.toPx(), y - 4.sp.toPx(), labelPaint,
            )
            distM += 50f
        }
        // Lateral gridlines every 20 m.
        var lateralM = -60f
        while (lateralM <= 60f) {
            val x = originX + lateralM * pxPerM
            if (x >= 0f && x <= w) {
                drawLine(
                    GolfColors.Line.copy(alpha = 0.5f),
                    Offset(x, 0f), Offset(x, originY), 1f,
                )
            }
            lateralM += 20f
        }

        val history = shots.dropLast(1)
        for (shot in history) {
            val x = originX + shot.shotResult.sideM * pxPerM
            val y = originY - shot.shotResult.carryM * pxPerM
            drawCircle(GolfColors.Teal.copy(alpha = 0.55f), radius = 3.sp.toPx(), center = Offset(x.toFloat(), y.toFloat()))
        }
        shots.lastOrNull()?.let { last ->
            val x = originX + last.shotResult.sideM * pxPerM
            val y = originY - last.shotResult.carryM * pxPerM
            drawCircle(GolfColors.AmberGlow, radius = 8.sp.toPx(), center = Offset(x.toFloat(), y.toFloat()))
            drawCircle(GolfColors.Amber, radius = 4.sp.toPx(), center = Offset(x.toFloat(), y.toFloat()))
        }
    }
}
