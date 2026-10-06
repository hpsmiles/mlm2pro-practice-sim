# Bag Mapping & True Gapping (M6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A guided full-bag mapping test on a new top-level BAG tab: 5 valid shots per club (quality-gated up to 15), auto-filtered misreads/duffs, write-through persistence with resume-after-kill, and a box-plot carry matrix result with gap flags, drill-down, retest, and history.

**Architecture:** Room v3→v4 adds `bag_mapping_sessions` + `bag_mapping_shots` (games isolation pattern — mapping data never enters range sessions/`shots`). The repository write path recomputes the duff filter through the pure `BagMappingStats` module on every append. `AppRoot` routes decoded shots to a plain `BagMappingCollector` state holder (games seam) while collecting; UI derives everything else from Room getter-flows, mirroring the `HistoryScreen` selection pattern.

**Tech Stack:** Kotlin + Jetpack Compose (Canvas painter, no Navigation-Compose, no ViewModels), Room + raw-SQLite migration tests (Robolectric), pure JVM logic classes. Spec: `docs/superpowers/specs/2026-10-06-m6-bag-mapping-design.md`.

**Spec amendment (one, deliberate):** the spec §7 table list omits a club snapshot column, but §4/§9 require it (clubs added mid-test don't join; renamed/deleted clubs don't reshape past sessions; skipped clubs render "no data" rows). The `bag_mapping_sessions` table therefore gains `clubList TEXT NOT NULL` — comma-joined `name:TYPE` pairs. Safe because `addClub`/`renameClub` already reject commas in names (SessionRepository).

**Environment note (Windows):** JDK is not on `PATH`. Prefix every Gradle command with:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```
Gradle flags precede task names (`--console=plain` if needed); quiet configure output ≠ hung. Aggregate `test` rejects `--tests` — always use `testDebugUnitTest` with `--tests`. No bash available; use PowerShell.

**Existing anchors (verified):**
- `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt:82` — `private enum class RangeTab { RANGE, GAMES, SETTINGS, HISTORY }`; tab state `:104`; `clubRecords` gated capture `:189`; `persist()` `:209`; `completeGame()` `:223`; `routeShot()` `:247`; `onMeasurement`/`onMisread` wiring `:267-282`; `fireDemo()` `:294`; `gameResults` capture `:492`; `requestTab()` `:507`; NavRail buttons `:537-541`; `when (tab)` switch `:617-696`.
- `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabase.kt:16` — version 3; `MIGRATION_2_3` DDL style `:53-63`; `SessionRepository.build()` registers migrations (`SessionRepository.kt:347-350`).
- `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt` — getter-flow pattern `:57-74` (must be getters, not stored fields — corrupt-DB recovery swaps `db`); never-throw + `persistError` contract on every write (`:117-140`); `db.withTransaction` usage `:126-133`; `DEFAULT_CLUBS` `:394`.
- Entities/DAOs style: `GameResultEntity.kt` (`@Entity(tableName=...)`, `object GameModes` constants), `GameResultDao.kt` (`@Insert suspend`, `@Query`, Flow returns), `ShotEntity.kt` (raw block + physics caches comment discipline).
- Migration test pattern: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/MigrationFrom2Test.kt` — hand-built raw SQLite vN file, forged `room_master_table` identity hash `'94014c8b5c8f2a1fda6d5bef72244606'`, `raw.version = N`, then `SessionRepository.open(context)` + `initializeAndRestore()` and assertions. No `MigrationTestHelper` in this repo.
- Physics seam: `RangeSession.add()` (`RangeSession.kt:86-107`) builds `LaunchConditions(...)` (throws `IllegalArgumentException` on impossible decodes → return null) then `BallFlightEngine.simulate(launch, Environment(), <SurfaceProvider>)`. `ShotResult(carryM, rolloutM, totalM, sideM, apexM, flightTimeSec, samples, restX, restY, groundHops)` — `ShotResult.kt:12`. `UniformSurface(surface: Surface)` in `core/physics/SurfaceProvider.kt`. Carry is surface-independent; `totalM` (rollout) is not — mapping uses the default `Surface.FAIRWAY_NORMAL` uniformly (documented simplification; carry is the gapping metric).
- Games pattern: `GamesScreen.kt:38-130` (plain when-enum sub-state, hoisted mode), `TargetPracticeGame.kt:25-121` (plain state holder, injectable `clockMs`/`simulator`, JVM-tested).
- Design tokens: `GolfColors.{Base, Line, TextPrimary, TextSecondary, Teal, Teal55, Teal40, Amber, AmberHalo, AmberGlow, AlertRed}`, `GolfTypography.{Hero, MetricValue, Unit, MetricLabel, Status, ScreenTitle, Body, BodySmall}`, `GolfSpacing.{Xs, Sm, Md, Lg, Xl, Xxl, CornerCard}`. No Material icons — text-glyph chips only.
- Units: hard-coded `"%.0f m"` convention (`HistoryFormats.kt:16`); `UnitsPreference` intentionally out of scope.

**File structure (all new files, one responsibility each):**

| File | Responsibility |
|---|---|
| `core/data/.../bag/BagMappingStats.kt` | Pure duff filter + club distributions (repo + app) |
| `core/data/.../entity/BagMappingSessionEntity.kt` | Session row + status constants + snapshot codec |
| `core/data/.../entity/BagMappingShotEntity.kt` | Mapping shot row (snapshots + filter cache) |
| `core/data/.../dao/BagMappingSessionDao.kt` | Session queries |
| `core/data/.../dao/BagMappingShotDao.kt` | Shot queries + filter cache updates |
| `core/data/.../Mlm2proDatabase.kt` (modify) | v4, new entities/DAOs, `MIGRATION_3_4` |
| `core/data/.../SessionRepository.kt` (modify) | Bag mapping API (start/append/complete/flows) |
| `core/data/schemas/.../4.json` (generated) | Committed schema export |
| `app/.../bag/ClubQualityGate.kt` | Pure quality-gate predicate + constants |
| `app/.../bag/GapAnalysis.kt` | Pure adjacent-club gap flags |
| `app/.../bag/BoxPlotGeom.kt` | Pure value→pixel axis mapping |
| `app/.../bag/BagMappingCollector.kt` | Plain state holder: plan, current club, gate prompt, shot computation |
| `app/.../bag/BagMappingFormats.kt` | Display formatting (metres, smash, dates) |
| `app/.../bag/BagMappingScreen.kt` | INTRO / COLLECTING / RESULT / HISTORY screen |
| `app/.../bag/CarryMatrixCanvas.kt` | Compose Canvas box-plot matrix painter |
| `app/.../range/AppRoot.kt` (modify) | BAG tab, flows, shot routing, start/resume/complete |
| `ROADMAP.md` (modify) | M6 → shipped |

Tests: `core/data/src/test/.../bag/BagMappingStatsTest.kt`, `core/data/src/test/.../MigrationFrom3Test.kt`, `core/data/src/test/.../BagMappingRepositoryTest.kt`, `app/src/test/.../bag/{ClubQualityGateTest,GapAnalysisTest,BoxPlotGeomTest,BagMappingCollectorTest}.kt`.

---

### Task 1: `BagMappingStats` — pure duff filter + distributions

**Files:**
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/bag/BagMappingStats.kt`
- Test: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/bag/BagMappingStatsTest.kt`

- [x] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.core.data.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

/**
 * Golden cases for the M6 duff filter + distributions (spec §5). The filter
 * is a pure function of the club's whole shot set — recomputed on every
 * write, so verdicts may change retroactively as shots accumulate.
 */
class BagMappingStatsTest {

    private fun shot(id: Long, ballSpeed: Double) = BagMappingShotEntity(
        id = id, sessionId = 1, clubName = "7i", clubType = "IRON",
        timestampMs = id, clubHeadSpeedMps = 33.0, ballSpeedMps = ballSpeed,
        launchAngleDeg = 12.0, launchDirDeg = -1.0, spinAxisDeg = -4.0,
        totalSpinRpm = 8000, carryM = 140.0, totalM = 150.0,
    )

    @Test
    fun `dormant below three shots - everything kept`() {
        val verdicts = BagMappingStats.applyDuffFilter(listOf(shot(1, 20.0), shot(2, 48.0)))
        assertEquals(listOf(false, false), verdicts.map { it.filtered })
        assertEquals(listOf(null, null), verdicts.map { it.reason })
    }

    @Test
    fun `three shots - low ball speed filtered against median of ALL shots`() {
        // median(20, 48, 46) = 46 → cutoff 39.1 → 20 is a duff
        val verdicts = BagMappingStats.applyDuffFilter(listOf(shot(1, 20.0), shot(2, 48.0), shot(3, 46.0)))
        assertEquals(listOf(true, false, false), verdicts.map { it.filtered })
        assertEquals(BagMappingStats.REASON_DUFF_LOW_BALL_SPEED, verdicts[0].reason)
    }

    @Test
    fun `recompute retroactively filters early shots`() {
        // Same three shots as above but inserted in the order 46, 48, 20:
        // after the 3rd write the FIRST row (id 3, 20 m/s) flips to filtered.
        val verdicts = BagMappingStats.applyDuffFilter(listOf(shot(3, 46.0), shot(2, 48.0), shot(1, 20.0)))
        assertEquals(listOf(false, false, true), verdicts.map { it.filtered })
    }

    @Test
    fun `poisoned baseline - filter blind spot is real, gate catches it`() {
        // Two duffs first → median sits low → 85% check cannot separate.
        // Documented blind spot (spec §5): the window condition of the
        // quality gate prompts "hit more", which pulls the median up.
        val verdicts = BagMappingStats.applyDuffFilter(listOf(shot(1, 20.0), shot(2, 22.0), shot(3, 48.0)))
        assertEquals(listOf(false, false, false), verdicts.map { it.filtered })
    }

    @Test
    fun `low side only - a very fast strike is never filtered`() {
        val verdicts = BagMappingStats.applyDuffFilter(listOf(shot(1, 48.0), shot(2, 50.0), shot(3, 70.0)))
        assertEquals(listOf(false, false, false), verdicts.map { it.filtered })
    }

    @Test
    fun `already-filtered rows re-judged identically - deterministic`() {
        val shots = listOf(shot(1, 20.0), shot(2, 48.0), shot(3, 46.0))
        val first = BagMappingStats.applyDuffFilter(shots)
        val second = BagMappingStats.applyDuffFilter(shots)
        assertEquals(first, second)
    }

    @Test
    fun `median - odd and even sizes`() {
        assertEquals(3.0, BagMappingStats.median(listOf(1.0, 2.0, 3.0, 4.0, 5.0))!!, 1e-9)
        assertEquals(2.5, BagMappingStats.median(listOf(1.0, 2.0, 3.0, 4.0))!!, 1e-9)
        assertNull(BagMappingStats.median(emptyList()))
    }

