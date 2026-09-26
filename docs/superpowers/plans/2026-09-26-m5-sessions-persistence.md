# M5 Sessions & Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist every accepted shot into Room with club tagging, session auto-start/explicit-end/restore, and a past-session review screen — sessions survive app restart.

**Architecture:** New Android-library module `core/data` (Room + KSP, Robolectric-tested `SessionRepository` facade). Raw `BallData` is the source of truth with cached physics scalars written once at insert; trajectories are never stored. `:app` wires the repository behind the existing main-thread-mediated shot flow and adds the HISTORY tab, club pill/picker, bag editor, and re-tag interaction.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.1 built-in Kotlin, Room 2.8.5 (2.x line, `androidx.room` plugin), KSP 2.3.12 (KSP2 only — never kapt), Robolectric 4.17, kotlinx-coroutines-test 1.11.0, Jetpack Compose (existing design system).

**Spec:** `docs/superpowers/specs/2026-09-26-m5-sessions-persistence-design.md` (all decisions D1–D8 live there).

## Global Constraints

- Windows Gradle needs JDK first, EVERY command: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` (PowerShell 5.1; `rg` is NOT installed — use `Select-String` if shell search is needed).
- Build everything: `.\gradlew.bat build` · all tests: `.\gradlew.bat test` · one module: `.\gradlew.bat :core:data:test` · install: `.\gradlew.bat :app:installDebug`.
- Module conventions: Android libraries use `alias(libs.plugins.golf.android.library)` (convention applies the Compose compiler, so non-Compose consumers ALSO declare `implementation(platform(libs.compose.bom))` + `implementation(libs.compose.ui)` — mirror `core/connect/build.gradle.kts`). Namespace pattern `com.hpsmiles.golfsim.core.data`.
- **Kapt is forbidden anywhere** (incompatible with AGP 9 built-in Kotlin). KSP only; there is no `ksp.useKSP2` flag to set.
- Room is the **2.x line**: artifacts `androidx.room:room-*`, plugin id `androidx.room` (NOT `androidx.room3` / `androidx.room3:room3-*`).
- Verified versions (2026-09-26, lib-3 research, all cited): `ksp = "2.3.12"`, `room = "2.8.5"`, `robolectric = "4.17"`, `kotlinxCoroutines = "1.11.0"`.
- UI uses ONLY the M3 design system (`GolfColors`, `GolfTypography`, `GolfSpacing`, `SectionCard`, `MetricRow`, `MetricChip`, `NavRail`/`NavRailButton`, `StatusStrip`). Landscape tablet, minSdk 31, compileSdk 37.
- Physics stays pure/deterministic; no physics-module code is modified.
- No network/cloud anything; single-device constraint is hard.
- Commit style: conventional commits matching repo history, e.g. `feat(data): ...`, `test(data): ...`, `feat(range): ...` — one commit per task.
- Robolectric tests annotate `@Config(sdk = [31])` (minSdk parity) and get context via `RuntimeEnvironment.getApplication()` (no androidx.test dependency).
- Room schema JSONs in `core/data/schemas/` are committed (exportSchema = true baseline for future migrations).

---

### Task 1: Version catalog + `core/data` module skeleton

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Create: `core/data/build.gradle.kts`

**Interfaces:**
- Consumes: nothing new.
- Produces: empty `:core:data` Android library that builds; catalog aliases `libs.plugins.ksp`, `libs.plugins.androidx.room`, `libs.room.runtime`, `libs.room.ktx`, `libs.room.compiler`, `libs.robolectric`, `libs.kotlinx.coroutines.test` used by later tasks.

- [ ] **Step 1: Add catalog entries**

In `gradle/libs.versions.toml`, extend the three tables (keep existing entries untouched):

```toml
[versions]
# existing: agp, kotlin, composeBom, activityCompose, junit — unchanged; append:
ksp = "2.3.12"
room = "2.8.5"
robolectric = "4.17"
kotlinxCoroutines = "1.11.0"

[libraries]
# existing entries unchanged; append:
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }

[plugins]
# existing entries unchanged; append:
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidx-room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 2: Register the module**

In `settings.gradle.kts`, after `include(":core:designsystem")`, add:

```kotlin
include(":core:data")
```

- [ ] **Step 3: Create the module build file**

Create `core/data/build.gradle.kts`:

```kotlin
// core/data/build.gradle.kts
plugins {
    alias(libs.plugins.golf.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.hpsmiles.golfsim.core.data"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Robolectric on JDK 17 wants headroom; tasks.withType is DSL-stable across AGP versions.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
}

dependencies {
    implementation(project(":core:ble"))
    implementation(project(":core:physics"))
    // Non-Compose module under golf-android-library (convention applies the
    // Compose compiler), so the runtime must still reach the compiler —
    // mirrors :core:connect's proven implementation-scoped BOM+ui pattern.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

Note: if Robolectric later complains on this JDK, add to the same `tasks.withType<Test>` block: `jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")`. The module has no sources yet — Room/KSP run lazily once entities exist.

- [ ] **Step 4: Verify the skeleton builds**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:assemble`
Expected: `BUILD SUCCESSFUL` (first run downloads Room 2.8.5 + KSP 2.3.12 from google()/mavenCentral()).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts core/data/build.gradle.kts
git commit -m "build(data): add core/data module with Room + KSP wiring"
```

---
### Task 2: Entities, records, `ShotSource`, auto-titles (pure Kotlin, TDD)

**Files:**
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/SessionEntity.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/ShotEntity.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/ClubEntity.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/record/ShotSource.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/record/Records.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/record/SessionSummary.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/record/SessionTitles.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/record/Mappers.kt`
- Test: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/RecordsTest.kt`

**Interfaces:**
- Consumes: `com.hpsmiles.golfsim.core.ble.BallData(clubHeadSpeed, ballSpeed, launchDirection, launchAngle, spinAxis — all Double; totalSpin: Int; unknown1: Int; unknown2: Int)` and `com.hpsmiles.golfsim.core.physics.ShotResult(carryM, rolloutM, totalM, sideM, apexM, flightTimeSec — Double, samples = emptyList())`.
- Produces (later tasks rely on exactly these names):
  - `enum class ShotSource(val code: Int) { LIVE(0), DEMO(1) }`
  - `data class ShotRecord(id: Long, sessionId: Long, seq: Int, timestampMs: Long, source: ShotSource, clubName: String?, ballData: BallData, carryM: Double, totalM: Double, sideM: Double, apexM: Double, flightTimeSec: Double)`
  - `data class ClubRecord(id: Long, name: String)`
  - `data class RestoredSession(sessionId: Long, misreadCount: Int, shots: List<ShotRecord>)`
  - `data class SessionSummaryRow(    id: Long, startedAtEpochMs: Long, endedAtEpochMs: Long?, title: String?, misreadCount: Int, shotCount: Int, liveCount: Int, demoCount: Int, liveAvgCarryM: Double?, liveMaxCarryM: Double?, allAvgCarryM: Double?, allMaxCarryM: Double?, liveClubsCsv: String?, allClubsCsv: String?)`
  - `data class SessionSummary(id: Long, startedAtEpochMs: Long, endedAtEpochMs: Long?, title: String?, misreadCount: Int, shotCount: Int, avgCarryM: Double?, maxCarryM: Double?, clubNames: List<String>, hasDemoShot: Boolean, isOpen: Boolean)`
  - `fun SessionSummaryRow.toSummary(liveOnly: Boolean): SessionSummary`
  - `fun makeShotEntity(sessionId: Long, seq: Int, timestampMs: Long, source: ShotSource, clubName: String?, ballData: BallData, result: ShotResult): ShotEntity`
  - `fun ShotEntity.toRecord(): ShotRecord` and `fun ShotEntity.toBallData(): BallData`
  - `object SessionTitles { fun auto(startedAtEpochMs: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String }`

- [ ] **Step 1: Write the failing tests**

Create `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/RecordsTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.record.SessionSummaryRow
import com.hpsmiles.golfsim.core.data.record.SessionTitles
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.data.record.makeShotEntity
import com.hpsmiles.golfsim.core.data.record.toBallData
import com.hpsmiles.golfsim.core.data.record.toRecord
import com.hpsmiles.golfsim.core.data.record.toSummary
import com.hpsmiles.golfsim.core.physics.ShotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RecordsTest {

    private val ball = BallData(
        clubHeadSpeed = 33.4, ballSpeed = 48.2, launchDirection = -1.7,
        launchAngle = 12.5, spinAxis = -4.0, totalSpin = 8100,
        unknown1 = 5, unknown2 = 10,
    )
    private val result = ShotResult(
        carryM = 141.2, rolloutM = 9.1, totalM = 150.3, sideM = -3.4,
        apexM = 27.6, flightTimeSec = 6.1,
    )

    private fun entity() = makeShotEntity(
        sessionId = 7, seq = 3, timestampMs = 1_000L,
        source = ShotSource.LIVE, clubName = null, ballData = ball, result = result,
    )

    @Test
    fun `shot entity round trips through record`() {
        val record = entity().toRecord()
        assertEquals(ball, record.ballData)
        assertEquals(141.2, record.carryM, 1e-9)
        assertEquals(ShotSource.LIVE, record.source)
        assertNull(record.clubName)
        assertEquals(3, record.seq)
        assertEquals(7L, record.sessionId)
    }

    @Test
    fun `makeShotEntity preserves raw unknowns and scalars`() {
        val e = makeShotEntity(
            sessionId = 7, seq = 3, timestampMs = 1_000L,
            source = ShotSource.LIVE, clubName = "7i", ballData = ball, result = result,
        )
        assertEquals(5, e.unknown1)
        assertEquals(10, e.unknown2)
        assertEquals(150.3, e.totalM, 1e-9)
        assertEquals(33.4, e.clubHeadSpeedMps, 1e-9)
        assertEquals(8100, e.spinRpm)
        assertEquals(-1.7, e.launchDirDeg, 1e-9)
    }

    @Test
    fun `entity exposes BallData view`() {
        assertEquals(ball, entity().toBallData())
    }

    @Test
    fun `summary row maps live-only and all-shots views`() {
        val row = SessionSummaryRow(
            id = 1, startedAtEpochMs = 2L, endedAtEpochMs = 3L, title = null, misreadCount = 2,
            shotCount = 4, liveCount = 3, demoCount = 1,
            liveAvgCarryM = 100.0, liveMaxCarryM = 120.0,
            allAvgCarryM = 95.0, allMaxCarryM = 120.0,
            liveClubsCsv = "7i, 8i", allClubsCsv = "7i, 8i, DEMO-CLUB",
        )
        val live = row.toSummary(liveOnly = true)
        assertEquals(3, live.shotCount)
        assertEquals(listOf("7i", "8i"), live.clubNames)
        assertTrue(live.hasDemoShot)
        assertFalse(live.isOpen)
        val all = row.toSummary(liveOnly = false)
        assertEquals(4, all.shotCount)
        assertEquals(95.0, all.avgCarryM!!, 1e-9)
        assertEquals(listOf("7i", "8i", "DEMO-CLUB"), all.clubNames)
    }

    @Test
    fun `summary row open session and nulls survive`() {
        val row = SessionSummaryRow(
            id = 1, startedAtEpochMs = 2L, endedAtEpochMs = null, title = "named", misreadCount = 0,
            shotCount = 0, liveCount = 0, demoCount = 0,
            liveAvgCarryM = null, liveMaxCarryM = null,
            allAvgCarryM = null, allMaxCarryM = null,
            liveClubsCsv = null, allClubsCsv = null,
        )
        val s = row.toSummary(liveOnly = true)
        assertTrue(s.isOpen)
        assertNull(s.avgCarryM)
        assertEquals(emptyList<String>(), s.clubNames)
        assertFalse(s.hasDemoShot)
    }

    @Test
    fun `auto title formats from start time`() {
        val title = SessionTitles.auto(1_795_000_000_000L, locale = Locale.US)
        // 2026-06-19 ~02:26 UTC. Shape only (day varies with zone): "Wed 17 Jun · 02:26"-like.
        assertTrue(title.matches(Regex("[A-Z][a-z]{2} \\d{1,2} [A-Z][a-z]{2} \u00b7 \\d{2}:\\d{2}")))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:test`
