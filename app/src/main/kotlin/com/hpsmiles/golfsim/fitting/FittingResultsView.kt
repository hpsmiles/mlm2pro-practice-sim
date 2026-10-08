package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.bag.BagMappingFormats
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.range.ChipFont

/**
 * FIT results view (design notes §2): TABLE | TOP-DOWN segmented switch over
 * the same session's kept/excluded shots. [readOnly] = a completed history
 * session: exclusion controls are disabled, END COMPARISON is hidden, and the
 * header context line reads the viewed session's date instead of live counts.
 * The TABLE mode renders inside a Card-framed region (notes §2.2); the
 * TOP-DOWN pane is the coloured canvas + dispersion ovals in FittingTopDown.kt.
 */
@Composable
internal fun FittingResultsView(
    modifier: Modifier = Modifier,
    shots: List<FittingShotEntity>,
    readOnly: Boolean,
    controller: FittingController,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onSetExcluded: (List<Long>, Boolean) -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(GolfColors.Base)
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            // BACK chip (notes §2.1.1) → COMPARE (keeps collecting).
            Text(
                "← BACK",
                style = ChipFont,
                color = GolfColors.TextSecondary,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                    .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs),
            )
            // Segmented switch TABLE | TOP-DOWN (notes §2.1.2): one pill
            // container, selected segment = Teal40 background + Teal text.
            Row(
                modifier = Modifier
                    .background(GolfColors.Panel, RoundedCornerShape(50))
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FittingResultsMode.entries.forEach { mode ->
                    val selected = controller.resultsMode == mode
                    Text(
                        mode.label,
                        style = ChipFont,
                        color = if (selected) GolfColors.Teal else GolfColors.TextSecondary,
                        modifier = Modifier
                            .clickable { controller.resultsMode = mode }
                            .then(
                                if (selected) {
                                    Modifier.background(GolfColors.Teal40, RoundedCornerShape(50))
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs),
                    )
                }
            }
            // Shot-context line (notes §2.1.4): live counts, or the viewed
            // session's date when read-only (history). weight(1f, fill = false)
            // so it ellipsizes at its natural width across the full free space
            // (a second weight(1f) Spacer used to cap it at ~50 % of the row).
            Text(
                resultsContextLine(shots, readOnly),
                style = GolfTypography.Status,
                color = GolfColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!readOnly) {
                // END COMPARISON (notes §2.1.5): the only red control in FIT —
                // AlertRed ghost, no confirmation dialog (spec §6). Hidden for
                // read-only history sessions.
                Text(
                    "END COMPARISON",
                    style = ChipFont,
                    color = GolfColors.AlertRed,
                    modifier = Modifier
                        .clickable(onClick = onComplete)
                        .border(1.dp, GolfColors.AlertRed, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
                )
            }
        }
        if (controller.resultsMode == FittingResultsMode.TABLE) {
            // Card-framed table region (notes §2.2): Card surface, corner,
            // Md padding around the scrollable table.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(GolfSpacing.CornerCard))
                    .background(GolfColors.Card)
                    .padding(GolfSpacing.Md),
            ) {
                FittingTable(
                    shots = shots,
                    baselineClubId = controller.baselineClubId,
                    onBaselineChange = { controller.baselineClubId = it },
                    onSetExcluded = onSetExcluded,
                    readOnly = readOnly,
                    distanceMode = controller.distanceMode,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            FittingTopDownPane(shots = shots, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Header context line (notes §2.1.4): live = "N shots · M excluded"; for a
 * read-only history session the line reads the viewed session's date instead
 * (formatted from its first shot's timestamp).
 */
private fun resultsContextLine(shots: List<FittingShotEntity>, readOnly: Boolean): String {
    if (readOnly) {
        val ts = shots.firstOrNull()?.timestampMs ?: return ""
        return BagMappingFormats.dateTime(ts)
    }
    val excluded = shots.count { it.excluded }
    return "${shots.size} shots · $excluded excluded"
}
