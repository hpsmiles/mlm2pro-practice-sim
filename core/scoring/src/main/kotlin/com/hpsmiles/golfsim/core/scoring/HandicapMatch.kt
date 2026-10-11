package com.hpsmiles.golfsim.core.scoring

import kotlin.math.abs
import kotlin.math.sqrt

/** One scored shot: start lie + distance, rest lie + distance. */
data class ShotInput(val startLie: Lie, val startM: Double, val endLie: Lie, val endM: Double)

/** Handicap estimate with an uncertainty range (null when n < 2) and clamp flags. */
data class HandicapEstimate(
    val est: Double,
    val low: Double?,
    val high: Double?,
    val perBandSg: Map<SkillBand, Double>,
    val clampedLow: Boolean,
    val clampedHigh: Boolean,
)

/**
 * Handicap match on the mean SG curve: SG(h) is piecewise linear between band anchors
 * (TOUR -5, SCRATCH 0, HCP10 10, HCP18 18). SG increases with h; the estimate is the
 * closed-form root of SG(h) = 0, extrapolated on the end segments when all SGs are one-sided.
 */
fun handicapMatch(shots: List<ShotInput>): HandicapEstimate {
    require(shots.isNotEmpty()) { "handicapMatch requires at least one shot" }

    val perBand = LinkedHashMap<SkillBand, Double>()
    for (band in SkillBand.entries) {
        val sgs = shots.map { strokesGained(band, it.startLie, it.startM, it.endLie, it.endM) }
        perBand[band] = sgs.average()
    }

    // Anchors on the handicap axis taken from the band definitions; SG values aligned by band.
    val h = DoubleArray(SkillBand.entries.size) { SkillBand.entries[it].anchorHc }
    val s = DoubleArray(h.size) { perBand.getValue(SkillBand.entries[it]) }
    val segs = h.size - 1 // number of piecewise-linear segments between anchors

    val est: Double
    val segment: Int // index of the segment used for the root (0 = [-5,0], 1 = [0,10], 2 = [10,18])
    // Set when every band mean is negative AND the last ([10,18]) segment is
    // non-increasing: the shot is worse than every band, so we clamp HIGH
    // rather than extrapolate (see the all-negative branch below).
    var degenerateAllNegative = false
    when {
        s[0] >= 0.0 -> {
            est = h[0] + (h[1] - h[0]) * (-s[0]) / s1Diff(s, 0)
            segment = 0
        }
        else -> {
            var crossing = -1
            for (i in 0 until segs) if (s[i] < 0.0 && s[i + 1] >= 0.0) { crossing = i; break }
            if (crossing >= 0) {
                est = h[crossing] + (h[crossing + 1] - h[crossing]) * (-s[crossing]) / s1Diff(s, crossing)
                segment = crossing
            } else {
                // All band means are negative (worse than every band). The
                // [10,18] segment is the only one left to extend. Extending it
                // toward BETTER golfers is only meaningful when SG actually
                // rises across it; a non-increasing segment (a genuine
                // non-monotonic dip in the data) would run off to a nonsense
                // low estimate (e.g. ~-56 → wrongly shown as "TOUR+"), so we
                // clamp HIGH instead. Positive slope keeps today's behaviour.
                segment = segs - 1
                if (s[segs] - s[segs - 1] <= 0.0) {
                    est = Double.POSITIVE_INFINITY
                    degenerateAllNegative = true
                } else {
                    est = h[segs - 1] + (h[segs] - h[segs - 1]) * (-s[segs - 1]) / s1Diff(s, segs - 1)
                }
            }
        }
    }

    val low: Double?
    val high: Double?
    if (shots.size < 2 || degenerateAllNegative) {
        low = null
        high = null
    } else {
        val scratchSgs = shots.map { strokesGained(SkillBand.SCRATCH, it.startLie, it.startM, it.endLie, it.endM) }
        val mean = scratchSgs.average()
        val sd = sqrt(scratchSgs.sumOf { (it - mean) * (it - mean) } / (shots.size - 1))
        val slope = (s[segment + 1] - s[segment]) / (h[segment + 1] - h[segment])
        val denom = if (abs(slope) < 1e-12) 1e-12 else slope
        val uncertainty = sd / sqrt(shots.size.toDouble()) / abs(denom)
        low = est - uncertainty
        high = est + uncertainty
    }

    return HandicapEstimate(
        est = est,
        low = low,
        high = high,
        perBandSg = perBand,
        clampedLow = est < -5.0,
        clampedHigh = degenerateAllNegative || est > 25.0,
    )
}

private fun s1Diff(s: DoubleArray, i: Int): Double {
    val d = s[i + 1] - s[i]
    return if (abs(d) < 1e-12) 1e-12 else d
}
