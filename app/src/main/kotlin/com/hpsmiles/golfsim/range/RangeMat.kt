// app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeMat.kt
package com.hpsmiles.golfsim.range

import androidx.compose.ui.graphics.Color

/**
 * Range mat geometry (spec 2026-09-25, approved mockup B2): commercial
 * rubber-base + turf-strip mat, 2.5 x 2.5 m, ball 0.35 m from the right
 * edge (right-handed golfer — the base extends left toward the stance
 * area). The ball sits at world (0, 0); the mat's near edge extends
 * behind the tee, running "up to the hitter's feet".
 *
 * Pure constants, no behavior — drawing lives in the canvases.
 */
object RangeMat {

    // Rubber base: 2.5 x 2.5 m.
    const val BASE_X_MIN = -2.15   // ball 0.35 m from the right edge
    const val BASE_X_MAX = 0.35
    const val BASE_Y_MIN = -1.25   // world y (down-range); near edge behind the tee
    const val BASE_Y_MAX = 1.25

    // Turf strip inset into the base.
    const val STRIP_X_MIN = -1.75
    const val STRIP_X_MAX = 0.05
    const val STRIP_Y_MIN = -0.95
    const val STRIP_Y_MAX = 0.95

    // Thin hitting line through the ball, length of the strip.
    const val LINE_HALF_WIDTH_M = 0.01

    // Palette (approved mockup B2).
    val BASE = Color(0xFF22262B)
    val BASE_EDGE = Color(0xFF16191D)
    val STRIP = Color(0xFF3B7D4C)
    val STRIP_EDGE = Color(0xFF2E5F3B)
    val HITTING_LINE = Color(0xFF356B44)
}
