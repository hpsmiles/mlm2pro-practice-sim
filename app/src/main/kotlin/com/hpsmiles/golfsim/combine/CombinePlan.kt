package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie
import com.hpsmiles.golfsim.core.scoring.ShotInput
import kotlin.math.sqrt

/** Pure round-robin / geometry math for a combine session. */
object CombinePlan {
    const val SHOTS_PER_VISIT = 2
    const val STATIONS_PER_CYCLE = 3
    const val CYCLES = 3
    const val SHOTS_TOTAL = STATIONS_PER_CYCLE * CYCLES * SHOTS_PER_VISIT

    /** 0,0,1,1,2,2 repeating. */
    fun stationIndexFor(shotIndex: Int): Int =
        (shotIndex % (STATIONS_PER_CYCLE * SHOTS_PER_VISIT)) / SHOTS_PER_VISIT

    fun visitFor(shotIndex: Int): Int = shotIndex / (STATIONS_PER_CYCLE * SHOTS_PER_VISIT)

    fun isBannerShot(takenCount: Int): Boolean =
        takenCount > 0 && takenCount < SHOTS_TOTAL && takenCount % SHOTS_PER_VISIT == 0

    fun bannerLabel(entry: CombineCatalogEntry, takenCount: Int): String {
        val station = entry.stations[stationIndexFor(takenCount)]
        return "NEXT: ${station.label} · VISIT ${visitFor(takenCount) + 1}"
    }

    fun countsFor(entry: CombineCatalogEntry, takenCount: Int): List<Int> =
        entry.stations.indices.map { station ->
            (0 until takenCount).count { stationIndexFor(it) == station }
        }

    fun pinFor(entry: CombineCatalogEntry, shotIndex: Int): PinOffset? {
        val station = entry.stations[stationIndexFor(shotIndex)]
        if (station.kind == StationKind.DRIVER) return null
        return station.pins[visitFor(shotIndex) % station.pins.size]
    }

    /** Approach: distance to the visit pin. Driver: distance to the invisible pin at (0, [DRIVER_HOLE_M]). */
    fun restDistanceM(entry: CombineCatalogEntry, shotIndex: Int, restX: Double, restY: Double): Double {
        val station = entry.stations[stationIndexFor(shotIndex)]
        return if (station.kind == StationKind.DRIVER) {
            sqrt(restX * restX + (DRIVER_HOLE_M - restY) * (DRIVER_HOLE_M - restY))
        } else {
            val pin = pinFor(entry, shotIndex)!!
            val cy = station.startDistanceM + pin.dy
            sqrt((restX - pin.dx) * (restX - pin.dx) + (restY - cy) * (restY - cy))
        }
    }

    fun shotInput(entry: CombineCatalogEntry, shotIndex: Int, restLie: Lie, restDistanceM: Double): ShotInput {
        val station = entry.stations[stationIndexFor(shotIndex)]
        val startLie = if (station.kind == StationKind.DRIVER) Lie.TEE else Lie.FAIRWAY
        return ShotInput(startLie, station.startDistanceM, restLie, restDistanceM)
    }
}