Expected: COMPILATION FAIL — `unresolved reference` on `record.makeShotEntity` etc. (nothing exists yet).

---
- [ ] **Step 3: Implement entities, records, mappers**

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/SessionEntity.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One practice session. `endedAtEpochMs == null` means open (max one ever). */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val title: String? = null,
    val misreadCount: Int = 0,
)
```

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/ClubEntity.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The editable bag. Shot rows snapshot names; renaming never rewrites history. */
@Entity(tableName = "clubs", indices = [Index(value = ["name"], unique = true)])
data class ClubEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
)
```

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/ShotEntity.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One accepted shot. Raw block = source of truth (BallData field-for-field,
 * unknowns preserved for the offsets-12–15 distance experiment); scalar
 * block = physics cache written once at capture, never recomputed for lists.
 */
@Entity(
    tableName = "shots",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class ShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val seq: Int,
    val timestampMs: Long,
    val source: Int,               // ShotSource.code
    val clubName: String?,         // null = untagged
    // raw block
    val clubHeadSpeedMps: Double,
    val ballSpeedMps: Double,
    val launchDirDeg: Double,
    val launchAngleDeg: Double,
    val spinAxisDeg: Double,
    val spinRpm: Int,
    val unknown1: Int,
    val unknown2: Int,
    // cached scalars
    val carryM: Double,
    val totalM: Double,
    val sideM: Double,
    val apexM: Double,
    val flightTimeSec: Double,
)
```

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/record/ShotSource.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.record

/** Origin of the shot data — a BLE callback is LIVE even if the demo toggle is on. */
enum class ShotSource(val code: Int) { LIVE(0), DEMO(1) }
```

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/record/Records.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.record

import com.hpsmiles.golfsim.core.ble.BallData

/** What the repository exposes for one persisted shot. */
data class ShotRecord(
    val id: Long,
    val sessionId: Long,
    val seq: Int,
    val timestampMs: Long,
    val source: ShotSource,
    val clubName: String?,
    val ballData: BallData,
    val carryM: Double,
    val totalM: Double,
    val sideM: Double,
    val apexM: Double,
    val flightTimeSec: Double,
)

/** One bag entry as UI sees it. */
data class ClubRecord(val id: Long, val name: String)

/** The open session plus its shots handed back at app launch. */
data class RestoredSession(
    val sessionId: Long,
    val misreadCount: Int,
    val shots: List<ShotRecord>,
)
```

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/record/SessionSummary.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.record

/**
 * Aggregate-query POJO (all shots; live and demo slices side by side).
 * Mapped to [SessionSummary] by [toSummary] according to the live-only filter.
 */
data class SessionSummaryRow(
    val id: Long,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val title: String?,
    val misreadCount: Int,
    val shotCount: Int,          // ALL shots incl. demo
    val liveCount: Int,
    val demoCount: Int,
    val liveAvgCarryM: Double?,
    val liveMaxCarryM: Double?,
    val allAvgCarryM: Double?,
    val allMaxCarryM: Double?,
    val liveClubsCsv: String?,
    val allClubsCsv: String?,
)

data class SessionSummary(
    val id: Long,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val title: String?,
    val misreadCount: Int,
    val shotCount: Int,
    val avgCarryM: Double?,
    val maxCarryM: Double?,
    val clubNames: List<String>,
    val hasDemoShot: Boolean,
    val isOpen: Boolean,
)

fun SessionSummaryRow.toSummary(liveOnly: Boolean): SessionSummary = SessionSummary(
    id = id,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    title = title,
    misreadCount = misreadCount,
    shotCount = if (liveOnly) liveCount else shotCount,
    avgCarryM = if (liveOnly) liveAvgCarryM else allAvgCarryM,
    maxCarryM = if (liveOnly) liveMaxCarryM else allMaxCarryM,
    clubNames = (if (liveOnly) liveClubsCsv else allClubsCsv)
        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
    hasDemoShot = demoCount > 0,
    isOpen = endedAtEpochMs == null,
)
```

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/record/SessionTitles.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.record

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Auto-title for sessions without a custom label: "Mon 21 Sep · 16:10". */
object SessionTitles {
    fun auto(
        startedAtEpochMs: Long,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM \u00b7 HH:mm", locale)
        return fmt.format(Instant.ofEpochMilli(startedAtEpochMs).atZone(zone))
    }
}
```

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/record/Mappers.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.record

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.ShotEntity
import com.hpsmiles.golfsim.core.physics.ShotResult

/** Raw-block view of a stored shot (source of truth, unknowns included). */
fun ShotEntity.toBallData(): BallData = BallData(
    clubHeadSpeed = clubHeadSpeedMps,
    ballSpeed = ballSpeedMps,
    launchDirection = launchDirDeg,
    launchAngle = launchAngleDeg,
    spinAxis = spinAxisDeg,
    totalSpin = spinRpm,
    unknown1 = unknown1,
    unknown2 = unknown2,
)

fun ShotEntity.toRecord(): ShotRecord = ShotRecord(
    id = id, sessionId = sessionId, seq = seq, timestampMs = timestampMs,
    source = if (source == ShotSource.DEMO.code) ShotSource.DEMO else ShotSource.LIVE,
    clubName = clubName, ballData = toBallData(),
    carryM = carryM, totalM = totalM, sideM = sideM,
    apexM = apexM, flightTimeSec = flightTimeSec,
)

/**
 * Insert-ready entity: raw BallData (source of truth) + the physics scalars
 * cached at capture time (hybrid storage decision D8). rolloutM is derived
 * on restore (totalM - carryM) and never persisted.
 */
fun makeShotEntity(
    sessionId: Long,
    seq: Int,
    timestampMs: Long,
    source: ShotSource,
    clubName: String?,
    ballData: BallData,
    result: ShotResult,
): ShotEntity = ShotEntity(
    sessionId = sessionId, seq = seq, timestampMs = timestampMs,
    source = source.code, clubName = clubName,
    clubHeadSpeedMps = ballData.clubHeadSpeed, ballSpeedMps = ballData.ballSpeed,
    launchDirDeg = ballData.launchDirection, launchAngleDeg = ballData.launchAngle,
    spinAxisDeg = ballData.spinAxis, spinRpm = ballData.totalSpin,
    unknown1 = ballData.unknown1, unknown2 = ballData.unknown2,
    carryM = result.carryM, totalM = result.totalM, sideM = result.sideM,
    apexM = result.apexM, flightTimeSec = result.flightTimeSec,
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:test`
Expected: `BUILD SUCCESSFUL`, 6 tests passing (all in `RecordsTest`).

- [ ] **Step 5: Commit**

```bash
git add core/data/src
git commit -m "feat(data): shot/session entities, records and mappers"
```

---
### Task 3: DAOs + `Mlm2proDatabase` (Robolectric TDD)

**Files:**
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/SessionDao.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/ShotDao.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/ClubDao.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabase.kt`
- Test: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabaseTest.kt`

**Interfaces:**
- Consumes: Task 2 entities + `record.SessionSummaryRow`.
- Produces:
  - `class Mlm2proDatabase : RoomDatabase` — `sessionDao(): SessionDao`, `shotDao(): ShotDao`, `clubDao(): ClubDao`; version 1, `exportSchema = true`.
  - `SessionDao`: `suspend insert(session: SessionEntity): Long` · `suspend findOpen(): SessionEntity?` · `fun observeOpen(): Flow<SessionEntity?>` · `suspend end(id: Long, endedAtEpochMs: Long)` · `suspend rename(id: Long, title: String?)` · `suspend incrementMisread(id: Long)` · `fun observeSummaries(): Flow<List<SessionSummaryRow>>` · `suspend deleteAll()`.
  - `ShotDao`: `suspend insert(shot: ShotEntity): Long` · `fun observeShots(sessionId: Long): Flow<List<ShotEntity>>` · `suspend shotsForSession(sessionId: Long): List<ShotEntity>` · `suspend countForSession(sessionId: Long): Int` · `suspend retagShotIds(ids: List<Long>, clubName: String?)` · `suspend deleteAll()`.
  - `ClubDao`: `suspend insert(club: ClubEntity): Long` · `fun observeClubs(): Flow<List<ClubEntity>>` · `suspend count(): Int` · `suspend findByName(name: String): ClubEntity?` · `suspend maxSortOrder(): Int` · `suspend rename(id: Long, name: String)` · `suspend delete(id: Long)` · `suspend deleteAll()`.

- [ ] **Step 1: Write the failing Robolectric tests**

Create `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabaseTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:test`
Expected: COMPILATION FAIL — `Mlm2proDatabase`/DAOs unresolved. First Robolectric run downloads android-all jars (network, once).

---
- [ ] **Step 3: Implement the DAOs and database**

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabase.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hpsmiles.golfsim.core.data.dao.ClubDao
import com.hpsmiles.golfsim.core.data.dao.SessionDao
import com.hpsmiles.golfsim.core.data.dao.ShotDao
import com.hpsmiles.golfsim.core.data.entity.ClubEntity
import com.hpsmiles.golfsim.core.data.entity.SessionEntity
import com.hpsmiles.golfsim.core.data.entity.ShotEntity

