package com.hpsmiles.golfsim.fitting

import androidx.compose.ui.graphics.Color
import com.hpsmiles.golfsim.core.designsystem.GolfColors

/**
 * M7 club palette (design notes §0.1): assigned by first-appearance order,
 * distinct from the range's teal history dots / amber last shot. All six
 * entries (A–F) live in the design system's Comparison object — values come
 * from GolfColors (no local hexes) and follow the §0.1 club-position order:
 * A teal (club 1) → B rose (club 2) → C yellow (club 3) → D indigo (club 4,
 * was blue) → E magenta (club 5) → F green (club 6), reordered 2026-10-08 for
 * consecutive hue separation (173°/67°/179°/64°/179°). A 7th+ club cycles
 * back to A (6-club comparisons are the realistic ceiling).
 */
object FittingColors {
    val CLUB: List<Color> = listOf(
        GolfColors.Comparison.A, GolfColors.Comparison.B, GolfColors.Comparison.C, GolfColors.Comparison.D,
        GolfColors.Comparison.E, GolfColors.Comparison.F,
    )

    fun clubColor(index: Int): Color = CLUB[index % CLUB.size]
}
