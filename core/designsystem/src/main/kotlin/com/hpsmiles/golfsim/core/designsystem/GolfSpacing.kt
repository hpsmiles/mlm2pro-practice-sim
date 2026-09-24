// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfSpacing.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.ui.unit.dp

/** Spacing scale and component dimensions (M3 spec §5). */
object GolfSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp

    // Wide enough for the longest rail label ("SETTINGS") on one line —
    // the NavRailButton inner box is NavRailWidth - 2 * Sm.
    // Widened 2026-09-24 (user request) so RailChip labels fit at 15 sp.
    val NavRailWidth = 96.dp
    val StatusStripHeight = 24.dp
    val CornerCard = 14.dp
}
