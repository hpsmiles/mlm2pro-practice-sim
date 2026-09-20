package com.hpsmiles.golfsim.core.physics

/**
 * End-to-end shot outcome, SI, full precision (display converts at M3+).
 * rolloutM = totalM - carryM (can be negative — spin-back on soft greens).
 * sideM: +x is right of the target line looking down-range.
 */
data class ShotResult(
    val carryM: Double,
    val rolloutM: Double,
    val totalM: Double,
    val sideM: Double,
    val apexM: Double,
    val flightTimeSec: Double,
)
