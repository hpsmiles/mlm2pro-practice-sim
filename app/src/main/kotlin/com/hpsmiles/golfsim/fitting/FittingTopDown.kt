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

/**
 * M7 top-down (design notes §2.3): kept shots as per-club coloured dots over
 * the same world mapping and grid + distance boards as `TopDownCanvas`
 * (x = side, y = REST position = total), with 1σ and 2σ dispersion ovals per
 * club (≥3 kept shots; 1σ @ 55 % alpha, 2σ @ 25 %, 2 dp stroke, no fill).
 * Excluded shots are never drawn — they stay visible in the table drill-down.
 * Legend chips (colour dot + name + count badge) wrap to a second row beyond
 * 4 clubs and are not tappable in v1.
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
    // oval inputs and the dots below (previously filtered 3× per pass).
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
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawRect(GolfColors.Base)
                // World mapping identical to TopDownCanvas: origin bottom-centre,
                // x lateral (side), y 0..GROUND_END_Y, full ground extent fitted.
                val pxPerM = h / RangeScene.GROUND_END_Y.toFloat()
                val originX = w / 2f
                val originY = h - 8.sp.toPx()

                // Standing distance boards (RangeSigns) — same fixed-size icons
                // as TopDownCanvas, not world-scaled (readability at 300+ m).
                labelPaint.apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                for (sign in RangeSigns.signPlan()) {
                    val x = originX + (sign.xM * pxPerM).toFloat()
                    val y = originY - sign.distanceM * pxPerM
                    val wIcon = 26.dp.toPx()
                    val hIcon = 16.dp.toPx()
                    // Thin post down to the ground position.
                    drawLine(GolfColors.Line, Offset(x, y), Offset(x, y - hIcon), 1f)
                    // Board + teal top edge.
                    drawRoundRect(
                        color = GolfColors.Card,
                        topLeft = Offset(x - wIcon / 2f, y - hIcon),
                        size = Size(wIcon, hIcon),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                    drawLine(
                        GolfColors.Teal,
                        Offset(x - wIcon / 2f, y - hIcon),
                        Offset(x + wIcon / 2f, y - hIcon),
                        strokeWidth = 2f,
                    )
                    labelPaint.textSize = 10.sp.toPx()
                    labelPaint.color = GolfColors.TextPrimary.toArgb()
                    drawContext.canvas.nativeCanvas.drawText("${sign.distanceM}", x, y - hIcon * 0.3f, labelPaint)
                }

                // Lateral gridlines every 20 m (TopDownCanvas grid).
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

                // Practice-grid identity matching the POV view: a solid centre
                // line at x = 0 and light dashed guide lines at x = +/-10/+/-20,
                // from the tee to the ground end.
                val teeTop = originY - RangeScene.FAIRWAY_TEE_Y.toFloat() * pxPerM
                val groundTop = originY - RangeScene.GROUND_END_Y.toFloat() * pxPerM
                drawLine(
                    Color.White.copy(alpha = RangeScene.CENTER_LINE_ALPHA),
                    Offset(originX, teeTop), Offset(originX, groundTop), 1.5f,
                )
                val dashPx = RangeScene.GUIDE_DASH_ON_M.toFloat() * pxPerM
                val gapPx = RangeScene.GUIDE_DASH_OFF_M.toFloat() * pxPerM
                for (guideM in RangeScene.GUIDE_LINE_LATERALS_M) {
                    val x = originX + guideM.toFloat() * pxPerM
                    drawLine(
                        Color.White.copy(alpha = RangeScene.GUIDE_LINE_ALPHA),
                        Offset(x, teeTop), Offset(x, groundTop), 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx)),
                    )
                }

                // Range mat quad at the origin (orientation cue; ball at (0, 0)
                // right of centre — right-handed golfer) — same as TopDownCanvas.
                val matLeft = originX + (RangeMat.BASE_X_MIN * pxPerM)
                val matRight = originX + (RangeMat.BASE_X_MAX * pxPerM)
                val matTop = originY - (RangeMat.BASE_Y_MAX * pxPerM)
                val matBottom = originY - (RangeMat.BASE_Y_MIN * pxPerM)
                drawRect(
                    color = RangeMat.BASE,
                    topLeft = Offset(matLeft.toFloat(), matTop.toFloat()),
                    size = Size((matRight - matLeft).toFloat(), (matBottom - matTop).toFloat()),
                )
                val stripLeft = originX + (RangeMat.STRIP_X_MIN * pxPerM)
                val stripRight = originX + (RangeMat.STRIP_X_MAX * pxPerM)
                val stripTop = originY - (RangeMat.STRIP_Y_MAX * pxPerM)
                val stripBottom = originY - (RangeMat.STRIP_Y_MIN * pxPerM)
                drawRect(
                    color = RangeMat.STRIP,
                    topLeft = Offset(stripLeft.toFloat(), stripTop.toFloat()),
                    size = Size((stripRight - stripLeft).toFloat(), (stripBottom - stripTop).toFloat()),
                )
                // Ball dot at the origin (same world point as the POV tee ball).
                drawCircle(Color.White, radius = 2.sp.toPx(), center = Offset(originX, originY))

                // Dispersion ovals FIRST (rings under dots).
                // Notes §2.3 (binding overrides): 1σ @ 55 % alpha, 2σ @ 25 %,
                // 2 dp stroke, no fill — replaces the plan's 0.9/0.45 @ 1.5 dp.
                order.forEachIndexed { index, key ->
                    // compute returns null for a degenerate cloud — zero
                    // variance OR collinear (λ2 ≈ 0 with λ1 > 0, a half-line):
                    // the ring is skipped, dots below are still drawn.
                    val kept = keptByClub[key].orEmpty()
                    val oval = DispersionOval.compute(kept.map { it.sideM to it.totalM }) ?: return@forEachIndexed
                    val color = FittingColors.clubColor(index)
                    listOf(2.0 to 0.25f, 1.0 to 0.55f).forEach { (scale, alpha) ->
                        val pts = DispersionOval.polygon(oval, scale)
                        val path = Path()
                        pts.forEachIndexed { j, (x, y) ->
                            val px = originX + (x * pxPerM).toFloat()
                            val py = originY - (y * pxPerM).toFloat()
                            if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        path.close()
                        drawPath(path, color.copy(alpha = alpha), style = Stroke(width = 2.dp.toPx()))
                    }
                }

                // Kept shot dots (6 dp), coloured per club (notes §2.3 —
                // 6 dp dots replaces the plan's 3.sp radius).
                order.forEachIndexed { index, key ->
                    val color = FittingColors.clubColor(index)
                    keptByClub[key].orEmpty().forEach { s ->
                        val x = originX + (s.sideM * pxPerM).toFloat()
                        val y = originY - (s.totalM * pxPerM).toFloat()
                        drawCircle(color.copy(alpha = 0.8f), radius = 3.dp.toPx(), center = Offset(x, y))
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
