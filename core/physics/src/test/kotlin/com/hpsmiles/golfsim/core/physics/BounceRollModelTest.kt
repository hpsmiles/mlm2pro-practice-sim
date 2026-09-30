package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BounceRollModelTest {

    private val env = Environment()

    @Test
    fun wedgeBackspinRollsBackwardOnGreen() {
        // Steep, fast, high-spin impact — the Penner 2R*omega/7 impulse
        // overpowers forward momentum (CalOf hand-check: newTan ~ -3.4 m/s).
        val landing = LandingState(
            position = Vec3(0.0, 50.0, 0.0),
            velocity = Vec3(0.0, 14.0, -12.0),
            spin = Vec3(700.0, 0.0, 0.0),   // ~6685 rpm backspin
            apexM = 10.0,
            flightTimeSec = 3.0,
        )
        val ground = BounceRollModel.bounceAndRoll(landing, Surface.GREEN_NORMAL, 45.0, env)
        assertTrue("expected negative rollout, got ${ground.deltaY}", ground.deltaY < -1.0)
    }

    @Test
    fun bounceLoopIsCappedAtFour() {
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, 25.0, -20.0),
            spin = Vec3(0.0, 0.0, 0.0),
            apexM = 20.0,
            flightTimeSec = 5.0,
        )
        val ground = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 12.0, env)
        assertTrue(ground.bounces in 1..4)
        assertTrue(ground.deltaY.isFinite())
    }

    @Test
    fun roughStopsShorterThanFairway() {
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, 30.0, -15.0),
            spin = Vec3(0.0, 0.0, 0.0),
            apexM = 25.0,
            flightTimeSec = 6.0,
        )
        val fairway = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 12.0, env)
        val rough = BounceRollModel.bounceAndRoll(landing, Surface.ROUGH_NORMAL, 12.0, env)
        assertTrue(rough.deltaY < fairway.deltaY)
    }

    @Test
    fun highSpinStopsMuchShorterOnGreenThanFairway() {
        // Approach-shot profile: same steep fast impact, high backspin.
        // Green spin-back scale 1.12 vs fairway 0.35 -> Penner impulse
        // reverses the ball on green (CalOf hand-check: newTan green ~ -2.1,
        // fairway ~ +0.5 m/s).
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, 15.0, -12.0),
            spin = Vec3(500.0, 0.0, 0.0),   // ~4775 rpm backspin
            apexM = 20.0,
            flightTimeSec = 5.0,
        )
        val fairway = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 40.0, env)
        val green = BounceRollModel.bounceAndRoll(landing, Surface.GREEN_NORMAL, 40.0, env)
        assertTrue(green.deltaY < fairway.deltaY)
    }

    @Test
    fun repeatRunsAreBitIdentical() {
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(2.0, 40.0, -18.0),
            spin = Vec3(400.0, 0.0, 0.0),
            apexM = 28.0,
            flightTimeSec = 6.2,
        )
        val a = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 14.8, env)
        val b = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 14.8, env)
        assertEquals(a, b)
    }

    /**
     * 2026-09-30 shed capture (8i, live): landings at ~43-44.5 deg, ~23 m/s,
     * ~4.3-4.6k rpm at impact. Spin-dominance R*omega/vh ~ 0.60 — turf friction
     * should bleed forward speed, not reverse the ball. User-observed ground
     * truth: an 8i stops with no or minimal forward rollout (never backwards).
     */
    @Test
    fun midIronLiveProfileDoesNotSpinBackOnGreen() {
        val landings = listOf(
            Vec3(0.0, 16.60, -15.62) to Vec3(469.0, 0.0, 0.0),   // shot-1
            Vec3(0.0, 16.51, -16.04) to Vec3(453.0, 0.0, 0.0),   // shot-2
            Vec3(0.0, 16.37, -16.06) to Vec3(438.0, 0.0, 0.0),   // shot-3
            Vec3(0.0, 16.48, -15.87) to Vec3(466.0, 0.0, 0.0),   // shot-4
            Vec3(0.0, 16.96, -15.30) to Vec3(467.0, 0.0, 0.0),   // shot-5
        )
        landings.forEachIndexed { i, (v, s) ->
            val ground = BounceRollModel.bounceAndRoll(
                LandingState(Vec3(0.0, 130.0, 0.0), v, s, 22.0, 5.3),
                Surface.GREEN_NORMAL, 20.5, env,
            )
            assertTrue(
                "shot-${i + 1} rollout ${"%.2f".format(ground.deltaY)} m outside [-0.5, +1.0]",
                ground.deltaY in -0.5..1.0,
            )
        }
    }

    /**
     * A ~4.8k-rpm / 39 deg approach must still stop shorter on green than on
     * fairway (ordering pin already covered by
     * [highSpinStopsMuchShorterOnGreenThanFairway]); the gate must not flip it.
     */
    @Test
    fun greenStillStopsShorterThanFairwayForModerateSpinApproach() {
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, 15.0, -12.0),
            spin = Vec3(500.0, 0.0, 0.0),
            apexM = 20.0,
            flightTimeSec = 5.0,
        )
        val fairway = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 40.0, env)
        val green = BounceRollModel.bounceAndRoll(landing, Surface.GREEN_NORMAL, 40.0, env)
        assertTrue(
            "green ${green.deltaY} must be < fairway ${fairway.deltaY}",
            green.deltaY < fairway.deltaY,
        )
    }
}
