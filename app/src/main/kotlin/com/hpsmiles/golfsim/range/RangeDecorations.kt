// app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeDecorations.kt
package com.hpsmiles.golfsim.range

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors

/**
 * Shared painted-range scenery used by both the range and the games. Mirrors
 * PovRangeCanvas's ground layers, mow stripes, signs, and horizon haze so the
 * games read as the same facility. Kept in the range package because it is an
 * extraction of the range's own drawing logic.
 */
object RangeDecorations {

    // Painted-ground palette A - "Tour Broadcast" (same as PovRangeCanvas).
    private val SkyTop = Color(0xFFFCFDFE)
    private val SkyBottom = Color(0xFFD9E4EA)
    private val RoughBase = Color(0xFF1E4D26)
    private val Fairway = Color(0xFF3E8E43)
    private val StripeLight = Color(0xFF47A04C)
    private val StripeDark = Color(0xFF3A8440)
    private val Haze = Color.White.copy(alpha = 0.22f)

    /**
     * Draws sky, rough, fairway, mow stripes, and horizon haze. No targets,
     * grid, or signs — those are added by callers that need them.
     */
    fun DrawScope.drawRangeGround(cam: RangeCamera, w: Float, h: Float) {
        val focalPx = w * 1.10f
        val v0Px = h * 0.30f
        val centerX = w / 2f
        val horizonPx = v0Px - kotlin.math.tan(cam.pitchRad).toFloat() * focalPx

        if (horizonPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(SkyTop, SkyBottom), startY = 0f, endY = horizonPx,
                ),
                size = Size(w, horizonPx),
            )
        }
        val groundTop = horizonPx.coerceAtLeast(0f)
        drawRect(RoughBase, topLeft = Offset(0f, groundTop), size = Size(w, h - groundTop))

        val fairway = groundPath(cam, w, h, RangeScene.fairwayOutline())
        if (fairway != null) {
            val nearest = fairwayNearestDepth(cam)
            drawPath(fairway, Fairway.copy(alpha = PovProjector.nearFadeFactor(nearest).toFloat()))
        }

        for (index in 0 until RangeScene.stripeBandCount()) {
            val (yFrom, yTo) = RangeScene.stripeBand(index)
            if (yFrom >= RangeScene.FAIRWAY_END_Y) break
            val halfFrom = RangeScene.fairwayHalfWidth(yFrom)
            val halfTo = RangeScene.fairwayHalfWidth(yTo)
            val stripe = groundPath(
                cam, w, h,
                listOf(-halfFrom to yFrom, halfFrom to yFrom, halfTo to yTo, -halfTo to yTo),
            )
            if (stripe != null) {
                val nearest = stripeNearestDepth(cam, yFrom, yTo)
                val base = if (RangeScene.stripeIsLight(0.0, yFrom)) StripeLight else StripeDark
                drawPath(stripe, base.copy(alpha = PovProjector.nearFadeFactor(nearest).toFloat()))
            }
        }

        drawRect(
            brush = Brush.verticalGradient(
                0f to Haze,
                1f to Color.Transparent,
                startY = horizonPx,
                endY = horizonPx + h * 0.15f,
            ),
            topLeft = Offset(0f, horizonPx),
            size = Size(w, h * 0.15f),
        )
    }

    /**
     * Standing distance signs like the range. The same world-fixed posts/boards
     * are projected through [cam]; the follow cam keeps them correct.
     */
    fun DrawScope.drawRangeSigns(cam: RangeCamera, w: Float, h: Float, labelPaint: Paint) {
        val focalPx = w * 1.10f
        val v0Px = h * 0.30f
        val centerX = w / 2f

        fun groundDepth(distM: Double): Double {
            val dy = distM - cam.y
            val cosPitch = kotlin.math.cos(cam.pitchRad)
            val sinPitch = kotlin.math.sin(cam.pitchRad)
            return dy * cosPitch - (-cam.z) * sinPitch
        }

        labelPaint.apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        for (sign in RangeSigns.signPlan()) {
            val d = sign.distanceM.toDouble()
            if (groundDepth(d) <= 0.0) continue
            val xL = sign.xM - RangeSigns.BOARD_HALF_W_M
            val xR = sign.xM + RangeSigns.BOARD_HALF_W_M
            val bl = worldToScreen(cam, v0Px, focalPx, centerX, xL, d, RangeSigns.POST_H_M) ?: continue
            val br = worldToScreen(cam, v0Px, focalPx, centerX, xR, d, RangeSigns.POST_H_M) ?: continue
            val tl = worldToScreen(cam, v0Px, focalPx, centerX, xL, d, RangeSigns.BOARD_TOP_Z_M) ?: continue
            val tr = worldToScreen(cam, v0Px, focalPx, centerX, xR, d, RangeSigns.BOARD_TOP_Z_M) ?: continue

            drawLine(GolfColors.Line, bl, tl, strokeWidth = 1f)
            drawLine(GolfColors.Line, br, tr, strokeWidth = 1f)

            val board = Path().apply {
                moveTo(bl.x, bl.y); lineTo(br.x, br.y); lineTo(tr.x, tr.y); lineTo(tl.x, tl.y); close()
            }
            drawPath(board, GolfColors.Card)
            drawPath(board, GolfColors.Line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
            drawLine(GolfColors.Teal, tl, tr, strokeWidth = 2f)

            val boardHpx = ((bl.y + br.y) / 2f - (tl.y + tr.y) / 2f)
            if (boardHpx > 3f) {
                labelPaint.textSize = boardHpx * 0.55f
                labelPaint.color = GolfColors.TextPrimary.toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    "${sign.distanceM}",
                    (tl.x + tr.x) / 2f,
                    (bl.y + br.y + tl.y + tr.y) / 4f + boardHpx * 0.18f,
                    labelPaint,
                )
            }
        }
    }

    /** Same screen mapping the games already use for their ground paths. */
    private fun worldToScreen(
        cam: RangeCamera,
        v0Px: Float,
        focalPx: Float,
        centerX: Float,
        x: Double,
        y: Double,
        z: Double,
    ): Offset? {
        val p = PovProjector.project(cam, x, y, z) ?: return null
        return Offset((centerX + p.u * focalPx).toFloat(), (v0Px + p.v * focalPx).toFloat())
    }

    /** Reusable ground polygon with near clip + fade helper. */
    internal fun groundPath(cam: RangeCamera, w: Float, h: Float, world: List<Pair<Double, Double>>): Path? {
        val clipped = PovProjector.clipGroundPath(world, cam)
        if (clipped.size < 3) return null
        val focalPx = w * 1.10f
        val v0Px = h * 0.30f
        val centerX = w / 2f
        val path = Path()
        var first = true
        for ((x, y) in clipped) {
            val p = PovProjector.project(cam, x, y, RangeScene.groundHeight(x, y)) ?: return null
            val sx = (centerX + p.u * focalPx).toFloat()
            val sy = (v0Px + p.v * focalPx).toFloat()
            if (first) { path.moveTo(sx, sy); first = false } else path.lineTo(sx, sy)
        }
        path.close()
        return if (first) null else path
    }

    /** Near depth of the fairway outline (used for the fade factor). */
    internal fun fairwayNearestDepth(cam: RangeCamera): Double {
        var nearest = Double.POSITIVE_INFINITY
        for ((_, y) in RangeScene.fairwayOutline()) {
            nearest = kotlin.math.min(nearest, PovProjector.groundDepth(cam, y))
        }
        return nearest
    }

    /** Near depth of a stripe band (front edge). */
    internal fun stripeNearestDepth(cam: RangeCamera, yFrom: Double, yTo: Double): Double {
        val yFront = kotlin.math.min(yFrom, yTo)
        return PovProjector.groundDepth(cam, yFront)
    }
}
