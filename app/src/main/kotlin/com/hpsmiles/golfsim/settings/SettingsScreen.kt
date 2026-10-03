// app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt
package com.hpsmiles.golfsim.settings

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.connect.CaptureLog
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.core.physics.GreenCondition
import com.hpsmiles.golfsim.core.physics.TurfCondition
import com.hpsmiles.golfsim.range.AddClubForm
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private fun chipStyle(enabled: Boolean) = if (enabled) GolfColors.Teal else GolfColors.TextMuted

/**
 * M5: BAG editor — add/rename/delete club tags (spec D3). Deleting never
 * rewrites history: persisted shots keep their snapshotted club string.
 * M4b cards (auth guidance + bench capture tooling) are unchanged.
 * Capture exports write timestamped files (capture-yyyyMMdd-HHmmss.properties)
 * so multiple captures are retained per bench session.
 */
@Composable
fun SettingsScreen(
    captureLog: CaptureLog = CaptureLog(),
    greenCondition: GreenCondition = GreenCondition.NORMAL,
    onGreenConditionChange: (GreenCondition) -> Unit = {},
    turfCondition: TurfCondition = TurfCondition.FIRM,
    onTurfConditionChange: (TurfCondition) -> Unit = {},
    clubs: List<ClubRecord> = emptyList(),
    onAddClub: suspend (String, ClubType, Boolean) -> Boolean = { _, _, _ -> false },
    onRenameClub: suspend (Long, String) -> Boolean = { _, _ -> false },
    onDeleteClub: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    var captureOn by remember { mutableStateOf(captureLog.enabled) }
    var lastExportPath by remember { mutableStateOf("") }

    var entryCount by remember { mutableStateOf(captureLog.entries().size) }
    LaunchedEffect(captureOn) {
        while (captureOn) {
            entryCount = captureLog.entries().size
            kotlinx.coroutines.delay(250)
        }
    }

    // BAG editor state.
    var renameTarget by remember { mutableStateOf<ClubRecord?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameRejected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GolfColors.Base)
            .verticalScroll(rememberScrollState())
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
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
        SectionCard("BAG") {
            Text(
                text = "Club tags used when tagging shots. Renames apply to new shots only; shots keep the tag they were saved with.",
                style = GolfTypography.Body,
                color = GolfColors.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(bottom = GolfSpacing.Sm),
            )
            if (clubs.isEmpty()) {
                Text(
                    "NO CLUBS YET",
                    style = GolfTypography.Status,
                    color = GolfColors.TextMuted,
                    modifier = Modifier.padding(bottom = GolfSpacing.Sm),
                )
            }
            clubs.forEach { club ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Text(
                        club.name,
                        style = GolfTypography.Body,
                        color = GolfColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "RENAME",
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.TextSecondary,
                        modifier = Modifier
                            .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                            .clickable {
                                renameTarget = club
                                renameText = club.name
                                renameRejected = false
                            }
                            .padding(horizontal = GolfSpacing.Md, vertical = 6.dp),
                    )
                    Text(
                        text = "DELETE",
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.AlertRed,
                        modifier = Modifier
                            .border(1.dp, GolfColors.AlertRed, RoundedCornerShape(50))
                            .clickable { onDeleteClub(club.id) }
                            .padding(horizontal = GolfSpacing.Md, vertical = 6.dp),
                    )
                }
            }
            // M5x E3: same shared add-club form as the range picker.
            AddClubForm(
                onSubmit = onAddClub,
                onAdded = {},
            )
        }
        SectionCard("GREEN") {
            Text(
                text = "Green surface for the range target and both games. Softer greens hold approach shots; firmer greens release and roll out.",
                style = GolfTypography.Body,
                color = GolfColors.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(bottom = GolfSpacing.Sm),
            )
            PickerList(
                rows = GreenCondition.entries.map { Triple(it.label, it.blurb, it == greenCondition) },
                onSelect = { onGreenConditionChange(GreenCondition.entries[it]) },
            )
        }
        SectionCard("TURF") {
            Text(
                text = "Fairway surface everywhere the ball is not on a green. Firmer turf runs out faster.",
                style = GolfTypography.Body,
                color = GolfColors.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(bottom = GolfSpacing.Sm),
            )
            PickerList(
                rows = TurfCondition.entries.map { Triple(it.label, it.blurb, it == turfCondition) },
                onSelect = { onTurfConditionChange(TurfCondition.entries[it]) },
            )
        }
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
                text = if (captureOn) "LISTENING - $entryCount notification(s) captured" else "Toggle ON during a live session to record raw/decrypted notifications",
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
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                            val file = File(context.filesDir, "capture-$stamp.properties")
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

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("RENAME ${renameTarget?.name}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = {
                            renameText = it.take(20)
                            renameRejected = false
                        },
                        singleLine = true,
                    )
                    if (renameRejected) {
                        Text(
                            text = if (renameText.isBlank()) "ENTER A NAME" else if (renameText.contains(',')) "NO COMMAS" else "ALREADY IN BAG",
                            style = GolfTypography.Status,
                            color = GolfColors.AlertRed,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = renameTarget ?: return@TextButton
                        scope.launch {
                            if (onRenameClub(target.id, renameText.trim())) {
                                renameTarget = null
                            } else {
                                renameRejected = true
                            }
                        }
                    },
                ) { Text("CONFIRM") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("CANCEL") }
            },
        )
    }
}

/**
 * Shared settings picker: a vertical list of selectable rows (label + blurb),
 * teal-highlighted when selected. Used by the GREEN and TURF cards.
 */
@Composable
private fun PickerList(
    rows: List<Triple<String, String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    rows.forEachIndexed { index, (label, blurb, selected) ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .border(
                    1.dp,
                    if (selected) GolfColors.Teal else GolfColors.Line,
                    RoundedCornerShape(GolfSpacing.Sm),
                )
                .clickable { onSelect(index) }
                .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Sm),
        ) {
            Text(
                text = label,
                style = GolfTypography.Body,
                color = if (selected) GolfColors.Teal else GolfColors.TextPrimary,
            )
            Text(
                text = blurb,
                style = GolfTypography.Status,
                color = GolfColors.TextMuted,
            )
        }
    }
}
