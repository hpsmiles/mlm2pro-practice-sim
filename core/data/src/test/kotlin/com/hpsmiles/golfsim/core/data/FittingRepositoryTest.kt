package com.hpsmiles.golfsim.core.data

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
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
 * M7 repository contract (spec §2): start/append/complete lifecycle,
 * at-most-one-in-progress, write-through club snapshot with NO auto-filter,
 * manual exclusion toggles, history after completion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FittingRepositoryTest {

    private fun repo() = SessionRepository.open(RuntimeEnvironment.getApplication())

    private fun ball(ballSpeed: Double) = BallData(
        clubHeadSpeed = 33.0, ballSpeed = ballSpeed, launchDirection = -1.0,
        launchAngle = 12.0, spinAxis = -4.0, totalSpin = 8000, unknown1 = 5, unknown2 = 10,
    )

    private fun result(carry: Double = 140.0) = ShotResult(
        carryM = carry, rolloutM = 10.0, totalM = carry + 10.0, sideM = -3.0, apexM = 27.0, flightTimeSec = 6.0,
    )

    private val clubA = ClubRecord(1, "Shaft A", ClubType.DRIVER, true)
    private val clubB = ClubRecord(2, "Shaft B", ClubType.DRIVER, true)

    @Test
    fun `start is idempotent while in progress`() = runTest {
        val repo = repo()
        val id1 = repo.startFittingSession(startedAtMs = 1000)!!
        val id2 = repo.startFittingSession(startedAtMs = 2000)!!
        assertEquals(id1, id2)
        assertNull(repo.fittingHistory.first().firstOrNull())
    }

    @Test
    fun `append write-through keeps the club snapshot - no auto-filter`() = runTest {
        val repo = repo()
        val id = repo.startFittingSession(startedAtMs = 1000)!!
        repo.appendFittingShot(id, clubA, ball(48.0), result(), timestampMs = 1001)
        repo.appendFittingShot(id, clubA, ball(20.0), result(90.0), timestampMs = 1002) // would be a bag duff
        repo.appendFittingShot(id, clubA, ball(46.0), result(), timestampMs = 1003)
        val shots = repo.observeFittingShots(id).first()
        assertEquals(3, shots.size)
        assertEquals("Shaft A", shots[0].clubName)
        assertEquals("DRIVER", shots[0].clubType)
        assertTrue(shots[0].clubWasTemp)
        assertEquals(1L, shots[0].clubId)
        // Manual exclusion only — nothing is auto-filtered in fitting.
        assertTrue(shots.none { it.excluded })
    }

    @Test
    fun `setFittingShotsExcluded toggles rows`() = runTest {
        val repo = repo()
        val id = repo.startFittingSession(startedAtMs = 1000)!!
        repo.appendFittingShot(id, clubA, ball(48.0), result(), timestampMs = 1001)
        repo.appendFittingShot(id, clubB, ball(48.0), result(), timestampMs = 1002)
        val shots = repo.observeFittingShots(id).first()
        repo.setFittingShotsExcluded(listOf(shots[0].id), true)
        val after = repo.observeFittingShots(id).first()
        assertTrue(after[0].excluded)
        assertFalse(after[1].excluded)
        repo.setFittingShotsExcluded(listOf(shots[0].id), false)
        assertFalse(repo.observeFittingShots(id).first()[0].excluded)
    }

    @Test
    fun `setFittingShotsExcluded edge cases - empty ids, stale ids, idempotence`() = runTest {
        val repo = repo()
        val id = repo.startFittingSession(startedAtMs = 1000)!!
        repo.appendFittingShot(id, clubA, ball(48.0), result(), timestampMs = 1001)
        repo.appendFittingShot(id, clubB, ball(48.0), result(), timestampMs = 1002)
        val shots = repo.observeFittingShots(id).first()

        // (a) Empty ids: early return — no crash, nothing changes.
        repo.setFittingShotsExcluded(emptyList(), true)
        assertEquals(listOf(false, false), repo.observeFittingShots(id).first().map { it.excluded })

        // (b) Stale/nonexistent ids: no error, 0 rows change — others untouched.
        repo.setFittingShotsExcluded(listOf(99999L, 12345L), true)
        assertEquals(listOf(false, false), repo.observeFittingShots(id).first().map { it.excluded })

        // (c) Idempotence: calling twice with the same ids leaves the same state.
        repo.setFittingShotsExcluded(listOf(shots[0].id), true)
        repo.setFittingShotsExcluded(listOf(shots[0].id), true)
        assertEquals(listOf(true, false), repo.observeFittingShots(id).first().map { it.excluded })
    }

    @Test
    fun `restart cycle - complete then start yields a new in-progress session`() = runTest {
        val repo = repo()
        val id1 = repo.startFittingSession(startedAtMs = 1000)!!
        repo.appendFittingShot(id1, clubA, ball(48.0), result(), timestampMs = 1001)
        repo.completeFittingSession(id1, completedAtMs = 2000)
        assertNull(repo.fittingActive.first())

        val id2 = repo.startFittingSession(startedAtMs = 3000)!!
        assertTrue(id2 != id1)
        val active = repo.fittingActive.first()!!
        assertEquals(id2, active.id)
        // The new session starts empty; the completed one stays in history.
        assertEquals(0, repo.observeFittingShots(id2).first().size)
        assertEquals(1, repo.fittingHistory.first().size)
    }

    @Test
    fun `complete moves the session to history`() = runTest {
        val repo = repo()
        val id = repo.startFittingSession(startedAtMs = 1000)!!
        repo.appendFittingShot(id, clubA, ball(48.0), result(), timestampMs = 1001)
        repo.completeFittingSession(id, completedAtMs = 9000)
        assertNull(repo.fittingActive.first())
        val done = repo.fittingHistory.first()
        assertEquals(1, done.size)
        assertEquals(id, done.single().id)
        assertEquals(9000L, done.single().completedAtMs)
        // Persisted shots survive completion (read-only history view feeds on this).
        assertEquals(1, repo.observeFittingShots(id).first().size)
    }
}
