package com.hpsmiles.golfsim.core.physics

/** State of the ball at first ground contact, after fractional-step interpolation. */
data class LandingState(
    val position: Vec3,
    val velocity: Vec3,
    val spin: Vec3,
    val apexM: Double,
    val flightTimeSec: Double,
)
