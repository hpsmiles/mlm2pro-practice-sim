// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/MetricChip.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pill telemetry chip (range-view side panel + status areas). The accent color
 * paints a 3 dp left bar; amber marks the live moment (LAST/hot values only).
 */
@Composable
fun MetricChip(
    label: String,
    value: String,
    unit: String?,
    accent: Color = GolfColors.Teal,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(GolfColors.Panel, RoundedCornerShape(50))
            .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Column(modifier = Modifier.padding(start = GolfSpacing.Sm)) {
            Text(
                text = label.uppercase(),
                style = GolfTypography.MetricLabel,
                color = GolfColors.TextSecondary,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = GolfTypography.MetricValue,
                    color = GolfColors.TextPrimary,
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = GolfTypography.Unit,
                        color = GolfColors.TextMuted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                    )
                }
            }
        }
    }
}
