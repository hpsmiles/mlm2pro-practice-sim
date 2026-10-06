package com.hpsmiles.golfsim.games

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * In-game quick settings (spec 2026-10-06): gear chip + modal dialog so the
 * user can flip game-facing settings (today: sound) without leaving the game.
 * Pure UI over hoisted state — never touches activeGame / tab / shot routing,
 * so mid-game progress survives. Future quick toggles add a row to the dialog.
 */

/** Small gear glyph chip for a play-screen HUD corner. */
@Composable
fun GameQuickSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "⚙",
        style = GolfTypography.MetricValue,
        color = GolfColors.TextPrimary,
        modifier = modifier
            .semantics { contentDescription = "Game quick settings" }
            .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
            .clickable(onClick = onClick)
            .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
    )
}

/**
 * Modal quick-settings dialog. Sounds row mirrors Settings > SOUND
 * (SettingsScreen.kt "SOUNDS: ON/OFF" chip styling).
 */
@Composable
fun GameQuickSettingsDialog(
    soundsEnabled: Boolean,
    onSoundsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GAME SETTINGS") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (soundsEnabled) "SOUNDS: ON" else "SOUNDS: OFF",
                        style = GolfTypography.MetricLabel,
                        color = if (soundsEnabled) GolfColors.Teal else GolfColors.TextSecondary,
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (soundsEnabled) GolfColors.Teal else GolfColors.Line,
                                RoundedCornerShape(50),
                            )
                            .clickable { onSoundsChange(!soundsEnabled) }
                            .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                    )
                }
                Text(
                    text = "Game cue sounds (glass break, success, miss).",
                    style = GolfTypography.Body,
                    color = GolfColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = GolfSpacing.Sm),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("DONE") }
        },
    )
}
