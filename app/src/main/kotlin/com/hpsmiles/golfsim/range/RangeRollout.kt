// app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeRollout.kt
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import kotlin.math.sqrt

/**
 * Synthetic post-landing ground roll (spec 2026-09-25): the modelled flight
 * ends at the carry point, then the ball decelerates to rest at totalM.
 * The roll is a pure quadratic ease-out (lands with forward speed, slows to
 * a stop) fitted inside the existing follow-cam hold window (capped at
 * 1.8 s, well under LAND_HOLD_SEC = 2.5 s). No Android deps; same inputs
 * always produce the same samples.
 */
object RangeRollout {
    private const val DECEL_MPS2 = 3.5
    private const val MIN_DURATION_SEC = 0.3
    private const val MAX_DURATION_SEC = 1.8
    private const val STEP_SEC = 0.05

    /**
     * Roll duration from the constant-deceleration distance formula
     * (d = v^2 / 2a), coerced into [MIN_DURATION_SEC, MAX_DURATION_SEC].
     * Zero when there is no forward rollout.
     */
    fun durationSec(shot: ShotResult): Double {
        if (shot.rolloutM <= 0.0) return 0.0
        val raw = sqrt(2.0 * shot.rolloutM / DECEL_MPS2)
        return raw.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC)
    }

    /**
     * Ground-roll samples appended after the flight: px stays on the shot's
     * lateral line, pz = 0, py eases from carryM out to the exact rest
     * position totalM. Empty when there is no rollout.
     */
    fun samples(shot: ShotResult): List<TrajectorySample> {
        if (shot.rolloutM <= 0.0) return emptyList()
        val t = shot.flightTimeSec
        val duration = durationSec(shot)
        val out = ArrayList<TrajectorySample>()
        var step = 1
        var time = t + STEP_SEC
        while (time < t + duration) {
            val tau = (time - t) / duration
            val y = shot.carryM + shot.rolloutM * (2.0 * tau - tau * tau)
            out.add(TrajectorySample(shot.sideM, y, 0.0, time))
            step++
            time = t + step * STEP_SEC
        }
        out.add(TrajectorySample(shot.sideM, shot.totalM, 0.0, t + duration))
        return out
    }
}
