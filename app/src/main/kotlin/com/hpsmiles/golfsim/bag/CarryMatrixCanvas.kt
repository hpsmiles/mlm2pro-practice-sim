package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
)

/**
 * Box-plot carry matrix painter (spec §6). All rows map through ONE shared
 * carry axis: the caller supplies the value-domain bounds ([minM], [maxM])
 * over every club's kept+filtered carries (via [BoxPlotGeom]), so a 230 m
 * driver and a 110 m wedge render on the same scale — that cross-club
 * comparison is the point of the matrix. Pixel mapping is derived per canvas
 * from its measured width. Per row over KEPT shots: whisker = min–max,
 * box = Q1–Q3, median tick = volt teal, mean dot = light; filtered shots =
 * hollow amber dots. Pure geometry in [BoxPlotGeom], distributions in
 * [BagMappingStats] — both JVM-tested.
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
            drawRow(row, axis, top = index * rowHeight, centerY = index * rowHeight + rowHeight / 2f)
        }
    }
}

private fun DrawScope.drawRow(row: CarryMatrixRow, axis: BoxPlotGeom.Axis, top: Float, centerY: Float) {
    val dist = BagMappingStats.distribution(row.kept)
    if (dist != null) {
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
            color = GolfColors.Teal,
            topLeft = Offset(left, centerY - BoxHeight.toPx() / 2f),
            size = Size(maxOf(2f, right - left), BoxHeight.toPx()),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 2f),
        )
        val medX = BoxPlotGeom.x(dist.median, axis)
        drawLine(
            color = GolfColors.Amber,
            start = Offset(medX, centerY - BoxHeight.toPx() / 2f - 4f),
            end = Offset(medX, centerY + BoxHeight.toPx() / 2f + 4f),
            strokeWidth = MedianWidth.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = GolfColors.TextPrimary,
            radius = MeanRadius.toPx(),
            center = Offset(BoxPlotGeom.x(dist.mean, axis), centerY),
        )
    }
    row.filtered.forEach { carry ->
        drawCircle(
            color = GolfColors.Amber,
            radius = FilteredRadius.toPx(),
            center = Offset(BoxPlotGeom.x(carry, axis), centerY),
            style = Stroke(width = 2f),
        )
    }
}

private val WhiskerWidth = 2.dp
private val MedianWidth = 3.dp
private val BoxHeight = 18.dp
private val MeanRadius = 4.dp
private val FilteredRadius = 5.dp
