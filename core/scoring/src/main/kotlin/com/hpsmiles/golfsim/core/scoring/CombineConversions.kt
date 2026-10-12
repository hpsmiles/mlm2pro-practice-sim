package com.hpsmiles.golfsim.core.scoring

/**
 * Layer 2: fitted shed→real conversion offsets (SG per shot, per station kind).
 * Public vars exist for the fitting harness; task 9 pins these to fitted literals
 * (vars become vals) and the calibration record lives in the spec §8 tuning log. Spec §4.
 */
object CombineConversions {
    var APPROACH_100M = 0.0
    var APPROACH_150M = 0.0
    var DRIVER = 0.0

    /**
     * Approach conversion at a start distance: linear interpolation between the two fitted
     * stations (100 m, 150 m), linear extrapolation outside [100, 150] (WEDGES 50/70/90).
     */
    fun approachOffsetFor(startDistanceM: Double): Double =
        APPROACH_100M + (startDistanceM - 100.0) / 50.0 * (APPROACH_150M - APPROACH_100M)
}
