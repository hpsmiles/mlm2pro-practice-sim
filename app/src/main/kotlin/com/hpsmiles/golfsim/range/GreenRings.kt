package com.hpsmiles.golfsim.range

import kotlin.math.ceil

/**
 * Concentric-ring green styling (user request 2026-10-15): greens render as
 * alternating very-light / light green rings, each ~2 m wide, with the
 * outermost ring made thinner when the green radius is not a multiple of the
 * band width so the rings always fill exactly out to the green radius.
 *
 * Pure geometry only — the POV and top-down painters pick up the radii and
 * alternate colours themselves, so this stays unit-testable on the JVM.
 */
object GreenRings {

    /** Nominal ring width in metres. */
    const val BAND_M = 2.0

    /**
     * Ring boundary radii, outermost first, always ending with the full green
     * radius and stepping inward by [bandM]. Inner rings stay exactly 2 m;
     * the outermost absorbs the remainder (thinner when
     * `radiusM % bandM != 0`). Example: radius 5 m -> [5.0, 4.0, 2.0] — a
     * 1 m outer ring, then two 2 m rings filled to the centre.
     */
    fun boundaryRadii(radiusM: Double, bandM: Double = BAND_M): List<Double> {
        if (radiusM <= 0.0) return emptyList()
        val n = ceil(radiusM / bandM).toInt().coerceAtLeast(1)
        val radii = ArrayList<Double>(n)
        radii.add(radiusM)
        var r = radiusM - (radiusM - (n - 1) * bandM)
        while (r > 1e-9) {
            radii.add(r)
            r -= bandM
        }
        return radii
    }

    /**
     * Colour alternation by ring index (0 = outermost): even rings take the
     * very-light tone, odd rings the light tone, so neighbours always
     * contrast regardless of how many rings a green has.
     */
    fun isVeryLightRing(index: Int): Boolean = index % 2 == 0
}
