package com.hpsmiles.golfsim.combine

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.core.scoring.SkillBand
import com.hpsmiles.golfsim.games.ConfettiBurst
import java.util.Locale

private val NegativeBarColor = Color(0xFFE4565A) // tune to palette if a token exists

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

    Box(modifier.background(GolfColors.Base)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(GolfSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
        ) {
            Text(entry.name, style = GolfTypography.ScreenTitle, color = GolfColors.Teal)
            Text(CombineFormats.formatHcWithUncertainty(results.overall), style = GolfTypography.MetricValue, color = GolfColors.TextPrimary)
            Text("OVERALL HANDICAP", style = GolfTypography.MetricLabel, color = GolfColors.TextSecondary)

            SectionCard("VS BANDS") {
                Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                    for (band in SkillBand.entries) {
                        val sg = results.overall.perBandSg.getValue(band)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(bandLabel(band), style = GolfTypography.BodySmall, color = GolfColors.TextSecondary)
                            Text(String.format(Locale.US, "%+.2f", sg), style = GolfTypography.BodySmall, color = GolfColors.TextPrimary)
                        }
                    }
                }
            }

            for (station in results.stations) {
                SectionCard(station.label) {
                    Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                        Text(CombineFormats.formatHcWithUncertainty(station.estimate), style = GolfTypography.MetricValue, color = GolfColors.TextPrimary)
                        Text(
                            "${station.avgLabel} ${CombineFormats.oneDp(station.avgValue)} m",
                            style = GolfTypography.BodySmall,
                            color = GolfColors.TextSecondary,
                        )
                        for (band in SkillBand.entries) {
                            SgBar(station.estimate.perBandSg.getValue(band))
                        }
                    }
                }
            }

            SectionCard("SHOTS") {
                Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                    for (station in results.stations) {
                        val stationFacts = sortedFacts.filter {
                            CombinePlan.stationIndexFor(it.shotIndex) == station.stationIndex
                        }
                        stationFacts.forEachIndexed { i, fact ->
                            val stationDef = entry.stations[station.stationIndex]
                            val distanceText = if (stationDef.kind == StationKind.DRIVER) {
                                "${CombineFormats.zeroDp(DRIVER_HOLE_M - fact.restDistanceM)} m"
                            } else {
                                "${CombineFormats.oneDp(fact.restDistanceM)} m"
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "$distanceText · ${fact.restLie}",
                                    style = GolfTypography.BodySmall,
                                    color = GolfColors.TextSecondary,
                                )
                                Text(
                                    station.chips.getOrElse(i) { "" },
                                    style = GolfTypography.BodySmall,
                                    color = GolfColors.TextPrimary,
                                )
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

@Composable
private fun SgBar(sg: Double) {
    val fraction = (Math.abs(sg) / 0.5).coerceAtMost(1.0).toFloat()
    Row(Modifier.fillMaxWidth().height(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (sg < 0) {
                Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(NegativeBarColor))
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
    SkillBand.HCP10 -> "10"
    SkillBand.HCP18 -> "18"
}
