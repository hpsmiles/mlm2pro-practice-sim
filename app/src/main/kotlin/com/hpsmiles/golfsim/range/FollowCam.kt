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
 *   blend    1.4 s              quintic smoothstep STATIC -> chase rig (target moves)
 *   chase    -> apex            drawn ball + (0, -CHASE_BACK_M, +CHASE_UP_M), pitch 0
 *   descent  apex -> touchdown  lerp the MOVING chase rig -> overlook rig
 *                              (no halt at the apex); pitch eases
 *                              0 -> LAND_PITCH_DEG on the same progress
 *   hold     LAND_HOLD_SEC      overlook rig parked
 *   snap back after hold        cut to STATIC (tracer + landing marker persist)
 *
 * The rig follows the DRAWN flight ([scaledSamples]: big apexes clamped to
 * the top 8% of the frame), not the raw samples — the camera must ride the
 * trajectory the player actually sees (user gate 2026-09-25).
 */
object FollowCam {

    /** Static window before the follow cam engages. */
    const val FOLLOW_DELAY_SEC = 1.0

    /** Cross-fade from the static rig to the chase rig. */
    const val BLEND_SEC = 1.4

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
     * Default top-8%-of-frame clamp line for [scaledSamples]: the value for
     * the reference tablet geometry. The canvas passes its live
     * geometry-derived apexVMin (and passes the same to [cameraAt]) so the
     * camera and the drawn flight always agree.
     */
    const val REFERENCE_APEX_VMIN = -0.125

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

    /**
     * Frame-adjusted trajectory the canvas draws: big apexes are scaled
     * down so the flight stays inside the top 8% of the STATIC frame
     * (moved here from the canvas so the follow camera can chase the same
     * path the player sees — chasing the raw samples let big drives dive
     * out the bottom of the chase frame, user report 2026-09-25).
     *
     * The scale is a single scalar computed against the STATIC rig, so
     * the drawn geometry does not breathe while the follow cam moves.
     * [apexVMin] is the frame's clamp line (v of the apex as seen from
     * the STATIC rig); the canvas derives it from its live geometry.
     */
    fun scaledSamples(
        shot: ShotResult,
        apexVMin: Double = REFERENCE_APEX_VMIN,
    ): List<TrajectorySample> {
        if (shot.samples.isEmpty()) return emptyList()
        val yApex = shot.carryM / 2.0
        val depthApex = yApex + PovProjector.CAM_BACK_M
        val vApex = (PovProjector.CAM_HEIGHT_M - shot.apexM) / depthApex
        val apexScale = if (vApex < apexVMin) {
            ((PovProjector.CAM_HEIGHT_M - apexVMin * depthApex) / shot.apexM).coerceIn(0.1, 1.0)
        } else {
            1.0
        }
        if (apexScale >= 1.0) return shot.samples
        return shot.samples.map { it.copy(pz = it.pz * apexScale) }
    }

    /**
     * The camera for a shot at [playFraction] (0 = impact, 1 = touchdown).
     * [apexVMin] is the frame's top-8% clamp line — pass the same value
     * the canvas uses so the chase follows the exact drawn flight.
     */
    fun cameraAt(
        shot: ShotResult,
        playFraction: Float,
        apexVMin: Double = REFERENCE_APEX_VMIN,
    ): RangeCamera {
        val t = shot.flightTimeSec
        if (t <= 0.0 || shot.samples.isEmpty()) return RangeCamera.STATIC

        val timeSec = playFraction.toDouble() * t
        if (timeSec >= t + LAND_HOLD_SEC - HOLD_END_EPS_SEC) return RangeCamera.STATIC // snapped back

        val overlook = overlookRig(shot)

        // The drawn flight: the chase must follow the frame-scaled path
        // the player sees, or big apexes leave the chase frame.
        val drawn = scaledSamples(shot, apexVMin)

        // Short shots: the flight ends before delay/blend ever would —
        // never point the camera at empty sky; park at the overlook.
        if (t <= FOLLOW_DELAY_SEC + BLEND_SEC) return overlook

        val engageT = earlyEngageT(drawn)?.coerceAtMost(FOLLOW_DELAY_SEC) ?: FOLLOW_DELAY_SEC
        val blendEnd = engageT + BLEND_SEC
        val apexT = drawn.maxByOrNull { it.pz }?.tSec ?: (t / 2.0)
        val descentT = maxOf(apexT, blendEnd)

        return when {
            timeSec < engageT -> RangeCamera.STATIC
            timeSec < blendEnd ->
                lerpCam(
                    RangeCamera.STATIC,
                    chaseRig(sampleAt(drawn, timeSec)),
                    smoothstep((timeSec - engageT) / BLEND_SEC),
                )
            timeSec < descentT -> chaseRig(sampleAt(drawn, timeSec))
            timeSec < t -> lerpCam(
                chaseRig(sampleAt(drawn, timeSec)), // moving: no halt at the apex
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

    private fun sampleAt(samples: List<TrajectorySample>, t: Double): TrajectorySample =
        samples.lastOrNull { it.tSec <= t } ?: samples.first()

    /** First time the drawn flight's STATIC projection reaches the top of frame. */
    private fun earlyEngageT(samples: List<TrajectorySample>): Double? {
        for (s in samples) {
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

    /**
     * Quintic smoothstep (6s^5 - 15s^4 + 10s^3): zero camera velocity at
     * both ends like the classic cubic, but a softer, longer-feeling ramp
     * through the middle of the blend.
     */
    private fun smoothstep(x: Double): Double {
        val c = x.coerceIn(0.0, 1.0)
        return c * c * c * (c * (c * 6.0 - 15.0) + 10.0)
    }
}
