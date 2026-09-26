package com.hpsmiles.golfsim.history

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.data.record.ShotRecord
import com.hpsmiles.golfsim.core.data.record.ShotSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryGroupingTest {

    private fun shot(
        id: Long,
        club: String?,
        seq: Int = 0,
        carry: Double = 140.0,
        side: Double = -3.0,
        excluded: Boolean = false,
        wasTemp: Boolean = false,
    ) = ShotRecord(
        id = id, sessionId = 1, seq = seq, timestampMs = id, source = ShotSource.LIVE,
        clubName = club, ballData = BallData(33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10),
        carryM = carry, totalM = 150.0, sideM = side, apexM = 27.0, flightTimeSec = 6.0,
        excluded = excluded, clubWasTemp = wasTemp,
    )

    private val bag = listOf(
        ClubRecord(1, "D", ClubType.DRIVER),
        ClubRecord(2, "7i", ClubType.IRON),
        ClubRecord(3, "PW", ClubType.WEDGE),
    )

    @Test
    fun `groups order bag-first then unknown alphabetical then untagged last`() {
        val shots = listOf(
            shot(1, "PW"), shot(2, "GONE-9"), shot(3, null), shot(4, "D"),
            shot(5, "7i"), shot(6, "AAA-CLUB"),
        )
        val names = groupByClub(shots, bag).map { it.clubName }
        assertEquals(listOf("D", "7i", "PW", "AAA-CLUB", "GONE-9", null), names)
    }

    @Test
    fun `shots within a group keep seq order`() {
        val shots = listOf(shot(2, "7i", seq = 1), shot(1, "7i", seq = 0))
        assertEquals(listOf(0, 1), groupByClub(shots, bag).single { it.clubName == "7i" }.shots.map { it.seq })
    }

    @Test
    fun `avg and sigma exclude excluded shots - side is a signed mean`() {
        val group = ClubGroup("7i", listOf(shot(1, "7i", carry = 100.0, side = 10.0), shot(2, "7i", carry = 140.0, side = 2.0, excluded = true), shot(3, "7i", carry = 120.0, side = -4.0)))
        val s = statsFor(group)
        assertEquals(2, s.count)
        assertEquals(110.0, s.avgCarryM!!, 0.0)   // (100+120)/2; excluded 140 omitted
        assertEquals(3.0, s.avgSideM!!, 0.0)      // (10 + −4)/2 signed mean
        assertEquals(10.0, s.sigmaCarryM, 1e-9)   // population σ: 100/120 → 10
    }

    @Test
    fun `TEST badge derives from any shots snapshot - all excluded empty stats`() {
        val group = ClubGroup("7i-A", listOf(shot(1, "7i-A", wasTemp = true), shot(2, "7i-A")))
        assertTrue(group.wasTemp)

        val empty = ClubGroup("7i-A", listOf(shot(1, "7i-A", excluded = true)))
        val s = statsFor(empty)
        assertEquals(0, s.count)
        assertNull(s.avgCarryM)
        assertNull(s.avgSideM)
        assertEquals(0.0, s.sigmaCarryM, 0.0)
    }

    @Test
    fun `smash and club speed average only over shots with club speed`() {
        // Ball speed 48 m/s; club speed 33 m/s → smash = 48/33.
        val group = ClubGroup("7i", listOf(shot(1, "7i"), shot(2, "7i")))
        assertEquals(48.0 / 33.0, statsFor(group).avgSmash!!, 1e-9)
        val zeroClub = shot(1, "7i").let {
            it.copy(ballData = it.ballData.copy(clubHeadSpeed = 0.0))
        }
        assertNull(statsFor(ClubGroup("7i", listOf(zeroClub))).avgSmash)
    }
}
