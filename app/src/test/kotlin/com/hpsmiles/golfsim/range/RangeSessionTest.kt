package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.ble.BallData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeSessionTest {

    // Real on-device 8i shot (M4c golden fixture, event-008: fade).
    private fun fade() = BallData(
        clubHeadSpeed = 35.5, ballSpeed = 44.2, launchDirection = -6.9,
        launchAngle = 21.7, spinAxis = 11.3, totalSpin = 7463,
        unknown1 = 118, unknown2 = 125,
    )

    // Same shot with ball speed zeroed — violates LaunchConditions require.
    private fun garbage() = fade().copy(ballSpeed = 0.0)

    @Test
    fun `add returns true and appends a DisplayShot with simulated result`() {
        val session = RangeSession()
        assertTrue(session.add(fade()))
        assertEquals(1, session.shots.size)
        val shot = session.shots[0]
        assertEquals(44.2, shot.ballData.ballSpeed, 1e-9)
        assertEquals(21.7, shot.launch.launchAngleDeg, 1e-9)
        assertTrue(shot.shotResult.carryM > 0.0)
    }

    @Test
    fun `add advances tick by one per shot`() {
        val session = RangeSession()
        val before = session.tick.intValue
        session.add(fade())
        assertEquals(before + 1, session.tick.intValue)
    }

    @Test
    fun `add returns false and appends nothing when LaunchConditions require fails`() {
        val session = RangeSession()
        assertFalse(session.add(garbage()))
        assertEquals(0, session.shots.size)
        assertEquals(0, session.tick.intValue)
    }

    @Test
    fun `misreads coalesce inside 500 ms window`() {
        val session = RangeSession()
        var now = 1_000L
        session.clockMs = { now }
        session.markMisread(atMs = now)
        session.markMisread(atMs = now + 200)   // EVENTS + MEASUREMENT pair
        assertEquals(1, session.misreadCount.intValue)
        session.markMisread(atMs = now + 1_000) // genuinely new misread
        assertEquals(2, session.misreadCount.intValue)
    }

    @Test
    fun `dismissMisreads clears the pill counter`() {
        val session = RangeSession()
        session.markMisread(atMs = 1_000L)
        session.dismissMisreads()
        assertEquals(0, session.misreadCount.intValue)
    }
}
