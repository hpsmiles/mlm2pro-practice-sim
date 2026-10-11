package com.hpsmiles.golfsim.core.data

import com.hpsmiles.golfsim.core.data.record.ShotSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class CombineRepositoryTest {

    private fun repo() = SessionRepository.open(RuntimeEnvironment.getApplication())

    @Test
    fun startIsIdempotentForTheSameCombine() = runTest {
        val repository = repo()
        val first = repository.startCombineSession("APPROACH", "APPROACH", 1, 1_000L, ShotSource.LIVE)
        val second = repository.startCombineSession("APPROACH", "APPROACH", 1, 2_000L, ShotSource.LIVE)
        assertEquals(first, second)
        assertEquals(first, repository.combineActive.first()?.id)
    }

    @Test
    fun startReturnsNullWhenADifferentCombineIsInProgress() = runTest {
        val repository = repo()
        repository.startCombineSession("APPROACH", "APPROACH", 1, 1_000L, ShotSource.LIVE)
        val second = repository.startCombineSession("WEDGES", "WEDGES", 1, 2_000L, ShotSource.LIVE)
        assertNull(second)
    }

    @Test
    fun writeThroughCompletionAndHistory() = runTest {
        val repository = repo()
        val id = repository.startCombineSession("APPROACH", "APPROACH", 1, 1_000L, ShotSource.LIVE)!!
        for (index in 0 until 18) {
            repository.appendCombineShot(
                sessionId = id,
                shotIndex = index,
                stationIndex = index / 6,
                restLie = "GREEN",
                restDistanceM = 5.0 + index,
            )
        }
        assertEquals(18, repository.observeCombineShots(id).first().size)
        repository.completeCombineSession(id, 9_000L)
        assertNull(repository.combineActive.first())
        val history = repository.combineHistory.first()
        assertEquals(1, history.size)
        assertEquals(id, history.first().id)
        assertEquals(18, repository.combineHistoryShots.first().size)
    }

    @Test
    fun discardDeletesSessionAndShots() = runTest {
        val repository = repo()
        val id = repository.startCombineSession("WEDGES", "WEDGES", 1, 1_000L, ShotSource.LIVE)!!
        repository.appendCombineShot(id, 0, 0, "GREEN", 3.0)
        repository.discardCombineSession(id)
        assertNull(repository.combineActive.first())
        assertTrue(repository.observeCombineShots(id).first().isEmpty())
    }

    @Test
    fun completedSessionsResumeIsUnaffectedByDiscardedOnes() = runTest {
        val repository = repo()
        repository.startCombineSession("WEDGES", "WEDGES", 1, 1_000L, ShotSource.LIVE)
        val active = repository.combineActive.first()!!
        repository.appendCombineShot(active.id, 0, 0, "GREEN", 4.0)
        val resumed = repository.startCombineSession("WEDGES", "WEDGES", 1, 5_000L, ShotSource.LIVE)
        assertEquals(active.id, resumed)
        assertEquals(1, repository.observeCombineShots(active.id).first().size)
    }

    @Test
    fun completedReadsReturnSessionAndShots() = runTest {
        val repository = repo()
        val id = repository.startCombineSession("APPROACH", "APPROACH", 1, 1_000L, ShotSource.LIVE)!!
        for (index in 0 until 18) {
            repository.appendCombineShot(id, index, index / 6, "GREEN", 5.0 + index)
        }
        repository.completeCombineSession(id, 9_000L)

        val completed = repository.combineCompletedSessions("APPROACH")
        assertEquals(1, completed.size)
        assertEquals(id, completed.single().id)

        // shotsForSessions has no ORDER BY — assert the count, not order.
        assertEquals(18, repository.combineShotsForSessions(listOf(id)).size)
        assertTrue(repository.combineShotsForSessions(emptyList()).isEmpty())
    }

    @Test
    fun markCombineSessionDemoRestampsSourceOnActiveAndHistoryRows() = runTest {
        val repository = repo()
        val id = repository.startCombineSession("APPROACH", "APPROACH", 1, 1_000L, ShotSource.LIVE)!!
        assertEquals(ShotSource.LIVE.code, repository.combineActive.first()?.source)

        repository.appendCombineShot(id, 0, 0, "GREEN", 5.0)
        repository.markCombineSessionDemo(id)

        // The active row now reports DEMO, so the completion effect's LIVE gate
        // (active.source == ShotSource.LIVE.code) would not set a record.
        assertEquals(ShotSource.DEMO.code, repository.combineActive.first()?.source)

        repository.completeCombineSession(id, 9_000L)
        val history = repository.combineHistory.first()
        assertEquals(1, history.size)
        assertEquals(ShotSource.DEMO.code, history.first().source)
    }
}
