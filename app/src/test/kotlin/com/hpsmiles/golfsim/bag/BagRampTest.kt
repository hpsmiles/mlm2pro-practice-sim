package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.designsystem.GolfColors
import org.junit.Assert.assertEquals
import org.junit.Test

class BagRampTest {

    @Test
    fun `thirteen distinct hues`() {
        assertEquals(13, BagRamp.hueCount)
        assertEquals(BagRamp.hueCount, BagRamp.hues.toSet().size)
    }

    @Test
    fun `first six are the app comparison colours`() {
        assertEquals(
            listOf(
                GolfColors.Comparison.A, GolfColors.Comparison.B, GolfColors.Comparison.C,
                GolfColors.Comparison.D, GolfColors.Comparison.E, GolfColors.Comparison.F,
            ),
            BagRamp.hues.take(6),
        )
    }

    @Test
    fun `assignment is positional by bag order and cycles past the end`() {
        assertEquals(BagRamp.hues[0], BagRamp.clubColor(0))
        assertEquals(BagRamp.hues[12], BagRamp.clubColor(12))
        assertEquals(BagRamp.hues[0], BagRamp.clubColor(13))
        assertEquals(BagRamp.hues[12], BagRamp.clubColor(-1))
    }
}
