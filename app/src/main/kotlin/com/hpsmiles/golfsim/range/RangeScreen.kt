package com.hpsmiles.golfsim.range

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTheme
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.MetricChip
import com.hpsmiles.golfsim.core.designsystem.MetricRow
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import java.util.Locale
import kotlin.math.sqrt

/**
 * Uniform overlay chip for the canvas overlays — Status font, same proportions
 * as the VIEW and speed-multiplier chips.
 */
@Composable
private fun OverlayChip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = ChipFont,
        color = if (active) GolfColors.Teal else GolfColors.TextMuted,
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(1.dp, if (active) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
            .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
    )
}

private val ChipFont = GolfTypography.Status.copy(fontSize = 15.sp)

/** Metres/second to mph for display. */
private const val MPH_PER_MS = 2.23694

private enum class SpeedMult(val label: String, val divisor: Float) {
    X1("1x", 1f), X15("1.5x", 1.5f), X2("2x", 2f), X4("4x", 4f);
}

private enum class ViewMode(val label: String) { POV("POV"), TOP_DOWN("TOP-DOWN") }

/**
 * Phase A range screen: demo-shot pipeline. FIRE solves a shot through the
 * real BallFlightEngine and renders metrics INSTANTLY; the tracer animation
 * plays alongside at real duration divided by the speed multiplier. In
 * Phase B/C the DemoShotSource is swapped for the BLE source — the UI
 * does not know where shots come from.
 *
 * Tracer controls (2026-09-24 user request): back INSIDE the range frame at
 * the top-left, using the same Status chip style as VIEW (top-right).
 * FIRE / MODE / CONNECT moved to AppRoot's left rail column.
 */
