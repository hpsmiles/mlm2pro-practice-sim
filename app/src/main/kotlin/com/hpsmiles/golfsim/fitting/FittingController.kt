package com.hpsmiles.golfsim.fitting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** FIT tab views (spec §1): live compare → results (table + top-down) → history. */
enum class FittingView { COMPARE, RESULTS, HISTORY }

/** Carry/total toggle for the comparison card + table distance columns. */
enum class FittingDistanceMode(val label: String) { CARRY("CARRY"), TOTAL("TOTAL") }

/** RESULTS screen switch: metric table vs coloured top-down. */
enum class FittingResultsMode(val label: String) { TABLE("TABLE"), TOP_DOWN("TOP-DOWN") }

/**
 * FIT tab UI state (M7 spec §1). Plain state holder — no ViewModels. All
 * shot/session truth lives in Room; this holds only which view is up and
 * table options. [sessionId] binds the active fitting session (0 = none
 * yet; auto-started by the first routed shot).
 */
class FittingController {
    var view by mutableStateOf(FittingView.COMPARE)
    var resultsMode by mutableStateOf(FittingResultsMode.TABLE)
    var distanceMode by mutableStateOf(FittingDistanceMode.CARRY)
    /** Null = deltas auto-pair (2 clubs) or default to first club (3+). */
    var baselineClubId by mutableStateOf<Long?>(null)
    /** Non-null = read-only RESULTS of a completed history session. */
    var viewedSessionId by mutableStateOf<Long?>(null)
    var sessionId: Long = 0
}
