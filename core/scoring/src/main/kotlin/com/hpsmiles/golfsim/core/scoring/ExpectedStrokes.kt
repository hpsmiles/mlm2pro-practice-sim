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

/** Driver-station hole baseline in metres (scoring-only; the hole is invisible in the scene). */
const val DRIVER_HOLE_M = 340.0

/**
 * Strokes gained by a drive at [band], rated against the position a golfer of that level
 * would typically leave themselves: the band's [SkillBand.typicalDriveM] leaves a reference
 * rest of `DRIVER_HOLE_M - typicalDriveM` on the fairway. The comparison is position-to-position
 * (no `- 1`): the reference already represents a drive, so a drive that leaves the golfer where
 * a player of that level would typically be scores 0.0.
 */
fun drivingStrokesGained(band: SkillBand, restLie: Lie, restDistanceM: Double): Double =
    expectedStrokes(band, Lie.FAIRWAY, DRIVER_HOLE_M - band.typicalDriveM) -
        expectedStrokes(band, restLie, restDistanceM)

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
