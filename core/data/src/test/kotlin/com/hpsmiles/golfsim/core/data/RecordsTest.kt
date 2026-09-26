package com.hpsmiles.golfsim.core.data

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.record.SessionSummaryRow
import com.hpsmiles.golfsim.core.data.record.SessionTitles
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.data.record.makeShotEntity
import com.hpsmiles.golfsim.core.data.record.toBallData
import com.hpsmiles.golfsim.core.data.record.toRecord
import com.hpsmiles.golfsim.core.data.record.toSummary
import com.hpsmiles.golfsim.core.physics.ShotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RecordsTest {

    private val ball = BallData(
        clubHeadSpeed = 33.4, ballSpeed = 48.2, launchDirection = -1.7,
        launchAngle = 12.5, spinAxis = -4.0, totalSpin = 8100,
        unknown1 = 5, unknown2 = 10,
    )
    private val result = ShotResult(
        carryM = 141.2, rolloutM = 9.1, totalM = 150.3, sideM = -3.4,
        apexM = 27.6, flightTimeSec = 6.1,
    )

    private fun entity() = makeShotEntity(
        sessionId = 7, seq = 3, timestampMs = 1_000L,
        source = ShotSource.LIVE, clubName = null, ballData = ball, result = result,
    )

    @Test
    fun `shot entity round trips through record`() {
        val record = entity().toRecord()
        assertEquals(ball, record.ballData)
        assertEquals(141.2, record.carryM, 1e-9)
        assertEquals(ShotSource.LIVE, record.source)
        assertNull(record.clubName)
        assertEquals(3, record.seq)
        assertEquals(7L, record.sessionId)
    }

    @Test
    fun `makeShotEntity preserves raw unknowns and scalars`() {
        val e = makeShotEntity(
            sessionId = 7, seq = 3, timestampMs = 1_000L,
            source = ShotSource.LIVE, clubName = "7i", ballData = ball, result = result,
        )
        assertEquals(5, e.unknown1)
        assertEquals(10, e.unknown2)
        assertEquals(150.3, e.totalM, 1e-9)
        assertEquals(33.4, e.clubHeadSpeedMps, 1e-9)
        assertEquals(8100, e.spinRpm)
        assertEquals(-1.7, e.launchDirDeg, 1e-9)
    }

    @Test
    fun `entity exposes BallData view`() {
        assertEquals(ball, entity().toBallData())
    }

    @Test
    fun `summary row maps live-only and all-shots views`() {
        val row = SessionSummaryRow(
            id = 1, startedAtEpochMs = 2L, endedAtEpochMs = 3L, title = null, misreadCount = 2,
            shotCount = 4, liveCount = 3, demoCount = 1,
            liveAvgCarryM = 100.0, liveMaxCarryM = 120.0,
            allAvgCarryM = 95.0, allMaxCarryM = 120.0,
            liveClubsCsv = "7i, 8i", allClubsCsv = "7i, 8i, DEMO-CLUB",
        )
        val live = row.toSummary(liveOnly = true)
        assertEquals(3, live.shotCount)
        assertEquals(listOf("7i", "8i"), live.clubNames)
        assertTrue(live.hasDemoShot)
        assertFalse(live.isOpen)
        val all = row.toSummary(liveOnly = false)
        assertEquals(4, all.shotCount)
        assertEquals(95.0, all.avgCarryM!!, 1e-9)
        assertEquals(listOf("7i", "8i", "DEMO-CLUB"), all.clubNames)
    }

    @Test
    fun `summary row open session and nulls survive`() {
        val row = SessionSummaryRow(
            id = 1, startedAtEpochMs = 2L, endedAtEpochMs = null, title = "named", misreadCount = 0,
            shotCount = 0, liveCount = 0, demoCount = 0,
            liveAvgCarryM = null, liveMaxCarryM = null,
            allAvgCarryM = null, allMaxCarryM = null,
            liveClubsCsv = null, allClubsCsv = null,
        )
        val s = row.toSummary(liveOnly = true)
        assertTrue(s.isOpen)
        assertNull(s.avgCarryM)
        assertEquals(emptyList<String>(), s.clubNames)
        assertFalse(s.hasDemoShot)
    }

    @Test
    fun `auto title formats from start time`() {
        val title = SessionTitles.auto(1_795_000_000_000L, locale = Locale.US)
        // 2026-06-19 ~02:26 UTC. Shape only (day varies with zone): "Wed 17 Jun · 02:26"-like.
        assertTrue(title.matches(Regex("[A-Z][a-z]{2} \\d{1,2} [A-Z][a-z]{2} \u00b7 \\d{2}:\\d{2}")))
    }
}
