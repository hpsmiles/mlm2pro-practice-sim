package com.hpsmiles.golfsim.core.scoring

/** Anchor bands of the SG tables; [anchorHc] positions the band on the handicap axis. */
enum class SkillBand(val anchorHc: Double) {
    TOUR(-5.0),
    SCRATCH(0.0),
    HCP10(10.0),
    HCP18(18.0),
}
