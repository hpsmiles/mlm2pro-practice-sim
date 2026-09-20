// core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfSpacingTest.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class GolfSpacingTest {
    @Test fun spacingScaleMatchesSpec() {
        assertEquals(4.dp, GolfSpacing.Xs)
        assertEquals(8.dp, GolfSpacing.Sm)
        assertEquals(12.dp, GolfSpacing.Md)
        assertEquals(16.dp, GolfSpacing.Lg)
        assertEquals(24.dp, GolfSpacing.Xl)
        assertEquals(32.dp, GolfSpacing.Xxl)
    }

    @Test fun componentDimensionsMatchSpec() {
        // 72 dp: fits "SETTINGS" unwrapped in the rail button (bench finding
        // — 56 dp wrapped the final S onto a second line).
        assertEquals(72.dp, GolfSpacing.NavRailWidth)
        assertEquals(24.dp, GolfSpacing.StatusStripHeight)
        assertEquals(14.dp, GolfSpacing.CornerCard)
    }
}
