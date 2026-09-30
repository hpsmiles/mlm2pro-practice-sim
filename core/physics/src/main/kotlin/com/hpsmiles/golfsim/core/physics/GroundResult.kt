package com.hpsmiles.golfsim.core.physics

/** Total ground displacement from first bounce to rest. */
data class GroundResult(
    val deltaX: Double,
    val deltaY: Double,
    val bounces: Int,
    /** Recorded hop chain (spec 2026-09-30); empty when the first impact never leaves the ground. */
    val hops: List<GroundHop> = emptyList(),
)