@Database(
    entities = [SessionEntity::class, ShotEntity::class, ClubEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class Mlm2proDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun shotDao(): ShotDao
    abstract fun clubDao(): ClubDao
}
```

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/SessionDao.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.SessionEntity
import com.hpsmiles.golfsim.core.data.record.SessionSummaryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE endedAtEpochMs IS NULL ORDER BY startedAtEpochMs DESC LIMIT 1")
    suspend fun findOpen(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE endedAtEpochMs IS NULL ORDER BY startedAtEpochMs DESC LIMIT 1")
    fun observeOpen(): Flow<SessionEntity?>

    @Query("UPDATE sessions SET endedAtEpochMs = :endedAtEpochMs WHERE id = :id")
    suspend fun end(id: Long, endedAtEpochMs: Long)

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String?)

    @Query("UPDATE sessions SET misreadCount = misreadCount + 1 WHERE id = :id")
    suspend fun incrementMisread(id: Long)

    @Query(
        """
        SELECT s.id AS id, s.startedAtEpochMs AS startedAtEpochMs,
               s.endedAtEpochMs AS endedAtEpochMs, s.title AS title,
               s.misreadCount AS misreadCount,
               COUNT(sh.id) AS shotCount,
               COALESCE(SUM(CASE WHEN sh.source = 0 THEN 1 ELSE 0 END), 0) AS liveCount,
               COALESCE(SUM(CASE WHEN sh.source = 1 THEN 1 ELSE 0 END), 0) AS demoCount,
               AVG(CASE WHEN sh.source = 0 THEN sh.carryM END) AS liveAvgCarryM,
               MAX(CASE WHEN sh.source = 0 THEN sh.carryM END) AS liveMaxCarryM,
               AVG(sh.carryM) AS allAvgCarryM,
               MAX(sh.carryM) AS allMaxCarryM,
               GROUP_CONCAT(DISTINCT CASE WHEN sh.source = 0 THEN sh.clubName END) AS liveClubsCsv,
               GROUP_CONCAT(DISTINCT sh.clubName) AS allClubsCsv
        FROM sessions s
        LEFT JOIN shots sh ON sh.sessionId = s.id
        GROUP BY s.id
        ORDER BY s.startedAtEpochMs DESC
        """
    )
    fun observeSummaries(): Flow<List<SessionSummaryRow>>

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
```

Note the invariant baked into this query: `GROUP_CONCAT` uses the default `,` separator, and **club names are guaranteed comma-free by the repository layer** (Task 4's `addClub`/`renameClub` reject commas), so `liveClubsCsv`/`allClubsCsv` parse cleanly in `toSummary`. Do not change the separator.

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/ShotDao.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.ShotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShotDao {

    @Insert
    suspend fun insert(shot: ShotEntity): Long

    @Query("SELECT * FROM shots WHERE sessionId = :sessionId ORDER BY seq")
    fun observeShots(sessionId: Long): Flow<List<ShotEntity>>

    @Query("SELECT * FROM shots WHERE sessionId = :sessionId ORDER BY seq")
    suspend fun shotsForSession(sessionId: Long): List<ShotEntity>

    @Query("SELECT COUNT(*) FROM shots WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("UPDATE shots SET clubName = :clubName WHERE id IN (:ids)")
    suspend fun retagShotIds(ids: List<Long>, clubName: String?)

    @Query("DELETE FROM shots")
    suspend fun deleteAll()
}
```

`core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/ClubDao.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.ClubEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClubDao {

    @Insert
    suspend fun insert(club: ClubEntity): Long

    @Query("SELECT * FROM clubs ORDER BY sortOrder")
    fun observeClubs(): Flow<List<ClubEntity>>

    @Query("SELECT COUNT(*) FROM clubs")
    suspend fun count(): Int

    @Query("SELECT * FROM clubs WHERE name = :name")
    suspend fun findByName(name: String): ClubEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM clubs")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE clubs SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM clubs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM clubs")
    suspend fun deleteAll()
}
```

- [ ] **Step 4: Run tests to verify they pass (and the schema exports)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:test`
Expected: `BUILD SUCCESSFUL` — 6 DAO tests green alongside Task 2's 6 (12 total in `:core:data`). KSP generates `core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/1.json` during the run.

- [ ] **Step 5: Commit (schemas included — they are the migration baseline)**

```bash
git add core/data/src core/data/schemas
git commit -m "feat(data): Room database, DAOs and aggregate summary query"
```

---
### Task 4: `SessionRepository` (Robolectric TDD)

**Files:**
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt`
- Test: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/SessionRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 3 DAOs; Task 2 records/mappers.
- Produces (`:app` and later milestones rely on exactly these):
  - `companion object { fun open(context: Context): SessionRepository; val DEFAULT_CLUBS: List<String> }` — DEFAULT_CLUBS = `["D","3W","5W","4H","4i","5i","6i","7i","8i","9i","PW","GW","SW","LW"]`
  - `val persistError: StateFlow<Boolean>` · `val liveOnly: MutableStateFlow<Boolean>` (true = live-only default)
  - `val summaries: Flow<List<SessionSummary>>` (emits already-filtered by `liveOnly`)
  - `val hasOpenSession: Flow<Boolean>` · `val clubs: Flow<List<ClubRecord>>`
  - `fun observeShots(sessionId: Long): Flow<List<ShotRecord>>`
  - `suspend fun initializeAndRestore(): RestoredSession?` (seeds bag once, probes the DB — corrupt file ⇒ archive + fresh DB + `persistError=true`, returns null)
  - `suspend fun appendShot(ballData: BallData, result: ShotResult, source: ShotSource, clubName: String?, timestampMs: Long)` (auto-creates the open session; never throws — sets `persistError` on failure)
  - `suspend fun incrementMisread()` (no-op without open session) · `suspend fun endSession()` · `suspend fun renameSession(id: Long, title: String)` (blank ⇒ null) · `suspend fun retagShots(ids: List<Long>, clubName: String?)`
     - `suspend fun addClub(name: String): Boolean` (false: blank/comma/duplicate) · `suspend fun renameClub(id: Long, newName: String): Boolean` · `suspend fun deleteClub(id: Long)`

- [ ] **Step 1: Write the failing tests**

Create `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/SessionRepositoryTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data

import com.hpsmiles.golfsim.core.ble.BallData
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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.SessionRepositoryTest"`
Expected: COMPILATION FAIL — `SessionRepository` unresolved.

---
- [ ] **Step 3: Implement the repository**

Create `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.ClubEntity
import com.hpsmiles.golfsim.core.data.entity.SessionEntity
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.RestoredSession
import com.hpsmiles.golfsim.core.data.record.SessionSummary
import com.hpsmiles.golfsim.core.data.record.ShotRecord
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.data.record.makeShotEntity
import com.hpsmiles.golfsim.core.data.record.toRecord
import com.hpsmiles.golfsim.core.data.record.toSummary
import com.hpsmiles.golfsim.core.physics.ShotResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * The persistence facade `:app` talks to. All mutations are suspend; `:app`
 * owns the calling coroutine (single-writer discipline via main-thread
 * mediation, spec §7). Nothing here launches coroutines of its own.
 */
class SessionRepository private constructor(private val context: Context) {

    private var db: Mlm2proDatabase = build()
    private val sessionDao get() = db.sessionDao()
    private val shotDao get() = db.shotDao()
    private val clubDao get() = db.clubDao()

    /** True once a write/open failure has been seen; cleared on next success. */
    val persistError = MutableStateFlow(false)

    /** History filter: true = live shots only (default, spec D4). UI toggles. */
    val liveOnly = MutableStateFlow(true)

    /**
     * Session list, already mapped through the [liveOnly] filter.
     *
     * These three flows are property GETTERS, not stored fields: they must
     * re-derive from the CURRENT [db] so they survive a corrupt-DB recovery
     * ([recoverFromCorruptFile] swaps in a rebuilt database). Flows captured
     * from the closed database die with Room's cancelled SupervisorJob —
     * caught by the recovery mechanism test, the first code path to execute
     * real recovery.
     */
    val summaries: Flow<List<SessionSummary>>
        get() = combine(sessionDao.observeSummaries(), liveOnly) { rows, onlyLive ->
            rows.map { it.toSummary(onlyLive) }
        }

    val hasOpenSession: Flow<Boolean>
        get() = sessionDao.observeOpen().map { it != null }

    val clubs: Flow<List<ClubRecord>>
        get() = clubDao.observeClubs().map { list -> list.map { ClubRecord(it.id, it.name) } }

    fun observeShots(sessionId: Long): Flow<List<ShotRecord>> =
        shotDao.observeShots(sessionId).map { list -> list.map { it.toRecord() } }

    /**
     * Seeds the default bag once and probes the DB by opening it eagerly.
     * A corrupt/broken file is archived and replaced with a fresh empty DB
     * with [persistError] set (spec §8) — returns null in that case.
     */
    suspend fun initializeAndRestore(): RestoredSession? = try {
        initialize()
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        recoverFromCorruptFile()
        null
    }

    private suspend fun initialize(): RestoredSession? {
        seedClubsIfEmpty()
        val open = sessionDao.findOpen() ?: return null
        return RestoredSession(
            sessionId = open.id,
            misreadCount = open.misreadCount,
            shots = shotDao.shotsForSession(open.id).map { it.toRecord() },
        )
    }

    private suspend fun seedClubsIfEmpty() {
        if (clubDao.count() == 0) {
            DEFAULT_CLUBS.forEachIndexed { i, name ->
                clubDao.insert(ClubEntity(name = name, sortOrder = i))
            }
        }
    }

    /**
     * Appends one accepted shot; auto-creates the open session if none.
     * Never throws — persistence trouble surfaces via [persistError] so the
     * live UI keeps rendering from memory (spec §8).
     */
    suspend fun appendShot(
        ballData: BallData,
        result: ShotResult,
        source: ShotSource,
        clubName: String?,
        timestampMs: Long,
    ) {
        try {
            db.withTransaction {
                val sessionId = sessionDao.findOpen()?.id
                    ?: sessionDao.insert(SessionEntity(startedAtEpochMs = timestampMs))
                val seq = shotDao.countForSession(sessionId)
                shotDao.insert(makeShotEntity(sessionId, seq, timestampMs, source, clubName, ballData, result))
            }
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "appendShot failed", t)
            persistError.value = true
        }
    }

    /** Coalesced misread (RangeSession already coalesces the 0x05 pair). */
    suspend fun incrementMisread() {
        val open = sessionDao.findOpen() ?: return
        sessionDao.incrementMisread(open.id)
    }

    suspend fun endSession() {
        val open = sessionDao.findOpen() ?: return
        sessionDao.end(open.id, System.currentTimeMillis())
    }

    suspend fun renameSession(id: Long, title: String) {
        sessionDao.rename(id, title.trim().take(MAX_TITLE).ifBlank { null })
    }

    suspend fun retagShots(ids: List<Long>, clubName: String?) {
        if (ids.isEmpty()) return
        shotDao.retagShotIds(ids, clubName?.trim()?.take(MAX_CLUB))
    }

    /** False on blank, comma-containing (summary CSV separator), or duplicate name. */
    suspend fun addClub(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.length > MAX_CLUB) return false
        if (trimmed.contains(',')) return false
        if (clubDao.findByName(trimmed) != null) return false
        clubDao.insert(ClubEntity(name = trimmed, sortOrder = clubDao.maxSortOrder() + 1))
        return true
    }

    /** False on blank, comma-containing (summary CSV separator), or duplicate name. */
    suspend fun renameClub(id: Long, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed.length > MAX_CLUB) return false
        if (trimmed.contains(',')) return false
        if (clubDao.findByName(trimmed) != null) return false
        clubDao.rename(id, trimmed)
        return true
    }

    suspend fun deleteClub(id: Long) {
        clubDao.delete(id)
    }

    private fun build(): Mlm2proDatabase =
        Room.databaseBuilder(context, Mlm2proDatabase::class.java, DB_NAME).build()

    /**
     * Spec §8: archive the broken file (with SQLite sidecars), start fresh.
     * internal (not private) so the corrupt-DB mechanism test can drive it
     * directly — Robolectric's SQLite won't throw on forged files, so the
     * real-throw detection path is verified on-device (Task 10, Step 5b).
     */
    internal suspend fun recoverFromCorruptFile() {
        Log.e(TAG, "database unreadable - recreating; old file archived")
        try {
            db.close()
        } catch (_: Exception) {
        }
        val parent = context.getDatabasePath(DB_NAME).parentFile
        for (suffix in listOf("-journal", "-wal", "-shm")) {
            val side = File(parent, DB_NAME + suffix)
            if (side.exists()) side.delete()
        }
        val main = context.getDatabasePath(DB_NAME)
        if (main.exists() && parent != null) {
            main.renameTo(File(parent, "corrupt-${System.currentTimeMillis()}-$DB_NAME"))
        }
        db = build()
        persistError.value = true
        try {
            seedClubsIfEmpty()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "SessionRepository"
        private const val DB_NAME = "golfsim.db"
        private const val MAX_TITLE = 40
        private const val MAX_CLUB = 20
        val DEFAULT_CLUBS = listOf(
            "D", "3W", "5W", "4H", "4i", "5i", "6i", "7i", "8i", "9i",
            "PW", "GW", "SW", "LW",
        )

        fun open(context: Context): SessionRepository = SessionRepository(context.applicationContext)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:test`
Expected: `BUILD SUCCESSFUL` — 9 repository tests green (21 total in `:core:data`).

- [ ] **Step 5: Commit**

```bash
git add core/data/src
git commit -m "feat(data): SessionRepository with auto-start sessions, retag and corrupt-DB recovery"
```

---
### Task 4a: DAO hardening — regression pack + deterministic ordering

Small, self-contained follow-up to Task 3's quality review (runs AFTER Task 4 completes; does not touch repository code).

**Files:**
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/SessionDao.kt`
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/ShotDao.kt`
- Test: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabaseTest.kt`

**Interfaces:**
- Consumes: Task 3 DAOs + entities as committed (`d197a78`); Task 4's `SessionRepository` untouched.
- Produces: identical DAO public API (only ORDER BY clauses change internally); `Mlm2proDatabaseTest` grows from 6 to 8 tests with two sharpened fixtures.

**Conscious deferrals (reviewed 2026-09-26 — do NOT add):** zero-shot-session aggregate test (impossible state — sessions auto-start on the first accepted shot); standalone `observeOpen` empty-DB test (covered by Task 4's restore-null repository test); `clubNames` bag-ordering (encounter order is deliberate); schema-level comma hardening of `clubs.name` (Task 4's repository comma rule + the single-writer design stand; revisit only if a schema v2 happens for other reasons).

- [ ] **Step 1: Deterministic ordering tiebreakers (pure SQL refactor)**

In `SessionDao.kt`:
- `findOpen`: `ORDER BY startedAtEpochMs DESC LIMIT 1` → `ORDER BY startedAtEpochMs DESC, id DESC LIMIT 1`
- `observeOpen`: same change as `findOpen`
- `observeSummaries`: `ORDER BY s.startedAtEpochMs DESC` → `ORDER BY s.startedAtEpochMs DESC, s.id DESC`

In `ShotDao.kt`:
- `observeShots`: `ORDER BY seq` → `ORDER BY seq, id`
- `shotsForSession`: `ORDER BY seq` → `ORDER BY seq, id`

- [ ] **Step 2: Run module tests (tiebreaker refactor must stay green)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:test`
Expected: `BUILD SUCCESSFUL` — 6 RecordsTest + 6 Mlm2proDatabaseTest, unchanged suite still green.

- [ ] **Step 3: Add the FK CASCADE regression test**

Append inside `Mlm2proDatabaseTest` (after `unique club name rejects duplicates`):

```kotlin
    @Test
    fun `deleting a session cascades to its shots`() = runTest {
        val id = db.sessionDao().insert(SessionEntity(startedAtEpochMs = 1_000L))
        db.shotDao().insert(makeShotEntity(id, 0, 1L, ShotSource.LIVE, "7i", ball, result))
        assertEquals(1, db.shotDao().countForSession(id))
        db.sessionDao().deleteAll()
        assertEquals(0, db.shotDao().countForSession(id))
    }
```

- [ ] **Step 4: Add the flow re-emission smoke test**

Add imports at the top of the test file (keep alphabetical order):

```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
```

Append inside `Mlm2proDatabaseTest`:

```kotlin
    @Test
    fun `observeSummaries re-emits after a shot insert`() = runBlocking {
        val totals = mutableListOf<Int>()
        val collector = launch {
            db.sessionDao().observeSummaries().collect { totals.add(it.sumOf { row -> row.shotCount }) }
        }
        try {
            withTimeout(2_000L) { while (totals.isEmpty()) delay(20L) }
            assertEquals(0, totals.first())
            val id = db.sessionDao().insert(SessionEntity(startedAtEpochMs = 1_000L))
            db.shotDao().insert(makeShotEntity(id, 0, 1L, ShotSource.LIVE, "7i", ball, result))
            withTimeout(2_000L) { while (totals.none { it > 0 }) delay(20L) }
            assertEquals(1, totals.last())
        } finally {
            collector.cancel()
        }
    }
```

**Why `runBlocking`, not `runTest`:** Room's InvalidationTracker fires on real background executors. Under `runTest` virtual time, a `withTimeout` + `delay` polling loop burns through virtual time instantly and would time out before Room's real threads ever emit. `runBlocking` uses real time so the poll loop genuinely waits for the re-emission. Do not "fix" this back to `runTest`.

- [ ] **Step 5: Sharpen two fixtures and rename one test (same file)**

Replace the body of `summaries aggregate live and demo slices` — distinct live carries and clubs; CSVs asserted as SETS because SQLite does not document GROUP_CONCAT ordering:

```kotlin
    @Test
    fun `summaries aggregate live and demo slices`() = runTest {
        val id = db.sessionDao().insert(SessionEntity(startedAtEpochMs = 1_000L))
        db.shotDao().insert(
            makeShotEntity(id, 0, 1_000L, ShotSource.LIVE, "7i", ball, result.copy(carryM = 130.0, totalM = 140.0))
        )
        db.shotDao().insert(
            makeShotEntity(id, 1, 1_001L, ShotSource.LIVE, "SW", ball, result.copy(carryM = 150.0, totalM = 160.0))
        )
        db.shotDao().insert(
            makeShotEntity(id, 2, 1_500L, ShotSource.DEMO, null, ball.copy(ballSpeed = 50.0),
                result.copy(carryM = 160.0, totalM = 170.0, sideM = 1.0))
        )
        val row = db.sessionDao().observeSummaries().first().single()
        assertEquals(3, row.shotCount)
        assertEquals(2, row.liveCount)
        assertEquals(1, row.demoCount)
        assertEquals(140.0, row.liveAvgCarryM!!, 1e-9)
        assertEquals(150.0, row.liveMaxCarryM!!, 1e-9)
        assertEquals((130.0 + 150.0 + 160.0) / 3.0, row.allAvgCarryM!!, 1e-9)
        assertEquals(160.0, row.allMaxCarryM!!, 1e-9)
        assertEquals(setOf("7i", "SW"), row.liveClubsCsv!!.split(',').toSet())
        assertEquals(setOf("7i", "SW"), row.allClubsCsv!!.split(',').toSet())
    }
```

Replace the body of `shotsForSession returns rows ordered by seq` — insert seq 1 BEFORE seq 0 so rowid order no longer satisfies the assertion:

```kotlin
    @Test
    fun `shotsForSession returns rows ordered by seq`() = runTest {
        val id = db.sessionDao().insert(SessionEntity(startedAtEpochMs = 1_000L))
        db.shotDao().insert(makeShotEntity(id, 1, 6L, ShotSource.LIVE, "7i", ball, result))
        db.shotDao().insert(makeShotEntity(id, 0, 5L, ShotSource.LIVE, "7i", ball, result))
        assertEquals(listOf(0, 1), db.shotDao().shotsForSession(id).map { it.seq })
        assertEquals(2, db.shotDao().countForSession(id))
    }
```

Rename `retag updates rows and re-emits the flow` → `retag updates rows and clears to null` (the two-`.first()` body never observed re-emission; the name overclaimed). Body unchanged.

- [ ] **Step 6: Run full module suite**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:test`
(If Gradle reports up-to-date, force with `.\gradlew.bat :core:data:testDebugUnitTest --rerun`.)
Expected: `BUILD SUCCESSFUL` — 23 tests, 0 failures (6 RecordsTest + 8 Mlm2proDatabaseTest + 9 SessionRepositoryTest).

- [ ] **Step 7: Commit**

```bash
git add core/data/src
git commit -m "test(data): DAO regression pack and deterministic ordering tiebreakers"
```

---
### Task 4b: Repository follow-up — cancellation discipline + rename/retag pins

Origin: Task 4 quality-review findings (ora-8). Two code changes with no API impact: (1) `recoverFromCorruptFile`'s reseed catch swallows `CancellationException` on a suspend path — the discipline bug Task 4 already fixed at `appendShot`/`initializeAndRestore`; (2) `renameSession`'s trim/truncate/blank-reset and `retagShots`' trim/truncate mapping have no repo-level tests, and Tasks 7-9 build directly on them. The same amendment also adds `remember(repo)` capture of the property-getter flows at their five consumption sites in Tasks 6-9 wiring (plan text only — no code until those tasks run).

Conscious deferrals (review'd, deliberately NOT done — do not re-flag in later reviews): `persistError` clearing only on shot writes (spec §8 as written), `renameTo` result logging / same-ms collision handling, `checkNotNull(parent)`, exposing `persistError` as `StateFlow` rather than `MutableStateFlow`.

**Files:**
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt`
- Modify: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/SessionRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 4 API — no signature changes.
- Produces: unchanged public API; `:core:data` suite grows 23 → 25 tests (SessionRepositoryTest 9 → 11).

- [ ] **Step 1: Append the two regression tests.** These pin existing correct behavior — they are expected to PASS immediately; a failure means a real defect, not a TDD red bar. Insert after the `recoverFromCorruptFile archives, reseeds and flags persistError` test, before the class's closing brace:

```kotlin
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
```

- [ ] **Step 2: Run the class — expect 11 green (pin, not red)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.SessionRepositoryTest"`
Expected: `BUILD SUCCESSFUL` — 11 tests, 0 failures.

- [ ] **Step 3: Fix the CancellationException swallow in `recoverFromCorruptFile`.** In `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt`, find (end of `recoverFromCorruptFile`):

```kotlin
        try {
            seedClubsIfEmpty()
        } catch (_: Exception) {
        }
```

Replace with:

```kotlin
        try {
            seedClubsIfEmpty()
        } catch (t: Exception) {
            // Same cancellation discipline as appendShot / initializeAndRestore:
            // a cancelled caller must not have its CancellationException eaten
            // on this suspend path (Task 4b, review finding).
            if (t is CancellationException) throw t
        }
```

(`CancellationException` is already imported.)

- [ ] **Step 4: Full module suite**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:test`
Expected: `BUILD SUCCESSFUL` — 25 tests, 0 failures (6 RecordsTest + 8 Mlm2proDatabaseTest + 11 SessionRepositoryTest).

- [ ] **Step 5: Commit**

```bash
git add core/data/src
git commit -m "fix(data): rethrow cancellation in recovery reseed, pin rename/retag mapping"
```

---
### Task 5: `RangeSession` returns shots + restore support (JVM TDD in `:app`)

**Files:**
- Modify: `app/build.gradle.kts` (add `:core:data` dependency)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/DisplayShot.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt`
- Test (modify + extend): `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSessionTest.kt`

**Interfaces:**
- Consumes: `core/data` `ShotRecord`, `ShotSource`, `RestoredSession`.
- Produces (Task 6/7 rely on exactly these):
  - `data class DisplayShot(ballData: BallData, launch: LaunchConditions, shotResult: ShotResult, timestampMs: Long = 0L)` — default keeps existing scene/camera tests compiling.
  - `RangeSession.add(ballData: BallData): DisplayShot?` — null = rejected (physics guards), non-null = accepted and appended; `timestampMs` filled from the injectable `clockMs`.
  - `RangeSession.restore(restored: RestoredSession)` — rebuilds scalar-backed resting shots + misread count.
  - top-level `fun ShotRecord.toDisplayShot(): DisplayShot?` in `RangeSession.kt` (null if the row no longer passes `LaunchConditions` guards).

- [ ] **Step 1: Update the tests (they are the failing spec)**

In `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSessionTest.kt`:

Replace the first test with:

```kotlin
    @Test
    fun `add returns a DisplayShot and appends it`() {
        val session = RangeSession()
        val shot = session.add(fade())
        assertNotNull(shot)
        assertEquals(1, session.shots.size)
        assertEquals(44.2, shot!!.ballData.ballSpeed, 1e-9)
        assertEquals(21.7, shot.launch.launchAngleDeg, 1e-9)
        assertTrue(shot.shotResult.carryM > 0.0)
    }
```

Replace the third test with:

```kotlin
    @Test
    fun `add returns null and appends nothing when LaunchConditions require fails`() {
        val session = RangeSession()
        assertNull(session.add(garbage()))
        assertEquals(0, session.shots.size)
        assertEquals(0, session.tick.intValue)
    }
```

Update imports (`assertNull`, `assertNotNull`, `ShotRecord`, `ShotSource`, `RestoredSession`) and add these two tests plus a helper at class level:

```kotlin
    private fun shotRecord(seq: Int, club: String?) = ShotRecord(
        id = seq.toLong(), sessionId = 5L, seq = seq, timestampMs = seq.toLong(),
        source = ShotSource.LIVE, clubName = club, ballData = fade(),
        carryM = 140.9, totalM = 150.0, sideM = -3.0, apexM = 27.0, flightTimeSec = 6.1,
    )

    @Test
    fun `add stamps the shot from the injectable clock`() {
        val session = RangeSession()
        var now = 123_456L
        session.clockMs = { now }
        val shot = session.add(fade())
        assertEquals(123_456L, shot!!.timestampMs)
        now = 999L
        assertEquals(999L, session.add(fade())!!.timestampMs) // each shot stamped at its own add
    }

    @Test
    fun `restore skips rows that no longer pass launch guards`() {
        val session = RangeSession()
        val bad = shotRecord(2, "7i").copy(ballData = garbage())
        session.restore(RestoredSession(5L, 0, listOf(bad)))
        assertEquals(0, session.shots.size)
    }
```

And add the restore test:

```kotlin
    @Test
    fun `restore rebuilds resting shots and misreads without trajectories`() {
        val session = RangeSession()
        session.restore(RestoredSession(5L, 3, listOf(shotRecord(0, "7i"), shotRecord(1, null))))
        assertEquals(2, session.shots.size)
        assertEquals(1L, session.shots[0].timestampMs)
        assertEquals(3, session.misreadCount.intValue)
        // Scalar-backed ShotResult carries no samples (nothing replays after restore)…
        assertEquals(0, session.shots[0].shotResult.samples.size)
        // …and rollout is derived on restore: total - carry.
        assertEquals(9.1, session.shots[0].shotResult.rolloutM, 1e-9)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:test`
Expected: COMPILATION FAIL — `add` returns `Boolean` (FixtureShot null-check mismatch), `restore`/`RestoredSession`/`ShotRecord` unresolved.

- [ ] **Step 3: Implement**

In `app/build.gradle.kts` dependencies (after the `:core:connect` line):

```kotlin
    // M5: session/shot persistence + bag records.
    implementation(project(":core:data"))
```

Replace `app/src/main/kotlin/com/hpsmiles/golfsim/range/DisplayShot.kt` body:

```kotlin
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult

/**
 * One shot as the range screen holds it: the decoded launch data, the solve
 * inputs used, and the physics result. All UI rendering derives from these.
 * `timestampMs` stamps capture time (injectable clock via RangeSession).
 */
data class DisplayShot(
    val ballData: BallData,
    val launch: LaunchConditions,
    val shotResult: ShotResult,
    val timestampMs: Long = 0L,
)
```

In `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt`: change `add` to return `DisplayShot?` and stamp the clock; add `restore` and the mapper (imports: `com.hpsmiles.golfsim.core.data.record.RestoredSession`, `com.hpsmiles.golfsim.core.data.record.ShotRecord`, `com.hpsmiles.golfsim.core.physics.ShotResult`):

```kotlin
    /**
     * Converts one decoded measurement into a rendered shot. Returns null —
     * appending nothing — when the data violates LaunchConditions require()
     * guards, so a bad live decode can never crash the UI thread.
     * M4c evidence + golden fixtures pin the expected live values.
     * M5: returns the accepted DisplayShot (timestamped from the injectable
     * clock) so AppRoot can hand it to the persistence layer.
     */
    fun add(ballData: BallData): DisplayShot? {
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
        val result = BallFlightEngine.simulate(
            launch,
            Environment(),
            UniformSurface(Surface.FAIRWAY_NORMAL),
        )
        val shot = DisplayShot(ballData, launch, result, clockMs())
        shots.add(shot)
        tick.intValue++
        return shot
    }

    /**
     * M5 restart resume: rebuilds scalar-backed resting shots from the open
     * session. No trajectories (nothing replays), no tick bump.
     */
    fun restore(restored: RestoredSession) {
        shots.clear()
        restored.shots.forEach { record ->
            record.toDisplayShot()?.let { shots.add(it) }
        }
        misreadCount.intValue = restored.misreadCount
    }
```

And append at file end (top-level function):

```kotlin
/**
 * Rebuilds a persisted shot as a scalar-backed resting DisplayShot (empty
 * trajectory samples — nothing replays after a restart). Returns null if the
 * stored row no longer passes LaunchConditions guards (defensive against
 * tampered data).
 */
fun ShotRecord.toDisplayShot(): DisplayShot? {
    val bd = ballData
    val launch = try {
        LaunchConditions(
            ballSpeedMps = bd.ballSpeed,
            launchAngleDeg = bd.launchAngle,
            spinRpm = bd.totalSpin,
            spinAxisDeg = bd.spinAxis,
            launchDirDeg = bd.launchDirection,
        )
    } catch (_: IllegalArgumentException) {
        return null
    }
    val result = ShotResult(
        carryM = carryM,
        rolloutM = totalM - carryM,
        totalM = totalM,
        sideM = sideM,
        apexM = apexM,
        flightTimeSec = flightTimeSec,
    )
    return DisplayShot(bd, launch, result, timestampMs)
}
```

AppRoot's `fireDemo()` currently reads `if (demo) session.add(demoSource.nextShot())` — an unused-result expression — and `onMeasurement` discards the result, so both call sites still compile unchanged. Task 6 rewrites them.

- [ ] **Step 4: Run tests to verify they pass**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:test`
Expected: `BUILD SUCCESSFUL` — RangeSessionTest 8 green; RangeSceneTest/RangeRolloutTest/PovProjectorTest/FollowCamTest untouched by the defaulted parameter.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src
git commit -m "feat(range): RangeSession returns timestamped shots and restores sessions"
```

---
### Task 6: AppRoot persistence wiring + END SESSION chip + `ActiveClubStore`

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/ActiveClubStore.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`

**Interfaces:**
- Consumes: Task 4 `SessionRepository` API; Task 5 `RangeSession.add(): DisplayShot?` + `restore(RestoredSession)`.
- Produces (Task 7/8/9 rely on): `ActiveClubStore(context)` with `get(): String?` / `set(String?)`; AppRoot-held `sessionRepository`, `activeClubName` state, `selectClub(name: String?)` helper, `persist(shot, source)` helper; StatusStrip shows `DB WRITE FAILING` while `persistError` is set; rail shows END SESSION while a session is open.

No unit tests in this task — it is Compose wiring, and `ActiveClubStore` is pure SharedPreferences delegation (spec §4 keeps SharedPreferences). Verified by `:app:test` staying green, `:app:assembleDebug`, and the Task 10 device checklist ("pill selection survives restart").

- [ ] **Step 1: Create `ActiveClubStore`**

`app/src/main/kotlin/com/hpsmiles/golfsim/range/ActiveClubStore.kt`:

```kotlin
package com.hpsmiles.golfsim.range

import android.content.Context
import android.content.SharedPreferences

/**
 * The active-club pill selection (spec §4 — SharedPreferences alongside the
 * SecretStore pattern; no DataStore migration, requirements did not grow).
 */
class ActiveClubStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun get(): String? = prefs.getString(KEY, null)

    fun set(clubName: String?) {
        prefs.edit().putString(KEY, clubName).apply()
    }

    private companion object {
        const val PREFS_FILE = "golfsim_prefs"
        const val KEY = "active_club"
    }
}
```

- [ ] **Step 2: Wire AppRoot**

In `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`:

(a) Add imports (near the existing `androidx.compose.runtime.*` block):

```kotlin
import androidx.compose.runtime.LaunchedEffect
import com.hpsmiles.golfsim.core.data.SessionRepository
import com.hpsmiles.golfsim.core.data.record.ShotSource
```

(b) After `val session = remember { RangeSession() }` (the M4d block), insert:

```kotlin
    // M5: persistence facade — one per composition. Bag seeding, DB probe and
    // open-session restore run once at startup; appends ride the existing
    // main-thread mediation (single-writer discipline, spec §7).
    val sessionRepository = remember { SessionRepository.open(context) }
    val activeClubStore = remember { ActiveClubStore(context) }
    var activeClubName by remember { mutableStateOf(activeClubStore.get()) }

    fun selectClub(name: String?) {
        activeClubName = name
        activeClubStore.set(name)
    }

    LaunchedEffect(sessionRepository) {
        sessionRepository.initializeAndRestore()?.let { session.restore(it) }
    }

    // M5: persist every accepted shot. Fire-and-forget — the repository never
    // throws and flags StatusStrip via persistError (spec §8).
    fun persist(shot: DisplayShot?, source: ShotSource) {
        if (shot == null) return
        val club = activeClubName
        scope.launch {
            sessionRepository.appendShot(shot.ballData, shot.shotResult, source, club, shot.timestampMs)
        }
    }
```

(c) Replace the `DisposableEffect(gattClient)` body so shots and misreads persist:

```kotlin
    DisposableEffect(gattClient) {
        gattClient.onMeasurement = { ballData ->
            scope.launch { persist(session.add(ballData), ShotSource.LIVE) }
        }
        gattClient.onMisread = {
            scope.launch {
                session.markMisread()
                sessionRepository.incrementMisread()
            }
        }
        onDispose {
            gattClient.onMeasurement = null
            gattClient.onMisread = null
        }
    }
```

(d) Replace `fireDemo()`:

```kotlin
    fun fireDemo() {
        if (demo) persist(session.add(demoSource.nextShot()), ShotSource.DEMO)
    }
```

(e) In the NavRail, after the `if (linkVisible) { ... }` ARM/STANDBY + DISCONNECT block, add the END chip:

```kotlin
                    // M5: explicit session end (spec D1). Non-destructive — the
                    // session stays fully browsable in HISTORY (Task 9).
                    // Getter flow (new Flow per access) — remember-capture it
                    // so collectAsState doesn't restart collection on every
                    // recomposition (Task 4b review finding).
                    val hasOpenSession by remember(sessionRepository) {
                        sessionRepository.hasOpenSession
                    }.collectAsState(false)
                    if (hasOpenSession) {
                        Spacer(Modifier.height(4.dp))
                        RailChip(
                            label = "END SESSION",
                            border = GolfColors.Amber,
                            labelColor = GolfColors.Amber,
                            onClick = { scope.launch { sessionRepository.endSession() } },
                        )
                    }
```

(f) Replace the `StatusStrip(...)` call so persistence errors win over state text:

```kotlin
            val persistError by sessionRepository.persistError.collectAsState()
            StatusStrip(
                armed = demo || connectionState is ConnectionState.Armed,
                info = if (persistError) "DB WRITE FAILING" else describe(connectionState, demo = demo, scanning = scanning),
            )
```

- [ ] **Step 3: Verify build + tests**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:test :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` — no new tests; existing suite green; APK assembles with the new wiring (`activeClubName` is only consumed so far — that is expected until Task 7).

- [ ] **Step 4: Commit**

```bash
git add app/src
git commit -m "feat(app): persist shots via SessionRepository, END SESSION chip, active club store"
```

---

### Task 7: Active-club pill + picker overlay (range)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/ClubPickerOverlay.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt` (signature ~line 89, state block ~line 98, top-left column ~line 187, end of range `Box` ~line 248)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` (`when (tab)` RANGE branch, ~line 214)

**Interfaces:**
- Consumes: Task 6's AppRoot `activeClubName: String?` state + `selectClub(name: String?)`; Task 4's `SessionRepository.clubs: Flow<List<ClubRecord>>` and `suspend addClub(name: String): Boolean`; `ClubRecord(id: Long, name: String)`.
- Produces: `RangeScreen(modifier, session, activeClubName: String? = null, clubNames: List<String> = emptyList(), onSelectClub: (String?) -> Unit = {}, onAddClub: suspend (String) -> Boolean = { false })` — additive defaulted params, existing call sites compile unchanged. `ClubPickerOverlay(activeClubName, clubNames, onDismiss, onSelectClub, onAddClub)` (used only inside RangeScreen).

No unit tests in this task: the pill/overlay are presentation-only — selection logic is a nullable pass-through (covered by Task 4 `addClub`/seed tests and Task 6 wiring), and this repo has no Compose-UI test harness (`ScaffoldSmokeTest` is a compile canary). Behavior is verified by Task 10's on-device checklist.

- [ ] **Step 1: Create `ClubPickerOverlay.kt`**

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/range/ClubPickerOverlay.kt
package com.hpsmiles.golfsim.range

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import kotlinx.coroutines.launch

/** Dim scrim over the whole range (mockup club-selector.html). */
private val OverlayScrim = Color(0x88000000)

/**
 * M5 (spec D7, mockup club-selector): full-range club picker overlay.
 * Scrim tap dismisses. The first tile is "—" (untagged — clears the pill);
 * each bag tile makes that club active. The ＋ ADD tile reveals an inline
 * field routed through [onAddClub] — the repository owns the duplicate /
 * blank rules, so a rejected name keeps the form open with a hint.
 */
@Composable
fun ClubPickerOverlay(
    activeClubName: String?,
    clubNames: List<String>,
    onDismiss: () -> Unit,
    onSelectClub: (String?) -> Unit,
    onAddClub: suspend (String) -> Boolean,
) {
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var addRejected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayScrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // Panel swallows the scrim's dismiss click without consuming it
        // beyond its own bounds (enabled=false click still steals the tap).
        Surface(
            shape = RoundedCornerShape(GolfSpacing.CornerCard),
            color = GolfColors.Card,
            modifier = Modifier.clickable(enabled = false) {},
        ) {
            Column(
                modifier = Modifier.padding(GolfSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("ACTIVE CLUB", style = GolfTypography.ScreenTitle, color = GolfColors.TextPrimary)
                // "—" (untagged) tile first, then the bag, 4 per row.
                (listOf<String?>(null) + clubNames).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                        row.forEach { name ->
                            ClubTile(
                                label = name ?: "—",
                                active = name == activeClubName,
                                onClick = {
                                    onSelectClub(name)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
                ClubTile(
                    label = if (adding) "×" else "＋ ADD",
                    active = false,
                    onClick = {
                        adding = !adding
                        addRejected = false
                    },
                )
                if (adding) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = {
                                newName = it.take(20)
                                addRejected = false
                            },
                            singleLine = true,
                            modifier = Modifier.width(160.dp),
                        )
                        Text(
                            text = "ADD",
                            style = GolfTypography.MetricLabel,
                            color = GolfColors.Teal,
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        if (onAddClub(newName.trim())) {
                                            adding = false
                                            newName = ""
                                            addRejected = false
                                        } else {
                                            addRejected = true
                                        }
                                    }
                                }
                                .border(1.dp, GolfColors.Teal, RoundedCornerShape(50))
                                .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                        )
                    }
                }
                if (addRejected) {
                    Text(
                        text = if (newName.isBlank()) "ENTER A NAME" else if (newName.contains(',')) "NO COMMAS" else "ALREADY IN BAG",
                        style = GolfTypography.Status,
                        color = GolfColors.AlertRed,
                    )
                }
            }
        }
    }
}

