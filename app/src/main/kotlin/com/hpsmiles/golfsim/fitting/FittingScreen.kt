package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
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
    Text("M7 FIT — screens land in tasks 7–10", modifier = modifier.padding(GolfSpacing.Md))
}
