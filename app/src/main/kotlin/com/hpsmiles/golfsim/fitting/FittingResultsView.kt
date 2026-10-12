package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * FIT results view (design notes §2, layout per spec §5): one scrollable page —
 * the metric table card with the coloured top-down pane stacked UNDER it, the
 * page as the single vertical scroll owner (expanding a club drill-down pushes
 * the pane down; nothing overlays it). [readOnly] = a completed history
 * session: exclusion controls are disabled, END COMPARISON is hidden, and the
 * header context line reads the viewed session's date instead of live counts.
 * The TABLE renders inside a Card-framed region (notes §2.2); the top-down
 * pane is the coloured canvas + enclosing-ellipse rings in FittingTopDown.kt.
 * SAVE PDF (2026-10-08) exports the viewed shots — live or read-only — via the
 * share sheet ([onExportPdf] is threaded from AppRoot, which renders + shares).
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
    onExportPdf: () -> Unit,
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
            // SAVE PDF (2026-10-08): export the viewed session — live OR a
            // read-only history session — via the share sheet. Same ghost-chip
            // idiom; works for both because the shots list is already resolved.
            Text(
                "SAVE PDF",
                style = ChipFont,
                color = GolfColors.TextSecondary,
                modifier = Modifier
                    .clickable(onClick = onExportPdf)
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                    .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs),
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
        // Single scroll owner (spec §5): the page scrolls; the table card and
        // the top-down pane stack vertically, so expanding a club drill-down
        // grows the page and pushes the pane down — it can never overlay it.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
        ) {
            // Card-framed table region (notes §2.2): Card surface, corner,
            // Md padding around the table (the table's own vertical scroll is
            // gone — the page scrolls; the horizontal metric scroll stays).
            Box(
                Modifier
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Top-down under the table (spec §5), framed as a sibling card —
            // same clip/background/padding geometry as the table card plus a
            // 1 dp Line border, so the pane sits horizontally inline with the
            // table (device feedback 2026-10-12). Fixed height, device-tunable.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(GolfSpacing.CornerCard))
                    .background(GolfColors.Card)
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.CornerCard))
                    .padding(GolfSpacing.Md),
            ) {
                FittingTopDownPane(shots = shots, modifier = Modifier.fillMaxWidth().height(360.dp))
            }
            // Bottom spacing so the pane never sits flush on the scroll edge.
            Spacer(Modifier.height(GolfSpacing.Md))
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
