// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/StatusStrip.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Bottom status strip: BLE armed dot + session info (avg/sigma/misreads). */
@Composable
fun StatusStrip(
    armed: Boolean,
    info: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GolfSpacing.StatusStripHeight)
            .background(GolfColors.Panel)
            .padding(horizontal = GolfSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (armed) GolfColors.BleArmedGreen else GolfColors.AlertRed,
                    CircleShape,
                ),
        )
        Text(
            text = info,
            style = GolfTypography.Status,
            color = GolfColors.TextMuted,
            modifier = Modifier.padding(start = GolfSpacing.Sm),
        )
    }
}
