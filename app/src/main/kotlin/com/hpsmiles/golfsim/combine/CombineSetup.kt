package com.hpsmiles.golfsim.combine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.data.CombineSessionEntity
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.games.GameCard

@Composable
internal fun CombineSetup(
    combineActive: CombineSessionEntity?,
    takenCount: Int,
    onStart: (CombineCatalogEntry) -> Unit,
    onResume: () -> Unit,
    onDiscardAndStart: (CombineCatalogEntry) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingEntry by remember { mutableStateOf<CombineCatalogEntry?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
    ) {
        Text("COMBINES", style = GolfTypography.ScreenTitle, color = GolfColors.Teal)

        if (combineActive != null) {
            GameCard(
                title = "RESUME ${combineActive.combineName}",
                blurb = "$takenCount/${CombinePlan.SHOTS_TOTAL} shots · pick up where you left off",
                onClick = onResume,
            )
        }

        for (entry in CombineCatalog.entries) {
            GameCard(
                title = entry.name,
                blurb = entry.blurb + " · " + entry.stations.joinToString(" / ") { it.label },
                onClick = {
                    when {
                        combineActive == null -> onStart(entry)
                        combineActive.combineId == entry.id -> onResume()
                        else -> pendingEntry = entry
                    }
                },
            )
        }

        OutlinedButton(onClick = onCancel) { Text("CANCEL") }
    }

    pendingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingEntry = null },
            title = { Text("DISCARD UNFINISHED COMBINE?") },
            text = {
                Text(
                    "Your in-progress ${combineActive?.combineName ?: ""} " +
                        "(${takenCount}/${CombinePlan.SHOTS_TOTAL} shots) will be deleted."
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingEntry = null
                    onDiscardAndStart(entry)
                }) { Text("DISCARD") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingEntry = null }) { Text("KEEP") }
            },
        )
    }
}
