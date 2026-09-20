package com.hpsmiles.golfsim.core.physics

/** Ball + world physical constants (R&A/USGA maximums; SI). Spec §6. */
object BallPhysical {
    const val MASS_KG = 0.04593
    const val RADIUS_M = 0.021335
    const val DIAMETER_M = 0.04267
    val AREA_M2 = Math.PI * RADIUS_M * RADIUS_M
    const val GRAVITY_MPS2 = 9.81
    const val MU_AIR = 1.7894e-5
    const val METERS_PER_YARD = 0.9144
}
