// app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSigns.kt
package com.hpsmiles.golfsim.range

/** One standing range distance sign (pure data; mockup range-signs-alternating). */
data class RangeSign(
    val distanceM: Int,
    val xM: Double, // world lateral position (side applied)
    val side: Int,  // +1 right of centre, -1 left
)

/**
 * M5x E6: standing signs replace the painted band lines. Board ~3.0 × 1.9 m
 * on ~1.2 m posts (top edge z ≈ 3.1). Shared by POV and top-down renderers;
 * world-fixed, so the follow cam is correct by construction.
 */
object RangeSigns {
    const val BOARD_W_M = 3.0
    const val BOARD_HALF_W_M = 1.5
    const val POST_H_M = 1.2
    const val BOARD_TOP_Z_M = 3.1
    /** Just outside the fairway edge — clear of the greens at ±12 m. */
    const val EDGE_OFFSET_M = 2.5
    val DISTANCES_M = (50..350 step 50).toList()

    /** One sign per distance, alternating sides starting RIGHT at 50 m. */
    fun signPlan(): List<RangeSign> = DISTANCES_M.mapIndexed { i, d ->
        val side = if (i % 2 == 0) 1 else -1
        RangeSign(d, side * (RangeScene.fairwayHalfWidth(d.toDouble()) + EDGE_OFFSET_M), side)
    }
}
