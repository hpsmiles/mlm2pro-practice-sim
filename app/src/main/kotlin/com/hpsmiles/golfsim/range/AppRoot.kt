// app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
package com.hpsmiles.golfsim.range

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfTheme
import com.hpsmiles.golfsim.core.designsystem.NavRail
import com.hpsmiles.golfsim.core.designsystem.NavRailButton
import com.hpsmiles.golfsim.core.designsystem.StatusStrip
import com.hpsmiles.golfsim.settings.SettingsScreen

private enum class RangeTab { RANGE, SETTINGS }

@Composable
fun AppRoot() {
    var tab by remember { mutableStateOf(RangeTab.RANGE) }
    GolfTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GolfColors.Base)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(modifier = Modifier.weight(1f)) {
                NavRail {
                    // Mechanical: NavRailButton(label, selected, onClick, modifier) —
                    // a trailing lambda would bind to `modifier`, so name onClick.
                    NavRailButton("RANGE", tab == RangeTab.RANGE, onClick = { tab = RangeTab.RANGE })
                    NavRailButton("SETTINGS", tab == RangeTab.SETTINGS, onClick = { tab = RangeTab.SETTINGS })
                }
                when (tab) {
                    RangeTab.RANGE -> RangeScreen()
                    RangeTab.SETTINGS -> SettingsScreen()
                }
            }
            StatusStrip(
                armed = true,
                info = if (tab == RangeTab.RANGE) "DEMO MODE - FIRE TO SHOOT" else "SETTINGS",
            )
        }
    }
}
