// core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfMotionTest.kt
package com.hpsmiles.golfsim.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class GolfMotionTest {
    @Test fun durationsMatchSpec() {
        assertEquals(500, GolfMotion.LandingPulseMs)
        assertEquals(700, GolfMotion.TracerDrawMs)
        assertEquals(150, GolfMotion.FadeInMs)
    }

    @Test fun replaySpeedsIncludeToggleSteps() {
        assertEquals(1, GolfMotion.ReplaySpeed1x)
        assertEquals(2, GolfMotion.ReplaySpeed2x)
        assertEquals(4, GolfMotion.ReplaySpeed4x)
    }
}
