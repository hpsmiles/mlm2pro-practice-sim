# M7 Club & Shaft Testing (FIT mode) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** New FIT tab: live head-to-head club comparison with a per-club results table, per-shot exclusion, and a colour-coded top-down view with dispersion ovals — persisted end-to-end (Room v5).

**Architecture:** Bag-mapping clone (spec §decision): `fitting_sessions`/`fitting_shots` tables, write-through per shot, plain Compose state holders, auto-start on first FIT shot, kill/resume from Room flows, completed comparisons in FIT history. Fitting shots never enter range sessions.

**Tech Stack:** Kotlin, Jetpack Compose, Room v4→v5, Robolectric tests, pure-JVM stats/geometry.

**Spec:** `docs/superpowers/specs/2026-10-08-m7-club-fitting-design.md`

**Build commands (Windows, per AGENTS.md):**
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.MigrationFrom4Test"   # typed variant; flags precede tasks
.\gradlew.bat --console=plain build        # whole build + all tests (task 11)
```
- `:core:physics` is the only plain-`test` module (not touched by this plan).
- Gradle is quiet during configure (5s–2min) — quiet ≠ hung.

**Conventions enforced by this plan:** no ViewModels / Navigation-Compose; pure logic JVM-tested; fitting shots never persist to range sessions; Room migrations never destructive; per-shot club snapshots survive the temp-club purge.

**Deviation from spec §1 (agreed during planning):** no `onCollectingChange` seam — `routeShot` gates directly on `tab == RangeTab.FIT`, so capture stays live across COMPARE/RESULTS (one comparison per session) and stops when the tab changes. FIT has no guided collector state, so the BAG seam is redundant.

## Execution status

| Task | Status |
|---|---|
| 1. Room v5 tables/DAOs/migration | ✅ done — 649b6fe, spec ✅, quality ✅ approve (minor follow-ups ride with Task 3: DAO ORDER BY guards, status-literal interpolation, ordering comment) |
| 2. FittingStats (TDD) | ✅ done — 401a475, spec ✅, quality ✅ approve (5 minor backlog items, none blocking) |
| 3. Repository fitting API (TDD) | ✅ done — 6a4d2e2 (Task-1 DAO follow-ups) + ab7f860, spec ✅, quality ✅ approve (4 minor: no session scoping on setFittingShotsExcluded — bag-consistent, KDoc candidate; untested empty-ids/stale-id branches; missing restart-cycle assertion — all ride with Task 11 test sweep) |
| 4. DispersionOval (TDD) | ✅ done — f903f08, spec ✅, quality ✅ approve (3 polish-only minors queued for Task 11) |
| 5. FittingController + AppRoot wiring | ✅ done — c36b0e6, spec ✅, quality ✅ approve (ora-10; latent stale-id race found). Task 7 MUST first land hardening commit: bind sessionId from Room via `LaunchedEffect(fitActive?.id) { fittingController.sessionId = fitActive?.id ?: 0 }` + drop the `.also` write-back in routeShot; `sessionId` → mutableStateOf; add fitSession to green/turf surface LaunchedEffects (RANGE parity). Misread-pill decision: none on FIT in v1 (GAMES precedent — FIT misreads attribute to range session; no signature change) |
| 6. UI design pass (designer) | ✅ done — design notes committed; **visual overrides for Tasks 8/9** (design notes are authority): ovals 1σ α0.55 / 2σ α0.25 (NOT Task 9's 0.45/0.9), baseline club marked by 1 dp Teal row border (NOT BASE chip), drill-down spans full card width OUTSIDE the horizontal scroll, COMPARE canvas VIEW chip omitted in v1, club colours by positional slot incl. NEW `Comparison.E 0xFF6FD86F` / `F 0xFFE85C7A` |
| 7. COMPARE view | ✅ done — 900503b (hardening: sessionId from Room via LaunchedEffect, .also dropped, mutableStateOf, fitSession surface parity) + ad30e35 + be59e83 + 352d5b3 (review fixes: VIEW chip omitted, HISTORY · N TextMuted suffix) + 3ba9946 (polish: mph helper, remembered summaries, gated unit). spec ✅ 3 rounds, quality ✅ approve (follow-ups ride with Task 9: E/F + ActiveClubButton/speed-chip promotion, shared LastShotChips) |
| 8. RESULTS table | ✅ done — 7f58008+1497fd3 (MPH ruling), spec ✅, quality ✅ approve (polish rides with Task 11: remember(shots) summaries, dead Col formatters comment, centered empty state, header single-Spacer) |
| 9. RESULTS top-down | ✅ done — d041904 + 45fa44a (E/F promotion), spec ✅, quality ✅ approve (polish rides with Task 11: keptByClub single-sourcing, degenerate-cloud comment, trailing blank lines) |
| 10. HISTORY | in progress |
| 11. Build/tests/conventions/device | pending |

Notes for implementers: repo convention is `src/main/kotlin` (plan paths say `java` — follow repo). `ShotResult` lives in `core.physics` with required `rolloutM`.

---

## File Structure

```
core/data/src/main/java/com/hpsmiles/golfsim/core/data/
  entity/FittingSessionEntity.kt        (new — session row + FittingStatus)
  entity/FittingShotEntity.kt           (new — shot row with club snapshot + excluded)
  dao/FittingSessionDao.kt              (new)
  dao/FittingShotDao.kt                 (new)
  fitting/FittingStats.kt               (new — pure aggregates, deltas, duff filter)
  Mlm2proDatabase.kt                    (mod — v5, MIGRATION_4_5, 2 DAOs)
  SessionRepository.kt                  (mod — fitting API + flows)
  schemas/.../5.json                    (generated + committed)
  test/.../MigrationFrom4Test.kt        (new)
  test/.../fitting/FittingStatsTest.kt  (new)
  test/.../FittingRepositoryTest.kt     (new)

app/src/main/java/com/hpsmiles/golfsim/
  range/AppRoot.kt                      (mod — FIT tab, routing, flows, resume)
  range/ClubPickerOverlay.kt            (mod — addLabel/initialTest params)
  range/AddClubForm.kt                  (mod — initialTest param)
  fitting/FittingController.kt          (new — UI state holder)
  fitting/FittingColors.kt              (new — club palette)
  fitting/FittingFormats.kt             (new — number formatting)
  fitting/DispersionOval.kt             (new — pure ellipse math)
  fitting/FittingScreen.kt              (new — view dispatcher)
  fitting/FittingCompareView.kt         (new — live view + comparison card)
  fitting/FittingResultsView.kt         (new — TABLE | TOP-DOWN switch)
  fitting/FittingTable.kt               (new — table + drill-down + deltas)
  fitting/FittingTopDown.kt             (new — coloured dots + ovals)
  fitting/FittingHistoryView.kt         (new — completed comparisons)
  test/.../fitting/DispersionOvalTest.kt (new)
```

---

### Task 1: Room v5 — fitting tables, DAOs, migration

**Files:**
- Create: `core/data/src/main/java/com/hpsmiles/golfsim/core/data/entity/FittingSessionEntity.kt`
- Create: `core/data/src/main/java/com/hpsmiles/golfsim/core/data/entity/FittingShotEntity.kt`
- Create: `core/data/src/main/java/com/hpsmiles/golfsim/core/data/dao/FittingSessionDao.kt`
- Create: `core/data/src/main/java/com/hpsmiles/golfsim/core/data/dao/FittingShotDao.kt`
- Modify: `core/data/src/main/java/com/hpsmiles/golfsim/core/data/Mlm2proDatabase.kt`
- Modify: `core/data/src/main/java/com/hpsmiles/golfsim/core/data/SessionRepository.kt` (register migration)
- Test: `core/data/src/test/java/com/hpsmiles/golfsim/core/data/MigrationFrom4Test.kt`

- [ ] **Step 0: Branch**

```bash
git checkout -b m7-club-fitting
```

- [ ] **Step 1: Write the failing migration test**

Create `MigrationFrom4Test.kt` — clone the structure of `MigrationFrom3Test.kt` (same dir). The v4 DDL = the v3 DDL pasted in `MigrationFrom3Test` PLUS the two `bag_mapping_*` tables exactly as created by `MIGRATION_3_4` (both are in the repo already — copy them verbatim). The v4 `identityHash` must be read from `core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/4.json` (`database.identityHash` field) and substituted for `V4_IDENTITY_HASH` below.

```kotlin
package com.hpsmiles.golfsim.core.data

