// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfMotion.kt
package com.hpsmiles.golfsim.core.designsystem

/**
 * Motion durations in milliseconds (M3 spec §5). Replay plays at real duration by
 * default; 2x/4x are user toggle steps. Easings use Material standard curves at
 * call sites. Metrics NEVER wait for animation (M4 instant-feedback rule).
 */
object GolfMotion {
    const val LandingPulseMs = 500
    const val TracerDrawMs = 700
    const val FadeInMs = 150

    const val ReplaySpeed1x = 1
    const val ReplaySpeed2x = 2
    const val ReplaySpeed4x = 4
}
