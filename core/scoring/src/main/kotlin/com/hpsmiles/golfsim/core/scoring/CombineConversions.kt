package com.hpsmiles.golfsim.core.scoring

/**
 * Layer 2: fitted shed→real conversion offsets (SG per shot, per station kind).
 * Fitted to the owner's declared anchors by the task-9 harness (spec §4, §8 tuning log);
 * pinned literals — the calibration session (2026-10-12) solves to +3 / −1 / +8.
 * Spec §4.
 */
object CombineConversions {
    val APPROACH_100M = -0.169254
    val APPROACH_150M = -0.221857
    val DRIVER = -0.065042

    /**
     * Approach conversion at a start distance: linear interpolation between the two fitted
     * stations (100 m, 150 m), linear extrapolation outside [100, 150] (WEDGES 50/70/90).
     */
    fun approachOffsetFor(startDistanceM: Double): Double =
        APPROACH_100M + (startDistanceM - 100.0) / 50.0 * (APPROACH_150M - APPROACH_100M)
}
