package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.range.RangeScene
import java.util.Locale
import kotlin.math.abs

/**
 * RESULT state (spec §6, 2026-10-16 rework): a chart-led page. The top half is
 * the SIDE / TOP-DOWN selector over a CARRY / TOTAL basis toggle — the basis
 * drives the arcs, the axis, the summary strip and the gap basis. Below the
 * chart: the summary strip, a collapsed-by-default DISTRIBUTION & GAPS panel
 * (scrollable; the box-plot matrix and the DISTANCE GAPS block live in there)
 * and the RETEST + HISTORY row. All gaps come from ONE computed list shared by
 * the strip, the panel block and the charts, so they can never disagree.
 * Per-club drill-down opens over the page (all stored shots, kept vs filtered);
 * its per-shot tick box layers a user include/exclude override on the auto
 * gate verdict — the effective state, [BagMappingShotEntity.effectiveIncluded],
 * drives the aggregates, gaps and kept-vs-filtered counts everywhere.
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
    var view by remember { mutableStateOf(BagResultsView.SIDE) }
    var basis by remember { mutableStateOf(DistanceBasis.CARRY) }
    var panelOpen by remember { mutableStateOf(false) }

    // One gaps source for the whole page: the summary strip, the charts and
    // the panel's DISTANCE GAPS block all read this list, so they can never
    // disagree. The gap basis follows the toggle (user decision 2026-10-16);
    // the box-plot lanes inside the panel stay carry + total.
    // Hoisted once per session — everything below (colours, traces, dots,
    // gaps, the matrix section) reads this, so they all key on it instead of
    // re-parsing the snapshot per remember block.
    val rows = remember(session) { session.clubSnapshot() }
    val colorByClub = remember(rows) {
        rows.mapIndexed { index, (name, _) -> name to BagRamp.clubColor(index) }.toMap()
    }
    val traces = remember(rows, shots, basis) {
        rows.mapNotNull { (name, _) ->
            BagTrace.trace(name, shots.filter { it.clubName == name }, basis)
        }
    }
    val dots = remember(rows, shots, basis) {
        rows.map { (name, _) -> BagTrace.dots(name, shots.filter { it.clubName == name }, basis) }
    }
    // Gap flags are computed over medians in canonical long→short order
    // DERIVED FROM CLUB IDENTITY, NOT the persisted test order — feeding the
    // test order made every gap negative and all INVERTED (2026-10-16).
    val gaps = remember(rows, shots, basis) {
        GapAnalysis.analyzeByClubIdentity(
            rows.mapNotNull { (name, type) ->
                val kept = shots.filter { it.clubName == name && it.effectiveIncluded() }
                    .map { it.distanceFor(basis) }
                val dist = BagMappingStats.distribution(kept) ?: return@mapNotNull null
                Triple(name, type, dist.median)
            },
        )
    }
    val summary = remember(gaps) { summarizeGaps(gaps) }
    val density = LocalDensity.current
    val maxAbsSideM = remember(dots) {
        dots.flatMap { it.points }.maxOfOrNull { abs(it.first) } ?: 0.0
    }
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl),
    ) {
        // TOP-DOWN true-scale containment floor (2026-10-16 round 3): at 1:1
        // the chart pane must be at least
        //     (maxAbsSide + margin) * 2 * plotWidth / FAIRWAY_END_Y
        // tall for the vertical reach to cover the widest dot at the full
        // range — a fixed chart/panel weight ratio cannot guarantee that on
        // every session. With the panel open, the chart gets exactly this
        // height and the panel takes the remainder (harmless: the panel
        // scrolls under its pinned scale). The plot canvas spans the content
        // width, and the pads mirror BagTopDownView's 26/12 dp.
        val minChartDp = if (view == BagResultsView.TOP_DOWN && panelOpen) {
            with(density) {
                val plotWidthPx = constraints.maxWidth - (26.dp + 12.dp).toPx()
                val minPanePx = ((maxAbsSideM + TOP_DOWN_LATERAL_MARGIN_M) * 2.0 *
                    plotWidthPx / RangeScene.FAIRWAY_END_Y).toFloat()
                // Guard: an absurd lateral spread must never swallow the
                // page — such a session falls through to the geom's squash
                // fallback instead.
                minPanePx.coerceAtMost(constraints.maxHeight * 0.5f).coerceAtLeast(0f).toDp()
            }
        } else null
        Column(
            modifier = Modifier.fillMaxSize(),
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
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            SegmentedChoice("SIDE", view == BagResultsView.SIDE) { view = BagResultsView.SIDE }
            SegmentedChoice("TOP-DOWN", view == BagResultsView.TOP_DOWN) { view = BagResultsView.TOP_DOWN }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            SegmentedChoice("CARRY", basis == DistanceBasis.CARRY) { basis = DistanceBasis.CARRY }
            SegmentedChoice("TOTAL", basis == DistanceBasis.TOTAL) { basis = DistanceBasis.TOTAL }
        }
        BagResultsSummary(summary, basis)
        when (view) {
            BagResultsView.SIDE -> BagSideView(
                traces = traces,
                colors = colorByClub,
                onClubTap = { drillClub = it },
                modifier = Modifier.weight(1f),
            )
            BagResultsView.TOP_DOWN -> BagTopDownView(
                clubs = dots,
                colors = colorByClub,
                onClubTap = { drillClub = it },
                // With the panel open the chart gets its data-derived
                // containment floor and the panel the remainder; otherwise
                // the pane is tall enough for the fixed weight.
                modifier = minChartDp?.let { Modifier.height(it) } ?: Modifier.weight(1f),
            )
        }
        BagDistPanel(
            open = panelOpen,
            onToggle = { panelOpen = !panelOpen },
            // Split rule (2026-10-16 rounds 2-3): SIDE keeps the 1.5x panel
            // share (its chart has no containment floor to honour); with
            // TOP-DOWN open the chart's height is fixed above and the panel
            // simply takes the remainder.
            modifier = when {
                !panelOpen -> Modifier
                view == BagResultsView.TOP_DOWN -> Modifier.weight(1f)
                else -> Modifier.weight(1.5f)
            },
        ) {
            CarryMatrixSection(
                rows = rows,
                shots = shots,
                basis = basis,
                gaps = gaps,
                onDrillDown = { drillClub = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md)) {
            if (!isStaleResult) {
                Button(onClick = onRetest, colors = ButtonDefaults.buttonColors(containerColor = GolfColors.Teal)) {
                    Text("RETEST")
                }
            }
            OutlinedButton(onClick = onOpenHistory) { Text("HISTORY") }
        }
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

// --- Carry/total box-plot matrix -----------------------------------------

/** Leading club-name column width — shared by rows and the sticky scale header. */
private val ClubLabelWidth = 72.dp

