package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing

/**
 * FIT results view (design notes §2): TABLE | TOP-DOWN switch, metric table
 * with frozen CLUB column + delta rows, and the coloured top-down with
 * dispersion ovals. Read-only when [readOnly] (a viewed history session).
 * Stub — full body lands in task 8.
 */
@Composable
internal fun FittingResultsView(
    modifier: Modifier = Modifier,
    shots: List<FittingShotEntity>,
    readOnly: Boolean,
    controller: FittingController,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onSetExcluded: (List<Long>, Boolean) -> Unit,
) {
    Text("RESULTS — task 8", modifier = modifier.padding(GolfSpacing.Md))
}
