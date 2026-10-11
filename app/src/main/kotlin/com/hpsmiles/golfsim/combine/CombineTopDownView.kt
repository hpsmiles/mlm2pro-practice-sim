package com.hpsmiles.golfsim.combine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.fitting.FittingColors
import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.abs

private val SidePanelWidth = 210.dp

/**
 * M8 post-ship spec §3: horizontal plan view of a completed session's finish points.
 * Tee on the left, 0–330 m to the right, the constant 28 m corridor, 50 m gridlines,
 * each approach station's green + pin, and one colour-coded group per station (finish
 * dots, centroid, one dashed 1σ ellipse) beside a per-group stats panel. Falls back to
 * an empty state when no shot carries rest coordinates (pre-v7 sessions).
 */
@Composable
internal fun CombineTopDownView(
    entry: CombineCatalogEntry,
    facts: List<CombineShotFacts>,
    modifier: Modifier = Modifier,
) {
    val groups = remember(entry, facts) { CombineDispersion.groups(entry, facts) }
    val pointsByStation = remember(facts) {
        facts.groupBy { CombinePlan.stationIndexFor(it.shotIndex) }
            .mapValues { (_, stationFacts) ->
                stationFacts.mapNotNull { fact ->
                    val x = fact.restXM ?: return@mapNotNull null
                    val y = fact.restYM ?: return@mapNotNull null
                    x to y
                }
            }
    }

    if (groups.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "no dispersion data for this session",
                style = GolfTypography.Body,
                color = GolfColors.TextMuted,
            )
        }
        return
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Lg)) {
        CombineTopDownCanvas(
            entry = entry,
            groups = groups,
            pointsByStation = pointsByStation,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        Column(
            modifier = Modifier.width(SidePanelWidth).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
        ) {
            for ((index, group) in groups.withIndex()) {
                val station = entry.stations[group.stationIndex]
                val color = FittingColors.clubColor(index)
                Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
                    ) {
                        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
                        Text(station.label, style = GolfTypography.MetricLabel, color = GolfColors.TextPrimary)
                    }
                    val isDriver = station.kind == StationKind.DRIVER
                    val avgLabel = if (isDriver) "AVG TOTAL" else "AVG TO PIN"
                    val avgValue = if (isDriver) DRIVER_HOLE_M - group.avgRestM else group.avgRestM
                    StatLine("$avgLabel ${CombineFormats.oneDp(avgValue)} m")
                    StatLine("LONG–SHORT ${CombineFormats.oneDp(group.longShortSpreadM)} m")
                    StatLine("LEFT–RIGHT ${CombineFormats.oneDp(group.leftRightSpreadM)} m")
                }
            }
        }
    }
}

@Composable
private fun StatLine(text: String) {
    Text(text, style = GolfTypography.Status, color = GolfColors.TextSecondary)
}

@Composable
private fun CombineTopDownCanvas(
    entry: CombineCatalogEntry,
    groups: List<CombineDispersion.Group>,
    pointsByStation: Map<Int, List<Pair<Double, Double>>>,
    modifier: Modifier = Modifier,
) {
    val labelPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    Canvas(modifier) {
        drawRect(GolfColors.Base)

        val leftPad = 26.dp.toPx()
        val rightPad = 12.dp.toPx()
        val vPad = 12.dp.toPx()
        val worldWidthM = RangeScene.FAIRWAY_END_Y
        val pxPerMX = (size.width - leftPad - rightPad) / worldWidthM.toFloat()
        val corridorHalfM = RangeScene.fairwayHalfWidth(0.0)
        val midY = size.height / 2f
        // Lateral window: at least a couple of corridors tall, else wide enough to hold
        // every group's 1σ ring plus a small margin (so no ring is clipped).
        val lateralHalfM = maxOf(
            corridorHalfM * 2.5,
            groups.maxOf { abs(it.centroidX) + 2.5 * it.sigmaX + 2.0 },
        )
        val pxPerMY = (size.height / 2f - vPad) / lateralHalfM.toFloat()
        fun sx(restY: Double) = leftPad + (restY * pxPerMX).toFloat()
        fun sy(restX: Double) = midY - (restX * pxPerMY).toFloat()

        // Corridor (the constant 28 m fairway band) + centre line.
        val corridorTop = sy(corridorHalfM)
        val corridorBottom = sy(-corridorHalfM)
        drawRect(
            color = GolfColors.HoleFairway,
            topLeft = Offset(sx(0.0), corridorTop),
            size = Size(sx(worldWidthM) - sx(0.0), corridorBottom - corridorTop),
        )
        drawLine(
            GolfColors.Line,
            Offset(sx(0.0), midY),
            Offset(sx(worldWidthM), midY),
            1.5f,
        )

        // 50 m gridlines + labels.
        labelPaint.color = GolfColors.TextMuted.toArgb()
        labelPaint.textSize = 10.sp.toPx()
        var gridM = 50.0
        while (gridM <= worldWidthM) {
            val x = sx(gridM)
            drawLine(GolfColors.Line.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), 1f)
            drawContext.canvas.nativeCanvas.drawText("${gridM.toInt()} m", x, 12.sp.toPx(), labelPaint)
            gridM += 50.0
        }

        // Tee marker at the world origin.
        drawCircle(GolfColors.TextPrimary, radius = 2.dp.toPx(), center = Offset(sx(0.0), sy(0.0)))

        // Approach greens + pins (each station's visit-1 pin).
        for (station in entry.stations) {
            if (station.kind != StationKind.APPROACH) continue
            val pin = station.pins.firstOrNull() ?: PinOffset(0.0, 0.0)
            val gx = sx(station.startDistanceM + pin.dy)
            val gy = sy(pin.dx)
            val rx = (station.greenRadiusM * pxPerMX).toFloat()
            val ry = (station.greenRadiusM * pxPerMY).toFloat()
            drawOval(
                color = GolfColors.Teal.copy(alpha = 0.18f),
                topLeft = Offset(gx - rx, gy - ry),
                size = Size(2 * rx, 2 * ry),
            )
            drawOval(
                color = GolfColors.Teal.copy(alpha = 0.5f),
                topLeft = Offset(gx - rx, gy - ry),
                size = Size(2 * rx, 2 * ry),
                style = Stroke(1.5f),
            )
            drawCircle(GolfColors.TextPrimary, radius = 2.5.dp.toPx(), center = Offset(gx, gy))
        }

        // One colour-coded group per station: finish dots, 1σ ring, centroid.
        for ((index, group) in groups.withIndex()) {
            val color = FittingColors.clubColor(index)
            for ((restX, restY) in pointsByStation[group.stationIndex].orEmpty()) {
                drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(sx(restY), sy(restX)))
            }
            val rx = (group.sigmaY * pxPerMX).toFloat()
            val ry = (group.sigmaX * pxPerMY).toFloat()
            if (group.hasRing && rx > 0.5f && ry > 0.5f) {
                drawOval(
                    color = color,
                    topLeft = Offset(sx(group.centroidY) - rx, sy(group.centroidX) - ry),
                    size = Size(2 * rx, 2 * ry),
                    style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            val cx = sx(group.centroidY)
            val cy = sy(group.centroidX)
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(cx, cy), style = Stroke(2f))
            drawCircle(color, radius = 2.dp.toPx(), center = Offset(cx, cy))
        }
    }
}
