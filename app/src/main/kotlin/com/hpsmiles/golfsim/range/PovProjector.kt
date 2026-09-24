// app/src/main/kotlin/com/hpsmiles/golfsim/range/PovProjector.kt
package com.hpsmiles.golfsim.range

/**
 * Pinhole projection from range world coordinates to normalized screen
 * coordinates. Pure Kotlin (no Android types) so it is unit-testable on the
 * JVM. The canvas layer converts these normalized values to pixels.
 *
 * World axes (metres): x lateral (+right), y down-range (+away from the
 * hitter), z up. Camera: [CAM_BACK_M] BEHIND the ball (shed decision
 * 2026-09-24: the launch must be visible, so the eye sits back from the tee),
 * [CAM_HEIGHT_M] above the ground, looking straight down +y with no tilt —
 * the horizon is exactly at v = 0. All distances inside the projection are
 * measured from the camera plane: depth = y + CAM_BACK_M.
 */
object PovProjector {

    /** Eye height of the camera above the hitting mat, in metres. */
    const val CAM_HEIGHT_M = 1.7

    /**
     * Camera distance behind the ball (y = 0), in metres. 4.5 puts the tee at
     * v = 1.7/4.5 = 0.378 — ~95% of the way down the 0.7h ground area on the
     * landscape tablet (focal 1.1w), i.e. the launch point is visible just
     * above the bottom edge (user request 2026-09-24).
     */
    const val CAM_BACK_M = 4.5

    /**
     * Normalized projection of a world point. `u` is lateral position (0 =
     * dead ahead, +right), `v` is vertical position (0 = horizon, +toward the
     * viewer's feet), `scale` is the relative on-screen size of a 1 m object
     * at that depth. All values are independent of viewport size.
     */
    data class ProjectedPoint(val u: Double, val v: Double, val scale: Double)

    /**
     * Projects a world point to the screen, or returns null when the point is
     * at or behind the camera plane (y <= -CAM_BACK_M) — such points must not
     * be drawn.
     */
    fun project(x: Double, y: Double, z: Double): ProjectedPoint? {
        val depth = y + CAM_BACK_M
        if (depth <= 0.0) return null
        return ProjectedPoint(
            u = x / depth,
            v = (CAM_HEIGHT_M - z) / depth,
            scale = 1.0 / depth,
        )
    }

    /**
     * The `v` of the ground at a given down-range distance (a horizontal
     * distance band line on the POV canvas). The [distanceM] argument is a
     * world y; band lines sit at depth y + [CAM_BACK_M] from the camera.
     */
    fun bandV(distanceM: Double): Double = CAM_HEIGHT_M / (distanceM + CAM_BACK_M)
}
