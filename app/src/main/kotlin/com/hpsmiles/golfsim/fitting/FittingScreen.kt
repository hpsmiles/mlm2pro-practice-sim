package com.hpsmiles.golfsim.fitting

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.range.DisplayShot

/**
 * M7 FIT tab (spec §1). Plain state dispatcher — shot/session truth is
 * Room; [FittingController] holds only view/options state.
 */
@Composable
fun FittingScreen(
    modifier: Modifier = Modifier,
    controller: FittingController,
    clubs: List<ClubRecord>,
    activeClubName: String?,
    activeSession: FittingSessionEntity?,
    history: List<FittingSessionEntity>,
    historyDetails: Map<Long, List<Pair<String, Int>>>,
    liveShots: List<DisplayShot>,
    sessionShots: List<FittingShotEntity>,
    viewedShots: List<FittingShotEntity>,
    armed: Boolean,
    info: String,
    onSelectClub: (String?) -> Unit,
    onAddClub: suspend (String, ClubType, Boolean) -> Boolean,
    onComplete: () -> Unit,
    onSetExcluded: (List<Long>, Boolean) -> Unit,
) {
    when (controller.view) {
        FittingView.COMPARE -> FittingCompareView(
            modifier = modifier,
            clubs = clubs,
            activeClubName = activeClubName,
            sessionShots = sessionShots,
            liveShots = liveShots,
            armed = armed,
            info = info,
            distanceMode = controller.distanceMode,
            historyCount = history.size,
            onDistanceModeChange = { controller.distanceMode = it },
            onSelectClub = onSelectClub,
            onAddClub = onAddClub,
            onViewResults = { controller.view = FittingView.RESULTS },
            onOpenHistory = { controller.view = FittingView.HISTORY },
        )
        FittingView.RESULTS -> {
            val readOnly = controller.viewedSessionId != null
            FittingResultsView(
                modifier = modifier,
                shots = if (readOnly) viewedShots else sessionShots,
                readOnly = readOnly,
                controller = controller,
                onBack = {
                    if (controller.viewedSessionId != null) {
                        controller.viewedSessionId = null
                        controller.view = FittingView.HISTORY
                    } else {
                        controller.view = FittingView.COMPARE
                    }
                },
                onComplete = onComplete,
                onSetExcluded = onSetExcluded,
            )
        }
        FittingView.HISTORY -> FittingHistoryView(
            modifier = modifier,
            history = history,
            historyDetails = historyDetails,
            onOpen = { id ->
                controller.viewedSessionId = id
                controller.view = FittingView.RESULTS
            },
            onBack = { controller.view = FittingView.COMPARE },
        )
    }
}
