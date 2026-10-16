package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.record.ClubType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Gap flag between two adjacent mapped clubs in bag order (spec §6). */
enum class GapFlag { TIGHT, HEALTHY, WIDE, INVERTED }

data class GapEntry(
    val longerClub: String,   // upper row (earlier in bag order)
    val shorterClub: String,  // lower row
    val gapM: Double,
    val flag: GapFlag,
    /** This pair's healthy band — printed by the UI on flagged lines. */
    val bandLowM: Double = 0.0,
    val bandHighM: Double = 0.0,
)

/**
 * Adjacent-club gap flags over the medians of the clubs SHOWN, in bag order
 * (spec §6 + §9: gaps only between adjacent mapped clubs — the caller passes
 * exactly the rows with data). Healthy band (2026-10-16, research-backed):
 * 4–10% of the LONGER club's carry, clamped to [BAND_FLOOR_M]–[BAND_CAP_M].
 * Published fitting guidance puts iron-to-iron gaps at 9–14 m for an average
 * player, tighter in wedges and wider at the long end, scaling with speed
 * (short hitters 5–8 m, long hitters 9–16 m); the percentage band reproduces
 * those absolute numbers at every speed. A gap above ~20 m is a "hole" at
 * every speed — hence the hard cap. Inverted when the shorter club's median
 * ≥ the longer's (mislabeled club or genuine problem). Single club → no gaps.
 */
object GapAnalysis {

    /** Healthy-gap band as a fraction of the LONGER club's carry (2026-10-16). */
    const val BAND_LOW_FRACTION = 0.04
    const val BAND_HIGH_FRACTION = 0.10

    /** Absolute clamps: never a sub-3 m target; never a target above the 20 m "hole" line. */
    const val BAND_FLOOR_M = 3.0
    const val BAND_CAP_M = 20.0

    fun bandLowM(longerCarryM: Double): Double = max(BAND_FLOOR_M, BAND_LOW_FRACTION * longerCarryM)
    fun bandHighM(longerCarryM: Double): Double = min(BAND_CAP_M, BAND_HIGH_FRACTION * longerCarryM)

    /** Flag for one pair: [gapM] against the band set by the LONGER club's carry. */
    fun flagFor(longerCarryM: Double, gapM: Double): GapFlag = when {
        gapM <= 0.0 -> GapFlag.INVERTED
        gapM < bandLowM(longerCarryM) -> GapFlag.TIGHT
        gapM > bandHighM(longerCarryM) -> GapFlag.WIDE
        else -> GapFlag.HEALTHY
    }

    /**
     * Generic list analysis over arbitrary (name, carry) pairs already in
     * long→short order — no ordering, NO filtering. Driver exclusion happens
     * only in [analyzeByClubIdentity].
     */
    fun analyze(clubsInBagOrder: List<Pair<String, Double>>): List<GapEntry> {
        if (clubsInBagOrder.size < 2) return emptyList()
        val entries = mutableListOf<GapEntry>()
        for (i in 0 until clubsInBagOrder.size - 1) {
            val (upperName, upperMedian) = clubsInBagOrder[i]
            val (lowerName, lowerMedian) = clubsInBagOrder[i + 1]
            val gap = upperMedian - lowerMedian
            entries.add(
                GapEntry(
                    longerClub = upperName,
                    shorterClub = lowerName,
                    gapM = abs(gap),
                    flag = flagFor(upperMedian, gap),
                    bandLowM = bandLowM(upperMedian),
                    bandHighM = bandHighM(upperMedian),
                ),
            )
        }
        return entries
    }

    /** Specialist clubs sit outside the gapping chain: the driver is a tee club
     *  with a different role, so the chain stops at the longest fairway club. */
    val SPECIALIST_TYPES = setOf(ClubType.DRIVER)

    /**
     * Canonical long→short ordering by club IDENTITY, independent of the
     * persisted test order (WEDGE_FIRST persists short→long, RANDOM a
     * shuffled permutation — neither is bag order). Primary key:
     * [ClubType.ordinal] (declaration order IS bag order: DRIVER … WEDGE);
     * tiebreak within a type: median carry descending (longer club first).
     */
    fun orderedByBag(mediansByClub: List<Triple<String, ClubType, Double>>): List<Pair<String, Double>> =
        mediansByClub
            .sortedWith(
                // ordinal ASCENDING = bag order (DRIVER=0 first); within a
                // type the LONGER median comes first.
                compareBy<Triple<String, ClubType, Double>> { it.second.ordinal }
                    .thenByDescending { it.third },
            )
            .map { it.first to it.third }

    /**
     * [analyze] fed clubs in canonical long→short order derived from club
     * identity, with specialist clubs ([SPECIALIST_TYPES], the driver)
     * dropped first so no gap line involves them. Callers pass the mapped
     * medians in whatever order they happened to build them.
     */
    fun analyzeByClubIdentity(mediansByClub: List<Triple<String, ClubType, Double>>): List<GapEntry> =
        analyze(orderedByBag(mediansByClub.filterNot { it.second in SPECIALIST_TYPES }))
}
