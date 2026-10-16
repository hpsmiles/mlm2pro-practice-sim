package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import kotlin.math.abs
import kotlin.math.round

/** One plotted club: kept and filtered carries (m), bag order handled by the caller. */
data class CarryMatrixRow(
    val clubName: String,
    val kept: List<Double>,
    val filtered: List<Double>,
    /** Kept total distances (carry + rollout); drawn as the second box (spec item 4). */
    val keptTotal: List<Double> = emptyList(),
)

/**
 * Box-plot matrix painter (spec §6 + item 4). All rows map through ONE shared
 * value axis over carry AND total distances (caller supplies [minM]/[maxM]
 * via [BoxPlotGeom]), so a 230 m driver and a 110 m wedge render on the same
 * scale — the cross-club comparison is the point of the matrix. Pixel mapping
 * is derived per canvas from its measured width.
 *
 * Each row draws TWO boxes on separate horizontal lanes within the row
 * height: CARRY (teal, upper lane) and TOTAL (series blue, lower lane).
 * Per box over kept shots: whisker = min–max, box = Q1–Q3 + median tick
 * (restored 2026-10-16 after review; mean dot stays dropped). The tick is
 * TextPrimary on BOTH lanes — the original carry-lane colour was Amber,
 * which now collides with the amber exclusion dots sharing that lane.
 * Filtered shots = small hollow amber dots on the CARRY lane only (shot
 * markers, not statistical glyphs — kept).
 * Pure geometry in [BoxPlotGeom], distributions in [BagMappingStats] — both
 * JVM-tested.
 */
@Composable
fun CarryMatrixCanvas(
    rows: List<CarryMatrixRow>,
    minM: Double,
    maxM: Double,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    Canvas(modifier = modifier) {
        val axis = BoxPlotGeom.Axis(minM, maxM, leftPadPx = 0f, plotWidthPx = size.width)
        val rowHeight = size.height / rows.size
        rows.forEachIndexed { index, row ->
            val rowTop = index * rowHeight
            drawRow(row, axis, carryCenterY = rowTop + rowHeight * 0.28f, totalCenterY = rowTop + rowHeight * 0.72f)
        }
    }
}

/**
 * Metre scale for the pinned header above the matrix: tick marks + metre
 * labels on the SAME axis the boxes use, so they line up with the plot area
 * (the caller mirrors the row's label/stats column widths and inset around
 * it). Ticks come from the page's SHARED [BagDistanceAxis] (2026-10-16) — the
 * same step the SIDE chart and the TOP-DOWN use — not a re-derived ladder.
 * Layout top-to-bottom: tick rules in the upper part ending just above
 * the text top, labels underneath — nothing overlaps.
 */
@Composable
fun CarryMatrixScaleCanvas(
    minM: Double,
    maxM: Double,
    ticksM: List<Double>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val axis = BoxPlotGeom.Axis(minM, maxM, leftPadPx = 0f, plotWidthPx = size.width)
        val textPx = TickTextSp.toPx()
        // Labels sit TickLabelGap above the canvas bottom; their glyph box
        // (approximated by the full text size) starts just below that, and
        // every rule stops at that line — never through the digits.
        val labelBaseline = size.height - TickLabelGap.toPx()
        val textTop = labelBaseline - textPx
        val paint = android.graphics.Paint().apply {
            color = GolfColors.TextSecondary.toArgb()
            textSize = textPx
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        ticksM.filter { it >= minM && it <= maxM }.forEach { tick ->
            val x = BoxPlotGeom.x(tick, axis)
            drawLine(
                color = GolfColors.Line,
                start = Offset(x, 0f),
                end = Offset(x, textTop),
                strokeWidth = 1f,
            )
            drawContext.canvas.nativeCanvas.drawText(
                formatTick(tick),
                x,
                labelBaseline,
                paint,
            )
        }
    }
}

/** Tick labels: whole metres without a decimal, sub-metre with one. */
internal fun formatTick(v: Double): String =
    if (abs(v - round(v)) < 1e-6) String.format(java.util.Locale.US, "%.0f", v)
    else String.format(java.util.Locale.US, "%.1f", v)

private fun DrawScope.drawRow(
    row: CarryMatrixRow,
    axis: BoxPlotGeom.Axis,
    carryCenterY: Float,
    totalCenterY: Float,
) {
    val carryDist = BagMappingStats.distribution(row.kept)
    if (carryDist != null) {
        drawBoxPlot(
            dist = carryDist,
            axis = axis,
            centerY = carryCenterY,
            boxColor = GolfColors.Teal,
        )
    }
    val totalDist = BagMappingStats.distribution(row.keptTotal)
    if (totalDist != null) {
        drawBoxPlot(
            dist = totalDist,
            axis = axis,
            centerY = totalCenterY,
            boxColor = TotalBoxColor,
        )
    }
    row.filtered.forEach { carry ->
        drawCircle(
            color = GolfColors.Amber,
            radius = FilteredRadius.toPx(),
            center = Offset(BoxPlotGeom.x(carry, axis), carryCenterY),
            style = Stroke(width = FilteredStroke.toPx()),
        )
    }
}

/** Whisker (min–max), Q1–Q3 box and median tick for one distribution on one lane. */
private fun DrawScope.drawBoxPlot(
    dist: BagMappingStats.Distribution,
    axis: BoxPlotGeom.Axis,
    centerY: Float,
    boxColor: Color,
) {
    drawLine(
        color = GolfColors.Line,
        start = Offset(BoxPlotGeom.x(dist.min, axis), centerY),
        end = Offset(BoxPlotGeom.x(dist.max, axis), centerY),
        strokeWidth = WhiskerWidth.toPx(),
        cap = StrokeCap.Round,
    )
    val left = BoxPlotGeom.x(dist.q1, axis)
    val right = BoxPlotGeom.x(dist.q3, axis)
    drawRoundRect(
        color = boxColor,
        topLeft = Offset(left, centerY - BoxHeight.toPx() / 2f),
        size = Size(maxOf(2f, right - left), BoxHeight.toPx()),
        cornerRadius = CornerRadius(CornerRadiusDp.toPx(), CornerRadiusDp.toPx()),
        style = Stroke(width = WhiskerWidth.toPx()),
    )
    // Median tick, restored from the pre-2026-10-16 drawing (same 3 dp
    // weight and 4 dp overshoot); colour TextPrimary — see class doc.
    val medX = BoxPlotGeom.x(dist.median, axis)
    drawLine(
        color = GolfColors.TextPrimary,
        start = Offset(medX, centerY - BoxHeight.toPx() / 2f - TickOvershoot.toPx()),
        end = Offset(medX, centerY + BoxHeight.toPx() / 2f + TickOvershoot.toPx()),
        strokeWidth = MedianWidth.toPx(),
        cap = StrokeCap.Round,
    )
}

/** TOTAL box color — series blue, distinct from the carry teal. */
internal val TotalBoxColor = Color(0xFF5B9DF9)

private val WhiskerWidth = 2.dp
private val MedianWidth = 3.dp
private val BoxHeight = 14.dp
private val CornerRadiusDp = 4.dp
private val TickOvershoot = 4.dp
private val FilteredRadius = 3.dp
private val FilteredStroke = 1.5.dp
private val TickTextSp = 10.sp
private val TickLabelGap = 4.dp
