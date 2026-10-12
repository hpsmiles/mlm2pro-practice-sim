package com.hpsmiles.golfsim.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class CombineConversionsTest {

    // Constants are pinned (task 9) — assertions are constant-independent, so the fitted
    // literals can drift without breaking the interpolation/extrapolation structure tests.
    @Test
    fun `interpolates linearly between fitted stations`() {
        assertEquals(CombineConversions.APPROACH_100M, CombineConversions.approachOffsetFor(100.0), 1e-9)
        assertEquals(CombineConversions.APPROACH_150M, CombineConversions.approachOffsetFor(150.0), 1e-9)
        assertEquals(
            (CombineConversions.APPROACH_100M + CombineConversions.APPROACH_150M) / 2.0,
            CombineConversions.approachOffsetFor(125.0),
            1e-9,
        )
    }

    @Test
    fun `extrapolates linearly outside the fitted range`() {
        assertEquals(
            2.0 * CombineConversions.APPROACH_100M - CombineConversions.APPROACH_150M,
            CombineConversions.approachOffsetFor(50.0),
            1e-9,
        )
        assertEquals(
            1.5 * CombineConversions.APPROACH_150M - 0.5 * CombineConversions.APPROACH_100M,
            CombineConversions.approachOffsetFor(175.0),
            1e-9,
        )
    }
}
