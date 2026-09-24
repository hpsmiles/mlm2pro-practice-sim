package com.hpsmiles.golfsim.core.physics

/** One point of the modelled flight. tSec is solver time (first sample = 0 at launch). */
data class TrajectorySample(
    val px: Double,
    val py: Double,
    val pz: Double,
    val tSec: Double,
)
