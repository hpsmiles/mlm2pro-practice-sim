package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.HandicapEstimate
import com.hpsmiles.golfsim.core.scoring.SkillBand
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Display formatting for handicap estimates and per-shot chips.
 *
 * Boundary note (spec-literal, intentional asymmetry): the numeric range is
 * `[-5, 0)` but the chip band is `[-5, 0]`, so at exactly the TOUR anchor
 * (-5.0) `formatHc` renders "+5.0" while `chip` renders "SCR". The upper clamp
 * is "above 25": exactly 25.0 renders "25.0" and 25.1 renders "25+". These
 * brackets deliberately differ in granularity per rendering and mirror
 * HandicapMatch's clampedLow/clampedHigh flags.
 */
object CombineFormats {

    fun formatHc(est: Double): String = when {
        est < SkillBand.TOUR.anchorHc -> "tour-or-better"
        est < 0.0 -> "+" + oneDp(-est)
        est <= 25.0 -> oneDp(est)
        else -> "25+"
    }

    fun formatHcWithUncertainty(estimate: HandicapEstimate): String {
        val low = estimate.low
        val high = estimate.high
        return if (low == null || high == null) {
            "≈ ${formatHc(estimate.est)}"
        } else {
            "≈ ${formatHc(estimate.est)} ± ${zeroDp((high - low) / 2.0)}"
        }
    }

    fun chip(est: Double): String = when {
        est < SkillBand.TOUR.anchorHc -> "TOUR+"
        est < 0.0 -> "SCR"
        est <= 25.0 -> "≈" + est.roundToInt()
        else -> "25+"
    }

    fun oneDp(value: Double): String = String.format(Locale.US, "%.1f", value)

    fun zeroDp(value: Double): String = String.format(Locale.US, "%.0f", value)
}
