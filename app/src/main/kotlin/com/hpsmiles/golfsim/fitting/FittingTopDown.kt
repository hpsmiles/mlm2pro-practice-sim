package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.fitting.FittingStats
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.range.ChipFont
import com.hpsmiles.golfsim.range.RangeMat
import com.hpsmiles.golfsim.range.RangeScene
import com.hpsmiles.golfsim.range.RangeSigns
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * M7 top-down (design notes §2.3, reoriented per spec §6): horizontal plan
 * view — tee on the LEFT, distance → right, lateral on the vertical axis
 * (positive side draws higher), mirroring `CombineTopDownView`. Shading is the
 * range POV's own ground palette (GolfColors.Range*): dark rough surrounds
 * the constant 28 m fairway corridor with its 10 m mown stripes (device
 * feedback 2026-10-12). Adaptive vertical distance gridlines ("N m" labels,
 * combine idiom) and right-edge lateral guide labels give scale at any zoom.
 * Kept shots are per-club coloured dots with ONE rotated minimum-enclosing
 * ellipse (Löwner / MVEE, scaled ×1.05) per club (≥3 kept shots; 55 % alpha,
 * 2 dp stroke, no fill — the same ellipse whose area the table's AREA column
 * reports, so ring and table agree), traced as a 72-step world-space path
 * (required under anisotropic scale — a rotated world ellipse is not a screen
 * oval). Excluded shots are never drawn — they stay visible in the table
 * drill-down. Legend chips (colour dot + name + count badge) wrap to a second
 * row beyond 4 clubs and are not tappable in v1.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FittingTopDownPane(
    shots: List<FittingShotEntity>,
    modifier: Modifier = Modifier,
) {
    val order = FittingStats.clubOrder(shots)
    val labelPaint = remember { android.graphics.Paint() }
    val keptCount = shots.count { !it.excluded }
    // One kept-shot set per club, computed once — exclusion stays the single
    // source of truth by construction and feeds the legend badge counts, the
    // ellipse inputs and the dots below (previously filtered 3× per pass).
    val keptByClub = order.associateWith { key -> shots.filter { it.clubId == key.id && !it.excluded } }
    Column(modifier.fillMaxSize()) {
        // Legend (notes §2.3): one chip per club — 10 dp colour dot + name
        // (ChipFont) + count badge (§0.3). Chips are not tappable in v1; the
        // FlowRow wraps beyond 4 clubs.
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = GolfSpacing.Md, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            order.forEachIndexed { index, key ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(FittingColors.clubColor(index)))
                    Text(key.name, style = ChipFont, color = GolfColors.TextPrimary, maxLines = 1)
                    FittingCountBadge(keptByClub[key].orEmpty().size)
                }
            }
        }
        if (order.size > 4) {
            // Same hint as the COMPARISON card (task 7): TextMuted — amber is
            // reserved for count badges only (notes §0.2).
            Text(
                "Many clubs — 4 or fewer compares best",
                style = GolfTypography.BodySmall,
                color = GolfColors.TextMuted,
                modifier = Modifier.padding(horizontal = GolfSpacing.Md),
            )
        }
        // clipToBounds: for zoomed windows the unguarded draws below (fairway
        // rect, centre/guide lines, mat, rings, dots) extend off-canvas by
        // design — they must never paint outside the pane (2026-10-12).
        Box(Modifier.fillMaxSize().clipToBounds()) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Rough everywhere (the range POV's rough green); the fairway
                // corridor shades over it below.
                drawRect(GolfColors.RangeRough)
                // Content-fit mapping (device feedback 2026-10-08), same bounds
                // rule as before — kept shots AND their enclosing-ellipse ring
                // extents plus a buffer drive the window (0–150 m of empty
                // range is useless on a driver fitting); full-range fallback
                // when there are no kept shots. Mapping is now the horizontal
                // contract (spec §6): tee left, distance → right.
                val ellipses = order.map { key ->
                    val kept = keptByClub[key].orEmpty()
                    DispersionEllipse.fromKept(kept.map { it.sideM to it.totalM })
                }
                var minDistM = Double.POSITIVE_INFINITY
                var maxDistM = Double.NEGATIVE_INFINITY
                var maxAbsSideM = 0.0
                var hasData = false
                order.forEachIndexed { index, key ->
                    val kept = keptByClub[key].orEmpty()
                    if (kept.isNotEmpty()) hasData = true
                    // Buffered enclosing ellipse (3+ kept): its projected
                    // extents subsume every kept point (containment is the
                    // whole point of the MVEE), so they drive the fit.
                    val ell = ellipses[index]
                    if (ell != null) {
                        val halfSide = ell.projectedHalfSideM()
                        val halfTotal = ell.projectedHalfTotalM()
                        minDistM = minOf(minDistM, ell.centreTotalM - halfTotal)
                        maxDistM = maxOf(maxDistM, ell.centreTotalM + halfTotal)
                        maxAbsSideM = maxOf(maxAbsSideM, abs(ell.centreSideM) + halfSide)
                    } else {
                        // 1–2 kept shots (no ellipse): per-shot extremes still
                        // shape the fit's bounds.
                        kept.forEach { s ->
                            minDistM = minOf(minDistM, s.totalM)
                            maxDistM = maxOf(maxDistM, s.totalM)
                            maxAbsSideM = maxOf(maxAbsSideM, abs(s.sideM))
                        }
                    }
                }
                val leftPadPx = 26.dp.toPx()
                val rightPadPx = 12.dp.toPx()
                val vPadPx = 12.dp.toPx()
                val fit = computeTopDownFitH(
                    width = w.toDouble(), height = h.toDouble(),
                    minDistM = minDistM, maxDistM = maxDistM,
                    maxAbsSideM = maxAbsSideM, hasData = hasData,
                    leftPadPx = leftPadPx.toDouble(),
                    rightPadPx = rightPadPx.toDouble(),
                    vPadPx = vPadPx.toDouble(),
                )
                fun sx(distM: Double) = (fit.originX + distM * fit.pxPerMX).toFloat()
                // Sign convention (verified 2026-10-16, M4c goldens): +side is
                // RIGHT of the target looking down-range — with the tee at the
                // left and distance increasing rightward, right-of-target draws
                // DOWN the screen.
                fun sy(sideM: Double) = (fit.midY + sideM * fit.pxPerMY).toFloat()

                // Fairway corridor (tee 10 m → 330 m, lateral ±14 m — the
                // constant 28 m band) in the range POV's fairway green, with
                // the same 10 m mown stripes the range shows (pure RangeScene
                // banding; subtle enough that dots/rings stay legible). The
                // base rect stays as a safety floor under the 32 stripe bands
                // (they tile 10–330 m exactly — this guards any future gap).
                val corridorHalfM = RangeScene.fairwayHalfWidth(0.0)
                drawRect(
                    color = GolfColors.RangeFairway,
                    topLeft = Offset(sx(RangeScene.FAIRWAY_TEE_Y), sy(corridorHalfM)),
                    size = Size(
                        sx(RangeScene.FAIRWAY_END_Y) - sx(RangeScene.FAIRWAY_TEE_Y),
                        sy(-corridorHalfM) - sy(corridorHalfM),
                    ),
                )
                for (band in 0 until RangeScene.stripeBandCount()) {
                    val (yFrom, yTo) = RangeScene.stripeBand(band)
                    drawRect(
                        color = if (RangeScene.stripeIsLight(0.0, yFrom)) {
                            GolfColors.RangeStripeLight
                        } else {
                            GolfColors.RangeStripeDark
                        },
                        topLeft = Offset(sx(yFrom), sy(corridorHalfM)),
                        size = Size(sx(yTo) - sx(yFrom), sy(-corridorHalfM) - sy(corridorHalfM)),
                    )
                }

                // Lateral gridlines every 20 m — horizontal lines spanning the
                // canvas (the old vertical grid rotated with the view).
                var lateralM = -60.0
                while (lateralM <= 60.0) {
                    val y = sy(lateralM)
                    if (y >= 0f && y <= h) {
                        drawLine(
                            GolfColors.Line.copy(alpha = 0.5f),
                            Offset(0f, y), Offset(w, y), 1f,
                        )
                    }
                    lateralM += 20.0
                }

                // Distance scale references (device feedback 2026-10-12):
                // vertical gridlines (combine parity α0.6) + "N m" labels at
                // the top edge, centred on each line. Labels are TextSecondary
                // — TextMuted is functionally invisible on the green rough
                // (≈1.7:1; review 2026-10-12). The step adapts to the visible
                // window (distanceGridStepM) — ≥ 2 references at fitting
                // zooms, no crowding at full range (50 m steps coincide with
                // the sign boards).
                val visLoM = (0.0 - fit.originX) / fit.pxPerMX
                val visHiM = (w.toDouble() - fit.originX) / fit.pxPerMX
                val stepM = distanceGridStepM(visHiM - visLoM)
                val distLabelBaselineY = 12.sp.toPx()
                labelPaint.apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 10.sp.toPx()
                    color = GolfColors.TextSecondary.toArgb()
                }
                var gridM = ceil(visLoM / stepM) * stepM
                while (gridM <= visHiM) {
                    val x = sx(gridM)
                    drawLine(GolfColors.Line.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, h), 1f)
                    drawContext.canvas.nativeCanvas.drawText("${gridM.toInt()} m", x, distLabelBaselineY, labelPaint)
                    gridM += stepM
                }

                // Practice-grid identity matching the POV view: a solid centre
                // line at side = 0 and light dashed guides at side = ±10/±20 m,
                // running from the tee to the ground end — horizontal now.
                val midY = fit.midY.toFloat()
                val teeLeft = sx(RangeScene.FAIRWAY_TEE_Y)
                val groundRight = sx(RangeScene.GROUND_END_Y)
                drawLine(
                    Color.White.copy(alpha = RangeScene.CENTER_LINE_ALPHA),
                    Offset(teeLeft, midY), Offset(groundRight, midY), 1.5f,
                )
                val dashPx = (RangeScene.GUIDE_DASH_ON_M * fit.pxPerMX).toFloat()
                val gapPx = (RangeScene.GUIDE_DASH_OFF_M * fit.pxPerMX).toFloat()
                for (guideM in RangeScene.GUIDE_LINE_LATERALS_M) {
                    val y = sy(guideM)
                    drawLine(
                        Color.White.copy(alpha = RangeScene.GUIDE_LINE_ALPHA),
                        Offset(teeLeft, y), Offset(groundRight, y), 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx)),
                    )
                }
                // Lateral references (device feedback 2026-10-12): each dashed
                // guide's distance from centre ("10 m" / "20 m"), 10.sp
                // right-aligned at the canvas edge just above its line — the
                // spacing stays readable at any zoom. Ink = white at the centre
                // line's alpha so it reads on BOTH the fairway band (the ±10 m
                // labels sit inside the ±14 m corridor) and the rough (±20 m)
                // — TextMuted was invisible there (review 2026-10-12) — while
                // thin text strokes stay subordinate to the α0.8 filled dots.
                labelPaint.apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                    textSize = 10.sp.toPx()
                    color = Color.White.copy(alpha = RangeScene.CENTER_LINE_ALPHA).toArgb()
                }
                for (guideM in RangeScene.GUIDE_LINE_LATERALS_M) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "${abs(guideM).toInt()} m",
                        w - rightPadPx,
                        sy(guideM) - 2.dp.toPx(),
                        labelPaint,
                    )
                }

                // Range mat at the origin (orientation cue; ball at (0, 0) —
                // right-handed golfer) — the world rect rotated: its distance
                // extent maps to screen x, its lateral extent to screen y.
                drawRect(
                    color = RangeMat.BASE,
                    topLeft = Offset(sx(RangeMat.BASE_Y_MIN), sy(RangeMat.BASE_X_MIN)),
                    size = Size(
                        sx(RangeMat.BASE_Y_MAX) - sx(RangeMat.BASE_Y_MIN),
                        sy(RangeMat.BASE_X_MAX) - sy(RangeMat.BASE_X_MIN),
                    ),
                )
                drawRect(
                    color = RangeMat.STRIP,
                    topLeft = Offset(sx(RangeMat.STRIP_Y_MIN), sy(RangeMat.STRIP_X_MIN)),
                    size = Size(
                        sx(RangeMat.STRIP_Y_MAX) - sx(RangeMat.STRIP_Y_MIN),
                        sy(RangeMat.STRIP_X_MAX) - sy(RangeMat.STRIP_X_MIN),
                    ),
                )
                // Ball dot at the origin (same world point as the POV tee ball).
                drawCircle(Color.White, radius = 2.sp.toPx(), center = Offset(sx(0.0), sy(0.0)))

                // Standing distance boards (RangeSigns) along the bottom edge —
                // same fixed-size icons as before (readability at 300+ m), each
                // board's base at the bottom padding; off-canvas signs skipped.
                labelPaint.apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 10.sp.toPx()
                    color = GolfColors.TextPrimary.toArgb()
                }
                val signBaseY = h - vPadPx
                val wIcon = 26.dp.toPx()
                val hIcon = 16.dp.toPx()
                for (sign in RangeSigns.signPlan()) {
                    val x = sx(sign.distanceM.toDouble())
                    if (x < 0f || x > w) continue
                    // Thin post up to the board.
                    drawLine(GolfColors.Line, Offset(x, signBaseY), Offset(x, signBaseY - hIcon), 1f)
                    // Board + teal top edge.
                    drawRoundRect(
                        color = GolfColors.Card,
                        topLeft = Offset(x - wIcon / 2f, signBaseY - hIcon),
                        size = Size(wIcon, hIcon),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                    drawLine(
                        GolfColors.Teal,
                        Offset(x - wIcon / 2f, signBaseY - hIcon),
                        Offset(x + wIcon / 2f, signBaseY - hIcon),
                        strokeWidth = 2f,
                    )
                    drawContext.canvas.nativeCanvas.drawText("${sign.distanceM}", x, signBaseY - hIcon * 0.3f, labelPaint)
                }

                // Dispersion rings FIRST (rings under dots).
                // Notes §2.3 (device ruling 2026-10-08): ONE rotated
                // minimum-enclosing ellipse of the kept shots, scaled ×1.05
                // about its centre, 2 dp stroke, no fill — the same ellipse
                // whose area the AREA column shows, so ring and table agree.
                // Traced as a 72-step world-space path (mirror of
                // CombineTopDownView) because the canvas scales the two world
                // axes differently — a rotated world ellipse is not a screen
                // oval, so the old rotate() + drawOval cannot draw it. A
                // collinear cloud (no enclosing ellipse) skips the ring; the
                // dots below are still drawn.
                // Legibility on the green ground (device feedback 2026-10-12):
                // the dark-palette hues (teal A, green F) vanish on
                // fairway/stripes, so every ring gets a symmetric light
                // keyline — same path, white α0.55 at +1.5 dp total width
                // (0.75 dp rim per side) — beneath colour ink bumped to α0.7.
                // Ring centres/axes unchanged; dots stay the strongest marks.
                order.forEachIndexed { index, _ ->
                    val ell = ellipses[index] ?: return@forEachIndexed
                    val color = FittingColors.clubColor(index)
                    val cosA = cos(ell.angleRad)
                    val sinA = sin(ell.angleRad)
                    val path = Path()
                    val steps = 72
                    for (i in 0..steps) {
                        val t = 2.0 * PI * i / steps
                        val u = ell.semiAxisM * cos(t)
                        val v = ell.semiCrossM * sin(t)
                        val worldSide = ell.centreSideM + u * cosA - v * sinA
                        val worldDist = ell.centreTotalM + u * sinA + v * cosA
                        val px = sx(worldDist)
                        val py = sy(worldSide)
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.55f),
                        style = Stroke(width = 3.5.dp.toPx()),
                    )
                    drawPath(
                        path = path,
                        color = color.copy(alpha = 0.7f),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }

                // Kept shot dots (r = 3 dp ⇒ the 6 dp dots of notes §2.3),
                // coloured per club — same keyline treatment as the rings:
                // a 1 dp white α0.6 disc rim beneath ink bumped to α0.9, so
                // every hue separates from rough, fairway and stripes alike.
                order.forEachIndexed { index, key ->
                    val color = FittingColors.clubColor(index)
                    keptByClub[key].orEmpty().forEach { s ->
                        val x = sx(s.totalM)
                        val y = sy(s.sideM)
                        drawCircle(Color.White.copy(alpha = 0.6f), radius = 4.dp.toPx(), center = Offset(x, y))
                        drawCircle(color.copy(alpha = 0.9f), radius = 3.dp.toPx(), center = Offset(x, y))
                    }
                }
            }
            // Empty state (notes §2.3): centred copy over the bare grid — shown
            // whenever no KEPT shots exist (empty session or all excluded).
            if (keptCount == 0) {
                Text(
                    "No kept shots yet",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextMuted,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
