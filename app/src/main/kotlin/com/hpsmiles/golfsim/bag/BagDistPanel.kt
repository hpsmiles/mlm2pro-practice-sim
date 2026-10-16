package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * Collapsed-by-default "DISTRIBUTION & GAPS" panel (spec §9): the box-plot
 * matrix and the gap block live in here, so the top of the page belongs to the
 * chart. Collapsed by default; the caller owns the open state and supplies the
 * content as a slot.
 */
@Composable
fun BagDistPanel(
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(GolfSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            Chevron(open)
            Text("DISTRIBUTION & GAPS", style = GolfTypography.MetricLabel, color = GolfColors.TextSecondary)
            Text(
                if (open) "tap to collapse" else "box plots and gap detail",
                style = GolfTypography.Status,
                color = GolfColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
        }
        if (open) content()
    }
}

@Composable
private fun Chevron(open: Boolean) {
    Canvas(Modifier.size(10.dp).rotate(if (open) 90f else 0f)) {
        val path = Path().apply {
            moveTo(size.width * 0.25f, 0f)
            lineTo(size.width * 0.75f, size.height / 2f)
            lineTo(size.width * 0.25f, size.height)
        }
        drawPath(path, GolfColors.TextSecondary, style = Stroke(width = 2f))
    }
}
