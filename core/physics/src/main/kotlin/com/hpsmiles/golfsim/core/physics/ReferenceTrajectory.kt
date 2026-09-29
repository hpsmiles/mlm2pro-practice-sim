package com.hpsmiles.golfsim.core.physics

/**
 * Solves a stock reference shot for a target distance by binary search on
 * ball speed (carry is monotonic in ball speed). Pure + deterministic -
 * anchors the Break-the-Pane middle row so all three rows stay reachable
 * by flighting (spec section 5 geometry, engine-verified calcs in section 2).
 *
 * Reference launch/spin profile is selected by distance bracket so every
 * requested target is comfortably within the reachable envelope:
 *   - target <= 120 m  : 21 degrees, 7500 rpm
 *   - 120 < target <= 220 m : 18 degrees, 5500 rpm
 *   - target > 220 m   : 14 degrees, 3000 rpm
 * Within each bracket the profile is fixed, so carry is monotone in ball speed
 * and the binary search is well behaved.
 */
object ReferenceTrajectory {

    const val SPEED_MIN_MPS = 20.0
    const val SPEED_MAX_MPS = 95.0

    /** Stock shot whose carry is closest to [targetM]. 60 iterations cap the ODE work. */
    fun stockShot(targetM: Double, toleranceM: Double = 0.5): ShotResult {
        val launchAngleDeg = profileFor(targetM).first
        val spinRpm = profileFor(targetM).second
        var lo = SPEED_MIN_MPS
        var hi = SPEED_MAX_MPS
        var best = simulate(lo, launchAngleDeg, spinRpm)
        var bestErr = kotlin.math.abs(best.carryM - targetM)
        repeat(60) {
            val mid = (lo + hi) / 2.0
            val shot = simulate(mid, launchAngleDeg, spinRpm)
            val err = kotlin.math.abs(shot.carryM - targetM)
            if (err < bestErr) {
                bestErr = err
                best = shot
            }
            if (err <= toleranceM) return shot
            if (shot.carryM < targetM) lo = mid else hi = mid
        }
        return best
    }

    /** z height where the stock shot for [targetM] crosses the pane plane (y = fraction x target). */
    fun paneCrossingHeightM(targetM: Double, planeFraction: Double = 0.25): Double {
        val shot = stockShot(targetM)
        val planeY = targetM * planeFraction
        return FlightCrossing.firstForwardCrossing(shot.samples, planeY)?.second
            ?: error("reference trajectory never crosses the pane plane at y=$planeY")
    }

    private fun profileFor(targetM: Double): Pair<Double, Int> = when {
        targetM <= 120.0 -> 21.0 to 7500
        targetM <= 220.0 -> 18.0 to 5500
        else -> 14.0 to 3000
    }

    private fun simulate(
        ballSpeedMps: Double,
        launchAngleDeg: Double,
        spinRpm: Int,
    ): ShotResult =
        BallFlightEngine.simulate(
            LaunchConditions(
                ballSpeedMps = ballSpeedMps,
                launchAngleDeg = launchAngleDeg,
                spinRpm = spinRpm,
            ),
            Environment(),
            UniformSurface(Surface.FAIRWAY_NORMAL),
        )
}
