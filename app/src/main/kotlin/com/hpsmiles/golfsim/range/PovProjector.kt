// app/src/main/kotlin/com/hpsmiles/golfsim/range/PovProjector.kt
package com.hpsmiles.golfsim.range

/**
 * Pinhole projection from range world coordinates to normalized screen
 * coordinates. Pure Kotlin (no Android types) so it is unit-testable on the
 * JVM. The canvas layer converts these normalized values to pixels.
 *
 * World axes (metres): x lateral (+right), y down-range (+away from the
 * hitter), z up. Camera: at the origin, [CAM_HEIGHT_M] above the ground,
 * looking straight down +y with no tilt — the horizon is exactly at v = 0.
 */
object PovProjector {

    /** Eye height of the camera above the hitting mat, in metres. */
    const val CAM_HEIGHT_M = 1.7

    /**
     * Normalized projection of a world point. `u` is lateral position (0 =
     * dead ahead, +right), `v` is vertical position (0 = horizon, +toward the
     * viewer's feet), `scale` is the relative on-screen size of a 1 m object
     * at that depth. All values are independent of viewport size.
     */
    data class ProjectedPoint(val u: Double, val v: Double, val scale: Double)

    /**
     * Projects a world point to the screen, or returns null when the point is
     * at or behind the camera plane (y <= 0) — such points must not be drawn.
     */
    fun project(x: Double, y: Double, z: Double): ProjectedPoint? {
        if (y <= 0.0) return null
        return ProjectedPoint(
            u = x / y,
            v = (CAM_HEIGHT_M - z) / y,
            scale = 1.0 / y,
        )
    }

    /**
     * The `v` of the ground at a given down-range distance (a horizontal
     * distance band line on the POV canvas).
     */
    fun bandV(distanceM: Double): Double = CAM_HEIGHT_M / distanceM
}
