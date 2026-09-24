package com.hpsmiles.golfsim.range

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.physics.ShotResult

// Painted-ground palette A — "Tour Broadcast" (approved spec palette).
private val SKY_TOP = Color(0xFF6FA8DC)
private val SKY_BOTTOM = Color(0xFFC9E2F5)
private val ROUGH_BASE = Color(0xFF1E4D26)
private val FAIRWAY = Color(0xFF3E8E43)
private val STRIPE_LIGHT = Color(0xFF47A04C)
private val STRIPE_DARK = Color(0xFF3A8440)
private val GREEN_SURFACE = Color(0xFF5FBF63)
private val FRINGE = Color(0xFF2F6E35)
private val HAZE = Color.White.copy(alpha = 0.22f)

// Previous-shot tracer lines: faded teal per the M3 design language
// (history = teal, live moment = amber).
private val HISTORY_LINE = GolfColors.Teal.copy(alpha = 0.35f)

/**
 * Player-perspective range view. Painted "Tour Broadcast" scene (rough base,
 * fairway, mow stripes, greens with fringes, horizon haze; bands and target
 * ovals stay on top) plus the current shot's tracer and landing pulse, driven
 * by [playFraction] in 0..1 (1 = flight complete), and faded tracer lines for
 * [previousShots].
 *
 * All world-to-screen math goes through PovProjector. Ground layers are flat
 * polygons: world vertices at z = RangeScene.groundHeight are projected into
 * closed Paths, with vertices at/behind the camera clipped (y > 0.5) or the
 * layer skipped entirely. The tracer arc is still a stylized quadratic
 * anchored to the real solve endpoints this phase; the modelled ODE flight
 * samples replace it in the following task.
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

        // Sky, then painted ground back-to-front: rough base (full bleed),
        // fairway, mow stripes, greens with fringes. All layers are flat
        // polygons at z = groundHeight in front of the camera.
        drawRect(
            brush = Brush.verticalGradient(
                listOf(SKY_TOP, SKY_BOTTOM), startY = 0f, endY = horizonPx,
            ),
            size = Size(w, horizonPx),
        )
        drawRect(ROUGH_BASE, topLeft = Offset(0f, horizonPx), size = Size(w, h - horizonPx))

        // Flat-ground polygon: project world vertices (at ground height) into
        // a closed Path. Vertices at/behind the camera (y <= 0.5) are clipped
        // away; if any remaining vertex still fails to project, the layer is
        // skipped rather than drawn malformed.
        fun groundPath(vertices: List<Pair<Double, Double>>): Path? {
            val visible = vertices.filter { it.second > 0.5 }
            if (visible.size < 3) return null
            val path = Path()
            var first = true
            for ((x, y) in visible) {
                val p = worldToScreen(centerX, focalPx, horizonPx, x, y, RangeScene.groundHeight(x, y))
                    ?: return null
                if (first) { path.moveTo(p.x, p.y); first = false } else path.lineTo(p.x, p.y)
            }
            path.close()
            return path
        }

        val fairway = groundPath(RangeScene.fairwayOutline())
        if (fairway != null) drawPath(fairway, FAIRWAY)

        // Alternating 12 m mow stripes as trapezoids clipped to the fairway.
        for (index in 0..14) {
            val (yFrom, yTo) = RangeScene.stripeBand(index)
            if (yFrom >= RangeScene.FAIRWAY_END_Y) break
            val halfFrom = RangeScene.fairwayHalfWidth(yFrom)
            val halfTo = RangeScene.fairwayHalfWidth(yTo)
            val stripe = groundPath(
                listOf(-halfFrom to yFrom, halfFrom to yFrom, halfTo to yTo, -halfTo to yTo),
            )
            if (stripe != null) {
                drawPath(stripe, if (RangeScene.stripeIsLight(0.0, yFrom)) STRIPE_LIGHT else STRIPE_DARK)
            }
        }

        // Greens: fringe ring first, putting surface on top.
        for (green in RangeScene.greens) {
            if (green.distanceM < 8.0) continue // near edge would sit at/behind the camera
            val fringe = groundPath(
                RangeScene.circleOutline(green.lateralM, green.distanceM, green.fringeRadiusM),
            ) ?: continue
            drawPath(fringe, FRINGE)
            val surface = groundPath(
                RangeScene.circleOutline(green.lateralM, green.distanceM, green.radiusM),
            ) ?: continue
            drawPath(surface, GREEN_SURFACE)
        }

        // Plan-verbatim correction: use android.graphics.Paint directly for
        // native text (androidx Paint.asFrameworkPaint() was wrong in the draft).
        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 10.sp.toPx()
            color = Color.White.toArgb()
        }

        // Distance bands on the ground: v = camH / distance. White for
        // "Tour Broadcast" crispness against the painted fairway.
        val bandDistances = listOf(50f, 100f, 150f, 200f)
        for (d in bandDistances) {
            val y = horizonPx + PovProjector.bandV(d.toDouble()) * focalPx
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
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

        // Horizon haze, last over the painted ground: a soft white fade at
        // the top of the rough blending the scene into the sky.
        drawRect(
            brush = Brush.verticalGradient(
                0f to HAZE,
                1f to Color.Transparent,
                startY = horizonPx,
                endY = horizonPx + h * 0.15f,
            ),
            topLeft = Offset(0f, horizonPx),
            size = Size(w, h * 0.15f),
        )

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