/** 84×72 tile per the D7 mockup; Teal border marks the active club. */
@Composable
private fun ClubTile(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(84.dp, 72.dp)
            .border(1.dp, if (active) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = GolfTypography.Status.copy(fontSize = 16.sp),
            color = if (active) GolfColors.Teal else GolfColors.TextPrimary,
        )
    }
}
```

Note the `active = name == activeClubName` line: `String? == String?` is structural, so it correctly marks the "—" tile active only when nothing is selected and a named tile only when it is the active club.

- [ ] **Step 2: Modify `RangeScreen.kt`**

(a) Extend the signature (after `session: RangeSession,`):

```kotlin
@Composable
fun RangeScreen(
    modifier: Modifier = Modifier,
    session: RangeSession,
    activeClubName: String? = null,
    clubNames: List<String> = emptyList(),
    onSelectClub: (String?) -> Unit = {},
    onAddClub: suspend (String) -> Boolean = { false },
) {
```

(b) Add picker state beside the other remembered state (after `var historyLimit ...`):

```kotlin
    var showClubPicker by remember { mutableStateOf(false) }
```

(c) Insert the pill as the FIRST child of the top-left tracer `Column` (before the TRACER/PREV `Row`):

```kotlin
                // M5 D7: active-club pill — first chip, above TRACER. "—" = untagged.
                Text(
                    text = "${activeClubName ?: "—"} ▾",
                    color = if (activeClubName != null) GolfColors.Teal else GolfColors.TextSecondary,
                    style = ChipFont,
                    modifier = Modifier
                        .clickable { showClubPicker = true }
                        .border(1.dp, if (activeClubName != null) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                )
```

(d) Mount the overlay at the end of the range `Box`, after the top-center speed-chip `Row` (before the `// FIRE / MODE / CONNECT moved to AppRoot's rail` comment):

```kotlin
            // M5: club picker overlay — scrim covers the whole range area.
            if (showClubPicker) {
                ClubPickerOverlay(
                    activeClubName = activeClubName,
                    clubNames = clubNames,
                    onDismiss = { showClubPicker = false },
                    onSelectClub = onSelectClub,
                    onAddClub = onAddClub,
                )
            }
```

- [ ] **Step 3: Wire the RANGE branch in `AppRoot.kt`**

Replace `RangeTab.RANGE -> RangeScreen(Modifier.weight(1f), session = session)` with:

```kotlin
                when (tab) {
                    RangeTab.RANGE -> {
                        // Getter flow — remember-capture once (Task 4b).
                        val clubRecords by remember(sessionRepository) {
                            sessionRepository.clubs
                        }.collectAsState(initial = emptyList())
                        RangeScreen(
                            Modifier.weight(1f),
                            session = session,
                            activeClubName = activeClubName,
                            clubNames = clubRecords.map { it.name },
                            onSelectClub = ::selectClub,
                            onAddClub = { sessionRepository.addClub(it) },
                        )
                    }
                    RangeTab.SETTINGS -> SettingsScreen(captureLog = captureLog)
                }
```

(`collectAsState`/`getValue` are already imported in AppRoot from Task 6; `emptyList()` type-infers to `List<ClubRecord>` without an explicit import.)

- [ ] **Step 4: Verify build + tests**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:test :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` — existing suite green (signature change is additive with defaults); APK assembles with the overlay.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/ClubPickerOverlay.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(app): active club pill and picker overlay on range"
```

---

### Task 8: BAG editor in Settings

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt` (full rewrite below — wraps the previously bare sibling `SectionCard`s in a scrolling `Column`, which also fixes their layout as `Row` children)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` (SETTINGS branch, ~line 214)

**Interfaces:**
- Consumes: Task 4's `SessionRepository.clubs: Flow<List<ClubRecord>>`, `suspend addClub(name: String): Boolean`, `suspend renameClub(id: Long, newName: String): Boolean`, `suspend deleteClub(id: Long)`; `ClubRecord(id: Long, name: String)`.
- Produces: `SettingsScreen(captureLog: CaptureLog = CaptureLog(), clubs: List<ClubRecord> = emptyList(), onAddClub: suspend (String) -> Boolean = { false }, onRenameClub: suspend (Long, String) -> Boolean = { false }, onDeleteClub: (Long) -> Unit = {})` — additive defaulted params. No later task extends SettingsScreen.

No unit tests: same rationale as Task 7 (presentation-only; repo rules live in Task 4's `addClub`/`renameClub` tests; device-verified in Task 10).

- [ ] **Step 1: Rewrite `SettingsScreen.kt`**

Full new file (the two pre-existing cards keep their bodies verbatim; the change is the wrapper `Column`, the new import block, and the BAG card + rename dialog):

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt
package com.hpsmiles.golfsim.settings

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.connect.CaptureLog
import com.hpsmiles.golfsim.core.data.ClubRecord
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import java.io.File
import kotlinx.coroutines.launch

private fun chipStyle(enabled: Boolean) = if (enabled) GolfColors.Teal else GolfColors.TextMuted

/**
 * M5: BAG editor — add/rename/delete club tags (spec D3). Deleting never
 * rewrites history: persisted shots keep their snapshotted club string.
 * M4b cards (auth guidance + bench capture tooling) are unchanged.
 */
@Composable
fun SettingsScreen(
    captureLog: CaptureLog = CaptureLog(),
    clubs: List<ClubRecord> = emptyList(),
    onAddClub: suspend (String) -> Boolean = { false },
    onRenameClub: suspend (Long, String) -> Boolean = { false },
    onDeleteClub: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    var captureOn by remember { mutableStateOf(captureLog.enabled) }
    var lastExportPath by remember { mutableStateOf("") }

    var entryCount by remember { mutableStateOf(captureLog.entries().size) }
    LaunchedEffect(captureOn) {
        while (captureOn) {
            entryCount = captureLog.entries().size
            kotlinx.coroutines.delay(250)
        }
    }

    // BAG editor state.
    var newClubName by remember { mutableStateOf("") }
    var addRejected by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ClubRecord?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameRejected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GolfColors.Base)
            .verticalScroll(rememberScrollState())
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        SectionCard("RAPSODO AUTH") {
            Text(
                text = "Before using a live session:\n" +
                    "1. In the Rapsodo app: Play \u2192 Simulation \u2192 3rd Party Apps \u2192 Awesome Golf \u2192 Authenticate Now\n" +
                    "2. Re-authorize every 24 hours (Rapsodo requirement)\n" +
                    "3. The session token lasts ~3 hours; if the device stays red, re-authenticate and power-cycle it",
                style = GolfTypography.Body,
                color = GolfColors.TextSecondary,
            )
        }
        SectionCard("BAG") {
            Text(
                text = "Club tags used when tagging shots. Renames apply to new shots only; shots keep the tag they were saved with.",
                style = GolfTypography.Body,
                color = GolfColors.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(bottom = GolfSpacing.Sm),
            )
            if (clubs.isEmpty()) {
                Text(
                    "NO CLUBS YET",
                    style = GolfTypography.Status,
                    color = GolfColors.TextMuted,
                    modifier = Modifier.padding(bottom = GolfSpacing.Sm),
                )
            }
            clubs.forEach { club ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Text(
                        club.name,
                        style = GolfTypography.Body,
                        color = GolfColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "RENAME",
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.TextSecondary,
                        modifier = Modifier
                            .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                            .clickable {
                                renameTarget = club
                                renameText = club.name
                                renameRejected = false
                            }
                            .padding(horizontal = GolfSpacing.Md, vertical = 6.dp),
                    )
                    Text(
                        text = "DELETE",
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.AlertRed,
                        modifier = Modifier
                            .border(1.dp, GolfColors.AlertRed, RoundedCornerShape(50))
                            .clickable { onDeleteClub(club.id) }
                            .padding(horizontal = GolfSpacing.Md, vertical = 6.dp),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
                modifier = Modifier.padding(top = GolfSpacing.Sm),
            ) {
                OutlinedTextField(
                    value = newClubName,
                    onValueChange = {
                        newClubName = it.take(20)
                        addRejected = false
                    },
                    singleLine = true,
                    modifier = Modifier.width(160.dp),
                )
                Text(
                    text = "ADD",
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.Teal,
                    modifier = Modifier
                        .clickable {
                            scope.launch {
                                if (onAddClub(newClubName.trim())) {
                                    newClubName = ""
                                    addRejected = false
                                } else {
                                    addRejected = true
                                }
                            }
                        }
                        .border(1.dp, GolfColors.Teal, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                )
            }
            if (addRejected) {
                Text(
                    text = if (newClubName.isBlank()) "ENTER A NAME" else if (newClubName.contains(',')) "NO COMMAS" else "ALREADY IN BAG",
                    style = GolfTypography.Status,
                    color = GolfColors.AlertRed,
                )
            }
        }
        SectionCard("DEBUG - NOTIFICATION CAPTURE (BENCH)") {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(
                    text = if (captureOn) "CAPTURE: ON" else "CAPTURE: OFF",
                    style = GolfTypography.MetricLabel,
                    color = chipStyle(captureOn),
                    modifier = Modifier
                        .border(1.dp, if (captureOn) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                        .clickable {
                            captureOn = !captureOn
                            captureLog.enabled = captureOn
                        }
                        .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                )
            }
            Text(
                text = if (captureOn) "LISTENING - $entryCount notification(s) captured" else "Toggle ON during a live session to record raw/decrypted notifications",
                style = GolfTypography.Status,
                color = GolfColors.TextMuted,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "EXPORT",
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.TextPrimary,
                    modifier = Modifier
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                        .clickable {
                            val file = File(context.filesDir, "capture.properties")
                            file.writeText(captureLog.exportProperties(firmware = "unknown-M4b-bench"))
                            lastExportPath = file.absolutePath
                            Log.i("CaptureExport", "fixture exported: ${file.absolutePath}")
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "MLM2PRO capture fixture")
                                putExtra(Intent.EXTRA_TEXT, file.readText())
                            }
                            context.startActivity(Intent.createChooser(share, "Share capture fixture"))
                        }
                        .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                )
            }
            if (lastExportPath.isNotEmpty()) {
                Text(
                    text = "Last export: $lastExportPath (adb pull from app files dir works)",
                    style = GolfTypography.Status,
                    color = GolfColors.TextMuted,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("RENAME ${renameTarget?.name}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = {
                            renameText = it.take(20)
                            renameRejected = false
                        },
                        singleLine = true,
                    )
                    if (renameRejected) {
                        Text(
                            text = if (renameText.isBlank()) "ENTER A NAME" else if (renameText.contains(',')) "NO COMMAS" else "ALREADY IN BAG",
                            style = GolfTypography.Status,
                            color = GolfColors.AlertRed,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = renameTarget ?: return@TextButton
                        scope.launch {
                            if (onRenameClub(target.id, renameText.trim())) {
                                renameTarget = null
                            } else {
                                renameRejected = true
                            }
                        }
                    },
                ) { Text("CONFIRM") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("CANCEL") }
            },
        )
    }
}
```

- [ ] **Step 2: Wire the SETTINGS branch in `AppRoot.kt`**

Replace `RangeTab.SETTINGS -> SettingsScreen(captureLog = captureLog)` (inside the `when (tab)` from Task 7) with:

```kotlin
                    RangeTab.SETTINGS -> {
                        // Getter flow — remember-capture once (Task 4b).
                        val clubRecords by remember(sessionRepository) {
                            sessionRepository.clubs
                        }.collectAsState(initial = emptyList())
                        SettingsScreen(
                            captureLog = captureLog,
                            clubs = clubRecords,
                            onAddClub = { sessionRepository.addClub(it) },
                            onRenameClub = { id, name -> sessionRepository.renameClub(id, name) },
                            onDeleteClub = { id -> scope.launch { sessionRepository.deleteClub(id) } },
                        )
                    }
