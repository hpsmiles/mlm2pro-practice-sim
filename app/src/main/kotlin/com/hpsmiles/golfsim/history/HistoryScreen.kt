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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.combine.CombineCatalog
import com.hpsmiles.golfsim.combine.CombineFormats
import com.hpsmiles.golfsim.combine.CombinePlan
import com.hpsmiles.golfsim.combine.CombineResultsView
import com.hpsmiles.golfsim.combine.overallEstimate
import com.hpsmiles.golfsim.combine.toFacts
import com.hpsmiles.golfsim.core.data.CombineSessionEntity
import com.hpsmiles.golfsim.core.data.CombineShotEntity
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.data.entity.GameResultEntity
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.SessionSummary
import com.hpsmiles.golfsim.core.data.record.SessionTitles
import com.hpsmiles.golfsim.core.data.record.ShotRecord
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.MetricChip
import com.hpsmiles.golfsim.core.designsystem.NavRailButton
import com.hpsmiles.golfsim.fitting.FittingController
import com.hpsmiles.golfsim.fitting.FittingHistoryRow
import com.hpsmiles.golfsim.fitting.FittingResultsView

private enum class HistoryTab(val label: String) {
    SESSIONS("SESSIONS"),
    GAMES("GAMES"),
    COMBINES("COMBINES"),
    FITTINGS("FITTINGS"),
}

private enum class HistoryMode(val label: String) { ORDER("ORDER"), CLUBS("CLUBS") }
private enum class GameHistoryOrder(val label: String) {
    RECENCY("RECENT"),
    BEST("BEST"),
}

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
    clubs: List<ClubRecord> = emptyList(),
    onToggleExcluded: (Long, Boolean) -> Unit = { _, _ -> },
    gameResults: List<GameResultEntity> = emptyList(),
    combineHistory: List<CombineSessionEntity> = emptyList(),
    combineHistoryShots: List<CombineShotEntity> = emptyList(),
    fittingController: FittingController,
    fittingHistory: List<FittingSessionEntity> = emptyList(),
    fittingHistoryDetails: Map<Long, List<Pair<String, Int>>> = emptyMap(),
    viewedFittingId: Long? = null,
    onOpenFitting: (Long?) -> Unit = {},
    fittingViewedShots: List<FittingShotEntity> = emptyList(),
    onSetExcluded: (List<Long>, Boolean) -> Unit = { _, _ -> },
    onExportFittingPdf: () -> Unit = {},
) {
    var tab by remember { mutableStateOf(HistoryTab.SESSIONS) }
    var viewedCombineId by remember { mutableStateOf<Long?>(null) }

    Box(modifier = modifier.fillMaxSize().background(GolfColors.Base)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Switching sidebar tabs always closes an open read-only view
            // (2026-10-12 spec §1/§4).
            HistorySidebar(
                tab = tab,
                onTabChange = {
                    tab = it
                    viewedCombineId = null
                    onOpenFitting(null)
                },
            )
            when {
                viewedCombineId != null -> {
                    val session = combineHistory.firstOrNull { it.id == viewedCombineId }
                    val entry = session?.let { CombineCatalog.byId(it.combineId) }
                    if (session != null && entry != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Session-context header (Task 13 quality review): the same
                            // CombineResultsView is otherwise indistinguishable across
                            // sessions — mirror FittingResultsView's read-only date line
                            // plus a DEMO marker for demo-sourced sessions.
                            Text(
                                text = GameHistoryFormats.dateTime(session.startedAtMs) +
                                    if (session.source == 1) " · DEMO" else "",
                                style = GolfTypography.BodySmall,
                                color = GolfColors.TextMuted,
                                modifier = Modifier.padding(horizontal = GolfSpacing.Xl, vertical = GolfSpacing.Md),
                            )
                            CombineResultsView(
                                entry = entry,
                                facts = combineHistoryShots.filter { it.sessionId == session.id }.map { it.toFacts() },
                                record = null,
                                readOnly = true,
                                onBack = { viewedCombineId = null },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        // session vanished (discarded): reset after composition, the list renders next
                        LaunchedEffect(Unit) { viewedCombineId = null }
                    }
                }
                viewedFittingId != null -> {
                    // Vanished-session guard (mirror of the combine guard
                    // above): if the viewed session leaves history, reset the
                    // view after composition — the list renders next.
                    LaunchedEffect(viewedFittingId, fittingHistory) {
                        if (fittingHistory.none { it.id == viewedFittingId }) onOpenFitting(null)
                    }
                    // Read-only fitting results fill the content area like the
                    // combine read-only view (2026-10-12 spec §4).
                    FittingResultsView(
                        modifier = Modifier.weight(1f),
                        shots = fittingViewedShots,
                        readOnly = true,
                        controller = fittingController,
                        onBack = { onOpenFitting(null) },
                        onComplete = {},
                        onSetExcluded = onSetExcluded,
                        onExportPdf = onExportFittingPdf,
                    )
                }
                else -> when (tab) {
                    HistoryTab.SESSIONS -> SessionsHistory(
                        summaries = summaries,
                        liveOnly = liveOnly,
                        onToggleLiveOnly = onToggleLiveOnly,
                        selectedSessionId = selectedSessionId,
                        onSelectSession = onSelectSession,
                        shots = shots,
                        clubs = clubs,
                        clubNames = clubNames,
                        onRetag = onRetag,
                        onRenameSession = onRenameSession,
                        onToggleExcluded = onToggleExcluded,
                    )
                    HistoryTab.GAMES -> GamesHistory(
                        gameResults = gameResults,
                        sortModel = GameHistorySorting,
                    )
                    HistoryTab.COMBINES -> CombinesHistory(
                        combineHistory = combineHistory,
                        combineHistoryShots = combineHistoryShots,
                        onOpenCombine = { viewedCombineId = it },
                    )
                    HistoryTab.FITTINGS -> FittingsHistory(
                        fittingHistory = fittingHistory,
                        fittingHistoryDetails = fittingHistoryDetails,
                        onOpenFitting = onOpenFitting,
                    )
                }
            }
        }
    }
}

