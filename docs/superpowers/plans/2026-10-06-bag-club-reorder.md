# Bag Club Reordering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user reorder clubs within their type group from Settings ▸ BAG via a focused drag-handle panel, persisted in `clubs.sortOrder` (a newly added 3i currently lands under 9i with no way to fix it).

**Architecture:** A pure, JVM-tested `settings/BagReorderMath.kt` (grouping / drop-slot / move math); `SessionRepository.moveClub(id, toIndexInType)` owns the one-transaction renumber-within-group; a new `settings/BagReorderPanel.kt` (plain Compose state holder — no ViewModel) renders group headers, drawn drag handles, live preview and edge auto-scroll; `SettingsScreen` gains an EDIT ORDER mode and AppRoot wires the callback. No schema change — `sortOrder` already exists and is only interpreted within a type group.

**Tech Stack:** Kotlin 2.4.20, Jetpack Compose BOM 2026.09.00, Room 2.8.5, Robolectric JUnit4. Modules: `:core:data`, `:app`.

**Spec:** `docs/superpowers/specs/2026-10-06-bag-club-reorder-design.md`

**Conventions (Windows):** set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before every Gradle command. Gradle flags (e.g. `--console=plain`) precede task names. Use the typed `testDebugUnitTest` variants — the aggregate `test` task rejects `--tests`. Gradle is quiet during configure; runs take 5 s–2 min. There is an untracked `hard-glass-break.mp3` in the repo root — never `git add -A`; add explicit paths.

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/ClubDao.kt` | Modify | `findById`, `all`, `updateSortOrder` queries. |
| `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt` | Modify | `moveClub(id, toIndexInType)` + `internal fun clubDao()` test seam. |
| `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/BagReorderRepositoryTest.kt` | Create | Persistence contract: move, clamp, group isolation, garbage types, no-ops. |
| `app/src/main/kotlin/com/hpsmiles/golfsim/settings/BagReorderMath.kt` | Create | `BagGroup`, `bagGroups`, `dropTargetIndex`, `moveId`. |
| `app/src/test/kotlin/com/hpsmiles/golfsim/settings/BagReorderMathTest.kt` | Create | Pure math pins. |
| `app/src/main/kotlin/com/hpsmiles/golfsim/settings/BagReorderPanel.kt` | Create | Reorder panel UI + drag state holder, edge auto-scroll. |
| `app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt` | Modify | EDIT ORDER chip, `reorderMode`, panel mount, `onMoveClub` param. |
| `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` | Modify | Wire `onMoveClub` to the repository. |

---

### Task 1: Repository move within a type group (`:core:data`)

**Files:**
- Create: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/BagReorderRepositoryTest.kt`
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/ClubDao.kt`
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.BagReorderRepositoryTest"
```
Expected: FAIL — test compilation errors (`unresolved reference: moveClub`, `unresolved reference: clubDao`).

- [ ] **Step 3: Add the DAO queries**

In `ClubDao.kt`, after `findByName` (before `maxSortOrder`), add:

```kotlin
    @Query("SELECT * FROM clubs WHERE id = :id")
    suspend fun findById(id: Long): ClubEntity?

    @Query("SELECT * FROM clubs")
    suspend fun all(): List<ClubEntity>

    @Query("UPDATE clubs SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)
```

- [ ] **Step 4: Add `moveClub` and the test seam to `SessionRepository`**

Immediately after the `clubs` getter (after its closing `}` at the old line 80), add the seam:

```kotlin
    /** Test seam: raw club rows, for reorder order/uniqueness assertions. */
    internal fun clubDao() = clubDao
```

Immediately after `deleteClub` (after its closing `}` at the old line 289), add:

```kotlin
    /**
     * Moves a club to a 0-based slot within its own type group. The slot is a
     * final position and is clamped to 0..k-1. Renumbers only that group's
     * sortOrder to 0..k-1. Same-slot drop is a no-op. Never throws — same
     * contract as [deleteClub]; a failed write leaves the old order in the
     * [clubs] flow.
     */
    suspend fun moveClub(id: Long, toIndexInType: Int) {
        try {
            db.withTransaction {
                val club = clubDao.findById(id) ?: return@withTransaction
                val type = ClubType.fromName(club.type)
                val group = clubDao.all()
                    .filter { ClubType.fromName(it.type) == type }
                    .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                val from = group.indexOfFirst { it.id == id }
                if (from < 0) return@withTransaction
                val reordered = group.toMutableList()
                reordered.removeAt(from)
                reordered.add(toIndexInType.coerceIn(0, reordered.size), club)
                if (reordered.map { it.id } == group.map { it.id }) return@withTransaction
                reordered.forEachIndexed { index, entity ->
                    if (entity.sortOrder != index) clubDao.updateSortOrder(entity.id, index)
                }
            }
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "moveClub failed", t)
            persistError.value = true
        }
    }
```

