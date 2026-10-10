package com.hpsmiles.golfsim.core.scoring

/** Expected strokes for [band] playing from [lie] at [metres] from the hole. Clamps at grid ends. */
fun expectedStrokes(band: SkillBand, lie: Lie, metres: Double): Double =
    interpolate(lie.distances, lie.valuesFor(band), metres)

/** Strokes gained by a shot that started at (startLie, startM) and rests at (endLie, endM). */
fun strokesGained(
    band: SkillBand,
    startLie: Lie,
    startM: Double,
    endLie: Lie,
    endM: Double,
): Double =
    expectedStrokes(band, startLie, startM) - expectedStrokes(band, endLie, endM) - 1.0

internal fun interpolate(xs: DoubleArray, ys: DoubleArray, x: Double): Double {
    if (x <= xs.first()) return ys.first()
    if (x >= xs.last()) return ys.last()
    var lo = 0
    var hi = xs.size - 1
    while (hi - lo > 1) {
        val mid = (lo + hi) / 2
        if (xs[mid] <= x) lo = mid else hi = mid
    }
    val f = (x - xs[lo]) / (xs[hi] - xs[lo])
    return ys[lo] + f * (ys[hi] - ys[lo])
}
