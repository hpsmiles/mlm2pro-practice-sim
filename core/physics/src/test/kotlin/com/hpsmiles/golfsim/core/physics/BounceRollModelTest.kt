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
    fun bounceLoopStaysWithinCap() {
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, 25.0, -20.0),
            spin = Vec3(0.0, 0.0, 0.0),
            apexM = 20.0,
            flightTimeSec = 5.0,
        )
        val ground = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 12.0, env)
        assertTrue(ground.bounces in 1..8)
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

    /** Spec 2026-09-30: the bounce loop records one hop per FlightSolver solve. */
    @Test
    fun wedgeBackspinHopsBackwardOnGreen() {
        // Same steep, fast, high-spin impact as wedgeBackspinRollsBackwardOnGreen:
        // the Penner reversal makes the FIRST recorded touch land behind carry.
        val landing = LandingState(
            position = Vec3(0.0, 50.0, 0.0),
            velocity = Vec3(0.0, 14.0, -12.0),
            spin = Vec3(700.0, 0.0, 0.0),   // ~6685 rpm backspin
            apexM = 10.0,
            flightTimeSec = 3.0,
        )
        val ground = BounceRollModel.bounceAndRoll(landing, Surface.GREEN_NORMAL, 45.0, env)
        assertTrue("expected recorded hops, got ${ground.hops.size}", ground.hops.isNotEmpty())
        assertEquals("one hop per non-terminal bounce", ground.bounces - 1, ground.hops.size)
        for (hop in ground.hops) {
            assertTrue("apex must be positive, got ${hop.apexM}", hop.apexM > 0.0)
            assertTrue("duration must be positive, got ${hop.durationSec}", hop.durationSec > 0.0)
        }
        assertTrue(
            "expected backward first touch, got ${ground.hops.first().landingY}",
            ground.hops.first().landingY < 0.0,
        )
    }

    /**
     * 2026-09-30 live 8i capture (shot-1) on a green: non-spin-dominant
     * impact keeps the whole hop chain forward, and the final touch->rest
     * roll reconstructs the remaining ground delta (spec invariant).
     */
    @Test
    fun midIronLiveProfileHopsForwardOnGreen() {
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, 16.60, -15.62),
            spin = Vec3(469.0, 0.0, 0.0),   // ~4478 rpm backspin at impact
            apexM = 20.0,
            flightTimeSec = 5.0,
        )
        val ground = BounceRollModel.bounceAndRoll(landing, Surface.GREEN_NORMAL, 20.3, env)
        assertTrue("expected recorded hops, got ${ground.hops.size}", ground.hops.isNotEmpty())
        assertEquals("one hop per non-terminal bounce", ground.bounces - 1, ground.hops.size)
        var prevY = 0.0
        for (hop in ground.hops) {
            assertTrue(hop.apexM > 0.0)
            assertTrue(hop.durationSec > 0.0)
            assertTrue("touches must stay forward, got ${hop.landingY}", hop.landingY > 0.0)
            assertTrue("cumulative touches must advance, got ${hop.landingY}", hop.landingY > prevY)
            prevY = hop.landingY
        }
        // Spec invariant: last recorded touch + final roll == total ground delta,
        // and the remaining roll is forward (or zero).
        val last = ground.hops.last()
        assertTrue("roll must not reverse", ground.deltaY >= last.landingY)
    }

    /**
     * Rollout recalibration 2026-10-03 (Biber 2023 + TrackMan 2023): a low,
     * low-spin knuckle drive lands shallow-fast and must release, but a
     * realistic firm-fairway decel keeps run-out inside a sane band — not the
     * ~77% of carry the old mu=0.030 fairway law produced.
     */
    @Test
    fun lowDriveRunOutStaysRealistic() {
        val drive = LaunchConditions(
            ballSpeedMps = 175.0 * 0.44704, // 175 mph
            launchAngleDeg = 5.0,
            spinRpm = 1400,
        )
        val shot = BallFlightEngine.simulate(
            drive,
            surfaces = UniformSurface(Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM)),
        )
        val ratio = shot.rolloutM / shot.carryM
        assertTrue(
            "knuckle drive rollout ratio ${"%.3f".format(ratio)} outside [0.20, 0.55]",
            ratio in 0.20..0.55,
        )
    }

    /** A gap wedge must back up only slightly on a receptive green (Biber: sub-metre to ~1 m). */
    @Test
    fun gapWedgeBacksUpOnlySlightlyOnGreen() {
        val gw = LaunchConditions(ballSpeedMps = 90.0 * 0.44704, launchAngleDeg = 23.5, spinRpm = 10_000)
        val shot = BallFlightEngine.simulate(gw, surfaces = UniformSurface(Surface.GREEN_NORMAL))
        assertTrue(
            "GW rollout ${"%.2f".format(shot.rolloutM)} m outside [-1.8, -0.3]",
            shot.rolloutM in -1.8..-0.3,
        )
    }

    /** A tour mid-iron must stop within a metre on the green. */
    @Test
    fun midIronStopsOnGreen() {
        val sevenIron = LaunchConditions(ballSpeedMps = 123.0 * 0.44704, launchAngleDeg = 16.3, spinRpm = 7124)
        val shot = BallFlightEngine.simulate(sevenIron, surfaces = UniformSurface(Surface.GREEN_NORMAL))
        assertTrue(
            "7i green rollout ${"%.2f".format(shot.rolloutM)} m outside [0.0, 1.0]",
            shot.rolloutM in 0.0..1.0,
        )
    }

    /** Even a high-spin wedge's backward run must stay bounded (~1.5 m total). */
    @Test
    fun highSpinWedgeBackupBounded() {
        val wedge = LaunchConditions(ballSpeedMps = 66.0 * 0.44704, launchAngleDeg = 32.0, spinRpm = 11_000)
        val shot = BallFlightEngine.simulate(wedge, surfaces = UniformSurface(Surface.GREEN_NORMAL))
        assertTrue(
            "high-spin wedge backup ${"%.2f".format(shot.rolloutM)} m exceeds -1.8",
            shot.rolloutM >= -1.8,
        )
    }
}
