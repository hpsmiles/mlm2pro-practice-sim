package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.data.CombineShotEntity
import com.hpsmiles.golfsim.core.scoring.HandicapEstimate
import com.hpsmiles.golfsim.core.scoring.Lie
import com.hpsmiles.golfsim.core.scoring.SkillBand
import com.hpsmiles.golfsim.core.scoring.drivingStrokesGained
import com.hpsmiles.golfsim.core.scoring.handicapMatchSg
import com.hpsmiles.golfsim.core.scoring.strokesGained

/**
 * Raw scoring facts for one shot — the fields the SG engine needs, plus the finish
 * coordinates the top-down dispersion view plots ([restXM]/[restYM], null for shots
 * recorded before Room v7 or when the simulator produced no rest point).
 */
data class CombineShotFacts(
    val shotIndex: Int,
    val restLie: Lie,
    val restDistanceM: Double,
    val restXM: Double? = null,
    val restYM: Double? = null,
)

fun CombineShot.toFacts(): CombineShotFacts =
    CombineShotFacts(
        index,
        restLie,
        restDistanceM,
        display.shotResult.restX,
        display.shotResult.restY,
    )

fun CombineShotEntity.toFacts(): CombineShotFacts =
    CombineShotFacts(
        shotIndex,
        // Total parse: an unknown/corrupt stored value degrades to fairway rather than crashing the results render.
        Lie.entries.firstOrNull { it.name == restLie } ?: Lie.FAIRWAY,
        restDistanceM,
        restXM,
        restYM,
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
 * @param overall the four-band solve across all shots.
 * @param lockedBand the benchmark band the completed session locks to — the anchor nearest
 *   [overall]'s estimate (see [SkillBand.nearestTo]). Every per-shot ledger SG is scored
 *   against this band.
 * @param stations only stations with at least one shot, ascending by `stationIndex`.
 */
data class CombineResults(
    val overall: HandicapEstimate,
    val lockedBand: SkillBand,
    val stations: List<StationResult>,
)

fun combineResults(entry: CombineCatalogEntry, facts: List<CombineShotFacts>): CombineResults {
    require(facts.isNotEmpty()) { "combineResults requires at least one shot" }
    val sorted = facts.sortedBy { it.shotIndex }
    // Overall averages per-band SGs across all shots: DRIVER shots contribute their
    // baseline-relative (position-to-position) SG, APPROACH shots their TEE-table SG.
    val overall = handicapMatchSg(sorted.map { perShotSg(entry, it) })
    val stations = entry.stations.indices.mapNotNull { stationIndex ->
        val stationFacts = sorted.filter { CombinePlan.stationIndexFor(it.shotIndex) == stationIndex }
        if (stationFacts.isEmpty()) return@mapNotNull null
        val station = entry.stations[stationIndex]
        val estimate = handicapMatchSg(stationFacts.map { perShotSg(entry, it) })
        val avgRest = stationFacts.map { it.restDistanceM }.average()
        val isDriver = station.kind == StationKind.DRIVER
        StationResult(
            stationIndex = stationIndex,
            label = station.label,
            estimate = estimate,
            avgLabel = if (isDriver) "AVG TOTAL" else "AVG TO PIN",
            avgValue = if (isDriver) DRIVER_HOLE_M - avgRest else avgRest,
            chips = stationFacts.map { shotChip(entry, it) },
        )
    }
    return CombineResults(overall, SkillBand.nearestTo(overall.est), stations)
}

/** Per-shot chip (the shot's own nearest benchmark band) shown in the results ledger. */
fun shotChip(entry: CombineCatalogEntry, fact: CombineShotFacts): String =
    CombineFormats.chip(handicapMatchSg(listOf(perShotSg(entry, fact))).est)

fun overallEstimate(entry: CombineCatalogEntry, facts: List<CombineShotFacts>): Double =
    handicapMatchSg(
        facts.sortedBy { it.shotIndex }.map { perShotSg(entry, it) }
    ).est

/**
 * Per-shot, per-band SG for one combine shot: [shotSgForBand] across every band.
 */
private fun perShotSg(entry: CombineCatalogEntry, fact: CombineShotFacts): Map<SkillBand, Double> =
    SkillBand.entries.associateWith { shotSgForBand(entry, fact, it) }

/**
 * SG for one combine shot against a single [band]. DRIVER-station shots are rated against the
 * band's typical drive ([drivingStrokesGained] on the rest lie/distance); every other station uses
 * the TEE-table [strokesGained] from its [CombinePlan.shotInput]. This is the routing shared by the
 * four-band solve and the benchmark-locked ledger column.
 */
fun shotSgForBand(entry: CombineCatalogEntry, fact: CombineShotFacts, band: SkillBand): Double {
    val station = entry.stations[CombinePlan.stationIndexFor(fact.shotIndex)]
    return if (station.kind == StationKind.DRIVER) {
        drivingStrokesGained(band, fact.restLie, fact.restDistanceM)
    } else {
        val input = CombinePlan.shotInput(entry, fact.shotIndex, fact.restLie, fact.restDistanceM)
        strokesGained(band, input.startLie, input.startM, input.endLie, input.endM)
    }
}
