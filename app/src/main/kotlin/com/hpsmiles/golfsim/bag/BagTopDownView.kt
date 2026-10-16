package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.fitting.DispersionEllipse
import com.hpsmiles.golfsim.range.RangeScene
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val RING_STEPS = 72
private const val GRID_STEP_M = 50.0
private val RingKeylineWidth = 3.5.dp
private val RingColorWidth = 2.dp
private val DotRimRadius = 4.dp
private val DotRadius = 3.dp
private val TapTolerance = 16.dp

/**
 * TOP-DOWN view (spec §7): one dot per kept shot plus a fitted minimum-enclosing
 * dispersion ring per club, in the club's ramp colour, over the range ground
 * treatment. The legend carries colour + club name only — deliberately no shot
 * count (user ruling 2026-10-16). Canvas and tap hit-test share one
 * [BagTopDownFit], so a dot can never be drawn where it cannot be tapped.
 */
@Composable
fun BagTopDownView(
    clubs: List<ClubDots>,
    colors: Map<String, Color>,
    onClubTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val withData = clubs.filter { it.points.isNotEmpty() }
    if (withData.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No shots to plot.", style = GolfTypography.Body, color = GolfColors.TextSecondary)
        }
        return
    }
    Column(modifier.fillMaxSize()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
        ) {
            withData.forEach { club ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors[club.clubName] ?: GolfColors.TextSecondary),
                    )
                    Text(club.clubName, style = GolfTypography.Status, color = GolfColors.TextSecondary)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val fit = remember(widthPx, heightPx, withData) {
                computeBagTopDownFit(widthPx, heightPx, withData)
            }
            val tolerance = with(LocalDensity.current) { TapTolerance.toPx() }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(fit) {
                        detectTapGestures { offset ->
                            val current = fit ?: return@detectTapGestures
                            val hit = withData
                                .flatMap { club -> club.points.map { club.clubName to it } }
                                .minByOrNull { (_, point) ->
                                    val dx = current.sx(point.second) - offset.x
                                    val dy = current.sy(point.first) - offset.y
                                    dx * dx + dy * dy
                                } ?: return@detectTapGestures
                            val dx = current.sx(hit.second.second) - offset.x
                            val dy = current.sy(hit.second.first) - offset.y
                            if (dx * dx + dy * dy <= tolerance * tolerance) onClubTap(hit.first)
                        }
                    },
            ) {
                fit?.let { drawTopDown(it, withData, colors) }
            }
        }
    }
}

private fun DrawScope.drawTopDown(fit: BagTopDownFit, clubs: List<ClubDots>, colors: Map<String, Color>) {
    // Ground: rough base, mown fairway corridor, 10 m stripes clipped to the
    // window — the same treatment as the fitting top-down (FittingTopDown.kt
    // 172-198), with the distance axis on x instead of y.
    drawRect(GolfColors.RangeRough, size = size)
    val corridorHalfM = RangeScene.fairwayHalfWidth(0.0)
    val corridorTop = fit.sy(corridorHalfM)
    val corridorBottom = fit.sy(-corridorHalfM)
    drawRect(
        color = GolfColors.RangeFairway,
        topLeft = Offset(fit.sx(RangeScene.FAIRWAY_TEE_Y), corridorTop),
        size = Size(
            fit.sx(RangeScene.FAIRWAY_END_Y) - fit.sx(RangeScene.FAIRWAY_TEE_Y),
            corridorBottom - corridorTop,
        ),
    )
    for (band in 0 until RangeScene.stripeBandCount()) {
        val (fromM, toM) = RangeScene.stripeBand(band)
        val lo = maxOf(fromM, fit.windowMinM)
        val hi = minOf(toM, fit.windowMaxM)
        if (hi <= lo) continue
        drawRect(
            color = if (RangeScene.stripeIsLight(0.0, fromM)) {
                GolfColors.RangeStripeLight
            } else {
                GolfColors.RangeStripeDark
            },
            topLeft = Offset(fit.sx(lo), corridorTop),
            size = Size(fit.sx(hi) - fit.sx(lo), corridorBottom - corridorTop),
        )
    }
    // Distance gridlines with labels, inside the window only.
    val gridPaint = android.graphics.Paint().apply {
        color = GolfColors.TextMuted.toArgb()
        textSize = 10.sp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    var gridM = ceil(fit.windowMinM / GRID_STEP_M) * GRID_STEP_M
    while (gridM <= fit.windowMaxM) {
        val x = fit.sx(gridM)
        drawLine(GolfColors.Line, Offset(x, 0f), Offset(x, size.height), 1f)
        drawContext.canvas.nativeCanvas.drawText(
            String.format(Locale.US, "%.0f", gridM),
            x,
            size.height - 4f,
            gridPaint,
        )
        gridM += GRID_STEP_M
    }

    // Rings under dots: minimum-enclosing ellipse per club, buffered 5 %.
    clubs.forEach { club ->
        val color = colors[club.clubName] ?: GolfColors.TextSecondary
        val ellipse = DispersionEllipse.fromKept(club.points) ?: return@forEach
        val path = Path()
        for (step in 0..RING_STEPS) {
            val t = 2.0 * PI * step / RING_STEPS
            val a = ellipse.semiAxisM * cos(t)
            val b = ellipse.semiCrossM * sin(t)
            val sideM = ellipse.centreSideM + a * cos(ellipse.angleRad) - b * sin(ellipse.angleRad)
            val distanceM = ellipse.centreTotalM + a * sin(ellipse.angleRad) + b * cos(ellipse.angleRad)
            val x = fit.sx(distanceM)
            val y = fit.sy(sideM)
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(width = RingKeylineWidth.toPx()))
        drawPath(path, color.copy(alpha = 0.7f), style = Stroke(width = RingColorWidth.toPx()))
    }
    // Dots on top.
    clubs.forEach { club ->
        val color = colors[club.clubName] ?: GolfColors.TextSecondary
        club.points.forEach { (lateralM, distanceM) ->
            val centre = Offset(fit.sx(distanceM), fit.sy(lateralM))
            drawCircle(Color.White.copy(alpha = 0.6f), DotRimRadius.toPx(), centre)
            drawCircle(color.copy(alpha = 0.9f), DotRadius.toPx(), centre)
        }
    }
}