```

- [ ] **Step 3: Verify build + tests**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:test :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(app): bag editor section in settings"
```

---

### Task 9: History screen (master-detail, retag, rename)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryFormats.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryScreen.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/history/HistoryFormatsTest.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` (enum, NavRail, state, `when` branch)

**Interfaces:**
- Consumes: Task 4's `SessionRepository`: `summaries: Flow<List<SessionSummary>>` (already `liveOnly`-filtered), `liveOnly: MutableStateFlow<Boolean>`, `clubs: Flow<List<ClubRecord>>`, `observeShots(sessionId: Long): Flow<List<ShotRecord>>`, `suspend retagShots(ids: List<Long>, clubName: String?)`, `suspend renameSession(id: Long, title: String)`; Task 2's `SessionTitles.auto(startedAtEpochMs)`, `SessionSummary(id, startedAtEpochMs, endedAtEpochMs, title, misreadCount, shotCount, avgCarryM, maxCarryM, clubNames, hasDemoShot, isOpen)`, `ShotRecord(id, sessionId, seq, timestampMs, source, clubName, ballData, carryM, totalM, sideM, apexM, flightTimeSec)`.
- Produces: `HistoryScreen(modifier = Modifier, summaries, liveOnly, onToggleLiveOnly, shots, selectedSessionId, onSelectSession, clubNames, onRetag, onRenameSession)` consumed by AppRoot this task; `HistoryFormats` object (pure, tested).

