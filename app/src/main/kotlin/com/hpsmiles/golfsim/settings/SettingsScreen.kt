// app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt
package com.hpsmiles.golfsim.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard

/**
 * M4b: Settings reduces to authorization guidance — the Rapsodo Web API
 * Secret is an embedded constant (SecretProvider, decrypted at runtime) and
 * Awesome Golf authorization happens in the Rapsodo app (spec §2 decision 1).
 * SecretStore.kt is retained (unused) for future non-API settings.
 */
@Composable
fun SettingsScreen() {
    SectionCard("RAPSODO AUTH") {
        Text(
            text = "Before using a live session:\n" +
                "1. In the Rapsodo app: Play \u2192 Simulation \u2192 3rd Party Apps \u2192 Awesome Golf \u2192 Authenticate Now\n" +
                "2. Re-authorize every 24 hours (Rapsodo requirement)\n" +
                "3. The session token lasts ~3 hours; if the device stays red, re-authenticate and power-cycle it",
            style = GolfTypography.Body,
            color = GolfColors.TextSecondary
        )
    }
}