Notes: `ClubType`, `Log`, `CancellationException` and `db.withTransaction` are already imported in this file. `sortOrder` is deliberately not unique across groups — the bag sort's primary key is the type ordinal, so cross-group value collisions are inert.

- [ ] **Step 5: Run test to verify it passes**

Run:
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.BagReorderRepositoryTest"
```
Expected: BUILD SUCCESSFUL — 7 tests pass.

- [ ] **Step 6: Commit**

```
git add core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/ClubDao.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/BagReorderRepositoryTest.kt
git commit -m "feat(data): move clubs within their type group"
```

---

### Task 2: Pure reorder math (`:app`)

**Files:**
- Create: `app/src/test/kotlin/com/hpsmiles/golfsim/settings/BagReorderMathTest.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/settings/BagReorderMath.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.settings.BagReorderMathTest"
```
Expected: FAIL — test compilation errors (`unresolved reference: bagGroups`, `BagGroup`, `dropTargetIndex`, `moveId`).

- [ ] **Step 3: Write the math file**

```kotlin
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
        groups += BagGroup(clubs[i].type, i, clubs.subList(i, j))
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
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.settings.BagReorderMathTest"
```
Expected: BUILD SUCCESSFUL — 5 tests pass.

- [ ] **Step 5: Commit**

```
git add app/src/main/kotlin/com/hpsmiles/golfsim/settings/BagReorderMath.kt app/src/test/kotlin/com/hpsmiles/golfsim/settings/BagReorderMathTest.kt
git commit -m "feat(settings): add pure bag reorder math"
```

---

### Task 3: The reorder panel (`:app`)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/settings/BagReorderPanel.kt`

- [ ] **Step 1: Write the panel**

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/settings/BagReorderPanel.kt
package com.hpsmiles.golfsim.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard

private val RowHeight = 44.dp
private val HandleSize = 40.dp
private val AutoScrollEdge = 48.dp
private val AutoScrollStep = 6.dp

/**
 * Focused reorder mode (spec §5.2): all clubs grouped by type, drag handles on
 * the left; a drop moves the club within its own type group only. Plain
 * Compose state holder, no ViewModel — the math lives in BagReorderMath.kt.
 */
@Composable
fun BagReorderPanel(
    clubs: List<ClubRecord>,
    onMoveClub: (Long, Int) -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    val rowPitchPx = with(density) { RowHeight.toPx() }
    val edgePx = with(density) { AutoScrollEdge.toPx() }
    val stepPx = with(density) { AutoScrollStep.toPx() }
    val scrollState = rememberScrollState()
    val dragState = remember { ReorderDragState() }

    val groups = remember(clubs) { bagGroups(clubs) }
    val ids = remember(clubs) { clubs.map { it.id } }
    val previewIds = remember(clubs, dragState.clubId, dragState.appliedSlot) {
        if (dragState.clubId != null && dragState.appliedSlot >= 0) {
            moveId(ids, dragState.originSlot, dragState.appliedSlot)
        } else {
            ids
        }
    }

    SectionCard("REORDER CLUBS", modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Drag a club's handle to move it within its type group — the Driver → Putter group order is fixed.",
            style = GolfTypography.Body,
            color = GolfColors.TextSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = GolfSpacing.Sm)) {
            Text(
                text = "DONE",
                style = GolfTypography.MetricLabel,
                color = GolfColors.Teal,
                modifier = Modifier
                    .border(1.dp, GolfColors.Teal, RoundedCornerShape(50))
                    .clickable(onClick = onDone)
                    .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .onGloballyPositioned { coords ->
                    dragState.viewportTopPx = coords.positionInRoot().y
                    dragState.viewportHeightPx = coords.size.height.toFloat()
                },
        ) {
            groups.forEach { group ->
                Text(
                    text = groupHeader(group.type),
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.TextMuted,
                    modifier = Modifier.padding(top = GolfSpacing.Sm, bottom = GolfSpacing.Xs),
                )
                group.clubs.forEachIndexed { index, club ->
                    ReorderRow(
                        club = club,
                        layoutIndex = group.startIndex + index,
                        workingIndex = previewIds.indexOf(club.id),
                        group = group,
                        groups = groups,
                        dragState = dragState,
                        rowPitchPx = rowPitchPx,
                        edgePx = edgePx,
                        stepPx = stepPx,
                        scrollState = scrollState,
                        onMoveClub = onMoveClub,
                    )
                }
            }
        }
    }
}

