package com.hpsmiles.golfsim.bag

import kotlin.math.abs

/** Gap flag between two adjacent mapped clubs in bag order (spec §6). */
enum class GapFlag { TIGHT, HEALTHY, WIDE, INVERTED }

data class GapEntry(
    val longerClub: String,   // upper row (earlier in bag order)
    val shorterClub: String,  // lower row
    val gapM: Double,
    val flag: GapFlag,
)

/**
 * Adjacent-club gap flags over the medians of the clubs SHOWN, in bag order
 * (spec §6 + §9: gaps only between adjacent mapped clubs — the caller passes
 * exactly the rows with data). Thresholds: tight < 8 m, healthy 8–20 m,
 * wide > 20 m; inverted when the shorter club's median ≥ the longer's
 * (mislabeled club or genuine problem). Single club → no gaps.
 */
object GapAnalysis {
    const val TIGHT_MAX_M = 8.0
    const val WIDE_MIN_M = 20.0

    fun analyze(clubsInBagOrder: List<Pair<String, Double>>): List<GapEntry> {
        if (clubsInBagOrder.size < 2) return emptyList()
        val entries = mutableListOf<GapEntry>()
        for (i in 0 until clubsInBagOrder.size - 1) {
            val (upperName, upperMedian) = clubsInBagOrder[i]
            val (lowerName, lowerMedian) = clubsInBagOrder[i + 1]
            val gap = upperMedian - lowerMedian
            val flag = when {
                gap <= 0.0 -> GapFlag.INVERTED
                gap < TIGHT_MAX_M -> GapFlag.TIGHT
                gap > WIDE_MIN_M -> GapFlag.WIDE
                else -> GapFlag.HEALTHY
            }
            entries.add(GapEntry(upperName, lowerName, abs(gap), flag))
        }
        return entries
    }
}
