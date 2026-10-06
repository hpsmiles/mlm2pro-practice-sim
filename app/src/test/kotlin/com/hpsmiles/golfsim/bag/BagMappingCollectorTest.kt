package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.physics.ShotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guided-collection state holder (spec §4): plan, advance, gate latch, no-read coalesce. */
class BagMappingCollectorTest {

    // Known-good live values (M4c golden fixture discipline).
    private fun ball(ballSpeed: Double = 48.0) = BallData(
        clubHeadSpeed = 33.0, ballSpeed = ballSpeed, launchDirection = -1.0,
        launchAngle = 12.0, spinAxis = -4.0, totalSpin = 8000, unknown1 = 5, unknown2 = 10,
    )

    private fun collector(): BagMappingCollector {
        val c = BagMappingCollector()
        c.simulator = { ShotResult(carryM = 140.0, rolloutM = 10.0, totalM = 150.0, sideM = 0.0, apexM = 27.0, flightTimeSec = 6.0) }
        c.begin(7L, listOf(BagPlanClub("D", ClubType.DRIVER), BagPlanClub("7i", ClubType.IRON), BagPlanClub("PW", ClubType.WEDGE)))
        return c
    }

    @Test
    fun `begin at first club - add returns payload for write-through persistence`() {
        val c = collector()
        assertEquals("D", c.currentClub?.name)
        val shot = c.add(ball())!!
        assertEquals("D", shot.clubName)
        assertEquals(ClubType.DRIVER, shot.clubType)
        assertEquals(140.0, shot.carryM, 1e-9)
        assertEquals(150.0, shot.totalM, 1e-9)
        assertEquals(48.0, shot.ballData.ballSpeed, 1e-9)
        assertTrue(shot.timestampMs > 0)
    }

    @Test
    fun `advance walks the plan - done after the last club`() {
        val c = collector()
        c.advance()
        assertEquals("7i", c.currentClub?.name)
        c.advance()
        assertEquals("PW", c.currentClub?.name)
        assertFalse(c.done)
        c.advance()
        assertTrue(c.done)
        assertNull(c.currentClub)
        assertNull(c.add(ball())) // nothing to collect when done
    }

    @Test
    fun `guard violation - bad decode is never stored, just ignored`() {
        val c = collector()
        assertNull(c.add(ball(ballSpeed = -5.0))) // LaunchConditions throws → null
    }

    @Test
    fun `no read coalesces the events-plus-measurement pair`() {
        val c = collector()
        assertTrue(c.markNoRead(1000))
        assertFalse(c.markNoRead(1200)) // within 500 ms window
        assertTrue(c.markNoRead(1600))
        assertTrue(c.markNoRead(2100)) // exactly 500 ms later is counted (500 < 500 is false)
        assertEquals(3, c.noReadCount.intValue)
    }

    @Test
    fun `hit more latches per club - accept or advance clears it`() {
        val c = collector()
        assertEquals("D", c.currentClub?.name)
        c.hitMore()
        assertEquals("D", c.moreGrantedFor.value)
        c.advance()
        assertNull(c.moreGrantedFor.value)
        c.hitMore()
        c.acceptResult()
        assertTrue(c.currentIndex.intValue >= 1) // accept = advance
        assertNull(c.moreGrantedFor.value)
    }

    @Test
    fun `resume coerces the club index into the plan`() {
        val c = BagMappingCollector()
        val plan = listOf(BagPlanClub("D", ClubType.DRIVER), BagPlanClub("7i", ClubType.IRON))
        c.beginAt(9L, plan, clubIndex = 99)
        assertEquals("7i", c.currentClub?.name) // clamped to last club
        c.reset()
        assertEquals(-1L, c.sessionId)
        assertEquals(0, c.noReadCount.intValue)
        assertNull(c.currentClub)
    }
}
