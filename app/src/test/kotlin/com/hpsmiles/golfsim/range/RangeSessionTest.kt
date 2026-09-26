package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.record.RestoredSession
import com.hpsmiles.golfsim.core.data.record.ShotRecord
import com.hpsmiles.golfsim.core.data.record.ShotSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `add returns a DisplayShot and appends it`() {
        val session = RangeSession()
        val shot = session.add(fade())
        assertNotNull(shot)
        assertEquals(1, session.shots.size)
        assertEquals(44.2, shot!!.ballData.ballSpeed, 1e-9)
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
    fun `add returns null and appends nothing when LaunchConditions require fails`() {
        val session = RangeSession()
        assertNull(session.add(garbage()))
        assertEquals(0, session.shots.size)
        assertEquals(0, session.tick.intValue)
    }

    private fun shotRecord(seq: Int, club: String?) = ShotRecord(
        id = seq.toLong(), sessionId = 5L, seq = seq, timestampMs = seq.toLong(),
        source = ShotSource.LIVE, clubName = club, ballData = fade(),
        carryM = 140.9, totalM = 150.0, sideM = -3.0, apexM = 27.0, flightTimeSec = 6.1,
    )

    @Test
    fun `add stamps the shot from the injectable clock`() {
        val session = RangeSession()
        var now = 123_456L
        session.clockMs = { now }
        val shot = session.add(fade())
        assertEquals(123_456L, shot!!.timestampMs)
        now = 999L
        assertEquals(999L, session.add(fade())!!.timestampMs) // each shot stamped at its own add
    }

    @Test
    fun `restore skips rows that no longer pass launch guards`() {
        val session = RangeSession()
        val bad = shotRecord(2, "7i").copy(ballData = garbage())
        assertEquals(1, session.restore(RestoredSession(5L, 0, listOf(bad))))
        assertEquals(0, session.shots.size)
    }

    @Test
    fun `restore rebuilds resting shots and misreads without trajectories`() {
        val session = RangeSession()
        assertEquals(0, session.restore(RestoredSession(5L, 3, listOf(shotRecord(0, "7i"), shotRecord(1, null)))))
        assertEquals(2, session.shots.size)
        assertEquals(0L, session.shots[0].timestampMs)
        assertEquals(1L, session.shots[1].timestampMs) // seq order survives the rebuild
        assertEquals(3, session.misreadCount.intValue)
        assertEquals(0, session.tick.intValue)          // restore never bumps tick (playback gate)
        // Scalar-backed ShotResult carries no samples (nothing replays after restore)…
        assertEquals(0, session.shots[0].shotResult.samples.size)
        // …and rollout is derived on restore: total - carry.
        assertEquals(9.1, session.shots[0].shotResult.rolloutM, 1e-9)
        // A second restore clears before refilling and reports zero skips.
        assertEquals(0, session.restore(RestoredSession(6L, 0, listOf(shotRecord(0, "8i")))))
        assertEquals(1, session.shots.size)
    }

    @Test
    fun `misreads coalesce inside 500 ms window and report the coalesce owner`() {
        val session = RangeSession()
        var now = 1_000L
        session.clockMs = { now }
        assertTrue(session.markMisread(atMs = now))
        assertFalse(session.markMisread(atMs = now + 200)) // EVENTS + MEASUREMENT pair
        assertEquals(1, session.misreadCount.intValue)
        assertTrue(session.markMisread(atMs = now + 1_000)) // genuinely new misread
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
