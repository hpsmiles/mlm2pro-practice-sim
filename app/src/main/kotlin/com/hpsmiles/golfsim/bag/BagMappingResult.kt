package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import java.util.Locale
import kotlin.math.abs

/**
 * RESULT state (spec §6): box-plot carry matrix in bag order, per-club stats
 * column, adjacent gap flags, RETEST + HISTORY, "test in progress" banner
 * while a retest runs, and per-club drill-down (all stored shots, kept vs
 * filtered). The drill-down's per-shot tick box (2026-10-16) layers a user
 * include/exclude override on the auto gate verdict; the effective state —
 * [BagMappingShotEntity.effectiveIncluded] — drives the aggregates, gaps and
 * kept-vs-filtered counts.
 */
@Composable
fun BagMappingResult(
    session: BagMappingSessionEntity,
    shots: List<BagMappingShotEntity>,
    isStaleResult: Boolean,
    onRetest: () -> Unit,
    onResume: () -> Unit,
    onOpenHistory: () -> Unit,
    onSetIncludeOverride: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drillClub by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        Text("BAG MAPPING", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
        Text(
            BagMappingFormats.dateTime(session.completedAtMs ?: session.startedAtMs),
            style = GolfTypography.BodySmall,
            color = GolfColors.TextSecondary,
        )
        if (isStaleResult) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GolfColors.Amber, RoundedCornerShape(GolfSpacing.CornerCard))
                    .padding(GolfSpacing.Md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TEST IN PROGRESS", style = GolfTypography.MetricLabel, color = GolfColors.Amber)
                Button(onClick = onResume, colors = ButtonDefaults.buttonColors(containerColor = GolfColors.Teal)) {
                    Text("RESUME")
                }
            }
        }
        CarryMatrixSection(session, shots, onDrillDown = { drillClub = it })
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md)) {
            if (!isStaleResult) {
                Button(onClick = onRetest, colors = ButtonDefaults.buttonColors(containerColor = GolfColors.Teal)) {
                    Text("RETEST")
                }
            }
            OutlinedButton(onClick = onOpenHistory) { Text("HISTORY") }
        }
    }
    drillClub?.let { clubName ->
        DrillDownPanel(
            clubName = clubName,
            shots = shots.filter { it.clubName == clubName },
            onSetIncludeOverride = onSetIncludeOverride,
            onDismiss = { drillClub = null },
        )
    }
}

