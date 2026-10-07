package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RESULT state (spec §6): box-plot carry matrix in bag order, per-club stats
 * column, adjacent gap flags, RETEST + HISTORY, "test in progress" banner
 * while a retest runs, and per-club drill-down (all stored shots, kept vs
 * filtered). Read-only — no manual exclusion in v1.
 */
@Composable
fun BagMappingResult(
    session: BagMappingSessionEntity,
    shots: List<BagMappingShotEntity>,
    isStaleResult: Boolean,
    onRetest: () -> Unit,
    onResume: () -> Unit,
    onOpenHistory: () -> Unit,
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
        val kept = clubShots.filter { !it.filtered }.map { it.carryM }
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
    val kept = clubShots.filter { !it.filtered }.map { it.carryM }
    val keptTotal = clubShots.filter { !it.filtered }.map { it.totalM }
    val filtered = clubShots.filter { it.filtered }.map { it.carryM }
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
                            if (filtered.isNotEmpty()) " (+${filtered.size} filtered)" else "",
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

@Composable
private fun DrillDownPanel(
    clubName: String,
    shots: List<BagMappingShotEntity>,
    onDismiss: () -> Unit,
) {
    // One formatter for all rows (same "HH:mm:ss" output as before, but no
    // per-row allocation).
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
            items(shots, key = { it.id }) { shot ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        timeFormat.format(Date(shot.timestampMs)),
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextSecondary,
                    )
                    Text("C ${BagMappingFormats.carry(shot.carryM)}", style = GolfTypography.MetricLabel, color = GolfColors.TextPrimary)
                    Text("T ${BagMappingFormats.total(shot.totalM)}", style = GolfTypography.MetricLabel, color = GolfColors.TextPrimary)
                    Text(
                        "smash ${BagMappingFormats.smash(shot.clubHeadSpeedMps, shot.ballSpeedMps)}",
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextSecondary,
                    )
                    if (shot.filtered) {
                        Text(
                            "FILTERED (${shot.filterReason ?: "DUFF"})",
                            style = GolfTypography.BodySmall,
                            color = GolfColors.Amber,
                        )
                    } else {
                        Text("KEPT", style = GolfTypography.BodySmall, color = GolfColors.Teal)
                    }
                }
            }
        }
    }
}
