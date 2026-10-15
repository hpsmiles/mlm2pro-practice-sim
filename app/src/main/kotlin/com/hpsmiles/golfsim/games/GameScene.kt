package com.hpsmiles.golfsim.games

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.GreenRings
import com.hpsmiles.golfsim.range.PinPainter
import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera
import com.hpsmiles.golfsim.range.RangeDecorations
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

    /**
     * Game ground: same sky, fairway, mow stripes, distance boards, hitting
     * mat, and horizon haze as the range, but no practice grid or target
     * ovals. Item 4 (2026-10-01): the range mat renders in every mode.
     */
    fun DrawScope.drawGameGround(cam: RangeCamera, w: Float, h: Float, labelPaint: Paint) {
        with(RangeDecorations) {
            drawRangeGround(cam, w, h)
            drawRangeMat(cam, w, h)
            drawRangeSigns(cam, w, h, labelPaint)
        }
    }

    /**
     * Game green: putting surface only, painted as alternating very-light /
     * light green concentric rings (user request 2026-10-15) via the shared
     * [GreenRings] banding — 2 m rings, thinner outermost when the radius
     * isn't a multiple of 2 m. The range keeps its fringe ring; games do not,
     * because the fringe looked like a scoring ring the user thought should
     * count.
     */
    fun DrawScope.drawGreen(cam: RangeCamera, w: Float, h: Float, cx: Double, cy: Double, radiusM: Double) {
        val radii = GreenRings.boundaryRadii(radiusM)
        for ((index, r) in radii.withIndex()) {
            groundPath(cam, w, h, RangeScene.circleOutline(cx, cy, r))?.let { path ->
                val color = if (GreenRings.isVeryLightRing(index)) {
                    GolfColors.GreenRingVeryLight
                } else {
                    GolfColors.GreenRingLight
                }
                drawPath(path, color)
            }
        }
    }

    /**
     * Graphic pin, shared across all POV views — the painting lives in
     * [PinPainter] (range/) so the range POV uses it without depending on
     * games. Kept here as a shim for the existing `with(GameScene)` callers.
     */
    fun DrawScope.drawPin(cam: RangeCamera, w: Float, h: Float, cx: Double, cy: Double, heightM: Double = 5.25) {
        with(PinPainter) { drawPin(cam, w, h, cx, cy, heightM) }
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
