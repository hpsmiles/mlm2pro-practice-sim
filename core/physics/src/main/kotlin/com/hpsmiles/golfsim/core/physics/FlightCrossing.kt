package com.hpsmiles.golfsim.core.physics

/**
 * Where a flight crosses a vertical plane y = constant (pure, deterministic).
 * Used by ReferenceTrajectory (physics) and PaneGeom (app) - one definition,
 * shared, so both games agree on the crossing math.
 */
object FlightCrossing {

    /**
     * First py-INCREASING crossing of y = [planeY], linearly interpolated
     * between consecutive samples. Returns (x, z), or null when the flight
     * never crosses the plane forward.
     */
    fun firstForwardCrossing(
        samples: List<TrajectorySample>,
        planeY: Double,
    ): Pair<Double, Double>? {
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            if (a.py < planeY && b.py >= planeY) {
                val t = (planeY - a.py) / (b.py - a.py)
                return (a.px + (b.px - a.px) * t) to (a.pz + (b.pz - a.pz) * t)
            }
        }
        return null
    }
}
