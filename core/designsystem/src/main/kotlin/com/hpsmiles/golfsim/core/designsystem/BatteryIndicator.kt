// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryIndicator.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Clamped battery readout state; the readout renders entirely from this. */
internal data class BatteryDisplay(val percent: Int, val low: Boolean)

/**
 * Maps a raw EVENTS 0x03 battery byte to its display state: clamped to
 * 0..100 (semantics "believed percent", formally unverified) and low
 * strictly below 20. Pure so the JVM test pins the boundary.
 */
internal fun batteryDisplay(percent: Int): BatteryDisplay {
    val clamped = percent.coerceIn(0, 100)
    return BatteryDisplay(percent = clamped, low = clamped < 20)
}

/**
 * Right-edge status-strip readout: a drawn battery glyph (no icon
 * dependency, matching the custom-painter house style) + percent label.
 * TextMuted normally, AlertRed under 20 % (Amber stays reserved for
 * live-moment UI).
 */
@Composable
internal fun BatteryIndicator(percent: Int, modifier: Modifier = Modifier) {
    val display = batteryDisplay(percent)
    val color = if (display.low) GolfColors.AlertRed else GolfColors.TextMuted
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 20.dp, height = 10.dp)) {
            val stroke = 1.dp.toPx()
            val nubWidth = 2.dp.toPx()
            val nubHeight = 4.dp.toPx()
            // 1 dp gap between body and terminal nub.
            val bodyRight = size.width - nubWidth - stroke
            // Terminal nub, vertically centered on the right edge.
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - nubWidth, (size.height - nubHeight) / 2f),
                size = Size(nubWidth, nubHeight),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
            // Body outline.
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(bodyRight - stroke, size.height - stroke),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = stroke),
            )
            // Fill level: inset from the outline, width = percent of interior.
            if (display.percent > 0) {
                val inset = stroke * 2f
                val innerWidth = bodyRight - inset * 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(inset, inset),
                    size = Size(innerWidth * display.percent / 100f, size.height - inset * 2f),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            }
        }
        Text(
            text = "${display.percent}%",
            style = GolfTypography.Status,
            color = color,
            modifier = Modifier.padding(start = GolfSpacing.Xs),
        )
    }
}