import com.hpsmiles.golfsim.core.data.entity.FittingStatus
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.ble.BallData
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.database.sqlite.SQLiteDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MigrationFrom4Test {

    @Test
    fun `v4 file migrates - fitting tables usable - prior data intact`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("golfsim.db")
        dbFile.parentFile!!.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        // v4 DDL — sessions/shots/clubs/game_results copied verbatim from
        // MigrationFrom3Test; bag_mapping_* copied verbatim from MIGRATION_3_4.
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
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `bag_mapping_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startedAtMs` INTEGER NOT NULL, `completedAtMs` INTEGER, " +
                "`status` TEXT NOT NULL, `clubList` TEXT NOT NULL)",
        )
        raw.execSQL(
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
        raw.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bag_mapping_shots_sessionId` " +
                "ON `bag_mapping_shots` (`sessionId`)",
        )
        raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        raw.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, 'V4_IDENTITY_HASH_FROM_4_JSON')",
        )
        raw.execSQL("INSERT INTO sessions(startedAtEpochMs, endedAtEpochMs, title, misreadCount) VALUES(1000, NULL, NULL, 0)")
        raw.execSQL("INSERT INTO clubs(name, sortOrder, type, isTemp) VALUES('7i', 7, 'IRON', 0)")
        raw.execSQL(
            "INSERT INTO shots(sessionId, seq, timestampMs, source, clubName, clubHeadSpeedMps, " +
                "ballSpeedMps, launchDirDeg, launchAngleDeg, spinAxisDeg, spinRpm, unknown1, unknown2, " +
                "carryM, totalM, sideM, apexM, flightTimeSec, excluded, clubWasTemp) " +
                "VALUES(1, 0, 1000, 0, '7i', 33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10, 140.0, 150.0, -3.0, 27.0, 6.0, 0, 0)",
        )
        raw.version = 4
        raw.close()

        val repo = SessionRepository.open(context)
        repo.initializeAndRestore() // migration runs here; prior data must survive

        // Prior range data survives.
        val summaries = repo.summaries.first()
        assertEquals(1, summaries.size)
        assertEquals(1, summaries.single().shotCount)

        // New fitting tables are usable immediately: start → append → complete.
        val fitId = repo.startFittingSession(startedAtMs = 5000)!!
        assertEquals(FittingStatus.IN_PROGRESS, repo.fittingActive.first()?.status)
        repo.appendFittingShot(
            sessionId = fitId,
            club = ClubRecord(1, "7i", ClubType.IRON, false),
            ballData = BallData(33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10),
            result = com.hpsmiles.golfsim.core.ble.ShotResult(
                carryM = 140.0, totalM = 150.0, sideM = -3.0, apexM = 27.0, flightTimeSec = 6.0,
            ),
            timestampMs = 5001,
        )
        assertEquals(1, repo.observeFittingShots(fitId).first().size)
        repo.completeFittingSession(fitId, completedAtMs = 9000)
        assertNull(repo.fittingActive.first())
        assertEquals(1, repo.fittingHistory.first().size)
        assertNotNull(repo.fittingHistory.first().single().completedAtMs)
    }
}
```

NOTE: check the real `ShotResult` constructor signature in `core/ble` (fields used by `makeShotEntity`: `carryM, totalM, sideM, apexM, flightTimeSec`) and adapt the constructor call — do not change the assertion semantics. If `BallData`'s parameter names differ, match `appendBagMappingShot`'s usage (`ballData.clubHeadSpeed, .ballSpeed, .launchAngle, .launchDirection, .spinAxis, .totalSpin`).

- [ ] **Step 2: Run test to verify it fails (compile error — no fitting API yet)**

```powershell
.\gradlew.bat --console=plain :core:data:compileDebugUnitTestKotlin
```
Expected: FAIL — unresolved `startFittingSession` / `FittingStatus`.

- [ ] **Step 3: Entities**

`FittingSessionEntity.kt`:
```kotlin
package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Status values for [FittingSessionEntity.status]. */
object FittingStatus {
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"
}

/**
 * One club-fitting comparison (M7 spec §2). At most one row with status
 * IN_PROGRESS exists — the repository enforces it (bag pattern). The
 * compared club set is NOT stored here: it derives from the distinct club
 * snapshots in fitting_shots, ordered by first appearance — stable across
 * kill/resume and immune to the temp-club purge.
 */
@Entity(tableName = "fitting_sessions")
data class FittingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    /** Null while the session is open; set when completed. */
    val completedAtMs: Long?,
    val status: String,            // FittingStatus values
)
```

`FittingShotEntity.kt`:
```kotlin
package com.hpsmiles.golfsim.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stored fitting shot (M7 spec §2). ALL shots are stored — excluded ones
 * stay visible (greyed) in the drill-down. Club snapshot columns mean the
 * end-of-session temp-club purge never rewrites fitting history (same trick
 * as ShotEntity.clubWasTemp). No hard FK on sessionId — the repository
 * manages lifecycle (existing entity conventions).
 */
@Entity(tableName = "fitting_shots", indices = [Index("sessionId")])
data class FittingShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val clubId: Long,              // snapshot; the club row may be purged later
    val clubName: String,
    val clubType: String,          // ClubType.name snapshot
    val clubWasTemp: Boolean,
    val timestampMs: Long,
    // raw block (BallData field-for-field)
    val clubHeadSpeedMps: Double,
    val ballSpeedMps: Double,
    val launchAngleDeg: Double,
    val launchDirDeg: Double,
    val spinAxisDeg: Double,
    val totalSpinRpm: Int,
    // physics caches
    val carryM: Double,
    val totalM: Double,
    val sideM: Double,
    val apexM: Double,
    val flightTimeSec: Double,
    /** Excluded = outlier/mishit; drops from every aggregate, dot and ring. */
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
)
```

- [ ] **Step 4: DAOs**

`FittingSessionDao.kt`:
```kotlin
package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FittingSessionDao {
    @Insert
    suspend fun insert(session: FittingSessionEntity): Long

    @Query("SELECT * FROM fitting_sessions WHERE status = 'IN_PROGRESS' LIMIT 1")
    suspend fun findInProgress(): FittingSessionEntity?

    @Query("SELECT * FROM fitting_sessions WHERE status = 'IN_PROGRESS' LIMIT 1")
    fun observeInProgress(): Flow<FittingSessionEntity?>

    @Query("SELECT * FROM fitting_sessions WHERE status = 'COMPLETED' ORDER BY completedAtMs DESC")
    fun observeHistory(): Flow<List<FittingSessionEntity>>

    @Query("UPDATE fitting_sessions SET completedAtMs = :completedAtMs, status = 'COMPLETED' WHERE id = :id")
    suspend fun complete(id: Long, completedAtMs: Long)
}
```

`FittingShotDao.kt`:
```kotlin
package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FittingShotDao {
    @Insert
    suspend fun insert(shot: FittingShotEntity): Long

    @Query("SELECT * FROM fitting_shots WHERE sessionId = :sessionId ORDER BY id")
    fun observeBySession(sessionId: Long): Flow<List<FittingShotEntity>>

    @Query("UPDATE fitting_shots SET excluded = :excluded WHERE id IN (:ids)")
    suspend fun setExcluded(ids: List<Long>, excluded: Boolean)
}
```

- [ ] **Step 5: Database v5 + MIGRATION_4_5**

In `Mlm2proDatabase.kt`:
1. Add `FittingSessionEntity::class, FittingShotEntity::class` to `entities`, bump `version = 5`.
2. Add `abstract fun fittingSessionDao(): FittingSessionDao` and `abstract fun fittingShotDao(): FittingShotDao`.
3. Add the migration constant (same style as `MIGRATION_3_4`):

```kotlin
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fitting_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startedAtMs` INTEGER NOT NULL, `completedAtMs` INTEGER, " +
                        "`status` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fitting_shots` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, `clubId` INTEGER NOT NULL, " +
                        "`clubName` TEXT NOT NULL, `clubType` TEXT NOT NULL, " +
                        "`clubWasTemp` INTEGER NOT NULL, `timestampMs` INTEGER NOT NULL, " +
                        "`clubHeadSpeedMps` REAL NOT NULL, `ballSpeedMps` REAL NOT NULL, " +
                        "`launchAngleDeg` REAL NOT NULL, `launchDirDeg` REAL NOT NULL, " +
                        "`spinAxisDeg` REAL NOT NULL, `totalSpinRpm` INTEGER NOT NULL, " +
                        "`carryM` REAL NOT NULL, `totalM` REAL NOT NULL, `sideM` REAL NOT NULL, " +
                        "`apexM` REAL NOT NULL, `flightTimeSec` REAL NOT NULL, " +
                        "`excluded` INTEGER NOT NULL DEFAULT 0)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_fitting_shots_sessionId` " +
                        "ON `fitting_shots` (`sessionId`)",
                )
            }
        }
```

4. In `SessionRepository.build()` add `Mlm2proDatabase.MIGRATION_4_5` to `addMigrations(...)`.

- [ ] **Step 6: Run migration test to verify it passes (also exports 5.json)**

```powershell
.\gradlew.bat --console=plain :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.MigrationFrom4Test"
```
Expected: PASS. Room validates the migrated schema against the entities (any column/default mismatch fails here — fix the DDL until green).

- [ ] **Step 7: Verify + commit the exported schema**

```powershell
git status --short core/data/schemas
```
Expected: `5.json` present (Room `exportSchema = true` writes it on build). Stage everything (entities, DAOs, database, repository build(), test, 5.json) and commit:

```bash
git add core/data
git commit -m "feat(core-data): Room v5 fitting_sessions + fitting_shots (M7)"
```

---

### Task 2: FittingStats — pure aggregates, deltas, duff filter (TDD)

**Files:**
- Create: `core/data/src/main/java/com/hpsmiles/golfsim/core/data/fitting/FittingStats.kt`
- Test: `core/data/src/test/java/com/hpsmiles/golfsim/core/data/fitting/FittingStatsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.core.data.fitting

