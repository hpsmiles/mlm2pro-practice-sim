// app/src/main/kotlin/com/hpsmiles/golfsim/range/ClubPickerOverlay.kt
package com.hpsmiles.golfsim.range

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import kotlinx.coroutines.launch

/** Dim scrim over the whole range (mockup club-selector.html). */
private val OverlayScrim = Color(0x88000000)

/**
 * M5 (spec D7, mockup club-selector): full-range club picker overlay.
 * Scrim tap dismisses. The first tile is "—" (untagged — clears the pill);
 * each bag tile makes that club active. The ＋ ADD tile reveals an inline
 * field routed through [onAddClub] — the repository owns the duplicate /
 * blank rules, so a rejected name keeps the form open with a hint.
 */
@Composable
fun ClubPickerOverlay(
    activeClubName: String?,
    clubNames: List<String>,
    onDismiss: () -> Unit,
    onSelectClub: (String?) -> Unit,
    onAddClub: suspend (String) -> Boolean,
) {
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var addRejected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayScrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // Panel swallows the scrim's dismiss click without consuming it
        // beyond its own bounds (enabled=false click still steals the tap).
        Surface(
            shape = RoundedCornerShape(GolfSpacing.CornerCard),
            color = GolfColors.Card,
            modifier = Modifier.clickable(enabled = false) {},
        ) {
            Column(
                modifier = Modifier.padding(GolfSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("ACTIVE CLUB", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
                // "—" (untagged) tile first, then the bag, 4 per row.
                (listOf<String?>(null) + clubNames).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                        row.forEach { name ->
                            ClubTile(
                                label = name ?: "—",
                                active = name == activeClubName,
                                onClick = {
                                    onSelectClub(name)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
                ClubTile(
                    label = if (adding) "×" else "＋ ADD",
                    active = false,
                    onClick = {
                        adding = !adding
                        addRejected = false
                    },
                )
                if (adding) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = {
                                newName = it.take(20)
                                addRejected = false
                            },
                            singleLine = true,
                            modifier = Modifier.width(160.dp),
                        )
                        Text(
                            text = "ADD",
                            style = GolfTypography.MetricLabel,
                            color = GolfColors.Teal,
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        if (onAddClub(newName.trim())) {
                                            adding = false
                                            newName = ""
                                            addRejected = false
                                        } else {
                                            addRejected = true
                                        }
                                    }
                                }
                                .border(1.dp, GolfColors.Teal, RoundedCornerShape(50))
                                .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                        )
                    }
                }
                if (addRejected) {
                    Text(
                        text = if (newName.isBlank()) "ENTER A NAME" else if (newName.contains(',')) "NO COMMAS" else "ALREADY IN BAG",
                        style = GolfTypography.Status,
                        color = GolfColors.AlertRed,
                    )
                }
            }
        }
    }
}

/** 84×72 tile per the D7 mockup; Teal border marks the active club. */
@Composable
private fun ClubTile(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(84.dp, 72.dp)
            .border(1.dp, if (active) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = GolfTypography.Status.copy(fontSize = 16.sp),
            color = if (active) GolfColors.Teal else GolfColors.TextPrimary,
        )
    }
}