@Composable
private fun ColumnScope.CarryMatrixSection(
    session: BagMappingSessionEntity,
    shots: List<BagMappingShotEntity>,
    onDrillDown: (String) -> Unit,
) {
    // Bag order comes from the session snapshot (spec §4); clubs without
    // shots render as "no data" rows (spec §9).
    val rows = session.clubSnapshot().map { (name, type) ->
        val clubShots = shots.filter { it.clubName == name }
        Triple(name, type.label, clubShots)
    }
    // ONE shared value axis over every club's kept+filtered carries AND kept
    // totals (spec §6 + item 4): the matrix's whole point is cross-club
    // comparison, so carry and total boxes render on the same metre scale.
    // The pixel fields are per-canvas (rebuilt inside the painter from each
    // canvas width); only the value bounds are shared here.
    val sharedAxis = BoxPlotGeom.axis(
        rows.flatMap { (_, _, clubShots) -> clubShots.flatMap { listOf(it.carryM, it.totalM) } },
        leftPadPx = 0f,
        plotWidthPx = 1f, // nominal — pixels are per-canvas; minM/maxM are width-independent
    )
    val mappedMedians = rows.mapNotNull { (name, _, clubShots) ->
        val kept = clubShots.filter { it.effectiveIncluded() }.map { it.carryM }
        val dist = BagMappingStats.distribution(kept) ?: return@mapNotNull null
        name to dist.median
    }
    val gaps = GapAnalysis.analyze(mappedMedians)
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
    ) {
        // Legend (shown once above the matrix): CARRY teal, TOTAL series blue.
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendSwatch(GolfColors.Teal, "CARRY")
                LegendSwatch(TotalBoxColor, "TOTAL")
            }
        }
        items(rows, key = { it.first }) { (name, typeLabel, clubShots) ->
            ClubRow(name, typeLabel, clubShots, sharedAxis, onDrillDown)
        }
        if (gaps.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    gaps.forEach { gap ->
                        Text(
                            text = "${gap.longerClub} → ${gap.shorterClub}: ${BagMappingFormats.carry(gap.gapM)} · ${gap.flag.name}",
                            style = GolfTypography.BodySmall,
                            color = when (gap.flag) {
                                GapFlag.TIGHT -> GolfColors.TextSecondary
                                GapFlag.HEALTHY -> GolfColors.Teal
                                GapFlag.WIDE -> GolfColors.Amber
                                GapFlag.INVERTED -> GolfColors.AlertRed
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClubRow(
    clubName: String,
    typeLabel: String,
    clubShots: List<BagMappingShotEntity>,
    sharedAxis: BoxPlotGeom.Axis?,
    onDrillDown: (String) -> Unit,
) {
    val kept = clubShots.filter { it.effectiveIncluded() }.map { it.carryM }
    val keptTotal = clubShots.filter { it.effectiveIncluded() }.map { it.totalM }
    val filtered = clubShots.filter { !it.effectiveIncluded() }.map { it.carryM }
    val dist = BagMappingStats.distribution(kept)
    val totalDist = BagMappingStats.distribution(keptTotal)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
            .clickable { onDrillDown(clubName) }
            .padding(GolfSpacing.Sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(72.dp)) {
                Text(clubName, style = GolfTypography.MetricValue, color = GolfColors.TextPrimary)
                Text(typeLabel, style = GolfTypography.BodySmall, color = GolfColors.TextSecondary)
            }
            if (dist == null && totalDist == null) {
                Text("no data", style = GolfTypography.Body, color = GolfColors.TextSecondary)
            } else if (sharedAxis != null) {
                // A club with kept shots always contributes to the shared axis,
                // so it is non-null exactly when the canvas is drawn.
                CarryMatrixCanvas(
                    rows = listOf(CarryMatrixRow(clubName, kept, filtered, keptTotal)),
                    minM = sharedAxis.minM,
                    maxM = sharedAxis.maxM,
                    modifier = Modifier.weight(1f).height(44.dp),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "C ${BagMappingFormats.carry(dist?.median ?: 0.0)} · T ${BagMappingFormats.total(totalDist?.median ?: 0.0)}",
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.TextPrimary,
                    )
                    Text(
                        "σ ${sigmaText(dist)} / ${sigmaText(totalDist)} · ${dist?.count ?: 0} kept" +
                            if (filtered.isNotEmpty()) " (+${filtered.size} excluded)" else "",
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/** One-line σ label ("σ 4.1 m", "—" without data). */
private fun sigmaText(dist: BagMappingStats.Distribution?): String =
    if (dist == null) "—" else "σ ${BagMappingFormats.carry(dist.sigma)}"

/** Legend entry: colour square + label (CARRY / TOTAL above the matrix). */
@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = GolfTypography.MetricLabel, color = GolfColors.TextSecondary)
    }
}

// --- Drill-down table (2026-10-16 rework) --------------------------------
//
// Ten right-aligned metric cells + the keep/exclude tick at the FAR RIGHT.
// A single header row carries the labels (units live there, not per row);
// a muted MEDIAN row shows the club baseline the auto gates compare against
// (medians over ALL shots — kept and filtered alike — matching
// [BagMappingStats]). A gate-failed shot has its FAILING metric's cell
// rendered in yellow — the reason string is for the gates, not the UI.

/** Column weights (BALL SPD … TOTAL) — shared by header, median and shot rows. */
private val CELL_WEIGHTS = listOf(1.0f, 1.0f, 0.9f, 0.95f, 0.85f, 1.1f, 0.85f, 1.0f, 1.0f)

/** Header labels; units are carried here so per-row text stays numeric. */
private val HEADER_LABELS = listOf(
    "BALL SPD mph", "CHS mph", "SMASH", "LAUNCH°", "DIR°", "SPIN", "AXIS°", "CARRY m", "TOTAL m",
)

/** Yellow for a failing metric cell (comparison-yellow; Amber stays reserved). */
private val FailYellow = GolfColors.Comparison.C

/** Tabular-figure cell style shared by header, median and shot rows. */
private val CellStyle = GolfTypography.MetricLabel.copy(fontFeatureSettings = "tnum")

/** Bold, slightly larger variant for the MEDIAN row (label + values). */
private val MedianCellStyle = CellStyle.copy(
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
)

/**
 * Fixed leading row-label column, shared by header, MEDIAN and shot rows so
 * the metric columns stay aligned. 68 dp fits the bold 13 sp "Median" label
 * plus breathing room, while leaving the wide tablet width to the 9 weighted
 * metric cells.
 */
private val LabelWidth = 68.dp

/** Leading label cell (null = blank spacer for header/shot rows). */
@Composable
private fun RowScope.RowLabel(text: String?, color: Color = GolfColors.TextMuted) {
    if (text == null) {
        Spacer(Modifier.width(LabelWidth))
    } else {
        Text(
            text,
            style = MedianCellStyle,
            color = color,
            modifier = Modifier.width(LabelWidth),
        )
    }
}

private fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)

private fun fmt0(v: Double): String = String.format(Locale.US, "%.0f", v)

/** One right-aligned table cell occupying its weight share of the row. */
@Composable
private fun RowScope.MetricCell(
    text: String,
    weight: Float,
    color: Color,
    style: TextStyle = CellStyle,
) {
    Text(
        text,
        style = style,
        color = color,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(weight).fillMaxWidth(),
    )
}

/** The 9 numeric cell values for one shot (same order as [HEADER_LABELS]). */
private fun shotCells(s: BagMappingShotEntity): List<String> = listOf(
    BagMappingFormats.mph(s.ballSpeedMps),
    BagMappingFormats.mph(s.clubHeadSpeedMps),
    BagMappingFormats.smash(s.clubHeadSpeedMps, s.ballSpeedMps),
    fmt1(s.launchAngleDeg),
    fmt1(s.launchDirDeg),
    s.totalSpinRpm.toString(),
    fmt1(s.spinAxisDeg),
    fmt0(s.carryM),
    fmt0(s.totalM),
)

/** Indices (into the 9 columns) of the metrics that tripped the auto gate. */
private fun failingCells(
    shot: BagMappingShotEntity,
    medianDirDeg: Double?,
    medianAxisDeg: Double?,
): Set<Int> {
    if (!shot.filtered) return emptySet()
    return when (shot.filterReason) {
        BagMappingStats.REASON_DUFF_LOW_BALL_SPEED -> setOf(0)
        BagMappingStats.REASON_MISREAD_CHS -> setOf(1)
        BagMappingStats.REASON_MISREAD_SMASH -> setOf(2)
        BagMappingStats.REASON_MISREAD_LAUNCH_ANGLE -> setOf(3)
        BagMappingStats.REASON_MISREAD_SPIN -> setOf(5)
        BagMappingStats.REASON_MISREAD_DIRECTION -> buildSet {
            // The persisted reason doesn't say which half breached — re-derive
            // it from the same thresholds the gate used; both when unclear.
            if (medianDirDeg != null &&
                abs(shot.launchDirDeg - medianDirDeg) > BagMappingStats.DIRECTION_LAUNCH_DEG
            ) {
                add(4)
            }
            if (medianAxisDeg != null &&
                abs(shot.spinAxisDeg - medianAxisDeg) > BagMappingStats.DIRECTION_SPIN_AXIS_DEG
            ) {
                add(6)
            }
            if (isEmpty()) addAll(setOf(4, 6))
        }
        else -> emptySet()
    }
}

@Composable
private fun DrillDownPanel(
    clubName: String,
    shots: List<BagMappingShotEntity>,
    onSetIncludeOverride: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val kept = shots.count { it.effectiveIncluded() }
    val excluded = shots.size - kept
    // Baseline medians over ALL shots — the exact baseline the auto gates
    // judge against. Used ONLY for the direction/axis yellow-cell
    // disambiguation; the displayed MEDIAN row tracks the included set.
    val medianDirDeg = BagMappingStats.median(shots.map { it.launchDirDeg })
    val medianAxisDeg = BagMappingStats.median(shots.map { it.spinAxisDeg })
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GolfColors.Base)
            .padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("$clubName — ALL SHOTS", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
            TextButton(onClick = onDismiss) { Text("BACK") }
        }
        Text(
            "$kept kept · $excluded excluded — tap ✓/✕ to override the gate",
            style = GolfTypography.BodySmall,
            color = GolfColors.TextSecondary,
        )
        // Single header row (units included) + divider.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RowLabel(null)
            HEADER_LABELS.forEachIndexed { i, label -> MetricCell(label, CELL_WEIGHTS[i], GolfColors.TextMuted) }
            Spacer(Modifier.width(4.dp))
            Spacer(Modifier.size(22.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(GolfColors.Line))
        // Muted, bold MEDIAN row — the gates' baseline, not a shot; no tick.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(GolfColors.Card, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
        ) {
            RowLabel("Median", color = GolfColors.TextSecondary)
            // Displayed baseline over the INCLUDED shots only — it tracks the
            // user's ✓/✕ overrides live. (The gate thresholds for the yellow
            // failing-cell disambiguation above stay on the ALL-shots median.)
            val includedShots = shots.filter { it.effectiveIncluded() }
            val smashMedian = BagMappingStats.median(
                includedShots.filter { it.clubHeadSpeedMps > 0.0 }.map { it.ballSpeedMps / it.clubHeadSpeedMps },
            )
            val medians = listOf(
                BagMappingStats.median(includedShots.map { it.ballSpeedMps }),
                BagMappingStats.median(includedShots.map { it.clubHeadSpeedMps }),
                smashMedian,
                BagMappingStats.median(includedShots.map { it.launchAngleDeg }),
                BagMappingStats.median(includedShots.map { it.launchDirDeg }),
                BagMappingStats.median(includedShots.map { it.totalSpinRpm.toDouble() }),
                BagMappingStats.median(includedShots.map { it.spinAxisDeg }),
                BagMappingStats.median(includedShots.map { it.carryM }),
                BagMappingStats.median(includedShots.map { it.totalM }),
            )
            medians.forEachIndexed { i, v ->
                val text = when {
                    v == null -> "—"
                    i == 0 || i == 1 -> BagMappingFormats.mph(v) // ball / club speed → mph
                    i == 5 -> fmt0(v) // spin: whole rpm
                    else -> fmt1(v)
                }
                MetricCell(text, CELL_WEIGHTS[i], GolfColors.TextSecondary, style = MedianCellStyle)
            }
            Spacer(Modifier.width(4.dp))
            Spacer(Modifier.size(22.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
            items(shots, key = { it.id }) { shot ->
                ShotRow(shot, medianDirDeg, medianAxisDeg, onSetIncludeOverride)
            }
        }
    }
}

/** One shot row: 9 metric cells + keep/exclude tick (22 dp) at the far right. */
@Composable
private fun ShotRow(
    shot: BagMappingShotEntity,
    medianDirDeg: Double?,
    medianAxisDeg: Double?,
    onSetIncludeOverride: (Long, Boolean) -> Unit,
) {
    // Effective state drives the tick (fitting-results pattern): a gate-failed
    // shot arrives ticked-out (✕) but can be ticked back in; a passing shot
    // can be ticked out. A gate-failed shot's failing metric reads yellow.
    val included = shot.effectiveIncluded()
    val failing = failingCells(shot, medianDirDeg, medianAxisDeg)
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (included) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cells = shotCells(shot)
        RowLabel(null)
        cells.forEachIndexed { i, text ->
            MetricCell(text, CELL_WEIGHTS[i], if (i in failing) FailYellow else GolfColors.TextPrimary)
        }
        Spacer(Modifier.width(4.dp))
        ShotTick(
            included = included,
            onToggle = { onSetIncludeOverride(shot.id, !included) },
        )
    }
}

/** Keep/exclude tick control (fitting-results pattern): kept = Line border + teal ✓, excluded = Amber border + amber ✕. */
@Composable
private fun ShotTick(included: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(GolfSpacing.Sm)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(shape)
            .border(1.dp, if (included) GolfColors.Line else GolfColors.Amber, shape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (included) "✓" else "✕",
            style = GolfTypography.BodySmall,
            color = if (included) GolfColors.Teal else GolfColors.Amber,
        )
    }
}