/**
 * 2026-10-12 spec §1: the sidebar mirrors NavRail — 96 dp (NavRailWidth),
 * Panel background, one rail-sized NavRailButton per tab (same 80×40 dp
 * component as the rail HISTORY button next to it).
 */
@Composable
private fun HistorySidebar(
    tab: HistoryTab,
    onTabChange: (HistoryTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(GolfSpacing.NavRailWidth)
            .fillMaxSize()
            .background(GolfColors.Panel)
            .padding(vertical = GolfSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HistoryTab.entries.forEach { t ->
            NavRailButton(
                label = t.label,
                selected = t == tab,
                onClick = { onTabChange(t) },
            )
        }
    }
}

@Composable
private fun SessionsHistory(
    summaries: List<SessionSummary>,
    liveOnly: Boolean,
    onToggleLiveOnly: () -> Unit,
    selectedSessionId: Long?,
    onSelectSession: (Long) -> Unit,
    shots: List<ShotRecord>,
    clubs: List<ClubRecord>,
    clubNames: List<String>,
    onRetag: (List<Long>, String?) -> Unit,
    onRenameSession: (Long, String) -> Unit,
    onToggleExcluded: (Long, Boolean) -> Unit,
) {
    var selectedIds by remember(selectedSessionId) { mutableStateOf(setOf<Long>()) }
    var retagIndex by remember(selectedSessionId) { mutableStateOf(-1) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val retagChoices = remember(clubNames) { listOf<String?>(null) + clubNames }
    val selected = summaries.firstOrNull { it.id == selectedSessionId }

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
                    clubs = clubs,
                    selectedIds = selectedIds,
                    onSelectionChange = {
                        selectedIds = it
                        if (it.isEmpty()) retagIndex = -1
                    },
                    onRename = {
                        renameText = selected.title ?: ""
                        showRename = true
                    },
                    onToggleExcluded = onToggleExcluded,
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

/**
 * 2026-10-12 spec §2: a per-game switcher tops the panel; below it the
 * existing GAME SCORES header + rows for the selected game only. The COMBINES
 * section moved out to its own sidebar tab (spec §3).
 */
@Composable
private fun GamesHistory(
    gameResults: List<GameResultEntity>,
    sortModel: GameHistorySorting,
) {
    var order by remember { mutableStateOf(GameHistorySorting.Order.RECENCY) }
    var gameMode by remember { mutableStateOf(defaultGameMode(gameResults)) }
    val grouped = remember(gameResults, order) { sortModel.sorted(gameResults, order) }
    val results = grouped[gameMode].orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
    ) {
        // Per-game switcher (spec §2): same chip idiom as the ORDER chips —
        // selected = Teal text on Teal40, unselected = TextMuted + Line border.
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
            listOf(GameModes.TARGET_PRACTICE, GameModes.BREAK_PANE).forEach { mode ->
                val active = mode == gameMode
                Text(
                    text = GameHistoryFormats.modeLabel(mode),
                    style = GolfTypography.Status,
                    color = if (active) GolfColors.Teal else GolfColors.TextMuted,
                    modifier = Modifier
                        .clickable { gameMode = mode }
                        .background(if (active) GolfColors.Teal40 else Color.Transparent, RoundedCornerShape(50))
                        .border(1.dp, if (active) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "GAME SCORES",
                style = GolfTypography.ScreenTitle,
                color = GolfColors.TextPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                GameHistorySorting.Order.entries.forEach { o ->
                    val active = o == order
                    Text(
                        text = o.label,
                        style = GolfTypography.Status,
                        color = if (active) GolfColors.Teal else GolfColors.TextMuted,
                        modifier = Modifier
                            .clickable { order = o }
                            .border(1.dp, if (active) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                            .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                    )
                }
            }
        }

        if (results.isEmpty()) {
            Text("NO GAMES YET", style = GolfTypography.Status, color = GolfColors.TextMuted)
        } else {
            results.forEach { result ->
                GameResultRow(result)
            }
        }
    }
}

/**
 * 2026-10-12 spec §3: combines moved out of the games panel into their own
 * sidebar tab. Complete sessions only (all 24 shots), own RECENT/BEST order
 * chips (BEST = lowest overall HC), tap → read-only CombineResultsView via
 * the existing viewedCombineId flow.
 */
@Composable
private fun CombinesHistory(
    combineHistory: List<CombineSessionEntity>,
    combineHistoryShots: List<CombineShotEntity>,
    onOpenCombine: (Long) -> Unit,
) {
    var order by remember { mutableStateOf(GameHistorySorting.Order.RECENCY) }
    val combineRows = remember(combineHistory, combineHistoryShots, order) {
        combineHistory
            .mapNotNull { session ->
                val entry = CombineCatalog.byId(session.combineId) ?: return@mapNotNull null
                val facts = combineHistoryShots.filter { it.sessionId == session.id }.map { it.toFacts() }
                if (facts.size < CombinePlan.SHOTS_TOTAL) return@mapNotNull null
                session to overallEstimate(entry, facts)
            }
            .let { rows ->
                when (order) {
                    GameHistorySorting.Order.RECENCY -> rows.sortedByDescending { it.first.startedAtMs }
                    GameHistorySorting.Order.SCORE -> rows.sortedBy { it.second } // BEST = lowest overall HC
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "COMBINES",
                style = GolfTypography.ScreenTitle,
                color = GolfColors.TextPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                GameHistorySorting.Order.entries.forEach { o ->
                    val active = o == order
                    Text(
                        text = o.label,
                        style = GolfTypography.Status,
                        color = if (active) GolfColors.Teal else GolfColors.TextMuted,
                        modifier = Modifier
                            .clickable { order = o }
                            .border(1.dp, if (active) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                            .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                    )
                }
            }
        }

        if (combineRows.isEmpty()) {
            Text("NO COMBINES YET", style = GolfTypography.Status, color = GolfColors.TextMuted)
        } else {
            for ((session, est) in combineRows) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GolfColors.Card, RoundedCornerShape(GolfSpacing.Sm))
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
                        .clickable { onOpenCombine(session.id) }
                        .padding(GolfSpacing.Md),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = session.combineName,
                            style = GolfTypography.Body,
                            color = GolfColors.TextPrimary,
                        )
                        Text(
                            text = GameHistoryFormats.dateTime(session.startedAtMs) +
                                if (session.source == 1) " · DEMO" else "",
                            style = GolfTypography.BodySmall,
                            color = GolfColors.TextMuted,
                        )
                    }
                    Text(
                        text = CombineFormats.formatHc(est),
                        style = GolfTypography.MetricValue,
                        color = GolfColors.Teal,
                    )
                }
            }
        }
    }
}

