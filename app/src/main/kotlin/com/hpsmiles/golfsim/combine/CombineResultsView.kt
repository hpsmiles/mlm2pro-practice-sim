package com.hpsmiles.golfsim.combine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.core.scoring.SkillBand
import com.hpsmiles.golfsim.games.ConfettiBurst
import java.util.Locale

private val NegativeColor = Color(0xFFE4565A) // tune to palette if a token exists
private val HeroNumber = GolfTypography.Hero.copy(fontSize = 48.sp)
private val TargetNumber = GolfTypography.Hero.copy(fontSize = 24.sp)

/**
 * M8 post-ship spec §2: results redesigned into one vertical column — a centred
 * hero overall handicap, a compact per-target row, the VS BANDS strip, an
 * optional record banner, and a collapsible SHOTS ledger (collapsed by default)
 * whose per-shot SG column is scored against the session's [CombineResults.lockedBand].
 * The read-only history drill reuses this view unchanged (no PLAY AGAIN, no
 * record card, no confetti).
 */
@Composable
internal fun CombineResultsView(
    entry: CombineCatalogEntry,
    facts: List<CombineShotFacts>,
    record: CombineRecordComparison?,
    readOnly: Boolean,
    onPlayAgain: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (facts.isEmpty()) return
    val results = remember(entry, facts) { combineResults(entry, facts) }
    val sortedFacts = remember(facts) { facts.sortedBy { it.shotIndex } }
    var shotsExpanded by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(CombineViewMode.SUMMARY) }

    Box(modifier.background(GolfColors.Base)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(GolfSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
        ) {
            Text(entry.name, style = GolfTypography.ScreenTitle, color = GolfColors.Teal)

            ViewToggle(selected = viewMode, onSelect = { viewMode = it })

            if (viewMode == CombineViewMode.TOP_DOWN) {
                CombineTopDownView(
                    entry = entry,
                    facts = facts,
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )
            } else {
                // Hero: large overall handicap, ± uncertainty smaller beneath.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                ) {
                    Text(
                        "≈ ${CombineFormats.formatHc(results.overall.est)}",
                        style = HeroNumber,
                        color = GolfColors.TextPrimary,
                    )
                    val low = results.overall.low
                    val high = results.overall.high
                    if (low != null && high != null) {
                        Text(
                            "± ${CombineFormats.zeroDp((high - low) / 2.0)}",
                            style = GolfTypography.MetricValue,
                            color = GolfColors.TextSecondary,
                        )
                    }
                    Text("OVERALL HANDICAP", style = GolfTypography.MetricLabel, color = GolfColors.TextMuted)
                }

                // Per-target row: one compact item per catalog station.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                    for ((stationIndex, station) in entry.stations.withIndex()) {
                        val stationResult = results.stations.firstOrNull { it.stationIndex == stationIndex }
                        Column(
                            modifier = Modifier.weight(1f)
                                .background(GolfColors.Card, RoundedCornerShape(GolfSpacing.Sm))
                                .padding(GolfSpacing.Md),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                station.label.uppercase(),
                                style = GolfTypography.MetricLabel,
                                color = GolfColors.TextSecondary,
                            )
                            Text(
                                stationResult?.let { "≈ ${CombineFormats.formatHc(it.estimate.est)}" } ?: "—",
                                style = TargetNumber,
                                color = GolfColors.Teal,
                            )
                            if (stationResult != null) {
                                Text(
                                    "${stationResult.avgLabel} ${CombineFormats.oneDp(stationResult.avgValue)} m",
                                    style = GolfTypography.Status,
                                    color = GolfColors.TextMuted,
                                )
                            }
                        }
                    }
                }

                // VS BANDS strip: the four diverging bars, overall per-band SG.
                SectionCard("VS BANDS") {
                    Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                        Text(
                            "AVG STROKES GAINED PER SHOT VS EACH BENCHMARK · TEAL = THIS SESSION'S",
                            style = GolfTypography.Status,
                            color = GolfColors.TextMuted,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                            for (band in SkillBand.entries) {
                                val sg = results.overall.perBandSg.getValue(band)
                                val locked = band == results.lockedBand
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                                ) {
                                    Text(
                                        bandLabel(band),
                                        style = GolfTypography.MetricLabel,
                                        color = if (locked) GolfColors.Teal else GolfColors.TextSecondary,
                                    )
                                    Text(
                                        String.format(Locale.US, "%+.2f", sg),
                                        style = GolfTypography.BodySmall,
                                        color = if (sg < 0) NegativeColor else GolfColors.Teal,
                                    )
                                    SgBar(sg)
                                }
                            }
                        }
                    }
                }

                if (!readOnly && record != null) {
                    SectionCard("RECORD") {
                        Column {
                            Text(CombineRecord.headline(record), style = GolfTypography.MetricValue, color = GolfColors.Teal)
                            Text(CombineRecord.detail(record), style = GolfTypography.BodySmall, color = GolfColors.TextSecondary)
                        }
                    }
                }

                // SHOTS: collapsible ledger, collapsed by default.
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(GolfColors.Card, RoundedCornerShape(GolfSpacing.CornerCard)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { shotsExpanded = !shotsExpanded }
                            .padding(GolfSpacing.Lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "SHOTS · ${facts.size} shots · scored vs ${bandLabel(results.lockedBand)}",
                            style = GolfTypography.ScreenTitle,
                            color = GolfColors.TextPrimary,
                        )
                        Text(
                            if (shotsExpanded) "▾" else "▸",
                            style = GolfTypography.ScreenTitle,
                            color = GolfColors.Teal,
                        )
                    }
                    if (shotsExpanded) {
                        CombineLedger(entry = entry, facts = sortedFacts, lockedBand = results.lockedBand)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                if (!readOnly && onPlayAgain != null) {
                    Button(onClick = onPlayAgain) { Text("PLAY AGAIN") }
                }
                OutlinedButton(onClick = onBack) { Text("BACK") }
            }
        }
        if (!readOnly && record?.outcome == CombineRecordOutcome.NEW_RECORD) {
            ConfettiBurst(Modifier.fillMaxSize())
        }
    }
}

