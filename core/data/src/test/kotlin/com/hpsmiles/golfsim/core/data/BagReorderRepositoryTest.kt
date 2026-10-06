package com.hpsmiles.golfsim.core.data

import com.hpsmiles.golfsim.core.data.entity.ClubEntity
import com.hpsmiles.golfsim.core.data.record.ClubType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Bag reorder contract (docs/superpowers/specs/2026-10-06-bag-club-reorder-design.md):
 * move within a type group, renumber only that group to 0..k-1, clamp, normalize
 * duplicate sortOrder values, tolerate garbage type strings and unknown ids,
 * never throw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class BagReorderRepositoryTest {

    private fun newRepo() = SessionRepository.open(RuntimeEnvironment.getApplication())

    private suspend fun namesOfType(repo: SessionRepository, type: ClubType): List<String> =
        repo.clubs.first().filter { it.type == type }.map { it.name }

    private suspend fun sortOrders(repo: SessionRepository): Map<Long, Int> =
        repo.clubDao().all().associate { it.id to it.sortOrder }

    @Test
    fun `3i moves to the top of IRONS and the order survives a fresh read`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        assertTrue(repo.addClub("3i", ClubType.IRON))
        // New clubs append at the bottom of their type — the user report.
        assertEquals(listOf("4i", "5i", "6i", "7i", "8i", "9i", "3i"), namesOfType(repo, ClubType.IRON))

        val threeIron = repo.clubs.first().first { it.name == "3i" }
        repo.moveClub(threeIron.id, 0)

        assertEquals(listOf("3i", "4i", "5i", "6i", "7i", "8i", "9i"), namesOfType(repo, ClubType.IRON))
        assertEquals(listOf("3W", "5W"), namesOfType(repo, ClubType.WOOD))
        assertEquals(listOf("PW", "GW", "SW", "LW"), namesOfType(repo, ClubType.WEDGE))

        val second = newRepo() // fresh repository over the same file
        second.initializeAndRestore()
        assertEquals(listOf("3i", "4i", "5i", "6i", "7i", "8i", "9i"), namesOfType(second, ClubType.IRON))
    }

    @Test
    fun `target index clamps to the group at both ends`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        val fourIron = repo.clubs.first().first { it.name == "4i" }

        repo.moveClub(fourIron.id, 99)
        assertEquals(listOf("5i", "6i", "7i", "8i", "9i", "4i"), namesOfType(repo, ClubType.IRON))

        repo.moveClub(fourIron.id, -99)
        assertEquals(listOf("4i", "5i", "6i", "7i", "8i", "9i"), namesOfType(repo, ClubType.IRON))
    }

    @Test
    fun `only the moved group's sortOrder values change and they renumber 0 until k-1`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        val before = sortOrders(repo)

        val nineIron = repo.clubs.first().first { it.name == "9i" }
        repo.moveClub(nineIron.id, 0)

        val after = sortOrders(repo)
        val irons = repo.clubs.first().filter { it.type == ClubType.IRON }
        assertEquals(listOf("9i", "4i", "5i", "6i", "7i", "8i"), irons.map { it.name })
        assertEquals((0..5).toList(), irons.map { after.getValue(it.id) })
        val others = repo.clubs.first().filter { it.type != ClubType.IRON }
        assertTrue(others.all { before.getValue(it.id) == after.getValue(it.id) })
    }

    @Test
    fun `garbage type string moves within the IRON group`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        repo.clubDao().insert(ClubEntity(name = "zgarbage", sortOrder = 100, type = "zgarbage"))

        val row = repo.clubs.first().first { it.name == "zgarbage" }
        assertEquals(ClubType.IRON, row.type)
        assertEquals("zgarbage", namesOfType(repo, ClubType.IRON).last())

        repo.moveClub(row.id, 0)
        assertEquals("zgarbage", namesOfType(repo, ClubType.IRON).first())
        assertEquals(listOf("3W", "5W"), namesOfType(repo, ClubType.WOOD))
    }

    @Test
    fun `same-slot move writes nothing`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        val before = sortOrders(repo)
        val sixIron = repo.clubs.first().first { it.name == "6i" }
        repo.moveClub(sixIron.id, 2) // 6i is already the third IRON (index 2)
        assertEquals(before, sortOrders(repo))
    }

    @Test
    fun `unknown id is a silent no-op`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        val before = sortOrders(repo)
        repo.moveClub(99_999L, 0)
        assertEquals(before, sortOrders(repo))
        assertFalse(repo.persistError.value)
    }

    @Test
    fun `duplicate sortOrder values normalize to unique slots on the next move`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        val fourIron = repo.clubs.first().first { it.name == "4i" }
        val fiveIron = repo.clubs.first().first { it.name == "5i" }
        // Forge a legacy/tampered state: 4i and 5i share sortOrder 5.
        repo.clubDao().updateSortOrder(fourIron.id, 5)
        repo.clubDao().updateSortOrder(fiveIron.id, 5)

        repo.moveClub(fourIron.id, 2)

        val after = repo.clubDao().all()
            .filter { ClubType.fromName(it.type) == ClubType.IRON }
            .sortedBy { it.sortOrder }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), after.map { it.sortOrder })
        assertEquals(listOf("5i", "6i", "4i", "7i", "8i", "9i"), after.map { it.name })
    }
}
