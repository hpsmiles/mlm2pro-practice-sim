package com.hpsmiles.golfsim.core.physics

/**
 * Drag/lift/spin-decay for a dimpled golf ball.
 *
 * Reynolds-piecewise drag and Bearman-binned lift polynomials, structure and
 * constants from openfairway's FlightProfile/AerodynamicsModel (MIT — see
 * spec §2/§5; ultimate provenance Bearman & Harvey 1976, Lyu et al. 2018).
 * Tuned named parameters (spec §5, calibration sweep 2026-09-20):
 * sdCoeff = 8.0, sdCap = 1.55, msBoostMax = 0.0, spin tau = 12 s.
 * OF Default reference values: 4.0 / 1.20 / 0.45 / 5 s.
 */
object AerodynamicModel {

    // ---- FlightProfile Default constants (verbatim) ----
    private const val CD_POLY_A = 1.1948
    private const val CD_POLY_B = -2.09661e-5
    private const val CD_POLY_C = 1.42472e-10
    private const val CD_POLY_D = -3.14383e-16
    private const val HIGH_RE_CD_CAP = 0.200
    private const val LOW_RE_CD_FLOOR = 0.38
    private const val LOW_RE_BLEND_START = 30000.0
    private const val CD_AT_50K = 0.4632
    const val CD_MIN = 0.223
    private const val CLMAX_BASE = 0.268
    private const val CLMAX_HI = 0.32
    private const val CLMAX_SRS = 0.35
    private const val CLMAX_SRE = 0.50

    // Tuned (M2 calibration): sdCoeff 8.0, sdCap 1.55, msBoostMax 0.0
    const val SD_COEFF = 8.0
    const val SD_CAP = 1.55
    const val MS_BOOST_MAX = 0.0
    private const val SD_HIGH_SPIN_MAX = 1.03
    private const val SD_ULTRA_MAX = 1.21
    private const val HSD_SRS = 0.30
    private const val HSD_SRE = 0.48
    private const val HSD_RELIEF_RE_FULL = 90000.0
    private const val HSD_RELIEF_RE_ZERO = 105000.0
    private const val UHSD_SRS = 0.57
    private const val UHSD_SRE = 0.77
    private const val PROG_SRS = 0.33
    private const val PROG_SRE = 0.50
    private const val PROG_BOOST_MAX = 0.25
    private const val HIGH_RE_START = 75000.0
    private const val HRE_GAIN = 16.0
    private const val HRE_MID_GAIN = 16.0
    private const val HRE_RED_S = 0.10
    private const val HRE_RED_E = 0.18
    private const val HRE_REC_S = 0.26
    private const val HRE_REC_E = 0.40
    private const val HS_ATT_S = 0.45
    private const val HS_ATT_E = 0.55
    private const val HS_ATT_MAX = 0.09
    private const val UHS_ATT_S = 0.58
    private const val UHS_ATT_E = 0.85
    private const val UHS_ATT_MAX = 0.10
    private const val LOWRE_HS_ATT_MAX = 0.10
    private const val LOWRE_UHS_ATT_MAX = 0.06
    private const val LL_MAX = 1.08
    private const val LL_VLA_FULL = 6.5
    private const val LL_VLA_ZERO = 9.5
    private const val LL_RE_S = 85000.0
    private const val LL_RE_E = 110000.0
    private const val LL_SRS = 0.18
    private const val LL_SRE = 0.22
    private const val HL_BOOST_MAX = 1.24
    private const val HL_VLA_S = 24.5
    private const val HL_VLA_F = 31.5
    private const val HL_SRS = 0.50
    private const val HL_SRE = 0.70
    private const val MS_BOOST_SRS = 0.17
    private const val MS_BOOST_SRE = 0.31

    /** Spin-decay time constant (s) — tuned M2 value, verify vs M4 live captures. */
    const val SPIN_TAU_SEC = 12.0

    private fun smooth01(t: Double): Double {
        if (t <= 0.0) return 0.0
        if (t >= 1.0) return 1.0
        return t * t * (3.0 - 2.0 * t)
    }

