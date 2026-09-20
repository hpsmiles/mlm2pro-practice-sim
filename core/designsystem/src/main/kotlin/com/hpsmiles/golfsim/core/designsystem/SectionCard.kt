// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/SectionCard.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Card panel: card background, 14 dp corners, title + content slot. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(GolfColors.Card, RoundedCornerShape(GolfSpacing.CornerCard))
            .padding(GolfSpacing.Lg),
    ) {
        Text(
            text = title,
            style = GolfTypography.ScreenTitle,
            color = GolfColors.TextPrimary,
        )
        Column(
            modifier = Modifier.padding(top = GolfSpacing.Sm),
            content = content,
        )
    }
}
