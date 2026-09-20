package com.hpsmiles.golfsim.core.physics

/** Supplies the turf under any landing spot. M4's real target layouts implement this. */
fun interface SurfaceProvider {
    fun surfaceAt(x: Double, y: Double): Surface
}

/** One surface everywhere (tour fixtures run on this). */
class UniformSurface(private val surface: Surface) : SurfaceProvider {
    override fun surfaceAt(x: Double, y: Double): Surface = surface
}

/**
 * Simple down-range zoning (spec §4): green inside greenEndY, fairway mid,
 * rough beyond fairwayEndY. M4 replaces the rules with real target ovals.
 */
class ZoneTable(
    private val green: Surface = Surface.GREEN_NORMAL,
    private val fairway: Surface = Surface.FAIRWAY_NORMAL,
    private val rough: Surface = Surface.ROUGH_NORMAL,
    private val greenEndY: Double = 200.0,
    private val fairwayEndY: Double = 250.0,
) : SurfaceProvider {
    override fun surfaceAt(x: Double, y: Double): Surface = when {
        y <= greenEndY -> green
        y <= fairwayEndY -> fairway
        else -> rough
    }
}
