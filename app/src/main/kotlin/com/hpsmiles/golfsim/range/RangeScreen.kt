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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
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
internal fun OverlayChip(text: String, active: Boolean, onClick: () -> Unit) {
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

internal val ChipFont = GolfTypography.Status.copy(fontSize = 15.sp)

/** Metres/second to mph for display. */
private const val MPH_PER_MS = 2.23694

internal enum class SpeedMult(val label: String, val divisor: Float) {
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
    activeClubName: String? = null,
    clubs: List<ClubRecord> = emptyList(),
    onSelectClub: (String?) -> Unit = {},
    onAddClub: suspend (String, ClubType, Boolean) -> Boolean = { _, _, _ -> false },
) {
    var speedMult by remember { mutableStateOf(SpeedMult.X15) }
    var viewMode by remember { mutableStateOf(ViewMode.POV) }
    val currentShot = session.shots.lastOrNull()
    var showClubPicker by remember { mutableStateOf(false) }
    var showGreenPicker by remember { mutableStateOf(false) }

    // Tracer playback / follow cam / previous-shot lines live in
    // RangeLiveView (extracted so the BAG collecting view reuses them).

    Row(modifier = modifier.fillMaxSize().background(GolfColors.Base)) {
        // Range canvas with overlays. Left of the range there is exactly one
        // column: AppRoot's NavRail (2026-09-24 user request).
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (viewMode == ViewMode.POV) {
                RangeLiveView(
                    shots = session.shots,
                    modifier = Modifier.fillMaxSize(),
                    speedMult = speedMult,
                    customGreen = session.customGreen.value,
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
            // Tracer controls (TRACER/PREV + history slider) moved into
            // RangeLiveView — they render there, top-left, unchanged.
            // M5x E1+E2: VIEW chip + the filled ACTIVE CLUB trigger, top-right.
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(GolfSpacing.Sm),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
            ) {
                Text(
                    "VIEW: ${viewMode.label}",
                    color = GolfColors.TextSecondary,
                    style = ChipFont,
                    modifier = Modifier
                        .clickable {
                            viewMode = if (viewMode == ViewMode.POV) ViewMode.TOP_DOWN else ViewMode.POV
                        }
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                )
                ActiveClubButton(activeClubName) { showClubPicker = true }
                Text(
                    "GREEN: ${session.customGreen.value?.let { "${it.distanceM.toInt()} M" } ?: "OFF"}",
                    color = GolfColors.TextSecondary,
                    style = ChipFont,
                    modifier = Modifier
                        .clickable { showGreenPicker = !showGreenPicker }
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                )
            }
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
            // M5: club picker overlay — scrim covers the whole range area.
            if (showClubPicker) {
                ClubPickerOverlay(
                    activeClubName = activeClubName,
                    clubs = clubs,
                    onDismiss = { showClubPicker = false },
                    onSelectClub = onSelectClub,
                    onAddClub = onAddClub,
                )
            }
            // Item 3 (2026-10-01): practice-green setup overlay.
            if (showGreenPicker) {
                GreenSetupOverlay(
                    current = session.customGreen.value,
                    onSet = { dist, radius ->
                        session.setGreen(dist, radius)
                        showGreenPicker = false
                    },
                    onClear = {
                        session.clearGreen()
                        showGreenPicker = false
                    },
                    onDismiss = { showGreenPicker = false },
                )
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
                    // Club head speed is the only club metric the MLM2PRO transmits (MEASUREMENT 0-1);
                    // smash factor is derived client-side as ball speed / club speed (both m/s).
                    MetricChip("club", String.format(Locale.US, "%.1f", shot.ballData.clubHeadSpeed * MPH_PER_MS), "MPH")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip(
                        "smash",
                        if (shot.ballData.clubHeadSpeed > 0.0) {
                            String.format(Locale.US, "%.2f", shot.ballData.ballSpeed / shot.ballData.clubHeadSpeed)
                        } else "-",
                        "",
                    )
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    // totalSpin is an Int (M1 BallData) — %d, not %.0f (IllegalFormatConversionException).
                    MetricChip("spin", String.format(Locale.US, "%d", shot.ballData.totalSpin), "RPM")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip("launch", String.format(Locale.US, "%.1f", shot.ballData.launchAngle), "DEG")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    // HLA sign verified on-device M4c: − left / + right of target.
                    MetricChip("dir", String.format(Locale.US, "%+.1f", shot.ballData.launchDirection), "DEG")
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

/**
 * M5x E2: filled ACTIVE CLUB trigger — solid teal, ~170×58 dp, big club name
 * with ▾, "ACTIVE CLUB" label beneath. The ONLY filled colored control on
 * the range screen (everything else stays bordered/ghost style). "—" when
 * no club is active.
 */
@Composable
private fun ActiveClubButton(clubName: String?, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = GolfColors.Teal,
            shape = RoundedCornerShape(GolfSpacing.Sm),
            modifier = Modifier.size(170.dp, 58.dp).clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "${clubName ?: "—"} ▾",
                    style = GolfTypography.Hero.copy(fontSize = 26.sp),
                    color = GolfColors.Panel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = "ACTIVE CLUB",
            style = GolfTypography.MetricLabel,
            color = GolfColors.TextMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Item 3 (2026-10-01): user-defined practice green — distance + radius, or
 * CLEAR for no green (plain fairway). Mirrors GameSetupScreen's slider+field
 * sync pattern and ClubPickerOverlay's scrim-dismiss mechanic.
 */
@Composable
private fun GreenSetupOverlay(
    current: RangeScene.Green?,
    onSet: (Double, Double) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val distMin = 20.0
    val distMax = 320.0
    val radiusMin = 3.0
    val radiusMax = 20.0

    var distM by remember { mutableStateOf(current?.distanceM ?: 100.0) }
    var distText by remember { mutableStateOf(String.format(Locale.US, "%.0f", current?.distanceM ?: 100.0)) }
    var radiusM by remember { mutableStateOf(current?.radiusM ?: 8.0) }
    var radiusText by remember { mutableStateOf(String.format(Locale.US, "%.0f", current?.radiusM ?: 8.0)) }

    // Scrim covers the range area; tap outside dismisses (same mechanic as
    // ClubPickerOverlay).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GolfColors.Base.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        SectionCard(title = "PRACTICE GREEN", modifier = Modifier.padding(GolfSpacing.Xl)) {
            Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md)) {
                Slider(
                    value = distM.toFloat(),
                    onValueChange = {
                        distM = it.toDouble()
                        distText = String.format(Locale.US, "%.0f", distM)
                    },
                    valueRange = distMin.toFloat()..distMax.toFloat(),
                )
                OutlinedTextField(
                    value = distText,
                    onValueChange = { raw ->
                        distText = raw
                        raw.toDoubleOrNull()?.let { distM = it.coerceIn(distMin, distMax) }
                    },
                    label = { Text("Distance (m, 20-320)") },
                    modifier = Modifier.width(260.dp),
                )
                Slider(
                    value = radiusM.toFloat(),
                    onValueChange = {
                        radiusM = it.toDouble()
                        radiusText = String.format(Locale.US, "%.0f", radiusM)
                    },
                    valueRange = radiusMin.toFloat()..radiusMax.toFloat(),
                )
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = { raw ->
                        radiusText = raw
                        raw.toDoubleOrNull()?.let { radiusM = it.coerceIn(radiusMin, radiusMax) }
                    },
                    label = { Text("Radius (m, 3-20)") },
                    modifier = Modifier.width(260.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                    Button(onClick = { onSet(distM, radiusM) }) { Text("SET GREEN") }
                    OutlinedButton(onClick = onClear) { Text("CLEAR") }
                    OutlinedButton(onClick = onDismiss) { Text("CANCEL") }
                }
            }
        }
    }
}