/** Section header: labels pluralize; DRIVER and PUTTER stay singular. */
private fun groupHeader(type: ClubType): String = when (type) {
    ClubType.DRIVER -> "DRIVER"
    ClubType.PUTTER -> "PUTTER"
    else -> type.label.uppercase() + "S"
}

/**
 * Panel-local drag state: the dragged club, the raw delta since drag start and
 * the previewed flat slot. Slots are indexes into the rendered clubs list; the
 * drag is always clamped to the dragged club's group span.
 */
private class ReorderDragState {
    var clubId by mutableStateOf<Long?>(null)
        private set
    var offsetPx by mutableStateOf(0f)
        private set
    var appliedSlot by mutableStateOf(-1)
        private set
    var originSlot = -1

    /** Row content tops (root Y relative to the scroll content), captured idle. */
    val rowTops = HashMap<Long, Float>()
    var viewportTopPx = 0f
    var viewportHeightPx = 0f

    fun start(id: Long, layoutIndex: Int) {
        clubId = id
        originSlot = layoutIndex
        appliedSlot = layoutIndex
        offsetPx = 0f
    }

    fun drag(
        deltaY: Float,
        groups: List<BagGroup>,
        type: ClubType,
        rowPitchPx: Float,
        scroll: ScrollState,
        edgePx: Float,
        stepPx: Float,
    ) {
        val id = clubId ?: return
        val group = groups.firstOrNull { it.type == type } ?: return
        offsetPx += deltaY
        appliedSlot = dropTargetIndex(originSlot, offsetPx, rowPitchPx, group)

        // Edge auto-scroll: the dragged row is the finger's proxy (spec §5.2).
        val visualTop = (rowTops[id] ?: 0f) + visualOffsetPx(groups, rowPitchPx) - scroll.value
        when {
            visualTop < edgePx -> scroll.dispatchRawDelta(-stepPx)
            visualTop + rowPitchPx > viewportHeightPx - edgePx -> scroll.dispatchRawDelta(stepPx)
        }
    }

    /** Continuous, group-clamped translation of the dragged row from its slot. */
    fun visualOffsetPx(groups: List<BagGroup>, rowPitchPx: Float): Float {
        val id = clubId ?: return 0f
        val group = groups.firstOrNull { g -> g.clubs.any { it.id == id } } ?: return 0f
        val firstPx = group.startIndex * rowPitchPx
        val lastPx = (group.startIndex + group.clubs.size - 1) * rowPitchPx
        val continuousPx = originSlot * rowPitchPx + offsetPx
        return continuousPx.coerceIn(firstPx, lastPx) - originSlot * rowPitchPx
    }

    fun finish(commit: Boolean, groups: List<BagGroup>, onMoveClub: (Long, Int) -> Unit) {
        val id = clubId
        if (commit && id != null && appliedSlot != originSlot) {
            val group = groups.firstOrNull { g -> g.clubs.any { it.id == id } }
            if (group != null) onMoveClub(id, appliedSlot - group.startIndex)
        }
        clubId = null
        appliedSlot = -1
        originSlot = -1
        offsetPx = 0f
    }
}

