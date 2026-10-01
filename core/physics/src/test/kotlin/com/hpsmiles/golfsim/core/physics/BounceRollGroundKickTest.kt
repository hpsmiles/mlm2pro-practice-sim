package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 1 (2026-10-01): the ball always ran out straight after landing.
 * Yaw spin (spin.z) must deflect the bounce/roll toward the direction the
 * ball was curving. Per BallFlightEngine, +spinAxisDeg (right-curving flight)
 * gives spin.z < 0, so a RIGHT-curving ball has NEGATIVE spin.z.
 */
class BounceRollGroundKickTest {

    /** Straight-down-range landing with pure backspin plus a yaw component (rad/s). */
    private fun landing(yawRadPerSec: Double): LandingState = LandingState(
        position = Vec3(0.0, 0.0, 0.0),
        velocity = Vec3(0.0, 28.0, -6.0),
        spin = Vec3(550.0, 0.0, yawRadPerSec),
        apexM = 12.0,
        flightTimeSec = 4.5,
    )

    @Test
    fun `side spin deflects run out toward the curve direction`() {
        // 55 rad/s yaw = ~5 deg spin axis on 6000 rpm total spin (realistic).
        val right = BounceRollModel.bounceAndRoll(landing(-55.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        val left = BounceRollModel.bounceAndRoll(landing(55.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        assertTrue("right-curving ball must kick right, deltaX=${right.deltaX}", right.deltaX > 0.0)
        assertTrue("left-curving ball must kick left, deltaX=${left.deltaX}", left.deltaX < 0.0)
        assertEquals("mirror symmetry", -left.deltaX, right.deltaX, 1e-6)
    }

    @Test
    fun `zero side spin keeps the run out straight`() {
        val straight = BounceRollModel.bounceAndRoll(landing(0.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        assertEquals(0.0, straight.deltaX, 1e-6)
    }

    @Test
    fun `kick is capped so extreme sidespin cannot sling the ball sideways`() {
        val huge = BounceRollModel.bounceAndRoll(landing(-3000.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        val modest = BounceRollModel.bounceAndRoll(landing(-55.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        assertTrue("capped deflection ${huge.deltaX} must stay bounded", huge.deltaX < 10.0)
        assertTrue("capped kick must still exceed modest kick", huge.deltaX > modest.deltaX)
    }

    @Test
    fun `green surfaces bite the first hop harder than fairway`() {
        // Compare the FIRST hop only: total deflection is confounded by
        // rollout-length differences between surfaces. The kick is scaled by
        // spinbackScale (green 1.12 vs fairway 0.35).
        val green = BounceRollModel.bounceAndRoll(landing(-55.0), Surface.GREEN_NORMAL, 12.0, Environment())
        val fairway = BounceRollModel.bounceAndRoll(landing(-55.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        assertTrue(
            "green first-hop kick ${green.hops[0].landingX} must exceed fairway ${fairway.hops[0].landingX}",
            green.hops[0].landingX > fairway.hops[0].landingX,
        )
    }
}
