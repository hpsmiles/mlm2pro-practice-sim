// app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt
package com.hpsmiles.golfsim.settings

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.connect.CaptureLog
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import java.io.File

private fun chipStyle(enabled: Boolean) = if (enabled) GolfColors.Teal else GolfColors.TextMuted

/**
 * M4b: Settings reduces to authorization guidance — the Rapsodo Web API
 * Secret is an embedded constant (SecretProvider, decrypted at runtime) and
 * Awesome Golf authorization happens in the Rapsodo app (spec §2 decision 1).
 * SecretStore.kt is retained (unused) for future non-API settings.
 *
 * FIX 5: debug/bench capture tooling — CAPTURE ON/OFF (feeds the GATT client's
 * [CaptureLog]) and EXPORT (writes a golden `.properties` fixture into the app
 * files dir, logs the path for adb pull, and offers a text/plain share).
 */
@Composable
fun SettingsScreen(captureLog: CaptureLog = CaptureLog()) {
    val context = LocalContext.current
    var captureOn by remember { mutableStateOf(captureLog.enabled) }
    var lastExportPath by remember { mutableStateOf("") }

    SectionCard("RAPSODO AUTH") {
        Text(
            text = "Before using a live session:\n" +
                "1. In the Rapsodo app: Play \u2192 Simulation \u2192 3rd Party Apps \u2192 Awesome Golf \u2192 Authenticate Now\n" +
                "2. Re-authorize every 24 hours (Rapsodo requirement)\n" +
                "3. The session token lasts ~3 hours; if the device stays red, re-authenticate and power-cycle it",
            style = GolfTypography.Body,
            color = GolfColors.TextSecondary,
        )
    }
    // DEBUG/BENCH TOOLING (FIX 5) — not part of the consumer surface.
    SectionCard("DEBUG - NOTIFICATION CAPTURE (BENCH)") {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                text = if (captureOn) "CAPTURE: ON" else "CAPTURE: OFF",
                style = GolfTypography.MetricLabel,
                color = chipStyle(captureOn),
                modifier = Modifier
                    .border(1.dp, if (captureOn) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                    .clickable {
                        captureOn = !captureOn
                        captureLog.enabled = captureOn
                    }
                    .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
            )
        }
        Text(
            text = if (captureOn) "LISTENING - ${captureLog.entries().size} notification(s) captured" else "Toggle ON during a live session to record raw/decrypted notifications",
            style = GolfTypography.Status,
            color = GolfColors.TextMuted,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "EXPORT",
                style = GolfTypography.MetricLabel,
                color = GolfColors.TextPrimary,
                modifier = Modifier
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                    .clickable {
                        val file = File(context.filesDir, "capture.properties")
                        file.writeText(captureLog.exportProperties(firmware = "unknown-M4b-bench"))
                        lastExportPath = file.absolutePath
                        Log.i("CaptureExport", "fixture exported: ${file.absolutePath}")
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "MLM2PRO capture fixture")
                            putExtra(Intent.EXTRA_TEXT, file.readText())
                        }
                        context.startActivity(Intent.createChooser(share, "Share capture fixture"))
                    }
                    .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
            )
        }
        if (lastExportPath.isNotEmpty()) {
            Text(
                text = "Last export: $lastExportPath (adb pull from app files dir works)",
                style = GolfTypography.Status,
                color = GolfColors.TextMuted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