TDD split: the table/meta formatting rules are pure JVM logic → tested here (repo convention); the master-detail/long-select mechanics are presentation (no Compose test harness in repo) → verified in Task 10's device checklist.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/hpsmiles/golfsim/history/HistoryFormatsTest.kt
package com.hpsmiles.golfsim.history

import com.hpsmiles.golfsim.core.data.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFormatsTest {

    private fun summary(shotCount: Int, avgCarryM: Double?, clubNames: List<String>) = SessionSummary(
        id = 1L,
        startedAtEpochMs = 0L,
        endedAtEpochMs = null,
        title = null,
        misreadCount = 0,
        shotCount = shotCount,
        avgCarryM = avgCarryM,
        maxCarryM = null,
        clubNames = clubNames,
        hasDemoShot = false,
        isOpen = false,
    )

    @Test
    fun `side uses explicit plus and unicode minus`() {
        assertEquals("+4.2 m", HistoryFormats.side(4.18))
        assertEquals("−3.1 m", HistoryFormats.side(-3.14))
    }

    @Test
    fun `carry is whole metres`() {
        assertEquals("139 m", HistoryFormats.carry(138.6))
    }

    @Test
    fun `session meta joins count, average and clubs`() {
        assertEquals("1 shot", HistoryFormats.sessionMeta(summary(1, null, emptyList())))
        assertEquals(
            "6 shots · avg 139 m · 7i, SW",
            HistoryFormats.sessionMeta(summary(6, 138.9, listOf("7i", "SW"))),
        )
    }

    @Test
    fun `club null renders dash`() {
        assertEquals("—", HistoryFormats.clubOrDash(null))
        assertEquals("7i", HistoryFormats.clubOrDash("7i"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:test --tests "com.hpsmiles.golfsim.history.HistoryFormatsTest"`
Expected: compile error — `HistoryFormats` unresolved (and `history` package does not exist yet).

- [ ] **Step 3: Implement `HistoryFormats.kt`**

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryFormats.kt
package com.hpsmiles.golfsim.history

import com.hpsmiles.golfsim.core.data.SessionSummary
import java.util.Locale

/** Metres/second to mph for display (same constant as the range panel). */
private const val MPH_PER_MS = 2.23694

/**
 * Pure formatting for the history screen — unit-testable JVM, no Compose.
 * Minus sign is U+2212 per the M5 mockups (review-layout/retag-interaction).
 */
object HistoryFormats {

    fun carry(m: Double): String = String.format(Locale.US, "%.0f m", m)

    fun side(sideM: Double): String =
        String.format(Locale.US, "%+.1f m", sideM).replace('-', '−')

    fun ballMph(ballSpeedMps: Double): String =
        String.format(Locale.US, "%.1f mph", ballSpeedMps * MPH_PER_MS)

    fun spin(rpm: Int): String = String.format(Locale.US, "%d rpm", rpm)

    fun clubOrDash(clubName: String?): String = clubName ?: "—"

    fun sessionMeta(s: SessionSummary): String {
        val count = if (s.shotCount == 1) "1 shot" else "${s.shotCount} shots"
        val avg = s.avgCarryM?.let { " · avg ${carry(it)}" } ?: ""
        val clubs = if (s.clubNames.isEmpty()) "" else " · ${s.clubNames.joinToString(", ")}"
        return count + avg + clubs
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:test --tests "com.hpsmiles.golfsim.history.HistoryFormatsTest"`
Expected: PASS — 4 tests green.

- [ ] **Step 5: Create `HistoryScreen.kt`**

Complete file in two parts (split for readability — it is ONE file). Part 1 of 2 — screen, session list, badges:

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryScreen.kt
package com.hpsmiles.golfsim.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.data.SessionSummary
import com.hpsmiles.golfsim.core.data.SessionTitles
import com.hpsmiles.golfsim.core.data.ShotRecord
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.MetricChip

/**
 * M5 (spec §6 D5/D6, mockups review-layout + retag-interaction):
 * master-detail session history. Left: session summary list with the
 * LIVE ONLY / ALL toggle (backed by the repository's liveOnly flow).
 * Right: stat chips + shot table; long-press a row to start multi-select,
 * then pick a club chip (or "—") and APPLY to retag. Tapping the session
 * title opens a rename dialog.
 */
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    summaries: List<SessionSummary>,
    liveOnly: Boolean,
    onToggleLiveOnly: () -> Unit,
    shots: List<ShotRecord>,
    selectedSessionId: Long?,
    onSelectSession: (Long) -> Unit,
    clubNames: List<String>,
    onRetag: (List<Long>, String?) -> Unit,
    onRenameSession: (Long, String) -> Unit,
) {
    // Selection + retag choice reset whenever the viewed session changes.
    var selectedIds by remember(selectedSessionId) { mutableStateOf(setOf<Long>()) }
    var retagIndex by remember(selectedSessionId) { mutableStateOf(-1) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val retagChoices = remember(clubNames) { listOf<String?>(null) + clubNames }
    val selected = summaries.firstOrNull { it.id == selectedSessionId }

    Box(modifier = modifier.fillMaxSize().background(GolfColors.Base)) {
        Row(modifier = Modifier.fillMaxSize()) {
            SessionList(summaries, selectedSessionId, liveOnly, onToggleLiveOnly, onSelectSession)
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                if (selected == null) {
                    Text(
                        text = "SELECT A SESSION",
                        style = GolfTypography.Status,
                        color = GolfColors.TextMuted,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    SessionDetail(
                        session = selected,
                        shots = shots,
                        selectedIds = selectedIds,
                        onSelectionChange = { selectedIds = it },
                        onRename = {
                            renameText = selected.title ?: ""
                            showRename = true
                        },
                    )
                }
                if (selectedIds.isNotEmpty()) {
                    RetagBar(
                        count = selectedIds.size,
                        choices = retagChoices,
                        choiceIndex = retagIndex,
                        onChoose = { retagIndex = it },
                        onApply = {
                            onRetag(selectedIds.toList(), retagChoices.getOrNull(retagIndex))
                            selectedIds = emptySet()
                            retagIndex = -1
                        },
                        onCancel = {
                            selectedIds = emptySet()
                            retagIndex = -1
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        if (showRename && selected != null) {
            AlertDialog(
                onDismissRequest = { showRename = false },
                title = { Text("RENAME SESSION") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it.take(40) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRenameSession(selected.id, renameText.trim())
                            showRename = false
                        },
                    ) { Text("CONFIRM") }
                },
                dismissButton = {
                    TextButton(onClick = { showRename = false }) { Text("CANCEL") }
                },
            )
        }
    }
}

@Composable
private fun SessionList(
    summaries: List<SessionSummary>,
    selectedSessionId: Long?,
    liveOnly: Boolean,
    onToggleLiveOnly: () -> Unit,
    onSelectSession: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxSize()
            .background(GolfColors.Panel)
            .verticalScroll(rememberScrollState())
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
            listOf("LIVE ONLY" to liveOnly, "ALL" to !liveOnly).forEach { (label, active) ->
                Text(
                    text = label,
                    style = GolfTypography.Status,
                    color = if (active) GolfColors.Teal else GolfColors.TextMuted,
                    modifier = Modifier
                        .clickable { if (!active) onToggleLiveOnly() }
                        .border(1.dp, if (active) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                )
            }
        }
        if (summaries.isEmpty()) {
            Text("NO SESSIONS YET", style = GolfTypography.Status, color = GolfColors.TextMuted)
        }
        summaries.forEach { s ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSession(s.id) }
                    .background(if (s.id == selectedSessionId) GolfColors.Base else Color.Transparent)
                    .border(
                        1.dp,
                        if (s.id == selectedSessionId) GolfColors.Teal else GolfColors.Line,
                        RoundedCornerShape(GolfSpacing.Sm),
                    )
                    .padding(GolfSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = s.title ?: SessionTitles.auto(s.startedAtEpochMs),
                        style = GolfTypography.Body,
                        color = GolfColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (s.isOpen) {
                        Badge("OPEN", GolfColors.Teal)
                        Spacer(Modifier.width(GolfSpacing.Xs))
                    }
                    if (s.hasDemoShot) {
                        Badge("DEMO", GolfColors.Amber)
                    }
                }
                Text(
                    text = HistoryFormats.sessionMeta(s),
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun Badge(label: String, color: Color) {
    Text(
        text = label,
        style = GolfTypography.Status.copy(fontSize = 11.sp),
        color = color,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = GolfSpacing.Sm, vertical = 1.dp),
    )
}
```

Part 2 of 2 — detail pane, shot table cells, retag bar (append after `Badge` in the same file):

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDetail(
    session: SessionSummary,
    shots: List<ShotRecord>,
    selectedIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onRename: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        Text(
            text = session.title ?: SessionTitles.auto(session.startedAtEpochMs),
            style = GolfTypography.ScreenTitle,
            color = GolfColors.TextPrimary,
            modifier = Modifier.clickable(onClick = onRename),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            MetricChip("shots", "${session.shotCount}", null)
            MetricChip("misreads", "${session.misreadCount}", null, accent = GolfColors.AlertRed)
            session.avgCarryM?.let {
                MetricChip("avg carry", String.format(java.util.Locale.US, "%.0f", it), "M")
            }
            session.maxCarryM?.let {
                MetricChip("longest", String.format(java.util.Locale.US, "%.0f", it), "M")
            }
        }
        // Shot table (D5): # CLUB CARRY SIDE BALL SPIN.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("#", 36.dp)
            HeaderCell("CLUB", 64.dp)
            HeaderCell("CARRY", 80.dp)
            HeaderCell("SIDE", 84.dp)
            HeaderCell("BALL", 88.dp)
            HeaderCell("SPIN", 104.dp)
        }
        shots.forEach { shot ->
            val selected = shot.id in selectedIds
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            // D6: tap toggles membership only while a
                            // selection is active (long-press starts it).
                            if (selectedIds.isNotEmpty()) {
                                onSelectionChange(
                                    if (selected) selectedIds - shot.id else selectedIds + shot.id,
                                )
                            }
                        },
                        onLongClick = {
                            if (!selected) onSelectionChange(selectedIds + shot.id)
                        },
                    )
                    .background(if (selected) GolfColors.Panel else Color.Transparent)
                    .border(
                        1.dp,
                        if (selected) GolfColors.Teal else Color.Transparent,
                        RoundedCornerShape(GolfSpacing.Sm),
                    )
                    .padding(vertical = 4.dp),
            ) {
                Cell("${shot.seq + 1}", 36.dp)
                Cell(HistoryFormats.clubOrDash(shot.clubName), 64.dp)
                Cell(HistoryFormats.carry(shot.carryM), 80.dp)
                Cell(HistoryFormats.side(shot.sideM), 84.dp)
                Cell(HistoryFormats.ballMph(shot.ballData.ballSpeed), 88.dp)
                Cell(HistoryFormats.spin(shot.ballData.totalSpin), 104.dp)
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        style = GolfTypography.MetricLabel,
        color = GolfColors.TextMuted,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun Cell(text: String, width: Dp) {
    Text(
        text = text,
        style = GolfTypography.Body,
        color = GolfColors.TextPrimary,
        modifier = Modifier.width(width),
    )
}

/** Bottom bar while rows are selected: club chips + APPLY / CANCEL (D6). */
@Composable
private fun RetagBar(
    count: Int,
    choices: List<String?>,
    choiceIndex: Int,
    onChoose: (Int) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(GolfSpacing.CornerCard),
        color = GolfColors.Card,
        modifier = modifier.padding(GolfSpacing.Md),
    ) {
        Column(
            modifier = Modifier.padding(GolfSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                choices.forEachIndexed { i, name ->
                    Text(
                        text = name ?: "—",
                        style = GolfTypography.Status.copy(fontSize = 15.sp),
                        color = if (i == choiceIndex) GolfColors.Teal else GolfColors.TextPrimary,
                        modifier = Modifier
                            .clickable { onChoose(i) }
                            .border(
                                1.dp,
                                if (i == choiceIndex) GolfColors.Teal else GolfColors.Line,
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
            ) {
                Text(
                    text = "RETAG $count",
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "CANCEL",
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.TextSecondary,
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                )
                val canApply = choiceIndex >= 0
                Text(
                    text = "APPLY",
                    style = GolfTypography.MetricLabel,
                    color = if (canApply) GolfColors.Teal else GolfColors.TextMuted,
                    modifier = Modifier
                        .clickable { if (canApply) onApply() }
                        .border(
                            1.dp,
                            if (canApply) GolfColors.Teal else GolfColors.Line,
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 6: Wire the HISTORY tab in `AppRoot.kt`**

(a) Extend the tab enum:

```kotlin
private enum class RangeTab { RANGE, SETTINGS, HISTORY }
```

(b) Add imports (Task 6 already added `LaunchedEffect`):

```kotlin
import com.hpsmiles.golfsim.history.HistoryScreen
import kotlinx.coroutines.flow.flowOf
```

(c) Add history state before `var tab by remember { mutableStateOf(RangeTab.RANGE) }`:

```kotlin
    // M5 Task 9: history tab state.
    // summaries is a getter flow — remember-capture once (Task 4b).
    val summaries by remember(sessionRepository) { sessionRepository.summaries }
        .collectAsState(initial = emptyList())
    val liveOnly by sessionRepository.liveOnly.collectAsState()
    var historySelectedId by remember { mutableStateOf<Long?>(null) }
    val historyShots by remember(historySelectedId) {
        historySelectedId?.let { sessionRepository.observeShots(it) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // Newest session is selected by default; an absent selection falls back
    // to newest so the detail pane never points at a ghost row.
    LaunchedEffect(summaries) {
        val ids = summaries.map { it.id }
        if (historySelectedId == null || historySelectedId !in ids) {
            historySelectedId = summaries.firstOrNull()?.id
        }
    }
```

(d) In the NavRail, directly under the SETTINGS button, add:

```kotlin
                    NavRailButton("HISTORY", tab == RangeTab.HISTORY, onClick = { tab = RangeTab.HISTORY })
```

(e) Add the HISTORY branch to the `when (tab)`:

```kotlin
                    RangeTab.HISTORY -> {
                        // Getter flow — remember-capture once (Task 4b).
                        val clubRecords by remember(sessionRepository) {
                            sessionRepository.clubs
                        }.collectAsState(initial = emptyList())
                        HistoryScreen(
                            Modifier.weight(1f),
                            summaries = summaries,
                            liveOnly = liveOnly,
                            onToggleLiveOnly = {
                                sessionRepository.liveOnly.value = !sessionRepository.liveOnly.value
                            },
                            shots = historyShots,
                            selectedSessionId = historySelectedId,
                            onSelectSession = { historySelectedId = it },
                            clubNames = clubRecords.map { it.name },
                            onRetag = { ids, club ->
                                scope.launch { sessionRepository.retagShots(ids, club) }
                            },
                            onRenameSession = { id, title ->
                                scope.launch { sessionRepository.renameSession(id, title) }
                            },
                        )
                    }
```

- [ ] **Step 7: Verify build + tests**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:test :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` — `HistoryFormatsTest` (4 tests) green, full existing suite green, APK assembles.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/history app/src/test/kotlin/com/hpsmiles/golfsim/history app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(app): history screen with retag and rename"
```

---

### Task 10: Full verification + on-device exit checklist (M5 close-out)

**Files:**
- Modify: none planned. `ROADMAP.md` stays untouched — its convention records milestone completion via git history, not inline markers (M0–M4 are all complete with no checkmarks in the file).
- Commit only if device verification uncovers a fix.

**Interfaces:**
- Consumes: everything from Tasks 1–9.
- Produces: verified M5 exit criterion — "sessions survive app restart" (ROADMAP M5) — plus the spec §9 on-device checklist.

- [ ] **Step 1: Full build**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build`
Expected: `BUILD SUCCESSFUL` — includes `:core:data` KSP/Room compilation and every module's unit tests.

- [ ] **Step 2: Full test run (explicit, per repo convention)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat test`
Expected: `BUILD SUCCESSFUL` — all tests green across `core/ble`, `core/physics`, `core/connect`, `core/data`, `app`.

- [ ] **Step 3: Install on the tablet (Lenovo TB373FU, USB-connected)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:installDebug`
Expected: `BUILD SUCCESSFUL`, installs `com.hpsmiles.golfsim`.

- [ ] **Step 4: On-device scenario A — tagging, retag, rename, END, restart (DEMO mode)**

adb helper for force-stops: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am force-stop com.hpsmiles.golfsim`

- [ ] Launch the app; the club pill top-left of the range reads `— ▾` (Line border while untagged).
- [ ] FIRE 3 demo shots untagged; right panel SESSION shows `shots 3`.
- [ ] Tap the pill → picker overlay opens; tap `7i` → overlay closes, pill reads `7i ▾` with Teal border.
- [ ] FIRE 2 more shots → 5 total (3 untagged, 2 × 7i).
- [ ] In the picker's ＋ ADD form: type `7i` → ADD → `ALREADY IN BAG` (repository rule, not a local guess); then type `7i-A` → ADD → new tile appears (M7 duplicate-name pattern works); then type `7i,` → ADD → `NO COMMAS` (repository comma rule).
- [ ] HISTORY tab: newest session (auto-title "EEE d MMM · HH:mm" format, DEMO + OPEN badges) is selected by default; stat chips show the right shot count; `LIVE ONLY` is on and the list is EMPTY (demo-only) — toggle `ALL` → the session appears (spec D4).
- [ ] Long-press one untagged row → Teal selection + retag bar; tap two more rows; choose `8i`; APPLY → those rows' CLUB column reads `8i`, list club summary updates.
- [ ] Tap the session title → rename dialog pre-filled → type `Demo tag test` → CONFIRM → list row + detail title update. Rename again with only spaces → CONFIRM → title returns to the auto-title (repo blank ⇒ null).
- [ ] RANGE tab: `END SESSION` rail chip present → tap → chip disappears. FIRE once → a NEW session auto-starts (chip reappears); SESSION panel resets to 1 shot.
- [ ] Force-stop (command above), relaunch → HISTORY: both sessions listed with correct counts (first closed with 5, second OPEN with 1); no DB WRITE FAILING strip at any point.

- [ ] **Step 5: On-device scenario B — open-session restart continuity**

- [ ] From the open 1-shot session: FIRE 3 more (4 open-session shots on the mat as resting balls).
- [ ] Force-stop WITHOUT ending the session; relaunch.
- [ ] RANGE: the 4 previous shots render as resting balls at their landing spots (scalar-backed restore — no replay animation for restored shots is expected); SESSION panel shows `shots 4`; HISTORY shows the same single session, still OPEN.
- [ ] FIRE 1 more → count goes to 5 on the SAME session (no second session row in HISTORY).

- [ ] **Step 5b: On-device DB-corruption recovery (the real-throw path the JVM cannot simulate)**

- [ ] With the app holding data from scenario B, force-stop it (command in Step 4).
- [ ] Overwrite the live database with garbage (debug builds allow `run-as`): `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell run-as com.hpsmiles.golfsim dd if=/dev/urandom of=databases/golfsim.db bs=1024 count=64`
- [ ] Relaunch: the app must NOT crash. SQLite on-device rejects the unreadable header, `initializeAndRestore()` catches it and runs the recovery path Robolectric could not reach: the corrupt file is archived, a fresh DB is created, and the StatusStrip shows `DB WRITE FAILING` (persistError set at recovery).
- [ ] FIRE 1 demo shot → the error strip clears (next successful write resets persistError); HISTORY shows a fresh session list (the corrupt data is archived, not shown); the bag still has all 14 default clubs (reseeded).
- [ ] Confirm the archive exists: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell run-as com.hpsmiles.golfsim ls databases/` → `golfsim.db` plus a `corrupt-<timestamp>-golfsim.db` (and no stale `-journal`/`-wal`/`-shm` sidecars for the old file).

- [ ] **Step 6: On-device scenario C — live shed session (user-gated; needs the MLM2PRO)**

- [ ] Per the Settings RAPSODO AUTH card: re-authorize in the Rapsodo app (Play → Simulation → 3rd Party Apps → Awesome Golf → Authenticate Now) if the 24h token has lapsed.
- [ ] Settings → DEBUG capture: CAPTURE ON before connecting.
- [ ] CONNECT → ARM (StatusStrip ARMED); tag a club (e.g., `8i`) on the pill; hit ~10 shots.
- [ ] Each shot renders live AND the HISTORY session grows (opening HISTORY mid-session shows live counts); a deliberate duff increments the no-read pill AND the HISTORY misreads stat chip by the same count.
- [ ] END SESSION; compare HISTORY carry/total numbers side-by-side with the Rapsodo app's session readouts (M2 tolerance).
- [ ] EXPORT the capture fixture; note the raw `unknown1`/`unknown2` ints (MEASUREMENT offsets 12–15) next to the app's carry/total for those shots — the distance-candidate experiment from `docs/research/2026-09-26-mlm2pro-club-data-and-transports.md` (record findings wherever convenient; no code depends on them).
- [ ] Relaunch once after END: history intact, no write errors.

- [ ] **Step 7: Wrap-up**

- [ ] `git status` clean; Tasks 1–9 commits landed. If verification surfaced fixes, commit them in repo style (`fix(app): …` / `fix(data): …`). M5 is complete — exit criterion proven.

---
*Plan end. ROADMAP.md intentionally unmodified (repo convention: completion lives in git history). Spec: `docs/superpowers/specs/2026-09-26-m5-sessions-persistence-design.md`.*
