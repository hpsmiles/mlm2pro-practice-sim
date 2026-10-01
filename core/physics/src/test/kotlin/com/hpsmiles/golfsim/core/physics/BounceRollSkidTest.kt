package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 2 (2026-10-01): punch shots landing shallow on firm fairway must
 * release and run out; high-spin wedges keep checking up.
 */
class BounceRollSkidTest {

    /** Landing state with a given descent angle (rad) and backspin (rpm), straight at the target. */
    private fun landing(impactAngleRad: Double, spinRpm: Double, vh: Double = 25.0): LandingState {
        val vn = vh * Math.tan(impactAngleRad)
        return LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, vh, -vn),
            spin = Vec3(spinRpm * 2.0 * Math.PI / 60.0, 0.0, 0.0),
            apexM = 10.0,
            flightTimeSec = 4.0,
        )
    }

    private fun rollDistance(g: GroundResult): Double = Math.hypot(g.deltaX, g.deltaY)

    private val firm = Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM)

    @Test
    fun `shallow punch-class impact releases on firm fairway`() {
        // 6000 rpm / 7 deg / vh 25 m/s measured 42.3 m before the skid fix.
        val punch = BounceRollModel.bounceAndRoll(landing(Math.toRadians(7.0), 6000.0), firm, 10.0, Environment())
        assertTrue("punch run-out must exceed 55 m, got ${rollDistance(punch)}", rollDistance(punch) > 55.0)
    }

    @Test
    fun `shallow low-spin punch out-rolls steep impact`() {
        val shallow = BounceRollModel.bounceAndRoll(landing(Math.toRadians(7.0), 2500.0), firm, 10.0, Environment())
        val steep = BounceRollModel.bounceAndRoll(landing(Math.toRadians(25.0), 2500.0), firm, 10.0, Environment())
        assertTrue(
            "shallow (${rollDistance(shallow)}) must out-roll steep (${rollDistance(steep)})",
            rollDistance(shallow) > rollDistance(steep),
        )
    }

    @Test
    fun `high-spin wedge keeps checking up on shallow impacts`() {
        // On NORMAL fairway (firm's retention floor swamps spin differences).
        val wedge = BounceRollModel.bounceAndRoll(landing(Math.toRadians(7.0), 9000.0), Surface.FAIRWAY_NORMAL, 10.0, Environment())
        val punch = BounceRollModel.bounceAndRoll(landing(Math.toRadians(7.0), 6000.0), Surface.FAIRWAY_NORMAL, 10.0, Environment())
        assertTrue(
            "wedge (${rollDistance(wedge)}) must out-check punch (${rollDistance(punch)})",
            rollDistance(wedge) < rollDistance(punch),
        )
        assertTrue("wedge run-out must stay short, got ${rollDistance(wedge)}", rollDistance(wedge) < 30.0)
    }

    @Test
    fun `firm fairway out-rolls normal fairway for the same punch`() {
        val punch = landing(Math.toRadians(7.0), 6000.0)
        val normal = BounceRollModel.bounceAndRoll(punch, Surface.FAIRWAY_NORMAL, 10.0, Environment())
        val firmResult = BounceRollModel.bounceAndRoll(punch, firm, 10.0, Environment())
        assertTrue(
            "firm (${rollDistance(firmResult)}) must out-roll normal (${rollDistance(normal)})",
            rollDistance(firmResult) > rollDistance(normal),
        )
    }
}
