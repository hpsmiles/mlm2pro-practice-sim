package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.data.CombineShotEntity
import com.hpsmiles.golfsim.core.scoring.HandicapEstimate
import com.hpsmiles.golfsim.core.scoring.Lie
import com.hpsmiles.golfsim.core.scoring.handicapMatch

/** Raw scoring facts for one shot — the only fields the SG engine needs. */
data class CombineShotFacts(val shotIndex: Int, val restLie: Lie, val restDistanceM: Double)

fun CombineShot.toFacts(): CombineShotFacts = CombineShotFacts(index, restLie, restDistanceM)

fun CombineShotEntity.toFacts(): CombineShotFacts =
    CombineShotFacts(
        shotIndex,
        // Total parse: an unknown/corrupt stored value degrades to fairway rather than crashing the results render.
        Lie.entries.firstOrNull { it.name == restLie } ?: Lie.FAIRWAY,
        restDistanceM,
    )

/**
 * Aggregated result for one combine station.
 *
 * @param stationIndex index into the owning [CombineCatalogEntry.stations]; consumers use it to look up station geometry.
 * @param chips one formatted chip per station shot, in ascending `shotIndex` order.
 * @param avgValue for [StationKind.APPROACH] the average rest distance to the pin; for [StationKind.DRIVER] the average
 * distance hit toward the baseline (`DRIVER_HOLE_M - avgRest`).
 */
data class StationResult(
    val stationIndex: Int,
    val label: String,
    val estimate: HandicapEstimate,
    val avgLabel: String,
    val avgValue: Double,
    val chips: List<String>,
)

/**
 * Aggregated results for a combine session.
 *
 * @param stations only stations with at least one shot, ascending by `stationIndex`.
 */
data class CombineResults(
    val overall: HandicapEstimate,
    val stations: List<StationResult>,
)

fun combineResults(entry: CombineCatalogEntry, facts: List<CombineShotFacts>): CombineResults {
    require(facts.isNotEmpty()) { "combineResults requires at least one shot" }
    val sorted = facts.sortedBy { it.shotIndex }
    val overall = handicapMatch(
        sorted.map { CombinePlan.shotInput(entry, it.shotIndex, it.restLie, it.restDistanceM) }
    )
    val stations = entry.stations.indices.mapNotNull { stationIndex ->
        val stationFacts = sorted.filter { CombinePlan.stationIndexFor(it.shotIndex) == stationIndex }
        if (stationFacts.isEmpty()) return@mapNotNull null
        val station = entry.stations[stationIndex]
        val inputs = stationFacts.map { CombinePlan.shotInput(entry, it.shotIndex, it.restLie, it.restDistanceM) }
        val estimate = handicapMatch(inputs)
        val avgRest = stationFacts.map { it.restDistanceM }.average()
        val isDriver = station.kind == StationKind.DRIVER
        StationResult(
            stationIndex = stationIndex,
            label = station.label,
            estimate = estimate,
            avgLabel = if (isDriver) "AVG TOTAL" else "AVG TO PIN",
            avgValue = if (isDriver) DRIVER_HOLE_M - avgRest else avgRest,
            chips = stationFacts.map { fact ->
                CombineFormats.chip(
                    handicapMatch(listOf(CombinePlan.shotInput(entry, fact.shotIndex, fact.restLie, fact.restDistanceM))).est
                )
            },
        )
    }
    return CombineResults(overall, stations)
}

fun overallEstimate(entry: CombineCatalogEntry, facts: List<CombineShotFacts>): Double =
    handicapMatch(
        facts.sortedBy { it.shotIndex }
            .map { CombinePlan.shotInput(entry, it.shotIndex, it.restLie, it.restDistanceM) }
    ).est