/**
 * Horizontal inset inside each bordered row (and mirrored by the sticky scale
 * header) so the scale ticks line up exactly above the box-plot lanes.
 */
private val RowInset = GolfSpacing.Sm

/**
 * Trailing stats-column width — shared by rows and the sticky scale header so
 * the metre scale ticks line up exactly above the box-plot lanes. Sized for
 * the 12 sp range line (2026-10-16: bumped from 10 sp after tablet review —
 * the widest observed line "C 236–239 · T 249–254 m" measured ~132 dp at
 * 10 sp; a 4-digit case "C 1000–1024 · T 1005–1031 m" needs ~172 dp at 12 sp,
 * so 184 dp leaves margin) plus the bold 17 sp C/T values.
 */
private val StatsColumnWidth = 184.dp

/**
 * The box-plot matrix (inside the collapsed-by-default DISTRIBUTION & GAPS
 * panel). Medians and gap flags are computed ONCE in [BagMappingResult] and
 * passed in as [gaps] — the section must not recompute them, or the strip and
 * this block could disagree. The gap basis follows the page toggle ([basis])
 * while the box-plot lanes stay carry + total.
 */
@Composable
private fun CarryMatrixSection(
    rows: List<Pair<String, ClubType>>,
    shots: List<BagMappingShotEntity>,
    basis: DistanceBasis,
    gaps: List<GapEntry>,
    onDrillDown: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Club order comes from the hoisted session snapshot (spec §4) — passed
    // down from [BagMappingResult] so this never re-parses (or diverges from)
    // the chart above; clubs without shots render as "no data" rows (spec §9).
    // The ClubType is kept (not just the label) — the gap analysis needs it
    // for canonical ordering.
    val clubRows = rows.map { (name, type) ->
        val clubShots = shots.filter { it.clubName == name }
        Triple(name, type, clubShots)
    }
    // ONE shared value axis over every club's kept+filtered carries AND kept
    // totals (spec §6 + item 4): the matrix's whole point is cross-club
    // comparison, so carry and total boxes render on the same metre scale.
    // The pixel fields are per-canvas (rebuilt inside the painter from each
    // canvas width); only the value bounds are shared here.
    val sharedAxis = BoxPlotGeom.axis(
        clubRows.flatMap { (_, _, clubShots) -> clubShots.flatMap { listOf(it.carryM, it.totalM) } },
        leftPadPx = 0f,
        plotWidthPx = 1f, // nominal — pixels are per-canvas; minM/maxM are width-independent
    )
    // Scroll affordance (2026-10-16 review): the open panel holds far more
    // content than fits (13 rows + the gap block), and a bare LazyColumn gave
    // no cue that the cut-off rows below are reachable. A fade + "scroll for
    // more" caption appears exactly while `canScrollForward` — it never lies.
    val listState = rememberLazyListState()
    // The metre scale is PINNED ABOVE the list rather than a stickyHeader
    // (2026-10-16 round 2, F1): a sticky overlay strikes through whatever
    // scrolls beneath it, and on this tablet the gap block is taller than
    // the viewport below the sticky even with a trailing spacer — the
    // heading could never fully clear it. Pinned outside the list, the scale
    // stays visible for the club rows and can never obscure a row or the
    // gap block, at any scroll position.
    Column(modifier.fillMaxWidth()) {
        if (sharedAxis != null) {
            Column(modifier = Modifier.fillMaxWidth().background(GolfColors.Base)) {
                Row(
                    modifier = Modifier.padding(horizontal = RowInset),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Spacer(Modifier.width(ClubLabelWidth))
                    CarryMatrixScaleCanvas(
                        minM = sharedAxis.minM,
                        maxM = sharedAxis.maxM,
                        modifier = Modifier.weight(1f).height(ScaleHeight),
                    )
                    Spacer(Modifier.width(StatsColumnWidth))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(GolfColors.Line))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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
        items(clubRows, key = { it.first }) { (name, type, clubShots) ->
            ClubRow(name, type.label, clubShots, sharedAxis, onDrillDown)
        }
        if (gaps.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "DISTANCE GAPS · ${basis.name}",
                        style = GolfTypography.MetricLabel, color = GolfColors.TextMuted,
                    )
                    Text(
                        // Band is per-pair now, so the hint states the rule.
                        // ~93 chars at BodySmall ≈ 670 dp on the ~1015 dp
                        // content width — fits on one line, so the driver
                        // note is included.
                        "Gap between each club and the next longer club — target " +
                            String.format(Locale.US, "%.0f", GapAnalysis.BAND_LOW_FRACTION * 100) + "–" +
                            String.format(Locale.US, "%.0f", GapAnalysis.BAND_HIGH_FRACTION * 100) +
                            "% of that club's distance (driver excluded).",
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextSecondary,
                    )
                    // analyze returns canonical long→short order; card order
                    // above is shortest first, so reverse and name the shorter
                    // club first ("LW – SW  14 m · good").
                    gaps.asReversed().forEach { gap ->
                        Text(
                            text = gapLine(gap),
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
        if (listState.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(ScrollCueHeight)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to GolfColors.Base,
                        ),
                    ),
            )
            Text(
                "scroll for more",
                style = GolfTypography.Status,
                color = GolfColors.TextMuted,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = GolfSpacing.Xs),
            )
        }
        }
    }
}

