// app/src/test/kotlin/com/hpsmiles/golfsim/history/HistoryFormatsTest.kt
package com.hpsmiles.golfsim.history

import com.hpsmiles.golfsim.core.data.record.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFormatsTest {

    private fun summary(shotCount: Int, avgCarryM: Double?, clubNames: List<String>) = SessionSummary(
        id = 1L,
        startedAtEpochMs = 0L,
        endedAtEpochMs = null,
        title = null,
        misreadCount = 0,
        shotCount = shotCount,
        avgCarryM = avgCarryM,
        maxCarryM = null,
        clubNames = clubNames,
        hasDemoShot = false,
        isOpen = false,
    )

    @Test
    fun `side uses explicit plus and unicode minus`() {
        assertEquals("+4.2 m", HistoryFormats.side(4.18))
        assertEquals("−3.1 m", HistoryFormats.side(-3.14))
    }

    @Test
    fun `carry is whole metres`() {
        assertEquals("139 m", HistoryFormats.carry(138.6))
    }

    @Test
    fun `session meta joins count, average and clubs`() {
        assertEquals("1 shot", HistoryFormats.sessionMeta(summary(1, null, emptyList())))
        assertEquals(
            "6 shots · avg 139 m · 7i, SW",
            HistoryFormats.sessionMeta(summary(6, 138.9, listOf("7i", "SW"))),
        )
    }

    @Test
    fun `club null renders dash`() {
        assertEquals("—", HistoryFormats.clubOrDash(null))
        assertEquals("7i", HistoryFormats.clubOrDash("7i"))
    }

    @Test
    fun `dir formats signed degrees with unicode minus`() {
        assertEquals("+3.5°", HistoryFormats.dir(3.46))
        assertEquals("−2.0°", HistoryFormats.dir(-2.04))
        assertEquals("+0.0°", HistoryFormats.dir(0.0))
    }
}
