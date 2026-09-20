package com.hpsmiles.golfsim.range

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.physics.ShotResult

private val SKY_TOP = GolfColors.Panel
private val SKY_BOTTOM = GolfColors.Base
private val GROUND_TOP = Color(0xFF131A20)
private val GROUND_BOTTOM = GolfColors.Base

/**
 * Player-perspective range view. Static scene (bands, targets) plus the
 * current shot's tracer and landing pulse, driven by [playFraction] in 0..1
 * (1 = flight complete). All world-to-screen math goes through PovProjector.
 * The tracer arc is a stylized quadratic anchored to the real solve
 * endpoints (documented plan deviation: ShotResult carries no trajectory
 * samples; physical arcs are a Phase C design decision).
 */
@Composable
fun PovRangeCanvas(currentShot: ShotResult?, playFraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizonPx = h * 0.30f
        val focalPx = w * 1.10f
        val centerX = w / 2f

        // Sky and ground gradients.
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(SKY_TOP, SKY_BOTTOM), startY = 0f, endY = horizonPx,
            ),
            size = androidx.compose.ui.geometry.Size(w, horizonPx),
        )
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(GROUND_TOP, GROUND_BOTTOM), startY = horizonPx, endY = h,
            ),
            topLeft = Offset(0f, horizonPx),
            size = androidx.compose.ui.geometry.Size(w, h - horizonPx),
        )
        drawLine(GolfColors.Line, Offset(0f, horizonPx), Offset(w, horizonPx), 1f)

        // Plan-verbatim correction: use android.graphics.Paint directly for
        // native text (androidx Paint.asFrameworkPaint() was wrong in the draft).
        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 10.sp.toPx()
            color = GolfColors.TextMuted.toArgb()
        }

        // Distance bands on the ground: v = camH / distance.
        val bandDistances = listOf(50f, 100f, 150f, 200f)
        for (d in bandDistances) {
            val y = horizonPx + PovProjector.bandV(d.toDouble()) * focalPx
            drawLine(
                color = GolfColors.Line.copy(alpha = 0.8f),
                start = Offset(0f, y.toFloat()),
                end = Offset(w, y.toFloat()),
                strokeWidth = 1f,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${d.toInt()} M", w - 8.sp.toPx() * 3, y.toFloat() - 4.sp.toPx(), labelPaint,
            )
        }

        // Teal target ovals at (lateral, distance) metres.
        val targets = listOf(-12f to 75f, 0f to 100f, 12f to 150f)
        val targetRadiusM = 5f
        for ((lateralM, distM) in targets) {
            val centre = PovProjector.project(lateralM.toDouble(), distM.toDouble(), 0.0) ?: continue
            val cx = centerX + centre.u * focalPx
            val cy = horizonPx + centre.v * focalPx
            // Ground circle: horizontal radius projects directly; vertical
            // extent from the near/far edge band difference.
            val rx = (targetRadiusM / distM) * focalPx
            val nearV = PovProjector.bandV((distM - targetRadiusM).toDouble())
            val farV = PovProjector.bandV((distM + targetRadiusM).toDouble())
            // Mechanical: bandV returns Double — Size() needs Float.
            val ry = (((nearV - farV) / 2.0) * focalPx).toFloat()
            drawOval(
                color = GolfColors.Teal,
                topLeft = Offset((cx - rx).toFloat(), (cy - ry).toFloat()),
                size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
                style = Stroke(width = 1.5f),
            )
            drawOval(
                color = GolfColors.Teal.copy(alpha = 0.4f),
                topLeft = Offset((cx - rx / 2f).toFloat(), (cy - ry / 2f).toFloat()),
                size = androidx.compose.ui.geometry.Size(rx, ry),
                style = Stroke(width = 1f),
            )
        }

        if (currentShot != null) {
            val s = currentShot
            fun worldToScreen(x: Double, y: Double, z: Double): Offset? {
                val p = PovProjector.project(x, y, z) ?: return null
                return Offset((centerX + p.u * focalPx).toFloat(), (horizonPx + p.v * focalPx).toFloat())
            }

            // Tracer: stylized quadratic through real solve endpoints.
            val points = 40
            val drawn = (points * playFraction.coerceIn(0f, 1f)).toInt()
            if (drawn > 0) {
                val path = androidx.compose.ui.graphics.Path()
                var first = true
                for (i in 0..drawn) {
                    val t = i.toFloat() / points
                    val x = s.sideM * t
                    val y = s.carryM * t
                    val z = 4.0 * s.apexM * t * (1.0 - t)
                    val point = worldToScreen(x, y, z) ?: break
                    if (first) { path.moveTo(point.x, point.y); first = false }
                    else path.lineTo(point.x, point.y)
                }
                if (!first) {
                    drawPath(path, GolfColors.Amber, style = Stroke(width = 2.5f))
                }
                // Ball dot at the current tracer head.
                val t = drawn.toFloat() / points
                val head = worldToScreen(s.sideM * t, s.carryM * t, 4.0 * s.apexM * t * (1.0 - t))
                if (head != null) {
                    drawCircle(GolfColors.AmberGlow, radius = 9.sp.toPx(), center = head)
                    drawCircle(GolfColors.Amber, radius = 5.sp.toPx(), center = head)
                }
            }

            // Landing pulse once the flight has completed.
            if (playFraction >= 1f) {
                val landing = worldToScreen(s.sideM, s.carryM + s.rolloutM * 0.0, 0.0)
                if (landing != null) {
                    val pulse = ((playFraction - 1f) * 2f) % 1f
                    drawCircle(
                        GolfColors.AmberHalo,
                        radius = (6f + 18f * pulse).sp.toPx() * 0.8f,
                        center = landing,
                        style = Stroke(width = 2f),
                    )
                    drawCircle(GolfColors.Amber, radius = 4.sp.toPx(), center = landing)
                }
            }
        }
    }
}
