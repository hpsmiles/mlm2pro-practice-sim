package com.hpsmiles.golfsim.core.data

import androidx.room.Room
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.ClubEntity
import com.hpsmiles.golfsim.core.data.entity.SessionEntity
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.data.record.makeShotEntity
import com.hpsmiles.golfsim.core.physics.ShotResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class Mlm2proDatabaseTest {

    private lateinit var db: Mlm2proDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            Mlm2proDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private val ball = BallData(33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10)
    private val result = ShotResult(140.0, 10.0, 150.0, -3.0, 27.0, 6.0)

    @Test
    fun `summaries aggregate live and demo slices`() = runTest {
        val id = db.sessionDao().insert(SessionEntity(startedAtEpochMs = 1_000L))
        repeat(2) { i ->
            db.shotDao().insert(makeShotEntity(id, i, 1_000L + i, ShotSource.LIVE, "7i", ball, result))
        }
        db.shotDao().insert(
            makeShotEntity(id, 2, 1_500L, ShotSource.DEMO, null, ball.copy(ballSpeed = 50.0),
                result.copy(carryM = 160.0, totalM = 170.0, sideM = 1.0))
        )
        val row = db.sessionDao().observeSummaries().first().single()
        assertEquals(3, row.shotCount)
        assertEquals(2, row.liveCount)
        assertEquals(1, row.demoCount)
        assertEquals(140.0, row.liveAvgCarryM!!, 1e-9)
        assertEquals(160.0, row.allMaxCarryM!!, 1e-9)
        assertEquals("7i", row.liveClubsCsv)
    }

    @Test
    fun `findOpen returns open sessions only`() = runTest {
        assertNull(db.sessionDao().findOpen())
        val id = db.sessionDao().insert(SessionEntity(startedAtEpochMs = 1L))
        assertEquals(id, db.sessionDao().findOpen()!!.id)
        db.sessionDao().end(id, 9_999L)
        assertNull(db.sessionDao().findOpen())
    }

    @Test
    fun `retag updates rows and re-emits the flow`() = runTest {
        val id = db.sessionDao().insert(SessionEntity(startedAtEpochMs = 1_000L))
        val a = db.shotDao().insert(makeShotEntity(id, 0, 1L, ShotSource.LIVE, null, ball, result))
        val b = db.shotDao().insert(makeShotEntity(id, 1, 2L, ShotSource.LIVE, null, ball, result))
        assertEquals(listOf(null, null), db.shotDao().observeShots(id).first().map { it.clubName })
        db.shotDao().retagShotIds(listOf(a, b), "8i")
        assertEquals(listOf("8i", "8i"), db.shotDao().observeShots(id).first().map { it.clubName })
        db.shotDao().retagShotIds(listOf(a), null)
        assertEquals("8i", db.shotDao().observeShots(id).first()[1].clubName)
    }

    @Test
    fun `shotsForSession returns rows ordered by seq`() = runTest {
        val id = db.sessionDao().insert(SessionEntity(startedAtEpochMs = 1_000L))
        db.shotDao().insert(makeShotEntity(id, 0, 5L, ShotSource.LIVE, "7i", ball, result))
        db.shotDao().insert(makeShotEntity(id, 1, 6L, ShotSource.LIVE, "7i", ball, result))
        assertEquals(listOf(0, 1), db.shotDao().shotsForSession(id).map { it.seq })
        assertEquals(2, db.shotDao().countForSession(id))
    }

    @Test
    fun `incrementMisread bumps the named session`() = runTest {
        val id = db.sessionDao().insert(SessionEntity(startedAtEpochMs = 1_000L))
        db.sessionDao().incrementMisread(id)
        db.sessionDao().incrementMisread(id)
        assertEquals(2, db.sessionDao().observeSummaries().first().single().misreadCount)
    }

    @Test
    fun `unique club name rejects duplicates`() = runTest {
        db.clubDao().insert(ClubEntity(name = "7i", sortOrder = 0))
        val duplicate = runCatching {
            db.clubDao().insert(ClubEntity(name = "7i", sortOrder = 1))
        }
        assertTrue(duplicate.isFailure)
        assertEquals(1, db.clubDao().count())
        assertEquals("7i", db.clubDao().findByName("7i")!!.name)
    }
}
