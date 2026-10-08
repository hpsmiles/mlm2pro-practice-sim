package com.hpsmiles.golfsim.fitting

import androidx.compose.ui.graphics.Color
import com.hpsmiles.golfsim.core.designsystem.GolfColors

/**
 * M7 club palette (design notes §0.1): assigned by first-appearance order,
 * distinct from the range's teal history dots / amber last shot. All six
 * entries (A–F) live in the design system's Comparison object — E green
 * (hue ≈ 120°) fills the gap between D-yellow and A-teal, F rose (hue ≈ 347°)
 * sits opposite A-teal and reads clearly off C-magenta. A 7th+ club cycles
 * back to A (6-club comparisons are the realistic ceiling).
 */
object FittingColors {
    val CLUB: List<Color> = listOf(
        GolfColors.Comparison.A, GolfColors.Comparison.B, GolfColors.Comparison.C, GolfColors.Comparison.D,
        GolfColors.Comparison.E, GolfColors.Comparison.F,
    )

    fun clubColor(index: Int): Color = CLUB[index % CLUB.size]
}
