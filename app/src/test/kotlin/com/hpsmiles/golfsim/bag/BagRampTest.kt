package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.designsystem.GolfColors
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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

    @Test
    fun `chromatic hues stay well separated around the wheel`() {
        // 2026-10-16 review: aqua vs teal and olive vs green were
        // near-duplicates. Pin a minimum hue separation between every
        // chromatic pair (slate is a neutral and exempt).
        fun hue(color: Color): Double {
            val r = color.red; val g = color.green; val b = color.blue
            val max = maxOf(r, g, b); val min = minOf(r, g, b)
            // Slate's faint cool tint (max-min ≈ 0.08) reads as a neutral on
            // Base — low-chroma colours are exempt from the hue rule.
            if (max - min < 0.1) return Double.NaN // neutral
            val d = max - min
            return when (max) {
                r -> 60.0 * (((g - b) / d).mod(6.0))
                g -> 60.0 * ((b - r) / d + 2.0)
                else -> 60.0 * ((r - g) / d + 4.0)
            }
        }
        val hues = BagRamp.hues.mapNotNull { hue(it).takeUnless(Double::isNaN) }
        for (i in hues.indices) for (j in i + 1 until hues.size) {
            val sep = minOf(abs(hues[i] - hues[j]), 360.0 - abs(hues[i] - hues[j]))
            assertTrue(
                "hues $i (${hues[i]}) and $j (${hues[j]}) only ${sep}° apart",
                sep >= 20.0,
            )
        }
    }
}
