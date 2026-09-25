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
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import kotlin.math.max
import kotlin.math.tan

// Painted-ground palette A — "Tour Broadcast" (approved spec palette).
// Sky switched to a white background at user request (2026-09-24).
private val SKY_TOP = Color(0xFFFCFDFE)
private val SKY_BOTTOM = Color(0xFFD9E4EA)
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
 * Everything is projected through [camera] (spec 2026-09-25: the FollowCam
 * moves the rig per frame, and the static rig is the 8 m crane position).
 * The world horizon is derived from the camera pitch (v = -tan(pitch) at
 * infinite distance); the sky is only drawn above it.
 *
 * Visual clamps (user demo-gate feedback): the apex scale is computed
 * against the STATIC rig only (so trajectory geometry stays constant while
 * the follow cam moves), and the waiting tee ball gets a minimum on-screen
 * radius (a true-scale ball is ~2 px at 21.2 m).
 */
@Composable
fun PovRangeCanvas(
    currentShot: ShotResult?,
    previousShots: List<ShotResult>,
    playFraction: Float,
    showTracer: Boolean,
    showHistory: Boolean,
    modifier: Modifier = Modifier,
    camera: RangeCamera = RangeCamera.STATIC,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val focalPx = w * 1.10f
        val centerX = w / 2f
        // Screen y of v = 0 (camera level). The world horizon sits at
        // v = -tan(pitch) — it moves up the frame as the camera noses down.
        val v0Px = h * 0.30f
        val horizonPx = v0Px - tan(camera.pitchRad).toFloat() * focalPx

        // Sky, then painted ground back-to-front: rough base (full bleed),
        // fairway, mow stripes, greens with fringes. All layers are flat
        // polygons at z = groundHeight in front of the camera.
        if (horizonPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(SKY_TOP, SKY_BOTTOM), startY = 0f, endY = horizonPx,
                ),
                size = Size(w, horizonPx),
            )
        }
        val groundTop = horizonPx.coerceAtLeast(0f)
        drawRect(ROUGH_BASE, topLeft = Offset(0f, groundTop), size = Size(w, h - groundTop))

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
                val p = worldToScreen(camera, v0Px, focalPx, centerX, x, y, RangeScene.groundHeight(x, y))
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

        // Distance bands on the ground: screen-horizontal under pitch
        // (rotation is about the lateral axis). White for "Tour Broadcast"
        // crispness against the painted fairway.
        val bandDistances = listOf(50f, 100f, 150f, 200f)
        for (d in bandDistances) {
            val y = v0Px + PovProjector.bandV(camera, d.toDouble()) * focalPx
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
            val centre = PovProjector.project(camera, lateralM.toDouble(), distM.toDouble(), 0.0) ?: continue
            val cx = centerX + centre.u * focalPx
            val cy = v0Px + centre.v * focalPx
            // Ground circle: horizontal radius from the projection scale;
            // vertical extent from the near/far edge band difference.
            val rx = (targetRadiusM * centre.scale * focalPx).toFloat()
            val nearV = PovProjector.bandV(camera, (distM - targetRadiusM).toDouble())
            val farV = PovProjector.bandV(camera, (distM + targetRadiusM).toDouble())
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
        // the world horizon blending the scene into the sky.
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
        val vMin = (h * 0.08f - v0Px) / focalPx

        // Real-trajectory polyline: project the modelled flight samples.
        // apexScale (single scalar, top-8% rule) keeps big apexes in frame;
        // it is computed against the STATIC rig only so the drawn geometry
        // does not breathe while the follow cam moves.
        fun scaledSamples(s: ShotResult): List<TrajectorySample> {
            if (s.samples.isEmpty()) return emptyList()
            val yApex = s.carryM / 2.0
            val depthApex = yApex + PovProjector.CAM_BACK_M
            val vApex = (PovProjector.CAM_HEIGHT_M - s.apexM) / depthApex
            val apexScale = if (vApex < vMin) {
                ((PovProjector.CAM_HEIGHT_M - vMin * depthApex) / s.apexM).coerceIn(0.1, 1.0)
            } else {
                1.0
            }
            if (apexScale >= 1.0) return s.samples
            return s.samples.map { it.copy(pz = it.pz * apexScale) }
        }

        fun drawTracer(s: ShotResult, timeSec: Double, color: Color, width: Float) {
            val samples = scaledSamples(s)
            if (samples.size < 2) return
            // Screen-space clip: collect only in-frame projections, keeping
            // sample indices so we can break the path across frame-exit gaps.
            val pts = ArrayList<Pair<Int, Offset>>()
            for (i in samples.indices) {
                if (samples[i].tSec > timeSec) break
                val p = worldToScreen(camera, v0Px, focalPx, centerX, samples[i].px, samples[i].py, samples[i].pz)
                    ?: continue
                if (p.y > h - 8f) continue
                pts.add(i to p)
            }
            if (pts.isEmpty()) return
            val path = Path()
            path.moveTo(pts[0].second.x, pts[0].second.y)
            for (j in 1 until pts.size) {
                if (pts[j].first - pts[j - 1].first > 30) {
                    // Frame exit gap: resume as a new sub-path, never draw
                    // a straight line across it.
                    path.moveTo(pts[j].second.x, pts[j].second.y)
                } else {
                    path.lineTo(pts[j].second.x, pts[j].second.y)
                }
            }
            // Tangent lead-in: extend the arc's own first segment backwards to
            // the bottom edge (when the arc starts low in frame), so the launch
            // lead-in is exactly collinear with the curve — no fixed anchor
            // point, no elbow where it joins.
            if (pts.size >= 2 && pts[1].first == pts[0].first + 1 && pts[0].second.y > h * 0.6f) {
                val p0 = pts[0].second
                val p1 = pts[1].second
                val dy = p1.y - p0.y
                if (dy < -1f) {
                    val u = ((h - 8f) - p0.y) / dy
                    val qx = p0.x + u * (p1.x - p0.x)
                    if (u > 0.02f && qx >= 0f && qx <= w) {
                        path.moveTo(qx, h - 8f)
                        path.lineTo(p0.x, p0.y)
                    }
                }
            }
            drawPath(path, color, style = Stroke(width = width))
        }

        // Previous shots first (under the current tracer).
        if (showHistory) {
            for (prev in previousShots.asReversed()) {
                drawTracer(prev, Double.POSITIVE_INFINITY, HISTORY_LINE, 2f)
            }
        }

        // Static ball on the tee while waiting for a shot: a true world-ball
        // (~42.7 mm) projected at the tee position, sized by the projection
        // so it reads as an object at that depth, with a 4 px minimum
        // on-screen radius (spec 2026-09-25: a true-scale ball is ~2 px at
        // the 21.2 m crane rig).
        val ballRadiusM = 0.02135
        val waiting = currentShot == null || playFraction <= 0f || playFraction >= 1f
        if (showTracer && waiting) {
            val teeP = PovProjector.project(camera, 0.0, 0.0, ballRadiusM)
            if (teeP != null) {
                val tee = Offset(
                    (centerX + teeP.u * focalPx).toFloat(),
                    (v0Px + teeP.v * focalPx).toFloat(),
                )
                val rPx = max(4f, (ballRadiusM * teeP.scale * focalPx).toFloat())
                drawCircle(GolfColors.AmberGlow, radius = rPx * 2f, center = tee)
                drawCircle(GolfColors.Amber, radius = rPx, center = tee)
            }
        }

        if (currentShot != null) {
            val s = currentShot

            // Current tracer: full line persists after landing (fraction = 1).
            if (showTracer) {
                drawTracer(s, playFraction * s.flightTimeSec, GolfColors.Amber, 2.5f)
            }

            // Ball at the tracer head — the sample nearest the animation time —
            // clamped to the anchor until it clears the bottom edge, so the
            // ball is visible leaving the club.
            if (showTracer) {
                val timeSec = playFraction * s.flightTimeSec
                val samples = scaledSamples(s)
                val head = samples.lastOrNull { it.tSec <= timeSec } ?: samples.firstOrNull()
                var headPos = head?.let { worldToScreen(camera, v0Px, focalPx, centerX, it.px, it.py, it.pz) }
                if (headPos == null || headPos.y > h - 8f) headPos = launchAnchor
                drawCircle(GolfColors.AmberGlow, radius = 12.sp.toPx(), center = headPos)
                drawCircle(GolfColors.Amber, radius = 6.sp.toPx(), center = headPos)
            }

            // Landing dot plus ring once the flight has completed.
            if (playFraction >= 1f) {
                val landing = worldToScreen(camera, v0Px, focalPx, centerX, s.sideM, s.carryM, 0.0)
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
    camera: RangeCamera,
    v0Px: Float,
    focalPx: Float,
    centerX: Float,
    x: Double,
    y: Double,
    z: Double,
): Offset? {
    val p = PovProjector.project(camera, x, y, z) ?: return null
    return Offset((centerX + p.u * focalPx).toFloat(), (v0Px + p.v * focalPx).toFloat())
}
