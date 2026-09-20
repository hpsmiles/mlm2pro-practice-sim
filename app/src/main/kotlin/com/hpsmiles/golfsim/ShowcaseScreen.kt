// app/src/main/kotlin/com/hpsmiles/golfsim/ShowcaseScreen.kt
package com.hpsmiles.golfsim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTheme
import com.hpsmiles.golfsim.core.designsystem.MetricChip
import com.hpsmiles.golfsim.core.designsystem.MetricRow
import com.hpsmiles.golfsim.core.designsystem.NavRail
import com.hpsmiles.golfsim.core.designsystem.NavRailButton
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.core.designsystem.StatusStrip

/**
 * M3 acceptance surface: every kit component in its states, laid out like the
 * range screen (rail left, status strip bottom, card stack in between).
 */
@Composable
fun DesignSystemShowcase() {
    GolfTheme {
        Column(modifier = Modifier.fillMaxSize().background(GolfColors.Base)) {
            Row(modifier = Modifier.weight(1f)) {
                NavRail {
                    NavRailButton(label = "RANGE", selected = true, onClick = {})
                    NavRailButton(label = "BAG", selected = false, onClick = {})
                    NavRailButton(label = "STATS", selected = false, onClick = {})
                    NavRailButton(label = "SET", selected = false, onClick = {})
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(GolfSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
                ) {
                    SectionCard(title = "Telemetry") {
                        Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                MetricChip("carry", "163", "M")
                                MetricChip("total", "171", "M")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                MetricChip("ball", "68.6", "MPH")
                                MetricChip("spin", "5 620", "RPM")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                MetricChip("launch", "16.3", "\u00B0")
                                MetricChip("axis", "-5.6", "\u00B0")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                MetricChip("last", "163", "M", accent = GolfColors.Amber)
                                MetricChip("club", "7", "IRON")
                            }
                        }
                    }
                    SectionCard(title = "Session") {
                        Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                            MetricRow("carry", "163.4", "M", hot = true)
                            MetricRow("total", "171.0", "M")
                            MetricRow("ball speed", "68.6", "MPH")
                            MetricRow("spin", "5 620", "RPM")
                            MetricRow("launch", "16.3", "\u00B0")
                        }
                    }
                    SectionCard(title = "Comparison hues") {
                        Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                            listOf(
                                "A" to GolfColors.Comparison.A,
                                "B" to GolfColors.Comparison.B,
                                "C" to GolfColors.Comparison.C,
                                "D" to GolfColors.Comparison.D,
                            ).forEach { (name, hue) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(hue, RoundedCornerShape(4.dp)),
                                    )
                                    Text(
                                        text = name,
                                        modifier = Modifier.padding(start = GolfSpacing.Sm),
                                        color = GolfColors.TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            StatusStrip(armed = true, info = "BLE ARMED - AVG 163 M - SD 4.1 - MISREADS 2")
        }
    }
}
