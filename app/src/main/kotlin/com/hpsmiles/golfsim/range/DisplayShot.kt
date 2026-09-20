package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult

/**
 * One shot as the range screen holds it: the decoded launch data, the solve
 * inputs used, and the physics result. All UI rendering derives from these.
 */
data class DisplayShot(
    val ballData: BallData,
    val launch: LaunchConditions,
    val shotResult: ShotResult,
)
