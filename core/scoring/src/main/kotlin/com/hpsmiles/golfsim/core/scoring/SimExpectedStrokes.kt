package com.hpsmiles.golfsim.core.scoring

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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

    val START_DISTANCES = doubleArrayOf(50.0, 70.0, 90.0, 100.0, 150.0)

    private val GIR = mapOf(
        SkillBand.TOUR to doubleArrayOf(0.98, 0.95, 0.95, 0.95, 0.85),
        SkillBand.SCRATCH to doubleArrayOf(0.95, 0.92, 0.89, 0.88, 0.70),
        SkillBand.HCP10 to doubleArrayOf(0.90, 0.84, 0.77, 0.75, 0.55),
        SkillBand.HCP18 to doubleArrayOf(0.80, 0.70, 0.62, 0.60, 0.40),
    )
    private val PROX_MEAN = mapOf(
        SkillBand.TOUR to doubleArrayOf(4.0, 4.8, 5.5, 5.8, 7.5),
        SkillBand.SCRATCH to doubleArrayOf(5.0, 6.0, 7.0, 7.5, 10.5),
        SkillBand.HCP10 to doubleArrayOf(6.5, 8.0, 9.5, 10.0, 13.5),
        SkillBand.HCP18 to doubleArrayOf(8.5, 10.5, 12.0, 12.5, 16.0),
    )
    private val PROX_SD = mapOf(
        SkillBand.TOUR to doubleArrayOf(5.0, 6.0, 6.5, 7.0, 8.5),
        SkillBand.SCRATCH to doubleArrayOf(6.0, 7.0, 7.5, 8.0, 9.5),
        SkillBand.HCP10 to doubleArrayOf(7.5, 8.5, 9.0, 9.0, 10.5),
        SkillBand.HCP18 to doubleArrayOf(9.0, 10.0, 10.5, 10.0, 11.5),
    )
    private val MISS_ROUGH_SHARE = mapOf(
        SkillBand.TOUR to 0.35, SkillBand.SCRATCH to 0.45,
        SkillBand.HCP10 to 0.55, SkillBand.HCP18 to 0.65,
    )

    // 9-point equal-weight normal quantiles (green rest nodes).
    private val GREEN_Z = doubleArrayOf(
        -1.5927, -0.9674, -0.5895, -0.2822, 0.0, 0.2822, 0.5895, 0.9674, 1.5927,
    )
    // Triangular miss band beyond the 15 m green edge: [16.0, 27.5] m, mode 18.0.
    private const val MISS_A = 16.0
    private const val MISS_C = 27.5
    private const val MISS_MODE = 18.0
    private const val GREEN_NODE_FLOOR_M = 0.3
    private const val GREEN_NODE_CEILING_M = 15.0

    data class Outcome(val lie: Lie, val metres: Double, val probability: Double)

    fun outcomeGrid(band: SkillBand, startM: Double): List<Outcome> {
        val g = interpolate(START_DISTANCES, GIR.getValue(band), startM)
        val mean = interpolate(START_DISTANCES, PROX_MEAN.getValue(band), startM)
        val sd = interpolate(START_DISTANCES, PROX_SD.getValue(band), startM) * (1.0 - SHED_TIGHTENING)
        val roughShare = MISS_ROUGH_SHARE.getValue(band)
        val nodes = ArrayList<Outcome>(14)
        for (z in GREEN_Z) {
            val x = (mean + z * sd).coerceIn(GREEN_NODE_FLOOR_M, GREEN_NODE_CEILING_M)
            nodes.add(Outcome(Lie.GREEN, x, g / GREEN_Z.size))
        }
        val missP = (1.0 - g) / 5.0
        for (i in 0 until 5) {
            val u = (i + 0.5) / 5.0
            val x = triangularQuantile(u)
            nodes.add(Outcome(Lie.FAIRWAY, x, missP * (1.0 - roughShare)))
            nodes.add(Outcome(Lie.ROUGH, x, missP * roughShare))
        }
        return nodes
    }

    private fun triangularQuantile(u: Double): Double {
        val modeSplit = (MISS_MODE - MISS_A) / (MISS_C - MISS_A)
        return if (u <= modeSplit) {
            MISS_A + sqrt(u * (MISS_C - MISS_A) * (MISS_MODE - MISS_A))
        } else {
            MISS_C - sqrt((1.0 - u) * (MISS_C - MISS_A) * (MISS_C - MISS_MODE))
        }
    }

    fun simStartValue(band: SkillBand, startM: Double): Double =
        1.0 + outcomeGrid(band, startM).sumOf { it.probability * simEndValue(band, it.lie, it.metres) }

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