    @Test
    fun `distribution - Tukey halves with median included for odd sizes`() {
        val d = BagMappingStats.distribution(listOf(1.0, 2.0, 3.0, 4.0, 5.0))!!
        assertEquals(3.0, d.median, 1e-9)
        assertEquals(3.0, d.mean, 1e-9)
        assertEquals(sqrt(2.0), d.sigma, 1e-9) // population σ of 1..5
        assertEquals(2.0, d.q1, 1e-9)          // median of [1,2,3]
        assertEquals(4.0, d.q3, 1e-9)          // median of [3,4,5]
        assertEquals(1.0, d.min, 1e-9)
        assertEquals(5.0, d.max, 1e-9)
        assertEquals(5, d.count)
        val d4 = BagMappingStats.distribution(listOf(1.0, 2.0, 3.0, 4.0))!!
        assertEquals(1.5, d4.q1, 1e-9)
        assertEquals(3.5, d4.q3, 1e-9)
        assertNull(BagMappingStats.distribution(emptyList()))
    }
}
```

- [x] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.bag.BagMappingStatsTest"
```
Expected: FAIL — `BagMappingStats` and `BagMappingShotEntity` unresolved (entity arrives in Task 2; create both files in this task's Step 3 if the test cannot compile without the entity — the entity is a plain data class with no Room processing needed yet).

- [x] **Step 3: Write minimal implementation**

Create the entity FIRST (needed by stats + compile; Room annotations are inert until Task 3 registers the entity). `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/BagMappingShotEntity.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stored bag-mapping shot (M6 spec §7). ALL session shots are stored —
 * kept AND filtered — so the RESULT drill-down can show every strike.
 * Raw block = BallData field-for-field; carry/total are physics caches
 * written once at capture (same discipline as `shots`). Snapshot columns
 * (`clubName`, `clubType`) mean club renames/deletes never rewrite mapping
 * history. `sessionId` is a plain indexed column, no hard FK — the
 * repository manages lifecycle (existing entity conventions).
 */
@Entity(tableName = "bag_mapping_shots", indices = [Index("sessionId")])
data class BagMappingShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val clubName: String,
    val clubType: String,          // ClubType.name snapshot
    val timestampMs: Long,
    // raw block
    val clubHeadSpeedMps: Double,
    val ballSpeedMps: Double,
    val launchAngleDeg: Double,
    val launchDirDeg: Double,
    val spinAxisDeg: Double,
    val totalSpinRpm: Int,
    // physics caches
    val carryM: Double,
    val totalM: Double,
    /** Recomputed duff-filter cache (BagMappingStats); rewritten on every club write. */
    val filtered: Boolean = false,
    /** Null when kept; DUFF_LOW_BALL_SPEED when filtered. */
    val filterReason: String? = null,
)
```

Then `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/bag/BagMappingStats.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import kotlin.math.sqrt

/**
 * Pure M6 mapping math (spec §5): duff filter + per-club distributions.
 * No framework types; deterministic — same shots in, same verdicts out.
 * Lives in :core:data because the repository write path recomputes the
 * filter on every append; :app consumes it for rendering.
 */
object BagMappingStats {

    /** A shot below this fraction of the club's median ball speed is a duff. */
    const val DUFF_BALL_SPEED_FRACTION = 0.85

    /** The duff filter stays dormant until the club has this many shots. */
    const val DUFF_MIN_SHOTS = 3

    const val REASON_DUFF_LOW_BALL_SPEED = "DUFF_LOW_BALL_SPEED"

    /** One shot's recomputed filter state, keyed by row id. */
    data class FilterVerdict(val id: Long, val filtered: Boolean, val reason: String?)

    /**
     * Median over ALL stored shots of the club (kept and filtered alike —
     * spec worked example). Dormant below [DUFF_MIN_SHOTS]: everything kept.
     * From 3+ shots EVERY shot is judged (including the earliest): ball
     * speed < 85% of the median ball speed → DUFF_LOW_BALL_SPEED.
     * Low-side only — an unusually long strike is real data (spec §5).
     */
    fun applyDuffFilter(shots: List<BagMappingShotEntity>): List<FilterVerdict> {
        if (shots.size < DUFF_MIN_SHOTS) {
            return shots.map { FilterVerdict(it.id, filtered = false, reason = null) }
        }
        val medianBallSpeed = median(shots.map { it.ballSpeedMps })
            ?: return shots.map { FilterVerdict(it.id, filtered = false, reason = null) }
        val cutoff = medianBallSpeed * DUFF_BALL_SPEED_FRACTION
        return shots.map { shot ->
            if (shot.ballSpeedMps < cutoff) {
                FilterVerdict(shot.id, filtered = true, reason = REASON_DUFF_LOW_BALL_SPEED)
            } else {
                FilterVerdict(shot.id, filtered = false, reason = null)
            }
        }
    }

    /** Median: middle value, or mean of the two middle values. Null when empty. */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** Distribution summary over one club's kept carries (null when empty). */
    data class Distribution(
        val median: Double,
        val mean: Double,
        /** Population σ (descriptive, fixed set). */
        val sigma: Double,
        val q1: Double,
        val q3: Double,
        val min: Double,
        val max: Double,
        val count: Int,
    )

    fun distribution(values: List<Double>): Distribution? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mean = sorted.average()
        val variance = sorted.sumOf { (it - mean) * (it - mean) } / sorted.size
        return Distribution(
            median = median(sorted)!!,
            mean = mean,
            sigma = sqrt(variance),
            q1 = tukeyHalf(sorted, lower = true),
            q3 = tukeyHalf(sorted, lower = false),
            min = sorted.first(),
            max = sorted.last(),
            count = sorted.size,
        )
    }

    /**
     * Tukey halves (median INCLUDED in both halves for odd sizes — classic
     * box plot): Q1 = median of the lower half, Q3 = median of the upper half.
     */
    private fun tukeyHalf(sorted: List<Double>, lower: Boolean): Double {
        val halfSize = (sorted.size + 1) / 2
        val half = if (lower) {
            sorted.subList(0, halfSize)
        } else {
            sorted.subList(sorted.size - halfSize, sorted.size)
        }
        return median(half)!!
    }
}
```

- [x] **Step 4: Run test to verify it passes**

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.bag.BagMappingStatsTest"
```
Expected: PASS (all 8 tests).

- [x] **Step 5: Commit**

```powershell
git add core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/bag/BagMappingStats.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/BagMappingShotEntity.kt core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/bag/BagMappingStatsTest.kt
git commit -m "feat(core-data): pure bag-mapping duff filter and distributions (M6)"
```

---

### Task 2: Session entity + DAOs

**Files:**
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/BagMappingSessionEntity.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/BagMappingSessionDao.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/BagMappingShotDao.kt`

- [x] **Step 1: Create `BagMappingSessionEntity.kt`**

```kotlin
package com.hpsmiles.golfsim.core.data.entity

import com.hpsmiles.golfsim.core.data.record.ClubType

/** Status values for [BagMappingSessionEntity.status]. */
object BagMappingStatus {
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"
}

/**
 * One guided bag-mapping test (M6 spec §7). At most one row with status
 * IN_PROGRESS exists — the repository enforces it (spec §4). Mapping
 * sessions are isolated from range sessions: separate tables, games pattern.
 *
 * [clubList] snapshots the bag at START as comma-joined "name:TYPE" pairs
 * (names cannot contain commas — addClub/renameClub reject them). Clubs
 * added mid-test never join; renames/deletes never reshape a running or
 * past session (spec §4 + §9 "no data" rows for skipped clubs).
 */
@Entity(tableName = "bag_mapping_sessions")
data class BagMappingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    /** Null while the session is open; set when completed. */
    val completedAtMs: Long?,
    val status: String,            // BagMappingStatus values
    val clubList: String,
) {
    /** Bag at session start, in guided order: (clubName, clubType) pairs. */
    fun clubSnapshot(): List<Pair<String, ClubType>> =
        clubList.split(',').filter { it.contains(':') }.map { entry ->
            val parts = entry.split(':', limit = 2)
            parts[0] to ClubType.fromName(parts[1])
        }

    companion object {
        /** Serializes the bag snapshot; inverse of [clubSnapshot]. */
        fun encodeClubSnapshot(clubs: List<Pair<String, ClubType>>): String =
            clubs.joinToString(",") { "${it.first}:${it.second.name}" }
    }
}
```

- [x] **Step 2: Create the two DAOs**

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/BagMappingSessionDao.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BagMappingSessionDao {

    @Insert
    suspend fun insert(session: BagMappingSessionEntity): Long

    @Query("SELECT * FROM bag_mapping_sessions WHERE status = 'IN_PROGRESS' ORDER BY id DESC LIMIT 1")
    suspend fun findInProgress(): BagMappingSessionEntity?

    @Query("SELECT * FROM bag_mapping_sessions WHERE status = 'IN_PROGRESS' ORDER BY id DESC LIMIT 1")
    fun observeInProgress(): Flow<BagMappingSessionEntity?>

    @Query("SELECT * FROM bag_mapping_sessions WHERE status = 'COMPLETED' ORDER BY completedAtMs DESC, id DESC LIMIT 1")
    fun observeLatestCompleted(): Flow<BagMappingSessionEntity?>

    @Query("SELECT * FROM bag_mapping_sessions WHERE status = 'COMPLETED' ORDER BY completedAtMs DESC, id DESC")
    fun observeHistory(): Flow<List<BagMappingSessionEntity>>

    @Query("UPDATE bag_mapping_sessions SET status = '${BagMappingStatus.COMPLETED}', completedAtMs = :completedAtMs WHERE id = :id")
    suspend fun complete(id: Long, completedAtMs: Long)
}
```

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/BagMappingShotDao.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BagMappingShotDao {

    @Insert
    suspend fun insert(shot: BagMappingShotEntity): Long

    @Query("SELECT * FROM bag_mapping_shots WHERE sessionId = :sessionId ORDER BY timestampMs, id")
    suspend fun shotsForSession(sessionId: Long): List<BagMappingShotEntity>

    @Query("SELECT * FROM bag_mapping_shots WHERE sessionId = :sessionId ORDER BY timestampMs, id")
    fun observeShots(sessionId: Long): Flow<List<BagMappingShotEntity>>

    @Query("SELECT * FROM bag_mapping_shots WHERE sessionId = :sessionId AND clubName = :clubName ORDER BY timestampMs, id")
    suspend fun shotsForClub(sessionId: Long, clubName: String): List<BagMappingShotEntity>

    @Query("UPDATE bag_mapping_shots SET filtered = :filtered, filterReason = :reason WHERE id = :id")
    suspend fun setFiltered(id: Long, filtered: Boolean, reason: String?)
}
```

- [x] **Step 3: Compile to verify**

```powershell
.\gradlew.bat :core:data:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL (entities compile; Room KSP validates DAO queries against registered entities — these entities are not yet registered, KSP ignores them until Task 3).

- [x] **Step 4: Commit**

```powershell
git add core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/BagMappingSessionEntity.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/BagMappingSessionDao.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/BagMappingShotDao.kt
git commit -m "feat(core-data): bag mapping session entity and DAOs (M6)"
```

### Task 3: Database v4 — register entities/DAOs + `MIGRATION_3_4` + schema export

**Files:**
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabase.kt`
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt:347-350` (migration registration only)
- Create (generated, then committed): `core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/4.json`

- [x] **Step 1: Register entities, bump version, add `MIGRATION_3_4`**

In `Mlm2proDatabase.kt`: add imports for the two new entities and two DAOs; change the database annotation and DAO accessors to:

```kotlin
@Database(
    entities = [
        SessionEntity::class, ShotEntity::class, ClubEntity::class, GameResultEntity::class,
        BagMappingSessionEntity::class, BagMappingShotEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class Mlm2proDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun shotDao(): ShotDao
    abstract fun clubDao(): ClubDao
    abstract fun gameResultDao(): GameResultDao
    abstract fun bagMappingSessionDao(): BagMappingSessionDao
    abstract fun bagMappingShotDao(): BagMappingShotDao
```

and add to the companion (after `MIGRATION_2_3`):

```kotlin
        /**
         * M6 spec §7: bag_mapping_sessions + bag_mapping_shots. Existing
         * tables untouched. No DEFAULT clauses — fresh tables, every column
         * is NOT NULL or genuinely nullable, so the entities need no
         * @ColumnInfo defaultValue (game_results pattern). Corrupt-file
         * recovery reuses build(), so it inherits the migration.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bag_mapping_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startedAtMs` INTEGER NOT NULL, `completedAtMs` INTEGER, " +
                        "`status` TEXT NOT NULL, `clubList` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bag_mapping_shots` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, `clubName` TEXT NOT NULL, " +
                        "`clubType` TEXT NOT NULL, `timestampMs` INTEGER NOT NULL, " +
                        "`clubHeadSpeedMps` REAL NOT NULL, `ballSpeedMps` REAL NOT NULL, " +
                        "`launchAngleDeg` REAL NOT NULL, `launchDirDeg` REAL NOT NULL, " +
                        "`spinAxisDeg` REAL NOT NULL, `totalSpinRpm` INTEGER NOT NULL, " +
                        "`carryM` REAL NOT NULL, `totalM` REAL NOT NULL, " +
                        "`filtered` INTEGER NOT NULL, `filterReason` TEXT)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bag_mapping_shots_sessionId` " +
                        "ON `bag_mapping_shots` (`sessionId`)",
                )
            }
        }
```

- [x] **Step 2: Register the migration in `SessionRepository.build()`**

In `SessionRepository.kt:349` change:

```kotlin
            .addMigrations(
                Mlm2proDatabase.MIGRATION_1_2,
                Mlm2proDatabase.MIGRATION_2_3,
                Mlm2proDatabase.MIGRATION_3_4,
            )
```

- [x] **Step 3: Build `:core:data` to generate `4.json` and validate schema**

```powershell
.\gradlew.bat :core:data:build
```
Expected: BUILD SUCCESSFUL. Room KSP writes `core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/4.json`. Verify it exists and contains `bag_mapping_sessions` (with `clubList`) and `bag_mapping_shots` under `entities`, and `"version": 4`.

- [x] **Step 4: Commit (schema JSON included)**

```powershell
git add core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabase.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/4.json
git commit -m "feat(core-data): Room v4 with bag mapping tables (M6)"
```

---

### Task 4: `MigrationFrom3Test` — hand-built v3 file migrates, prior data survives

**Files:**
- Test: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/MigrationFrom3Test.kt`

- [x] **Step 1: Write the test (no failing-run step — it exercises Task 3's code)**

Mirror `MigrationFrom2Test` exactly: hand-built raw SQLite v3 file (v2 DDL + `game_results`), forged identity hash, `raw.version = 3`, then open through the repository. Assert prior data survives AND the new tables are usable.

```kotlin
package com.hpsmiles.golfsim.core.data

import android.database.sqlite.SQLiteDatabase
import com.hpsmiles.golfsim.core.data.entity.BagMappingStatus
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.ble.BallData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * MIGRATION_3_4 against a hand-built v3 file (same pattern as
 * MigrationFrom1Test/2Test - no MigrationTestHelper in this repo). Prior
 * sessions/shots/clubs/game results must survive; the new bag tables must
 * be usable through the repository immediately after.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MigrationFrom3Test {

    @Test
    fun `v3 file migrates - bag tables usable - prior data intact`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("golfsim.db")
        dbFile.parentFile!!.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        // v3 DDL (sessions, shots, clubs, game_results) - mirrors schemas/.../3.json
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startedAtEpochMs` INTEGER NOT NULL, `endedAtEpochMs` INTEGER, `title` TEXT, " +
                "`misreadCount` INTEGER NOT NULL)",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `shots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `timestampMs` INTEGER NOT NULL, " +
                "`source` INTEGER NOT NULL, `clubName` TEXT, `clubHeadSpeedMps` REAL NOT NULL, " +
                "`ballSpeedMps` REAL NOT NULL, `launchDirDeg` REAL NOT NULL, `launchAngleDeg` REAL NOT NULL, " +
                "`spinAxisDeg` REAL NOT NULL, `spinRpm` INTEGER NOT NULL, `unknown1` INTEGER NOT NULL, " +
                "`unknown2` INTEGER NOT NULL, `carryM` REAL NOT NULL, `totalM` REAL NOT NULL, " +
                "`sideM` REAL NOT NULL, `apexM` REAL NOT NULL, `flightTimeSec` REAL NOT NULL, " +
                "`excluded` INTEGER NOT NULL DEFAULT 0, `clubWasTemp` INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_shots_sessionId` ON `shots` (`sessionId`)")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `clubs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `type` TEXT NOT NULL DEFAULT 'IRON', " +
                "`isTemp` INTEGER NOT NULL DEFAULT 0)",
        )
        raw.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_clubs_name` ON `clubs` (`name`)")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `game_results` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`mode` TEXT NOT NULL, `difficulty` TEXT, `distanceBin` INTEGER NOT NULL, " +
                "`score` INTEGER NOT NULL, `source` INTEGER NOT NULL, " +
                "`playedAtEpochMs` INTEGER NOT NULL)",
        )
        raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        raw.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, '94014c8b5c8f2a1fda6d5bef72244606')",
        )
        raw.execSQL("INSERT INTO sessions(startedAtEpochMs, endedAtEpochMs, title, misreadCount) VALUES(1000, NULL, NULL, 0)")
        raw.execSQL("INSERT INTO clubs(name, sortOrder, type, isTemp) VALUES('7i', 7, 'IRON', 0)")
        raw.execSQL(
            "INSERT INTO shots(sessionId, seq, timestampMs, source, clubName, clubHeadSpeedMps, " +
                "ballSpeedMps, launchDirDeg, launchAngleDeg, spinAxisDeg, spinRpm, unknown1, unknown2, " +
                "carryM, totalM, sideM, apexM, flightTimeSec, excluded, clubWasTemp) " +
                "VALUES(1, 0, 1000, 0, '7i', 33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10, 140.0, 150.0, -3.0, 27.0, 6.0, 0, 0)",
        )
        raw.execSQL(
            "INSERT INTO game_results(mode, difficulty, distanceBin, score, source, playedAtEpochMs) " +
                "VALUES('TARGET_PRACTICE', 'MEDIUM', 140, 85, 0, 2000)",
        )
        raw.version = 3
        raw.close()

        val repo = SessionRepository.open(context)
        repo.initializeAndRestore() // migration runs here; prior data must survive

        // Prior range data survives.
        val summaries = repo.summaries.first()
        assertEquals(1, summaries.size)
        assertEquals(1, summaries.single().shotCount)
        assertEquals(1, repo.gameResultDao().getAll().size)

        // New bag tables are usable immediately: start → append → complete.
        val bagId = repo.startBagMappingSession(
            listOf(ClubRecord(1, "7i", ClubType.IRON, false)),
            startedAtMs = 5000,
        )!!
        assertNotNull(repo.bagMappingActive.first())
        assertEquals(
            BagMappingStatus.IN_PROGRESS,
            repo.bagMappingActive.first()?.status,
        )
        repo.appendBagMappingShot(
            sessionId = bagId,
            clubName = "7i",
            clubType = ClubType.IRON,
            ballData = BallData(33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10),
            carryM = 140.0,
            totalM = 150.0,
            timestampMs = 5001,
        )
        assertEquals(1, repo.observeBagMappingShots(bagId).first().size)
        repo.completeBagMappingSession(bagId, completedAtMs = 9000)
        assertNull(repo.bagMappingActive.first())
        assertEquals(BagMappingStatus.COMPLETED, repo.bagMappingLatestCompleted.first()?.status)
        assertTrue(repo.bagMappingHistory.first().isNotEmpty())
    }
}
```

- [x] **Step 2: Run test to verify it passes**

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.MigrationFrom3Test"
```
Expected: PASS. If Room throws a schema-validation error, the DDL in Task 3 does not match the entity — align them (diff `4.json` CREATE statements against the migration) and re-run.

- [x] **Step 3: Commit**

```powershell
git add core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/MigrationFrom3Test.kt
git commit -m "test(core-data): MIGRATION_3_4 preserves prior data, bag tables usable (M6)"
```

---

### Task 5: Repository bag-mapping API + repository tests

**Files:**
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt`
- Test: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/BagMappingRepositoryTest.kt`

- [x] **Step 1: Write the failing test**

```kotlin
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
```

- [x] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.BagMappingRepositoryTest"
```
Expected: FAIL — repository members unresolved (`startBagMappingSession` etc.).

- [x] **Step 3: Implement the repository API**

In `SessionRepository.kt`, add imports (`BagMappingSessionEntity`, `BagMappingShotEntity`, `BagMappingStatus`, `com.hpsmiles.golfsim.core.data.bag.BagMappingStats`). Add DAO getters next to the existing ones (after line 39):

```kotlin
    private val bagMappingSessionDao get() = db.bagMappingSessionDao()
    private val bagMappingShotDao get() = db.bagMappingShotDao()
```

Add the getter-flows + API at the end of the class body, before the `companion object` (getter-flow discipline: property GETTERS so they re-derive from the CURRENT `db` and survive corrupt-file recovery, spec comment at `:47-56`):

```kotlin
    // --- Bag mapping (M6, spec §7) -----------------------------------------

    /** Open (in-progress) mapping session, or null. Getter-flow — survives corrupt-file recovery. */
    val bagMappingActive: Flow<BagMappingSessionEntity?>
        get() = bagMappingSessionDao.observeInProgress()

    /** Latest completed mapping result (the active result), or null. Getter-flow. */
    val bagMappingLatestCompleted: Flow<BagMappingSessionEntity?>
        get() = bagMappingSessionDao.observeLatestCompleted()

    /** All completed mapping results, newest first. Getter-flow. */
    val bagMappingHistory: Flow<List<BagMappingSessionEntity>>
        get() = bagMappingSessionDao.observeHistory()

    /** Every stored shot (kept + filtered) of one mapping session. */
    fun observeBagMappingShots(sessionId: Long): Flow<List<BagMappingShotEntity>> =
        bagMappingShotDao.observeShots(sessionId)

    /**
     * Creates the IN_PROGRESS session (spec §4), snapshotting [clubs] (the
     * mapping-eligible bag, already putter/TEST-free) into clubList. At most
     * one IN_PROGRESS session exists (repository-enforced): when one is open
     * its id is returned untouched — START is idempotent, never destructive.
     * Returns null only on a persistence failure (persistError flags; never
     * throws — appendShot contract).
     */
    suspend fun startBagMappingSession(clubs: List<ClubRecord>, startedAtMs: Long): Long? =
        try {
            db.withTransaction {
                val existing = bagMappingSessionDao.findInProgress()
                existing?.id
                    ?: bagMappingSessionDao.insert(
                        BagMappingSessionEntity(
                            startedAtMs = startedAtMs,
                            completedAtMs = null,
                            status = BagMappingStatus.IN_PROGRESS,
                            clubList = BagMappingSessionEntity.encodeClubSnapshot(
                                clubs.map { it.name to it.type },
                            ),
                        ),
                    )
            }.also { persistError.value = false }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "startBagMappingSession failed", t)
            persistError.value = true
            null
        }

    /**
     * Write-through append for one mapping shot (spec §4) — this is what
     * makes resume-after-kill work. Inside the same transaction the duff
     * filter is recomputed over the club's whole shot set (BagMappingStats)
     * so kept-set, stats and plots stay consistent on every write, including
     * retroactively re-judging early shots. Returns the new row id, or null
     * on failure (persistError flags; never throws).
     */
    suspend fun appendBagMappingShot(
        sessionId: Long,
        clubName: String,
        clubType: ClubType,
        ballData: BallData,
        carryM: Double,
        totalM: Double,
        timestampMs: Long,
    ): Long? = try {
        db.withTransaction {
            val shotId = bagMappingShotDao.insert(
                BagMappingShotEntity(
                    sessionId = sessionId,
                    clubName = clubName,
                    clubType = clubType.name,
                    timestampMs = timestampMs,
                    clubHeadSpeedMps = ballData.clubHeadSpeed,
                    ballSpeedMps = ballData.ballSpeed,
                    launchAngleDeg = ballData.launchAngle,
                    launchDirDeg = ballData.launchDirection,
                    spinAxisDeg = ballData.spinAxis,
                    totalSpinRpm = ballData.totalSpin,
                    carryM = carryM,
                    totalM = totalM,
                ),
            )
            recomputeDuffFilter(sessionId, clubName)
            shotId
        }.also { persistError.value = false }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Log.e(TAG, "appendBagMappingShot failed", t)
        persistError.value = true
        null
    }

    /** Recomputes the duff filter for one club and persists changed verdicts. Call inside a transaction. */
    private suspend fun recomputeDuffFilter(sessionId: Long, clubName: String) {
        val shots = bagMappingShotDao.shotsForClub(sessionId, clubName)
        val byId = shots.associateBy { it.id }
        for (verdict in BagMappingStats.applyDuffFilter(shots)) {
            val current = byId[verdict.id] ?: continue
            if (current.filtered != verdict.filtered || current.filterReason != verdict.reason) {
                bagMappingShotDao.setFiltered(verdict.id, verdict.filtered, verdict.reason)
            }
        }
    }

    /** Marks the session COMPLETED — its result becomes the active result. Never throws. */
    suspend fun completeBagMappingSession(id: Long, completedAtMs: Long) {
        try {
            bagMappingSessionDao.complete(id, completedAtMs)
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "completeBagMappingSession failed", t)
            persistError.value = true
        }
    }
```

- [x] **Step 4: Run test to verify it passes, then the whole module**

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.BagMappingRepositoryTest"
.\gradlew.bat :core:data:test
```
Expected: PASS — including the pre-existing migration/repository suites (they pin v1→v2→v3 behavior, untouched).

- [x] **Step 5: Commit**

```powershell
git add core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/BagMappingRepositoryTest.kt
git commit -m "feat(core-data): bag mapping repository API with write-through filter recompute (M6)"
```

### Task 6: `ClubQualityGate` — pure gate predicate

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/ClubQualityGate.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/ClubQualityGateTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Test

/** Golden cases for the M6 quality gate (spec §5). */
class ClubQualityGateTest {

    private fun kept(vararg carries: Double) = carries.toList()

    @Test
    fun `dormant below target - keeps collecting`() {
        assertEquals(
            ClubQualityGate.Verdict.ACCEPT,
            ClubQualityGate.evaluate(kept(140.0, 141.0, 139.0, 142.0), filteredCount = 0),
        )
    }

    @Test
    fun `clean club at target - accept`() {
        assertEquals(
            ClubQualityGate.Verdict.ACCEPT,
            ClubQualityGate.evaluate(kept(140.0, 141.0, 139.0, 142.0, 140.5), filteredCount = 0),
        )
    }

    @Test
    fun `three or more filtered - ask for more`() {
        assertEquals(
            ClubQualityGate.Verdict.ASK_MORE,
            ClubQualityGate.evaluate(kept(140.0, 141.0, 139.0, 142.0, 140.5), filteredCount = 3),
        )
    }

    @Test
    fun `big window - ask for more`() {
        // window 30 > 20% of median 140 = 28
        assertEquals(
            ClubQualityGate.Verdict.ASK_MORE,
            ClubQualityGate.evaluate(kept(125.0, 140.0, 141.0, 142.0, 155.0), filteredCount = 0),
        )
    }

    @Test
    fun `window exactly at threshold - accept`() {
        // window 28 == 20% of median 140 → not > → accept (spec: window > 20%)
        assertEquals(
            ClubQualityGate.Verdict.ACCEPT,
            ClubQualityGate.evaluate(kept(126.0, 140.0, 141.0, 142.0, 154.0), filteredCount = 0),
        )
    }

    @Test
    fun `hard cap - never prompts past fifteen kept`() {
        val fifteen = List(15) { if (it == 0) 100.0 else 140.0 + it } // terrible window
        assertEquals(
            ClubQualityGate.Verdict.ACCEPT,
            ClubQualityGate.evaluate(fifteen, filteredCount = 4),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.ClubQualityGateTest"
```
Expected: FAIL — `ClubQualityGate` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.bag.BagMappingStats

/**
 * M6 quality gate (spec §5): once a club reaches [TARGET_KEPT] kept shots,
 * the player is asked to hit more iff the club is unreliable — at least
 * [MAX_FILTERED_FOR_PROMPT] filtered shots, or the kept carry window
 * exceeding [MAX_WINDOW_FRACTION] of the kept median. Hard cap [MAX_KEPT]:
 * the gate never prompts past it (the player can always accept the result).
 * Constants live here so UI copy and tests share the exact numbers.
 */
object ClubQualityGate {
    const val TARGET_KEPT = 5
    const val MAX_KEPT = 15
    const val MAX_FILTERED_FOR_PROMPT = 3
    const val MAX_WINDOW_FRACTION = 0.20

    enum class Verdict { ACCEPT, ASK_MORE }

    /**
     * Call whenever the kept set changes while collecting. Below
     * [TARGET_KEPT] the gate is dormant (ACCEPT; the caller distinguishes
     * "keep collecting" by kept count). Deterministic; pure.
     */
    fun evaluate(keptCarriesM: List<Double>, filteredCount: Int): Verdict {
        val keptCount = keptCarriesM.size
        if (keptCount < TARGET_KEPT) return Verdict.ACCEPT
        if (keptCount >= MAX_KEPT) return Verdict.ACCEPT
        if (filteredCount >= MAX_FILTERED_FOR_PROMPT) return Verdict.ASK_MORE
        if (keptCount < 2) return Verdict.ACCEPT
        val median = BagMappingStats.median(keptCarriesM) ?: return Verdict.ACCEPT
        if (median <= 0.0) return Verdict.ACCEPT
        val window = keptCarriesM.max() - keptCarriesM.min()
        return if (window > MAX_WINDOW_FRACTION * median) Verdict.ASK_MORE else Verdict.ACCEPT
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.ClubQualityGateTest"
```
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/ClubQualityGate.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/ClubQualityGateTest.kt
git commit -m "feat(app): bag mapping quality gate (M6)"
```

---

### Task 7: `GapAnalysis` — pure adjacent-club gap flags

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/GapAnalysis.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/GapAnalysisTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden cases for adjacent-club gap flags (spec §6: tight <8, healthy 8–20, wide >20, inverted). */
class GapAnalysisTest {

    @Test
    fun `healthy gap`() {
        val gaps = GapAnalysis.analyze(listOf("D" to 200.0, "3W" to 190.0))
        assertEquals(1, gaps.size)
        assertEquals(GapFlag.HEALTHY, gaps.single().flag)
        assertEquals(10.0, gaps.single().gapM, 1e-9)
        assertEquals("D", gaps.single().longerClub)
        assertEquals("3W", gaps.single().shorterClub)
    }

    @Test
    fun `tight below eight metres`() {
        assertEquals(GapFlag.TIGHT, GapAnalysis.analyze(listOf("7i" to 140.0, "8i" to 133.0)).single().flag)
    }

    @Test
    fun `boundaries - exactly 8 and exactly 20 are healthy`() {
        assertEquals(GapFlag.HEALTHY, GapAnalysis.analyze(listOf("A" to 148.0, "B" to 140.0)).single().flag)
        assertEquals(GapFlag.HEALTHY, GapAnalysis.analyze(listOf("A" to 160.0, "B" to 140.0)).single().flag)
    }

    @Test
    fun `wide over twenty metres`() {
        assertEquals(GapFlag.WIDE, GapAnalysis.analyze(listOf("D" to 201.0, "3W" to 180.0)).single().flag)
    }

    @Test
    fun `inverted - shorter club median reaches the longer club`() {
        val gap = GapAnalysis.analyze(listOf("7i" to 140.0, "8i" to 143.0)).single()
        assertEquals(GapFlag.INVERTED, gap.flag)
        assertEquals(3.0, gap.gapM, 1e-9)
        // Exactly equal medians also read as inverted (spec: shorter ≥ longer).
        assertEquals(GapFlag.INVERTED, GapAnalysis.analyze(listOf("7i" to 140.0, "8i" to 140.0)).single().flag)
    }

    @Test
    fun `single club - no gaps; partial bag - only mapped clubs passed in`() {
        assertTrue(GapAnalysis.analyze(listOf("7i" to 140.0)).isEmpty())
        assertTrue(GapAnalysis.analyze(emptyList()).isEmpty())
        val gaps = GapAnalysis.analyze(listOf("D" to 200.0, "8i" to 133.0, "9i" to 125.0))
        assertEquals(2, gaps.size)
        assertEquals(GapFlag.WIDE, gaps[0].flag)
        assertEquals(GapFlag.TIGHT, gaps[1].flag)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.GapAnalysisTest"
```
Expected: FAIL — `GapAnalysis` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hpsmiles.golfsim.bag

import kotlin.math.abs

/** Gap flag between two adjacent mapped clubs in bag order (spec §6). */
enum class GapFlag { TIGHT, HEALTHY, WIDE, INVERTED }

data class GapEntry(
    val longerClub: String,   // upper row (earlier in bag order)
    val shorterClub: String,  // lower row
    val gapM: Double,
    val flag: GapFlag,
)

/**
 * Adjacent-club gap flags over the medians of the clubs SHOWN, in bag order
 * (spec §6 + §9: gaps only between adjacent mapped clubs — the caller passes
 * exactly the rows with data). Thresholds: tight < 8 m, healthy 8–20 m,
 * wide > 20 m; inverted when the shorter club's median ≥ the longer's
 * (mislabeled club or genuine problem). Single club → no gaps.
 */
object GapAnalysis {
    const val TIGHT_MAX_M = 8.0
    const val WIDE_MIN_M = 20.0

    fun analyze(clubsInBagOrder: List<Pair<String, Double>>): List<GapEntry> {
        if (clubsInBagOrder.size < 2) return emptyList()
        val entries = mutableListOf<GapEntry>()
        for (i in 0 until clubsInBagOrder.size - 1) {
            val (upperName, upperMedian) = clubsInBagOrder[i]
            val (lowerName, lowerMedian) = clubsInBagOrder[i + 1]
            val gap = upperMedian - lowerMedian
            val flag = when {
                gap <= 0.0 -> GapFlag.INVERTED
                gap < TIGHT_MAX_M -> GapFlag.TIGHT
                gap > WIDE_MIN_M -> GapFlag.WIDE
                else -> GapFlag.HEALTHY
            }
            entries.add(GapEntry(upperName, lowerName, abs(gap), flag))
        }
        return entries
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.GapAnalysisTest"
```
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/GapAnalysis.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/GapAnalysisTest.kt
git commit -m "feat(app): adjacent-club gap analysis flags (M6)"
```

---

### Task 8: `BoxPlotGeom` — pure value→pixel mapping

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BoxPlotGeom.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BoxPlotGeomTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Axis mapping for the carry-matrix painter (spec §6). */
class BoxPlotGeomTest {

    @Test
    fun `empty values - no axis`() {
        assertNull(BoxPlotGeom.axis(emptyList(), leftPadPx = 60f, plotWidthPx = 300f))
    }

    @Test
    fun `endpoints map to plot edges - mid to center`() {
        val axis = BoxPlotGeom.axis(listOf(100.0, 200.0), leftPadPx = 60f, plotWidthPx = 300f)!!
        // span 100 → pad 8 each side → [92, 208]
        assertEquals(92.0, axis.minM, 1e-9)
        assertEquals(208.0, axis.maxM, 1e-9)
        assertEquals(60f, BoxPlotGeom.x(92.0, axis), 0.01f)
        assertEquals(360f, BoxPlotGeom.x(208.0, axis), 0.01f)
        assertEquals(210f, BoxPlotGeom.x(150.0, axis), 0.01f)
    }

    @Test
    fun `zero-width data - padded so the plot never degenerates`() {
        val axis = BoxPlotGeom.axis(listOf(140.0), leftPadPx = 0f, plotWidthPx = 300f)!!
        assertNotNull(axis)
        assertEquals(139.0, axis.minM, 1e-9) // max(1 m, 5%) pad
        assertEquals(141.0, axis.maxM, 1e-9)
        assertEquals(150f, BoxPlotGeom.x(140.0, axis), 0.01f)
    }

    @Test
    fun `values outside the axis clamp to plot edges`() {
        val axis = BoxPlotGeom.axis(listOf(100.0, 200.0), leftPadPx = 60f, plotWidthPx = 300f)!!
        assertEquals(60f, BoxPlotGeom.x(0.0, axis), 0.01f)
        assertEquals(360f, BoxPlotGeom.x(999.0, axis), 0.01f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BoxPlotGeomTest"
```
Expected: FAIL — `BoxPlotGeom` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hpsmiles.golfsim.bag

/**
 * Pure value→pixel mapping for the box-plot carry matrix painter (spec §6).
 * Plain Float pixels — no Compose types — so it stays JVM-testable.
 */
object BoxPlotGeom {

    /** Shared carry axis: [minM, maxM] → [leftPadPx, leftPadPx + plotWidthPx]. */
    data class Axis(val minM: Double, val maxM: Double, val leftPadPx: Float, val plotWidthPx: Float)

    /**
     * Auto-ranged axis over EVERY value that will be drawn (kept whiskers
     * AND filtered hollow dots — spec §6), padded 8% of the span each side.
     * Zero-width data (single distinct value) pads by max(1 m, 5% of the
     * value) so the plot never degenerates. Null when there is nothing to draw.
     */
    fun axis(values: List<Double>, leftPadPx: Float, plotWidthPx: Float): Axis? {
        if (values.isEmpty() || plotWidthPx <= 0f) return null
        val lo = values.min()
        val hi = values.max()
        val pad = if (hi - lo <= 0.0) maxOf(1.0, abs(lo) * 0.05) else (hi - lo) * 0.08
        return Axis(lo - pad, hi + pad, leftPadPx, plotWidthPx)
    }

    /** Pixel x for a carry value; values outside the axis clamp to the plot edges. */
    fun x(valueM: Double, axis: Axis): Float {
        val t = ((valueM - axis.minM) / (axis.maxM - axis.minM)).coerceIn(0.0, 1.0)
        return axis.leftPadPx + (t * axis.plotWidthPx).toFloat()
    }

    private fun abs(v: Double): Double = if (v < 0.0) -v else v
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BoxPlotGeomTest"
```
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BoxPlotGeom.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/BoxPlotGeomTest.kt
git commit -m "feat(app): box-plot axis geometry for carry matrix (M6)"
```

---

### Task 9: `BagMappingCollector` — guided collection state holder

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingCollector.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagMappingCollectorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.physics.ShotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guided-collection state holder (spec §4): plan, advance, gate latch, no-read coalesce. */
class BagMappingCollectorTest {

    // Known-good live values (M4c golden fixture discipline).
    private fun ball(ballSpeed: Double = 48.0) = BallData(
        clubHeadSpeed = 33.0, ballSpeed = ballSpeed, launchDirection = -1.0,
        launchAngle = 12.0, spinAxis = -4.0, totalSpin = 8000, unknown1 = 5, unknown2 = 10,
    )

    private fun collector(): BagMappingCollector {
        val c = BagMappingCollector()
        c.simulator = { ShotResult(carryM = 140.0, rolloutM = 10.0, totalM = 150.0, sideM = 0.0, apexM = 27.0, flightTimeSec = 6.0) }
        c.begin(7L, listOf(BagPlanClub("D", ClubType.DRIVER), BagPlanClub("7i", ClubType.IRON), BagPlanClub("PW", ClubType.WEDGE)))
        return c
    }

    @Test
    fun `begin at first club - add returns payload for write-through persistence`() {
        val c = collector()
        assertEquals("D", c.currentClub?.name)
        val shot = c.add(ball())!!
        assertEquals("D", shot.clubName)
        assertEquals(ClubType.DRIVER, shot.clubType)
        assertEquals(140.0, shot.carryM, 1e-9)
        assertEquals(150.0, shot.totalM, 1e-9)
        assertEquals(48.0, shot.ballData.ballSpeed, 1e-9)
        assertTrue(shot.timestampMs > 0)
    }

    @Test
    fun `advance walks the plan - done after the last club`() {
        val c = collector()
        c.advance()
        assertEquals("7i", c.currentClub?.name)
        c.advance()
        assertEquals("PW", c.currentClub?.name)
        assertFalse(c.done)
        c.advance()
        assertTrue(c.done)
        assertNull(c.currentClub)
        assertNull(c.add(ball())) // nothing to collect when done
    }

    @Test
    fun `guard violation - bad decode is never stored, just ignored`() {
        val c = collector()
        assertNull(c.add(ball(ballSpeed = -5.0))) // LaunchConditions throws → null
    }

    @Test
    fun `no read coalesces the events-plus-measurement pair`() {
        val c = collector()
        assertTrue(c.markNoRead(1000))
        assertFalse(c.markNoRead(1200)) // within 500 ms window
        assertTrue(c.markNoRead(1600))
        assertEquals(2, c.noReadCount.intValue)
    }

    @Test
    fun `hit more latches per club - accept or advance clears it`() {
        val c = collector()
        assertEquals("D", c.currentClub?.name)
        c.hitMore()
        assertEquals("D", c.moreGrantedFor.value)
        c.advance()
        assertNull(c.moreGrantedFor.value)
        c.hitMore()
        c.acceptResult()
        assertTrue(c.currentIndex.intValue >= 1) // accept = advance
        assertNull(c.moreGrantedFor.value)
    }

    @Test
    fun `resume coerces the club index into the plan`() {
        val c = BagMappingCollector()
        val plan = listOf(BagPlanClub("D", ClubType.DRIVER), BagPlanClub("7i", ClubType.IRON))
        c.beginAt(9L, plan, clubIndex = 99)
        assertEquals("7i", c.currentClub?.name) // clamped to last club
        c.reset()
        assertEquals(-1L, c.sessionId)
        assertEquals(0, c.noReadCount.intValue)
        assertNull(c.currentClub)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagMappingCollectorTest"
```
Expected: FAIL — `BagMappingCollector` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.Surface
import com.hpsmiles.golfsim.core.physics.UniformSurface

/** One club in the guided plan (snapshot — the clubs-table id is irrelevant here). */
data class BagPlanClub(val name: String, val type: ClubType)

/** A computed mapping shot handed to AppRoot for write-through persistence. */
data class BagMappedShot(
    val clubName: String,
    val clubType: ClubType,
    val timestampMs: Long,
    val ballData: BallData,
    val carryM: Double,
    val totalM: Double,
)

/**
 * Guided collection state holder (games pattern: plain Compose state,
 * injectable clock — spec §4). Holds ONLY session-ephemeral guidance state:
 * the plan snapshot, current club, gate-prompt latch, and the in-memory
 * "no read" counter. The shots themselves are Room truth — the UI renders
 * from repository flows and every accepted shot is write-through persisted
 * by AppRoot, so kill + resume reconstructs everything (spec §4/§9).
 */
class BagMappingCollector {

    val currentIndex = mutableIntStateOf(0)
    /** ASK_MORE prompt currently visible for the current club (spec §5). */
    val gatePrompt = mutableStateOf(false)
    /** Club the player answered HIT MORE for — suppresses re-prompting until they advance. */
    val moreGrantedFor = mutableStateOf<String?>(null)
    val noReadCount = mutableIntStateOf(0)
    /** Bumps on every state change so keyed UI effects re-run deterministically. */
    val tick = mutableIntStateOf(0)

    /** Injectable clock (ms). AppRoot can leave the default. */
    var clockMs: () -> Long = { System.currentTimeMillis() }

    /**
     * Injectable simulation seam. Mapping renders carry (surface-independent)
     * and total; the default simulates onto plain fairway. Settings surfaces
     * are deliberately NOT wired: carry is the gapping metric and must not
     * move with the range's green/turf choices (spec §6).
     */
    var simulator: (LaunchConditions) -> ShotResult = { launch ->
        BallFlightEngine.simulate(launch, Environment(), UniformSurface(Surface.FAIRWAY_NORMAL))
    }

    var sessionId: Long = -1L
        private set
    var plan: List<BagPlanClub> = emptyList()
        private set

    val currentClub: BagPlanClub? get() = plan.getOrNull(currentIndex.intValue)

    /** True once the index runs past the last club — AppRoot then completes the session. */
    val done: Boolean get() = plan.isNotEmpty() && currentIndex.intValue >= plan.size

    fun begin(sessionId: Long, plan: List<BagPlanClub>) = beginAt(sessionId, plan, 0)

    /** Resume: AppRoot derives the club index from the persisted shots. */
    fun beginAt(sessionId: Long, plan: List<BagPlanClub>, clubIndex: Int) {
        this.sessionId = sessionId
        this.plan = plan
        currentIndex.intValue = clubIndex.coerceIn(0, maxOf(0, plan.size - 1))
        gatePrompt.value = false
        moreGrantedFor.value = null
        noReadCount.intValue = 0
        lastNoReadMs = 0L
        tick.intValue++
    }

    /**
     * One decoded measurement → payload for write-through persistence.
     * Null on a LaunchConditions guard violation (bad decode — never stored,
     * M4b discipline) or when there is no club to collect for.
     */
    fun add(ballData: BallData): BagMappedShot? {
        val club = currentClub ?: return null
        val launch = try {
            LaunchConditions(
                ballSpeedMps = ballData.ballSpeed,
                launchAngleDeg = ballData.launchAngle,
                spinRpm = ballData.totalSpin,
                spinAxisDeg = ballData.spinAxis,
                launchDirDeg = ballData.launchDirection,
            )
        } catch (_: IllegalArgumentException) {
            return null
        }
        val result = simulator(launch)
        tick.intValue++
        return BagMappedShot(club.name, club.type, clockMs(), ballData, result.carryM, result.totalM)
    }

    /**
     * Coalesced "no read" for the guided pill (same 500 ms window discipline
     * as RangeSession.markMisread — one real mishit emits BOTH the EVENTS
     * alert and the all-zero sentinel). In-memory only; resets on resume,
     * like the range pill (RangeSession.restore). Returns true when counted.
     */
    fun markNoRead(atMs: Long = clockMs()): Boolean {
        if (atMs - lastNoReadMs < NO_READ_COALESCE_MS) return false
        lastNoReadMs = atMs
        noReadCount.intValue++
        return true
    }

    /** Player chose HIT MORE: hide the prompt, latch the club. */
    fun hitMore() {
        gatePrompt.value = false
        moreGrantedFor.value = currentClub?.name
        tick.intValue++
    }

    /** Player accepted the result at the prompt (or the UI auto-advances on ACCEPT). */
    fun acceptResult() {
        gatePrompt.value = false
        advance()
    }

    /** Moves to the next club; clears prompt + latch. Past the last club → [done]. */
    fun advance() {
        gatePrompt.value = false
        moreGrantedFor.value = null
        currentIndex.intValue++
        tick.intValue++
    }

    /** Clears all session-ephemeral state (after completion or abandonment). */
    fun reset() {
        sessionId = -1L
        plan = emptyList()
        currentIndex.intValue = 0
        gatePrompt.value = false
        moreGrantedFor.value = null
        noReadCount.intValue = 0
        lastNoReadMs = 0L
        tick.intValue++
    }

    private var lastNoReadMs = 0L

    companion object {
        const val NO_READ_COALESCE_MS = 500L
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagMappingCollectorTest"
```
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingCollector.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagMappingCollectorTest.kt
git commit -m "feat(app): bag mapping guided collection state holder (M6)"
```

### Task 10: Formats + INTRO view

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingFormats.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingIntro.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagMappingFormatsTest.kt`

- [ ] **Step 1: Write the failing formats test**

```kotlin
package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Test

class BagMappingFormatsTest {

    @Test
    fun `carry rounds to whole metres`() {
        assertEquals("140 m", BagMappingFormats.carry(140.4))
        assertEquals("141 m", BagMappingFormats.carry(140.6))
    }

    @Test
    fun `smash factor two decimals - dash when club speed missing`() {
        assertEquals("1.45", BagMappingFormats.smash(33.0, 47.9))
        assertEquals("—", BagMappingFormats.smash(0.0, 47.9))
    }

    @Test
    fun `date time format`() {
        assertEquals("2026-10-06 14:30", BagMappingFormats.dateTime(1780770600000))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagMappingFormatsTest"
```
Expected: FAIL — `BagMappingFormats` unresolved.

- [ ] **Step 3: Implement formats + INTRO**

`BagMappingFormats.kt`:

```kotlin
package com.hpsmiles.golfsim.bag

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Display formatting for bag mapping. Metres with the app-wide "%.0f m"
 * convention (HistoryFormats style); UnitsPreference wiring is out of
 * scope (spec §2 decision).
 */
object BagMappingFormats {
    fun carry(m: Double): String = String.format(Locale.US, "%.0f m", m)

    /** Smash factor = ball speed / club head speed. */
    fun smash(clubMps: Double, ballMps: Double): String =
        if (clubMps <= 0.0) "—" else String.format(Locale.US, "%.2f", ballMps / clubMps)

    fun dateTime(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
}
```

`BagMappingIntro.kt`:

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * INTRO state (spec §3): bag-up-to-date reminder, bag summary, START TEST.
 * Shown when no result and no in-progress session exist. Empty/putter-only
 * bag disables START and points at Settings (spec §9).
 */
@Composable
fun BagMappingIntro(
    eligibleClubs: List<ClubRecord>,
    onOpenSettings: () -> Unit,
    onStartTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
    ) {
        Text("BAG MAPPING", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
        Text(
            "Hit 5 clean shots with every club in your bag to build your true carry gaps.",
            style = GolfTypography.Body,
            color = GolfColors.TextSecondary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GolfColors.Amber, RoundedCornerShape(GolfSpacing.CornerCard))
                .padding(GolfSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
        ) {
            Text("BEFORE YOU START", style = GolfTypography.MetricLabel, color = GolfColors.Amber)
            Text(
                "Check your bag is up to date — add, rename or remove clubs in SETTINGS first. " +
                    "Putters and TEST clubs are skipped.",
                style = GolfTypography.Body,
                color = GolfColors.TextPrimary,
            )
            OutlinedButton(onClick = onOpenSettings) { Text("OPEN SETTINGS") }
        }
        Text(
            if (eligibleClubs.isEmpty()) "No clubs to map." else "${eligibleClubs.size} clubs in the bag",
            style = GolfTypography.Body,
            color = GolfColors.TextSecondary,
        )
        if (eligibleClubs.isEmpty()) {
            Text(
                "Add clubs in SETTINGS to start a test.",
                style = GolfTypography.Body,
                color = GolfColors.TextSecondary,
            )
        }
        Button(
            onClick = onStartTest,
            enabled = eligibleClubs.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = GolfColors.Teal),
        ) {
            Text("START TEST", style = GolfTypography.MetricLabel)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagMappingFormatsTest"
```
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingFormats.kt app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingIntro.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagMappingFormatsTest.kt
git commit -m "feat(app): bag mapping intro screen and formats (M6)"
```

---

### Task 11: `CarryMatrixCanvas` — box-plot painter

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/CarryMatrixCanvas.kt`

- [ ] **Step 1: Implement the painter**

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.bag.BagMappingStats

/** One plotted club: kept and filtered carries (m), bag order handled by the caller. */
data class CarryMatrixRow(
    val clubName: String,
    val kept: List<Double>,
    val filtered: List<Double>,
)

/**
 * Box-plot carry matrix painter (spec §6). One shared carry axis; per row
 * over KEPT shots: whisker = min–max, box = Q1–Q3, median tick = volt teal,
 * mean dot = light; filtered shots = hollow amber dots. Pure geometry in
 * [BoxPlotGeom], distributions in [BagMappingStats] — both JVM-tested.
 */
@Composable
fun CarryMatrixCanvas(
    rows: List<CarryMatrixRow>,
    modifier: Modifier = Modifier,
) {
    val allValues = rows.flatMap { it.kept + it.filtered }
    val axis = BoxPlotGeom.axis(allValues, leftPadPx = 0f, plotWidthPx = 1f) // placeholder axis range probe
    // Axis is computed per-canvas-size inside DrawScope (needs measured width),
    // so draw() re-derives it; the probe only short-circuits empty input.
    if (axis == null) return
    Canvas(modifier = modifier) {
        val leftPad = 0f
        val plotWidth = size.width
        val rowAxis = BoxPlotGeom.axis(allValues, leftPad, plotWidth) ?: return@Canvas
        val rowHeight = size.height / rows.size
        rows.forEachIndexed { index, row ->
            val top = index * rowHeight
            val centerY = top + rowHeight / 2f
            val dist = BagMappingStats.distribution(row.kept)
            if (dist != null) {
                // Whisker: min–max kept
                drawLine(
                    color = PlotLines,
                    start = Offset(BoxPlotGeom.x(dist.min, rowAxis), centerY),
                    end = Offset(BoxPlotGeom.x(dist.max, rowAxis), centerY),
                    strokeWidth = WhiskerWidth.toPx(),
                    cap = StrokeCap.Round,
                )
                // Box: Q1–Q3
                val left = BoxPlotGeom.x(dist.q1, rowAxis)
                val right = BoxPlotGeom.x(dist.q3, rowAxis)
                drawRoundRect(
                    color = PlotBox,
                    topLeft = Offset(left, centerY - BoxHeight.toPx() / 2f),
                    size = Size(maxOf(2f, right - left), BoxHeight.toPx()),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(width = 2f),
                )
                // Median tick (volt)
                val medX = BoxPlotGeom.x(dist.median, rowAxis)
                drawLine(
                    color = PlotMedian,
                    start = Offset(medX, centerY - BoxHeight.toPx() / 2f - 4f),
                    end = Offset(medX, centerY + BoxHeight.toPx() / 2f + 4f),
                    strokeWidth = MedianWidth.toPx(),
                    cap = StrokeCap.Round,
                )
                // Mean dot
                drawCircle(
                    color = PlotMean,
                    radius = MeanRadius.toPx(),
                    center = Offset(BoxPlotGeom.x(dist.mean, rowAxis), centerY),
                )
            }
            // Filtered shots: hollow amber dots
            row.filtered.forEach { carry ->
                drawCircle(
                    color = PlotFiltered,
                    radius = FilteredRadius.toPx(),
                    center = Offset(BoxPlotGeom.x(carry, rowAxis), centerY),
                    style = Stroke(width = 2f),
                )
            }
        }
    }
}

private val PlotLines = Color(0xFF28303A)      // GolfColors.Line — kept here to avoid a draw-scope import cycle
private val PlotBox = Color(0xFF3FA7A0)        // GolfColors.Teal
private val PlotMedian = Color(0xFFF2A93B)     // GolfColors.Amber
private val PlotMean = Color(0xFFE7EBEE)       // GolfColors.TextPrimary
private val PlotFiltered = Color(0xFFF2A93B)   // GolfColors.Amber
private val WhiskerWidth = 2.dp
private val MedianWidth = 3.dp
private val BoxHeight = 18.dp
private val MeanRadius = 4.dp
private val FilteredRadius = 5.dp
```

Wait — the placeholder probe above is clumsy and wrong (axis with width 1). Replace with a clean guard: compute nothing outside DrawScope; simply early-return on empty rows. Final composable body:

```kotlin
@Composable
fun CarryMatrixCanvas(
    rows: List<CarryMatrixRow>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    Canvas(modifier = modifier) {
        val allValues = rows.flatMap { it.kept + it.filtered }
        val rowAxis = BoxPlotGeom.axis(allValues, leftPadPx = 0f, plotWidthPx = size.width) ?: return@Canvas
        val rowHeight = size.height / rows.size
        rows.forEachIndexed { index, row ->
            drawRow(row, rowAxis, top = index * rowHeight, centerY = index * rowHeight + rowHeight / 2f)
        }
    }
}

private fun DrawScope.drawRow(row: CarryMatrixRow, axis: BoxPlotGeom.Axis, top: Float, centerY: Float) {
    val dist = BagMappingStats.distribution(row.kept)
    if (dist != null) {
        drawLine(
            color = PlotLines,
            start = Offset(BoxPlotGeom.x(dist.min, axis), centerY),
            end = Offset(BoxPlotGeom.x(dist.max, axis), centerY),
            strokeWidth = WhiskerWidth.toPx(),
            cap = StrokeCap.Round,
        )
        val left = BoxPlotGeom.x(dist.q1, axis)
        val right = BoxPlotGeom.x(dist.q3, axis)
        drawRoundRect(
            color = PlotBox,
            topLeft = Offset(left, centerY - BoxHeight.toPx() / 2f),
            size = Size(maxOf(2f, right - left), BoxHeight.toPx()),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 2f),
        )
        val medX = BoxPlotGeom.x(dist.median, axis)
        drawLine(
            color = PlotMedian,
            start = Offset(medX, centerY - BoxHeight.toPx() / 2f - 4f),
            end = Offset(medX, centerY + BoxHeight.toPx() / 2f + 4f),
            strokeWidth = MedianWidth.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = PlotMean,
            radius = MeanRadius.toPx(),
            center = Offset(BoxPlotGeom.x(dist.mean, axis), centerY),
        )
    }
    row.filtered.forEach { carry ->
        drawCircle(
            color = PlotFiltered,
            radius = FilteredRadius.toPx(),
            center = Offset(BoxPlotGeom.x(carry, axis), centerY),
            style = Stroke(width = 2f),
        )
    }
}
```

(Use the second version — one `CarryMatrixCanvas` composable + one private `drawRow`; keep the color/dp constants exactly as listed above. If a tint feels off at review time, swap the constant, not the structure.)

- [ ] **Step 2: Compile to verify**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/CarryMatrixCanvas.kt
git commit -m "feat(app): box-plot carry matrix painter (M6)"
```

---

### Task 12: RESULT view — matrix + stats + gap flags + drill-down

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingResult.kt`

- [ ] **Step 1: Implement the RESULT view**

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RESULT state (spec §6): box-plot carry matrix in bag order, per-club stats
 * column, adjacent gap flags, RETEST + HISTORY, "test in progress" banner
 * while a retest runs, and per-club drill-down (all stored shots, kept vs
 * filtered). Read-only — no manual exclusion in v1.
 */
@Composable
fun BagMappingResult(
    session: BagMappingSessionEntity,
    shots: List<BagMappingShotEntity>,
    isStaleResult: Boolean,
    onRetest: () -> Unit,
    onResume: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var drillClub by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        Text("BAG MAPPING", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
        Text(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(session.completedAtMs ?: session.startedAtMs)),
            style = GolfTypography.BodySmall,
            color = GolfColors.TextSecondary,
        )
        if (isStaleResult) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GolfColors.Amber, RoundedCornerShape(GolfSpacing.CornerCard))
                    .padding(GolfSpacing.Md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TEST IN PROGRESS", style = GolfTypography.MetricLabel, color = GolfColors.Amber)
                Button(onClick = onResume, colors = ButtonDefaults.buttonColors(containerColor = GolfColors.Teal)) {
                    Text("RESUME")
                }
            }
        }
        CarryMatrixSection(session, shots, onDrillDown = { drillClub = it })
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md)) {
            if (!isStaleResult) {
                Button(onClick = onRetest, colors = ButtonDefaults.buttonColors(containerColor = GolfColors.Teal)) {
                    Text("RETEST")
                }
            }
            OutlinedButton(onClick = onOpenHistory) { Text("HISTORY") }
        }
    }
    drillClub?.let { clubName ->
        DrillDownPanel(
            clubName = clubName,
            shots = shots.filter { it.clubName == clubName },
            onDismiss = { drillClub = null },
        )
    }
}

@Composable
private fun CarryMatrixSection(
    session: BagMappingSessionEntity,
    shots: List<BagMappingShotEntity>,
    onDrillDown: (String) -> Unit,
) {
    // Bag order comes from the session snapshot (spec §4); clubs without
    // shots render as "no data" rows (spec §9).
    val rows = session.clubSnapshot().map { (name, type) ->
        val clubShots = shots.filter { it.clubName == name }
        Triple(name, type.label, clubShots)
    }
    val mappedMedians = rows.mapNotNull { (name, _, clubShots) ->
        val kept = clubShots.filter { !it.filtered }.map { it.carryM }
        val dist = BagMappingStats.distribution(kept) ?: return@mapNotNull null
        name to dist.median
    }
    val gaps = GapAnalysis.analyze(mappedMedians)
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
    ) {
        items(rows, key = { it.first }) { (name, typeLabel, clubShots) ->
            ClubRow(name, typeLabel, clubShots, onDrillDown)
        }
        if (gaps.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    gaps.forEach { gap ->
                        Text(
                            text = "${gap.longerClub} → ${gap.shorterClub}: ${BagMappingFormats.carry(gap.gapM)} · ${gap.flag.name}",
                            style = GolfTypography.BodySmall,
                            color = when (gap.flag) {
                                GapFlag.TIGHT -> GolfColors.TextSecondary
                                GapFlag.HEALTHY -> GolfColors.Teal
                                GapFlag.WIDE -> GolfColors.Amber
                                GapFlag.INVERTED -> GolfColors.AlertRed
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClubRow(
    clubName: String,
    typeLabel: String,
    clubShots: List<BagMappingShotEntity>,
    onDrillDown: (String) -> Unit,
) {
    val kept = clubShots.filter { !it.filtered }.map { it.carryM }
    val filtered = clubShots.filter { it.filtered }.map { it.carryM }
    val dist = BagMappingStats.distribution(kept)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
            .clickable { onDrillDown(clubName) }
            .padding(GolfSpacing.Sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(72.dp)) {
                Text(clubName, style = GolfTypography.MetricValue, color = GolfColors.TextPrimary)
                Text(typeLabel, style = GolfTypography.BodySmall, color = GolfColors.TextSecondary)
            }
            if (dist == null) {
                Text("no data", style = GolfTypography.Body, color = GolfColors.TextSecondary)
            } else {
                CarryMatrixCanvas(
                    rows = listOf(CarryMatrixRow(clubName, kept, filtered)),
                    modifier = Modifier.weight(1f).height(28.dp),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${BagMappingFormats.carry(dist.median)} med",
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.TextPrimary,
                    )
                    Text(
                        "σ ${BagMappingFormats.carry(dist.sigma)} · ${dist.count} kept" +
                            if (filtered.isNotEmpty()) " (+${filtered.size} filtered)" else "",
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrillDownPanel(
    clubName: String,
    shots: List<BagMappingShotEntity>,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GolfColors.Base)
            .padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("$clubName — ALL SHOTS", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
            TextButton(onClick = onDismiss) { Text("BACK") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
            items(shots, key = { it.id }) { shot ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(shot.timestampMs)),
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextSecondary,
                    )
                    Text(BagMappingFormats.carry(shot.carryM), style = GolfTypography.MetricLabel, color = GolfColors.TextPrimary)
                    Text(
                        "smash ${BagMappingFormats.smash(shot.clubHeadSpeedMps, shot.ballSpeedMps)}",
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextSecondary,
                    )
                    if (shot.filtered) {
                        Text(
                            "FILTERED (${shot.filterReason ?: "DUFF"})",
                            style = GolfTypography.BodySmall,
                            color = GolfColors.Amber,
                        )
                    } else {
                        Text("KEPT", style = GolfTypography.BodySmall, color = GolfColors.Teal)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compile to verify**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingResult.kt
git commit -m "feat(app): bag mapping result screen with matrix, gap flags, drill-down (M6)"
```

### Task 13: COLLECTING view — guided flow with quality gate

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingCollecting.kt`

- [ ] **Step 1: Implement the COLLECTING view**

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * COLLECTING state (spec §4): current-club banner, 5 progress dots, last-shot
 * carry readout, "no read" pill, next-up preview, skip/END TEST controls, and
 * the quality-gate prompt. Shots come from the repository flow (Room truth —
 * AppRoot write-through persists each shot); this view only derives state and
 * drives the collector. Leaving the tab is safe: progress persists (spec §2).
 */
@Composable
fun BagMappingCollecting(
    collector: BagMappingCollector,
    activeShots: List<BagMappingShotEntity>,
    onCompleteSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val club = collector.currentClub
    // Quality-gate derivation (spec §5): runs whenever the kept set changes.
    // ACCEPT auto-advances (this also covers resume with >= 5 kept and the
    // 15 cap); ASK_MORE shows the prompt unless the player already chose
    // HIT MORE for this club (per-club latch in the collector).
    val clubShots = activeShots.filter { it.clubName == club?.name }
    val keptShots = clubShots.filter { !it.filtered }
    LaunchedEffect(club?.name, keptShots.size, clubShots.size, collector.moreGrantedFor.value, collector.tick.intValue) {
        if (club == null) return@LaunchedEffect
        if (keptShots.size >= ClubQualityGate.TARGET_KEPT) {
            when (ClubQualityGate.evaluate(keptShots.map { it.carryM }, clubShots.size - keptShots.size)) {
                ClubQualityGate.Verdict.ACCEPT -> collector.advance()
                ClubQualityGate.Verdict.ASK_MORE ->
                    if (collector.moreGrantedFor.value != club.name) collector.gatePrompt.value = true
            }
        }
    }
    Column(
        modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
    ) {
        Text("BAG MAPPING", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
        if (club == null) return@Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GolfColors.Teal, RoundedCornerShape(GolfSpacing.CornerCard))
                .padding(GolfSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("NOW: ${club.name}", style = GolfTypography.Hero, color = GolfColors.TextPrimary)
                Text(
                    "club ${collector.currentIndex.intValue + 1} of ${collector.plan.size}",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                repeat(ClubQualityGate.TARGET_KEPT) { i ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (i < keptShots.size) GolfColors.Teal else GolfColors.Line,
                                CircleShape,
                            ),
                    )
                }
                Text(
                    "${keptShots.size}/${ClubQualityGate.TARGET_KEPT} valid" +
                        if (clubShots.size > keptShots.size) " · ${clubShots.size - keptShots.size} filtered" else "",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextSecondary,
                )
            }
            val lastKept = keptShots.lastOrNull()
            Text(
                if (lastKept != null) "Last: ${BagMappingFormats.carry(lastKept.carryM)}" else "Hit a shot to begin",
                style = GolfTypography.MetricValue,
                color = GolfColors.TextPrimary,
            )
            if (collector.noReadCount.intValue > 0) {
                Text(
                    "no read (${collector.noReadCount.intValue})",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.Amber,
                )
            }
        }
        val nextUp = collector.plan.getOrNull(collector.currentIndex.intValue + 1)
        Text(
            if (nextUp != null) "Next up: ${nextUp.name}" else "Last club in the bag",
            style = GolfTypography.Body,
            color = GolfColors.TextSecondary,
        )
        if (collector.gatePrompt.value) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GolfColors.Amber, RoundedCornerShape(GolfSpacing.CornerCard))
                    .padding(GolfSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
            ) {
                Text("This club looks inconsistent", style = GolfTypography.MetricLabel, color = GolfColors.Amber)
                Text(
                    "Filtered shots or a wide carry window — more shots give a truer picture (up to ${ClubQualityGate.MAX_KEPT}).",
                    style = GolfTypography.Body,
                    color = GolfColors.TextPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md)) {
                    Button(onClick = { collector.hitMore() }, colors = ButtonDefaults.buttonColors(containerColor = GolfColors.Teal)) {
                        Text("HIT MORE")
                    }
                    OutlinedButton(onClick = { collector.acceptResult() }) { Text("ACCEPT RESULT") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md)) {
            OutlinedButton(onClick = { collector.advance() }) { Text("SKIP CLUB") }
            TextButton(onClick = onCompleteSession) { Text("END TEST") }
        }
    }
}
```

- [ ] **Step 2: Compile to verify**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingCollecting.kt
git commit -m "feat(app): guided bag mapping collection view with quality gate (M6)"
```

---

### Task 14: HISTORY view + per-session shot counts

**Files:**
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/BagMappingShotDao.kt` (add count query)
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt` (add counts accessor)
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingHistory.kt`

- [ ] **Step 1: Add the per-session count query**

In `BagMappingShotDao.kt` add (with a small POJO above the interface):

```kotlin
/** Per-session shot counts for history rows (spec §3). */
data class BagMappingSessionShotCount(val sessionId: Long, val count: Int)
```

and inside the DAO interface:

```kotlin
    @Query("SELECT sessionId, COUNT(*) as count FROM bag_mapping_shots GROUP BY sessionId")
    suspend fun countsBySession(): List<BagMappingSessionShotCount>
```

In `SessionRepository.kt` (bag mapping section) add:

```kotlin
    /** Mapping shots per session id, for history rows. Empty map on failure (never throws). */
    suspend fun bagMappingShotCounts(): Map<Long, Int> = try {
        bagMappingShotDao.countsBySession().associate { it.sessionId to it.count }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Log.e(TAG, "bagMappingShotCounts failed", t)
        emptyMap()
    }
```

- [ ] **Step 2: Compile + run the module tests**

```powershell
.\gradlew.bat :core:data:test
```
Expected: PASS (DAO change is additive).

- [ ] **Step 3: Implement the HISTORY view**

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * HISTORY state (spec §3): every completed mapping result, newest first
 * (repo order), date · clubs covered · shot count; tap → read-only result.
 * No retention limit (spec §2).
 */
@Composable
fun BagMappingHistory(
    history: List<BagMappingSessionEntity>,
    shotCounts: Map<Long, Int>,
    onOpen: (BagMappingSessionEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("BAG MAPPING — HISTORY", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
            OutlinedButton(onClick = onBack) { Text("BACK") }
        }
        if (history.isEmpty()) {
            Text("No completed tests yet.", style = GolfTypography.Body, color = GolfColors.TextSecondary)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            items(history, key = { it.id }) { session ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
                        .clickable { onOpen(session) }
                        .padding(GolfSpacing.Md),
                ) {
                    Text(
                        BagMappingFormats.dateTime(session.startedAtMs),
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.TextPrimary,
                    )
                    Text(
                        "${session.clubSnapshot().size} clubs · ${shotCounts[session.id] ?: 0} shots",
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextSecondary,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Compile to verify**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/BagMappingShotDao.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingHistory.kt
git commit -m "feat(app): bag mapping history view with per-session shot counts (M6)"
```

---

### Task 15: Screen shell — state selection wiring all views

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingScreen.kt`

- [ ] **Step 1: Implement the shell**

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType

/**
 * BAG tab screen (spec §3): in-screen when-enum over Room-derived state —
 * no SharedPreferences, no in-memory-only state. State selection:
 * in-progress + no result → COLLECTING; in-progress + result → RESULT with
 * resume banner; result only → RESULT; neither → INTRO; HISTORY reachable
 * from RESULT. Reports whether the COLLECTING view is up so AppRoot can
 * route shots (same seam as the games' onActiveGameChange).
 */
private enum class BagView { AUTO, INTRO, COLLECTING, RESULT, HISTORY }

@Composable
fun BagMappingScreen(
    clubs: List<ClubRecord>,
    activeSession: BagMappingSessionEntity?,
    latestCompleted: BagMappingSessionEntity?,
    history: List<BagMappingSessionEntity>,
    shotCounts: Map<Long, Int>,
    activeShots: List<BagMappingShotEntity>,
    viewedShots: List<BagMappingShotEntity>,
    collector: BagMappingCollector,
    onViewedSessionChange: (Long?) -> Unit,
    onCollectingChange: (Boolean) -> Unit,
    onStartTest: () -> Unit,
    onOpenSettings: () -> Unit,
    onCompleteSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by remember { mutableStateOf(BagView.AUTO) }
    var viewingHistory by remember { mutableStateOf<BagMappingSessionEntity?>(null) }

    // Spec §3 selection table. AUTO defers entirely to Room state.
    val autoView = when {
        activeSession != null && latestCompleted == null -> BagView.COLLECTING
        latestCompleted != null -> BagView.RESULT
        else -> BagView.INTRO
    }
    val resolvedView = when (view) {
        BagView.AUTO -> autoView
        BagView.INTRO -> if (activeSession == null) BagView.INTRO else autoView
        BagView.COLLECTING -> if (activeSession != null) BagView.COLLECTING else autoView
        BagView.RESULT -> if (latestCompleted != null || viewingHistory != null) BagView.RESULT else autoView
        BagView.HISTORY -> BagView.HISTORY
    }

    // Shot routing seam (games pattern): report whether collection is live.
    LaunchedEffect(resolvedView) {
        onCollectingChange(resolvedView == BagView.COLLECTING)
    }

    // The session whose shots are on screen: active while collecting,
    // completed on the result, historical in read-only. AppRoot collects the
    // flow for exactly this id (historySelectedId pattern).
    val displaySession = when (resolvedView) {
        BagView.COLLECTING -> activeSession
        BagView.RESULT -> viewingHistory ?: latestCompleted
        else -> null
    }
    LaunchedEffect(displaySession?.id) {
        onViewedSessionChange(displaySession?.id)
    }

    when (resolvedView) {
        BagView.INTRO -> BagMappingIntro(
            eligibleClubs = clubs.filter { !it.isTemp && it.type != ClubType.PUTTER },
            onOpenSettings = onOpenSettings,
            onStartTest = onStartTest,
            modifier = modifier,
        )
        BagView.COLLECTING -> BagMappingCollecting(
            collector = collector,
            activeShots = activeShots,
            onCompleteSession = onCompleteSession,
            modifier = modifier,
        )
        BagView.RESULT -> {
            val session = viewingHistory ?: latestCompleted
            if (session != null) {
                BagMappingResult(
                    session = session,
                    shots = viewedShots,
                    isStaleResult = activeSession != null && viewingHistory == null,
                    onRetest = onStartTest,
                    onResume = { view = BagView.COLLECTING },
                    onOpenHistory = { view = BagView.HISTORY },
                    modifier = modifier,
                )
            }
        }
        BagView.HISTORY -> BagMappingHistory(
            history = history,
            shotCounts = shotCounts,
            onOpen = { viewingHistory = it },
            onBack = {
                viewingHistory = null
                view = BagView.AUTO
            },
            modifier = modifier,
        )
        BagView.AUTO -> Unit // unreachable: AUTO resolves above
    }
}
```

- [ ] **Step 2: Compile to verify**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL (screen not yet referenced — AppRoot wires it in Task 16).

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingScreen.kt
git commit -m "feat(app): bag mapping screen state selection shell (M6)"
```

---

### Task 16: AppRoot wiring — BAG tab, flows, shot routing, lifecycle

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` (enum `:82`, state `:104-107`, shot routing `:247-263`, misread `:271-277`, flows near `:492`, NavRail `:537-541`, `when (tab)` `:617-696`)

- [ ] **Step 1: Add the tab + hoisted state**

At `AppRoot.kt:82` change the enum:

```kotlin
private enum class RangeTab { RANGE, GAMES, BAG, SETTINGS, HISTORY }
```

Near `:107` (after `var activeGame by remember { mutableStateOf(GameMode.NONE) }`) add:

```kotlin
    // M6: bag mapping. collector is session-ephemeral guidance state; the
    // shots are Room truth (write-through persisted, resume-after-kill).
    val bagCollector = remember { BagMappingCollector() }
    var bagCollecting by remember { mutableStateOf(false) }
    var bagViewedSessionId by remember { mutableStateOf<Long?>(null) }
```

Add imports: `com.hpsmiles.golfsim.bag.BagMappingCollector`, `com.hpsmiles.golfsim.bag.BagMappingScreen`, `com.hpsmiles.golfsim.bag.BagPlanClub`, `com.hpsmiles.golfsim.bag.ClubQualityGate`.

- [ ] **Step 2: Add the lifecycle helpers** — AFTER the Step 3 flow captures (Kotlin local functions may only reference locals declared above them; `completeBagTest` reads `bagActive`). Paste immediately below the Step 3 block:

```kotlin
    /**
     * M6: START TEST (also RETEST). Creates the IN_PROGRESS session with a
     * bag snapshot and binds the collector. Idempotent in the repo: if a
     * test is already running its session id comes back untouched (the UI
     * only offers START when none does — spec §4).
     */
    fun startBagTest() {
        scope.launch {
            val eligible = clubRecords.filter { !it.isTemp && it.type != ClubType.PUTTER }
            if (eligible.isEmpty()) return@launch
            val id = sessionRepository.startBagMappingSession(eligible, System.currentTimeMillis()) ?: return@launch
            bagCollector.begin(id, eligible.map { BagPlanClub(it.name, it.type) })
        }
    }

    /** M6: END TEST / final-club completion → the result becomes the active result. */
    fun completeBagTest() {
        val active = bagActive ?: return
        scope.launch {
            sessionRepository.completeBagMappingSession(active.id, System.currentTimeMillis())
            bagCollector.reset()
        }
    }
```

(`bagActive` is the flow capture from Step 3 — declare before use or Kotlin will still resolve it inside the function body since both live in the same composable scope; keep Step 3's declarations ABOVE these helpers to be safe.)

- [ ] **Step 3: Add the gated Room captures** (near the other captures, ~`:492`)

```kotlin
    // M6: bag mapping state — getter-flows captured gated like the others
    // (they re-derive from the current db, so they survive corrupt-file
    // recovery).
    val bagActive by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.bagMappingActive }, flowOf(null))
    }.collectAsState(initial = null)
    val bagLatest by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.bagMappingLatestCompleted }, flowOf(null))
    }.collectAsState(initial = null)
    val bagHistory by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.bagMappingHistory }, flowOf(emptyList()))
    }.collectAsState(initial = emptyList())
    val bagActiveShots by remember(sessionRepository, repoReady, bagActive?.id) {
        val id = bagActive?.id
        if (id == null) flowOf(emptyList())
        else gatedFlow(repoReady, { sessionRepository.observeBagMappingShots(id) }, flowOf(emptyList()))
    }.collectAsState(initial = emptyList())
    val bagViewedShots by remember(bagViewedSessionId) {
        bagViewedSessionId?.let { sessionRepository.observeBagMappingShots(it) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    var bagShotCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    LaunchedEffect(bagHistory) {
        bagShotCounts = sessionRepository.bagMappingShotCounts()
    }

    // M6: auto-bind the collector to an open session (process death, tab
    // re-entry). Resume index = first club below the 5-shot target; when
    // every club has >= 5 kept, re-run the gate on the last club. The shot
    // set is read fresh inside the effect — the gated bagActiveShots capture
    // may still be empty on the first composition after process death.
    LaunchedEffect(bagActive?.id) {
        val active = bagActive ?: return@LaunchedEffect
        if (bagCollector.sessionId != active.id) {
            val shots = sessionRepository.observeBagMappingShots(active.id).first()
            val plan = active.clubSnapshot().map { BagPlanClub(it.first, it.second) }
            val keptByClub = shots.filter { !it.filtered }.groupingBy { it.clubName }.eachCount()
            val idx = plan.indexOfFirst { (keptByClub[it.name] ?: 0) < ClubQualityGate.TARGET_KEPT }
            bagCollector.beginAt(active.id, plan, if (idx == -1) plan.size - 1 else idx)
        }
    }

    // M6: the last club advancing past the plan completes the session.
    LaunchedEffect(bagCollector.done, bagActive?.id) {
        if (bagCollector.done && bagActive != null) completeBagTest()
    }
```

- [ ] **Step 4: Route shots and misreads** (modify `routeShot` `:247-263` and the `onMisread` callback `:271-277`)

In `routeShot`, insert the bag branch FIRST:

```kotlin
    fun routeShot(ballData: com.hpsmiles.golfsim.core.ble.BallData, source: com.hpsmiles.golfsim.core.data.record.ShotSource) {
        // M6: mapping collection captures ONLY while the guided COLLECTING
        // view is up on the BAG tab (spec §4). Leaving the tab pauses
        // capture; progress persists and the range keeps its own shots.
        if (tab == RangeTab.BAG && bagCollecting) {
            val shot = bagCollector.add(ballData)
            if (shot != null) {
                val sessionId = bagCollector.sessionId
                scope.launch {
                    if (sessionId > 0) {
                        sessionRepository.appendBagMappingShot(
                            sessionId, shot.clubName, shot.clubType, shot.ballData,
                            shot.carryM, shot.totalM, shot.timestampMs,
                        )
                    }
                }
            }
            return
        }
        when (activeGame) {
            // ... existing branches unchanged ...
        }
    }
```

In the `onMisread` callback:

```kotlin
        gattClient.onMisread = {
            scope.launch {
                // M6: while collecting, a mishit feeds the guided "no read"
                // pill (in-memory, coalesced) and never the range session.
                if (tab == RangeTab.BAG && bagCollecting) {
                    bagCollector.markNoRead()
                } else if (session.markMisread()) sessionRepository.incrementMisread()
            }
        }
```

- [ ] **Step 5: NavRail button + screen branch**

After the GAMES `NavRailButton` (`:539`) add:

```kotlin
                    NavRailButton("BAG", tab == RangeTab.BAG, onClick = { requestTab(RangeTab.BAG) })
```

(`requestTab` needs no change: mapping is leave-safe — persist-and-resume, spec §2. Leaving the tab mid-collection keeps `bagCollecting=true` but shots only capture when `tab == RangeTab.BAG` too; re-entering BAG re-reports the collecting state via the screen's `LaunchedEffect`.)

In the `when (tab)` switch (between the GAMES and SETTINGS branches):

```kotlin
                    RangeTab.BAG -> {
                        BagMappingScreen(
                            modifier = Modifier.weight(1f),
                            clubs = clubRecords,
                            activeSession = bagActive,
                            latestCompleted = bagLatest,
                            history = bagHistory,
                            shotCounts = bagShotCounts,
                            activeShots = bagActiveShots,
                            viewedShots = bagViewedShots,
                            collector = bagCollector,
                            onViewedSessionChange = { bagViewedSessionId = it },
                            onCollectingChange = { bagCollecting = it },
                            onStartTest = ::startBagTest,
                            onOpenSettings = { requestTab(RangeTab.SETTINGS) },
                            onCompleteSession = ::completeBagTest,
                        )
                    }
```

- [ ] **Step 6: Compile + run all app tests**

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:test
```
Expected: BUILD SUCCESSFUL, all tests PASS (pre-existing suites untouched; `GameLeavePolicy`/`requestTab` behavior unchanged).

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(app): BAG tab with guided shot routing and session lifecycle (M6)"
```

---

### Task 17: Full verification + ROADMAP

**Files:**
- Modify: `ROADMAP.md` (~line 83, M6 entry)

- [ ] **Step 1: Build everything and run every test**

```powershell
.\gradlew.bat build
```
Expected: BUILD SUCCESSFUL — assemble + every module's unit tests, including the new `BagMappingStatsTest`, `MigrationFrom3Test`, `BagMappingRepositoryTest`, `ClubQualityGateTest`, `GapAnalysisTest`, `BoxPlotGeomTest`, `BagMappingCollectorTest`, `BagMappingFormatsTest`.

- [ ] **Step 2: Update ROADMAP M6 status**

Read `ROADMAP.md` lines 78–100, then mark the M6 entry shipped using the exact same formatting convention the M5.5 shipped entry uses (status marker + date). Keep the exit-criteria sentence.

- [ ] **Step 3: Commit**

```powershell
git add ROADMAP.md
git commit -m "docs: M6 bag mapping shipped"
```

- [ ] **Step 4: Manual smoke (tablet, optional but recommended)**

`.\gradlew.bat :app:installDebug` then on-device: BAG tab → START TEST → fire demo shots (MODE: DEMO + FIRE) → verify progress dots, gate prompt behavior with clustered carries, END TEST, matrix renders, RETEST shows the banner, HISTORY lists the result, app kill mid-collection resumes where it left off, and HISTORY/Range show zero mapping contamination.

---

## Self-review record (writing-plans checklist)

1. **Spec coverage:** §2 decisions → isolation (Tasks 2/5/16), sample policy + gate (6/13), auto-filter (1/5), guided order + skip + early end (9/13), persist-and-resume (5/9/16), retest banner (12/15), matrix layout (11/12), drill-down (12), metres (10), exclusions (15/16), history (14/15). §3 state table → Task 15. §4 flow → Tasks 9/13/16. §5 filter + gate → Tasks 1/6. §6 result + gaps + BoxPlotGeom → Tasks 7/8/11/12. §7 data model + repo API → Tasks 2/3/5. §8 pure modules → Tasks 1/6/7/8. §9 edge cases → empty bag (10/15), single club (7/11), partial bag (12: snapshot rows with "no data"), app kill (5/16), snapshots (2), recompute determinism (1), corrupt DB (3 comment + existing recovery). §10 tests → every task. §11 out of scope — not built.
2. **Placeholder scan:** none — every step carries complete code or exact edit anchors.
3. **Type consistency:** `BagPlanClub(name, type)`, `BagMappedShot`, `CarryMatrixRow(clubName, kept, filtered)`, `BagMappingSessionShotCount(sessionId, count)`, repo members (`bagMappingActive/bagMappingLatestCompleted/bagMappingHistory/observeBagMappingShots/startBagMappingSession/appendBagMappingShot/completeBagMappingSession/bagMappingShotCounts`), and gate constants (`TARGET_KEPT/MAX_KEPT/MAX_FILTERED_FOR_PROMPT/MAX_WINDOW_FRACTION`) are used identically across tasks; `filtered: Boolean` maps to Room `INTEGER NOT NULL` in the migration DDL (Task 3) matching `4.json`.


