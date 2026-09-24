package com.hpsmiles.golfsim.range

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hpsmiles.golfsim.core.ble.DemoShotSource
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
 * Uniform control chip for the left controls panel — same box proportions as
 * the FIRE chip (user request 2026-09-24: "all of them a similar size").
 * Active chips tint teal like the rest of the design language.
 */
@Composable
private fun ControlChip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = GolfTypography.MetricLabel,
        color = if (active) GolfColors.Teal else GolfColors.TextPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, if (active) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
            .padding(horizontal = GolfSpacing.Xs, vertical = GolfSpacing.Sm),
    )
}

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
 * Tracer controls (user demo-gate feedback): a toggle for the current
 * shot's tracer, a sub-toggle for faded previous-shot lines, and a slider
 * for how many previous lines are shown.
 *
 * M4b connect flow: the CONNECT button checks/request the runtime BLE
 * permissions and fires [onConnectRequested]; AppRoot owns the GATT client
 * and the scanner. The MODE toggle keeps the M4a demo pipeline as fallback
 * (demo = FIRE produces seeded demo shots).
 */
@Composable
fun RangeScreen(
    modifier: Modifier = Modifier,
    demo: Boolean,
    onDemoChanged: (Boolean) -> Unit,
    onConnectRequested: () -> Unit = {},
    session: RangeSession,
) {
    val demoSource = remember { DemoShotSource() }
    var playFraction by remember { mutableFloatStateOf(1f) }
    var speedMult by remember { mutableStateOf(SpeedMult.X15) }
    var viewMode by remember { mutableStateOf(ViewMode.POV) }
    var showTracer by remember { mutableStateOf(true) }
    var showHistory by remember { mutableStateOf(true) }
    var historyLimit by remember { mutableFloatStateOf(8f) }
    val currentShot = session.shots.lastOrNull()

    // M4b: live BLE connection state. The MODE toggle state is hoisted to
    // AppRoot (it owns the live StatusStrip mapping); demo gates whether
    // FIRE produces demo shots.
    var permissionDenied by remember { mutableStateOf(false) }

    // New shot (demo or live): restart the tracer animation.
    LaunchedEffect(session.tick.intValue) {
        playFraction = 0f
    }

    // Tracer playback: animate playFraction over the shot's real duration.
    LaunchedEffect(currentShot, speedMult) {
        val shot = currentShot ?: return@LaunchedEffect
        if (shot.shotResult.flightTimeSec <= 0.0) return@LaunchedEffect
        val durationMs = shot.shotResult.flightTimeSec * 1000.0 / speedMult.divisor
        var lastNanos = withFrameNanos { it }
        while (playFraction < 1f) {
            val now = withFrameNanos { it }
            val deltaMs = (now - lastNanos) / 1_000_000.0
            lastNanos = now
            playFraction = (playFraction + (deltaMs / durationMs).toFloat()).coerceAtMost(1f)
        }
    }

    fun fire() {
        // Demo pipeline only fires in DEMO mode (live shots arrive via
        // AppRoot's onMeasurement → session.add).
        if (!demo) return
        session.add(demoSource.nextShot())
        // Metrics render immediately from session.shots; the tracer animates on top.
    }

    // Previous-shot lines: faded, most recent first, limited by the slider.
    val previousShots = if (showHistory) {
        session.shots.dropLast(1).map { it.shotResult }.takeLast(historyLimit.toInt())
    } else {
        emptyList()
    }

    // M4b runtime permission gate: the Compose activity-result launcher is the
    // plan-approved mechanism here (wiring note: the Activity cast variant and
    // the "tap CONNECT again" bench path were the sketch alternatives) — the
    // denial callback drives the guidance line without touching MainActivity.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionDenied = grants.values.any { !it }
    }

    Row(modifier = modifier.fillMaxSize().background(GolfColors.Base)) {
        // Left controls panel (user request 2026-09-24): FIRE, CONNECT, MODE
        // and the tracer toggles move OUT of the canvas overlays into a
        // dedicated column, all sized like the FIRE chip, so the POV view
        // grows and stays uncluttered.
        Column(
            modifier = Modifier
                .width(96.dp)
                .fillMaxHeight()
                .background(GolfColors.Panel)
                .padding(GolfSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
        ) {
            // FIRE — the primary action, amber, prominent.
            Text(
                "FIRE",
                color = GolfColors.Base,
                style = GolfTypography.MetricLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GolfColors.Amber, RoundedCornerShape(50))
                    .clickable { fire() }
                    .padding(horizontal = GolfSpacing.Xs, vertical = GolfSpacing.Sm),
            )
            ControlChip("CONNECT", active = false) {
                val scanGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_SCAN,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val connectGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (scanGranted && connectGranted) {
                    permissionDenied = false
                    onConnectRequested()
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ),
                    )
                }
            }
            ControlChip(if (demo) "MODE: DEMO" else "MODE: LIVE", active = !demo) {
                onDemoChanged(!demo)
            }
            ControlChip("TRACER: ${if (showTracer) "ON" else "OFF"}", active = showTracer) {
                showTracer = !showTracer
            }
            if (showTracer) {
                ControlChip("PREV: ${if (showHistory) "ON" else "OFF"}", active = showHistory) {
                    showHistory = !showHistory
                }
                if (showHistory) {
                    Slider(
                        value = historyLimit,
                        onValueChange = { historyLimit = it },
                        valueRange = 0f..20f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth(),
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
            if (permissionDenied) {
                Text(
                    text = "Bluetooth permission required - enable in system settings",
                    style = GolfTypography.Status,
                    color = GolfColors.AlertRed,
                )
            }
        }

        // Range canvas with overlays.
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (viewMode == ViewMode.POV) {
                PovRangeCanvas(
                    currentShot?.shotResult, previousShots, playFraction, showTracer, showHistory,
                    Modifier.fillMaxSize(),
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
            Text(
                "VIEW: ${viewMode.label}",
                color = GolfColors.TextSecondary,
                style = GolfTypography.Status,
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
            // FIRE moved to the left controls panel (2026-09-24).
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
