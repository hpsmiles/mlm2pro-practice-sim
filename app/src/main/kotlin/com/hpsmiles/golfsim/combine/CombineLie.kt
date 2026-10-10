package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.physics.Surface
import com.hpsmiles.golfsim.core.physics.SurfaceProvider
import com.hpsmiles.golfsim.core.scoring.Lie
import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.sqrt

/**
 * Single source of truth for the lie a resting ball scores from:
 * green oval (approach stations only) > fairway corridor (the visible RangeScene region) > rough.
 * The same classifier decides the physics surface, so what the player sees is what is scored.
 */
object CombineLie {

    fun classify(entry: CombineCatalogEntry, shotIndex: Int, restX: Double, restY: Double): Lie {
        val station = entry.stations[CombinePlan.stationIndexFor(shotIndex)]
        if (station.kind == StationKind.APPROACH) {
            val pin = CombinePlan.pinFor(entry, shotIndex)!!
            val cx = pin.dx
            val cy = station.startDistanceM + pin.dy
            val dx = restX - cx
            val dy = restY - cy
            if (sqrt(dx * dx + dy * dy) <= station.greenRadiusM) return Lie.GREEN
        }
        return if (RangeScene.isOnFairway(restX, restY)) Lie.FAIRWAY else Lie.ROUGH
    }
}

/** Physics surfaces mirroring [CombineLie] for one specific shot's scene. */
class CombineSurfaceProvider(
    private val entry: CombineCatalogEntry,
    private val shotIndex: Int,
    private val green: Surface,
    private val fairway: Surface,
    private val rough: Surface,
) : SurfaceProvider {

    override fun surfaceAt(x: Double, y: Double): Surface =
        when (CombineLie.classify(entry, shotIndex, x, y)) {
            Lie.GREEN -> green
            Lie.FAIRWAY -> fairway
            Lie.ROUGH -> rough
            Lie.TEE -> fairway
        }
}
