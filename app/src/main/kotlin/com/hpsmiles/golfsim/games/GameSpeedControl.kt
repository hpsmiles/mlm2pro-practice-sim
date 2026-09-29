// app/src/main/kotlin/com/hpsmiles/golfsim/games/GameSpeedControl.kt
package com.hpsmiles.golfsim.games

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/** Replay speed options for the game play screens (mirrors RangeScreen). */
enum class GameSpeedMult(val label: String, val divisor: Float) {
    X1("1x", 1f), X15("1.5x", 1.5f), X2("2x", 2f), X4("4x", 4f);
}

private val ChipFont = GolfTypography.Status.copy(fontSize = 15.sp)

/** Compact row of 1x / 1.5x / 2x / 4x chips styled like the range overlay. */
@Composable
fun SpeedChipRow(
    selected: GameSpeedMult,
    onSelect: (GameSpeedMult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
    ) {
        for (speed in GameSpeedMult.entries) {
            Text(
                text = speed.label,
                color = if (speed == selected) GolfColors.Teal else GolfColors.TextMuted,
                style = ChipFont,
                modifier = Modifier
                    .clickable { onSelect(speed) }
                    .border(
                        1.dp,
                        if (speed == selected) GolfColors.Teal else GolfColors.Line,
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
            )
        }
    }
}
