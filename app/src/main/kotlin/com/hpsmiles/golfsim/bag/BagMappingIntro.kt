package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * INTRO state (spec §3): bag-up-to-date reminder, bag summary, START TEST.
 * Shown when no result and no in-progress session exist. Empty/putter-only
 * bag disables START and points at Settings (spec §9).
 */
@Composable
fun BagMappingIntro(
    eligibleClubs: List<ClubRecord>,
    onOpenSettings: () -> Unit,
    onStartTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Button(
            onClick = onStartTest,
            enabled = eligibleClubs.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = GolfColors.Teal),
        ) {
            Text("START TEST", style = GolfTypography.MetricLabel)
        }
    }
}
