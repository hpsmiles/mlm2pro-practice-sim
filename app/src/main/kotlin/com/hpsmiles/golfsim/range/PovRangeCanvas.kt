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

// Previous-shot tracer lines: faded teal per the M3 design language
// (history = teal, live moment = amber).
private val HISTORY_LINE = GolfColors.Teal.copy(alpha = 0.35f)

/**
 * Player-perspective range view. Static scene (bands, targets) plus the
 * current shot's tracer and landing pulse, driven by [playFraction] in 0..1
 * (1 = flight complete), and faded tracer lines for [previousShots].
 *
 * All world-to-screen math goes through PovProjector. The tracer arc is a
 * stylized quadratic anchored to the real solve endpoints (documented plan
 * deviation: ShotResult carries no trajectory samples; physical arcs are a
 * Phase C design decision).
 *
 * Visual clamps (user demo-gate feedback): the stylized arc is scaled so its
 * apex stays inside the top 8% of the frame, and near-field points that
 * would project below the bottom edge are clamped to the launch anchor so
 * the ball is visible leaving the club.
 */
@Composable
fun PovRangeCanvas(
    currentShot: ShotResult?,
    previousShots: List<ShotResult>,
    playFraction: Float,
    showTracer: Boolean,
    showHistory: Boolean,
    modifier: Modifier = Modifier,
) {
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

        // The launch anchor: the tracer attaches here while the ball is too
        // close to project inside the frame, so the ball is visible leaving.
        val launchAnchor = Offset(centerX, h - 24f)
        // Most-negative projected v allowed (apex must stay below the top 8%).
        val vMin = (h * 0.08f - horizonPx) / focalPx

        fun tracerPath(s: ShotResult, fraction: Float): androidx.compose.ui.graphics.Path? {
            // Arc scaling so the apex stays inside the frame: at t = 0.5 the
            // quadratic peaks at apexM over carryM/2; if that projects above
            // the top margin, scale the arc's height down.
            val yApex = s.carryM / 2.0
            val vApex = (PovProjector.CAM_HEIGHT_M - s.apexM) / yApex
            val apexScale = if (vApex < vMin) {
                ((PovProjector.CAM_HEIGHT_M - vMin * yApex) / s.apexM).coerceIn(0.1, 1.0)
            } else {
                1.0
            }
            val points = 40
            val drawn = (points * fraction.coerceIn(0f, 1f)).toInt()
            if (drawn <= 0) return null
            val path = androidx.compose.ui.graphics.Path()
            var first = true
            for (i in 0..drawn) {
                val t = i.toFloat() / points
                val x = s.sideM * t
                val y = s.carryM * t
                val z = apexScale * 4.0 * s.apexM * t * (1.0 - t)
                var point = worldToScreen(centerX, focalPx, horizonPx, x, y, z)
                // Near-field clamp: below the bottom edge, hold the launch anchor.
                if (point == null || point.y > h - 8f) point = launchAnchor
                if (first) { path.moveTo(point.x, point.y); first = false } else path.lineTo(point.x, point.y)
            }
            return if (first) null else path
        }

        // Previous shots first (under the current tracer).
        if (showHistory) {
            for (prev in previousShots.asReversed()) {
                val path = tracerPath(prev, 1f) ?: continue
                drawPath(path, HISTORY_LINE, style = Stroke(width = 2f))
            }
        }

        if (currentShot != null) {
            val s = currentShot

            // Current tracer: full line persists after landing (fraction = 1).
            if (showTracer) {
                val path = tracerPath(s, playFraction)
                if (path != null) {
                    drawPath(path, GolfColors.Amber, style = Stroke(width = 2.5f))
                }
            }

            // Ball at the tracer head, clamped to the anchor until it clears
            // the bottom edge — visible leaving the club.
            if (showTracer) {
                val points = 40
                val drawn = (points * playFraction.coerceIn(0f, 1f)).toInt()
                if (drawn > 0) {
                    val t = drawn.toFloat() / points
                    val yApex = s.carryM / 2.0
                    val vApex = (PovProjector.CAM_HEIGHT_M - s.apexM) / yApex
                    val apexScale = if (vApex < vMin) {
                        ((PovProjector.CAM_HEIGHT_M - vMin * yApex) / s.apexM).coerceIn(0.1, 1.0)
                    } else {
                        1.0
                    }
                    var head = worldToScreen(
                        centerX, focalPx, horizonPx,
                        s.sideM * t, s.carryM * t, apexScale * 4.0 * s.apexM * t * (1.0 - t),
                    )
                    if (head == null || head.y > h - 8f) head = launchAnchor
                    drawCircle(GolfColors.AmberGlow, radius = 12.sp.toPx(), center = head)
                    drawCircle(GolfColors.Amber, radius = 6.sp.toPx(), center = head)
                }
            }

            // Landing dot plus ring once the flight has completed.
            if (playFraction >= 1f) {
                val landing = worldToScreen(centerX, focalPx, horizonPx, s.sideM, s.carryM, 0.0)
                if (landing != null) {
                    drawCircle(
                        GolfColors.AmberHalo,
                        radius = (6f * 0.8f).sp.toPx(),
                        center = landing,
                        style = Stroke(width = 2f),
                    )
                    drawCircle(GolfColors.Amber, radius = 4.sp.toPx(), center = landing)
                }
            }
        }
    }
}

/** Screen-space projection helper shared by the tracer routines. */
private fun worldToScreen(
    centerX: Float,
    focalPx: Float,
    horizonPx: Float,
    x: Double,
    y: Double,
    z: Double,
): Offset? {
    val p = PovProjector.project(x, y, z) ?: return null
    return Offset((centerX + p.u * focalPx).toFloat(), (horizonPx + p.v * focalPx).toFloat())
}
