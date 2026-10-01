// app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeRollout.kt
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.GroundHop
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Post-landing ground phase (spec 2026-09-25, hop-aware 2026-09-30). When the
 * engine recorded bounce hops, the ground phase replays the real chain: one
 * parabolic arc per hop (real cumulative touch points, apex height and air
 * time) followed by a flat quadratic ease-out roll to the exact rest
 * position. Without hops the legacy single flat roll from carry to totalM is
 * kept unchanged. The ground phase plays un-compressed when it fits the
 * follow-cam hold (MAX_DURATION_SEC); the hold itself stretches to the
 * ground duration (FollowCam.holdSec), so only extreme run-outs are scaled.
 * No Android deps; same inputs always produce the same samples.
 */
object RangeRollout {
    private const val DECEL_MPS2 = 5.0
    private const val MIN_DURATION_SEC = 0.3
    private const val MAX_DURATION_SEC = 6.0
    private const val STEP_SEC = 0.05

    /**
     * Visual start-speed cap for the ease-out roll (user report 2026-10-01:
     * "first bounce looks right, then it seems to shoot"). A quadratic
     * ease-out starts at 2 x distance / duration — for a 16 m roll that was
     * ~22 m/s the instant the ball left the last bounce. Capping the start
     * speed at 8 m/s stretches the roll duration instead (T = 2d/v0), so the
     * ball glides away from the bounce and bleeds speed visibly.
     */
    private const val ROLL_START_CAP_MPS = 8.0

    /** Ease-out roll duration: physics-based, stretched to honour the
     *  start-speed cap. */
    private fun rollDurationSec(dRollM: Double): Double =
        maxOf(sqrt(2.0 * dRollM / DECEL_MPS2), 2.0 * dRollM / ROLL_START_CAP_MPS)

    /**
     * Ground-phase anchor: the flight's actual landing point (last flight
     * sample). With the side-spin ground kick the ball lands at
     * (sideM - deltaX, carryM), so anchoring the ground chain at the rest
     * x would teleport sideways at touchdown; anchoring at the last flight
     * sample keeps the tracer joined to the ball. Falls back to the drawn
     * carry position for replayed shots without flight samples.
     */
    private fun anchor(shot: ShotResult): Pair<Double, Double> {
        val last = shot.samples.lastOrNull() ?: return Pair(shot.sideM, shot.carryM)
        return Pair(last.px, last.py)
    }

    /** One ground-phase segment from (startX, startY) to (endX, endY). */
    private class Segment(
        val startX: Double,
        val startY: Double,
        val endX: Double,
        val endY: Double,
        val apexM: Double,
        val durationSec: Double,
        private val easeOut: Boolean,
    ) {
        /** Sample at fraction [tau] of the segment: linear along hop arcs
         *  (constant horizontal speed), quadratic ease-out along the roll,
         *  parabolic height pz = 4 * apex * tau * (1 - tau) for hops. */
        fun sampleAt(tau: Double, timeSec: Double): TrajectorySample {
            val progress = if (easeOut) 2.0 * tau - tau * tau else tau
            val x = startX + (endX - startX) * progress
            val y = startY + (endY - startY) * progress
            val pz = if (apexM > 0.0) 4.0 * apexM * tau * (1.0 - tau) else 0.0
            return TrajectorySample(x, y, pz, timeSec)
        }

        fun scaled(scale: Double): Segment =
            Segment(startX, startY, endX, endY, apexM, durationSec * scale, easeOut)
    }

    /**
     * Ground-phase duration, coerced into [MIN_DURATION_SEC, MAX_DURATION_SEC].
     * Zero when there is no ground phase (no hops and no forward rollout).
     */
    fun durationSec(shot: ShotResult): Double {
        if (shot.groundHops.isEmpty()) {
            if (shot.rolloutM <= 0.0) return 0.0
            return rollDurationSec(shot.rolloutM).coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC)
        }
        return scaledSegments(shot).sumOf { it.durationSec }
    }

    /**
     * Ground-phase samples appended after the flight: hop arcs with positive
     * pz (or the legacy flat roll), ending exactly at the modelled rest
     * position (sideM, totalM). Empty when there is no ground phase.
     */
    fun samples(shot: ShotResult): List<TrajectorySample> {
        if (shot.groundHops.isEmpty()) {
            if (shot.rolloutM <= 0.0) return emptyList()
            val t = shot.flightTimeSec
            val duration = durationSec(shot)
            val (ax, ay) = anchor(shot)
            val out = ArrayList<TrajectorySample>()
            var step = 1
            var time = t + STEP_SEC
            while (time < t + duration) {
                val tau = (time - t) / duration
                val y = ay + shot.rolloutM * (2.0 * tau - tau * tau)
                out.add(TrajectorySample(ax, y, 0.0, time))
                step++
                time = t + step * STEP_SEC
            }
            out.add(TrajectorySample(shot.sideM, shot.totalM, 0.0, t + duration))
            return out
        }

        val segments = scaledSegments(shot)
        val duration = segments.sumOf { it.durationSec }
        if (duration <= 0.0) return emptyList()
        val t0 = shot.flightTimeSec
        val out = ArrayList<TrajectorySample>()
        var step = 1
        var time = t0 + STEP_SEC
        while (time < t0 + duration) {
            var ground = time - t0
            var segIdx = 0
            while (segIdx < segments.size - 1 && ground > segments[segIdx].durationSec) {
                ground -= segments[segIdx].durationSec
                segIdx++
            }
            val seg = segments[segIdx]
            val tau = if (seg.durationSec > 1e-12) {
                (ground / seg.durationSec).coerceIn(0.0, 1.0)
            } else {
                1.0
            }
            out.add(seg.sampleAt(tau, time))
            step++
            time = t0 + step * STEP_SEC
        }
        val last = segments.last()
        out.add(last.sampleAt(1.0, t0 + duration))
        return out
    }

    /**
     * The hop chain as uniformly time-scaled segments: first touch at the
     * flight's landing point, each hop to its cumulative landing, then the
     * final ease-out roll to the exact rest position (sideM, totalM).
     */
    private fun scaledSegments(shot: ShotResult): List<Segment> {
        val raw = ArrayList<Segment>(shot.groundHops.size + 1)
        val (ax, ay) = anchor(shot)
        var px = ax
        var py = ay
        for (hop in shot.groundHops) {
            val tx = ax + hop.landingX
            val ty = ay + hop.landingY
            raw.add(Segment(px, py, tx, ty, hop.apexM, hop.durationSec, easeOut = false))
            px = tx
            py = ty
        }
        val dRoll = hypot(shot.sideM - px, shot.totalM - py)
        raw.add(Segment(px, py, shot.sideM, shot.totalM, 0.0, rollDurationSec(dRoll), easeOut = true))

        val rawTotal = raw.sumOf { it.durationSec }
        val scale = if (rawTotal > 0.0) {
            rawTotal.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC) / rawTotal
        } else {
            1.0
        }
        return raw.map { it.scaled(scale) }
    }
}
