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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * INTRO state (spec §3): bag-up-to-date reminder, bag summary, test-order
 * selector, START TEST. Shown when no result and no in-progress session
 * exist. Empty/putter-only bag disables START and points at Settings
 * (spec §9).
 */
@Composable
fun BagMappingIntro(
    eligibleClubs: List<ClubRecord>,
    onOpenSettings: () -> Unit,
    onStartTest: (BagOrderMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var orderMode by remember { mutableStateOf(BagOrderMode.WEDGE_FIRST) }
    Column(
        modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
    ) {
        Text("BAG MAPPING", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
        Text(
            "Hit 5 clean shots with every club in your bag to build your true carry gaps.",
            style = GolfTypography.Body,
            color = GolfColors.TextSecondary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GolfColors.Amber, RoundedCornerShape(GolfSpacing.CornerCard))
                .padding(GolfSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
        ) {
            Text("BEFORE YOU START", style = GolfTypography.MetricLabel, color = GolfColors.Amber)
            Text(
                "Check your bag is up to date — add, rename or remove clubs in SETTINGS first. " +
                    "Putters and TEST clubs are skipped.",
                style = GolfTypography.Body,
                color = GolfColors.TextPrimary,
            )
            OutlinedButton(onClick = onOpenSettings) { Text("OPEN SETTINGS") }
        }
        Text(
            if (eligibleClubs.isEmpty()) "No clubs to map." else "${eligibleClubs.size} clubs in the bag",
            style = GolfTypography.Body,
            color = GolfColors.TextSecondary,
        )
        if (eligibleClubs.isEmpty()) {
            Text(
                "Add clubs in SETTINGS to start a test.",
                style = GolfTypography.Body,
                color = GolfColors.TextSecondary,
            )
        }
        // Test-order selector (spec item 2): wedge-first default, random option.
        Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
            Text("ORDER", style = GolfTypography.MetricLabel, color = GolfColors.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                OrderOption(
                    label = "SHORT → LONG",
                    active = orderMode == BagOrderMode.WEDGE_FIRST,
                    onClick = { orderMode = BagOrderMode.WEDGE_FIRST },
                )
                OrderOption(
                    label = "RANDOM",
                    active = orderMode == BagOrderMode.RANDOM,
                    onClick = { orderMode = BagOrderMode.RANDOM },
                )
            }
            Text(
                if (orderMode == BagOrderMode.WEDGE_FIRST) "Shortest club first, up to driver."
                else "Clubs in a shuffled order.",
                style = GolfTypography.BodySmall,
                color = GolfColors.TextSecondary,
            )
        }
        Button(
            onClick = { onStartTest(orderMode) },
            enabled = eligibleClubs.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = GolfColors.Teal),
        ) {
            Text("START TEST", style = GolfTypography.MetricLabel)
        }
    }
}

/** Bordered pill selector option — same treatment as the range VIEW chip. */
@Composable
private fun OrderOption(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = GolfTypography.MetricLabel,
        color = if (active) GolfColors.Teal else GolfColors.TextMuted,
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(1.dp, if (active) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
            .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs),
    )
}
