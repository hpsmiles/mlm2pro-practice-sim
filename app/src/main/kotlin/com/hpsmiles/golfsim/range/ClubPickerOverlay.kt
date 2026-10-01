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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/** Dim scrim over the whole range (mockup club-selector.html). */
private val OverlayScrim = Color(0x88000000)

/**
 * M5x E2/E3: club picker over a scrim. Real clubs first (type-first order
 * arrives via the repository), then a trailing amber TEST section for
 * session-scoped clubs. The ＋ ADD tile reveals the shared [AddClubForm].
 */
@Composable
fun ClubPickerOverlay(
    activeClubName: String?,
    clubs: List<ClubRecord>,
    onDismiss: () -> Unit,
    onSelectClub: (String?) -> Unit,
    onAddClub: suspend (String, ClubType, Boolean) -> Boolean,
) {
    var adding by remember { mutableStateOf(false) }
    // The list column caps at 324dp and new clubs append at the end, so a
    // fresh tile rendered below the fold read as "the club never appeared"
    // (user report 2026-10-01). Hoisted scroll state + auto-scroll on growth.
    val listScroll = rememberScrollState()
    var prevClubCount by remember { mutableStateOf(0) }
    LaunchedEffect(clubs.size) {
        if (clubs.size > prevClubCount) {
            // Wait one frame so the grown column is laid out and maxValue is
            // current, then glide to the newly appended tile.
            withFrameNanos { }
            listScroll.animateScrollTo(listScroll.maxValue)
        }
        prevClubCount = clubs.size
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayScrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
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
                val real = clubs.filter { !it.isTemp }
                val test = clubs.filter { it.isTemp }
                Column(
                    modifier = Modifier
                        .heightIn(max = 324.dp)
                        .verticalScroll(listScroll),
                    verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
                ) {
                    (listOf<ClubRecord?>(null) + real).chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                            row.forEach { club ->
                                ClubTile(
                                    label = club?.name ?: "—",
                                    active = club?.name == activeClubName,
                                    onClick = {
                                        onSelectClub(club?.name)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                    // TEST section — only while any exist (spec §8).
                    if (test.isNotEmpty()) {
                        Text(
                            "TEST",
                            style = GolfTypography.MetricLabel,
                            color = GolfColors.Amber,
                            modifier = Modifier.align(Alignment.Start),
                        )
                        test.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                row.forEach { club ->
                                    ClubTile(
                                        label = club.name,
                                        active = club.name == activeClubName,
                                        temp = true,
                                        onClick = {
                                            onSelectClub(club.name)
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                // The row remains a tile so the reveal pattern is unchanged.
                ClubTile(
                    label = if (adding) "×" else "＋ ADD",
                    active = false,
                    onClick = { adding = !adding },
                )
                if (adding) {
                    AddClubForm(
                        onSubmit = onAddClub,
                        onAdded = { adding = false },
                        onCancel = { adding = false },
                    )
                }
            }
        }
    }
}

/**
 * 84×72 tile per the D7 mockup; Teal border marks the active club. M5x:
 * TEST tiles get an amber border + small TEST label.
 */
@Composable
private fun ClubTile(label: String, active: Boolean, onClick: () -> Unit, temp: Boolean = false) {
    val edge = when {
        active -> GolfColors.Teal
        temp -> GolfColors.Amber
        else -> GolfColors.Line
    }
    Box(
        modifier = Modifier
            .size(84.dp, 72.dp)
            .border(1.dp, edge, RoundedCornerShape(GolfSpacing.Sm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = GolfTypography.Status.copy(fontSize = 16.sp),
                color = if (active) GolfColors.Teal else GolfColors.TextPrimary,
            )
            if (temp) {
                Text("TEST", style = GolfTypography.Status.copy(fontSize = 9.sp), color = GolfColors.Amber)
            }
        }
    }
}
