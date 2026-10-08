// app/src/main/kotlin/com/hpsmiles/golfsim/games/GameMetricsPanel.kt
package com.hpsmiles.golfsim.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.sqrt
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.MetricRow
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.range.DisplayShot
import com.hpsmiles.golfsim.range.LastShotChips

/**
 * Right-side metrics panel for the game play screens. Mirrors RangeScreen's
 * LAST SHOT / SESSION panel exactly, using the same chips and rows.
 */
@Composable
fun GameMetricsPanel(
    currentShot: DisplayShot?,
    sessionShots: List<DisplayShot>,
    modifier: Modifier = Modifier,
) {
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
            if (currentShot == null) {
                Text("Fire a shot", style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
            } else {
                LastShotChips(currentShot)
            }
        }
        SectionCard("SESSION") {
            val carries = sessionShots.map { it.shotResult.carryM }
            val count = carries.size
            MetricRow("shots", "$count", "")
            MetricRow(
                "avg carry",
                if (count == 0) "-" else String.format(Locale.US, "%.0f", carries.average()),
                "M",
            )
            val sigma = if (count > 1) {
                val mean = carries.average()
                sqrt(carries.sumOf { (it - mean) * (it - mean) } / count)
            } else 0.0
            MetricRow("sigma", if (count > 1) String.format(Locale.US, "%.1f", sigma) else "-", "M")
        }
    }
}
