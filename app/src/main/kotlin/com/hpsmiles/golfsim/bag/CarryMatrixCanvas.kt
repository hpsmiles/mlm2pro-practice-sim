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

/** One plotted club: kept and filtered carries (m), bag order handled by the caller. */
data class CarryMatrixRow(
    val clubName: String,
    val kept: List<Double>,
    val filtered: List<Double>,
)

/**
 * Box-plot carry matrix painter (spec §6). One shared carry axis; per row
 * over KEPT shots: whisker = min–max, box = Q1–Q3, median tick = volt teal,
 * mean dot = light; filtered shots = hollow amber dots. Pure geometry in
 * [BoxPlotGeom], distributions in [BagMappingStats] — both JVM-tested.
 */
@Composable
fun CarryMatrixCanvas(
    rows: List<CarryMatrixRow>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    Canvas(modifier = modifier) {
        val allValues = rows.flatMap { it.kept + it.filtered }
        val rowAxis = BoxPlotGeom.axis(allValues, leftPadPx = 0f, plotWidthPx = size.width) ?: return@Canvas
        val rowHeight = size.height / rows.size
        rows.forEachIndexed { index, row ->
            drawRow(row, rowAxis, top = index * rowHeight, centerY = index * rowHeight + rowHeight / 2f)
        }
    }
}

private fun DrawScope.drawRow(row: CarryMatrixRow, axis: BoxPlotGeom.Axis, top: Float, centerY: Float) {
    val dist = BagMappingStats.distribution(row.kept)
    if (dist != null) {
        drawLine(
            color = PlotLines,
            start = Offset(BoxPlotGeom.x(dist.min, axis), centerY),
            end = Offset(BoxPlotGeom.x(dist.max, axis), centerY),
            strokeWidth = WhiskerWidth.toPx(),
            cap = StrokeCap.Round,
        )
        val left = BoxPlotGeom.x(dist.q1, axis)
        val right = BoxPlotGeom.x(dist.q3, axis)
        drawRoundRect(
            color = PlotBox,
            topLeft = Offset(left, centerY - BoxHeight.toPx() / 2f),
            size = Size(maxOf(2f, right - left), BoxHeight.toPx()),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 2f),
        )
        val medX = BoxPlotGeom.x(dist.median, axis)
        drawLine(
            color = PlotMedian,
            start = Offset(medX, centerY - BoxHeight.toPx() / 2f - 4f),
            end = Offset(medX, centerY + BoxHeight.toPx() / 2f + 4f),
            strokeWidth = MedianWidth.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = PlotMean,
            radius = MeanRadius.toPx(),
            center = Offset(BoxPlotGeom.x(dist.mean, axis), centerY),
        )
    }
    row.filtered.forEach { carry ->
        drawCircle(
            color = PlotFiltered,
            radius = FilteredRadius.toPx(),
            center = Offset(BoxPlotGeom.x(carry, axis), centerY),
            style = Stroke(width = 2f),
        )
    }
}

private val PlotLines = Color(0xFF28303A)      // GolfColors.Line — kept here to avoid a draw-scope import cycle
private val PlotBox = Color(0xFF3FA7A0)        // GolfColors.Teal
private val PlotMedian = Color(0xFFF2A93B)     // GolfColors.Amber
private val PlotMean = Color(0xFFE7EBEE)       // GolfColors.TextPrimary
private val PlotFiltered = Color(0xFFF2A93B)   // GolfColors.Amber
private val WhiskerWidth = 2.dp
private val MedianWidth = 3.dp
private val BoxHeight = 18.dp
private val MeanRadius = 4.dp
private val FilteredRadius = 5.dp
