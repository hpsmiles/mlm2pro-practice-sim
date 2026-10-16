package com.hpsmiles.golfsim.bag

/**
 * The headline gap statistics shown above the chart. Built from the same
 * [GapEntry] list the panel's DISTANCE GAPS block renders, so the strip and the
 * block can never disagree.
 *
 * The aggregates — [averageGapM], [closest], [farthest] — describe **real gaps
 * only**: `GapFlag.INVERTED` pairs (whose `gapM` is `abs(shorter − longer)`, so
 * it can be a small positive number for a club that actually carries further)
 * are counted but never averaged or ranked, so a mislabelled club can never win
 * CLOSEST or drag the average. The three counts ([goodCount], [wideCount],
 * [tightCount]) cover the full list; INVERTED contributes to none of them.
 */
data class GapSummary(
    val averageGapM: Double?,
    val closest: GapEntry?,
    val farthest: GapEntry?,
    val goodCount: Int,
    val wideCount: Int,
    val tightCount: Int,
)

fun summarizeGaps(gaps: List<GapEntry>): GapSummary {
    val real = gaps.filterNot { it.flag == GapFlag.INVERTED }
    return GapSummary(
        averageGapM = if (real.isEmpty()) null else real.map { it.gapM }.average(),
        closest = real.minByOrNull { it.gapM },
        farthest = real.maxByOrNull { it.gapM },
        goodCount = gaps.count { it.flag == GapFlag.HEALTHY },
        wideCount = gaps.count { it.flag == GapFlag.WIDE },
        tightCount = gaps.count { it.flag == GapFlag.TIGHT },
    )
}
