// core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfColorsTest.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class GolfColorsTest {
    @Test fun surfacesMatchSpec() {
        assertEquals(Color(0xFF0E1114), GolfColors.Base)
        assertEquals(Color(0xFF0B0E11), GolfColors.Panel)
        assertEquals(Color(0xFF151A1F), GolfColors.Card)
        assertEquals(Color(0xFF28303A), GolfColors.Line)
        assertEquals(Color(0xFF161B21), GolfColors.HoleFairway)
    }

    @Test fun textTiersMatchSpec() {
        assertEquals(Color(0xFFE7EBEE), GolfColors.TextPrimary)
        assertEquals(Color(0xFF8FA0AC), GolfColors.TextSecondary)
        assertEquals(Color(0xFF5C6873), GolfColors.TextMuted)
    }

    @Test fun structuralTealHasAlphaVariants() {
        assertEquals(Color(0xFF3FA7A0), GolfColors.Teal)
        // Deviation (mechanical test-only fix): Color.alpha is Float in Compose,
        // so expected/delta need Float literals; Teal55's byte is 0x8C = 140 ->
        // alpha 140f/255f = 0.54902 (prints as "0.55") — byte-exact keeps 1e-6.
        assertEquals(140f / 255f, GolfColors.Teal55.alpha, 1e-6f)
        assertEquals(0.40f, GolfColors.Teal40.alpha, 1e-6f)
    }

    @Test fun liveAmberHasHaloAndGlow() {
        assertEquals(Color(0xFFF2A93B), GolfColors.Amber)
        // Same Float-literal mechanical fix; 0x33 = 51/255 = 0.2, 0x99 = 153/255 = 0.6.
        assertEquals(0.20f, GolfColors.AmberHalo.alpha, 1e-6f)
        assertEquals(0.60f, GolfColors.AmberGlow.alpha, 1e-6f)
    }

    @Test fun comparisonHuesAreWideSpread() {
        assertEquals(Color(0xFF3FA7A0), GolfColors.Comparison.A)
        assertEquals(Color(0xFF5B9DF9), GolfColors.Comparison.B)
        assertEquals(Color(0xFFD66FD8), GolfColors.Comparison.C)
        assertEquals(Color(0xFFF0D64A), GolfColors.Comparison.D)
    }

    @Test fun semanticColorsMatchSpec() {
        assertEquals(Color(0xFF7BC96F), GolfColors.BleArmedGreen)
        assertEquals(Color(0xFFE86A5E), GolfColors.AlertRed)
    }
}
