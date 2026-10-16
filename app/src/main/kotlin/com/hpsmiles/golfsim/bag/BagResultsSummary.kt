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
 * True when the strip must render its "no gaps" message: only when the gap
 * list is EMPTY. An all-INVERTED list still has gaps — [GapSummary]'s
 * aggregates are null there (inverted pairs never average or rank), but the
 * strip must show the counts plus the inverted note, agreeing with the
 * panel's gap block (2026-10-16 round 4, finding A).
 */
internal fun summaryHasNoGaps(gapCount: Int): Boolean = gapCount == 0

/**
 * True when gaps exist but every one of them is INVERTED: the aggregates are
 * null while the gap list is non-empty, so the strip shows `—` for AVG GAP /
 * CLOSEST / FARTHEST plus an explicit note instead of claiming there is
 * nothing to summarise.
 */
internal fun summaryIsAllInverted(summary: GapSummary, gapCount: Int): Boolean =
    gapCount > 0 &&
        summary.averageGapM == null &&
        summary.goodCount + summary.wideCount + summary.tightCount == 0

/**
 * The headline gap statistics (spec §8), built from the same [GapEntry] list
 * the panel's gap block renders — gated on the gap COUNT, so the strip and
 * the block can never disagree (an all-inverted session shows the strip with
 * `—` aggregates and an "all gaps inverted" note, exactly matching the red
 * lines below). Follows the current basis — carry in CARRY mode, total in
 * TOTAL mode, named on the first item — and the driver is already excluded
 * upstream. [GapSummary]'s aggregates cover real (non-inverted) gaps only;
 * render `closest`/`farthest` as given, do not re-derive them.
 *
 * Verdict counts are tinted to match the gap block (spec §8): good = teal,
 * big = amber, tight = grey — the same colours the DISTANCE GAPS lines use.
 */
@Composable
fun BagResultsSummary(
    summary: GapSummary,
    gapCount: Int,
    basis: DistanceBasis,
    modifier: Modifier = Modifier,
) {
    if (summaryHasNoGaps(gapCount)) {
        Text(
            text = "No gaps to summarise — map at least two clubs.",
            style = GolfTypography.BodySmall,
            color = GolfColors.TextSecondary,
            modifier = modifier,
        )
        return
    }
    val allInverted = summaryIsAllInverted(summary, gapCount)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
    ) {
        SummaryItem(
            "AVG GAP · ${basis.name}",
            summary.averageGapM?.let { String.format(Locale.US, "%.1f m", it) } ?: "—",
        )
        SummaryItem(
            "CLOSEST",
            summary.closest?.let { "${it.gapM.roundToInt()} m (${it.shorterClub}–${it.longerClub})" } ?: "—",
        )
        SummaryItem(
            "FARTHEST",
            summary.farthest?.let { "${it.gapM.roundToInt()} m (${it.shorterClub}–${it.longerClub})" } ?: "—",
        )
        // Verdict counts, tinted per spec §8 to match the gap block's
        // TIGHT/HEALTHY/WIDE line colours; on an all-inverted session an
        // explicit note replaces the (all-zero) counts.
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
            Text("VERDICTS", style = GolfTypography.MetricLabel, color = GolfColors.TextMuted)
            if (allInverted) {
                Text(
                    "all gaps inverted",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.AlertRed, // the INVERTED lines' colour
                )
            } else {
                val sep = " · "
                Text(
                    "${summary.goodCount} good",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.Teal, // HEALTHY line colour
                )
                Text(sep, style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
                Text(
                    "${summary.wideCount} big",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.Amber, // WIDE line colour
                )
                Text(sep, style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
                Text(
                    "${summary.tightCount} tight",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextSecondary, // TIGHT line colour
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
        Text(label, style = GolfTypography.MetricLabel, color = GolfColors.TextMuted)
        Text(value, style = GolfTypography.BodySmall, color = GolfColors.TextPrimary)
    }
}
