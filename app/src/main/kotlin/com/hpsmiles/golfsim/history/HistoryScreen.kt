// app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryScreen.kt
package com.hpsmiles.golfsim.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.data.record.SessionSummary
import com.hpsmiles.golfsim.core.data.record.SessionTitles
import com.hpsmiles.golfsim.core.data.record.ShotRecord
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.MetricChip

/**
 * M5 (spec §6 D5/D6, mockups review-layout + retag-interaction):
 * master-detail session history. Left: session summary list with the
 * LIVE ONLY / ALL toggle (backed by the repository's liveOnly flow).
 * Right: stat chips + shot table; long-press a row to start multi-select,
 * then pick a club chip (or "—") and APPLY to retag. Tapping the session
 * title opens a rename dialog.
 */
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    summaries: List<SessionSummary>,
    liveOnly: Boolean,
    onToggleLiveOnly: () -> Unit,
    shots: List<ShotRecord>,
    selectedSessionId: Long?,
    onSelectSession: (Long) -> Unit,
    clubNames: List<String>,
    onRetag: (List<Long>, String?) -> Unit,
    onRenameSession: (Long, String) -> Unit,
) {
    // Selection + retag choice reset whenever the viewed session changes.
    var selectedIds by remember(selectedSessionId) { mutableStateOf(setOf<Long>()) }
    var retagIndex by remember(selectedSessionId) { mutableStateOf(-1) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val retagChoices = remember(clubNames) { listOf<String?>(null) + clubNames }
    val selected = summaries.firstOrNull { it.id == selectedSessionId }

    Box(modifier = modifier.fillMaxSize().background(GolfColors.Base)) {
        Row(modifier = Modifier.fillMaxSize()) {
            SessionList(summaries, selectedSessionId, liveOnly, onToggleLiveOnly, onSelectSession)
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                if (selected == null) {
                    Text(
                        text = "SELECT A SESSION",
                        style = GolfTypography.Status,
                        color = GolfColors.TextMuted,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    SessionDetail(
                        session = selected,
                        shots = shots,
                        selectedIds = selectedIds,
                        onSelectionChange = { selectedIds = it },
                        onRename = {
                            renameText = selected.title ?: ""
                            showRename = true
                        },
                    )
                }
                if (selectedIds.isNotEmpty()) {
                    RetagBar(
                        count = selectedIds.size,
                        choices = retagChoices,
                        choiceIndex = retagIndex,
                        onChoose = { retagIndex = it },
                        onApply = {
                            onRetag(selectedIds.toList(), retagChoices.getOrNull(retagIndex))
                            selectedIds = emptySet()
                            retagIndex = -1
                        },
                        onCancel = {
                            selectedIds = emptySet()
                            retagIndex = -1
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        if (showRename && selected != null) {
            AlertDialog(
                onDismissRequest = { showRename = false },
                title = { Text("RENAME SESSION") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it.take(40) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRenameSession(selected.id, renameText.trim())
                            showRename = false
                        },
                    ) { Text("CONFIRM") }
                },
                dismissButton = {
                    TextButton(onClick = { showRename = false }) { Text("CANCEL") }
                },
            )
        }
    }
}

@Composable
private fun SessionList(
    summaries: List<SessionSummary>,
    selectedSessionId: Long?,
    liveOnly: Boolean,
    onToggleLiveOnly: () -> Unit,
    onSelectSession: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxSize()
            .background(GolfColors.Panel)
            .verticalScroll(rememberScrollState())
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
            listOf("LIVE ONLY" to liveOnly, "ALL" to !liveOnly).forEach { (label, active) ->
                Text(
                    text = label,
                    style = GolfTypography.Status,
                    color = if (active) GolfColors.Teal else GolfColors.TextMuted,
                    modifier = Modifier
                        .clickable { if (!active) onToggleLiveOnly() }
                        .border(1.dp, if (active) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                )
            }
        }
        if (summaries.isEmpty()) {
            Text("NO SESSIONS YET", style = GolfTypography.Status, color = GolfColors.TextMuted)
        }
        summaries.forEach { s ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSession(s.id) }
                    .background(if (s.id == selectedSessionId) GolfColors.Base else Color.Transparent)
                    .border(
                        1.dp,
                        if (s.id == selectedSessionId) GolfColors.Teal else GolfColors.Line,
                        RoundedCornerShape(GolfSpacing.Sm),
                    )
                    .padding(GolfSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = s.title ?: SessionTitles.auto(s.startedAtEpochMs),
                        style = GolfTypography.Body,
                        color = GolfColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (s.isOpen) {
                        Badge("OPEN", GolfColors.Teal)
                        Spacer(Modifier.width(GolfSpacing.Xs))
                    }
                    if (s.hasDemoShot) {
                        Badge("DEMO", GolfColors.Amber)
                    }
                }
                Text(
                    text = HistoryFormats.sessionMeta(s),
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun Badge(label: String, color: Color) {
    Text(
        text = label,
        style = GolfTypography.Status.copy(fontSize = 11.sp),
        color = color,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = GolfSpacing.Sm, vertical = 1.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDetail(
    session: SessionSummary,
    shots: List<ShotRecord>,
    selectedIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onRename: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        Text(
            text = session.title ?: SessionTitles.auto(session.startedAtEpochMs),
            style = GolfTypography.ScreenTitle,
            color = GolfColors.TextPrimary,
            modifier = Modifier.clickable(onClick = onRename),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            MetricChip("shots", "${session.shotCount}", null)
            MetricChip("misreads", "${session.misreadCount}", null, accent = GolfColors.AlertRed)
            session.avgCarryM?.let {
                MetricChip("avg carry", String.format(java.util.Locale.US, "%.0f", it), "M")
            }
            session.maxCarryM?.let {
                MetricChip("longest", String.format(java.util.Locale.US, "%.0f", it), "M")
            }
        }
        // Shot table (D5): # CLUB CARRY SIDE BALL SPIN.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("#", 36.dp)
            HeaderCell("CLUB", 64.dp)
            HeaderCell("CARRY", 80.dp)
            HeaderCell("SIDE", 84.dp)
            HeaderCell("BALL", 88.dp)
            HeaderCell("SPIN", 104.dp)
        }
        shots.forEach { shot ->
            val selected = shot.id in selectedIds
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            // D6: tap toggles membership only while a
                            // selection is active (long-press starts it).
                            if (selectedIds.isNotEmpty()) {
                                onSelectionChange(
                                    if (selected) selectedIds - shot.id else selectedIds + shot.id,
                                )
                            }
                        },
                        onLongClick = {
                            if (!selected) onSelectionChange(selectedIds + shot.id)
                        },
                    )
                    .background(if (selected) GolfColors.Panel else Color.Transparent)
                    .border(
                        1.dp,
                        if (selected) GolfColors.Teal else Color.Transparent,
                        RoundedCornerShape(GolfSpacing.Sm),
                    )
                    .padding(vertical = 4.dp),
            ) {
                Cell("${shot.seq + 1}", 36.dp)
                Cell(HistoryFormats.clubOrDash(shot.clubName), 64.dp)
                Cell(HistoryFormats.carry(shot.carryM), 80.dp)
                Cell(HistoryFormats.side(shot.sideM), 84.dp)
                Cell(HistoryFormats.ballMph(shot.ballData.ballSpeed), 88.dp)
                Cell(HistoryFormats.spin(shot.ballData.totalSpin), 104.dp)
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        style = GolfTypography.MetricLabel,
        color = GolfColors.TextMuted,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun Cell(text: String, width: Dp) {
    Text(
        text = text,
        style = GolfTypography.Body,
        color = GolfColors.TextPrimary,
        modifier = Modifier.width(width),
    )
}

/** Bottom bar while rows are selected: club chips + APPLY / CANCEL (D6). */
@Composable
private fun RetagBar(
    count: Int,
    choices: List<String?>,
    choiceIndex: Int,
    onChoose: (Int) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(GolfSpacing.CornerCard),
        color = GolfColors.Card,
        modifier = modifier.padding(GolfSpacing.Md),
    ) {
        Column(
            modifier = Modifier.padding(GolfSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                choices.forEachIndexed { i, name ->
                    Text(
                        text = name ?: "—",
                        style = GolfTypography.Status.copy(fontSize = 15.sp),
                        color = if (i == choiceIndex) GolfColors.Teal else GolfColors.TextPrimary,
                        modifier = Modifier
                            .clickable { onChoose(i) }
                            .border(
                                1.dp,
                                if (i == choiceIndex) GolfColors.Teal else GolfColors.Line,
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
            ) {
                Text(
                    text = "RETAG $count",
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "CANCEL",
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.TextSecondary,
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                )
                val canApply = choiceIndex >= 0
                Text(
                    text = "APPLY",
                    style = GolfTypography.MetricLabel,
                    color = if (canApply) GolfColors.Teal else GolfColors.TextMuted,
                    modifier = Modifier
                        .clickable { if (canApply) onApply() }
                        .border(
                            1.dp,
                            if (canApply) GolfColors.Teal else GolfColors.Line,
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                )
            }
        }
    }
}
