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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.bag.BagMappingFormats
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.range.ChipFont

/**
 * FIT history view (design notes §3): completed comparisons, newest first
 * (DAO order). Each row is a SectionCard-styled card: date/time, colour-dotted
 * club names in that session's first-appearance order, "N shots" and a VIEW
 * chip; the whole row opens the session read-only in RESULTS. Header geometry
 * mirrors FittingResultsView — BACK chip + `COMPARISON HISTORY`, no END button
 * and no segmented switch.
 */
@Composable
internal fun FittingHistoryView(
    modifier: Modifier = Modifier,
    history: List<FittingSessionEntity>,
    historyDetails: Map<Long, List<Pair<String, Int>>>,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(GolfColors.Base)
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        // Header (notes §3; same geometry as §2.1 / FittingResultsView): BACK
        // chip → COMPARE + ScreenTitle. No END button, no segmented switch.
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            Text(
                "← BACK",
                style = ChipFont,
                color = GolfColors.TextSecondary,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                    .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs),
            )
            Text(
                "COMPARISON HISTORY",
                style = GolfTypography.ScreenTitle,
                color = GolfColors.TextPrimary,
            )
        }
        if (history.isEmpty()) {
            // Centered empty state (notes §3): fills the remaining space.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No completed comparisons yet",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextMuted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
            ) {
                items(history, key = { it.id }) { session ->
                    val details = historyDetails[session.id].orEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(GolfSpacing.CornerCard))
                            .background(GolfColors.Card)
                            .clickable { onOpen(session.id) }
                            .padding(GolfSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                        ) {
                            // Date/time (notes §3): completed-at preferred.
                            Text(
                                BagMappingFormats.dateTime(session.completedAtMs ?: session.startedAtMs),
                                style = GolfTypography.Body,
                                color = GolfColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // Second line: colour-dotted club names in the
                            // session's first-appearance order (the details map
                            // is already ordered; index 0 = first club). A
                            // session with no details entry shows only the date.
                            if (details.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                                ) {
                                    details.forEachIndexed { index, (name, _) ->
                                        if (index > 0) {
                                            Text("·", style = GolfTypography.BodySmall, color = GolfColors.TextSecondary)
                                        }
                                        Box(
                                            Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(FittingColors.clubColor(index)),
                                        )
                                        Text(
                                            name,
                                            style = GolfTypography.BodySmall,
                                            color = GolfColors.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        // Right: N shots + VIEW → ghost chip. The whole row tap
                        // does the same as VIEW (onOpen).
                        Column(
                            modifier = Modifier.padding(start = GolfSpacing.Md),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                        ) {
                            if (details.isNotEmpty()) {
                                Text(
                                    "${details.sumOf { it.second }} shots",
                                    style = GolfTypography.Status,
                                    color = GolfColors.TextMuted,
                                )
                            }
                            Text(
                                "VIEW →",
                                style = ChipFont,
                                color = GolfColors.TextSecondary,
                                modifier = Modifier
                                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                                    .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
