package com.hpsmiles.golfsim.combine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.fitting.DispersionEllipse
import com.hpsmiles.golfsim.fitting.FittingColors
import com.hpsmiles.golfsim.range.GreenRings
import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val SidePanelWidth = 210.dp

/**
 * M8 post-ship spec §3: horizontal plan view of a completed session's finish points.
 * Horizontal corridor cropped to the session's actual content (snapped to 25 m, the
 * full 0–330 m only when content demands it), true-scale 28 m corridor band, 50 m
 * gridlines, ±10/±20 m dashed lateral guides with edge labels (fitting top-down
 * parity), each approach station's green + pin, and one colour-coded group per
 * station (finish dots, centroid, one dashed 1σ ellipse) beside a per-group stats
 * panel. Falls back to an empty state when no shot carries rest coordinates
 * (pre-v7 sessions). Colour treatment matches the fitting top-down (2026-10-13):
 * the pane is framed as a Card sibling and draws the painted-range ground —
 * green rough backdrop, mown fairway stripes, white-alpha centre/guide ink,
 * white keylines under the colour marks (TextMuted vanishes on the greens).
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
    // One enclosing ring per station: the M7 minimum-enclosing ellipse (5% buffer,
    // containment-verified) so the ring covers every finish dot for that station.
    val rings = remember(pointsByStation) {
        pointsByStation.mapValues { (_, points) -> DispersionEllipse.fromKept(points) }
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
        // Framed like the fitting top-down pane (FittingResultsView parity):
        // Card surface, card corner, 1 dp Line border, Md padding — the map
        // reads as the same framed facility card.
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(GolfSpacing.CornerCard))
                .background(GolfColors.Card)
                .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.CornerCard))
                .padding(GolfSpacing.Md),
        ) {
            CombineTopDownCanvas(
                entry = entry,
                groups = groups,
                pointsByStation = pointsByStation,
                rings = rings,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
    rings: Map<Int, DispersionEllipse?>,
    modifier: Modifier = Modifier,
) {
    val labelPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    Canvas(modifier) {
        // Range-consistent ground (fitting top-down parity): dark rough
        // everywhere; the mown fairway corridor shades over it below.
        drawRect(GolfColors.RangeRough)

        val leftPad = 26.dp.toPx()
        val rightPad = 12.dp.toPx()
        // ONE scale for both world axes, so greens are true circles and rings keep
        // their true shape. The DISTANCE window is cropped to this session's actual
        // content (snapped outward to 25 m), so empty range left/right of the action
        // is trimmed away; the lateral window is sized to fit everything. Spare pane
        // space stays quiet background around the centred corridor.
        val fairwayEndM = RangeScene.FAIRWAY_END_Y
        val corridorHalfM = RangeScene.fairwayHalfWidth(0.0)
        val midY = size.height / 2f

        // Distance content extent (world Y): every approach green (centre ± radius),
        // every finish dot, and every ring's worst-case downrange reach (centre ± the
        // larger semi-axis — same worst-case convention as the lateral window).
        val greenDistancesM = entry.stations
            .filter { it.kind == StationKind.APPROACH }
            .flatMap { station ->
                val pin = station.pins.firstOrNull() ?: PinOffset(0.0, 0.0)
                val centreM = station.startDistanceM + pin.dy
                listOf(centreM - station.greenRadiusM, centreM + station.greenRadiusM)
            }
        val dotDistancesM = pointsByStation.values.flatMap { points -> points.map { it.second } }
        val ringDistancesM = rings.values.filterNotNull().flatMap { ring ->
            val reachM = maxOf(ring.semiAxisM, ring.semiCrossM)
            listOf(ring.centreTotalM - reachM, ring.centreTotalM + reachM)
        }
        val distanceExtentM = greenDistancesM + dotDistancesM + ringDistancesM
        // Snap outward to 25 m and clamp to the fairway; fall back to the full
        // corridor when the session carries no distance content at all.
        var windowMinM = distanceExtentM.minOrNull()
            ?.let { floor(it / 25.0) * 25.0 }?.coerceAtLeast(0.0) ?: 0.0
        var windowMaxM = distanceExtentM.maxOrNull()
            ?.let { ceil(it / 25.0) * 25.0 }?.coerceAtMost(fairwayEndM) ?: fairwayEndM
        // Guard: a degenerate window would blow up pxPerM — keep at least 50 m,
        // centred on the content and clamped inside the fairway.
        if (windowMaxM - windowMinM < 50.0) {
            val centreM = (windowMinM + windowMaxM) / 2.0
            windowMinM = (centreM - 25.0).coerceAtLeast(0.0)
            windowMaxM = (windowMinM + 50.0).coerceAtMost(fairwayEndM)
            windowMinM = windowMaxM - 50.0
        }
        val windowWidthM = windowMaxM - windowMinM

        val pxPerM = (size.width - leftPad - rightPad) / windowWidthM.toFloat()
        // Lateral half-window: the corridor, the biggest green (radius + pin offset),
        // and every station's enclosing ring (rotated world ellipse → worst-case
        // lateral extent is centre ± the larger semi-axis), each with margin.
        val ringLateralHalfM = rings.values.filterNotNull().maxOfOrNull { ring ->
            abs(ring.centreSideM) + maxOf(ring.semiAxisM, ring.semiCrossM)
        } ?: 0.0
        val greenLateralHalfM = entry.stations
            .filter { it.kind == StationKind.APPROACH }
            .maxOfOrNull { station ->
                val pin = station.pins.firstOrNull() ?: PinOffset(0.0, 0.0)
                abs(pin.dx) + station.greenRadiusM
            } ?: 0.0
        val lateralHalfM = maxOf(
            corridorHalfM,
            greenLateralHalfM,
            ringLateralHalfM,
        ) + 2.0
        fun sx(restY: Double) = leftPad + ((restY - windowMinM) * pxPerM).toFloat()
        // Sign convention (verified 2026-10-16, M4c goldens): +x is right of
        // the target looking down-range — with the tee at the left and distance
        // increasing rightward, right-of-target draws DOWN the screen.
        fun sy(restX: Double) = midY + (restX * pxPerM).toFloat()

        // Corridor (the constant 28 m fairway band) + centre line, spanning the
        // cropped window — the band is the backdrop, not content. Range palette
        // (fitting parity): RangeFairway base + the same 10 m mown stripes the
        // range and fitting show, intersected with the window so the crop stays
        // clean; the base rect stays as a safety floor under the stripes.
        // The rect spans ±corridorHalfM; its top/bottom are computed min/max
        // because the lateral mapping's sign was flipped once already
        // (2026-10-16) — the ordering must never depend on the sign again.
        val corridorTop = minOf(sy(corridorHalfM), sy(-corridorHalfM))
        val corridorBottom = maxOf(sy(corridorHalfM), sy(-corridorHalfM))
        drawRect(
            color = GolfColors.RangeFairway,
            topLeft = Offset(sx(windowMinM), corridorTop),
            size = Size(sx(windowMaxM) - sx(windowMinM), corridorBottom - corridorTop),
        )
        for (band in 0 until RangeScene.stripeBandCount()) {
            val (yFrom, yTo) = RangeScene.stripeBand(band)
            val loM = maxOf(yFrom, windowMinM)
            val hiM = minOf(yTo, windowMaxM)
            if (loM >= hiM) continue
            drawRect(
                color = if (RangeScene.stripeIsLight(0.0, yFrom)) {
                    GolfColors.RangeStripeLight
                } else {
                    GolfColors.RangeStripeDark
                },
                topLeft = Offset(sx(loM), corridorTop),
                size = Size(sx(hiM) - sx(loM), corridorBottom - corridorTop),
            )
        }
        drawLine(
            Color.White.copy(alpha = RangeScene.CENTER_LINE_ALPHA),
            Offset(sx(windowMinM), midY),
            Offset(sx(windowMaxM), midY),
            1.5f,
        )

        // Vertical extent of the lateral window (content band); gridlines and labels
        // stay inside it so the spare space above/below remains quiet. Also
        // min/max: the band spans ±lateralHalfM and the lateral mapping's sign
        // was flipped once already (2026-10-16) — ordering must not depend on it.
        val bandTop = minOf(sy(lateralHalfM), sy(-lateralHalfM)).coerceAtLeast(0f)
        val bandBottom = maxOf(sy(lateralHalfM), sy(-lateralHalfM)).coerceAtMost(size.height)

        // 50 m gridlines + labels, only inside the window (window edges snap to 25 m
        // but get no gridline/label of their own). Label ink = TextSecondary —
        // fitting parity: TextMuted is functionally invisible on the green rough.
        labelPaint.color = GolfColors.TextSecondary.toArgb()
        labelPaint.textSize = 10.sp.toPx()
        var gridM = maxOf(50.0, ceil(windowMinM / 50.0) * 50.0)
        while (gridM <= windowMaxM) {
            val x = sx(gridM)
            drawLine(GolfColors.Line.copy(alpha = 0.6f), Offset(x, bandTop), Offset(x, bandBottom), 1f)
            drawContext.canvas.nativeCanvas.drawText("${gridM.toInt()} m", x, bandTop - 4.dp.toPx(), labelPaint)
            gridM += 50.0
        }

        // Lateral scale references (fitting top-down parity): the range's
        // practice-grid laterals (±10/±20 m) as light dashed guides across the
        // corridor, each labelled "N m" right-aligned at the canvas edge just
        // above its line. The dash cycle is honest at true scale (4 m on / 2 m
        // off × pxPerM). Guides beyond the lateral window are skipped; groups
        // draw later so data always wins over this quiet furniture. Ink is
        // fitting's white-alpha (centre-line alpha for labels, guide alpha for
        // the dashes) so it reads on BOTH the fairway band and the rough.
        val dashPx = (RangeScene.GUIDE_DASH_ON_M * pxPerM).toFloat()
        val gapPx = (RangeScene.GUIDE_DASH_OFF_M * pxPerM).toFloat()
        val visibleGuidesM = RangeScene.GUIDE_LINE_LATERALS_M.filter { abs(it) <= lateralHalfM }
        for (guideM in visibleGuidesM) {
            val y = sy(guideM)
            drawLine(
                Color.White.copy(alpha = RangeScene.GUIDE_LINE_ALPHA),
                Offset(sx(windowMinM), y),
                Offset(sx(windowMaxM), y),
                1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx)),
            )
        }
        labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
        labelPaint.textSize = 10.sp.toPx()
        labelPaint.color = Color.White.copy(alpha = RangeScene.CENTER_LINE_ALPHA).toArgb()
        for (guideM in visibleGuidesM) {
            drawContext.canvas.nativeCanvas.drawText(
                "${abs(guideM).toInt()} m",
                size.width - rightPad,
                sy(guideM) - 2.dp.toPx(),
                labelPaint,
            )
        }

        // Tee marker at the world origin — only when the cropped window includes it.
        if (windowMinM <= 0.0) {
            drawCircle(GolfColors.TextPrimary, radius = 2.5.dp.toPx(), center = Offset(sx(0.0), sy(0.0)))
        }

        // Approach greens + pins (each station's visit-1 pin) — true circles now
        // that both axes share one scale. The fill is the shared concentric-ring
        // green styling (user request 2026-10-15): alternating very-light /
        // light green 2 m bands out to the green radius. The teal boundary keeps
        // fitting's white keyline (α0.55, +1.5 dp total width) beneath the
        // outline bumped to α0.7 so the circle separates from the mown fairway.
        for (station in entry.stations) {
            if (station.kind != StationKind.APPROACH) continue
            val pin = station.pins.firstOrNull() ?: PinOffset(0.0, 0.0)
            val centre = Offset(sx(station.startDistanceM + pin.dy), sy(pin.dx))
            for ((index, radiusM) in GreenRings.boundaryRadii(station.greenRadiusM).withIndex()) {
                val color = if (GreenRings.isVeryLightRing(index)) {
                    GolfColors.GreenRingVeryLight
                } else {
                    GolfColors.GreenRingLight
                }
                drawCircle(
                    color = color,
                    radius = (radiusM * pxPerM).toFloat(),
                    center = centre,
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = (station.greenRadiusM * pxPerM).toFloat(),
                center = centre,
                style = Stroke(1.5f + 1.5.dp.toPx()),
            )
            drawCircle(
                color = GolfColors.Teal.copy(alpha = 0.7f),
                radius = (station.greenRadiusM * pxPerM).toFloat(),
                center = centre,
                style = Stroke(1.5f),
            )
            drawCircle(GolfColors.TextPrimary, radius = 3.dp.toPx(), center = centre)
        }

        // One colour-coded group per station: finish dots, one enclosing ring,
        // centroid — each on fitting's keyline recipe (white halo beneath the
        // colour ink) so every hue separates from rough, fairway and stripes.
        for ((index, group) in groups.withIndex()) {
            val color = FittingColors.clubColor(index)
            val points = pointsByStation[group.stationIndex].orEmpty()
            for ((restX, restY) in points) {
                val c = Offset(sx(restY), sy(restX))
                drawCircle(Color.White.copy(alpha = 0.6f), radius = 4.5.dp.toPx() + 1.dp.toPx(), center = c)
                drawCircle(color.copy(alpha = 0.9f), radius = 4.5.dp.toPx(), center = c)
            }
            // The ring is the station's minimum-enclosing ellipse in the WORLD frame;
            // because it can be rotated relative to the canvas axes it is not an oval,
            // so it is traced point-by-point (both axes now share one scale).
            val ring = rings[group.stationIndex]
            if (group.hasRing && ring != null) {
                val path = Path()
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                val steps = 72
                for (i in 0..steps) {
                    val t = 2.0 * PI * i / steps
                    val u = ring.semiAxisM * cos(t)
                    val v = ring.semiCrossM * sin(t)
                    val worldX = ring.centreSideM + u * cos(ring.angleRad) - v * sin(ring.angleRad)
                    val worldY = ring.centreTotalM + u * sin(ring.angleRad) + v * cos(ring.angleRad)
                    val px = sx(worldY)
                    val py = sy(worldX)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                // Symmetric white keyline (same dashed path, +1.5 dp total
                // width, 0.75 dp rim per side) beneath the colour ink bumped
                // to α0.7 — fitting's ring treatment.
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.55f),
                    style = Stroke(
                        1.5f + 1.5.dp.toPx(),
                        pathEffect = dash,
                    ),
                )
                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.7f),
                    style = Stroke(
                        1.5f,
                        pathEffect = dash,
                    ),
                )
            }
            val cx = sx(group.centroidY)
            val cy = sy(group.centroidX)
            drawCircle(
                Color.White.copy(alpha = 0.6f),
                radius = 6.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(2f + 1.5.dp.toPx()),
            )
            drawCircle(color.copy(alpha = 0.9f), radius = 6.dp.toPx(), center = Offset(cx, cy), style = Stroke(2f))
            drawCircle(Color.White.copy(alpha = 0.6f), radius = 2.5.dp.toPx() + 1.dp.toPx(), center = Offset(cx, cy))
            drawCircle(color.copy(alpha = 0.9f), radius = 2.5.dp.toPx(), center = Offset(cx, cy))
        }
    }
}
