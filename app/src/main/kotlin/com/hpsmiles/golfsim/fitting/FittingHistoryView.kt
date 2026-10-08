package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing

/**
 * FIT history view (design notes §3): completed comparisons, newest first,
 * date + club-colour-dotted names + shot count + VIEW. Opening a row shows
 * the read-only RESULTS. Stub — full body lands in task 10.
 */
@Composable
internal fun FittingHistoryView(
    modifier: Modifier = Modifier,
    history: List<FittingSessionEntity>,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Text("HISTORY — task 10", modifier = modifier.padding(GolfSpacing.Md))
}