@Composable
private fun ReorderRow(
    club: ClubRecord,
    layoutIndex: Int,
    workingIndex: Int,
    group: BagGroup,
    groups: List<BagGroup>,
    dragState: ReorderDragState,
    rowPitchPx: Float,
    edgePx: Float,
    stepPx: Float,
    scrollState: ScrollState,
    onMoveClub: (Long, Int) -> Unit,
) {
    val isDragging = dragState.clubId == club.id
    val draggable = group.clubs.size >= 2

    val shiftTargetPx =
        if (dragState.clubId == null || isDragging) 0f else (workingIndex - layoutIndex) * rowPitchPx
    val animatedShiftPx by animateFloatAsState(
        targetValue = shiftTargetPx,
        animationSpec = tween(durationMillis = 150),
        label = "reorderRowShift",
    )
    val shiftPx = if (isDragging) dragState.visualOffsetPx(groups, rowPitchPx) else animatedShiftPx

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = shiftPx }
            .background(
                if (isDragging) GolfColors.Panel else Color.Transparent,
                RoundedCornerShape(GolfSpacing.Sm),
            )
            .then(
                if (isDragging) {
                    Modifier.border(1.dp, GolfColors.Teal, RoundedCornerShape(GolfSpacing.Sm))
                } else {
                    Modifier
                },
            )
            .onGloballyPositioned { coords ->
                if (dragState.clubId == null) {
                    dragState.rowTops[club.id] =
                        coords.positionInRoot().y - dragState.viewportTopPx + scrollState.value
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .size(HandleSize)
                .pointerInput(club.id, groups) {
                    if (!draggable) return@pointerInput
                    detectDragGestures(
                        onDragStart = { dragState.start(club.id, layoutIndex) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragState.drag(
                                deltaY = dragAmount.y,
                                groups = groups,
                                type = club.type,
                                rowPitchPx = rowPitchPx,
                                scroll = scrollState,
                                edgePx = edgePx,
                                stepPx = stepPx,
                            )
                        },
                        onDragEnd = {
                            dragState.finish(commit = true, groups = groups, onMoveClub = onMoveClub)
                        },
                        onDragCancel = {
                            dragState.finish(commit = false, groups = groups, onMoveClub = onMoveClub)
                        },
                    )
                },
        ) {
            val barWidth = 18.dp.toPx()
            val barHeight = 2.dp.toPx()
            val gap = 6.dp.toPx()
            val left = (size.width - barWidth) / 2f
            val top = (size.height - (barHeight * 3 + gap * 2)) / 2f
            val barColor = if (draggable) GolfColors.TextSecondary else GolfColors.TextMuted
            repeat(3) { i ->
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, top + i * (barHeight + gap)),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f),
                )
            }
        }
        Text(
            text = club.name,
            style = GolfTypography.Body,
            color = if (draggable) GolfColors.TextPrimary else GolfColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        if (club.isTemp) {
            Text(
                text = "TEST",
                style = GolfTypography.MetricLabel,
                color = GolfColors.Amber,
                modifier = Modifier.padding(end = GolfSpacing.Sm),
            )
        }
    }
}
```

Implementation notes:

- `pointerInput(club.id, groups)` restarts the gesture detector whenever the bag order changes (a new `groups` value), so the handlers never run with stale lists; `groups` is `remember(clubs)`-cached between emissions. Do not key on a freshly built id list — it would restart and cancel an active drag on every recomposition.
- The preview list is a single `moveId(ids, originSlot, appliedSlot)` move (equivalent to the sequence of individual moves).
- A drop calls `onMoveClub` only when the slot changed; after the write, the `clubs` flow re-emits and the panel renders the authoritative order.
- Row shifts animate with a 150 ms tween; the dragged row itself is untweened (it follows the finger).

- [ ] **Step 2: Verify it compiles**

Run:
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```
git add app/src/main/kotlin/com/hpsmiles/golfsim/settings/BagReorderPanel.kt
git commit -m "feat(settings): add bag reorder drag panel"
```

---

### Task 4: Settings entry and mode (`:app`)

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt`

All edits are in this one file; `bagGroups` and `BagReorderPanel` are same-package, no new imports. `Row`, `border`, `clickable`, `padding`, `RoundedCornerShape`, `GolfSpacing`, `GolfColors`, `GolfTypography`, `rememberScrollState`, `mutableStateOf` are already imported.

- [ ] **Step 1: Add the `onMoveClub` parameter**

Replace the tail of the parameter list:

```kotlin
    onDeleteClub: (Long) -> Unit = {},
) {
```
with:

```kotlin
    onDeleteClub: (Long) -> Unit = {},
    onMoveClub: (Long, Int) -> Unit = { _, _ -> },
) {
```

- [ ] **Step 2: Add reorder mode state**

After `val scope = rememberCoroutineScope()` add:

```kotlin
    // Reorder mode: the focused panel replaces the settings body (spec §5.1).
    var reorderMode by remember { mutableStateOf(false) }
    // Hoisted so returning from reorder mode keeps the settings scroll position.
    val settingsScroll = rememberScrollState()
```

- [ ] **Step 3: Restructure the body**

Replace the opening of the screen's root `Column` (currently `.fillMaxSize().background(...).verticalScroll(rememberScrollState()).padding(GolfSpacing.Md)` plus `verticalArrangement`) so the root no longer scrolls; the normal cards move into an inner scrolling `Column` in the `else` branch:

```kotlin
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GolfColors.Base)
            .padding(GolfSpacing.Md),
    ) {
        if (reorderMode) {
            BagReorderPanel(
                clubs = clubs,
                onMoveClub = onMoveClub,
                onDone = { reorderMode = false },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(settingsScroll),
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
            ) {
```

