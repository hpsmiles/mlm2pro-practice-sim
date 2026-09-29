package com.hpsmiles.golfsim.core.physics

/**
 * Green oval centred on (centerX, centerY) - games simulate with the ball
 * rolling on the green when it lands there, fairway otherwise (spec section 6.2).
 * Generalizes [ZoneTable]'s down-range bands to a real landing oval.
 */
class GreenZoneSurfaceProvider(
    private val centerX: Double,
    private val centerY: Double,
    private val radiusM: Double,
    private val green: Surface = Surface.GREEN_NORMAL,
    private val fairway: Surface = Surface.FAIRWAY_NORMAL,
) : SurfaceProvider {
    override fun surfaceAt(x: Double, y: Double): Surface {
        val dx = x - centerX
        val dy = y - centerY
        return if (dx * dx + dy * dy <= radiusM * radiusM) green else fairway
    }
}
