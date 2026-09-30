package com.hpsmiles.golfsim.core.physics

/** Total ground displacement from first bounce to rest. */
data class GroundResult(
    val deltaX: Double,
    val deltaY: Double,
    val bounces: Int,
    val hops: List<GroundHop> = emptyList(),
)
