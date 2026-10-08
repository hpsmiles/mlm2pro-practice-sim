package com.hpsmiles.golfsim.fitting

import androidx.compose.ui.graphics.Color
import com.hpsmiles.golfsim.core.designsystem.GolfColors

/**
 * M7 club palette (design notes §0.1): assigned by first-appearance order,
 * distinct from the range's teal history dots / amber last shot. A–D already
 * exist in the design system's Comparison object; E/F extend the wheel to 6
 * — E green (hue ≈ 120°) fills the gap between D-yellow and A-teal, F rose
 * (hue ≈ 347°) sits opposite A-teal and reads clearly off C-magenta. A 7th+
 * club cycles back to A (6-club comparisons are the realistic ceiling).
 */
object FittingColors {
    private val ComparisonE = Color(0xFF6FD86F)
    private val ComparisonF = Color(0xFFE85C7A)

    val CLUB: List<Color> = listOf(
        GolfColors.Comparison.A, GolfColors.Comparison.B, GolfColors.Comparison.C, GolfColors.Comparison.D,
        ComparisonE, ComparisonF,
    )

    fun clubColor(index: Int): Color = CLUB[index % CLUB.size]
}
