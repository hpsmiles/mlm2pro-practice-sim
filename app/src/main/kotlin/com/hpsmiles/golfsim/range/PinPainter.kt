package com.hpsmiles.golfsim.range

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hpsmiles.golfsim.core.designsystem.GolfColors

/**
 * Shared graphic pin for every POV view (games, combine, and the range's
 * practice green): flagstick + pennant, flat style, scaled up for visibility.
 * Restyled 2026-10-15 for the light concentric-ring greens: a dark outline
 * wraps the pole and flag so the pin reads on the pale rings, and the pennant
 * is a classic red (Amber stays reserved for the live moment, Teal for
 * history) with a gently wavy trailing edge so it reads as a flapping flag.
 *
 * The screen mapping here mirrors the shared POV frame (focal = 1.10w,
 * v0 = 0.30h) used by [GameScene] and PovRangeCanvas — the world-position /
 * projection math is unchanged from the previous games/GameScene painter.
 */
object PinPainter {

    /** Classic red flag — distinct from Amber (live) and Teal (history). */
    private val FLAG_RED = Color(0xFFE0322F)

    /** Near-black wrap so the pin separates from the pale ring tones. */
    private val PIN_OUTLINE = Color(0xFF101417)

    /**
     * Graphic pole width in world metres (the pin is already stylised: a
     * 5.25 m stick with an oversized flag). Projected as
     * `POLE_WIDTH_M * focalPx * scale` so width, length and pennant all scale
     * with perspective together. Calibrated to the previous fixed look:
     * ~8.125 px outline at ~100 m.
     */
    private const val POLE_WIDTH_M = 0.30

    fun DrawScope.drawPin(
        cam: RangeCamera,
        w: Float,
        h: Float,
        cx: Double,
        cy: Double,
        heightM: Double = 5.25, // 4.2 m x 1.25 (2026-10-15 size bump)
    ) {
        val base = PovProjector.project(cam, cx, cy, 0.0) ?: return
        val top = PovProjector.project(cam, cx, cy, heightM) ?: return
        val focalPx = w * 1.10f
        val b = Offset(w / 2f + (base.u * focalPx).toFloat(), h * 0.30f + (base.v * focalPx).toFloat())
        val t = Offset(w / 2f + (top.u * focalPx).toFloat(), h * 0.30f + (top.v * focalPx).toFloat())

        // Pole: classic banded flagstick — a dark outline underlay, then four
        // equal alternating white/black sections from base to top. Fixed
        // screen-space band COUNT (not world-scaled): at 50-150 m the stick
        // is only tens of pixels, so true ~30 cm bands would alias; four
        // bands stay legible at every distance and read like the real pin.
        //
        // Width is DEPTH-SCALED (2026-10-15): it uses the same perspective
        // projection as the pole length and pennant, so the whole pin shrinks
        // uniformly with distance (fixed screen widths made the pole ~5:1 fat
        // at 200 m). POLE_WIDTH_M = 0.30 m is calibrated so the outline is
        // ~8.125 px at ~100 m — the previous look; the 2.5 px floor keeps the
        // stick visible far out.
        val poleW = (POLE_WIDTH_M * focalPx * top.scale).coerceAtLeast(2.5).toFloat()
        drawLine(PIN_OUTLINE, b, t, strokeWidth = poleW)
        val bandCount = 4
        for (i in 0 until bandCount) {
            val from = Offset(
                t.x + (b.x - t.x) * i / bandCount,
                t.y + (b.y - t.y) * i / bandCount,
            )
            val to = Offset(
                t.x + (b.x - t.x) * (i + 1) / bandCount,
                t.y + (b.y - t.y) * (i + 1) / bandCount,
            )
            val ink = if (i % 2 == 0) GolfColors.TextPrimary else PIN_OUTLINE
            drawLine(ink, from, to, strokeWidth = (poleW * 0.385f).coerceAtLeast(1f))
        }

        // Pennant: straight top edge to the tip, gently wavy trailing edge
        // (cubic scoop) back to the pole — static but reads as flapping.
        // Perspective-scaled like the pole; a 6 px floor keeps the flag
        // visible at long range.
        val px = (1.3125 * focalPx * top.scale).coerceAtLeast(6.0)
        val pennant = Path().apply {
            moveTo(t.x, t.y)
            lineTo(t.x + px.toFloat(), t.y + (px * 0.30).toFloat())
            cubicTo(
                (t.x + px * 0.82).toFloat(), (t.y + px * 0.62).toFloat(),
                (t.x + px * 0.62).toFloat(), (t.y + px * 0.50).toFloat(),
                t.x, t.y + (px * 0.72).toFloat(),
            )
            close()
        }
        drawPath(pennant, FLAG_RED)
        drawPath(pennant, PIN_OUTLINE, style = Stroke(width = 1.875f)) // 1.5 x 1.25
    }
}
