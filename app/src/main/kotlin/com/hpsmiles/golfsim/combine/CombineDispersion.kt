package com.hpsmiles.golfsim.combine

import kotlin.math.sqrt

/**
 * Pure per-group dispersion statistics for the combine top-down view (M8 post-ship spec §3).
 *
 * World frame is the hole frame used everywhere in the combine: x = lateral (`restX`, metres,
 * +right of the target line), y = down-range (`restY`, metres from the tee). The 1σ ring is
 * axis-aligned in that frame (no covariance term) with semi-axes [Group.sigmaX] / [Group.sigmaY].
 *
 * Standard deviations are POPULATION (÷ n): the shots in hand are the whole population of that
 * station's group, not a sample of a larger one.
 */
object CombineDispersion {

    /** Dispersion of one station's finish points, in metres (world hole coordinates). */
    data class Group(
        val stationIndex: Int,
        /** Coordinate-bearing shots in this group (a lone finish is a dot, not a ring). */
        val count: Int,
        /** Mean lateral offset (restX), metres. */
        val centroidX: Double,
        /** Mean down-range (restY), metres. */
        val centroidY: Double,
        /** Lateral (restX) population standard deviation, metres — the ring's lateral semi-axis. */
        val sigmaX: Double,
        /** Down-range (restY) population standard deviation, metres — the ring's down-range semi-axis. */
        val sigmaY: Double,
        /** Max − min down-range (restY), metres. */
        val longShortSpreadM: Double,
        /** Max − min lateral (restX), metres. */
        val leftRightSpreadM: Double,
        /** Mean rest distance to the station target (pin / baseline), metres. */
        val avgRestM: Double,
    ) {
        /** A 1σ ring needs at least two points; a lone finish draws as a dot only. */
        val hasRing: Boolean get() = count >= 2
    }

    /**
     * One [Group] per catalog station that has at least one coordinate-bearing shot, ascending by
     * station index. Shots without coordinates (pre-v7 sessions) are ignored — a station with no
     * coordinate-bearing shots yields no group, so the view can fall back to its empty state.
     */
    fun groups(entry: CombineCatalogEntry, facts: List<CombineShotFacts>): List<Group> =
        entry.stations.indices.mapNotNull { stationIndex ->
            val points = facts
                .filter { CombinePlan.stationIndexFor(it.shotIndex) == stationIndex }
                .mapNotNull { fact ->
                    val x = fact.restXM ?: return@mapNotNull null
                    val y = fact.restYM ?: return@mapNotNull null
                    Triple(x, y, fact.restDistanceM)
                }
            if (points.isEmpty()) return@mapNotNull null
            val n = points.size
            val mx = points.sumOf { it.first } / n
            val my = points.sumOf { it.second } / n
            val varX = points.sumOf { val d = it.first - mx; d * d } / n
            val varY = points.sumOf { val d = it.second - my; d * d } / n
            Group(
                stationIndex = stationIndex,
                count = n,
                centroidX = mx,
                centroidY = my,
                sigmaX = sqrt(varX),
                sigmaY = sqrt(varY),
                longShortSpreadM = points.maxOf { it.second } - points.minOf { it.second },
                leftRightSpreadM = points.maxOf { it.first } - points.minOf { it.first },
                avgRestM = points.sumOf { it.third } / n,
            )
        }
}