The existing `SectionCard` blocks (RAPSODO AUTH … DEBUG) stay exactly as they are inside this new inner `Column` (indentation is not functional; re-indent by one level only if your formatter insists).

At the end of the card block, the structure gains two closing braces. The old tail:

```kotlin
        SectionCard("DEBUG - NOTIFICATION CAPTURE (BENCH)") {
            ...
        }
    }

    if (renameTarget != null) {
```

becomes:

```kotlin
        SectionCard("DEBUG - NOTIFICATION CAPTURE (BENCH)") {
            ...
        }
            } // closes the else-branch scroll Column
        } // closes the else
    } // closes the root Column

    if (renameTarget != null) {
```

(The `...` stands for the DEBUG card's existing content — unchanged. Net change: the two extra closing braces.)

- [ ] **Step 4: Add the EDIT ORDER chip**

In the BAG card, between the description `Text(...)` and `if (clubs.isEmpty())`, insert:

```kotlin
            // Bag reorder entry: shown once any type group has 2+ clubs.
            if (bagGroups(clubs).any { it.clubs.size >= 2 }) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = GolfSpacing.Sm)) {
                    Text(
                        text = "EDIT ORDER",
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.TextSecondary,
                        modifier = Modifier
                            .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                            .clickable { reorderMode = true }
                            .padding(horizontal = GolfSpacing.Md, vertical = 6.dp),
                    )
                }
            }
```

- [ ] **Step 5: Verify it compiles**

Run:
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```
git add app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt
git commit -m "feat(settings): add EDIT ORDER entry in the bag editor"
```

---

### Task 5: Wire AppRoot and build (`:app`)

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`

- [ ] **Step 1: Pass the move callback**

In the `SettingsScreen(...)` call (≈ line 762), after the `onDeleteClub = { ... },` block, add:

```kotlin
                            onMoveClub = { id, index ->
                                scope.launch { sessionRepository.moveClub(id, index) }
                            },
```

(`scope` and `sessionRepository` are already in scope there.)

- [ ] **Step 2: Full build**

Run:
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain build
```
Expected: BUILD SUCCESSFUL — assemble + all unit tests.

- [ ] **Step 3: Commit**

```
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(app): wire bag reorder into settings"
```

---

### Task 6: On-device verification (manual)

- [ ] **Step 1: Install and reproduce the report**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:installDebug
```
On the tablet: Settings ▸ BAG ▸ add `3i` (type IRON) — it lands under `9i`. Tap `EDIT ORDER`.

Expected: the panel shows REORDER CLUBS, the group headers (`DRIVER`, `WOODS`, `HYBRIDS`, `IRONS`, `WEDGES`, `PUTTER`) and a DONE chip; IRONS shows `4i … 9i, 3i`.

- [ ] **Step 2: Reorder and observe the preview**

Drag `3i`'s handle to the top of IRONS: the row lifts (teal border / panel background), other IRONS rows animate out of the way, the drag is clamped at the group edges, and dropping calls through — IRONS reads `3i, 4i … 9i`.

Expected: only IRONS reorders; WOODS/WEDGES never move; a same-slot drop does nothing.

- [ ] **Step 3: Check the downstream consumers**

Open the range club picker: `3i` appears before `4i`. Start a BAG MAPPING: the plan order follows the new bag order. HISTORY grouping also follows.

- [ ] **Step 4: Persistence and edge cases**

Force-stop the app and relaunch: the order persists. Drag a wedge within WEDGES. Drag a club hard past the group edge — clamped. Press and hold a drag near the top/bottom of the panel — the panel auto-scrolls (feel check; the rate is device-tuned). Add a TEST club and confirm it is draggable and still purged by END SESSION.

- [ ] **Step 5: Confirm no reliance on unfinished work**

If all of the above passes, the feature is done — stop and hand back to the user for the on-tablet sign-off.

---

## Self-review notes

- Spec coverage: §3.1 DAO (Task 1), §3.2 moveClub (Task 1), §4 pure math + edge table (Task 2), §5.1 entry/mode (Task 4), §5.2 panel (Task 3), §5.3 wiring (Task 5), §6 repository + math tests (Tasks 1–2) and manual script (Task 6). §5.4 behavior matrix is covered by those implementations; the only accepted deviation is the brief old-order frame while the drop write lands (documented in Task 3 notes).
- No schema change, no migration, no new dependencies.
