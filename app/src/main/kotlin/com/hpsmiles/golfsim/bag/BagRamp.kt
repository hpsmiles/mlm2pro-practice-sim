package com.hpsmiles.golfsim.bag

import androidx.compose.ui.graphics.Color
import com.hpsmiles.golfsim.core.designsystem.GolfColors

/**
 * One distinct hue per club, assigned positionally by bag order — the same rule
 * as FittingColors.clubColor, so the arcs, the landing labels, the top-down
 * legend and the rings always agree with each other.
 *
 * The first six are GolfColors.Comparison.A..F so a short bag looks like every
 * other comparison view in the app; seven more follow so a full bag (up to 13
 * clubs — putters and TEST clubs are excluded from mapping) never repeats.
 * Beyond 13 the ramp cycles, which is documented and not expected.
 *
 * Known, accepted collision: hue 0 is Comparison.A, which is also
 * GolfColors.Teal — the "good" gap verdict colour. The ramp appears in the
 * chart and the top-down legend; the verdict colours appear in the summary
 * strip and the panel's gap block, so they never sit in the same region.
 */
object BagRamp {

    val hues: List<Color> = listOf(
        GolfColors.Comparison.A, // teal
        GolfColors.Comparison.B, // rose
        GolfColors.Comparison.C, // yellow
        GolfColors.Comparison.D, // indigo
        GolfColors.Comparison.E, // magenta
        GolfColors.Comparison.F, // green
        Color(0xFFE4572E),       // orange
        Color(0xFF5B9DF9),       // sky
        Color(0xFF8FBF4A),       // olive
        Color(0xFFC77DFF),       // violet
        Color(0xFF57D6C4),       // aqua
        Color(0xFFFFB86B),       // apricot
        Color(0xFFB0BEC5),       // slate
    )

    val hueCount: Int get() = hues.size

    fun clubColor(index: Int): Color = hues[index.mod(hues.size)]
}
