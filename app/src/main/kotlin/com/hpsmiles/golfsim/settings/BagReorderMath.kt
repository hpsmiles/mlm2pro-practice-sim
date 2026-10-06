// app/src/main/kotlin/com/hpsmiles/golfsim/settings/BagReorderMath.kt
package com.hpsmiles.golfsim.settings

import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import kotlin.math.roundToInt

/** One displayed type group: its clubs plus the flat-list index span. */
data class BagGroup(
    val type: ClubType,
    val startIndex: Int,
    val clubs: List<ClubRecord>,
)

/**
 * Consecutive grouping that preserves the repository's emitted order. The
 * repository sorts type-first, so runs are the full groups; keeping the
 * grouping consecutive means a non-conforming order can never be reshuffled.
 */
fun bagGroups(clubs: List<ClubRecord>): List<BagGroup> {
    val groups = mutableListOf<BagGroup>()
    var i = 0
    while (i < clubs.size) {
        var j = i + 1
        while (j < clubs.size && clubs[j].type == clubs[i].type) j++
        groups += BagGroup(clubs[i].type, i, clubs.subList(i, j).toList())
        i = j
    }
    return groups
}

/**
 * Drop slot for a drag: the origin slot plus whole row steps (snaps once the
 * drag passes half a row — `roundToInt` on the delta / pitch), clamped to the
 * dragged club's group span.
 */
fun dropTargetIndex(fromIndex: Int, dragDeltaY: Float, rowHeightPx: Float, group: BagGroup): Int {
    if (rowHeightPx <= 0f) return fromIndex
    val steps = (dragDeltaY / rowHeightPx).roundToInt()
    val lastIndex = group.startIndex + group.clubs.size - 1
    return (fromIndex + steps).coerceIn(group.startIndex, lastIndex)
}

/** Applies a from→to move to an id list (live drag preview). */
fun moveId(ids: List<Long>, fromIndex: Int, toIndex: Int): List<Long> {
    if (fromIndex == toIndex) return ids
    if (fromIndex !in ids.indices || toIndex !in ids.indices) return ids
    val moved = ids.toMutableList()
    val id = moved.removeAt(fromIndex)
    moved.add(toIndex, id)
    return moved
}