/** Height of the bottom fade behind the "scroll for more" cue. */
private val ScrollCueHeight = 32.dp

/** Range line at 12 sp (2026-10-16 bump from 10 sp — too small on tablet). */
private val RangeTextStyle = GolfTypography.Status.copy(fontSize = 12.sp)

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
            .padding(RowInset),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(ClubLabelWidth)) {
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
                Column(
                    modifier = Modifier.width(StatsColumnWidth),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Big carry/total values — the dominant content; kept/-
                    // excluded count and the min–max range stay small below.
                    MetricValueLine("C", dist, BagMappingFormats::carry, GolfColors.Teal)
                    MetricValueLine("T", totalDist, BagMappingFormats::total, TotalBoxColor)
                    Text(
                        rangeText(dist, totalDist),
                        style = RangeTextStyle,
                        color = GolfColors.TextSecondary,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        keptText(dist, filtered),
                        style = GolfTypography.Status,
                        color = GolfColors.TextMuted,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/**
 * One big metric line: small muted letter label + bold number ("C 172.1").
 * The number is coloured to match the chart lane (carry teal / total blue —
 * same colours as the legend swatches, 2026-10-16); the letter label stays
 * muted so the bold coloured value dominates.
 */
@Composable
private fun MetricValueLine(
    label: String,
    dist: BagMappingStats.Distribution?,
    format: (Double) -> String,
    valueColor: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs), verticalAlignment = Alignment.Bottom) {
        Text(label, style = GolfTypography.MetricLabel, color = GolfColors.TextSecondary)
        Text(
            if (dist == null) "—" else format(dist.median),
            style = GolfTypography.MetricValue,
            color = valueColor,
        )
    }
}

/** Min–max range for both carry and total ("C 168–184 · T 178–196 m"). */
private fun rangeText(
    dist: BagMappingStats.Distribution?,
    totalDist: BagMappingStats.Distribution?,
): String {
    val carryRange = rangeSpan(dist)
    val totalRange = rangeSpan(totalDist)
    if (carryRange == null && totalRange == null) return "no range"
    val parts = buildList {
        if (carryRange != null) add("C $carryRange")
        if (totalRange != null) add("T $totalRange")
    }
    return parts.joinToString(" · ") + " m"
}

/**
 * Unit-less "168–184" (single shot collapses to "168"); null without data.
 * Uses unit-less [fmt0] — the single trailing " m" comes from [rangeText],
 * never per bound (BagMappingFormats.carry would double-print the unit).
 */
private fun rangeSpan(dist: BagMappingStats.Distribution?): String? {
    dist ?: return null
    val lo = fmt0(dist.min)
    val hi = fmt0(dist.max)
    return if (lo == hi) lo else "$lo–$hi"
}

/**
 * Verdict-aware rounding (2026-10-16 round 6): whole metres unless rounding
 * would change the flag verdict, then one decimal. Plain 0 dp produced
 * self-contradictions ("8 m · too tight" against an 8 m floor); the round-5
 * near-integer rule put a decimal on almost every line because medians
 * rarely land on integers. This gives 13.8 → "14 m", 21.7 → "22 m", and a
 * decimal only where the band boundary genuinely falls inside the rounded
 * value. The verdict is recomputed from the entry's own band — the band
 * fully determines the flag once the gap is known (monotonic in gap).
 */
private fun flagFromBand(gapM: Double, bandLowM: Double, bandHighM: Double): GapFlag = when {
    gapM <= 0.0 -> GapFlag.INVERTED
    gapM < bandLowM -> GapFlag.TIGHT
    gapM > bandHighM -> GapFlag.WIDE
    else -> GapFlag.HEALTHY
}

private fun gapMetres(entry: GapEntry): String {
    val rounded = kotlin.math.round(entry.gapM)
    val verdictSurvives =
        flagFromBand(rounded, entry.bandLowM, entry.bandHighM) == entry.flag
    return if (verdictSurvives) String.format(Locale.US, "%.0f m", entry.gapM)
    else String.format(Locale.US, "%.1f m", entry.gapM)
}

/** Band bounds in whole metres for the "(aim 4–9 m)" suffix. */
private fun bandMetres(m: Double): String = String.format(Locale.US, "%.0f", m)

/**
 * Plain-words gap line, shortest club first (card order): "LW – SW  14 m ·
 * good". Flagged lines print their own band ("big gap (aim 4–9 m)"); good
 * lines stay terse. INVERTED names the anomaly: the shorter club carrying
 * as far as — or further than — the longer one (mislabeled club or a real
 * problem); the band doesn't apply there, so it stays off.
 */
internal fun gapLine(gap: GapEntry): String {
    val flagWord = when (gap.flag) {
        GapFlag.TIGHT -> "too tight"
        GapFlag.HEALTHY -> "good"
        GapFlag.WIDE -> "big gap"
        GapFlag.INVERTED ->
            if (gap.gapM < 0.5) {
                "inverted — ${gap.shorterClub} carries as far as ${gap.longerClub}"
            } else {
                "inverted — ${gap.shorterClub} carries further than ${gap.longerClub}"
            }
    }
    val bandNote = when (gap.flag) {
        GapFlag.TIGHT, GapFlag.WIDE -> " (aim ${bandMetres(gap.bandLowM)}–${bandMetres(gap.bandHighM)} m)"
        else -> ""
    }
    return "${gap.shorterClub} – ${gap.longerClub}  ${gapMetres(gap)} · $flagWord$bandNote"
}

/** Kept/excluded count — useful, not a statistical symbol. */
private fun keptText(dist: BagMappingStats.Distribution?, filtered: List<Double>): String =
    "${dist?.count ?: 0} kept" + if (filtered.isNotEmpty()) " (+${filtered.size} excluded)" else ""

/** Height of the sticky metre-scale header: rule zone + 10 sp text + gap. */
private val ScaleHeight = 30.dp

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

/**
 * Small chip-style segmented choice (the range VIEW chip treatment): teal
 * border + teal text when active, line border + muted text otherwise.
 * Semantics: `selectable` with [Role.Tab] so a screen reader announces which
 * option is active; the visual treatment is unchanged.
 */
@Composable
private fun SegmentedChoice(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (active) GolfColors.Teal else GolfColors.Line,
                shape = RoundedCornerShape(GolfSpacing.Sm),
            )
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs),
    ) {
        Text(
            text = label,
            style = GolfTypography.MetricLabel,
            color = if (active) GolfColors.Teal else GolfColors.TextSecondary,
        )
    }
}
