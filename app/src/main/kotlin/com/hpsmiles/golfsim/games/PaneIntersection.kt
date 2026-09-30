package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.RangeRollout

/**
 * Where (and when) the drawn flight crosses the pane plane, so the canvas can
 * mark the break point once the tracer has reached it (spec 2026-09-30 §6).
 * Pure; uses the exact sample list [GameScene.drawTracer] draws.
 */
object PaneIntersection {

    /** Crossing point plus when it becomes visible along the tracer (0..1). */
    data class Mark(val xM: Double, val zM: Double, val revealFraction: Float)

    /** Null when the drawn flight never crosses the pane plane forward. */
    fun mark(result: ShotResult, planeYM: Double, apexVMin: Double): Mark? {
        val samples = drawnSamples(result, apexVMin)
        if (samples.size < 2) return null
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            if (a.py < planeYM && b.py >= planeYM) {
                val t = (planeYM - a.py) / (b.py - a.py)
                val x = a.px + (b.px - a.px) * t
                val z = a.pz + (b.pz - a.pz) * t
                val frac = ((i - 1) + t) / (samples.size - 1)
                return Mark(x, z, frac.coerceIn(0.0, 1.0).toFloat())
            }
        }
        return null
    }

    /** The same sample list [GameScene.drawTracer] renders (frame-scaled + rollout). */
    fun drawnSamples(result: ShotResult, apexVMin: Double): List<TrajectorySample> =
        if (result.flightTimeSec <= 0.0) {
            FollowCam.scaledSamples(result, apexVMin)
        } else {
            FollowCam.scaledSamples(result, apexVMin) + RangeRollout.samples(result)
        }
}
