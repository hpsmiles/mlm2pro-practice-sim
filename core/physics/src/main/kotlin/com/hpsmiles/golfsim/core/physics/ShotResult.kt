package com.hpsmiles.golfsim.core.physics

/**
 * End-to-end shot outcome, SI, full precision (display converts at M3+).
 * rolloutM = totalM - carryM (can be negative — spin-back on soft greens).
 * sideM: +x is right of the target line looking down-range.
 * restX/restY = bounce/roll end position (restX == sideM, hypot(restX, restY) == totalM).
 * groundHops = recorded bounce chain (cumulative touches relative to the
 * first ground touch) for hop-aware ground rendering; empty for replayed
 * persisted shots and shots whose first impact stays on the ground.
 */
data class ShotResult(
    val carryM: Double,
    val rolloutM: Double,
    val totalM: Double,
    val sideM: Double,
    val apexM: Double,
    val flightTimeSec: Double,
    val samples: List<TrajectorySample> = emptyList(),
    val restX: Double = 0.0,
    val restY: Double = 0.0,
    val groundHops: List<GroundHop> = emptyList(),
)
