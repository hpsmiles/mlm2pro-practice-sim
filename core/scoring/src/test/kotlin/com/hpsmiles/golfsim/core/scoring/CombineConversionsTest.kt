package com.hpsmiles.golfsim.core.scoring

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CombineConversionsTest {

    private val old100 = CombineConversions.APPROACH_100M
    private val old150 = CombineConversions.APPROACH_150M
    private val oldDriver = CombineConversions.DRIVER

    @Before
    fun snapshot() {
        // values captured in field initializers above; nothing else needed
    }

    @After
    fun restore() {
        CombineConversions.APPROACH_100M = old100
        CombineConversions.APPROACH_150M = old150
        CombineConversions.DRIVER = oldDriver
    }

    @Test
    fun `interpolates linearly between fitted stations`() {
        CombineConversions.APPROACH_100M = -0.24
        CombineConversions.APPROACH_150M = -0.10
        assertEquals(-0.24, CombineConversions.approachOffsetFor(100.0), 1e-9)
        assertEquals(-0.10, CombineConversions.approachOffsetFor(150.0), 1e-9)
        assertEquals(-0.17, CombineConversions.approachOffsetFor(125.0), 1e-9)
    }

    @Test
    fun `extrapolates linearly outside the fitted range`() {
        CombineConversions.APPROACH_100M = -0.24
        CombineConversions.APPROACH_150M = -0.10
        assertEquals(-0.38, CombineConversions.approachOffsetFor(50.0), 1e-9)
        assertEquals(-0.03, CombineConversions.approachOffsetFor(175.0), 1e-9)
    }
}
