package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Always-passing diagnostic that prints the ground-rollout sweep table for a
 * driver-class landing (vh = 35 m/s) across impact angles and backspin, on
 * FAIRWAY / FIRM / ROUGH / GREEN. Use it to steer the [BounceRollModel]
 * recalibration (plan 2026-10-07): the printed table is the calibration record.
 */
class RolloutSweepDiagnostic {
    private val env = Environment()

    private fun landing(angleDeg: Double, spinRpm: Double): LandingState {
        val vh = 35.0
        val vn = vh * Math.tan(Math.toRadians(angleDeg))
        val omega = spinRpm * 2.0 * Math.PI / 60.0
        return LandingState(Vec3(0.0, 0.0, 0.0), Vec3(0.0, vh, -vn), Vec3(omega, 0.0, 0.0), 30.0, 6.0)
    }

    @Test
    fun printSweepTable() {
        val surfaces = listOf(
            "FAIRWAY" to Surface.FAIRWAY_NORMAL,
            "FIRM" to Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM),
            "ROUGH" to Surface.ROUGH_NORMAL,
            "GREEN" to Surface.GREEN_NORMAL,
        )
        println("angle | spin | " + surfaces.joinToString(" | ") { it.first })
        for (angle in 10..50 step 2) {
            for (spin in doubleArrayOf(1500.0, 2600.0, 4500.0, 8000.0, 10000.0)) {
                val cells = surfaces.joinToString(" | ") { (_, s) ->
                    "%.1f".format(BounceRollModel.bounceAndRoll(landing(angle.toDouble(), spin), s, 12.0, env).deltaY)
                }
                println("${angle}deg | ${spin.toInt()}rpm | $cells")
            }
        }
        assertTrue(true)
    }
}