    private fun ss01(v: Double, a: Double, b: Double): Double {
        val rng = b - a
        if (Math.abs(rng) < 1e-9) return if (v >= b) 1.0 else 0.0
        return smooth01((v - a) / rng)
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun clampd(v: Double, lo: Double, hi: Double) = Math.max(lo, Math.min(hi, v))

    /** Reynolds-piecewise drag without spin/launch modifiers. */
    fun cdRaw(re: Double): Double {
        if (re > 200000.0) return HIGH_RE_CD_CAP
        if (re >= 50000.0) return CD_POLY_A + CD_POLY_B * re + CD_POLY_C * re * re + CD_POLY_D * re * re * re
        if (re <= LOW_RE_BLEND_START) return LOW_RE_CD_FLOOR
        return lerp(LOW_RE_CD_FLOOR, CD_AT_50K, ss01(re, LOW_RE_BLEND_START, 50000.0))
    }

    /** Spin-dependent drag multiplier with openfairway's cap-relief structure. */
    fun spinDragMult(sr: Double, re: Double): Double {
        if (sr <= 0.0) return 1.0
        val highSpinW = ss01(sr, HSD_SRS, HSD_SRE)
        val reReliefW = 1.0 - ss01(re, HSD_RELIEF_RE_FULL, HSD_RELIEF_RE_ZERO)
        var cap = lerp(SD_CAP, SD_HIGH_SPIN_MAX, highSpinW * reReliefW)
        cap += PROG_BOOST_MAX * ss01(sr, PROG_SRS, PROG_SRE)
        cap = lerp(cap, SD_ULTRA_MAX, ss01(sr, UHSD_SRS, UHSD_SRE))
        return Math.min(1.0 + SD_COEFF * sr * sr, cap)
    }

    fun highLaunchDragScale(vlaDeg: Double, sr: Double): Double {
        val lf = ss01(vlaDeg, HL_VLA_S, HL_VLA_F)
        if (lf <= 0.0) return 1.0
        val sf = ss01(sr, HL_SRS, HL_SRE)
        if (sf <= 0.0) return 1.0
        return lerp(1.0, HL_BOOST_MAX, lf * sf)
    }

    fun lowLaunchLiftScale(vlaDeg: Double, sr: Double, re: Double): Double {
        val lf = ss01(LL_VLA_ZERO - vlaDeg, 0.0, LL_VLA_ZERO - LL_VLA_FULL)
        if (lf <= 0.0) return 1.0
        val rf = ss01(re, LL_RE_S, LL_RE_E)
        if (rf <= 0.0) return 1.0
        val sf = 1.0 - ss01(sr, LL_SRS, LL_SRE)
        if (sf <= 0.0) return 1.0
        return lerp(1.0, LL_MAX, lf * rf * sf)
    }

    fun midSpinClBoost(sr: Double): Double {
        val t = ss01(sr, MS_BOOST_SRS, MS_BOOST_SRE)
        return 1.0 + MS_BOOST_MAX * (t * (1.0 - t) * 4.0)
    }

    fun clMax(sr: Double) = lerp(CLMAX_BASE, CLMAX_HI, ss01(sr, CLMAX_SRS, CLMAX_SRE))

    private fun clRe50k(s: Double) = 0.0472121 + 2.84795 * s - 23.4342 * s * s + 45.4849 * s * s * s
    private fun clRe60k(s: Double) = 0.320524 - 4.7032 * s + 14.0613 * s * s
    private fun clRe65k(s: Double) = 0.266667 - 4.0 * s + 13.3333 * s * s
    private fun clRe70k(s: Double) = 0.0496189 + 0.00211396 * s + 2.34201 * s * s

    private fun highReGain(sr: Double): Double {
        if (sr <= HRE_RED_S) return HRE_GAIN
        if (sr < HRE_RED_E) return lerp(HRE_GAIN, HRE_MID_GAIN, ss01(sr, HRE_RED_S, HRE_RED_E))
        if (sr <= HRE_REC_S) return HRE_MID_GAIN
        if (sr < HRE_REC_E) return lerp(HRE_MID_GAIN, HRE_GAIN, ss01(sr, HRE_REC_S, HRE_REC_E))
        return HRE_GAIN
    }

    private fun clHighRe(sr: Double): Double {
        val g = highReGain(sr)
        return clMax(sr) * sr * g / (1.0 + sr * g)
    }

    private fun attHigh(cl: Double, sr: Double): Double {
        val a1 = 1.0 - HS_ATT_MAX * ss01(sr, HS_ATT_S, HS_ATT_E)
        val a2 = 1.0 - UHS_ATT_MAX * ss01(sr, UHS_ATT_S, UHS_ATT_E)
        return cl * a1 * a2
    }

    private fun attLowRe(cl: Double, sr: Double): Double {
        val a1 = 1.0 - LOWRE_HS_ATT_MAX * ss01(sr, HS_ATT_S, HS_ATT_E)
        val a2 = 1.0 - LOWRE_UHS_ATT_MAX * ss01(sr, HS_ATT_S, HS_ATT_E)
        return cl * a1 * a2
    }

    private fun binVal(i: Int, s: Double): Double = when (i) {
        0 -> clRe50k(s)
        1 -> clRe60k(s)
        2 -> clRe65k(s)
        3 -> clRe70k(s)
        else -> clHighRe(s)
    }

    /** Full lift coefficient at (Re, spin ratio S) before launch modifiers. */
    fun clOf(re: Double, sr: Double): Double {
        if (sr <= 0.0) return 0.0
        val cmax = clMax(sr)
        if (re < 50000.0) {
            if (re <= 30000.0) return 0.0
            val lowReT = smooth01((re - 30000.0) / 20000.0)
            return attLowRe(clampd(clRe50k(sr), 0.0, cmax) * lowReT, sr)
        }
        if (re >= HIGH_RE_START) {
            return attHigh(clampd(clHighRe(sr), 0.0, cmax), sr)
        }
        val anchors = intArrayOf(50000, 60000, 65000, 70000, 75000)
        var hi = 4
        for (i in 0..4) {
            if (re <= anchors[i]) { hi = i; break }
        }
        val lo = Math.max(hi - 1, 0)
        val clLo = Math.max(binVal(lo, sr), 0.0)
        val clHi = Math.max(binVal(hi, sr), 0.0)
        val w = if (anchors[hi] != anchors[lo]) (re - anchors[lo]) / (anchors[hi] - anchors[lo]) else 0.0
        return attLowRe(clampd(lerp(clLo, clHi, w), 0.0, cmax), sr)
    }

    /** Composite drag: raw x spin multiplier x high-launch boost, floored at CdMin. */
    fun dragCoefficient(re: Double, s: Double, vlaDeg: Double): Double =
        Math.max(cdRaw(re) * spinDragMult(s, re) * highLaunchDragScale(vlaDeg, s), CD_MIN)

    /** Composite lift: bins x low-launch recovery x mid-spin boost (disabled). */
    fun liftCoefficient(re: Double, s: Double, vlaDeg: Double): Double =
        clOf(re, s) * lowLaunchLiftScale(vlaDeg, s, re) * midSpinClBoost(s)

    /** Exponential spin decay over dt with the named tau. */
    fun spinDecay(omega: Double, dt: Double, tauSec: Double = SPIN_TAU_SEC): Double =
        omega * Math.exp(-dt / tauSec)
}
