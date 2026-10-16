package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The headline gap statistics (spec §8), built from the same [GapEntry] list the
 * panel's gap block renders. Follows the current basis — carry in CARRY mode,
 * total in TOTAL mode, named on the first item — and the driver is already
 * excluded upstream. [GapSummary]'s aggregates cover real (non-inverted) gaps
 * only; render `closest`/`farthest` as given, do not re-derive them.
 */
@Composable
fun BagResultsSummary(summary: GapSummary, basis: DistanceBasis, modifier: Modifier = Modifier) {
    if (summary.averageGapM == null) {
        Text(
            text = "No gaps to summarise — map at least two clubs.",
            style = GolfTypography.BodySmall,
            color = GolfColors.TextSecondary,
            modifier = modifier,
        )
        return
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
    ) {
        SummaryItem(
            "AVG GAP · ${basis.name}",
            String.format(Locale.US, "%.1f m", summary.averageGapM),
            GolfColors.TextPrimary,
        )
        summary.closest?.let {
            SummaryItem(
                "CLOSEST",
                "${it.gapM.roundToInt()} m (${it.shorterClub}–${it.longerClub})",
                GolfColors.TextPrimary,
            )
        }
        summary.farthest?.let {
            SummaryItem(
                "FARTHEST",
                "${it.gapM.roundToInt()} m (${it.shorterClub}–${it.longerClub})",
                GolfColors.TextPrimary,
            )
        }
        SummaryItem(
            "VERDICTS",
            "${summary.goodCount} good · ${summary.wideCount} big · ${summary.tightCount} tight",
            GolfColors.TextPrimary,
        )
    }
}

@Composable
private fun SummaryItem(label: String, value: String, valueColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
        Text(label, style = GolfTypography.MetricLabel, color = GolfColors.TextMuted)
        Text(value, style = GolfTypography.BodySmall, color = valueColor)
    }
}
