package com.hpsmiles.golfsim.settings

import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import org.junit.Assert.assertEquals
import org.junit.Test

class BagReorderMathTest {

    private fun record(id: Long, type: ClubType) =
        ClubRecord(id = id, name = "c$id", type = type)

    // Flat list spanning four groups; WOOD and IRON have multiple clubs.
    private val clubs = listOf(
        record(1, ClubType.DRIVER),
        record(2, ClubType.WOOD), record(3, ClubType.WOOD),
        record(4, ClubType.IRON), record(5, ClubType.IRON), record(6, ClubType.IRON),
        record(7, ClubType.PUTTER),
    )
    private val driverGroup get() = bagGroups(clubs).first { it.type == ClubType.DRIVER }
    private val ironGroup get() = bagGroups(clubs).first { it.type == ClubType.IRON }

    @Test
    fun `bagGroups preserves emitted order and computes spans`() {
        val groups = bagGroups(clubs)
        assertEquals(
            listOf(ClubType.DRIVER, ClubType.WOOD, ClubType.IRON, ClubType.PUTTER),
            groups.map { it.type },
        )
        assertEquals(listOf(0, 1, 3, 6), groups.map { it.startIndex })
        assertEquals(listOf(2L, 3L), groups[1].clubs.map { it.id })
        assertEquals(listOf(4L, 5L, 6L), groups[2].clubs.map { it.id })
    }

    @Test
    fun `drop clamps to the group span at both edges`() {
        val pitch = 44f
        assertEquals(5, dropTargetIndex(4, 10f * pitch, pitch, ironGroup))   // past the end
        assertEquals(3, dropTargetIndex(4, -10f * pitch, pitch, ironGroup))  // past the start
    }

    @Test
    fun `drop snaps at half a row`() {
        val pitch = 44f
        assertEquals(4, dropTargetIndex(4, 0.49f * pitch, pitch, ironGroup))
        assertEquals(5, dropTargetIndex(4, 0.5f * pitch, pitch, ironGroup))
        assertEquals(5, dropTargetIndex(3, 1.6f * pitch, pitch, ironGroup))
        assertEquals(3, dropTargetIndex(3, -0.6f * pitch, pitch, ironGroup))
    }

    @Test
    fun `single-club group has a single slot`() {
        val pitch = 44f
        assertEquals(0, dropTargetIndex(0, 3f * pitch, pitch, driverGroup))
        assertEquals(0, dropTargetIndex(0, -3f * pitch, pitch, driverGroup))
    }

    @Test
    fun `moveId moves one id and keeps the rest ordered`() {
        val ids = listOf(10L, 20L, 30L, 40L)
        assertEquals(listOf(10L, 30L, 40L, 20L), moveId(ids, 1, 3)) // down
        assertEquals(listOf(40L, 10L, 20L, 30L), moveId(ids, 3, 0)) // up
        assertEquals(ids, moveId(ids, 2, 2))                        // same slot
    }

    @Test
    fun `bagGroups splits a non-conforming order into consecutive runs`() {
        val interleaved = listOf(
            record(2, ClubType.WOOD),
            record(4, ClubType.IRON),
            record(3, ClubType.WOOD),
        )
        val groups = bagGroups(interleaved)
        assertEquals(listOf(ClubType.WOOD, ClubType.IRON, ClubType.WOOD), groups.map { it.type })
        assertEquals(listOf(0, 1, 2), groups.map { it.startIndex })
        assertEquals(listOf(listOf(2L), listOf(4L), listOf(3L)), groups.map { g -> g.clubs.map { it.id } })
    }

    @Test
    fun `drop returns the origin slot for a non-positive pitch`() {
        assertEquals(4, dropTargetIndex(4, 100f, 0f, ironGroup))
        assertEquals(4, dropTargetIndex(4, 100f, -44f, ironGroup))
    }

    @Test
    fun `moveId passes out-of-bounds moves through untouched`() {
        val ids = listOf(10L, 20L, 30L, 40L)
        assertEquals(ids, moveId(ids, -1, 2))
        assertEquals(ids, moveId(ids, 0, 4))
    }
}
