package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.bag.ClubQualityGate
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.fitting.FittingStats
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.range.ChipFont
import java.util.Locale

/**
 * M7 results table (design notes §2.2): frozen CLUB column (150 dp, Panel)
 * + a horizontally scrollable metric row (n · CHS · BALL SPEED · SMASH ·
 * CARRY · TOTAL · LAUNCH · DIR · SPIN · SPIN AXIS · OFFLINE · AREA), a Δ row
 * per non-baseline club (3+ clubs) or one Δ B−A under the pair (2 clubs), and a
 * tap-to-expand drill-down below each club's rows. Excluded shots drop from
 * every aggregate — single source of truth: fitting_shots.excluded.
 *
 * [distanceMode] drives the CARRY|TOTAL emphasis: the mode's column renders
 * TextPrimary with a 1 dp Teal top border; the other distance column dims to
 * TextSecondary. Delta cells dim to TextMuted inside the noise band and
 * brighten to TextPrimary outside it (never green/red — favourable direction
 * differs per metric, design notes §2.2).
 */
@Composable
internal fun FittingTable(
    shots: List<FittingShotEntity>,
    baselineClubId: Long?,
    onBaselineChange: (Long) -> Unit,
    onSetExcluded: (List<Long>, Boolean) -> Unit,
    readOnly: Boolean,
    distanceMode: FittingDistanceMode,
    modifier: Modifier = Modifier,
) {
    val (order, summaries, areaByClub) = remember(shots) {
        val o = FittingStats.clubOrder(shots)
        val sums = o.map { FittingStats.summarize(it, shots) }
        // AREA column (device ruling 2026-10-08): area of the 5 %-buffered
        // kept-shot bounding box in m² per club, computed from the club's KEPT
        // shots' (side, total) pairs — the same buffered rectangle the top-down
        // ring draws, so table and ring agree. null when < 3 kept shots
        // (DispersionBox.fromKept's own null contract).
        val areas = o.associate { key ->
            val kept = shots.filter { it.clubId == key.id && !it.excluded }
            key.id to DispersionBox.fromKept(kept.map { it.sideM to it.totalM })?.bufferedAreaM2()
        }
        Triple(o, sums, areas)
    }
    val cols = COLS(areaByClub)
    val baseline = summaries.firstOrNull { it.key.id == baselineClubId } ?: summaries.firstOrNull()
    var expandedId by remember { mutableStateOf<Long?>(null) }
    // One shared horizontal scroll so the header, every club row and every Δ
    // row scroll in lockstep (design notes §2.2 — a single scrollable region).
    val metricScroll = rememberScrollState()

    Column(modifier.verticalScroll(rememberScrollState())) {
        // Header row: frozen CLUB cell + scrollable metric labels.
        Row(verticalAlignment = Alignment.CenterVertically) {
            FrozenHeaderCell()
            Row(Modifier.horizontalScroll(metricScroll)) {
                cols.forEach { col -> MetricHeaderCell(col, distanceMode) }
            }
        }
        RowSeparator()

        if (summaries.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(top = GolfSpacing.Md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No comparison yet — hit shots in COMPARE",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextMuted,
                )
            }
        }

        summaries.forEachIndexed { index, s ->
            val isBaseline = baseline != null && s.key.id == baseline.key.id
            val color = FittingColors.clubColor(index)
            Column {
                ClubRow(
                    s = s,
                    color = color,
                    isBaseline = isBaseline,
                    showBaselineControl = summaries.size >= 3,
                    metricScroll = metricScroll,
                    distanceMode = distanceMode,
                    cols = cols,
                    onToggle = { expandedId = if (expandedId == s.key.id) null else s.key.id },
                    onBaselineChange = { onBaselineChange(s.key.id) },
                )
                RowSeparator()
                // Δ rows: 3+ clubs → a Δ row directly under each non-baseline club.
                if (summaries.size >= 3 && baseline != null && !isBaseline) {
                    DeltaRow(label = "Δ ${s.key.name}", other = s, baseline = baseline, metricScroll = metricScroll, cols = cols)
                }
                // Drill-down: full card width, below this club's rows — NOT
                // inside the horizontal scroll (design notes §2.2.1, §4.4).
                if (expandedId == s.key.id) {
                    DrillDown(
                        s = s,
                        clubShots = shots.filter { it.clubId == s.key.id },
                        color = color,
                        readOnly = readOnly,
                        onSetExcluded = onSetExcluded,
                        onClose = { expandedId = null },
                    )
                }
            }
        }
        // Exactly 2 clubs → one Δ B−A line under the pair (design notes §2.2).
        if (summaries.size == 2 && baseline != null) {
            val other = summaries.first { it.key.id != baseline.key.id }
            DeltaRow(
                label = "Δ ${other.key.name}−${baseline.key.name}",
                other = other,
                baseline = baseline,
                metricScroll = metricScroll,
                cols = cols,
            )
        }
    }
}

