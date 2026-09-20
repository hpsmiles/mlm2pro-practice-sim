package com.hpsmiles.golfsim.core.physics

/**
 * Measured launch conditions straight from the launch monitor (M4 wires
 * [com.hpsmiles.golfsim.core.ble.BallData] onto this — speeds are already m/s
 * tenths there, no unit conversion drift).
 *
 * Sign conventions (unverified against live capture — M1 spec §9 list):
 * +launchDirDeg pushes toward +x; +spinAxisDeg tilts the spin axis so the
 * ball curves toward +x. Both preserved raw through the decoder.
 */
data class LaunchConditions(
    val ballSpeedMps: Double,
    val launchAngleDeg: Double,
    val spinRpm: Int,
    val launchDirDeg: Double = 0.0,
    val spinAxisDeg: Double = 0.0,
) {
    init {
        // Programmer errors fail fast at the construction boundary (spec §7).
        require(ballSpeedMps in 0.5..100.0) { "ball speed $ballSpeedMps m/s outside [0.5, 100]" }
        require(spinRpm in 0..12000) { "spin $spinRpm rpm outside [0, 12000]" }
        require(launchAngleDeg in 0.0..85.0) { "launch angle $launchAngleDeg deg outside [0, 85]" }
        require(launchDirDeg in -90.0..90.0) { "launch direction $launchDirDeg deg outside [-90, 90]" }
        require(spinAxisDeg in -90.0..90.0) { "spin axis $spinAxisDeg deg outside [-90, 90]" }
    }
}
