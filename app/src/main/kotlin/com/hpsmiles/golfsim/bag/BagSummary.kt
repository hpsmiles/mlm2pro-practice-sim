package com.hpsmiles.golfsim.bag

/**
 * The headline gap statistics shown above the chart. Built from the same
 * [GapEntry] list the panel's DISTANCE GAPS block renders, so the strip and the
 * block can never disagree.
 */
data class GapSummary(
    val averageGapM: Double?,
    val closest: GapEntry?,
    val farthest: GapEntry?,
    val goodCount: Int,
    val wideCount: Int,
    val tightCount: Int,
)

fun summarizeGaps(gaps: List<GapEntry>): GapSummary = GapSummary(
    averageGapM = if (gaps.isEmpty()) null else gaps.map { it.gapM }.average(),
    closest = gaps.minByOrNull { it.gapM },
    farthest = gaps.maxByOrNull { it.gapM },
    goodCount = gaps.count { it.flag == GapFlag.HEALTHY },
    wideCount = gaps.count { it.flag == GapFlag.WIDE },
    tightCount = gaps.count { it.flag == GapFlag.TIGHT },
)
