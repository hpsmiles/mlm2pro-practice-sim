// app/src/main/kotlin/com/hpsmiles/golfsim/range/PovProjector.kt
package com.hpsmiles.golfsim.range

import kotlin.math.cos
import kotlin.math.sin

/**
 * A range camera: world position (metres) + pitch (nose-down radians,
 * 0 = level). No yaw, no roll — one rotation axis only (spec 2026-09-25).
 */
data class RangeCamera(
    val x: Double,
    val y: Double,
    val z: Double,
    val pitchRad: Double,
) {
    companion object {
        /**
         * The raised static rig behind the tee: an 8 m crane position
         * ("mockup C", 2026-09-25 brainstorm). The tee stays at
         * v = 8 / 21.2 = 0.377 — just above the bottom edge.
         */
        val STATIC = RangeCamera(0.0, -PovProjector.CAM_BACK_M, PovProjector.CAM_HEIGHT_M, 0.0)
    }
}

/**
 * Pinhole projection from range world coordinates to normalized screen
 * coordinates. Pure Kotlin (no Android types) so it is unit-testable on
 * the JVM. The canvas layer converts these normalized values to pixels.
 *
 * World axes (metres): x lateral (+right), y down-range (+away from the
 * hitter), z up. The camera ([RangeCamera]) carries its own world
 * position and nose-down pitch; all distances inside the projection are
 * measured relative to the camera.
 *
 * With camera pitch theta and relative point (dx, dy, dz), dz = z - camZ:
 *   depth = dy*cos(theta) - dz*sin(theta)
 *   u     = dx / depth
 *   v     = -(dy*sin(theta) + dz*cos(theta)) / depth
 * At pitch = 0 this collapses exactly to the old level pinhole:
 *   u = x / (y + CAM_BACK_M), v = (CAM_HEIGHT_M - z) / (y + CAM_BACK_M).
 */
object PovProjector {

    /** Camera height above the hitting mat in the static rig, in metres. */
    const val CAM_HEIGHT_M = 8.0

    /** Camera distance behind the ball (y = 0) in the static rig, in metres. */
    const val CAM_BACK_M = 21.2

    /**
     * Normalized projection of a world point. `u` is lateral position (0 =
     * dead ahead, +right), `v` is vertical position (0 = camera level,
     * +toward the viewer's feet), `scale` is the relative on-screen size
     * of a 1 m object at that depth. All values are independent of
     * viewport size.
     */
    data class ProjectedPoint(val u: Double, val v: Double, val scale: Double)

    /**
     * Projects a world point through [cam], or returns null when the point
     * is at or behind the camera plane (depth <= 0) — such points must not
     * be drawn.
     */
    fun project(cam: RangeCamera, x: Double, y: Double, z: Double): ProjectedPoint? {
        val dx = x - cam.x
        val dy = y - cam.y
        val dz = z - cam.z
        val cosPitch = cos(cam.pitchRad)
        val sinPitch = sin(cam.pitchRad)
        val depth = dy * cosPitch - dz * sinPitch
        if (depth <= 0.0) return null
        return ProjectedPoint(
            u = dx / depth,
            v = -(dy * sinPitch + dz * cosPitch) / depth,
            scale = 1.0 / depth,
        )
    }

    /**
     * The `v` of the ground at a given down-range distance (a horizontal
     * distance band line on the POV canvas — rotation is about the lateral
     * axis, so bands stay screen-horizontal under pitch). Requires
     * `cam.x == 0.0` (bands span the full width). The [distanceM] argument
     * is a world y.
     */
    fun bandV(cam: RangeCamera, distanceM: Double): Double {
        val dy = distanceM - cam.y
        val dz = -cam.z
        val cosPitch = cos(cam.pitchRad)
        val sinPitch = sin(cam.pitchRad)
        val depth = dy * cosPitch - dz * sinPitch
        return -(dy * sinPitch + dz * cosPitch) / depth
    }
}