/** The benchmark-locked per-shot ledger (#, target, result, lie, SG + nearest-band chip). */
@Composable
private fun CombineLedger(
    entry: CombineCatalogEntry,
    facts: List<CombineShotFacts>,
    lockedBand: SkillBand,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = GolfSpacing.Lg)
            .padding(bottom = GolfSpacing.Lg),
    ) {
        LedgerRow(number = "#", target = "TARGET", result = "RESULT", lie = "LIE", sg = "SG", header = true)
        facts.forEachIndexed { index, fact ->
            val stationDef = entry.stations[CombinePlan.stationIndexFor(fact.shotIndex)]
            val resultText = if (stationDef.kind == StationKind.DRIVER) {
                "${CombineFormats.zeroDp(DRIVER_HOLE_M - fact.restDistanceM)} m"
            } else {
                "${CombineFormats.oneDp(fact.restDistanceM)} m to pin"
            }
            val sg = shotSgForBand(entry, fact, lockedBand)
            LedgerRow(
                number = (index + 1).toString(),
                target = stationDef.label,
                result = resultText,
                lie = fact.restLie.name,
                sg = String.format(Locale.US, "%+.2f", sg),
                sgColor = if (sg < 0) NegativeColor else GolfColors.Teal,
                chip = shotChip(entry, fact),
            )
        }
    }
}

@Composable
private fun LedgerRow(
    number: String,
    target: String,
    result: String,
    lie: String,
    sg: String,
    sgColor: Color = GolfColors.TextPrimary,
    chip: String = "",
    header: Boolean = false,
) {
    val style = if (header) GolfTypography.MetricLabel else GolfTypography.BodySmall
    val baseColor = if (header) GolfColors.TextMuted else GolfColors.TextSecondary
    Row(
        Modifier.fillMaxWidth().padding(vertical = GolfSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number, style = style, color = baseColor, modifier = Modifier.weight(0.4f))
        Text(target, style = style, color = baseColor, modifier = Modifier.weight(1.1f))
        Text(result, style = style, color = baseColor, modifier = Modifier.weight(1.6f))
        Text(lie, style = style, color = baseColor, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.weight(1.6f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sg, style = style, color = if (header) baseColor else sgColor)
            if (!header) {
                Text("  $chip", style = GolfTypography.Status, color = GolfColors.TextMuted)
            }
        }
    }
}

@Composable
private fun SgBar(sg: Double) {
    val fraction = (Math.abs(sg) / 0.5).coerceAtMost(1.0).toFloat()
    Row(Modifier.fillMaxWidth().height(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (sg < 0) {
                Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(NegativeColor))
            }
        }
        Box(Modifier.width(1.dp).height(12.dp).background(GolfColors.TextMuted))
        Box(Modifier.weight(1f)) {
            if (sg > 0) {
                Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(GolfColors.Teal))
            }
        }
    }
}

private fun bandLabel(band: SkillBand): String = when (band) {
    SkillBand.TOUR -> "TOUR"
    SkillBand.SCRATCH -> "SCRATCH"
    SkillBand.HCP10 -> "10 HC"
    SkillBand.HCP18 -> "18 HC"
}

/** Which body the results screen shows; state is hoisted in [CombineResultsView]. */
private enum class CombineViewMode { SUMMARY, TOP_DOWN }

/** SUMMARY / TOP-DOWN segmented toggle (M8 post-ship spec §3). */
@Composable
private fun ViewToggle(selected: CombineViewMode, onSelect: (CombineViewMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(GolfColors.Card, RoundedCornerShape(GolfSpacing.Sm))
            .padding(GolfSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
    ) {
        for (mode in CombineViewMode.entries) {
            val active = mode == selected
            Box(
                modifier = Modifier.weight(1f)
                    .background(if (active) GolfColors.Teal else Color.Transparent, RoundedCornerShape(GolfSpacing.Xs))
                    .clickable { onSelect(mode) }
                    .padding(vertical = GolfSpacing.Sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (mode == CombineViewMode.SUMMARY) "SUMMARY" else "TOP-DOWN",
                    style = GolfTypography.ScreenTitle,
                    color = if (active) GolfColors.Base else GolfColors.TextSecondary,
                )
            }
        }
    }
}
