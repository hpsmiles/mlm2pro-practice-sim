package com.hpsmiles.golfsim.bag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType

/**
 * BAG tab screen (spec §3): in-screen when-enum over Room-derived state —
 * no SharedPreferences, no in-memory-only state. State selection:
 * in-progress + no result → COLLECTING; in-progress + result → RESULT with
 * resume banner; result only → RESULT; neither → INTRO; HISTORY reachable
 * from RESULT. Reports whether the COLLECTING view is up so AppRoot can
 * route shots (same seam as the games' onActiveGameChange).
 */
private enum class BagView { AUTO, INTRO, COLLECTING, RESULT, HISTORY }

@Composable
fun BagMappingScreen(
    clubs: List<ClubRecord>,
    activeSession: BagMappingSessionEntity?,
    latestCompleted: BagMappingSessionEntity?,
    history: List<BagMappingSessionEntity>,
    shotCounts: Map<Long, Int>,
    activeShots: List<BagMappingShotEntity>,
    viewedShots: List<BagMappingShotEntity>,
    collector: BagMappingCollector,
    onViewedSessionChange: (Long?) -> Unit,
    onCollectingChange: (Boolean) -> Unit,
    onStartTest: (BagOrderMode) -> Unit,
    onOpenSettings: () -> Unit,
    onCompleteSession: () -> Unit,
    modifier: Modifier = Modifier,
    latestShot: com.hpsmiles.golfsim.range.DisplayShot? = null,
) {
    var view by remember { mutableStateOf(BagView.AUTO) }
    var viewingHistory by remember { mutableStateOf<BagMappingSessionEntity?>(null) }

    // Spec §3 selection table. AUTO defers entirely to Room state.
    val autoView = when {
        activeSession != null && latestCompleted == null -> BagView.COLLECTING
        latestCompleted != null -> BagView.RESULT
        else -> BagView.INTRO
    }
    val resolvedView = when (view) {
        BagView.AUTO -> autoView
        BagView.INTRO -> if (activeSession == null) BagView.INTRO else autoView
        BagView.COLLECTING -> if (activeSession != null) BagView.COLLECTING else autoView
        BagView.RESULT -> if (latestCompleted != null || viewingHistory != null) BagView.RESULT else autoView
        BagView.HISTORY -> BagView.HISTORY
    }

    // Shot routing seam (games pattern): report whether collection is live.
    LaunchedEffect(resolvedView) {
        onCollectingChange(resolvedView == BagView.COLLECTING)
    }
    // Self-contained seam: leaving the BAG tab disposes this composition and
    // cancels the LaunchedEffect above before its body runs, so bagCollecting
    // would stick true. Report collection-off on dispose (capture pauses;
    // progress persists — Room truth survives).
    DisposableEffect(Unit) {
        onDispose { onCollectingChange(false) }
    }

    // The session whose shots are on screen: active while collecting,
    // completed on the result, historical in read-only. AppRoot collects the
    // flow for exactly this id (historySelectedId pattern).
    val displaySession = when (resolvedView) {
        BagView.COLLECTING -> activeSession
        BagView.RESULT -> viewingHistory ?: latestCompleted
        else -> null
    }
    // Key on both id and view: the COLLECTING→RESULT transition keeps the same
    // session id, so an id-only key would never re-run and the RESULT view
    // would bind no shots. While COLLECTING the active session is already
    // observed via bagActiveShots — skip the duplicate viewed-id report.
    LaunchedEffect(displaySession?.id, resolvedView) {
        if (resolvedView != BagView.COLLECTING) {
            onViewedSessionChange(displaySession?.id)
        }
    }

    when (resolvedView) {
        BagView.INTRO -> BagMappingIntro(
            eligibleClubs = clubs.filter { !it.isTemp && it.type != ClubType.PUTTER },
            onOpenSettings = onOpenSettings,
            // Intro selector carries the mode (wedge-first default, spec item 2).
            onStartTest = { onStartTest(it) },
            modifier = modifier,
        )
        BagView.COLLECTING -> BagMappingCollecting(
            collector = collector,
            activeShots = activeShots,
            onCompleteSession = onCompleteSession,
            modifier = modifier,
            latestShot = latestShot,
        )
        BagView.RESULT -> {
            val session = viewingHistory ?: latestCompleted
            if (session != null) {
                BagMappingResult(
                    session = session,
                    shots = viewedShots,
                    isStaleResult = activeSession != null && viewingHistory == null,
                    onRetest = { onStartTest(BagOrderMode.WEDGE_FIRST) },
                    onResume = { view = BagView.COLLECTING },
                    onOpenHistory = { view = BagView.HISTORY },
                    modifier = modifier,
                )
            }
        }
        BagView.HISTORY -> BagMappingHistory(
            history = history,
            shotCounts = shotCounts,
            onOpen = { viewingHistory = it; view = BagView.RESULT },
            onBack = {
                viewingHistory = null
                view = BagView.AUTO
            },
            modifier = modifier,
        )
        BagView.AUTO -> Unit // unreachable: AUTO resolves above
    }
}
