package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * COLLECTING state (spec §4): current-club banner, 5 progress dots, last-shot
 * carry readout, "no read" pill, next-up preview, skip/END TEST controls, and
 * the quality-gate prompt. Shots come from the repository flow (Room truth —
 * AppRoot write-through persists each shot); this view only derives state and
 * drives the collector. Leaving the tab is safe: progress persists (spec §2).
 */
@Composable
fun BagMappingCollecting(
    collector: BagMappingCollector,
    activeShots: List<BagMappingShotEntity>,
    onCompleteSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val club = collector.currentClub
    // Quality-gate derivation (spec §5): runs whenever the kept set changes.
    // ACCEPT auto-advances (this also covers resume with >= 5 kept and the
    // 15 cap); ASK_MORE shows the prompt unless the player already chose
    // HIT MORE for this club (per-club latch in the collector).
    val clubShots = activeShots.filter { it.clubName == club?.name }
    val keptShots = clubShots.filter { !it.filtered }
    LaunchedEffect(club?.name, keptShots.size, clubShots.size, collector.moreGrantedFor.value, collector.tick.intValue) {
        if (club == null) return@LaunchedEffect
        if (keptShots.size >= ClubQualityGate.TARGET_KEPT) {
            when (ClubQualityGate.evaluate(keptShots.map { it.carryM }, clubShots.size - keptShots.size)) {
                ClubQualityGate.Verdict.ACCEPT -> collector.advance()
                ClubQualityGate.Verdict.ASK_MORE ->
                    if (collector.moreGrantedFor.value != club.name) collector.gatePrompt.value = true
            }
        }
    }
    Column(
        modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
    ) {
        Text("BAG MAPPING", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
        if (club == null) return@Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GolfColors.Teal, RoundedCornerShape(GolfSpacing.CornerCard))
                .padding(GolfSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("NOW: ${club.name}", style = GolfTypography.Hero, color = GolfColors.TextPrimary)
                Text(
                    "club ${collector.currentIndex.intValue + 1} of ${collector.plan.size}",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                repeat(ClubQualityGate.TARGET_KEPT) { i ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (i < keptShots.size) GolfColors.Teal else GolfColors.Line,
                                CircleShape,
                            ),
                    )
                }
                Text(
                    "${keptShots.size}/${ClubQualityGate.TARGET_KEPT} valid" +
                        if (clubShots.size > keptShots.size) " · ${clubShots.size - keptShots.size} filtered" else "",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextSecondary,
                )
            }
            val lastKept = keptShots.lastOrNull()
            Text(
                if (lastKept != null) "Last: ${BagMappingFormats.carry(lastKept.carryM)}" else "Hit a shot to begin",
                style = GolfTypography.MetricValue,
                color = GolfColors.TextPrimary,
            )
            if (collector.noReadCount.intValue > 0) {
                Text(
                    "no read (${collector.noReadCount.intValue})",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.Amber,
                )
            }
        }
        val nextUp = collector.plan.getOrNull(collector.currentIndex.intValue + 1)
        Text(
            if (nextUp != null) "Next up: ${nextUp.name}" else "Last club in the bag",
            style = GolfTypography.Body,
            color = GolfColors.TextSecondary,
        )
        if (collector.gatePrompt.value) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GolfColors.Amber, RoundedCornerShape(GolfSpacing.CornerCard))
                    .padding(GolfSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
            ) {
                Text("This club looks inconsistent", style = GolfTypography.MetricLabel, color = GolfColors.Amber)
                Text(
                    "Filtered shots or a wide carry window — more shots give a truer picture (up to ${ClubQualityGate.MAX_KEPT}).",
                    style = GolfTypography.Body,
                    color = GolfColors.TextPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md)) {
                    Button(onClick = { collector.hitMore() }, colors = ButtonDefaults.buttonColors(containerColor = GolfColors.Teal)) {
                        Text("HIT MORE")
                    }
                    OutlinedButton(onClick = { collector.acceptResult() }) { Text("ACCEPT RESULT") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md)) {
            OutlinedButton(onClick = { collector.advance() }) { Text("SKIP CLUB") }
            TextButton(onClick = onCompleteSession) { Text("END TEST") }
        }
    }
}
