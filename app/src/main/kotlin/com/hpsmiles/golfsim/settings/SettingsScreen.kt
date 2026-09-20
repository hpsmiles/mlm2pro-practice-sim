// app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt
package com.hpsmiles.golfsim.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard

/**
 * Phase A settings: only the Rapsodo Secret. Phase B consumes it for the
 * token fetch (requires "Awesome Golf" third-party access enabled in the
 * Rapsodo app — user prerequisite, see M4a spec §2).
 */
@Composable
fun SettingsScreen() {
    // Mechanical: LocalContext.current must run in composable scope, not
    // inside remember's calculation lambda.
    val context = LocalContext.current
    val store = remember { SecretStore(context) }
    var text by remember { mutableStateOf(store.getSecret()) }
    var saved by remember { mutableStateOf(false) }

    SectionCard("RAPSHODO AUTH") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; saved = false },
            placeholder = { Text("Rapsodo Secret") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        // Plan-note-shipped pattern: SAVE is a Text label inside a Box with
        // the standard androidx clickable — MetricChip has no onClick variant.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { store.setSecret(text); saved = true }
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = if (saved) "SAVED" else "SAVE",
                style = GolfTypography.MetricLabel,
                color = if (saved) GolfColors.BleArmedGreen else GolfColors.TextPrimary,
            )
        }
        Text(
            text = "Requires Awesome Golf third-party access enabled in the Rapsodo app (Phase B prerequisite).",
            style = GolfTypography.Status,
            color = GolfColors.TextMuted,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
