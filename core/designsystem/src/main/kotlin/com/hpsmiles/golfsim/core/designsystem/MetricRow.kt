// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/MetricRow.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Side-panel metric row: label left (secondary), value right (bold).
 * hot = the live moment: amber left bar + amber value. Never use amber for history.
 */
@Composable
fun MetricRow(
    label: String,
    value: String,
    unit: String?,
    hot: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GolfColors.Panel, RoundedCornerShape(GolfSpacing.CornerCard / 2))
            .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(
                    if (hot) GolfColors.Amber else GolfColors.Teal,
                    RoundedCornerShape(2.dp),
                ),
        )
        Text(
            text = label.uppercase(),
            style = GolfTypography.MetricLabel,
            color = GolfColors.TextSecondary,
            modifier = Modifier
                .weight(1f)
                .padding(start = GolfSpacing.Sm),
        )
        Text(
            text = value,
            style = GolfTypography.MetricValue,
            color = if (hot) GolfColors.Amber else GolfColors.TextPrimary,
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
