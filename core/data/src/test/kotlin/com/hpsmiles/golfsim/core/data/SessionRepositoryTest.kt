package com.hpsmiles.golfsim.core.data

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.physics.ShotResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SessionRepositoryTest {

    // Robolectric gives each test method a fresh temp filesystem, so the
    // real file-backed DB never leaks between tests.
    private fun newRepo() = SessionRepository.open(RuntimeEnvironment.getApplication())

    private val ball = BallData(33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10)
    private val result = ShotResult(140.0, 10.0, 150.0, -3.0, 27.0, 6.0)

    private suspend fun fire(repo: SessionRepository, i: Int = 0) =
        repo.appendShot(ball, result, ShotSource.LIVE, "7i", 1_000L + i)

    @Test
    fun `appendShot auto-creates exactly one open session`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        fire(repo, 0)
        fire(repo, 1)
        val summary = repo.summaries.first().single()
        assertEquals(2, summary.shotCount)
        assertTrue(summary.isOpen)
        assertTrue(repo.hasOpenSession.first())
        assertFalse(repo.persistError.value)
    }

    @Test
    fun `endSession closes - misreads after end are ignored`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        fire(repo)
        repo.incrementMisread()
        repo.endSession()
        val summary = repo.summaries.first().single()
        assertFalse(summary.isOpen)
        assertEquals(1, summary.misreadCount)
        assertFalse(repo.hasOpenSession.first())
        repo.incrementMisread() // no open session: no-op
        assertEquals(1, repo.summaries.first().single().misreadCount)
    }

    @Test
    fun `restore after restart continues the same session`() = runTest {
        val first = newRepo()
        first.initializeAndRestore()
        fire(first, 0)
        fire(first, 1)
        first.endSession()
        // "restart": a second repository over the same file continues fine
        val second = newRepo()
        val restored = second.initializeAndRestore()
        assertNull(restored) // no open session — ended last time
        fire(second, 2)
        assertEquals(2, second.summaries.first().size)
    }

    @Test
    fun `open session restores with shots and misreads`() = runTest {
        val first = newRepo()
        first.initializeAndRestore()
        fire(first, 0)
        fire(first, 1)
        first.incrementMisread()
        // simulate a crash-restart without END
        val second = newRepo()
        val restored = second.initializeAndRestore()
        assertNotNull(restored)
        assertEquals(2, restored!!.shots.size)
        assertEquals(1, restored.misreadCount)
        assertEquals("7i", restored.shots[0].clubName)
    }

    @Test
    fun `bag seeds once, addClub rejects duplicates and blanks`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        assertEquals(14, repo.clubs.first().size)
        assertEquals("D", repo.clubs.first().first().name)
        repo.initializeAndRestore() // second init must NOT re-seed
        assertEquals(14, repo.clubs.first().size)
        assertTrue(repo.addClub("7i-A"))
        assertFalse(repo.addClub("7i-A")) // duplicate
        assertFalse(repo.addClub("   "))  // blank
        assertFalse(repo.addClub("56, W")) // comma — summary CSV separator rule
        assertEquals(15, repo.clubs.first().size)
    }

    @Test
    fun `retagShots updates the observable shot rows`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        fire(repo, 0)
        fire(repo, 1)
        val sessionId = repo.summaries.first().single().id
        val records = repo.observeShots(sessionId).first()
        assertEquals(listOf("7i", "7i"), records.map { it.clubName })
        repo.retagShots(records.map { it.id }, "8i")
        assertEquals(listOf("8i", "8i"), repo.observeShots(sessionId).first().map { it.clubName })
    }

    @Test
    fun `setShotsExcluded flips rows and re-emits the shots flow`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        fire(repo, 0)
        fire(repo, 1)
        val sessionId = repo.summaries.first().single().id
        val ids = repo.observeShots(sessionId).first().map { it.id }
        repo.setShotsExcluded(listOf(ids[1]), true)
        val flags = repo.observeShots(sessionId).first().map { it.excluded }
        assertEquals(listOf(false, true), flags)
        repo.setShotsExcluded(listOf(ids[1]), false)
        assertEquals(listOf(false, false), repo.observeShots(sessionId).first().map { it.excluded })
    }

    @Test
    fun `excluded shots drop from summary averages and longest - counts intact`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        repo.appendShot(ball, ShotResult(140.0, 10.0, 150.0, -3.0, 27.0, 6.0), ShotSource.LIVE, "7i", 1L)
        repo.appendShot(ball, ShotResult(160.0, 10.0, 170.0, -3.0, 27.0, 6.0), ShotSource.LIVE, "7i", 2L)
        val sessionId = repo.summaries.first().single().id
        val ids = repo.observeShots(sessionId).first().map { it.id }
        repo.setShotsExcluded(listOf(ids[1]), true) // exclude the 160 m shot
        val s = repo.summaries.first().single()
        assertEquals(2, s.shotCount)                 // count unchanged (spec §5.4)
        assertEquals(140.0, s.avgCarryM!!, 0.0)
        assertEquals(140.0, s.maxCarryM!!, 0.0)

        // Exercise the ALL aggregate legs too (default liveOnly=true only
        // touches the live* legs; History's ALL view reads all*).
        repo.liveOnly.value = false
        val all = repo.summaries.first().single()
        assertEquals(140.0, all.avgCarryM!!, 0.0)  // unfiltered all-leg would read 150.0
        assertEquals(140.0, all.maxCarryM!!, 0.0)  // unfiltered all-leg would read 160.0

        // Every live shot excluded → NULL carry aggregates (contract Task 6 needs).
        repo.setShotsExcluded(listOf(ids[0]), true)
        val allExcluded = repo.summaries.first().single()
        assertNull(allExcluded.avgCarryM)
        assertNull(allExcluded.maxCarryM)
    }

    @Test
    fun `liveOnly filter re-shapes statistics`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        repo.appendShot(ball, result, ShotSource.LIVE, "7i", 1L)
        repo.appendShot(ball, result, ShotSource.DEMO, "SW", 2L)
        assertEquals(1, repo.summaries.first().single().shotCount) // default live-only
        repo.liveOnly.value = false
        assertEquals(2, repo.summaries.first().single().shotCount)
        assertEquals(listOf("7i", "SW"), repo.summaries.first().single().clubNames)
    }

    @Test
    fun `liveOnly hides demo-only sessions from the list`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        repo.appendShot(ball, result, ShotSource.DEMO, "SW", 1L)
        repo.appendShot(ball, result, ShotSource.DEMO, "SW", 2L)
        repo.endSession()
        // Default LIVE ONLY: a session with no live shots vanishes entirely.
        assertTrue(repo.summaries.first().isEmpty())
        repo.liveOnly.value = false
        assertEquals(2, repo.summaries.first().single().shotCount)
    }

    @Test
    fun `garbage database file is tolerated, not recovered`() = runTest {
        // Three empirical rounds proved Robolectric's SQLite will not throw
        // on ANY forged "corrupt" file: 64 garbage bytes open as an empty DB,
        // foreign tables are treated as a legacy DB and migrated alongside,
        // and even a room_master_table with a wrong identity hash is
        // tolerated. Real-file corruption detection therefore cannot be
        // simulated on the JVM — it is verified on-device in Task 10
        // Step 5b. What this test pins down instead is the guarantee the
        // repository makes for ANY unreadable-but-tolerated file: open()
        // never throws, and the repository stays functional.
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("golfsim.db")
        dbFile.parentFile!!.mkdirs()
        dbFile.writeBytes(ByteArray(64) { it.toByte() }) // not a SQLite file
        val repo = SessionRepository.open(context)
        assertNull(repo.initializeAndRestore())
        assertFalse(repo.persistError.value) // tolerated — recovery never ran
        fire(repo)
        assertEquals(1, repo.summaries.first().single().shotCount)
        assertEquals(14, repo.clubs.first().size)
    }

    @Test
    fun `recoverFromCorruptFile archives, reseeds and flags persistError`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("golfsim.db")
        dbFile.parentFile!!.mkdirs()
        dbFile.writeBytes(ByteArray(64) { it.toByte() }) // broken main file
        val parent = dbFile.parentFile!!
        parent.resolve("golfsim.db-journal").writeBytes(ByteArray(16))
        parent.resolve("golfsim.db-wal").writeBytes(ByteArray(16))
        parent.resolve("golfsim.db-shm").writeBytes(ByteArray(16))
        val repo = SessionRepository.open(context)
        repo.recoverFromCorruptFile() // internal — mechanism under test
        assertTrue(repo.persistError.value)
        // The bad file was archived aside. Of the stale sidecars' removal,
        // only `-journal` is observable after the method returns: the reseed
        // step opens the fresh DB in WAL mode, legitimately recreating new
        // `-wal`/`-shm` files, so asserting their absence would be wrong.
        // (If stale WAL content had leaked into the fresh DB, the reseed
        // and fire assertions below would fail.)
        val archives = parent.listFiles { f -> f.name.startsWith("corrupt-") }
        assertEquals(1, archives!!.size)
        assertFalse(parent.resolve("golfsim.db-journal").exists())
        // ...and the recovered repository is functional again (reseeded):
        assertEquals(14, repo.clubs.first().size)
        fire(repo)
        assertEquals(1, repo.summaries.first().single().shotCount)
        assertFalse(repo.persistError.value) // next successful write clears it
    }

    @Test
    fun `renameSession trims, truncates to 40 and blanks reset to auto`() = runTest {
        val repo = newRepo()
        repeat(2) { fire(repo, it) }
        repo.endSession()
        val id = repo.summaries.first().single().id

        repo.renameSession(id, "  My Session  ")
        assertEquals("My Session", repo.summaries.first().single().title)

        repo.renameSession(id, "x".repeat(50))
        assertEquals(40, repo.summaries.first().single().title!!.length) // MAX_TITLE

        repo.renameSession(id, "   ")
        assertEquals(null, repo.summaries.first().single().title) // blank → auto-title
    }

    @Test
    fun `retagShots trims and truncates club names`() = runTest {
        val repo = newRepo()
        repeat(2) { fire(repo, it) }
        val session = repo.summaries.first().single()
        val ids = repo.observeShots(session.id).first().map { it.id }

        repo.retagShots(ids, "  8i  ")
        assertEquals(listOf("8i", "8i"), repo.observeShots(session.id).first().map { it.clubName })

        repo.retagShots(ids, "x".repeat(30))
        val tagged = repo.observeShots(session.id).first()
        assertTrue(tagged.all { it.clubName!!.length == 20 }) // MAX_CLUB
    }

    @Test
    fun `bag orders type-first then sortOrder`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        val names = repo.clubs.first().map { it.name }
        assertEquals(
            listOf("D", "3W", "5W", "4H", "4i", "5i", "6i", "7i", "8i", "9i", "PW", "GW", "SW", "LW"),
            names,
        )
    }

    @Test
    fun `addClub keeps type and appends within its type group`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        assertTrue(repo.addClub("7i-A", ClubType.IRON))
        assertTrue(repo.addClub("9i-X", ClubType.IRON, isTest = true))
        val clubs = repo.clubs.first()
        // New clubs get max sortOrder → land at the end of their type group
        // (9i-X was added after 7i-A, so it is the final IRON).
        val irons = clubs.filter { it.type == ClubType.IRON }.map { it.name }
        assertEquals(listOf("4i", "5i", "6i", "7i", "8i", "9i", "7i-A", "9i-X"), irons)
        assertEquals(ClubType.IRON, clubs.first { it.name == "7i-A" }.type)
        assertTrue(clubs.first { it.name == "9i-X" }.isTemp)
    }

    @Test
    fun `TEST club purges on endSession - shots keep name and temp snapshot`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        assertTrue(repo.addClub("7i-A", ClubType.IRON, isTest = true))
        repo.appendShot(ball, result, ShotSource.LIVE, "7i-A", 1L, clubWasTemp = true)
        assertTrue(repo.addClub("7i-B")) // defaults: IRON, non-TEST
        repo.endSession()
        val bagNames = repo.clubs.first().map { it.name }
        assertTrue("7i-B" in bagNames)  // non-TEST user-added club survives purge
        assertEquals(15, bagNames.size) // 14 seeds + 7i-B; 7i-A purged
        assertFalse("7i-A" in bagNames)
        val shots = repo.observeShots(repo.summaries.first().single().id).first()
        assertEquals("7i-A", shots.single().clubName)  // label survives forever
        assertTrue(shots.single().clubWasTemp)        // snapshot survives too
    }

    @Test
    fun `endSession without an open session still purges TEST clubs`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        repo.endSession() // no shots fired: nothing open
        assertTrue(repo.addClub("X1", ClubType.WOOD, isTest = true))
        repo.endSession() // single rule: EVERY endSession purges isTemp
        assertFalse("X1" in repo.clubs.first().map { it.name })
    }

    @Test
    fun `typed seed names exactly cover the migration UPDATE name set`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        // Hand-maintained mirror of MIGRATION_1_2's UPDATE name→type mapping.
        // If either side drifts (name OR type), the seed bag and the v1→v2
        // migration stop agreeing — this guard fails loudly instead.
        // (Review carry-in.)
        val expected = mapOf(
            "D" to ClubType.DRIVER, "3W" to ClubType.WOOD, "5W" to ClubType.WOOD,
            "4H" to ClubType.HYBRID,
            "4i" to ClubType.IRON, "5i" to ClubType.IRON, "6i" to ClubType.IRON,
            "7i" to ClubType.IRON, "8i" to ClubType.IRON, "9i" to ClubType.IRON,
            "PW" to ClubType.WEDGE, "GW" to ClubType.WEDGE, "SW" to ClubType.WEDGE,
            "LW" to ClubType.WEDGE,
        )
        assertEquals(expected, repo.clubs.first().associate { it.name to it.type })
    }

    @Test
    fun `renameClub rejects blank, commas, duplicates and self-renames`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore() // seeds the default bag — the pin needs 7i to exist
        val sevenIron = repo.clubs.first().first { it.name == "7i" }

        // Rejection rules mirror addClub (blank / comma / duplicate) — Task 8a pin.
        assertFalse(repo.renameClub(sevenIron.id, "   "))
        assertFalse(repo.renameClub(sevenIron.id, "56, W"))
        assertFalse(repo.renameClub(sevenIron.id, "SW"))

        // Renaming to the club's own current name is also a "duplicate" —
        // conscious behavior (no self-exclusion): a no-op CONFIRM shows
        // ALREADY IN BAG rather than silently succeeding.
        assertFalse(repo.renameClub(sevenIron.id, " 7i "))

        // Valid rename: the clubs flow shows the new name, not the old one.
        assertTrue(repo.renameClub(sevenIron.id, "7 iron"))
        val names = repo.clubs.first().map { it.name }
        assertTrue("7 iron" in names)
        assertFalse("7i" in names)
    }
}
