package com.hpsmiles.golfsim.core.physics

/**
 * Solves a stock reference shot for a target distance by binary search on
 * ball speed (carry is monotonic in ball speed). Pure + deterministic -
 * anchors the Break-the-Pane middle row so all three rows stay reachable
 * by flighting (spec section 5 geometry, engine-verified calcs in section 2).
 */
object ReferenceTrajectory {

    private const val REF_LAUNCH_ANGLE_DEG = 21.0
    private const val REF_SPIN_RPM = 7500
    const val SPEED_MIN_MPS = 20.0
    const val SPEED_MAX_MPS = 95.0

    /** Stock shot whose carry is closest to [targetM]. 60 iterations cap the ODE work. */
    fun stockShot(targetM: Double, toleranceM: Double = 0.5): ShotResult {
        var lo = SPEED_MIN_MPS
        var hi = SPEED_MAX_MPS
        var best = simulate(lo)
        var bestErr = kotlin.math.abs(best.carryM - targetM)
        repeat(60) {
            val mid = (lo + hi) / 2.0
            val shot = simulate(mid)
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

    private fun simulate(ballSpeedMps: Double): ShotResult =
        BallFlightEngine.simulate(
            LaunchConditions(
                ballSpeedMps = ballSpeedMps,
                launchAngleDeg = REF_LAUNCH_ANGLE_DEG,
                spinRpm = REF_SPIN_RPM,
            ),
            Environment(),
            UniformSurface(Surface.FAIRWAY_NORMAL),
        )
}
