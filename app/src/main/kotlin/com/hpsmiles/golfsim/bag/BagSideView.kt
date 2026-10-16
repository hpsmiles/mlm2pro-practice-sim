package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import java.util.Locale
import kotlin.math.roundToInt

private val LabelStripHeight = 36.dp
private val LabelRowGap = 2.dp
private val ArcWidth = 2.dp
private val LandingDotRadius = 4.dp
private val LandingDotRim = 1.dp
private val AxisTickLabelSp = 10.sp
private val AxisTickLength = 4.dp

/**
 * SIDE view (spec §6): every club's real simulated median flight on one shared
 * distance axis, arcs first, landing markers and labels on top. Labels are real
 * composables in a two-row stagger beneath the axis, so they are accessible and
 * tappable and cannot be clipped by the canvas. The landing marker is a tap
 * target too (spec §6.5) — the canvas hit-tests it; the arc body is not tappable.
 */
@Composable
fun BagSideView(
    traces: List<ClubTrace>,
    colors: Map<String, Color>,
    onClubTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (traces.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No shots to plot.", style = GolfTypography.Body, color = GolfColors.TextSecondary)
        }
        return
    }
    val maxDistance = traces.maxOf { it.distanceM }
    val maxApex = traces.maxOf { it.apexM }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boxWidth = constraints.maxWidth
        val stripPx = with(density) { LabelStripHeight.toPx() }
        val rowGapPx = with(density) { LabelRowGap.toPx() }.roundToInt()
        val plotHeightPx = (constraints.maxHeight - stripPx).coerceAtLeast(1f)
        val fit = remember(boxWidth, plotHeightPx, maxDistance, maxApex) {
            computeSideFit(
                widthPx = boxWidth.toDouble(),
                heightPx = plotHeightPx.toDouble(),
                maxDistanceM = maxDistance,
                maxApexM = maxApex,
            )
        }
        Column(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { plotHeightPx.toDp() })
                    .pointerInput(fit, traces) {
                        detectTapGestures { offset ->
                            val hit = traces.minByOrNull { trace ->
                                val dx = fit.x(trace.distanceM) - offset.x
                                val dy = fit.y(0.0) - offset.y
                                dx * dx + dy * dy
                            } ?: return@detectTapGestures
                            val dx = fit.x(hit.distanceM) - offset.x
                            val dy = fit.y(0.0) - offset.y
                            val tolerance = 24.dp.toPx()
                            if (dx * dx + dy * dy <= tolerance * tolerance) onClubTap(hit.clubName)
                        }
                    },
            ) {
                drawSideChart(fit, traces, colors)
            }
            Layout(
                content = {
                    traces.forEach { trace ->
                        Text(
                            text = "${trace.clubName} ${trace.distanceM.roundToInt()}",
                            style = GolfTypography.Status,
                            color = colors[trace.clubName] ?: GolfColors.TextSecondary,
                            maxLines = 1,
                            modifier = Modifier.clickable { onClubTap(trace.clubName) },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { measurables, layoutConstraints ->
                val placeables = measurables.map { it.measure(Constraints()) }
                val rowHeight = placeables.maxOfOrNull { it.height } ?: 0
                layout(layoutConstraints.maxWidth, rowHeight * 2 + rowGapPx) {
                    placeables.forEachIndexed { index, placeable ->
                        val centreX = fit.x(traces[index].distanceM).roundToInt()
                        val x = (centreX - placeable.width / 2)
                            .coerceIn(0, (layoutConstraints.maxWidth - placeable.width).coerceAtLeast(0))
                        val y = labelRow(index) * (rowHeight + rowGapPx)
                        placeable.place(x, y)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSideChart(fit: SideFit, traces: List<ClubTrace>, colors: Map<String, Color>) {
    val baseline = fit.y(0.0)
    val tickPaint = android.graphics.Paint().apply {
        color = GolfColors.TextSecondary.toArgb()
        textSize = AxisTickLabelSp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    // Ground line, then ticks and their labels in the reserved bottom pad.
    drawLine(
        color = GolfColors.Line,
        start = Offset(fit.x(0.0), baseline),
        end = Offset(fit.x(fit.maxDistanceM), baseline),
        strokeWidth = 1f,
    )
    val tickLength = AxisTickLength.toPx()
    val labelBaseline = size.height - tickLength
    BoxPlotGeom.niceTicks(0.0, fit.maxDistanceM).forEach { tick ->
        val x = fit.x(tick)
        drawLine(
            color = GolfColors.Line,
            start = Offset(x, baseline),
            end = Offset(x, baseline + tickLength),
            strokeWidth = 1f,
        )
        drawContext.canvas.nativeCanvas.drawText(
            String.format(Locale.US, "%.0f", tick),
            x,
            labelBaseline,
            tickPaint,
        )
    }
    // Arcs first, all of them, so nothing crosses a marker or a label.
    traces.forEach { trace ->
        val color = colors[trace.clubName] ?: GolfColors.TextSecondary
        val path = Path()
        trace.points.forEachIndexed { index, point ->
            val x = fit.x(point.downrangeM)
            val y = fit.y(point.heightM)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = ArcWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    // Landing markers on top (white rim + club colour), matching the top-down dot.
    traces.forEach { trace ->
        val color = colors[trace.clubName] ?: GolfColors.TextSecondary
        val centre = Offset(fit.x(trace.distanceM), baseline)
        drawCircle(Color.White.copy(alpha = 0.6f), LandingDotRadius.toPx() + LandingDotRim.toPx(), centre)
        drawCircle(color, LandingDotRadius.toPx(), centre)
    }
    // The y-axis is deliberately unlabelled; say so rather than let it mislead.
    drawContext.canvas.nativeCanvas.drawText(
        "height, not to scale",
        fit.x(0.0),
        tickPaint.textSize,
        tickPaint.apply { textAlign = android.graphics.Paint.Align.LEFT },
    )
}
