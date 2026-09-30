package com.hpsmiles.golfsim.core.physics

/**
 * One recorded bounce hop (spec 2026-09-30): the landing touch point,
 * CUMULATIVE from the first ground touch (metres; x lateral, y downrange),
 * the hop's apex height, and its air time. The chain of cumulative touches
 * plus the final touch->rest roll reconstructs the full ground displacement.
 */
data class GroundHop(
    val landingX: Double,
    val landingY: Double,
    val apexM: Double,
    val durationSec: Double,
)
