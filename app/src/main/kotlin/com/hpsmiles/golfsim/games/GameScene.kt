package com.hpsmiles.golfsim.games

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera
import com.hpsmiles.golfsim.range.RangeRollout
import com.hpsmiles.golfsim.range.RangeScene

/**
 * Shared POV drawing for both game canvases. Mirrors PovRangeCanvas's screen
 * mapping (focal = 1.10w, v0 = 0.30h) and ground clipping so the follow cam
 * chases the exact drawn geometry, same as the range.
 *
 * Tracers are drawn from the same frame-scaled + rolled-out sample list that
 * [FollowCam] consumes, so big apexes stay clamped to the top 8% and the
 * rollout segment appears during the landing hold.
 */
object GameScene {

    fun focalPx(w: Float): Float = w * 1.10f
    fun v0Px(h: Float): Float = h * 0.30f

    /** Same apex-clamp line RangeScreen derives (v0 - 8% top margin). */
    fun apexVMin(w: Float, h: Float): Double = (0.08 * h - v0Px(h)) / focalPx(w)

    fun toScreen(w: Float, h: Float, p: PovProjector.ProjectedPoint): Offset =
        Offset(w / 2f + (p.u * focalPx(w)).toFloat(), v0Px(h) + (p.v * focalPx(w)).toFloat())

    fun project(cam: RangeCamera, w: Float, h: Float, x: Double, y: Double, z: Double): Offset? =
        PovProjector.project(cam, x, y, z)?.let { toScreen(w, h, it) }

    /** Ground polygon (world x,y vertices, z=0) -> near-clipped screen Path. */
    fun groundPath(cam: RangeCamera, w: Float, h: Float, world: List<Pair<Double, Double>>): Path? {
        val clipped = PovProjector.clipGroundPath(world, cam)
        if (clipped.size < 3) return null
        val path = Path()
        var first = true
        for ((x, y) in clipped) {
            val p = PovProjector.project(cam, x, y, 0.0) ?: continue
            val s = toScreen(w, h, p)
            if (first) { path.moveTo(s.x, s.y); first = false } else path.lineTo(s.x, s.y)
        }
        path.close()
        return if (first) null else path
    }

    /** Screen path for a closed polygon of world corners (any z). */
    fun quadPath(cam: RangeCamera, w: Float, h: Float, corners: List<Triple<Double, Double, Double>>): Path? {
        val path = Path()
        var first = true
        for ((x, y, z) in corners) {
            val p = PovProjector.project(cam, x, y, z) ?: return null
            val s = toScreen(w, h, p)
            if (first) { path.moveTo(s.x, s.y); first = false } else path.lineTo(s.x, s.y)
        }
        path.close()
        return if (first) null else path
    }

    private val GreenTurf = Color(0xFF1C3A30)
    private val FringeTurf = Color(0xFF16291F)

    fun DrawScope.drawGameGround(cam: RangeCamera, w: Float, h: Float) {
        drawRect(GolfColors.Base) // sky/backdrop
        groundPath(cam, w, h, RangeScene.fairwayOutline())?.let { drawPath(it, GolfColors.HoleFairway) }
    }

    fun DrawScope.drawGreen(cam: RangeCamera, w: Float, h: Float, cx: Double, cy: Double, radiusM: Double) {
        groundPath(cam, w, h, RangeScene.circleOutline(cx, cy, radiusM * 1.35))?.let { drawPath(it, FringeTurf) }
        groundPath(cam, w, h, RangeScene.circleOutline(cx, cy, radiusM))?.let { drawPath(it, GreenTurf) }
    }

    /** Graphic pin: flagstick + pennant, flat style (spec section 4/5). */
    fun DrawScope.drawPin(cam: RangeCamera, w: Float, h: Float, cx: Double, cy: Double, heightM: Double = 2.4) {
        val base = PovProjector.project(cam, cx, cy, 0.0) ?: return
        val top = PovProjector.project(cam, cx, cy, heightM) ?: return
        val b = toScreen(w, h, base)
        val t = toScreen(w, h, top)
        drawLine(GolfColors.TextPrimary, b, t, strokeWidth = 2f)
        val px = 0.6 * focalPx(w) * top.scale // ~0.6 m pennant at that depth
        val pennant = Path().apply {
            moveTo(t.x, t.y)
            lineTo(t.x + px.toFloat(), t.y + (px * 0.35).toFloat())
            lineTo(t.x, t.y + (px * 0.7).toFloat())
            close()
        }
        drawPath(pennant, GolfColors.TextPrimary)
    }

    /**
     * Amber live-moment tracer + rest dot (amber reserved for the live moment).
     * [apexVMin] must be the same frame clamp line passed to [FollowCam.cameraAt],
     * otherwise the tracer and camera diverge on high-apex shots.
     */
    fun DrawScope.drawTracer(cam: RangeCamera, w: Float, h: Float, result: ShotResult, fraction: Float, apexVMin: Double = FollowCam.REFERENCE_APEX_VMIN) {
        val samples = if (result.flightTimeSec <= 0.0) {
            FollowCam.scaledSamples(result, apexVMin)
        } else {
            FollowCam.scaledSamples(result, apexVMin) + RangeRollout.samples(result)
        }
        if (samples.size < 2) return
        val n = (samples.size * fraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(2)
        val path = Path()
        var first = true
        for (s in samples.subList(0, n)) {
            val p = PovProjector.project(cam, s.px, s.py, s.pz) ?: continue
            val sc = toScreen(w, h, p)
            if (first) { path.moveTo(sc.x, sc.y); first = false } else path.lineTo(sc.x, sc.y)
        }
        if (!first) drawPath(path, GolfColors.Amber, style = Stroke(3f))
        if (fraction >= 1f) {
            project(cam, w, h, result.restX, result.restY, 0.0)?.let { drawCircle(GolfColors.Amber, 5f, it) }
        }
    }
}