@Composable
fun RangeScreen(
    modifier: Modifier = Modifier,
    session: RangeSession,
) {
    var playFraction by remember { mutableFloatStateOf(1f) }
    var speedMult by remember { mutableStateOf(SpeedMult.X15) }
    var viewMode by remember { mutableStateOf(ViewMode.POV) }
    var showTracer by remember { mutableStateOf(true) }
    var showHistory by remember { mutableStateOf(true) }
    var historyLimit by remember { mutableFloatStateOf(8f) }
    val currentShot = session.shots.lastOrNull()

    // Follow cam (spec 2026-09-25): the per-frame camera from the pure
    // phase machine; STATIC whenever there is nothing in flight.
    val camera = when (val shot = currentShot) {
        null -> RangeCamera.STATIC
        else -> FollowCam.cameraAt(shot.shotResult, playFraction)
    }

    // M4d: live BLE connection state lives in AppRoot (which now also owns
    // DEMO toggle, FIRE and CONNECT).

    // New shot (demo or live): restart the tracer animation.
    LaunchedEffect(session.tick.intValue) {
        playFraction = 0f
    }

    // Tracer playback: animate playFraction over the shot's real duration,
    // EXTENDED past 1 by the follow-cam landing hold (FollowCam.endFraction).
    // The speed multiplier scales the whole timeline, hold included —
    // consistent with slow-mo review.
    LaunchedEffect(currentShot, speedMult) {
        val shot = currentShot ?: return@LaunchedEffect
        if (shot.shotResult.flightTimeSec <= 0.0) return@LaunchedEffect
        val durationMs = shot.shotResult.flightTimeSec * 1000.0 / speedMult.divisor
        val end = FollowCam.endFraction(shot.shotResult).toFloat()
        var lastNanos = withFrameNanos { it }
        while (playFraction < end) {
            val now = withFrameNanos { it }
            val deltaMs = (now - lastNanos) / 1_000_000.0
            lastNanos = now
            playFraction = (playFraction + (deltaMs / durationMs).toFloat()).coerceAtMost(end)
        }
    }

    // Previous-shot lines: faded, most recent first, limited by the slider.
    val previousShots = if (showHistory) {
        session.shots.dropLast(1).map { it.shotResult }.takeLast(historyLimit.toInt())
    } else {
        emptyList()
    }

    Row(modifier = modifier.fillMaxSize().background(GolfColors.Base)) {
        // Range canvas with overlays. Left of the range there is exactly one
        // column: AppRoot's NavRail (2026-09-24 user request).
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (viewMode == ViewMode.POV) {
                PovRangeCanvas(
                    currentShot?.shotResult, previousShots, playFraction, showTracer, showHistory,
                    camera = camera,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                TopDownCanvas(session.shots, Modifier.fillMaxSize())
            }
            // M4d no-read pill: live misreads coalesced in RangeSession.
            if (session.misreadCount.intValue > 0) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.align(Alignment.BottomStart).padding(GolfSpacing.Sm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "no read (${session.misreadCount.intValue})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { session.dismissMisreads() }) {
                            Text("dismiss")
                        }
                    }
                }
            }
            // Tracer controls: back inside the range frame, top-left (2026-09-24).
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(GolfSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                    OverlayChip("TRACER: ${if (showTracer) "ON" else "OFF"}", active = showTracer) {
                        showTracer = !showTracer
                    }
                    if (showTracer) {
                        OverlayChip("PREV: ${if (showHistory) "ON" else "OFF"}", active = showHistory) {
                            showHistory = !showHistory
                        }
                    }
                }
                if (showTracer && showHistory) {
                    Slider(
                        value = historyLimit,
                        onValueChange = { historyLimit = it },
                        valueRange = 0f..20f,
                        steps = 19,
                        modifier = Modifier.width(110.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = GolfColors.Teal,
                            activeTrackColor = GolfColors.Teal,
                            inactiveTrackColor = GolfColors.Line,
                        ),
                    )
                    Text(
                        "LAST ${historyLimit.toInt()} LINES",
                        color = GolfColors.TextMuted,
                        style = GolfTypography.Status,
                    )
                }
            }
            Text(
                "VIEW: ${viewMode.label}",
                color = GolfColors.TextSecondary,
                style = ChipFont,
                modifier = Modifier.align(Alignment.TopEnd).padding(GolfSpacing.Sm)
                    .clickable {
                        viewMode = if (viewMode == ViewMode.POV) ViewMode.TOP_DOWN else ViewMode.POV
                    }
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                    .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
            )
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(GolfSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
            ) {
                for (speed in SpeedMult.entries) {
                    Text(
                        speed.label,
                        color = if (speed == speedMult) GolfColors.Teal else GolfColors.TextMuted,
                        style = GolfTypography.Status,
                        modifier = Modifier
                            .clickable { speedMult = speed }
                            .border(1.dp, if (speed == speedMult) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                            .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                    )
                }
            }
            // FIRE / MODE / CONNECT moved to AppRoot's rail (2026-09-24).
        }

        // Right panel: metrics.
        Column(
            modifier = Modifier.width(210.dp).fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .background(GolfColors.Panel)
                .padding(GolfSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
        ) {
            SectionCard("LAST SHOT") {
                val shot = currentShot
                if (shot == null) {
                    Text("Fire a shot", style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
                } else {
                    val r = shot.shotResult
                    MetricChip("carry", String.format(Locale.US, "%.0f", r.carryM), "M")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip("total", String.format(Locale.US, "%.0f", r.totalM), "M")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip(
                        "ball", String.format(Locale.US, "%.1f", shot.ballData.ballSpeed * MPH_PER_MS), "MPH",
                        accent = GolfColors.Amber,
                    )
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    // totalSpin is an Int (M1 BallData) — %d, not %.0f (IllegalFormatConversionException).
                    MetricChip("spin", String.format(Locale.US, "%d", shot.ballData.totalSpin), "RPM")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip("launch", String.format(Locale.US, "%.1f", shot.ballData.launchAngle), "DEG")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip("axis", String.format(Locale.US, "%.1f", shot.ballData.spinAxis), "DEG")
                }
            }
            SectionCard("SESSION") {
                val carries = session.shots.map { it.shotResult.carryM }
                val count = carries.size
                MetricRow("shots", "$count", "")
                MetricRow(
                    "avg carry",
                    if (count == 0) "-" else String.format(Locale.US, "%.0f", carries.average()),
                    "M",
                )
                val sigma = if (count > 1) {
                    val mean = carries.average()
                    sqrt(carries.sumOf { (it - mean) * (it - mean) } / count)
                } else 0.0
                MetricRow("sigma", if (count > 1) String.format(Locale.US, "%.1f", sigma) else "-", "M")
            }
        }
    }
}