import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.record.ClubType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FittingStatsTest {

    private fun shot(
        id: Long,
        clubId: Long = 1,
        name: String = "A",
        excluded: Boolean = false,
        chs: Double = 33.0,
        ball: Double = 48.0,
        side: Double = 0.0,
        carry: Double = 140.0,
        total: Double = 150.0,
        spin: Int = 8000,
    ) = FittingShotEntity(
        id = id, sessionId = 1, clubId = clubId, clubName = name, clubType = "DRIVER",
        clubWasTemp = false, timestampMs = id, clubHeadSpeedMps = chs, ballSpeedMps = ball,
        launchAngleDeg = 12.0, launchDirDeg = -1.0, spinAxisDeg = -4.0, totalSpinRpm = spin,
        carryM = carry, totalM = total, sideM = side, apexM = 27.0, flightTimeSec = 6.0,
        excluded = excluded,
    )

    @Test
    fun `clubOrder - distinct clubs in first-appearance order`() {
        val shots = listOf(shot(1, clubId = 2, name = "B"), shot(2, clubId = 1, name = "A"), shot(3, clubId = 2, name = "B"))
        val order = FittingStats.clubOrder(shots)
        assertEquals(listOf(2L, 1L), order.map { it.id })
        assertEquals("B", order[0].name)
        assertEquals(ClubType.DRIVER, order[0].type)
    }

    @Test
    fun `summarize - excluded shots drop from every aggregate`() {
        val shots = listOf(
            shot(1, carry = 140.0, total = 150.0, side = -2.0),
            shot(2, carry = 150.0, total = 160.0, side = 4.0),
            shot(3, carry = 90.0, total = 95.0, side = 0.0, excluded = true, ball = 20.0),
        )
        val s = FittingStats.summarize(FittingStats.ClubKey(1, "A", ClubType.DRIVER, false), shots)
        assertEquals(2, s.kept)
        assertEquals(1, s.excluded)
        assertEquals(145.0, s.carry!!, 1e-9)
        assertEquals(5.0, s.carrySigma!!, 1e-9)
        assertEquals(155.0, s.total!!, 1e-9)
        assertEquals(3.0, s.offlineAvg!!, 1e-9)     // avg(|−2|, |4|)
        assertEquals(4.0, s.offlineWorst!!, 1e-9)
        assertEquals(48.0 / 33.0, s.smash!!, 1e-9)  // per-shot smash averaged
    }

    @Test
    fun `delta - significant vs noise band`() {
        assertTrue(FittingStats.delta(other = 5.0, baseline = 0.0, noise = 2.0)!!.significant)
        assertFalse(FittingStats.delta(other = 1.5, baseline = 0.0, noise = 2.0)!!.significant)
        assertNull(FittingStats.delta(other = null, baseline = 1.0, noise = 2.0))
        assertEquals(-3.0, FittingStats.delta(other = 2.0, baseline = 5.0, noise = 1.0)!!.delta, 1e-9)
    }

    @Test
    fun `applyDuffFilter - low ball speed vs median of ALL shots, dormant under 3`() {
        // median(20, 48, 46) = 46 → cutoff 39.1 → 20 is a duff (bag rule)
        val verdicts = FittingStats.applyDuffFilter(
            listOf(shot(1, ball = 20.0), shot(2, ball = 48.0), shot(3, ball = 46.0)),
        )
        assertEquals(listOf(true, false, false), verdicts.map { it.filtered })
        // Dormant below 3 shots: everything kept.
        val dormant = FittingStats.applyDuffFilter(listOf(shot(1, ball = 20.0), shot(2, ball = 48.0)))
        assertEquals(listOf(false, false), dormant.map { it.filtered })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
.\gradlew.bat --console=plain :core:data:compileDebugUnitTestKotlin
```
Expected: FAIL — unresolved `FittingStats`.

- [ ] **Step 3: Implement FittingStats**

```kotlin
package com.hpsmiles.golfsim.core.data.fitting

import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.record.ClubType
import kotlin.math.abs

/**
 * Pure M7 fitting math (spec §4): per-club aggregates over KEPT shots,
 * per-shot smash, offline stats, signed deltas with noise bands, and the
 * shared duff rule over fitting rows. No framework types; deterministic.
 * The comparison club set derives from shot snapshots, ordered by first
 * appearance (spec §2) — stable under kill/resume and temp-club purge.
 */
object FittingStats {

    // Delta noise bands (spec §4): |Δ| below the band renders dimmed.
    const val NOISE_CHS_MPS = 0.5
    const val NOISE_BALL_SPEED_MPS = 0.7
    const val NOISE_SMASH = 0.010
    const val NOISE_CARRY_M = 2.0
    const val NOISE_TOTAL_M = 2.0
    const val NOISE_LAUNCH_DEG = 0.5
    const val NOISE_DIR_DEG = 0.5
    const val NOISE_SPIN_RPM = 150.0
    const val NOISE_SPIN_AXIS_DEG = 2.0
    const val NOISE_OFFLINE_M = 1.0

    /** Club identity snapshotted per shot; the comparison key. */
    data class ClubKey(val id: Long, val name: String, val type: ClubType, val wasTemp: Boolean)

    /** Distinct clubs in first-appearance order (deduped). */
    fun clubOrder(shots: List<FittingShotEntity>): List<ClubKey> {
        val seen = LinkedHashMap<Long, ClubKey>()
        for (s in shots) {
            seen.getOrPut(s.clubId) {
                ClubKey(s.clubId, s.clubName, ClubType.fromName(s.clubType), s.clubWasTemp)
            }
        }
        return seen.values.toList()
    }

    data class ClubSummary(
        val key: ClubKey,
        val kept: Int,
        val excluded: Int,
        val chs: Double?,
        val ballSpeed: Double?,
        val smash: Double?,
        val carry: Double?,
        val carrySigma: Double?,
        val total: Double?,
        val totalSigma: Double?,
        val launch: Double?,
        val dir: Double?,
        val spin: Double?,
        val spinAxis: Double?,
        val offlineAvg: Double?,
        val offlineWorst: Double?,
    )

    /** Aggregates over one club's KEPT shots only (excluded drops everywhere). */
    fun summarize(key: ClubKey, shots: List<FittingShotEntity>): ClubSummary {
        val mine = shots.filter { it.clubId == key.id }
        val kept = mine.filter { !it.excluded }
        val chs = BagMappingStats.distribution(kept.map { it.clubHeadSpeedMps })
        val ball = BagMappingStats.distribution(kept.map { it.ballSpeedMps })
        val carry = BagMappingStats.distribution(kept.map { it.carryM })
        val total = BagMappingStats.distribution(kept.map { it.totalM })
        val launch = BagMappingStats.distribution(kept.map { it.launchAngleDeg })
        val dir = BagMappingStats.distribution(kept.map { it.launchDirDeg })
        val spin = BagMappingStats.distribution(kept.map { it.totalSpinRpm.toDouble() })
        val axis = BagMappingStats.distribution(kept.map { it.spinAxisDeg })
        val offline = kept.map { abs(it.sideM) }
        val smashes = kept.filter { it.clubHeadSpeedMps > 0.0 }.map { it.ballSpeedMps / it.clubHeadSpeedMps }
        return ClubSummary(
            key = key,
            kept = kept.size,
            excluded = mine.size - kept.size,
            chs = chs?.mean,
            ballSpeed = ball?.mean,
            smash = BagMappingStats.distribution(smashes)?.mean,
            carry = carry?.mean,
            carrySigma = carry?.sigma,
            total = total?.mean,
            totalSigma = total?.sigma,
            launch = launch?.mean,
            dir = dir?.mean,
            spin = spin?.mean,
            spinAxis = axis?.mean,
            offlineAvg = if (offline.isEmpty()) null else offline.average(),
            offlineWorst = offline.maxOrNull(),
        )
    }

    data class DeltaVerdict(val delta: Double, val significant: Boolean)

    /** Signed delta (other − baseline) + noise verdict; null when either side lacks data. */
    fun delta(other: Double?, baseline: Double?, noise: Double): DeltaVerdict? {
        if (other == null || baseline == null) return null
        val d = other - baseline
        return DeltaVerdict(d, abs(d) >= noise)
    }

    /** Shared duff rule (bag constants) over fitting rows: ball speed < 85 % of the median of ALL the club's shots, dormant under 3. */
    fun applyDuffFilter(shots: List<FittingShotEntity>): List<BagMappingStats.FilterVerdict> {
        if (shots.size < BagMappingStats.DUFF_MIN_SHOTS) {
            return shots.map { BagMappingStats.FilterVerdict(it.id, filtered = false, reason = null) }
        }
        val medianBallSpeed = BagMappingStats.median(shots.map { it.ballSpeedMps })
            ?: return shots.map { BagMappingStats.FilterVerdict(it.id, filtered = false, reason = null) }
        val cutoff = medianBallSpeed * BagMappingStats.DUFF_BALL_SPEED_FRACTION
        return shots.map {
            if (it.ballSpeedMps < cutoff) {
                BagMappingStats.FilterVerdict(it.id, filtered = true, BagMappingStats.REASON_DUFF_LOW_BALL_SPEED)
            } else {
                BagMappingStats.FilterVerdict(it.id, filtered = false, reason = null)
            }
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

```powershell
.\gradlew.bat --console=plain :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.fitting.FittingStatsTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(core-data): FittingStats — per-club aggregates, deltas, duff filter (M7)"
```

---

### Task 3: Repository fitting API (TDD)

**Files:**
- Modify: `core/data/src/main/java/com/hpsmiles/golfsim/core/data/SessionRepository.kt`
- Test: `core/data/src/test/java/com/hpsmiles/golfsim/core/data/FittingRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Clone `BagMappingRepositoryTest`'s setup (Robolectric + runTest, `repo()` helper). New file:

```kotlin
package com.hpsmiles.golfsim.core.data

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.ble.ShotResult
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FittingRepositoryTest {

    private fun repo() = SessionRepository.open(RuntimeEnvironment.getApplication())

    private fun ball(ballSpeed: Double) = BallData(
        clubHeadSpeed = 33.0, ballSpeed = ballSpeed, launchDirection = -1.0,
        launchAngle = 12.0, spinAxis = -4.0, totalSpin = 8000, unknown1 = 5, unknown2 = 10,
    )

    private fun result(carry: Double = 140.0) = ShotResult(
        carryM = carry, totalM = carry + 10.0, sideM = -3.0, apexM = 27.0, flightTimeSec = 6.0,
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
```

NOTE: match the real `ShotResult` constructor (see Task 1 note). `RuntimeEnvironment` import: `androidx.test.core.app.RuntimeEnvironment` if Robolectric's isn't what `BagMappingRepositoryTest` uses — copy that file's import exactly.

- [ ] **Step 2: Run to verify it fails**

```powershell
.\gradlew.bat --console=plain :core:data:compileDebugUnitTestKotlin
```
Expected: FAIL — unresolved `fittingActive`, `startFittingSession`, etc.

- [ ] **Step 3: Implement the repository API**

In `SessionRepository.kt` add (style mirrors the bag section verbatim — same try/catch/persistError pattern):

```kotlin
    private val fittingSessionDao get() = db.fittingSessionDao()
    private val fittingShotDao get() = db.fittingShotDao()

    // ---- M7 club fitting -------------------------------------------------

    val fittingActive: Flow<FittingSessionEntity?>
        get() = fittingSessionDao.observeInProgress()

    val fittingHistory: Flow<List<FittingSessionEntity>>
        get() = fittingSessionDao.observeHistory()

    fun observeFittingShots(sessionId: Long): Flow<List<FittingShotEntity>> =
        fittingShotDao.observeBySession(sessionId)

    /** Idempotent: at most one IN_PROGRESS fitting session (bag pattern). */
    suspend fun startFittingSession(startedAtMs: Long): Long? = try {
        db.withTransaction {
            val existing = fittingSessionDao.findInProgress()
            existing?.id
                ?: fittingSessionDao.insert(
                    FittingSessionEntity(
                        startedAtMs = startedAtMs,
                        completedAtMs = null,
                        status = FittingStatus.IN_PROGRESS,
                    ),
                )
        }.also { persistError.value = false }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Log.e(TAG, "startFittingSession failed", t)
        persistError.value = true
        null
    }

    /**
     * Write-through per shot with a full club snapshot (M7 spec §2). No
     * auto-filter: fitting exclusion is manual or one-tap via the UI.
     */
    suspend fun appendFittingShot(
        sessionId: Long,
        club: ClubRecord,
        ballData: BallData,
        result: ShotResult,
        timestampMs: Long,
    ): Long? = try {
        fittingShotDao.insert(
            FittingShotEntity(
                sessionId = sessionId,
                clubId = club.id,
                clubName = club.name,
                clubType = club.type.name,
                clubWasTemp = club.isTemp,
                timestampMs = timestampMs,
                clubHeadSpeedMps = ballData.clubHeadSpeed,
                ballSpeedMps = ballData.ballSpeed,
                launchAngleDeg = ballData.launchAngle,
                launchDirDeg = ballData.launchDirection,
                spinAxisDeg = ballData.spinAxis,
                totalSpinRpm = ballData.totalSpin,
                carryM = result.carryM,
                totalM = result.totalM,
                sideM = result.sideM,
                apexM = result.apexM,
                flightTimeSec = result.flightTimeSec,
            ),
        ).also { persistError.value = false }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Log.e(TAG, "appendFittingShot failed", t)
        persistError.value = true
        null
    }

    suspend fun setFittingShotsExcluded(ids: List<Long>, excluded: Boolean) {
        if (ids.isEmpty()) return
        try {
            fittingShotDao.setExcluded(ids, excluded)
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "setFittingShotsExcluded failed", t)
            persistError.value = true
        }
    }

    suspend fun completeFittingSession(id: Long, completedAtMs: Long) {
        try {
            fittingSessionDao.complete(id, completedAtMs)
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "completeFittingSession failed", t)
            persistError.value = true
        }
    }
```

Add imports: `com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity`, `FittingShotEntity`, `FittingStatus`.

- [ ] **Step 4: Run to verify it passes**

```powershell
.\gradlew.bat --console=plain :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.FittingRepositoryTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(core-data): fitting session repository API (M7)"
```

---

### Task 4: DispersionOval — pure ellipse math (TDD)

**Files:**
- Create: `app/src/main/java/com/hpsmiles/golfsim/fitting/DispersionOval.kt`
- Test: `app/src/test/java/com/hpsmiles/golfsim/fitting/DispersionOvalTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.fitting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class DispersionOvalTest {

    @Test
    fun `axis-aligned cloud - principal axes aligned with x and y`() {
        // (±2, 0), (0, ±1): var_x = 2, var_y = 0.5, cov = 0
        val oval = DispersionOval.compute(listOf(2.0 to 0.0, -2.0 to 0.0, 0.0 to 1.0, 0.0 to -1.0))!!
        assertEquals(0.0, oval.cx, 1e-9)
        assertEquals(0.0, oval.cy, 1e-9)
        assertEquals(sqrt(2.0), oval.a, 1e-9)
        assertEquals(sqrt(0.5), oval.b, 1e-9)
        assertEquals(0.0, oval.angleRad, 1e-9)
    }

    @Test
    fun `rotated cloud - angle follows the principal axis`() {
        // The aligned cloud rotated by 45°: angle → π/4, same axes.
        val rotated = listOf(
            1.4142135623730951 to 1.4142135623730951,
            -1.4142135623730951 to -1.4142135623730951,
            -0.7071067811865476 to 0.7071067811865476,
            0.7071067811865476 to -0.7071067811865476,
        )
        val oval = DispersionOval.compute(rotated)!!
        assertEquals(sqrt(2.0), oval.a, 1e-9)
        assertEquals(sqrt(0.5), oval.b, 1e-9)
        assertEquals(PI / 4, oval.angleRad, 1e-9)
    }

    @Test
    fun `degenerate inputs - null`() {
        assertNull(DispersionOval.compute(listOf(1.0 to 1.0, 2.0 to 2.0)))          // < 3 points
        assertNull(DispersionOval.compute(listOf(1.0 to 1.0, 1.0 to 1.0, 1.0 to 1.0))) // zero variance
    }

    @Test
    fun `polygon - sigma scaling and vertex placement`() {
        val pts = DispersionOval.polygon(DispersionOval.Oval(0.0, 0.0, 1.0, 1.0, 0.0), sigmaScale = 2.0, segments = 4)
        // t = 0, π/2, π, 3π/2 on a radius-2 circle
        assertEquals(listOf(2.0 to 0.0, 0.0 to 2.0, -2.0 to 0.0, 0.0 to -2.0)
            .zip(pts) { e, a -> assertEquals(e.first, a.first, 1e-9); assertEquals(e.second, a.second, 1e-9) }.size, 4)
        assertNotNull(pts)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
.\gradlew.bat --console=plain :app:compileDebugUnitTestKotlin
```
Expected: FAIL — unresolved `DispersionOval`.

- [ ] **Step 3: Implement DispersionOval**

```kotlin
package com.hpsmiles.golfsim.fitting

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure dispersion-oval math (M7 spec §5): mean + covariance of an (x, y)
 * point cloud → 1σ ellipse parameters + outline polygon. Metres in, metres
 * out; no framework types. Null below 3 points or for a degenerate cloud
 * (zero variance — no meaningful spread to draw).
 */
object DispersionOval {

    data class Oval(
        val cx: Double,
        val cy: Double,
        /** 1σ semi-axis along [angleRad] measured from +x. */
        val a: Double,
        /** 1σ semi-axis perpendicular to [angleRad]. */
        val b: Double,
        val angleRad: Double,
    )

    fun compute(points: List<Pair<Double, Double>>): Oval? {
        if (points.size < 3) return null
        val n = points.size.toDouble()
        val mx = points.sumOf { it.first } / n
        val my = points.sumOf { it.second } / n
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for ((x, y) in points) {
            val dx = x - mx
            val dy = y - my
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        sxx /= n; syy /= n; sxy /= n
        val sum = sxx + syy
        val diff = sxx - syy
        val root = sqrt(diff * diff / 4.0 + sxy * sxy)
        val lambda1 = sum / 2.0 + root   // larger eigenvalue
        val lambda2 = sum / 2.0 - root
        if (lambda1 <= 1e-12 || lambda2 <= 1e-12) return null
        // Eigenvector of the LARGER eigenvalue (closed form, symmetric 2×2).
        val angle = 0.5 * atan2(2.0 * sxy, diff)
        return Oval(mx, my, sqrt(lambda1), sqrt(lambda2), angle)
    }

    /** Ellipse outline points (x, y) at [sigmaScale]·1σ, CCW, [segments] vertices. */
    fun polygon(oval: Oval, sigmaScale: Double, segments: Int = 32): List<Pair<Double, Double>> {
        val a = oval.a * sigmaScale
        val b = oval.b * sigmaScale
        val cosA = cos(oval.angleRad)
        val sinA = sin(oval.angleRad)
        return (0 until segments).map { i ->
            val t = 2.0 * PI * i / segments
            val ex = a * cos(t)
            val ey = b * sin(t)
            (oval.cx + ex * cosA - ey * sinA) to (oval.cy + ex * sinA + ey * cosA)
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

```powershell
.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.fitting.DispersionOvalTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): DispersionOval pure math (M7)"
```

---

### Task 5: FittingController + AppRoot wiring (FIT tab routes shots)

**Files:**
- Create: `app/src/main/java/com/hpsmiles/golfsim/fitting/FittingController.kt`
- Create: `app/src/main/java/com/hpsmiles/golfsim/fitting/FittingScreen.kt` (placeholder body; full signature)
- Modify: `app/src/main/java/com/hpsmiles/golfsim/range/AppRoot.kt`

- [ ] **Step 1: FittingController**

```kotlin
package com.hpsmiles.golfsim.fitting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** FIT tab views (spec §1): live compare → results (table + top-down) → history. */
enum class FittingView { COMPARE, RESULTS, HISTORY }

/** Carry/total toggle for the comparison card + table distance columns. */
enum class FittingDistanceMode(val label: String) { CARRY("CARRY"), TOTAL("TOTAL") }

/** RESULTS screen switch: metric table vs coloured top-down. */
enum class FittingResultsMode(val label: String) { TABLE("TABLE"), TOP_DOWN("TOP-DOWN") }

/**
 * FIT tab UI state (M7 spec §1). Plain state holder — no ViewModels. All
 * shot/session truth lives in Room; this holds only which view is up and
 * table options. [sessionId] binds the active fitting session (0 = none
 * yet; auto-started by the first routed shot).
 */
class FittingController {
    var view by mutableStateOf(FittingView.COMPARE)
    var resultsMode by mutableStateOf(FittingResultsMode.TABLE)
    var distanceMode by mutableStateOf(FittingDistanceMode.CARRY)
    /** Null = deltas auto-pair (2 clubs) or default to first club (3+). */
    var baselineClubId by mutableStateOf<Long?>(null)
    /** Non-null = read-only RESULTS of a completed history session. */
    var viewedSessionId by mutableStateOf<Long?>(null)
    var sessionId: Long = 0
}
```

- [ ] **Step 2: FittingScreen placeholder (full signature, trivial body)**

```kotlin
package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.designsystem.theme.GolfSpacing
import com.hpsmiles.golfsim.range.DisplayShot

/**
 * M7 FIT tab (spec §1). Plain state dispatcher — shot/session truth is
 * Room; [FittingController] holds only view/options state.
 */
@Composable
fun FittingScreen(
    modifier: Modifier = Modifier,
    controller: FittingController,
    clubs: List<ClubRecord>,
    activeClubName: String?,
    activeSession: FittingSessionEntity?,
    history: List<FittingSessionEntity>,
    liveShots: List<DisplayShot>,
    sessionShots: List<FittingShotEntity>,
    viewedShots: List<FittingShotEntity>,
    armed: Boolean,
    info: String,
    onSelectClub: (String?) -> Unit,
    onAddClub: suspend (String, ClubType, Boolean) -> Boolean,
    onComplete: () -> Unit,
    onSetExcluded: (List<Long>, Boolean) -> Unit,
) {
    Text("M7 FIT — screens land in tasks 7–10", modifier = modifier.padding(GolfSpacing.Md))
}
```

NOTE: import `GolfSpacing` from the same path the other app screens use (copy the import line from `RangeScreen.kt`); same for `Text`/material imports if the project uses a different material artifact.

- [ ] **Step 3: AppRoot wiring**

In `AppRoot.kt`:

1. Extend the tab enum: `private enum class RangeTab { RANGE, GAMES, BAG, FIT, SETTINGS, HISTORY }`
2. Add the nav button next to BAG's (mirror the existing `NavRailButton("BAG", ...)` call):
```kotlin
                        NavRailButton("FIT", tab == RangeTab.FIT) { requestTab(RangeTab.FIT) }
```
3. Add the wiring block next to the bag wiring (~`:117-124`):
```kotlin
    // M7: club fitting. Same architecture as bag mapping — Room truth, plain
    // UI state, write-through per shot, never enters range sessions.
    // fitSession is a separate RangeSession instance used ONLY for physics +
    // live display; its shots never reach persist().
    val fittingController = remember { FittingController() }
    val fitSession = remember { RangeSession() }
    val fitActive by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.fittingActive }, flowOf(null))
    }.collectAsState(initial = null)
    val fitHistory by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.fittingHistory }, flowOf(emptyList()))
    }.collectAsState(initial = emptyList())
    val fitShots by remember(sessionRepository, repoReady, fitActive?.id) {
        gatedFlow(
            repoReady,
            { fitActive?.let { sessionRepository.observeFittingShots(it.id) } ?: flowOf(emptyList()) },
            flowOf(emptyList()),
        )
    }.collectAsState(initial = emptyList())
    val fitViewedShots by remember(sessionRepository, repoReady, fittingController.viewedSessionId) {
        gatedFlow(
            repoReady,
            { fittingController.viewedSessionId?.let { sessionRepository.observeFittingShots(it) } ?: flowOf(emptyList()) },
            flowOf(emptyList()),
        )
    }.collectAsState(initial = emptyList())
```
4. Active-club single site (find where the club picker's `onSelectClub` lambda is currently wired in AppRoot and route it through this function; the picker call site then passes `::selectClub`):
```kotlin
    /** M7: one site for active-club changes (picker + post-add activation). */
    fun selectClub(name: String?) {
        activeClubName = name
        activeClubStore.set(name)
    }
```
(If the current handler writes different/extra state, keep that extra work inside `selectClub` so behaviour is unchanged for RANGE.)
5. Completion function next to `completeBagTest`:
```kotlin
    /** M7: END COMPARISON → the session completes; compare view resets. */
    fun completeFittingTest() {
        val active = fitActive ?: return
        scope.launch {
            sessionRepository.completeFittingSession(active.id, System.currentTimeMillis())
            fittingController.sessionId = 0
            fittingController.baselineClubId = null
            fittingController.view = FittingView.COMPARE
            fitSession.clearForEndedSession()
        }
    }
```
6. `routeShot`: insert this branch BEFORE the BAG branch (tab checks are mutually exclusive so order is safe):
```kotlin
        // M7: fitting captures whenever the FIT tab is up (any sub-view) —
        // one comparison per session; shots never enter range sessions and
        // are write-through persisted to fitting_shots with a club snapshot.
        if (tab == RangeTab.FIT) {
            val club = clubRecords.find { it.name == activeClubName }
            if (club == null) return  // no active club → nothing to compare
            val shot = fitSession.add(ballData) ?: return
            scope.launch {
                val id = fittingController.sessionId.takeIf { it > 0 }
                    ?: sessionRepository.startFittingSession(System.currentTimeMillis())?.also {
                        fittingController.sessionId = it
                    }
                if (id != null) {
                    sessionRepository.appendFittingShot(id, club, ballData, shot.shotResult, shot.timestampMs)
                }
            }
            return
        }
```
7. `when (tab)` content — add between BAG and SETTINGS:
```kotlin
                    RangeTab.FIT -> {
                        FittingScreen(
                            modifier = Modifier.weight(1f),
                            controller = fittingController,
                            clubs = clubRecords,
                            activeClubName = activeClubName,
                            activeSession = fitActive,
                            history = fitHistory,
                            liveShots = fitSession.shots,
                            sessionShots = fitShots,
                            viewedShots = fitViewedShots,
                            armed = <the same armed value the RANGE branch passes to RangeScreen>,
                            info = <the same info string the RANGE branch passes to RangeScreen>,
                            onSelectClub = ::selectClub,
                            onAddClub = { name, type, isTest ->
                                val ok = sessionRepository.addClub(name, type, isTest)
                                if (ok) selectClub(name.trim())   // spec §3: auto-active on add
                                ok
                            },
                            onComplete = ::completeFittingTest,
                            onSetExcluded = { ids, excluded ->
                                scope.launch { sessionRepository.setFittingShotsExcluded(ids, excluded) }
                            },
                        )
                    }
```
Resolve the two `<...>` placeholders by copying the actual argument expressions the RANGE branch passes to `RangeScreen` for BLE armed/info status (find `RangeScreen(` in AppRoot). Add imports for `FittingScreen`, `FittingController`, `FittingView`.

Kill/resume is FREE here by design: `fitActive`/`fitShots` flows rebuild from Room after process death; the active club persists via `ActiveClubStore`; `fittingController.sessionId` rebinds lazily on the next routed shot (`startFittingSession` is idempotent inside a transaction, so a double-start race collapses to one session).

- [ ] **Step 4: Build + run module tests**

```powershell
.\gradlew.bat --console=plain :app:compileDebugKotlin
.\gradlew.bat --console=plain :core:data:testDebugUnitTest
```
Expected: both green (placeholder compiles; routing compiles).

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): FIT tab wiring — routing, flows, resume, auto-start (M7)"
```

---

### Task 6: UI design pass (designer lane, blocks tasks 7–10)

**Files:**
- Create: `docs/superpowers/plans/2026-10-08-m7-club-fitting-design-notes.md`

Per repo convention ("no feature screen without a design pass grounded in the M3 design system"), run the design pass BEFORE building the screens. The orchestrator dispatches the **designer** specialist with this brief and commits its decisions:

- [ ] **Step 1: Dispatch designer with the brief**

Brief for @designer (working dir `C:\aaa\code\golf sim`):
1. Read `docs/superpowers/specs/2026-10-08-m7-club-fitting-design.md` (§3–§5) and `core/designsystem` components (`SectionCard`, `MetricChip`, `MetricRow`, `StatusStrip`, `NavRail`, `GolfColors` — note `Comparison.A–D` already exist for exactly this purpose).
2. Design, against the M3 dark design system, tablet landscape (1280×800-ish):
   - **COMPARE**: live `RangeLiveView` canvas + 210 dp right panel (ACTIVE CLUB chip, VIEW RESULTS button, HISTORY chip, LAST SHOT chips, COMPARISON card with carry/total toggle + per-club rows: colour dot / name / count badge / avg m).
   - **RESULTS**: header row (BACK chip, TABLE | TOP-DOWN segmented switch, END COMPARISON button), the metric table (frozen CLUB+n column, ~11 scrollable metric columns, delta rows dimmed below noise, expandable per-club drill-down with exclude ticks + "EXCLUDE LIKELY MISREADS"), and the top-down (legend chips + coloured dots + 1σ/2σ ovals).
   - **HISTORY**: simple list of completed comparisons (date + VIEW →).
3. Constraints: fixed seams (state, data flow, callbacks) per tasks 7–10; visuals/polish/spacing/hierarchy are designer-owned; reuse `GolfColors.Comparison.A/B/C/D` + two new colours for clubs 5–6; amber for low-sample badges; do not invent new interaction flows beyond the spec.
4. Write the decisions (layout, hierarchy, exact colours/typography per element, states) to `docs/superpowers/plans/2026-10-08-m7-club-fitting-design-notes.md`.

- [ ] **Step 2: Review + commit notes**

Orchestrator reviews the notes for spec compliance (copy edits allowed; visual intent is designer's). Then:

```bash
git add docs/superpowers/plans/2026-10-08-m7-club-fitting-design-notes.md
git commit -m "docs: M7 FIT UI design pass notes"
```

---

### Task 7: COMPARE view — live range + comparison card + ADD COMPARISON

**Files:**
- Create: `app/src/main/java/com/hpsmiles/golfsim/fitting/FittingColors.kt`
- Create: `app/src/main/java/com/hpsmiles/golfsim/fitting/FittingFormats.kt`
- Create: `app/src/main/java/com/hpsmiles/golfsim/fitting/FittingCompareView.kt`
- Modify: `app/src/main/java/com/hpsmiles/golfsim/fitting/FittingScreen.kt` (real dispatcher body)
- Modify: `app/src/main/java/com/hpsmiles/golfsim/range/ClubPickerOverlay.kt`
- Modify: `app/src/main/java/com/hpsmiles/golfsim/range/AddClubForm.kt`

Apply the visual decisions from `2026-10-08-m7-club-fitting-design-notes.md` (task 6). The code below is the structural baseline; designer-owned polish (spacing, exact chip styling) per the notes.

- [ ] **Step 1: FittingColors + FittingFormats**

`FittingColors.kt`:
```kotlin
package com.hpsmiles.golfsim.fitting

import androidx.compose.ui.graphics.Color
import com.hpsmiles.golfsim.core.designsystem.theme.GolfColors

/**
 * M7 club palette (spec §5): assigned by first-appearance order, distinct
 * from the range's teal history dots / amber last shot. Comparison.A–D
 * already exist in the design system for A/B testing; E/F extend to 6.
 */
object FittingColors {
    val CLUB: List<Color> = listOf(
        GolfColors.Comparison.A, GolfColors.Comparison.B, GolfColors.Comparison.C, GolfColors.Comparison.D,
        Color(0xFF6FE08F), Color(0xFFE08050),
    )

    fun clubColor(index: Int): Color = CLUB[index % CLUB.size]
}
```

NOTE: verify the `GolfColors` import path matches what `ClubPickerOverlay.kt` uses; if `GolfColors.Comparison` lives elsewhere, point the references there.

`FittingFormats.kt`:
```kotlin
package com.hpsmiles.golfsim.fitting

import java.util.Locale

/** Metric formatting for the FIT comparison card + table (M7 spec §4). */
object FittingFormats {
    const val MPH_PER_MS = 2.23694

    fun m1(v: Double) = String.format(Locale.US, "%.1f", v)

    fun mph(v: Double) = String.format(Locale.US, "%.1f", v * MPH_PER_MS)

    /** "140.2 ± 3.1" — σ shown only from ~0.1 m up. */
    fun avgSigma(avg: Double?, sigma: Double?): String = when {
        avg == null -> "-"
        sigma == null || sigma < 0.05 -> m1(avg)
        else -> String.format(Locale.US, "%.1f ± %.1f", avg, sigma)
    }

    fun deg(v: Double?) = if (v == null) "-" else String.format(Locale.US, "%.1f°", v)

    fun degSigned(v: Double?) = if (v == null) "-" else String.format(Locale.US, "%+.1f°", v)

    fun rpm(v: Double?) = if (v == null) "-" else String.format(Locale.US, "%.0f", v)

    fun smash(v: Double?) = if (v == null) "-" else String.format(Locale.US, "%.3f", v)
}
```

- [ ] **Step 2: Picker + form parameters**

In `ClubPickerOverlay.kt` add two defaulted params to the signature:
```kotlin
    addLabel: String = "＋ ADD",
    initialTest: Boolean = false,
```
Use `addLabel` for the add tile (replace the hardcoded `if (adding) "×" else "＋ ADD"` label's `"＋ ADD"` with `addLabel`) and pass `initialTest = initialTest` into the `AddClubForm(...)` call.

In `AddClubForm.kt` add `initialTest: Boolean = false` to the signature and change the TEST checkbox state initializer to `mutableStateOf(initialTest)` (find the existing `isTest` state declaration; change only its initializer). Everything else — submit handler, rejection handling — unchanged. This keeps RANGE behaviour identical (defaults) while FIT pre-checks TEST per spec §3.

- [ ] **Step 3: FittingCompareView**

```kotlin
package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.bag.ClubQualityGate
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.designsystem.theme.GolfColors
import com.hpsmiles.golfsim.core.designsystem.theme.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.theme.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.core.designsystem.MetricChip
import com.hpsmiles.golfsim.core.designsystem.StatusStrip
import com.hpsmiles.golfsim.range.ClubPickerOverlay
import com.hpsmiles.golfsim.range.DisplayShot
import com.hpsmiles.golfsim.range.RangeLiveView
import java.util.Locale

/**
 * FIT live view (spec §3): the extracted M6 range live view + a 210 dp
 * right panel — ACTIVE CLUB, VIEW RESULTS, LAST SHOT, and the COMPARISON
 * card (per-club colour dot / name / kept count / avg with carry-total
 * toggle). ADD COMPARISON pre-checks TEST and auto-activates the new club
 * (wired via onAddClub in AppRoot).
 */
@Composable
internal fun FittingCompareView(
    modifier: Modifier = Modifier,
    clubs: List<ClubRecord>,
    activeClubName: String?,
    sessionShots: List<com.hpsmiles.golfsim.core.data.entity.FittingShotEntity>,
    liveShots: List<DisplayShot>,
    armed: Boolean,
    info: String,
    distanceMode: FittingDistanceMode,
    onDistanceModeChange: (FittingDistanceMode) -> Unit,
    onSelectClub: (String?) -> Unit,
    onAddClub: suspend (String, ClubType, Boolean) -> Boolean,
    onViewResults: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    Row(modifier.fillMaxSize().background(GolfColors.Base)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            RangeLiveView(shots = liveShots, modifier = Modifier.fillMaxSize(), customGreen = null)
        }
        Column(
            Modifier
                .width(210.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .background(GolfColors.Panel)
                .padding(GolfSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
        ) {
            StatusStrip(armed = armed, info = info)
            SectionCard("ACTIVE CLUB") {
                Text(
                    text = activeClubName?.uppercase(Locale.US) ?: "SELECT CLUB",
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.Teal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickerOpen = true }
                        .border(1.dp, GolfColors.Teal, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                )
            }
            Text(
                "VIEW RESULTS",
                style = GolfTypography.MetricLabel,
                color = GolfColors.Teal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onViewResults)
                    .border(1.dp, GolfColors.Teal, RoundedCornerShape(50))
                    .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
            )
            Text(
                "HISTORY",
                style = GolfTypography.MetricLabel,
                color = GolfColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenHistory)
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                    .padding(horizontal = GolfSpacing.Lg, vertical = 4.dp),
            )
            SectionCard("LAST SHOT") {
                val shot = liveShots.lastOrNull()
                if (shot == null) {
                    Text("Fire a shot", style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
                } else {
                    val r = shot.shotResult
                    MetricChip("carry", String.format(Locale.US, "%.0f", r.carryM), "M")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip("total", String.format(Locale.US, "%.0f", r.totalM), "M")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip(
                        "ball",
                        String.format(Locale.US, "%.1f", shot.ballData.ballSpeed * FittingFormats.MPH_PER_MS),
                        "MPH",
                        accent = GolfColors.Amber,
                    )
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip(
                        "club",
                        String.format(Locale.US, "%.1f", shot.ballData.clubHeadSpeed * FittingFormats.MPH_PER_MS),
                        "MPH",
                    )
                }
            }
            SectionCard("COMPARISON") {
                Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                    FittingDistanceMode.entries.forEach { mode ->
                        Text(
                            mode.label,
                            style = GolfTypography.MetricLabel,
                            color = if (mode == distanceMode) GolfColors.Teal else GolfColors.TextMuted,
                            modifier = Modifier
                                .clickable { onDistanceModeChange(mode) }
                                .border(
                                    1.dp,
                                    if (mode == distanceMode) GolfColors.Teal else GolfColors.Line,
                                    RoundedCornerShape(50),
                                )
                                .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                        )
                    }
                }
                val order = FittingStats.clubOrder(sessionShots)
                if (order.isEmpty()) {
                    Text("Hit a shot to start the comparison", style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
                }
                order.forEachIndexed { index, key ->
                    val s = FittingStats.summarize(key, sessionShots)
                    val avg = when (distanceMode) {
                        FittingDistanceMode.CARRY -> s.carry
                        FittingDistanceMode.TOTAL -> s.total
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(FittingColors.clubColor(index)))
                        Text(
                            key.name,
                            style = GolfTypography.BodySmall,
                            color = GolfColors.TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${s.kept}",
                            style = GolfTypography.MetricLabel,
                            // spec §3: amber low-sample badge under the 5-kept target
                            color = if (s.kept < ClubQualityGate.TARGET_KEPT) GolfColors.Amber else GolfColors.TextSecondary,
                        )
                        Text(
                            avg?.let { FittingFormats.m1(it) + " m" } ?: "-",
                            style = GolfTypography.BodySmall,
                            color = GolfColors.TextPrimary,
                        )
                    }
                }
                if (order.size > 4) {
                    Text("Many clubs — 4 or fewer compares best", style = GolfTypography.BodySmall, color = GolfColors.Amber)
                }
            }
        }
    }
    if (pickerOpen) {
        ClubPickerOverlay(
            activeClubName = activeClubName,
            clubs = clubs,
            onDismiss = { pickerOpen = false },
            onSelectClub = onSelectClub,
            onAddClub = onAddClub,
            addLabel = "＋ ADD COMPARISON",
            initialTest = true,
        )
    }
}
```

NOTE: verify actual import paths: `SectionCard`/`MetricChip`/`StatusStrip` package, `GolfColors`/`GolfTypography`/`GolfSpacing` package, and `ClubQualityGate`'s package (`com.hpsmiles.golfsim.bag` per the repo) — copy the import lines used by `RangeScreen.kt`/`BagMappingResult.kt`. `RangeLiveView` is `internal` in the range package — same Gradle module (`:app`), so it is visible.

- [ ] **Step 4: Real FittingScreen dispatcher**

Replace the placeholder body of `FittingScreen` (keep the full signature) with:

```kotlin
    when (controller.view) {
        FittingView.COMPARE -> FittingCompareView(
            modifier = modifier,
            clubs = clubs,
            activeClubName = activeClubName,
            sessionShots = sessionShots,
            liveShots = liveShots,
            armed = armed,
            info = info,
            distanceMode = controller.distanceMode,
            onDistanceModeChange = { controller.distanceMode = it },
            onSelectClub = onSelectClub,
            onAddClub = onAddClub,
            onViewResults = { controller.view = FittingView.RESULTS },
            onOpenHistory = { controller.view = FittingView.HISTORY },
        )
        FittingView.RESULTS -> {
            val readOnly = controller.viewedSessionId != null
            FittingResultsView(
                modifier = modifier,
                shots = if (readOnly) viewedShots else sessionShots,
                readOnly = readOnly,
                controller = controller,
                onBack = {
                    if (controller.viewedSessionId != null) {
                        controller.viewedSessionId = null
                        controller.view = FittingView.HISTORY
                    } else {
                        controller.view = FittingView.COMPARE
                    }
                },
                onComplete = onComplete,
                onSetExcluded = onSetExcluded,
            )
        }
        FittingView.HISTORY -> FittingHistoryView(
            modifier = modifier,
            history = history,
            onOpen = { id ->
                controller.viewedSessionId = id
                controller.view = FittingView.RESULTS
            },
            onBack = { controller.view = FittingView.COMPARE },
        )
    }
```

This references `FittingResultsView`/`FittingHistoryView` (tasks 8/10) — to keep the build green after THIS task, create them now as minimal stubs with the exact signatures used above (bodies land in tasks 8/10):

```kotlin
// FittingResultsView.kt — stub, expanded in task 8
package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.designsystem.theme.GolfSpacing

@Composable
internal fun FittingResultsView(
    modifier: Modifier = Modifier,
    shots: List<FittingShotEntity>,
    readOnly: Boolean,
    controller: FittingController,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onSetExcluded: (List<Long>, Boolean) -> Unit,
) {
    Text("RESULTS — task 8", modifier = modifier.padding(GolfSpacing.Md))
}
```

```kotlin
// FittingHistoryView.kt — stub, expanded in task 10
package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import com.hpsmiles.golfsim.core.designsystem.theme.GolfSpacing

@Composable
internal fun FittingHistoryView(
    modifier: Modifier = Modifier,
    history: List<FittingSessionEntity>,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Text("HISTORY — task 10", modifier = modifier.padding(GolfSpacing.Md))
}
```


- [ ] **Step 5: Build + verify**

```powershell
.\gradlew.bat --console=plain :app:compileDebugKotlin
.\gradlew.bat --console=plain :app:testDebugUnitTest
```
Expected: green.

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "feat(app): FIT compare view — comparison card, ADD COMPARISON, auto-activate (M7)"
```

---

### Task 8: RESULTS table — metrics, deltas, drill-down, exclusion

**Files:**
- Modify: `app/src/main/java/com/hpsmiles/golfsim/fitting/FittingResultsView.kt` (real body)
- Create: `app/src/main/java/com/hpsmiles/golfsim/fitting/FittingTable.kt`

Apply designer decisions from the design notes (task 6). Structure baseline below.

- [ ] **Step 1: FittingResultsView (real body)**

```kotlin
package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.designsystem.theme.GolfColors
import com.hpsmiles.golfsim.core.designsystem.theme.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.theme.GolfTypography

/**
 * FIT results (spec §4): TABLE | TOP-DOWN segmented switch over the same
 * session's kept/excluded shots. `readOnly` = a completed history session:
 * exclusion controls disabled, END hidden.
 */
@Composable
internal fun FittingResultsView(
    modifier: Modifier = Modifier,
    shots: List<FittingShotEntity>,
    readOnly: Boolean,
    controller: FittingController,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onSetExcluded: (List<Long>, Boolean) -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(GolfColors.Base)
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            Text(
                "BACK",
                style = GolfTypography.MetricLabel,
                color = GolfColors.TextSecondary,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                    .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
            )
            FittingResultsMode.entries.forEach { mode ->
                val selected = controller.resultsMode == mode
                Text(
                    mode.label,
                    style = GolfTypography.MetricLabel,
                    color = if (selected) GolfColors.Teal else GolfColors.TextMuted,
                    modifier = Modifier
                        .clickable { controller.resultsMode = mode }
                        .border(
                            1.dp,
                            if (selected) GolfColors.Teal else GolfColors.Line,
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (!readOnly) {
                Text(
                    "END COMPARISON",
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.Amber,
                    modifier = Modifier
                        .clickable(onClick = onComplete)
                        .border(1.dp, GolfColors.Amber, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
                )
            }
        }
        if (controller.resultsMode == FittingResultsMode.TABLE) {
            FittingTable(
                shots = shots,
                baselineClubId = controller.baselineClubId,
                onBaselineChange = { controller.baselineClubId = it },
                onSetExcluded = onSetExcluded,
                readOnly = readOnly,
                modifier = Modifier.weight(1f),
            )
        } else {
            FittingTopDownPane(shots = shots, modifier = Modifier.weight(1f))
        }
    }
}
```

NOTE: `Modifier.weight` inside `Row`/`Column` scope — if the project's Compose version needs the scope-qualified import, keep `Modifier.weight(1f)` inline in the scope (no import needed) and drop the `weight` import.

- [ ] **Step 2: FittingTable**

```kotlin
package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.designsystem.theme.GolfColors
import com.hpsmiles.golfsim.core.designsystem.theme.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.theme.GolfTypography
import java.util.Locale

/**
 * M7 results table (spec §4): one row per club (frozen CLUB column + n,
 * horizontally scrollable metric columns), Δ rows per non-baseline club
 * (dimmed inside the noise band), and a tap-to-expand drill-down listing
 * every shot with per-shot exclusion. Excluded shots drop from every
 * aggregate — single source of truth: fitting_shots.excluded.
 */
@Composable
internal fun FittingTable(
    shots: List<FittingShotEntity>,
    baselineClubId: Long?,
    onBaselineChange: (Long) -> Unit,
    onSetExcluded: (List<Long>, Boolean) -> Unit,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val order = FittingStats.clubOrder(shots)
    val summaries = order.map { FittingStats.summarize(it, shots) }
    val baseline = summaries.firstOrNull { it.key.id == baselineClubId } ?: summaries.firstOrNull()
    var expandedId by remember { mutableStateOf<Long?>(null) }

    Column(modifier.verticalScroll(rememberScrollState())) {
        Row {
            // Frozen columns: CLUB + n
            Column(Modifier.width(150.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CLUB", style = GolfTypography.MetricLabel, color = GolfColors.TextMuted)
                    Text("n", style = GolfTypography.MetricLabel, color = GolfColors.TextMuted)
                }
            }
            // Scrollable metric columns
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                COLS.forEach { col ->
                    Text(
                        col.label,
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.TextMuted,
                        modifier = Modifier
                            .width(COL_WIDTH)
                            .padding(horizontal = 4.dp),
                    )
                }
            }
        }
        summaries.forEachIndexed { index, s ->
            val isBaseline = baseline != null && s.key.id == baseline.key.id
            val color = FittingColors.clubColor(index)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.width(150.dp).clickable {
                        expandedId = if (expandedId == s.key.id) null else s.key.id
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(
                        s.key.name,
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp).weight(1f),
                    )
                    if (summaries.size >= 3) {
                        Text(
                            "BASE",
                            style = GolfTypography.MetricLabel,
                            color = if (isBaseline) GolfColors.Teal else GolfColors.TextMuted,
                            modifier = Modifier
                                .clickable { onBaselineChange(s.key.id) }
                                .border(1.dp, if (isBaseline) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                                .padding(horizontal = 4.dp),
                        )
                    }
                    Text(
                        "${s.kept}",
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextSecondary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    COLS.forEach { col ->
                        Text(
                            col.club(s),
                            style = GolfTypography.BodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = GolfColors.TextPrimary,
                            modifier = Modifier.width(COL_WIDTH).padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
        // Δ rows: per non-baseline club vs the baseline (spec §4).
        if (baseline != null && summaries.size >= 2) {
            summaries.filter { it.key.id != baseline.key.id }.forEach { other ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Δ ${other.key.name}",
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.TextSecondary,
                        modifier = Modifier.width(150.dp).padding(horizontal = 4.dp),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        COLS.forEach { col ->
                            val verdict = FittingStats.delta(col.pick(other), col.pick(baseline), col.noise)
                            Text(
                                verdict?.let { col.deltaFmt(it.delta) } ?: "-",
                                style = GolfTypography.BodySmall.copy(fontFamily = FontFamily.Monospace),
                                // spec §4: within-noise deltas render dimmed
                                color = GolfColors.Teal,
                                modifier = Modifier
                                    .width(COL_WIDTH)
                                    .padding(horizontal = 4.dp)
                                    .alpha(if (verdict == null || !verdict.significant) 0.45f else 1f),
                            )
                        }
                    }
                }
            }
        }
        // Drill-down: every shot of the expanded club (kept normal, excluded greyed).
        expandedId?.let { id ->
            val clubShots = shots.filter { it.clubId == id }
            val kept = clubShots.count { !it.excluded }
            Text(
                "${kept} shots · ${clubShots.size - kept} excluded",
                style = GolfTypography.MetricLabel,
                color = GolfColors.TextSecondary,
                modifier = Modifier.padding(top = GolfSpacing.Sm),
            )
            if (!readOnly) {
                val duffIds = FittingStats.applyDuffFilter(clubShots).filter { it.filtered }.map { it.id }
                val unexcludedDuffs = clubShots.filter { it.id in duffIds && !it.excluded }.map { it.id }
                if (unexcludedDuffs.isNotEmpty()) {
                    Text(
                        "EXCLUDE LIKELY MISREADS (${unexcludedDuffs.size})",
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.Amber,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { onSetExcluded(unexcludedDuffs, true) }
                            .border(1.dp, GolfColors.Amber, RoundedCornerShape(50))
                            .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
                    )
                }
            }
            clubShots.forEachIndexed { i, s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .alpha(if (s.excluded) 0.4f else 1f),
                ) {
                    Text(
                        "#${i + 1}  ${FittingFormats.m1(s.carryM)}m/${FittingFormats.m1(s.totalM)}m  " +
                            "${FittingFormats.mph(s.ballSpeedMps)}mph  smash ${FittingFormats.smash(s.ballSpeedMps / s.clubHeadSpeedMps)}  " +
                            "off ${FittingFormats.m1(kotlin.math.abs(s.sideM))}m",
                        style = GolfTypography.BodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = GolfColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (!readOnly) {
                        Text(
                            if (s.excluded) "INCLUDE" else "EXCLUDE",
                            style = GolfTypography.MetricLabel,
                            color = if (s.excluded) GolfColors.TextSecondary else GolfColors.Amber,
                            modifier = Modifier
                                .clickable { onSetExcluded(listOf(s.id), !s.excluded) }
                                .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
        if (summaries.isEmpty()) {
            Text("No shots yet — hit a shot in COMPARE", style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
        }
    }
}

private const val COL_WIDTH = 84.dp

/** One metric column: label, kept-shot picker, formatter, delta formatter, noise band. */
private class Col(
    val label: String,
    val pick: (FittingStats.ClubSummary) -> Double?,
    val club: (FittingStats.ClubSummary) -> String,
    val deltaFmt: (Double) -> String,
    val noise: Double,
)

private val COLS = listOf(
    Col("CHS", { it.chs }, { s -> s.chs?.let(FittingFormats::mph) ?: "-" },
        { d -> String.format(Locale.US, "%+.1f", d * FittingFormats.MPH_PER_MS) }, FittingStats.NOISE_CHS_MPS),
    Col("BALL", { it.ballSpeed }, { s -> s.ballSpeed?.let(FittingFormats::mph) ?: "-" },
        { d -> String.format(Locale.US, "%+.1f", d * FittingFormats.MPH_PER_MS) }, FittingStats.NOISE_BALL_SPEED_MPS),
    Col("SMASH", { it.smash }, { s -> FittingFormats.smash(s.smash) },
        { d -> String.format(Locale.US, "%+.3f", d) }, FittingStats.NOISE_SMASH),
    Col("CARRY", { it.carry }, { s -> FittingFormats.avgSigma(s.carry, s.carrySigma) },
        { d -> String.format(Locale.US, "%+.1f m", d) }, FittingStats.NOISE_CARRY_M),
    Col("TOTAL", { it.total }, { s -> FittingFormats.avgSigma(s.total, s.totalSigma) },
        { d -> String.format(Locale.US, "%+.1f m", d) }, FittingStats.NOISE_TOTAL_M),
    Col("LAUNCH", { it.launch }, { s -> FittingFormats.deg(s.launch) },
        { d -> String.format(Locale.US, "%+.1f°", d) }, FittingStats.NOISE_LAUNCH_DEG),
    Col("DIR", { it.dir }, { s -> FittingFormats.degSigned(s.dir) },
        { d -> String.format(Locale.US, "%+.1f°", d) }, FittingStats.NOISE_DIR_DEG),
    Col("SPIN", { it.spin }, { s -> FittingFormats.rpm(s.spin) },
        { d -> String.format(Locale.US, "%+.0f", d) }, FittingStats.NOISE_SPIN_RPM),
    Col("AXIS", { it.spinAxis }, { s -> FittingFormats.degSigned(s.spinAxis) },
        { d -> String.format(Locale.US, "%+.1f°", d) }, FittingStats.NOISE_SPIN_AXIS_DEG),
    Col("OFF AVG", { it.offlineAvg }, { s -> s.offlineAvg?.let { FittingFormats.m1(it) + " m" } ?: "-" },
        { d -> String.format(Locale.US, "%+.1f m", d) }, FittingStats.NOISE_OFFLINE_M),
    Col("OFF WORST", { it.offlineWorst }, { s -> s.offlineWorst?.let { FittingFormats.m1(it) + " m" } ?: "-" },
        { d -> String.format(Locale.US, "%+.1f m", d) }, FittingStats.NOISE_OFFLINE_M),
)
```

NOTES:
- `private const val COL_WIDTH = 84.dp` — top-level `const` cannot hold `Dp`; use `private val COL_WIDTH: androidx.compose.ui.unit.Dp = 84.dp` instead if the compiler objects.
- Drill-down smash guard: if `clubHeadSpeedMps == 0.0`, print "-" instead of dividing — wrap as `if (s.clubHeadSpeedMps > 0.0) FittingFormats.smash(s.ballSpeedMps / s.clubHeadSpeedMps) else "-"`.
- Both scroll containers (frozen club column + metrics) share one outer `verticalScroll`; alignment is by equal row heights — do not introduce per-row variable heights here without also fixing the two-column alignment.

- [ ] **Step 3: Build + verify**

```powershell
.\gradlew.bat --console=plain :app:compileDebugKotlin
.\gradlew.bat --console=plain :app:testDebugUnitTest
```
Expected: green.

- [ ] **Step 4: Commit**

```bash
git add app
git commit -m "feat(app): fitting results table — deltas, drill-down, exclusion (M7)"
```

---

### Task 9: RESULTS top-down — coloured dots, legend, dispersion ovals

**Files:**
- Create: `app/src/main/java/com/hpsmiles/golfsim/fitting/FittingTopDown.kt`

Apply designer decisions from the design notes (task 6).

- [ ] **Step 1: FittingTopDown**

```kotlin
package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.designsystem.theme.GolfColors
import com.hpsmiles.golfsim.core.designsystem.theme.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.theme.GolfTypography
import com.hpsmiles.golfsim.range.RangeScene

/**
 * M7 top-down (spec §5): kept shots as per-club coloured dots over the same
 * world mapping as TopDownCanvas (x = side, y = REST position = total),
 * with 1σ and 2σ dispersion ovals per club (≥3 kept shots). Excluded shots
 * are not drawn — they stay visible in the table drill-down.
 */
@Composable
internal fun FittingTopDownPane(
    shots: List<FittingShotEntity>,
    modifier: Modifier = Modifier,
) {
    val order = FittingStats.clubOrder(shots)
    Column(modifier.fillMaxSize()) {
        // Legend: colour → club (same first-appearance colours as the card/table).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = GolfSpacing.Md, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            order.forEachIndexed { index, key ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(FittingColors.clubColor(index)))
                    Text(key.name, style = GolfTypography.MetricLabel, color = GolfColors.TextPrimary, maxLines = 1)
                }
            }
        }
        if (order.size > 4) {
            Text(
                "Many clubs — 4 or fewer compares best",
                style = GolfTypography.BodySmall,
                color = GolfColors.Amber,
                modifier = Modifier.padding(horizontal = GolfSpacing.Md),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Same world mapping as TopDownCanvas: origin bottom-centre,
            // x lateral (side), y 0..GROUND_END_Y, full ground extent fitted.
            val endM = RangeScene.GROUND_END_Y.toFloat()
            val pxPerM = h / endM
            val originX = w / 2f
            val originY = h - 8.sp.toPx()

            // 50 m grid + distance labels.
            for (d in 0..endM.toInt() step 50) {
                val y = originY - d * pxPerM
                drawLine(GolfColors.Line.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }
            drawIntoCanvas { c ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 10.sp.toPx()
                    isAntiAlias = true
                }
                for (d in 50..endM.toInt() step 50) {
                    val y = originY - d * pxPerM
                    c.nativeCanvas.drawText("${d}m", 8f, y - 4f, paint)
                }
            }

            // Dispersion ovals FIRST (rings under dots): 2σ strong, 1σ faint.
            order.forEachIndexed { index, key ->
                val kept = shots.filter { it.clubId == key.id && !it.excluded }
                val oval = DispersionOval.compute(kept.map { it.sideM to it.totalM }) ?: return@forEachIndexed
                val color = FittingColors.clubColor(index)
                listOf(2.0 to 0.9f, 1.0 to 0.45f).forEach { (scale, alpha) ->
                    val pts = DispersionOval.polygon(oval, scale)
                    val path = Path()
                    pts.forEachIndexed { j, (x, y) ->
                        val px = originX + (x * pxPerM).toFloat()
                        val py = originY - (y * pxPerM).toFloat()
                        if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    drawPath(path, color.copy(alpha = alpha), style = Stroke(width = 1.5.dp.toPx()))
                }
            }

            // Kept shot dots, coloured per club.
            order.forEachIndexed { index, key ->
                val color = FittingColors.clubColor(index)
                shots.filter { it.clubId == key.id && !it.excluded }.forEach { s ->
                    val x = originX + (s.sideM * pxPerM).toFloat()
                    val y = originY - (s.totalM * pxPerM).toFloat()
                    drawCircle(color.copy(alpha = 0.8f), radius = 3.sp.toPx(), center = Offset(x, y))
                }
            }
        }
    }
}
```

NOTES:
- `RangeScene.GROUND_END_Y` — copy the exact access used by `TopDownCanvas.kt:40-44` (`h / RangeScene.GROUND_END_Y.toFloat()`); adapt the `toInt()` if it is already an `Int`.
- If `GolfColors.Line` is not exposed at that name, use whatever `TopDownCanvas` uses for grid lines.
- Verify `import com.hpsmiles.golfsim.range.RangeScene` matches the real package.

- [ ] **Step 2: Build + verify**

```powershell
.\gradlew.bat --console=plain :app:compileDebugKotlin
.\gradlew.bat --console=plain :app:testDebugUnitTest
```
Expected: green.

- [ ] **Step 3: Commit**

```bash
git add app
git commit -m "feat(app): fitting top-down — per-club colours + dispersion ovals (M7)"
```

---

### Task 10: HISTORY + read-only results

**Files:**
- Modify: `app/src/main/java/com/hpsmiles/golfsim/fitting/FittingHistoryView.kt` (real body)

- [ ] **Step 1: FittingHistoryView (real body)**

```kotlin
package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import com.hpsmiles.golfsim.core.designsystem.theme.GolfColors
import com.hpsmiles.golfsim.core.designsystem.theme.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.theme.GolfTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIT history (spec §6): completed comparisons, newest first; tapping opens
 * the session read-only in RESULTS (viewedSessionId seam in FittingScreen).
 */
@Composable
internal fun FittingHistoryView(
    modifier: Modifier = Modifier,
    history: List<FittingSessionEntity>,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val dateFmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.US)
    Column(
        modifier
            .fillMaxSize()
            .background(GolfColors.Base)
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            Text(
                "BACK",
                style = GolfTypography.MetricLabel,
                color = GolfColors.TextSecondary,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                    .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
            )
            Text("FITTING HISTORY", style = GolfTypography.MetricLabel, color = GolfColors.TextPrimary)
        }
        if (history.isEmpty()) {
            Text("No completed comparisons yet", style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(history, key = { it.id }) { session ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(session.id) }
                        .padding(vertical = GolfSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        dateFmt.format(Date(session.completedAtMs ?: session.startedAtMs)),
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text("VIEW →", style = GolfTypography.MetricLabel, color = GolfColors.Teal)
                }
            }
        }
    }
}
```

NOTE: if the design-system has a row/list idiom (bag history uses one), mirror it instead of a bare `LazyColumn` — check `BagMappingResult`'s HISTORY section and reuse its row styling. Drop the `weight` import if scope-qualified.

- [ ] **Step 2: Build + verify**

```powershell
.\gradlew.bat --console=plain :app:compileDebugKotlin
.\gradlew.bat --console=plain :app:testDebugUnitTest
```
Expected: green.

- [ ] **Step 3: Commit**

```bash
git add app
git commit -m "feat(app): fitting history + read-only results (M7)"
```

---

### Task 11: Full build, all tests, conventions, device verification

**Files:**
- Modify: `AGENTS.md` (conventions paragraph)

- [ ] **Step 1: AGENTS.md conventions**

Append to the bag-mapping bullet area in "Conventions for This Repo" (after the bag mapping paragraph):

```markdown
- Club fitting follows the bag pattern: `app/.../fitting/` holds plain Compose state holders + painters; FIT shots never enter range sessions — each is write-through persisted to `fitting_sessions`/`fitting_shots` (Room v5) with a per-shot club snapshot (survives the temp-club purge). Exclusion is one source of truth (`FittingShotEntity.excluded`): it drives table aggregates, deltas, top-down dots and dispersion ovals simultaneously. The comparison club set derives from shot snapshots in first-appearance order — never a separate persisted list.
```

- [ ] **Step 2: Whole build + every module test**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain build
```
Expected: `BUILD SUCCESSFUL` — assemble + all unit tests across `:core:ble`, `:core:data`, `:core:physics`, `:app`. Fix any regression before proceeding (gradle flags precede task names).

- [ ] **Step 3: Commit conventions**

```bash
git add AGENTS.md
git commit -m "docs: club fitting repo conventions (M7)"
```

- [ ] **Step 4: Device verification (Lenovo tablet, `installDebug`)**

```powershell
.\gradlew.bat --console=plain :app:installDebug
```
Manual checklist on device (tick each):
1. FIT tab appears between BAG and SETTINGS; range/games/bag behave unchanged.
2. COMPARE: pick a bag club → hit → LAST SHOT updates, COMPARISON card gains a row (count + avg).
3. ADD COMPARISON → form opens with TEST pre-checked → name "Shaft A" → submit → ACTIVE CLUB auto-switches to Shaft A.
4. Hit shots with 2+ clubs; toggle CARRY/TOTAL in the card — averages switch.
5. VIEW RESULTS → TABLE: one row per club; CHS/BALL/SMASH/CARRY±σ/TOTAL±σ/LAUNCH/DIR/SPIN/AXIS/OFF columns render; Δ row appears for the second club.
6. Tap club row → drill-down; EXCLUDE a shot → its row greys, "n excluded" count updates, club averages/deltas update, comparison card updates.
7. EXCLUDE LIKELY MISREADS appears only when a duff pattern exists; tapping excludes those shots.
8. TOP-DOWN: dots coloured per club matching legend; ovals appear once a club has ≥3 kept shots; excluded shots are not drawn.
9. END COMPARISON → back to fresh COMPARE; HISTORY lists the comparison; tapping opens it read-only; exclusion controls disabled there.
10. Kill the app mid-comparison → relaunch → FIT shows the same session with all shots (kill/resume); active club restored.
11. Hit a shot in RANGE after fitting — range session/history unaffected by fitting shots; END SESSION purges TEST clubs and fitting history keeps names/types.
12. σ/delta sanity: with one club, no Δ row; with 3 clubs, BASE chips select the baseline.

- [ ] **Step 5: Final review + push**

Run a whole-feature review against the spec (`docs/superpowers/specs/2026-10-08-m7-club-fitting-design.md`), confirm §8 out-of-scope items were NOT built, then push the branch:

```bash
git push -u origin m7-club-fitting
```

- [ ] **Step 6: Roadmap note (only after device verification passes)**

Update `ROADMAP.md` M7 section to **Shipped** with the date + merge commit, in the same style as M6's entry.

<!-- PLAN-COMPLETE -->






