package com.hpsmiles.golfsim.core.data

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.BagMappingStatus
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.physics.ShotResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * M6 repository contract (spec §7): start/append/complete lifecycle,
 * at-most-one-in-progress, active = latest completed, history ordering,
 * recompute-on-write consistency, range-session isolation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class BagMappingRepositoryTest {

    private fun repo() = SessionRepository.open(RuntimeEnvironment.getApplication())

    private fun ball(ballSpeed: Double) = BallData(
        clubHeadSpeed = 33.0, ballSpeed = ballSpeed, launchDirection = -1.0,
        launchAngle = 12.0, spinAxis = -4.0, totalSpin = 8000, unknown1 = 5, unknown2 = 10,
    )

    private val bag = listOf(ClubRecord(1, "D", ClubType.DRIVER, false), ClubRecord(2, "7i", ClubType.IRON, false))

    @Test
    fun `start snapshots the bag - idempotent while in progress`() = runTest {
        val repo = repo()
        val id1 = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        val id2 = repo.startBagMappingSession(bag, startedAtMs = 2000)!!
        assertEquals(id1, id2) // at most one IN_PROGRESS (spec §4)
        val active = repo.bagMappingActive.first()!!
        assertEquals("D:DRIVER,7i:IRON", active.clubList)
        assertEquals(listOf("D" to ClubType.DRIVER, "7i" to ClubType.IRON), active.clubSnapshot())
    }

    @Test
    fun `complete lifecycle - active clears - latest completed - history newest first`() = runTest {
        val repo = repo()
        val id1 = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        repo.completeBagMappingSession(id1, completedAtMs = 2000)
        val id2 = repo.startBagMappingSession(bag, startedAtMs = 3000)!!
        assertTrue(id2 != id1)
        repo.completeBagMappingSession(id2, completedAtMs = 4000)
        assertNull(repo.bagMappingActive.first())
        assertEquals(id2, repo.bagMappingLatestCompleted.first()?.id) // latest wins
        assertEquals(listOf(id2, id1), repo.bagMappingHistory.first().map { it.id })
    }

    @Test
    fun `append recomputes duff filter over the whole club set`() = runTest {
        val repo = repo()
        val id = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        // 20 m/s ball speed is a duff once the club has 3 shots (median 46).
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ball(20.0), 140.0, 150.0, 1001)
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ball(48.0), 141.0, 151.0, 1002)
        // Still dormant at 2 shots — the early shot is kept so far.
        assertFalse(repo.observeBagMappingShots(id).first().single { it.timestampMs == 1001L }.filtered)
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ball(46.0), 139.0, 149.0, 1003)
        val shots = repo.observeBagMappingShots(id).first()
        assertTrue(shots.single { it.timestampMs == 1001L }.filtered)
        assertEquals(BagMappingStats.REASON_DUFF_LOW_BALL_SPEED, shots.single { it.timestampMs == 1001L }.filterReason)
        assertFalse(shots.single { it.timestampMs == 1002L }.filtered)
        assertNull(shots.single { it.timestampMs == 1002L }.filterReason)
    }

    @Test
    fun `append lowering the median re-admits an earlier filtered duff`() = runTest {
        val repo = repo()
        val id = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        // 20 m/s is a duff once the club has 3 shots (median 46, cutoff 39.1).
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ball(20.0), 140.0, 150.0, 1001)
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ball(48.0), 141.0, 151.0, 1002)
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ball(46.0), 139.0, 149.0, 1003)
        assertTrue(repo.observeBagMappingShots(id).first().single { it.timestampMs == 1001L }.filtered)
        // Two low strikes drag the median to 22 → cutoff 18.7 → 20 m/s is re-admitted.
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ball(22.0), 120.0, 130.0, 1004)
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ball(21.0), 118.0, 128.0, 1005)
        val shots = repo.observeBagMappingShots(id).first()
        assertFalse(shots.single { it.timestampMs == 1001L }.filtered)
        assertNull(shots.single { it.timestampMs == 1001L }.filterReason)
    }

    @Test
    fun `mapping shots never enter range sessions and vice versa`() = runTest {
        val repo = repo()
        val bagId = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        repo.appendBagMappingShot(bagId, "7i", ClubType.IRON, ball(48.0), 140.0, 150.0, 1001)
        // Range session list stays empty: bag writes never create range rows.
        assertTrue(repo.summaries.first().isEmpty())
        assertFalse(repo.hasOpenSession.first())
        // Range writes never create bag rows: the bag session stays at 1 shot.
        repo.appendShot(
            ballData = ball(48.0),
            result = ShotResult(carryM = 140.0, rolloutM = 10.0, totalM = 150.0, sideM = -3.0, apexM = 27.0, flightTimeSec = 6.0),
            source = ShotSource.LIVE,
            clubName = "7i",
            timestampMs = 2000,
        )
        assertEquals(1, repo.observeBagMappingShots(bagId).first().size)
        assertEquals(1, repo.summaries.first().single().shotCount)
    }
}
