// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/NavRail.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Slim 72 dp navigation rail for landscape (left edge of every screen). */
@Composable
fun NavRail(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(GolfSpacing.NavRailWidth)
            .fillMaxHeight()
            .background(GolfColors.Panel)
            .padding(vertical = GolfSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(GolfSpacing.Sm),
        content = content,
    )
}

/** One rail button: selected tints teal, otherwise muted. */
@Composable
fun NavRailButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Button box: NavRailWidth - 2 * Sm (80 dp), label centered both axes
    // (2026-09-24 user request).
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(GolfSpacing.Sm))
            .background(
                if (selected) GolfColors.Teal40 else GolfColors.Panel,
                RoundedCornerShape(GolfSpacing.Sm),
            )
            .clickable(onClick = onClick)
            .size(width = 80.dp, height = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = GolfTypography.Status.copy(fontSize = 13.sp),
            color = if (selected) GolfColors.Teal else GolfColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