/**
 * 2026-10-12 spec §4: fitting comparison history inside HISTORY. Mirrors
 * FittingHistoryView's rows via the shared FittingHistoryRow, minus the BACK
 * chip — the sidebar is the navigation. Tap → read-only results in place
 * (the viewedFittingId != null branch in HistoryScreen).
 */
@Composable
private fun FittingsHistory(
    fittingHistory: List<FittingSessionEntity>,
    fittingHistoryDetails: Map<Long, List<Pair<String, Int>>>,
    onOpenFitting: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "COMPARISON HISTORY",
                style = GolfTypography.ScreenTitle,
                color = GolfColors.TextPrimary,
            )
        }
        if (fittingHistory.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No completed comparisons yet",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextMuted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
            ) {
                items(fittingHistory, key = { it.id }) { session ->
                    FittingHistoryRow(
                        session = session,
                        details = fittingHistoryDetails[session.id].orEmpty(),
                        onClick = { onOpenFitting(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameResultRow(result: GameResultEntity) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(GolfColors.Card, RoundedCornerShape(GolfSpacing.Sm))
            .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
            .padding(GolfSpacing.Md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = GameHistoryFormats.modeLabel(result.mode),
                style = GolfTypography.Body,
                color = GolfColors.TextPrimary,
            )
            Text(
                text = buildString {
                    append(GameHistoryFormats.targetDistanceM(result.distanceBin))
                    val diff = GameHistoryFormats.difficultyLabel(result.difficulty)
                    if (diff.isNotBlank()) {
                        append(" · ")
                        append(diff)
                    }
                    append(" · ")
                    append(GameHistoryFormats.dateTime(result.playedAtEpochMs))
                },
                style = GolfTypography.BodySmall,
                color = GolfColors.TextMuted,
            )
        }
        Text(
            text = GameHistoryFormats.scoreLabel(result),
            style = GolfTypography.MetricValue,
            color = GolfColors.Teal,
        )
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
    clubs: List<ClubRecord>,
    selectedIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onRename: () -> Unit,
    onToggleExcluded: (Long, Boolean) -> Unit,
) {
    var mode by remember(session.id) { mutableStateOf(HistoryMode.ORDER) }
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.border(1.dp, GolfColors.Line, RoundedCornerShape(50)),
        ) {
            HistoryMode.entries.forEach { m ->
                Text(
                    m.label,
                    style = GolfTypography.Status,
                    color = if (mode == m) GolfColors.Teal else GolfColors.TextMuted,
                    modifier = Modifier
                        .clickable { mode = m }
                        .background(if (mode == m) GolfColors.Card else Color.Transparent)
                        .padding(horizontal = GolfSpacing.Lg, vertical = 4.dp),
                )
            }
        }
        Column(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            HeaderRow()
            if (mode == HistoryMode.ORDER) {
                if (shots.isEmpty()) {
                    Text("NO SHOTS", style = GolfTypography.Status, color = GolfColors.TextMuted)
                }
                shots.forEach { shot ->
                    ShotRow(shot, shot.id in selectedIds, selectedIds, onSelectionChange, onToggleExcluded)
                }
            } else {
                val groups = remember(shots, clubs) { groupByClub(shots, clubs) }
                if (groups.isEmpty()) {
                    Text("NO SHOTS", style = GolfTypography.Status, color = GolfColors.TextMuted)
                }
                groups.forEach { group ->
                    GroupHeaderRow(group)
                    AvgRow(statsFor(group))
                    group.shots.forEach { shot ->
                        ShotRow(shot, shot.id in selectedIds, selectedIds, onSelectionChange, onToggleExcluded)
                    }
                }
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
private fun HeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val cols = listOf(
            "#" to 36.dp, "CARRY" to 80.dp, "TOTAL" to 80.dp, "SIDE" to 84.dp, "APEX" to 80.dp,
            "BALL" to 88.dp, "CLUB" to 76.dp, "SMASH" to 64.dp, "LAUNCH" to 72.dp,
            "DIR" to 72.dp,
            "AXIS" to 72.dp, "SPIN" to 96.dp,
        )
        cols.forEach { (label, w) -> HeaderCell(label, w) }
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun GroupHeaderRow(group: ClubGroup) {
    val stats = statsFor(group)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        modifier = Modifier.padding(top = GolfSpacing.Sm),
    ) {
        Text(
            HistoryFormats.clubOrDash(group.clubName),
            style = GolfTypography.MetricLabel,
            color = GolfColors.TextSecondary,
        )
        Text("${group.shots.size} SHOTS", style = GolfTypography.Status, color = GolfColors.TextMuted)
        Text("σ CARRY ${HistoryFormats.sigma(stats.sigmaCarryM)}", style = GolfTypography.Status, color = GolfColors.TextMuted)
        Text("σ BALL ${HistoryFormats.sigma(stats.sigmaBallMph)}", style = GolfTypography.Status, color = GolfColors.TextMuted)
        if (group.wasTemp) Badge("TEST", GolfColors.Amber)
    }
}

@Composable
private fun AvgRow(stats: ClubStats) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("AVG", style = GolfTypography.MetricLabel, color = GolfColors.Teal, modifier = Modifier.width(36.dp))
        AvgCell(HistoryFormats::carry, stats.avgCarryM, 80.dp)
        AvgCell(HistoryFormats::total, stats.avgTotalM, 80.dp)
        AvgCell(HistoryFormats::side, stats.avgSideM, 84.dp)
        AvgCell(HistoryFormats::apex, stats.avgApexM, 80.dp)
        AvgCell(HistoryFormats::ballMph, stats.avgBallMph, 88.dp)
        AvgCell(HistoryFormats::clubMph, stats.avgClubMph, 76.dp)
        val smash = stats.avgSmash
        Text(
            text = smash?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "-",
            style = GolfTypography.Body,
            color = GolfColors.Teal,
            modifier = Modifier.width(64.dp),
        )
        AvgCell(HistoryFormats::launch, stats.avgLaunchDeg, 72.dp)
        AvgCell(HistoryFormats::dir, stats.avgDirDeg, 72.dp)
        AvgCell(HistoryFormats::axis, stats.avgAxisDeg, 72.dp)
        AvgCell(HistoryFormats::avgSpin, stats.avgSpinRpm, 96.dp)
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun AvgCell(format: (Double) -> String, value: Double?, width: Dp) {
    Text(
        text = value?.let(format) ?: "-",
        style = GolfTypography.Body,
        color = GolfColors.Teal,
        modifier = Modifier.width(width),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShotRow(
    shot: ShotRecord,
    selected: Boolean,
    selectedIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onToggleExcluded: (Long, Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .combinedClickable(
                onClick = {
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
        ShotCell("${shot.seq + 1}", 36.dp, shot.excluded)
        ShotCell(HistoryFormats.carry(shot.carryM), 80.dp, shot.excluded)
        ShotCell(HistoryFormats.total(shot.totalM), 80.dp, shot.excluded)
        ShotCell(HistoryFormats.side(shot.sideM), 84.dp, shot.excluded)
        ShotCell(HistoryFormats.apex(shot.apexM), 80.dp, shot.excluded)
        ShotCell(HistoryFormats.ballMph(shot.ballData.ballSpeed), 88.dp, shot.excluded)
        ShotCell(HistoryFormats.clubMph(shot.ballData.clubHeadSpeed), 76.dp, shot.excluded)
        ShotCell(HistoryFormats.smash(shot.ballData.ballSpeed, shot.ballData.clubHeadSpeed), 64.dp, shot.excluded)
        ShotCell(HistoryFormats.launch(shot.ballData.launchAngle), 72.dp, shot.excluded)
        ShotCell(HistoryFormats.dir(shot.ballData.launchDirection), 72.dp, shot.excluded)
        ShotCell(HistoryFormats.axis(shot.ballData.spinAxis), 72.dp, shot.excluded)
        ShotCell(HistoryFormats.spin(shot.ballData.totalSpin), 96.dp, shot.excluded)
        Checkbox(
            checked = shot.excluded,
            onCheckedChange = { onToggleExcluded(shot.id, it) },
            colors = CheckboxDefaults.colors(
                checkedColor = GolfColors.Amber,
                checkmarkColor = GolfColors.Base,
                uncheckedColor = if (shot.excluded) GolfColors.Amber else GolfColors.TextMuted,
            ),
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun ShotCell(text: String, width: Dp, excluded: Boolean) {
    Text(
        text = text,
        style = GolfTypography.Body,
        color = if (excluded) GolfColors.TextMuted else GolfColors.TextPrimary,
        textDecoration = if (excluded) TextDecoration.LineThrough else null,
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
