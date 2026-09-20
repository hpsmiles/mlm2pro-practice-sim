package com.hpsmiles.golfsim.range

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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

/** Top-level app frame: nav rail + tab content + status strip. */
@Composable
fun AppRoot() {
    GolfTheme {
        Column(Modifier.fillMaxSize().background(GolfColors.Base)) {
            Row(Modifier.weight(1f)) {
                NavRail {
                    NavRailButton("RANGE", selected = true, onClick = { })
                }
                RangeScreen(Modifier.weight(1f))
            }
            // Phase A status: demo mode. Phase B/C swaps in BLE connection state.
            StatusStrip(armed = true, info = "DEMO MODE - FIRE TO SHOOT")
        }
    }
}
