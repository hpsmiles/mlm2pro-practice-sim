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
     * Near-plane depth for ground layers, in metres. Ground polygons are
     * clipped to this camera depth before projection; the [nearFadeFactor]
     * ramp starts here so a layer clipped at the plane is fully faded.
     */
    const val GROUND_MIN_DEPTH_M = 0.5

    /** Depth at which a ground layer reaches full opacity in [nearFadeFactor]. */
    const val GROUND_FULL_DEPTH_M = 20.0

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
     * axis, so bands stay screen-horizontal under pitch). `v` and depth are
     * invariant in `dx`, so this holds for any `cam.x` — which the chase
     * camera relies on (`cam.x` follows the ball laterally). The
     * [distanceM] argument is a world y.
     */
    fun bandV(cam: RangeCamera, distanceM: Double): Double {
        val dy = distanceM - cam.y
        val dz = -cam.z
        val cosPitch = cos(cam.pitchRad)
        val sinPitch = sin(cam.pitchRad)
        val depth = dy * cosPitch - dz * sinPitch
        return -(dy * sinPitch + dz * cosPitch) / depth
    }

    /**
     * Camera-plane depth of a GROUND point (z = 0) at down-range [y] for
     * [cam]. This is `project()`'s `dy*cos - dz*sin` with `dz = -cam.z`,
     * so it matches the projection's own behind-camera test exactly.
     */
    fun groundDepth(cam: RangeCamera, y: Double): Double {
        val dy = y - cam.y
        val cosPitch = cos(cam.pitchRad)
        val sinPitch = sin(cam.pitchRad)
        return dy * cosPitch + cam.z * sinPitch
    }

    /**
     * Sutherland-Hodgman clip of a closed ground polygon (world (x, y)
     * vertices, z = 0) against the camera near plane: every returned vertex
     * has [groundDepth] >= [minDepthM]. Edges crossing the plane are split
     * with linear interpolation so the silhouette stays continuous instead
     * of popping off wholesale. Returns an empty list when the whole
     * polygon is behind the plane; the caller then skips the layer.
     */
    fun clipGroundPath(
        points: List<Pair<Double, Double>>,
        cam: RangeCamera,
        minDepthM: Double = GROUND_MIN_DEPTH_M,
    ): List<Pair<Double, Double>> {
        if (points.size < 3) return emptyList()
        val out = ArrayList<Pair<Double, Double>>(points.size + 4)
        var prev = points.last()
        var prevDepth = groundDepth(cam, prev.second)
        var prevInside = prevDepth >= minDepthM
        for (curr in points) {
            val currDepth = groundDepth(cam, curr.second)
            val currInside = currDepth >= minDepthM
            if (currInside != prevInside) {
                // Crossing: interpolate along the edge to the exact plane.
                val t = (minDepthM - prevDepth) / (currDepth - prevDepth)
                out.add(prev.first + (curr.first - prev.first) * t to prev.second + (curr.second - prev.second) * t)
            }
            if (currInside) out.add(curr)
            prev = curr
            prevDepth = currDepth
            prevInside = currInside
        }
        return out
    }

    /**
     * Opacity ramp for a ground layer as the camera nears it: 0 at/below
     * [GROUND_MIN_DEPTH_M], 1 at/above [fullDepthM], quintic smoothstep
     * (monotonic, zero derivative at both ends) in between.
     */
    fun nearFadeFactor(nearestDepthM: Double, fullDepthM: Double = GROUND_FULL_DEPTH_M): Double {
        val span = fullDepthM - GROUND_MIN_DEPTH_M
        if (span <= 0.0) return if (nearestDepthM >= fullDepthM) 1.0 else 0.0
        val x = ((nearestDepthM - GROUND_MIN_DEPTH_M) / span).coerceIn(0.0, 1.0)
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0)
    }
}