private val FROZEN_WIDTH: Dp = 150.dp
private const val ROW_HEIGHT = 40

/** Frozen-column header cell: "CLUB", Panel background (design notes §2.2). */
@Composable
private fun FrozenHeaderCell() {
    Box(
        Modifier
            .width(FROZEN_WIDTH)
            .fillMaxHeight()
            .background(GolfColors.Panel)
            .padding(horizontal = GolfSpacing.Sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text("CLUB", style = GolfTypography.MetricLabel, color = GolfColors.TextSecondary)
    }
}

/** One metric column label (two-line allowed: BALL\nSPEED, SPIN\nAXIS). */
@Composable
private fun MetricHeaderCell(col: Col, distanceMode: FittingDistanceMode) {
    val emphasized = col.emphasized(distanceMode)
    Column(Modifier.width(col.width)) {
        if (emphasized) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(GolfColors.Teal))
        }
        Text(
            col.label,
            style = GolfTypography.MetricLabel,
            color = if (emphasized) GolfColors.TextPrimary else GolfColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

/**
 * One club row: frozen cell (dot + name + count badge, tap toggles drill-down
 * and — when 3+ clubs — moves the baseline) + scrollable metric cells. The
 * baseline row carries a 1 dp Teal border; other rows carry an invisible
 * transparent border so every row keeps identical 40 dp geometry.
 */
@Composable
private fun ClubRow(
    s: FittingStats.ClubSummary,
    color: Color,
    isBaseline: Boolean,
    showBaselineControl: Boolean,
    metricScroll: ScrollState,
    distanceMode: FittingDistanceMode,
    cols: List<Col>,
    onToggle: () -> Unit,
    onBaselineChange: () -> Unit,
) {
    Row(
        Modifier
            .height(ROW_HEIGHT.dp)
            .border(1.dp, if (isBaseline) GolfColors.Teal else Color.Transparent, RoundedCornerShape(GolfSpacing.Sm)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .width(FROZEN_WIDTH)
                .fillMaxHeight()
                .background(GolfColors.Panel)
                .clickable {
                    if (showBaselineControl && !isBaseline) onBaselineChange()
                    onToggle()
                }
                .padding(horizontal = GolfSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(
                s.key.name,
                style = GolfTypography.Body,
                color = GolfColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CountBadge(s.kept)
        }
        Row(Modifier.horizontalScroll(metricScroll), verticalAlignment = Alignment.CenterVertically) {
            cols.forEach { col -> MetricValueCell(col, s, distanceMode) }
        }
    }
}

/** One metric value cell; CARRY/TOTAL render avg + σ on two lines. */
@Composable
private fun MetricValueCell(col: Col, s: FittingStats.ClubSummary, distanceMode: FittingDistanceMode) {
    val emphasized = col.emphasized(distanceMode)
    Column(Modifier.width(col.width).fillMaxHeight()) {
        if (emphasized) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(GolfColors.Teal))
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (col.metric == Metric.CARRY || col.metric == Metric.TOTAL) {
                TwoLineCell(
                    avg = if (col.metric == Metric.CARRY) s.carry else s.total,
                    sigma = if (col.metric == Metric.CARRY) s.carrySigma else s.totalSigma,
                    emphasized = emphasized,
                )
            } else {
                Text(
                    col.club(s),
                    style = GolfTypography.MetricValue.copy(fontSize = 15.sp),
                    color = when {
                        emphasized -> GolfColors.TextPrimary
                        col.metric == Metric.N -> GolfColors.TextSecondary
                        else -> GolfColors.TextPrimary
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** CARRY/TOTAL cell: "avg" m (bold) with "± σ m" (Unit) on a second line. */
@Composable
private fun TwoLineCell(avg: Double?, sigma: Double?, emphasized: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        if (avg == null) {
            Text("-", style = GolfTypography.MetricValue.copy(fontSize = 15.sp), color = GolfColors.TextPrimary)
        } else {
            Text(
                String.format(Locale.US, "%.0f m", avg),
                style = GolfTypography.MetricValue.copy(fontSize = 15.sp),
                color = if (emphasized) GolfColors.TextPrimary else GolfColors.TextSecondary,
            )
            if (sigma != null && sigma >= 0.05) {
                Text(
                    "± " + String.format(Locale.US, "%.1f m", sigma),
                    style = GolfTypography.Unit,
                    color = GolfColors.TextSecondary,
                )
            }
        }
    }
}

/** Δ row: frozen label cell + signed metric deltas, dimmed inside the noise band. */
@Composable
private fun DeltaRow(
    label: String,
    other: FittingStats.ClubSummary,
    baseline: FittingStats.ClubSummary,
    metricScroll: ScrollState,
    cols: List<Col>,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = GolfTypography.MetricLabel,
            color = GolfColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(FROZEN_WIDTH)
                .background(GolfColors.Panel)
                .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
        )
        Row(Modifier.horizontalScroll(metricScroll), verticalAlignment = Alignment.CenterVertically) {
            cols.forEach { col ->
                // Named args on purpose: (other, baseline) positional is
                // silently swappable and flips the delta sign.
                val verdict = FittingStats.delta(
                    other = col.pick(other),
                    baseline = col.pick(baseline),
                    noise = col.noise,
                )
                Text(
                    verdict?.let { col.deltaFmt(it.delta) } ?: "-",
                    style = GolfTypography.MetricValue.copy(fontSize = 13.sp),
                    // spec §4 / notes §2.2: within-noise deltas dim to TextMuted,
                    // significant deltas are TextPrimary — never green/red.
                    color = if (verdict == null || !verdict.significant) GolfColors.TextMuted else GolfColors.TextPrimary,
                    modifier = Modifier.width(col.width).padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** 1 dp horizontal separator spanning both regions (design notes §2.2). */
@Composable
private fun RowSeparator() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(GolfColors.Line))
}

/**
 * Expandable drill-down (design notes §2.2.1): full card width, outside the
 * horizontal scroll. Header = dot + name + count badge + "· N excluded" +
 * EXCLUDE LIKELY MISREADS (amber ghost, disabled < 3 shots) + CLOSE. Shot
 * rows = keep/exclude tick (22 dp) + carry · total · ball · CHS · smash ·
 * spin · dir. Excluded rows render at alpha 0.45 with TextSecondary values.
 */
@Composable
private fun DrillDown(
    s: FittingStats.ClubSummary,
    clubShots: List<FittingShotEntity>,
    color: Color,
    readOnly: Boolean,
    onSetExcluded: (List<Long>, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val kept = clubShots.count { !it.excluded }
    val excluded = clubShots.size - kept
    Column(Modifier.fillMaxWidth().padding(top = GolfSpacing.Sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(
                s.key.name,
                style = GolfTypography.ScreenTitle,
                color = GolfColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CountBadge(kept)
            Text("· $excluded excluded", style = GolfTypography.Status, color = GolfColors.TextMuted)
            if (!readOnly) {
                val duffIds = FittingStats.applyDuffFilter(clubShots).filter { it.filtered }.map { it.id }
                val unexcludedDuffs = clubShots.filter { it.id in duffIds && !it.excluded }.map { it.id }
                ExcludeMisreads(
                    enabled = clubShots.size >= 3 && unexcludedDuffs.isNotEmpty(),
                    onClick = { onSetExcluded(unexcludedDuffs, true) },
                )
            }
            CloseButton(onClick = onClose)
        }
        // Label header once; values align under it via shared column widths.
        Row(Modifier.padding(top = GolfSpacing.Xs)) {
            Spacer(Modifier.width(TICK_SLOT))
            SHOT_COLS.forEach { col ->
                Text(
                    col.label,
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.TextMuted,
                    modifier = Modifier.width(col.width),
                )
            }
        }
        clubShots.forEach { shot ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .alpha(if (shot.excluded) 0.45f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(TICK_SLOT), contentAlignment = Alignment.CenterStart) {
                    if (!readOnly) {
                        ShotTick(
                            excluded = shot.excluded,
                            onToggle = { onSetExcluded(listOf(shot.id), !shot.excluded) },
                        )
                    }
                }
                SHOT_COLS.forEach { col ->
                    Text(
                        col.fmt(shot),
                        style = GolfTypography.BodySmall,
                        color = if (shot.excluded) GolfColors.TextSecondary else GolfColors.TextPrimary,
                        modifier = Modifier.width(col.width),
                    )
                }
            }
        }
    }
}

/** Keep/exclude tick control (design notes §2.2.1): 22 dp square — kept = Line border + teal ✓, excluded = Amber border + amber ✕. */
@Composable
private fun ShotTick(excluded: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(GolfSpacing.Sm)
    Box(
        Modifier
            .size(22.dp)
            .clip(shape)
            .border(1.dp, if (excluded) GolfColors.Amber else GolfColors.Line, shape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (excluded) "✕" else "✓",
            style = GolfTypography.BodySmall,
            color = if (excluded) GolfColors.Amber else GolfColors.Teal,
        )
    }
}

/** EXCLUDE LIKELY MISREADS — amber ghost; disabled-styled (Line/TextMuted) when the club has < 3 shots or nothing to exclude. */
@Composable
private fun ExcludeMisreads(enabled: Boolean, onClick: () -> Unit) {
    Text(
        "EXCLUDE LIKELY MISREADS",
        style = ChipFont,
        color = if (enabled) GolfColors.Amber else GolfColors.TextMuted,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .border(1.dp, if (enabled) GolfColors.Amber else GolfColors.Line, RoundedCornerShape(50))
            .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
    )
}

/** CLOSE — ghost pill closing the open drill-down. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Text(
        "CLOSE",
        style = ChipFont,
        color = GolfColors.TextSecondary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
            .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
    )
}

/** Count badge (design notes §0.3): 18 dp pill; Amber border+text under the 5-kept target. */
@Composable
private fun CountBadge(count: Int) {
    val low = count < ClubQualityGate.TARGET_KEPT
    Box(
        Modifier
            .height(18.dp)
            .background(GolfColors.Panel, RoundedCornerShape(50))
            .border(1.dp, if (low) GolfColors.Amber else GolfColors.Line, RoundedCornerShape(50))
            .padding(horizontal = GolfSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$count",
            style = GolfTypography.Status,
            color = if (low) GolfColors.Amber else GolfColors.TextSecondary,
        )
    }
}

private val TICK_SLOT = 26.dp

/** One metric column: identity, label, width, kept-shot picker, formatter, delta formatter, noise band. */
private class Col(
    val metric: Metric,
    val label: String,
    val width: Dp,
    val pick: (FittingStats.ClubSummary) -> Double?,
    val club: (FittingStats.ClubSummary) -> String,
    val deltaFmt: (Double) -> String,
    val noise: Double,
) {
    val isDistance: Boolean get() = metric == Metric.CARRY || metric == Metric.TOTAL

    /** CARRY|TOTAL emphasis honours distanceMode (design notes §2.2). */
    fun emphasized(mode: FittingDistanceMode): Boolean = when (metric) {
        Metric.CARRY -> mode == FittingDistanceMode.CARRY
        Metric.TOTAL -> mode == FittingDistanceMode.TOTAL
        else -> false
    }
}

private enum class Metric { N, CHS, BALL, SMASH, CARRY, TOTAL, LAUNCH, DIR, SPIN, AXIS, OFFLINE, AREA }

/**
 * Column set (design notes §2.2): n 40 dp, SMASH 64 dp, all others 88 dp.
 * CHS/ball speed DISPLAY in MPH (× FittingFormats.MPH_PER_MS) per the
 * 2026-10-08 user ruling (notes' "(m/s)" formats superseded for display);
 * the noise-band delta comparison stays in the m/s domain — [Col.pick]
 * returns m/s and [FittingStats.NOISE_CHS_MPS] / [NOISE_BALL_SPEED_MPS]
 * are m/s. Values/headers: MetricLabel TextSecondary (TextPrimary + 1 dp
 * Teal top border when the distance mode emphasises the column).
 *
 * AREA (device ruling 2026-10-08): the club's buffered-bbox area in m²
 * (DispersionBox.bufferedAreaM2 over the KEPT shots' side/total pairs — the
 * same 5 %-buffered rectangle the top-down ring draws) — a data-column with
 * no Δ row (area deltas are quadratic noise, deliberately excluded).
 * [Col.pick] → null renders "-" directly, the same no-delta convention N
 * already uses.
 *
 * Three formatters are structurally unreachable but kept so every Col is
 * uniform: N's [Col.deltaFmt] (pick → null renders "-" directly), the
 * CARRY/TOTAL [Col.club] lambdas (those cells render via TwoLineCell), and
 * AREA's pick/deltaFmt (pick → null renders "-" directly).
 */
private fun COLS(areaByClub: Map<Long, Double?>): List<Col> = listOf(
    Col(Metric.N, "n", 40.dp, { null }, { s -> "${s.kept}" }, { "-" }, 0.0),
    Col(Metric.CHS, "CHS", 88.dp, { it.chs }, { s -> s.chs?.let { FittingFormats.mph(it) } ?: "-" },
        { d -> String.format(Locale.US, "%+.1f", d * FittingFormats.MPH_PER_MS) }, FittingStats.NOISE_CHS_MPS),
    Col(Metric.BALL, "BALL\nSPEED", 88.dp, { it.ballSpeed }, { s -> s.ballSpeed?.let { FittingFormats.mph(it) } ?: "-" },
        { d -> String.format(Locale.US, "%+.1f", d * FittingFormats.MPH_PER_MS) }, FittingStats.NOISE_BALL_SPEED_MPS),
    Col(Metric.SMASH, "SMASH", 64.dp, { it.smash }, { s -> s.smash?.let { String.format(Locale.US, "%.2f", it) } ?: "-" },
        { d -> String.format(Locale.US, "%+.2f", d) }, FittingStats.NOISE_SMASH),
    Col(Metric.CARRY, "CARRY", 88.dp, { it.carry }, { s -> FittingFormats.avgSigma(s.carry, s.carrySigma) },
        { d -> String.format(Locale.US, "%+.0f m", d) }, FittingStats.NOISE_CARRY_M),
    Col(Metric.TOTAL, "TOTAL", 88.dp, { it.total }, { s -> FittingFormats.avgSigma(s.total, s.totalSigma) },
        { d -> String.format(Locale.US, "%+.0f m", d) }, FittingStats.NOISE_TOTAL_M),
    Col(Metric.LAUNCH, "LAUNCH", 88.dp, { it.launch }, { s -> FittingFormats.deg(s.launch) },
        { d -> String.format(Locale.US, "%+.1f°", d) }, FittingStats.NOISE_LAUNCH_DEG),
    Col(Metric.DIR, "DIR", 88.dp, { it.dir }, { s -> FittingFormats.degSigned(s.dir) },
        { d -> String.format(Locale.US, "%+.1f°", d) }, FittingStats.NOISE_DIR_DEG),
    Col(Metric.SPIN, "SPIN", 88.dp, { it.spin }, { s -> FittingFormats.rpm(s.spin) },
        { d -> String.format(Locale.US, "%+.0f", d) }, FittingStats.NOISE_SPIN_RPM),
    Col(Metric.AXIS, "SPIN\nAXIS", 88.dp, { it.spinAxis }, { s -> FittingFormats.degSigned(s.spinAxis) },
        { d -> String.format(Locale.US, "%+.1f°", d) }, FittingStats.NOISE_SPIN_AXIS_DEG),
    Col(Metric.OFFLINE, "OFFLINE", 88.dp, { it.offlineAvg },
        { s -> if (s.offlineAvg != null && s.offlineWorst != null) String.format(Locale.US, "%.0f / %.0f", s.offlineAvg, s.offlineWorst) else "-" },
        { d -> String.format(Locale.US, "%+.1f m", d) }, FittingStats.NOISE_OFFLINE_M),
    // AREA: buffered-bbox area in m² (device ruling 2026-10-08).
    // pick → null = no Δ row (quadratic noise — deliberately excluded); the
    // "-" renders for < 3 kept shots.
    Col(Metric.AREA, "AREA", 88.dp,
        { null },
        { s -> areaByClub[s.key.id]?.let { String.format(Locale.US, "%.0f m²", it) } ?: "-" },
        { "-" }, 0.0),
)

/** Drill-down shot columns (design notes §2.2.1): carry · total · ball · CHS · smash · spin · dir. */
private data class ShotCol(val label: String, val width: Dp, val fmt: (FittingShotEntity) -> String)

private val SHOT_COLS = listOf(
    ShotCol("CARRY", 64.dp) { String.format(Locale.US, "%.0f m", it.carryM) },
    ShotCol("TOTAL", 64.dp) { String.format(Locale.US, "%.0f m", it.totalM) },
    ShotCol("BALL", 64.dp) { String.format(Locale.US, "%.1f MPH", it.ballSpeedMps * FittingFormats.MPH_PER_MS) },
    ShotCol("CHS", 64.dp) { String.format(Locale.US, "%.1f MPH", it.clubHeadSpeedMps * FittingFormats.MPH_PER_MS) },
    ShotCol("SMASH", 56.dp) {
        if (it.clubHeadSpeedMps > 0.0) String.format(Locale.US, "%.2f", it.ballSpeedMps / it.clubHeadSpeedMps) else "-"
    },
    ShotCol("SPIN", 72.dp) { "${it.totalSpinRpm}" },
    ShotCol("DIR", 56.dp) { String.format(Locale.US, "%+.1f°", it.launchDirDeg) },
)
