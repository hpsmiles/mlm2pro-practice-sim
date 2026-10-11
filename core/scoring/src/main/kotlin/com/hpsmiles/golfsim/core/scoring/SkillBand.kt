package com.hpsmiles.golfsim.core.scoring

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
    HCP18(18.0, 193.9),
}
