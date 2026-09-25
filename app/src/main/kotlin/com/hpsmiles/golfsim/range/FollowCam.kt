// app/src/main/kotlin/com/hpsmiles/golfsim/range/FollowCam.kt
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ball follow camera (spec 2026-09-25): a pure phase machine mapping
 * (ShotResult, playFraction) -> RangeCamera. No Android deps; same inputs
 * always produce the same camera.
 *
 * Timeline (t = seconds since impact, T = flightTimeSec):
 *   static   t < engageT        STATIC rig (or early engage, whichever first)
 *   blend    0.8 s              smoothstep STATIC -> chase rig (target moves)
 *   chase    -> apex            ball + (0, -CHASE_BACK_M, +CHASE_UP_M), pitch 0
 *   descent  apex -> touchdown  ease chase rig -> overlook rig; pitch eases
 *                              0 -> LAND_PITCH_DEG on the same progress
 *   hold     LAND_HOLD_SEC      overlook rig parked
 *   snap back after hold        cut to STATIC (tracer + landing marker persist)
 */
object FollowCam {

    /** Static window before the follow cam engages. */
    const val FOLLOW_DELAY_SEC = 1.5

    /** Cross-fade from the static rig to the chase rig. */
    const val BLEND_SEC = 0.8

    /** Chase position: this far behind the ball. */
    const val CHASE_BACK_M = 20.0

    /** Chase position: this far above the ball. */
    const val CHASE_UP_M = 2.5

    /** Landing overlook: distance from the landing point to the camera. */
    const val OVERLOOK_DIST_M = 25.0

    /** Landing overlook pitch (deg). Tuning knob — user: "45 may not be right but it is a starting value". */
    const val LAND_PITCH_DEG = 45.0

    /** How long the overlook holds before cutting back to STATIC. */
    const val LAND_HOLD_SEC = 2.5

    /**
     * Early-engage threshold: STATIC-rig projected v of the ball at which
     * the follow cam engages immediately (big apexes are never lost to the
     * timer). -0.17 is the top of the frame on the reference tablet
     * (horizon 0.30h, focal 1.1w, 2000x1250: (0 - 0.30h) / 1.1w).
     */
    const val EARLY_ENGAGE_V = -0.17

    /**
     * Float playback fractions arrive at endFraction a few ULPs below the
     * hold boundary (the caller computes endFraction(shot).toFloat(), and
     * the roundtrip maps below the Double guard time by up to ~1e-6 s).
     * The resting frame at exactly endFraction must be the STATIC crane
     * view (the playback loop parks there), so the snap-back guard gets
     * this epsilon of slack.
     */
    private const val HOLD_END_EPS_SEC = 1e-6

    /** The playback fraction runs 0..endFraction; values > 1 cover the hold. */
    fun endFraction(shot: ShotResult): Double = 1.0 + LAND_HOLD_SEC / shot.flightTimeSec

    /** The camera for a shot at [playFraction] (0 = impact, 1 = touchdown). */
    fun cameraAt(shot: ShotResult, playFraction: Float): RangeCamera {
        val t = shot.flightTimeSec
        if (t <= 0.0 || shot.samples.isEmpty()) return RangeCamera.STATIC

        val timeSec = playFraction.toDouble() * t
        if (timeSec >= t + LAND_HOLD_SEC - HOLD_END_EPS_SEC) return RangeCamera.STATIC // snapped back

        val overlook = overlookRig(shot)

        // Short shots: the flight ends before delay/blend ever would —
        // never point the camera at empty sky; park at the overlook.
        if (t <= FOLLOW_DELAY_SEC + BLEND_SEC) return overlook

        val engageT = earlyEngageT(shot) ?: FOLLOW_DELAY_SEC
        val blendEnd = engageT + BLEND_SEC
        val apexT = shot.samples.maxByOrNull { it.pz }?.tSec ?: (t / 2.0)
        val descentT = maxOf(apexT, blendEnd)

        return when {
            timeSec < engageT -> RangeCamera.STATIC
            timeSec < blendEnd ->
                lerpCam(
                    RangeCamera.STATIC,
                    chaseRig(sampleAt(shot, timeSec)),
                    smoothstep((timeSec - engageT) / BLEND_SEC),
                )
            timeSec < descentT -> chaseRig(sampleAt(shot, timeSec))
            timeSec < t -> lerpCam(
                chaseRig(sampleAt(shot, descentT)),
                overlook,
                smoothstep(((timeSec - descentT) / (t - descentT)).coerceIn(0.0, 1.0)),
            )
            else -> overlook // landing hold
        }
    }

    /**
     * Behind-and-above the landing point so the camera axis passes through
     * the landing spot at the pitch angle.
     */
    private fun overlookRig(shot: ShotResult): RangeCamera {
        val pitch = Math.toRadians(LAND_PITCH_DEG)
        return RangeCamera(
            x = shot.sideM,
            y = shot.carryM - OVERLOOK_DIST_M * cos(pitch),
            z = OVERLOOK_DIST_M * sin(pitch),
            pitchRad = pitch,
        )
    }

    private fun chaseRig(head: TrajectorySample): RangeCamera =
        RangeCamera(head.px, head.py - CHASE_BACK_M, head.pz + CHASE_UP_M, 0.0)

    private fun sampleAt(shot: ShotResult, t: Double): TrajectorySample =
        shot.samples.lastOrNull { it.tSec <= t } ?: shot.samples.first()

    /** First time the ball's STATIC projection reaches the top of frame. */
    private fun earlyEngageT(shot: ShotResult): Double? {
        for (s in shot.samples) {
            val p = PovProjector.project(RangeCamera.STATIC, s.px, s.py, s.pz) ?: continue
            if (p.v <= EARLY_ENGAGE_V) return s.tSec
        }
        return null
    }

    private fun lerpCam(a: RangeCamera, b: RangeCamera, s: Double): RangeCamera =
        RangeCamera(
            x = a.x + (b.x - a.x) * s,
            y = a.y + (b.y - a.y) * s,
            z = a.z + (b.z - a.z) * s,
            pitchRad = a.pitchRad + (b.pitchRad - a.pitchRad) * s,
        )

    private fun smoothstep(x: Double): Double {
        val c = x.coerceIn(0.0, 1.0)
        return c * c * (3.0 - 2.0 * c)
    }
}
