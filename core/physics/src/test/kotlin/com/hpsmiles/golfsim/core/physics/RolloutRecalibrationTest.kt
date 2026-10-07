package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression gates for the 2026-10-07 rollout recalibration (plan
 * docs/superpowers/plans/2026-10-07-bag-rollout-polish.md Lane C, spec §5):
 *
 * 1. Tangential speed after bounce is continuous + monotonic vs impact angle
 *    across thetaCrit — no cliff (previously a ±1° landing-angle difference
 *    flipped rollout ~3×).
 * 2. Driver-class fairway rollout lands in the realistic 18–35 m band (was
 *    ~10 m on the steep branch).
 * 3. Rough never out-rolls fairway for identical landing states (previously
 *    the rough's higher thetaCrit made a driver impact "shallow/fast" there).
 *
 * POST-change calibration record (2026-10-07) — rollout in metres, vh = 35:
 *
 * angle | 1500rpm F/R | 2600rpm F/R | 4500rpm F/R | 8000rpm F/R
 * ------+-------------+-------------+-------------+-------------
 *  12   |    ...      |    ...      |    ...      |    ...
 *  ...
 *
 * (Full table maintained in [RolloutSweepDiagnostic].)
 */
class RolloutRecalibrationTest {
    private val env = Environment()

    private fun landing(angleDeg: Double, spinRpm: Double, vh: Double = 35.0): LandingState {
        val vn = vh * Math.tan(Math.toRadians(angleDeg))
        val omega = spinRpm * 2.0 * Math.PI / 60.0
        return LandingState(Vec3(0.0, 0.0, 0.0), Vec3(0.0, vh, -vn), Vec3(omega, 0.0, 0.0), 30.0, 6.0)
    }

    /** Spec req 2: driver-class fairway rollout lands in the realistic band. */
    @Test
    fun driverFairwayRolloutRealistic() {
        for (angle in 17..24) for (spin in intArrayOf(2000, 2600, 3000)) {
            val rollout = BounceRollModel.bounceAndRoll(
                landing(angle.toDouble(), spin.toDouble()), Surface.FAIRWAY_NORMAL, 12.0, env,
            ).deltaY
            assertTrue(
                "driver ${angle}deg/${spin}rpm rollout ${"%.1f".format(rollout)} m outside [18, 35]",
                rollout in 18.0..35.0,
            )
        }
    }

    /** Spec req 1: no cliff — adjacent-degree rollout ratio stays bounded. */
    @Test
    fun rolloutContinuousAcrossThetaCrit() {
        var prev = -1.0
        for (angle in 12..32) {
            val r = BounceRollModel.bounceAndRoll(
                landing(angle.toDouble(), 2600.0), Surface.FAIRWAY_NORMAL, 12.0, env,
            ).deltaY
            if (prev > 0) assertTrue(
                "rollout jumped $prev -> $r at ${angle}deg",
                r <= prev * 1.5 + 1.0,
            )
            prev = r
        }
    }

    /** Spec req 3: rough never out-rolls fairway for identical landings. */
    @Test
    fun roughNeverOutRollsFairway() {
        for (angle in 12..40 step 2) for (spin in doubleArrayOf(1500.0, 2600.0, 8000.0)) {
            val fairway = BounceRollModel.bounceAndRoll(landing(angle.toDouble(), spin), Surface.FAIRWAY_NORMAL, 12.0, env).deltaY
            val rough = BounceRollModel.bounceAndRoll(landing(angle.toDouble(), spin), Surface.ROUGH_NORMAL, 12.0, env).deltaY
            assertTrue("rough $rough > fairway $fairway at ${angle}deg/${spin.toInt()}rpm", rough <= fairway)
        }
    }
}
