// app/src/main/kotlin/com/hpsmiles/golfsim/range/AddClubForm.kt
package com.hpsmiles.golfsim.range

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import kotlinx.coroutines.launch

/**
 * M5x E3 compact inline add-club form (mockup add-club-form): TYPE ▾ ·
 * name · TEST checkbox in ONE row; CANCEL + ADD CLUB beneath. Shared by the
 * range club picker and Settings ▸ BAG so validation UX is identical. The
 * repository owns the reject rules; a rejected name keeps the form open with
 * a hint. TEST clubs are session-scoped (amber, "session only" caption).
 * On success the chosen TYPE is deliberately KEPT while name/isTest/rejected
 * reset — the dominant flow is successive adds within one type group (e.g.
 * bag-mapping the wedges), so re-picking the type every time would be noise.
 */
@Composable
fun AddClubForm(
    onSubmit: suspend (name: String, type: ClubType, isTest: Boolean) -> Boolean,
    onAdded: () -> Unit,
    onCancel: (() -> Unit)? = null,
    initialTest: Boolean = false,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ClubType.IRON) }
    // M7: the FIT picker pre-checks TEST (spec §3); the range default (false)
    // keeps existing add behaviour byte-identical.
    var isTest by remember { mutableStateOf(initialTest) }
    var rejected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            // TYPE dropdown — one shared control, 7 entries.
            Box {
                var open by remember { mutableStateOf(false) }
                Text(
                    text = "${type.label.uppercase()} ▾",
                    style = GolfTypography.Status.copy(fontSize = 12.sp),
                    color = GolfColors.TextSecondary,
                    modifier = Modifier
                        .clickable { open = true }
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Sm, vertical = 6.dp),
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    ClubType.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.label, style = GolfTypography.Body) },
                            onClick = { type = t; open = false; rejected = false },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(20)
                    rejected = false
                },
                singleLine = true,
                modifier = Modifier.width(160.dp),
            )
            // TEST checkbox, amber when on; tiny caption beneath.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isTest,
                        onCheckedChange = { isTest = it; rejected = false },
                        colors = CheckboxDefaults.colors(
                            checkedColor = GolfColors.Amber,
                            checkmarkColor = GolfColors.Base,
                            uncheckedColor = GolfColors.TextMuted,
                        ),
                    )
                    Text(
                        "TEST",
                        style = GolfTypography.Status.copy(fontSize = 11.sp),
                        color = if (isTest) GolfColors.Amber else GolfColors.TextMuted,
                    )
                }
                Text("session only", style = GolfTypography.Status.copy(fontSize = 9.sp), color = GolfColors.Amber)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            if (onCancel != null) {
                Text(
                    text = "CANCEL",
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.TextSecondary,
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                )
            }
            Text(
                text = "ADD CLUB",
                style = GolfTypography.MetricLabel,
                color = GolfColors.Teal,
                modifier = Modifier
                    .clickable {
                        scope.launch {
                            if (onSubmit(name.trim(), type, isTest)) {
                                name = ""
                                isTest = false
                                rejected = false
                                onAdded()
                            } else {
                                rejected = true
                            }
                        }
                    }
                    .border(1.dp, GolfColors.Teal, RoundedCornerShape(50))
                    .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
            )
        }
        if (rejected) {
            Text(
                text = if (name.isBlank()) "ENTER A NAME" else if (name.contains(',')) "NO COMMAS" else "ALREADY IN BAG",
                style = GolfTypography.Status,
                color = GolfColors.AlertRed,
            )
        }
    }
}
