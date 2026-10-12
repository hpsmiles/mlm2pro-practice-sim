package com.hpsmiles.golfsim.core.scoring

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Layer 1 of the sim-condition scoring model: positions priced at their in-shed cost.
 * Real-course tables remain the substrate; bounded relief terms remove costs the shed
 * does not impose (break/speed variance, rough severity). Spec §3.
 */
object SimExpectedStrokes {

    // Fitting knobs — public vars so the app-module fitting harness can scan them; task 9 pins them as private consts.
    var GAMMA = 1.05
    var K_FAIRWAY = 0.10
    var K_ROUGH = 0.15
    var SHED_TIGHTENING = 0.25

    private const val K_SLOPE = 0.4
    private const val RAMP_TAPIN_M = 1.0

    private val D50 = mapOf(
        SkillBand.TOUR to 3.2,
        SkillBand.SCRATCH to 2.75,
        SkillBand.HCP10 to 2.2,
        SkillBand.HCP18 to 1.8,
    )

    fun simEndValue(band: SkillBand, lie: Lie, metres: Double): Double = when (lie) {
        Lie.GREEN -> greenEndValue(band, metres)
        Lie.FAIRWAY -> max(1.0, (1.0 - K_FAIRWAY) * expectedStrokes(band, Lie.FAIRWAY, metres))
        Lie.ROUGH -> max(1.0, (1.0 - K_ROUGH) * expectedStrokes(band, Lie.ROUGH, metres))
        Lie.TEE -> error("TEE is not priced by the sim model")
    }

    /**
     * Two-state flat-green putt model: E = 1 + (1 - p)·γ with p the one-putt probability
     * on a dead-flat, known-speed green (logistic in d, per-band inflection d50, tap-in ramp).
     */
    private fun greenEndValue(band: SkillBand, d: Double): Double {
        val d50 = D50.getValue(band)
        val pLogit = 1.0 / (1.0 + exp(K_SLOPE * (d - d50)))
        val ramp = min(1.0, d / RAMP_TAPIN_M)
        val p = 1.0 - (1.0 - pLogit) * ramp
        return 1.0 + (1.0 - p) * GAMMA
    }
}
