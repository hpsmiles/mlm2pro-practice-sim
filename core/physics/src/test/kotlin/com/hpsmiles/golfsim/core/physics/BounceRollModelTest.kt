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
}
