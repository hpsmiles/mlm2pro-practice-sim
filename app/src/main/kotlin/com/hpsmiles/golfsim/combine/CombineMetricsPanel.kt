package com.hpsmiles.golfsim.combine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.range.DisplayShot
import com.hpsmiles.golfsim.range.LastShotChips

/** Right panel: LAST SHOT (shared chip strip verbatim) + TARGETS (per-station counts). */
@Composable
internal fun CombineMetricsPanel(
    lastShot: DisplayShot?,
    entry: CombineCatalogEntry,
    takenCount: Int,
    modifier: Modifier = Modifier,
) {
    val counts = CombinePlan.countsFor(entry, takenCount)
    val currentStation = CombinePlan.stationIndexFor(takenCount.coerceAtMost(CombinePlan.SHOTS_TOTAL - 1))
    Column(
        modifier = modifier
            .width(210.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .background(GolfColors.Panel)
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        SectionCard("LAST SHOT") {
            if (lastShot == null) {
                Text("Fire a shot", style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
            } else {
                LastShotChips(lastShot)
            }
        }
        SectionCard("TARGETS") {
            Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                entry.stations.forEachIndexed { index, station ->
                    val highlight = index == currentStation
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            station.label,
                            style = GolfTypography.BodySmall,
                            color = if (highlight) GolfColors.Teal else GolfColors.TextSecondary,
                        )
                        Text(
                            "${counts[index]}",
                            style = GolfTypography.BodySmall,
                            color = if (highlight) GolfColors.Teal else GolfColors.TextPrimary,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL", style = GolfTypography.BodySmall, color = GolfColors.TextSecondary)
                    Text(
                        "$takenCount/${CombinePlan.SHOTS_TOTAL}",
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextPrimary,
                    )
                }
            }
        }
    }
}
