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
 *
 * Local hues 7–13 (2026-10-16 reviews): the first set had near-duplicates
 * against the pinned six — aqua ~172° vs A teal ~178°, dull olive ~83° vs
 * F green ~120°, apricot ~28° vs C yellow ~51° — and sky ~215° crowded D
 * indigo ~233°. The current seven occupy the six hue gaps between the pinned
 * colours (one each, chartreuse + spring splitting the yellow→green span,
 * slate staying neutral). Round 2: the E→B gap (magenta 299° → rose 347°,
 * 48.5° wide) caps any single hue at 24.25° from both neighbours — with the
 * six pinned hues and 12 chromatic slots, 24.25° IS the global optimum, so
 * 25–30° everywhere is unreachable without touching pinned colours. The slot
 * therefore keeps its optimal ~322° hue but as a PALE pink (#FF99D9, much
 * lighter than rose B and orchid E) so the three warm chips no longer read
 * as shades of one colour. Minimum chromatic hue separation: ~23.6°.
 */
object BagRamp {

    val hues: List<Color> = listOf(
        GolfColors.Comparison.A, // teal      ~178°
        GolfColors.Comparison.B, // rose      ~348°
        GolfColors.Comparison.C, // yellow    ~51°
        GolfColors.Comparison.D, // indigo    ~233°
        GolfColors.Comparison.E, // magenta   ~300°
        GolfColors.Comparison.F, // green     ~120°
        Color(0xFFE4572E),       // orange    ~14°
        Color(0xFF3D9BE9),       // azure     ~206°
        Color(0xFF9BE01E),       // chartreuse ~81°
        Color(0xFF17D979),       // spring    ~150°
        Color(0xFF9B6FF3),       // violet    ~260°
        Color(0xFFFF99D9),       // pale pink ~322°
        Color(0xFFB0BEC5),       // slate     (neutral)
    )

    val hueCount: Int get() = hues.size

    fun clubColor(index: Int): Color = hues[index.mod(hues.size)]
}
