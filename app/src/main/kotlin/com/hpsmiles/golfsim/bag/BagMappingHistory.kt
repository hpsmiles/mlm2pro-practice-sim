package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * HISTORY state (spec §3): every completed mapping result, newest first
 * (repo order), date · clubs covered · shot count; tap → read-only result.
 * No retention limit (spec §2).
 */
@Composable
fun BagMappingHistory(
    history: List<BagMappingSessionEntity>,
    shotCounts: Map<Long, Int>,
    onOpen: (BagMappingSessionEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("BAG MAPPING — HISTORY", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
            OutlinedButton(onClick = onBack) { Text("BACK") }
        }
        if (history.isEmpty()) {
            Text("No completed tests yet.", style = GolfTypography.Body, color = GolfColors.TextSecondary)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            items(history, key = { it.id }) { session ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
                        .clickable { onOpen(session) }
                        .padding(GolfSpacing.Md),
                ) {
                    Text(
                        BagMappingFormats.dateTime(session.startedAtMs),
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.TextPrimary,
                    )
                    Text(
                        "${session.clubSnapshot().size} clubs · ${shotCounts[session.id] ?: 0} shots",
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextSecondary,
                    )
                }
            }
        }
    }
}
