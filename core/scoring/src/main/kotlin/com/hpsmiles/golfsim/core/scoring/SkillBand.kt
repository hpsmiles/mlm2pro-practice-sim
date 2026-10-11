package com.hpsmiles.golfsim.core.scoring

import kotlin.math.abs

/**
 * Anchor bands of the SG tables; [anchorHc] positions the band on the handicap axis.
 * [typicalDriveM] is the per-band typical total drive distance (metres), used to rate
 * driving quality against the position a golfer of that level would typically leave
 * themselves. Source: Arccos 2026 Annual Driving Distance Report (young-scratch cohort)
 * + PGA Tour average.
 */
enum class SkillBand(val anchorHc: Double, val typicalDriveM: Double) {
    TOUR(-5.0, 272.5),
    SCRATCH(0.0, 239.6),
    HCP10(10.0, 208.9),
    HCP18(18.0, 193.9);

    companion object {
        /**
         * The benchmark band whose anchor is nearest [estimate], after clamping out-of-range
         * and non-finite values to the anchor range (NaN / +Infinity and anything above the
         * top anchor -> [HCP18]; anything below the bottom anchor -> [TOUR]). Ties resolve to
         * the earlier entry on the handicap axis, i.e. the better band (SCRATCH before HCP10,
         * HCP10 before HCP18).
         */
        fun nearestTo(estimate: Double): SkillBand {
            val h = when {
                estimate.isNaN() || estimate > SkillBand.HCP18.anchorHc -> SkillBand.HCP18.anchorHc
                estimate < SkillBand.TOUR.anchorHc -> SkillBand.TOUR.anchorHc
                else -> estimate
            }
            return SkillBand.entries.minByOrNull { abs(it.anchorHc - h) }!!
        }
    }
}
