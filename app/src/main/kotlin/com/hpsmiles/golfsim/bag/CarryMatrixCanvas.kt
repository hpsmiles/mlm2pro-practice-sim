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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.designsystem.GolfColors

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
 * Per box over kept shots: whisker = min–max, box = Q1–Q3, median tick,
 * mean dot. Filtered shots = hollow amber dots on the CARRY lane only.
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
            medianColor = GolfColors.Amber,
            meanColor = GolfColors.TextPrimary,
        )
    }
    val totalDist = BagMappingStats.distribution(row.keptTotal)
    if (totalDist != null) {
        drawBoxPlot(
            dist = totalDist,
            axis = axis,
            centerY = totalCenterY,
            boxColor = TotalBoxColor,
            medianColor = GolfColors.TextPrimary,
            meanColor = GolfColors.TextPrimary,
        )
    }
    row.filtered.forEach { carry ->
        drawCircle(
            color = GolfColors.Amber,
            radius = FilteredRadius.toPx(),
            center = Offset(BoxPlotGeom.x(carry, axis), carryCenterY),
            style = Stroke(width = WhiskerWidth.toPx()),
        )
    }
}

/** Whisker, box, median tick and mean dot for one distribution on one lane. */
private fun DrawScope.drawBoxPlot(
    dist: BagMappingStats.Distribution,
    axis: BoxPlotGeom.Axis,
    centerY: Float,
    boxColor: Color,
    medianColor: Color,
    meanColor: Color,
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
        cornerRadius = CornerRadius(MeanRadius.toPx(), MeanRadius.toPx()),
        style = Stroke(width = WhiskerWidth.toPx()),
    )
    val medX = BoxPlotGeom.x(dist.median, axis)
    drawLine(
        color = medianColor,
        start = Offset(medX, centerY - BoxHeight.toPx() / 2f - TickOvershoot.toPx()),
        end = Offset(medX, centerY + BoxHeight.toPx() / 2f + TickOvershoot.toPx()),
        strokeWidth = MedianWidth.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = meanColor,
        radius = MeanRadius.toPx(),
        center = Offset(BoxPlotGeom.x(dist.mean, axis), centerY),
    )
}

/** TOTAL box color — series blue, distinct from the carry teal. */
internal val TotalBoxColor = Color(0xFF5B9DF9)

private val WhiskerWidth = 2.dp
private val MedianWidth = 3.dp
private val BoxHeight = 14.dp
private val MeanRadius = 4.dp
private val FilteredRadius = 5.dp
private val TickOvershoot = 4.dp
