package com.hpsmiles.golfsim.core.physics

/**
 * Ground-contact parameters for one turf (spec §6 table).
 *
 * The firmness modifiers are multiplicative ratios derived from the
 * openfairway FairwaySoft/Fairway column pairs (roll mu x1.40 soft / x0.60
 * firm; theta_crit x0.32/0.29 soft / x0.25/0.29 firm; spin scale x0.92/0.78
 * soft / x0.60/0.78 firm) plus a small named COR delta (x0.92 soft / x1.08
 * firm — our choice, spec §6). The catalog's mu_kinetic column is NOT
 * represented: the shipped ground model uses openfairway's retention-based
 * shallow-bounce law (spec §5 amendment), so nothing consumes it (YAGNI).
 *
 * Green roll is stimp-anchored: decel = 5.49/stimp m/s^2 (USGA Stimpmeter
 * release speed 1.83 m/s). Fairway/rough roll: decel = mu x g.
 */
data class Surface(
    val name: String,
    /** Base COR scale relative to the fairway parabola reference (0.40). */
    val cor: Double,
    /** Critical impact angle for the Penner spin-back branch (rad). */
    val thetaCritRad: Double,
    /** Spin-back impulse scale in the Penner branch (fairway tuned to 0.35). */
    val spinbackScale: Double,
    /** Spin retained after each bounce (fairway 0.75, green 0.85). */
    val spinRetention: Double,
    /** Constant roll deceleration on this surface (m/s^2). */
    val rollDecelMps2: Double,
) {
    fun withFirmness(firmness: Firmness): Surface = when (firmness) {
        Firmness.SOFT -> copy(
            name = "$name (soft)",
            cor = cor * 0.92,
            thetaCritRad = thetaCritRad * (0.32 / 0.29),
            spinbackScale = spinbackScale * (0.92 / 0.78),
            rollDecelMps2 = rollDecelMps2 * 1.40,
        )
        Firmness.NORMAL -> this
        Firmness.FIRM -> copy(
            name = "$name (firm)",
            cor = cor * 1.08,
            thetaCritRad = thetaCritRad * (0.25 / 0.29),
            spinbackScale = spinbackScale * (0.60 / 0.78),
            rollDecelMps2 = rollDecelMps2 * 0.60,
        )
    }

    companion object {
        fun green(stimp: Double = 11.0): Surface = Surface(
            name = "green",
            cor = 0.45,
            thetaCritRad = 0.36,
            spinbackScale = 1.12,
            spinRetention = 0.85,
            rollDecelMps2 = 5.49 / stimp,
        )

        /** Fairway with M2-tuned roll mu (0.030) and spin-back scale (0.35). */
        val FAIRWAY_NORMAL: Surface = Surface(
            name = "fairway",
            cor = 0.40,
            thetaCritRad = 0.29,
            spinbackScale = 0.35,
            spinRetention = 0.75,
            rollDecelMps2 = 0.030 * BallPhysical.GRAVITY_MPS2,
        )

        val ROUGH_NORMAL: Surface = Surface(
            name = "rough",
            cor = 0.35,
            thetaCritRad = 0.35,
            spinbackScale = 0.70,
            spinRetention = 0.75,
            rollDecelMps2 = 0.095 * BallPhysical.GRAVITY_MPS2,
        )

        val GREEN_NORMAL: Surface = green(stimp = 11.0)
    }
}

enum class Firmness { SOFT, NORMAL, FIRM }
