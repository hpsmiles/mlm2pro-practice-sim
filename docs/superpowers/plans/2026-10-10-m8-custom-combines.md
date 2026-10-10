# M8 Custom Combines Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship M8 — catalog combines (WEDGES 50/70/90 m, APPROACH 100 m/150 m/driver) played as an interleaved 18-shot round-robin, scored as strokes-gained handicap estimates against four skill bands, persisted to Room v6, resumable, and shown in HISTORY > GAMES.

**Architecture:** New pure-JVM `:core:scoring` module owns the SG data (generated Kotlin tables from the vendored CSV) and all scoring math (closed-form piecewise-linear handicap root-solve). The app-side `app/.../combine/` package follows the games/bag/fitting discipline: plain Compose state holders + painters (no ViewModels, no Navigation-Compose); all decision logic is pure and JVM-tested (`CombineCatalog`, `CombinePlan`, `CombineLie`, `CombineResults`, `CombineFormats`, `CombineRecord`). Shots are write-through persisted per shot (bag-mapping pattern); results render from in-memory facts; resume/history read from Room.

**Tech Stack:** Kotlin, Jetpack Compose, Room v6, JUnit (pure modules), Node (dev-time table generation only).

**Spec:** `docs/superpowers/specs/2026-10-10-m8-custom-combines-design.md` (commits 9ff1de3, d293b57, a1488d6)
---

## Build commands (Windows, per AGENTS.md)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # before any Gradle command
.\gradlew.bat build                                              # assemble + all tests
.\gradlew.bat test                                               # all unit tests
.\gradlew.bat :core:scoring:test                                 # pure-JVM module (plain `test` task, like :core:physics)
.\gradlew.bat :core:data:testDebugUnitTest                       # Android variant modules use the typed task
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombinePlanTest"
```

Gradle flags (e.g. `--console=plain`) must precede task names; quiet during configure is normal (runs take 5s–2min).

## File Structure

```
core/scoring/build.gradle.kts                    (new — pure JVM module build file)
core/scoring/tools/sg_reference_full.csv         (exists — vendored source, commit d293b57)
core/scoring/tools/generate-sg-tables.cjs        (new — Node generator, dev-time only)
core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/
    SkillBand.kt                                 (new — band enum)
    Lie.kt                                       (new — lie enum + table arrays)
    SgTables.kt                                  (generated — distance grids + values)
    ExpectedStrokes.kt                           (new — interpolation + strokesGained)
    HandicapMatch.kt                             (new — root-solve + uncertainty)
core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/
    SgTablesDataTest.kt                          (new)
    ExpectedStrokesTest.kt                       (new)
    HandicapMatchTest.kt                         (new)
core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/
    CombineEntities.kt                           (new — combine_sessions + combine_shots)
    CombineDaos.kt                               (new — CombineSessionDao + CombineShotDao)
    Mlm2proDatabase.kt                           (mod — version 6, entities, DAOs, MIGRATION_5_6)
    SessionRepository.kt                         (mod — combine ops + addMigrations)
core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/6.json  (generated + committed)
core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/
    MigrationFrom5Test.kt                        (new)
    CombineRepositoryTest.kt                     (new)
app/build.gradle.kts                             (mod — implementation(project(":core:scoring")))
app/src/main/kotlin/com/hpsmiles/golfsim/combine/
    CombineCatalog.kt                            (new — v1 catalog data + constants)
    CombinePlan.kt                               (new — index/round-robin math, rest distances)
    CombineLie.kt                                (new — lie classification + physics provider)
    CombineGame.kt                               (new — session state holder)
    CombineResults.kt                            (new — aggregation, facts mapping)
    CombineFormats.kt                            (new — display formatting)
    CombineRecord.kt                             (new — Double-based record compare)
    CombineCanvas.kt                             (new — play canvas)
    CombineMetricsPanel.kt                       (new — LAST SHOT + TARGETS panel)
    CombinePlay.kt                               (new — play screen)
    CombineResultsView.kt                        (new — full-screen results, readOnly drill)
    CombineSetup.kt                              (new — catalog picker + resume + discard dialog)
app/src/test/kotlin/com/hpsmiles/golfsim/combine/
    CombinePlanTest.kt                           (new)
    CombineLieTest.kt                            (new)
    CombineResultsTest.kt                        (new)
app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt    (mod — GameMode.COMBINE, third card, setup, play branch)
app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt        (mod — state, flows, routeShot, completion, resume, requestTab)
app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryScreen.kt (mod — combine section + read-only drill)
settings.gradle.kts                              (mod — include(":core:scoring"))
docs/superpowers/plans/2026-10-10-m8-custom-combines.md          (this file)
```

---

### Task 1: Scaffold `:core:scoring`

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/scoring/build.gradle.kts`

- [ ] **Step 0: Ensure the feature branch** (branch `m8-custom-combines` was created
  at master HEAD during the execution handoff — if it already exists this is a no-op)

```powershell
git switch m8-custom-combines
```

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, immediately after `include(":core:physics")` add:

```kotlin
include(":core:scoring")
```

- [ ] **Step 2: Create `core/scoring/build.gradle.kts`** (mirror `core/physics/build.gradle.kts` exactly)

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
```

- [ ] **Step 3: Verify the module builds**

Run: `.\gradlew.bat :core:scoring:build`
Expected: `BUILD SUCCESSFUL` (module has no sources yet — that is fine).

- [ ] **Step 4: Commit**

```powershell
git add settings.gradle.kts core/scoring/build.gradle.kts
git commit -m "feat: add :core:scoring module scaffold"
```

---

### Task 2: SG table generation + data integrity tests

**Files:**
- Create: `core/scoring/tools/generate-sg-tables.cjs`
- Create (generated): `core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SgTables.kt`
- Test: `core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/SgTablesDataTest.kt`

- [ ] **Step 1: Write the failing test**

`SgTablesDataTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SgTablesDataTest {

    @Test
    fun `grid lengths match the source series 1`() {
        assertEquals(60, SgTables.PUTT_DISTANCES.size)
        assertEquals(120, SgTables.FAIRWAY_DISTANCES.size)
        assertEquals(120, SgTables.ROUGH_DISTANCES.size)
        assertEquals(120, SgTables.TEE_DISTANCES.size)
    }

    @Test
    fun `grid endpoints match the source`() {
        assertEquals(0.3, SgTables.PUTT_DISTANCES.first(), 1e-9)
        assertEquals(18.29, SgTables.PUTT_DISTANCES.last(), 1e-9)
        assertEquals(4.6, SgTables.FAIRWAY_DISTANCES.first(), 1e-9)
        assertEquals(548.6, SgTables.FAIRWAY_DISTANCES.last(), 1e-9)
        assertEquals(91.0, SgTables.TEE_DISTANCES.first(), 1e-9)
        assertEquals(636.0, SgTables.TEE_DISTANCES.last(), 1e-9)
    }

    @Test
    fun `distance grids ascend strictly`() {
        for (grid in listOf(
            SgTables.PUTT_DISTANCES, SgTables.FAIRWAY_DISTANCES,
            SgTables.ROUGH_DISTANCES, SgTables.TEE_DISTANCES,
        )) {
            for (i in 1 until grid.size) {
                assertTrue("grid[$i] not ascending", grid[i] > grid[i - 1])
            }
        }
    }

    @Test
    fun `values are finite and band ordering holds at every point`() {
        val perLie = mapOf(
            SgTables.FAIRWAY_DISTANCES to listOf(
                SgTables.FAIRWAY_TOUR, SgTables.FAIRWAY_SCRATCH,
                SgTables.FAIRWAY_HCP10, SgTables.FAIRWAY_HCP18,
            ),
            SgTables.PUTT_DISTANCES to listOf(
                SgTables.PUTT_TOUR, SgTables.PUTT_SCRATCH,
                SgTables.PUTT_HCP10, SgTables.PUTT_HCP18,
            ),
            SgTables.ROUGH_DISTANCES to listOf(
                SgTables.ROUGH_TOUR, SgTables.ROUGH_SCRATCH,
                SgTables.ROUGH_HCP10, SgTables.ROUGH_HCP18,
            ),
            SgTables.TEE_DISTANCES to listOf(
                SgTables.TEE_TOUR, SgTables.TEE_SCRATCH,
                SgTables.TEE_HCP10, SgTables.TEE_HCP18,
            ),
        )
        for ((grid, bands) in perLie) {
            for (band in bands) assertEquals(grid.size, band.size)
            for (i in grid.indices) {
                val v = bands.map { it[i] }
                v.forEach { assertTrue("non-finite value", it.isFinite()) }
                assertTrue("tour <= scratch at $i", v[0] <= v[1])
                assertTrue("scratch <= hcp10 at $i", v[1] <= v[2])
                assertTrue("hcp10 <= hcp18 at $i", v[2] <= v[3])
            }
        }
    }

    @Test
    fun `exact source rows are preserved`() {
        // tour/fairway 91.4 m (100 y) is an exact CSV row
        val index = SgTables.FAIRWAY_DISTANCES.indexOfFirst { it == 91.4 }
        assertTrue(index >= 0)
        assertEquals(2.80, SgTables.FAIRWAY_TOUR[index], 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:scoring:test`
Expected: FAIL — compilation error `unresolved reference: SgTables`.

- [ ] **Step 3: Write the generation script**

`core/scoring/tools/generate-sg-tables.cjs`:

```javascript
#!/usr/bin/env node
// Generates SgTables.kt from sg_reference_full.csv (source: tapinstats.com, user-owned).
// Series 1 = rows before the first distance decrease within each (band, lie);
// the relabel series (yard counts relabelled as metres) is ignored.
// Usage: node tools/generate-sg-tables.cjs   (run from core/scoring/)
const fs = require("fs");
const path = require("path");

const BANDS = ["tour", "scratch", "hcp10", "hcp18"];
const LIES = ["putt", "fairway", "rough", "tee"];
const BAND_CONST = { tour: "TOUR", scratch: "SCRATCH", hcp10: "HCP10", hcp18: "HCP18" };
const LIE_CONST = { putt: "PUTT", fairway: "FAIRWAY", rough: "ROUGH", tee: "TEE" };

const csvPath = path.join(__dirname, "sg_reference_full.csv");
const lines = fs.readFileSync(csvPath, "utf8").split(/\r?\n/).filter((l) => l.trim().length > 0);
const header = lines.shift();
if (!header.startsWith("skill_level,lie_type")) throw new Error("unexpected header: " + header);

const data = {};
for (const line of lines) {
  const [band, lie, unit, dStr, eStr] = line.split(",");
  if (unit !== "m") throw new Error("non-metre unit: " + line);
  if (!LIES.includes(lie) || !BANDS.includes(band)) continue;
  (data[lie] ??= {});
  (data[lie][band] ??= []).push([parseFloat(dStr), parseFloat(eStr)]);
}

for (const lie of LIES) {
  for (const band of BANDS) {
    const rows = data[lie][band];
    const series1 = [];
    let prev = -Infinity;
    for (const row of rows) {
      if (row[0] < prev) break;
      series1.push(row);
      prev = row[0];
    }
    data[lie][band] = series1;
  }
  const grid = data[lie][BANDS[0]].map((r) => r[0]);
  for (const band of BANDS) {
    const g = data[lie][band].map((r) => r[0]);
    if (g.length !== grid.length || g.some((v, i) => Math.abs(v - grid[i]) > 1e-9)) {
      throw new Error(`grid mismatch for ${lie}/${band}`);
    }
  }
}

const fmt = (v) => {
  let s = Number(v.toFixed(6)).toString();
  if (!s.includes(".") && !s.includes("e")) s += ".0";
  return s;
};
const fmtArr = (arr) => arr.map(fmt).join(", ");

let out = "";
out += "// GENERATED FILE - DO NOT EDIT BY HAND.\n";
out += "// Source: tools/sg_reference_full.csv (tapinstats.com, user-owned), series 1 (true metres) only.\n";
out += "// Regenerate with: node tools/generate-sg-tables.cjs\n";
out += "package com.hpsmiles.golfsim.core.scoring\n\n";
out += "/** Expected-strokes grids per lie (one shared distance axis) plus values per skill band. */\n";
out += "object SgTables {\n";
for (const lie of LIES) {
  out += `    val ${LIE_CONST[lie]}_DISTANCES: DoubleArray = doubleArrayOf(${fmtArr(data[lie][BANDS[0]].map((r) => r[0]))})\n`;
  for (const band of BANDS) {
    out += `    val ${LIE_CONST[lie]}_${BAND_CONST[band]}: DoubleArray = doubleArrayOf(${fmtArr(data[lie][band].map((r) => r[1]))})\n`;
  }
}
out += "}\n";

const outPath = path.join(__dirname, "..", "src", "main", "kotlin", "com", "hpsmiles", "golfsim", "core", "scoring", "SgTables.kt");
fs.writeFileSync(outPath, out);
console.log("wrote " + outPath);
```

- [ ] **Step 4: Generate the tables**

Run: `node tools/generate-sg-tables.cjs` from `core/scoring/`
Expected: `wrote ...SgTables.kt`. Sanity-check the file head (first ~10 lines): the generated header comment plus the first `DoubleArray` declarations.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `.\gradlew.bat :core:scoring:test`
Expected: `BUILD SUCCESSFUL`, all `SgTablesDataTest` tests green.

- [ ] **Step 6: Commit**

```powershell
git add core/scoring/tools/generate-sg-tables.cjs core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SgTables.kt core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/SgTablesDataTest.kt
git commit -m "feat(scoring): generate SG tables from vendored CSV + data integrity tests"
```

---

### Task 3: Scoring API — `SkillBand`, `Lie`, `expectedStrokes`, `strokesGained`

**Files:**
- Create: `core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SkillBand.kt`
- Create: `core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/Lie.kt`
- Create: `core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/ExpectedStrokes.kt`
- Test: `core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/ExpectedStrokesTest.kt`

- [ ] **Step 1: Write the failing test**

`ExpectedStrokesTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpectedStrokesTest {

    @Test
    fun `exact rows are preserved`() {
        assertEquals(2.80, expectedStrokes(SkillBand.TOUR, Lie.FAIRWAY, 91.4), 1e-9)
    }

    @Test
    fun `relabel series is rejected`() {
        // series 2 would read putt 5 m as 1.23; series 1 gives 1.857
        assertEquals(1.857, expectedStrokes(SkillBand.SCRATCH, Lie.GREEN, 5.0), 0.002)
    }

    @Test
    fun `interpolation between rows matches the verified values`() {
        assertEquals(3.0267, expectedStrokes(SkillBand.SCRATCH, Lie.FAIRWAY, 100.0), 1e-3)
        assertEquals(3.3854, expectedStrokes(SkillBand.HCP10, Lie.FAIRWAY, 100.0), 1e-3)
        assertEquals(2.8274, expectedStrokes(SkillBand.TOUR, Lie.FAIRWAY, 100.0), 1e-3)
        assertEquals(3.9835, expectedStrokes(SkillBand.HCP18, Lie.FAIRWAY, 100.0), 1e-3)
    }

    @Test
    fun `values clamp at the grid ends`() {
        assertEquals(
            expectedStrokes(SkillBand.SCRATCH, Lie.GREEN, 0.30),
            expectedStrokes(SkillBand.SCRATCH, Lie.GREEN, 0.10),
            1e-12,
        )
        assertEquals(
            expectedStrokes(SkillBand.SCRATCH, Lie.TEE, 636.0),
            expectedStrokes(SkillBand.SCRATCH, Lie.TEE, 700.0),
            1e-12,
        )
    }

    @Test
    fun `strokesGained is start minus end minus one`() {
        // 100 m fairway to 10 m green, scratch band
        val sg = strokesGained(SkillBand.SCRATCH, Lie.FAIRWAY, 100.0, Lie.GREEN, 10.0)
        assertEquals(-0.070, sg, 0.005)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:scoring:test`
Expected: FAIL — `unresolved reference: expectedStrokes` (api not written yet).

- [ ] **Step 3: Write `SkillBand.kt`**

```kotlin
package com.hpsmiles.golfsim.core.scoring

/** Anchor bands of the SG tables; [anchorHc] positions the band on the handicap axis. */
enum class SkillBand(val anchorHc: Double) {
    TOUR(-5.0),
    SCRATCH(0.0),
    HCP10(10.0),
    HCP18(18.0),
}
```

- [ ] **Step 4: Write `Lie.kt`**

```kotlin
package com.hpsmiles.golfsim.core.scoring

/** Lies the combine scores. GREEN uses the putt table; TEE only ever starts a hole. */
enum class Lie {
    TEE,
    FAIRWAY,
    GREEN,
    ROUGH;

    val distances: DoubleArray
        get() = when (this) {
            TEE -> SgTables.TEE_DISTANCES
            FAIRWAY -> SgTables.FAIRWAY_DISTANCES
            GREEN -> SgTables.PUTT_DISTANCES
            ROUGH -> SgTables.ROUGH_DISTANCES
        }

    fun valuesFor(band: SkillBand): DoubleArray = when (this) {
        TEE -> when (band) {
            SkillBand.TOUR -> SgTables.TEE_TOUR
            SkillBand.SCRATCH -> SgTables.TEE_SCRATCH
            SkillBand.HCP10 -> SgTables.TEE_HCP10
            SkillBand.HCP18 -> SgTables.TEE_HCP18
        }
        FAIRWAY -> when (band) {
            SkillBand.TOUR -> SgTables.FAIRWAY_TOUR
            SkillBand.SCRATCH -> SgTables.FAIRWAY_SCRATCH
            SkillBand.HCP10 -> SgTables.FAIRWAY_HCP10
            SkillBand.HCP18 -> SgTables.FAIRWAY_HCP18
        }
        GREEN -> when (band) {
            SkillBand.TOUR -> SgTables.PUTT_TOUR
            SkillBand.SCRATCH -> SgTables.PUTT_SCRATCH
            SkillBand.HCP10 -> SgTables.PUTT_HCP10
            SkillBand.HCP18 -> SgTables.PUTT_HCP18
        }
        ROUGH -> when (band) {
            SkillBand.TOUR -> SgTables.ROUGH_TOUR
            SkillBand.SCRATCH -> SgTables.ROUGH_SCRATCH
            SkillBand.HCP10 -> SgTables.ROUGH_HCP10
            SkillBand.HCP18 -> SgTables.ROUGH_HCP18
        }
    }
}
```

- [ ] **Step 5: Write `ExpectedStrokes.kt`**

```kotlin
package com.hpsmiles.golfsim.core.scoring

/** Expected strokes for [band] playing from [lie] at [metres] from the hole. Clamps at grid ends. */
fun expectedStrokes(band: SkillBand, lie: Lie, metres: Double): Double =
    interpolate(lie.distances, lie.valuesFor(band), metres)

/** Strokes gained by a shot that started at (startLie, startM) and rests at (endLie, endM). */
fun strokesGained(
    band: SkillBand,
    startLie: Lie,
    startM: Double,
    endLie: Lie,
    endM: Double,
): Double =
    expectedStrokes(band, startLie, startM) - expectedStrokes(band, endLie, endM) - 1.0

internal fun interpolate(xs: DoubleArray, ys: DoubleArray, x: Double): Double {
    if (x <= xs.first()) return ys.first()
    if (x >= xs.last()) return ys.last()
    var lo = 0
    var hi = xs.size - 1
    while (hi - lo > 1) {
        val mid = (lo + hi) / 2
        if (xs[mid] <= x) lo = mid else hi = mid
    }
    val f = (x - xs[lo]) / (xs[hi] - xs[lo])
    return ys[lo] + f * (ys[hi] - ys[lo])
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `.\gradlew.bat :core:scoring:test`
Expected: `BUILD SUCCESSFUL`; all `ExpectedStrokesTest` tests green.

- [ ] **Step 7: Commit**

```powershell
git add core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SkillBand.kt core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/Lie.kt core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/ExpectedStrokes.kt core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/ExpectedStrokesTest.kt
git commit -m "feat(scoring): expected-strokes lookup + strokes gained"
```

---

### Task 4: Handicap match (closed-form root-solve + uncertainty)

**Files:**
- Create: `core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/HandicapMatch.kt`
- Test: `core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/HandicapMatchTest.kt`

- [ ] **Step 1: Write the failing test**

`HandicapMatchTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandicapMatchTest {

    private fun goldenShots(): List<ShotInput> =
        listOf(1.0, 6.0, 7.0, 8.0, 10.0, 12.0).map {
            ShotInput(Lie.FAIRWAY, 100.0, Lie.GREEN, it)
        }

    @Test
    fun `golden fixture - per-band mean SG`() {
        val estimate = handicapMatch(goldenShots())
        assertEquals(0.021, estimate.perBandSg.getValue(SkillBand.TOUR), 0.005)
        assertEquals(0.148, estimate.perBandSg.getValue(SkillBand.SCRATCH), 0.005)
        assertEquals(0.411, estimate.perBandSg.getValue(SkillBand.HCP10), 0.005)
        assertEquals(0.865, estimate.perBandSg.getValue(SkillBand.HCP18), 0.005)
    }

    @Test
    fun `golden fixture - estimate is below tour and clamped`() {
        val estimate = handicapMatch(goldenShots())
        assertEquals(-5.84, estimate.est, 0.05)
        assertTrue(estimate.clampedLow)
        assertFalse(estimate.clampedHigh)
    }

    @Test
    fun `golden fixture - uncertainty from scratch-band spread`() {
        val estimate = handicapMatch(goldenShots())
        assertNotNull(estimate.low)
        assertNotNull(estimate.high)
        val uncertainty = (estimate.high!! - estimate.low!!) / 2.0
        assertEquals(6.5, uncertainty, 0.1)
        assertTrue(estimate.low!! < estimate.est)
        assertTrue(estimate.high!! > estimate.est)
    }

    @Test
    fun `single shot has no uncertainty range`() {
        val estimate = handicapMatch(listOf(ShotInput(Lie.FAIRWAY, 100.0, Lie.GREEN, 10.0)))
        assertNull(estimate.low)
        assertNull(estimate.high)
    }

    @Test
    fun `empty shots are rejected`() {
        try {
            handicapMatch(emptyList())
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:scoring:test`
Expected: FAIL — `unresolved reference: handicapMatch`.

- [ ] **Step 3: Write `HandicapMatch.kt`**

```kotlin
package com.hpsmiles.golfsim.core.scoring

import kotlin.math.abs
import kotlin.math.sqrt

/** One scored shot: start lie + distance, rest lie + distance. */
data class ShotInput(val startLie: Lie, val startM: Double, val endLie: Lie, val endM: Double)

/** Handicap estimate with an uncertainty range (null when n < 2) and clamp flags. */
data class HandicapEstimate(
    val est: Double,
    val low: Double?,
    val high: Double?,
    val perBandSg: Map<SkillBand, Double>,
    val clampedLow: Boolean,
    val clampedHigh: Boolean,
)

/**
 * Handicap match on the mean SG curve: SG(h) is piecewise linear between band anchors
 * (TOUR -5, SCRATCH 0, HCP10 10, HCP18 18). SG increases with h; the estimate is the
 * closed-form root of SG(h) = 0, extrapolated on the end segments when all SGs are one-sided.
 */
fun handicapMatch(shots: List<ShotInput>): HandicapEstimate {
    require(shots.isNotEmpty()) { "handicapMatch requires at least one shot" }

    val perBand = LinkedHashMap<SkillBand, Double>()
    for (band in SkillBand.entries) {
        val sgs = shots.map { strokesGained(band, it.startLie, it.startM, it.endLie, it.endM) }
        perBand[band] = sgs.average()
    }

    val s = DoubleArray(4) { perBand.getValue(SkillBand.entries[it]) }
    val h = doubleArrayOf(-5.0, 0.0, 10.0, 18.0)

    val est: Double
    val segment: Int // index of the segment used for the root (0 = [-5,0], 1 = [0,10], 2 = [10,18])
    when {
        s[0] >= 0.0 -> {
            est = h[0] + (h[1] - h[0]) * (-s[0]) / s1Diff(s, 0)
            segment = 0
        }
        else -> {
            var crossing = -1
            for (i in 0 until 3) if (s[i] < 0.0 && s[i + 1] >= 0.0) { crossing = i; break }
            if (crossing >= 0) {
                est = h[crossing] + (h[crossing + 1] - h[crossing]) * (-s[crossing]) / s1Diff(s, crossing)
                segment = crossing
            } else {
                est = h[2] + (h[3] - h[2]) * (-s[2]) / s1Diff(s, 2)
                segment = 2
            }
        }
    }

    val low: Double?
    val high: Double?
    if (shots.size < 2) {
        low = null
        high = null
    } else {
        val scratchSgs = shots.map { strokesGained(SkillBand.SCRATCH, it.startLie, it.startM, it.endLie, it.endM) }
        val mean = scratchSgs.average()
        val sd = sqrt(scratchSgs.sumOf { (it - mean) * (it - mean) } / (shots.size - 1))
        val slope = (s[segment + 1] - s[segment]) / (h[segment + 1] - h[segment])
        val uncertainty = sd / sqrt(shots.size.toDouble()) / abs(slope)
        low = est - uncertainty
        high = est + uncertainty
    }

    return HandicapEstimate(
        est = est,
        low = low,
        high = high,
        perBandSg = perBand,
        clampedLow = est < -5.0,
        clampedHigh = est > 25.0,
    )
}

private fun s1Diff(s: DoubleArray, i: Int): Double {
    val d = s[i + 1] - s[i]
    return if (abs(d) < 1e-12) 1e-12 else d
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :core:scoring:test`
Expected: `BUILD SUCCESSFUL`; all `HandicapMatchTest` tests green. (`-5.84`, `±6.5` are the verified fixture values.)

- [ ] **Step 5: Commit**

```powershell
git add core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/HandicapMatch.kt core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/HandicapMatchTest.kt
git commit -m "feat(scoring): closed-form handicap match with uncertainty"
```

---

### Task 5: Room v6 — entities, DAOs, migration, schema JSON

**Files:**
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/CombineEntities.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/CombineDaos.kt`
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabase.kt`
- Create (generated): `core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/6.json`

- [ ] **Step 1: Write `CombineEntities.kt`**

```kotlin
package com.hpsmiles.golfsim.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "combine_sessions")
data class CombineSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val combineId: String,
    val combineName: String,
    val startedAtMs: Long,
    val completedAtMs: Long?,
    val source: Int,
    val layoutVersion: Int,
)

@Entity(tableName = "combine_shots", indices = [Index("sessionId")])
data class CombineShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val shotIndex: Int,
    val stationIndex: Int,
    val restLie: String,
    val restDistanceM: Double,
)
```

- [ ] **Step 2: Write `CombineDaos.kt`**

```kotlin
package com.hpsmiles.golfsim.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CombineSessionDao {
    @Insert
    suspend fun insert(session: CombineSessionEntity): Long

    @Query("SELECT * FROM combine_sessions WHERE completedAtMs IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun findInProgress(): CombineSessionEntity?

    @Query("SELECT * FROM combine_sessions WHERE completedAtMs IS NULL ORDER BY id DESC LIMIT 1")
    fun observeInProgress(): Flow<CombineSessionEntity?>

    @Query("SELECT * FROM combine_sessions WHERE completedAtMs IS NOT NULL ORDER BY id DESC")
    fun observeHistory(): Flow<List<CombineSessionEntity>>

    @Query("SELECT * FROM combine_sessions WHERE completedAtMs IS NOT NULL AND combineId = :combineId")
    suspend fun completedForCombine(combineId: String): List<CombineSessionEntity>

    @Query("UPDATE combine_sessions SET completedAtMs = :completedAtMs WHERE id = :id")
    suspend fun complete(id: Long, completedAtMs: Long)

    @Query("DELETE FROM combine_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CombineShotDao {
    @Insert
    suspend fun insert(shot: CombineShotEntity): Long

    @Query("SELECT * FROM combine_shots WHERE sessionId = :sessionId ORDER BY shotIndex")
    fun observeShots(sessionId: Long): Flow<List<CombineShotEntity>>

    @Query("SELECT * FROM combine_shots WHERE sessionId IN (:sessionIds)")
    suspend fun shotsForSessions(sessionIds: List<Long>): List<CombineShotEntity>

    @Query(
        "SELECT * FROM combine_shots WHERE sessionId IN " +
            "(SELECT id FROM combine_sessions WHERE completedAtMs IS NOT NULL) ORDER BY sessionId, shotIndex"
    )
    fun observeHistoryShots(): Flow<List<CombineShotEntity>>

    @Query("DELETE FROM combine_shots WHERE sessionId = :sessionId")
    suspend fun deleteShots(sessionId: Long)
}
```

- [ ] **Step 3: Modify `Mlm2proDatabase.kt`**

Three edits, matching the existing file's style exactly:

1. `@Database` annotation — add the entities and bump the version:

```kotlin
@Database(
    entities = [
        SessionEntity::class,
        ShotEntity::class,
        ClubEntity::class,
        GameResultEntity::class,
        BagMappingSessionEntity::class,
        BagMappingShotEntity::class,
        FittingSessionEntity::class,
        FittingShotEntity::class,
        CombineSessionEntity::class,
        CombineShotEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
```

2. Add abstract accessors alongside the existing ones:

```kotlin
abstract fun combineSessionDao(): CombineSessionDao
abstract fun combineShotDao(): CombineShotDao
```

3. Add the migration to the companion object (same `CREATE TABLE IF NOT EXISTS` style as `MIGRATION_3_4` / `MIGRATION_4_5`):

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `combine_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`combineId` TEXT NOT NULL, " +
                "`combineName` TEXT NOT NULL, " +
                "`startedAtMs` INTEGER NOT NULL, " +
                "`completedAtMs` INTEGER, " +
                "`source` INTEGER NOT NULL, " +
                "`layoutVersion` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `combine_shots` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`shotIndex` INTEGER NOT NULL, " +
                "`stationIndex` INTEGER NOT NULL, " +
                "`restLie` TEXT NOT NULL, " +
                "`restDistanceM` REAL NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_combine_shots_sessionId` ON `combine_shots` (`sessionId`)"
        )
    }
}
```

- [ ] **Step 4: Register the migration in `SessionRepository.build()`**

In `SessionRepository.kt`, add `MIGRATION_5_6` to the `addMigrations(...)` list (after `MIGRATION_4_5`).

- [ ] **Step 5: Generate and commit the schema JSON**

Run: `.\gradlew.bat :core:data:testDebugUnitTest` (compiles main sources and emits the schema)
Expected: build compiles; `git status` shows a new untracked file
`core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/6.json`.

- [ ] **Step 6: Commit**

```powershell
git add core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/CombineEntities.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/CombineDaos.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabase.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/6.json
git commit -m "feat(data): Room v6 combine tables + MIGRATION_5_6"
```

---

### Task 6: Repository combine ops + migration/repository tests

**Files:**
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt`
- Test: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/MigrationFrom5Test.kt`
- Test: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/CombineRepositoryTest.kt`

- [ ] **Step 1: Write the failing repository test**

`CombineRepositoryTest.kt` (follow `BagMappingRepositoryTest` exactly — same runner, config, helpers):

```kotlin
package com.hpsmiles.golfsim.core.data

import android.os.Build
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.CombineRepositoryTest"`
Expected: FAIL — `unresolved reference: startCombineSession`.

- [ ] **Step 3: Add the repository ops**

In `SessionRepository.kt`, next to the fitting section, add a combine section that follows the surrounding code's exact error-handling shape (never throw: catch `Throwable`, rethrow `CancellationException`, `Log.e(TAG, ...)`, `persistError.value = true`; success sets `persistError.value = false`):

```kotlin
// ---- combine ----

val combineActive: Flow<CombineSessionEntity?> get() = combineSessionDao.observeInProgress()

val combineHistory: Flow<List<CombineSessionEntity>> get() = combineSessionDao.observeHistory()

val combineHistoryShots: Flow<List<CombineShotEntity>> get() = combineShotDao.observeHistoryShots()

fun observeCombineShots(sessionId: Long): Flow<List<CombineShotEntity>> =
    combineShotDao.observeShots(sessionId)

suspend fun startCombineSession(
    combineId: String,
    combineName: String,
    layoutVersion: Int,
    startedAtMs: Long,
    source: ShotSource,
): Long? = try {
    db.withTransaction {
        val existing = combineSessionDao.findInProgress()
        when {
            existing == null -> combineSessionDao.insert(
                CombineSessionEntity(
                    combineId = combineId,
                    combineName = combineName,
                    startedAtMs = startedAtMs,
                    completedAtMs = null,
                    source = source.code,
                    layoutVersion = layoutVersion,
                )
            )
            existing.combineId == combineId -> existing.id
            else -> null
        }
    }.also { persistError.value = false }
} catch (t: Throwable) {
    if (t is CancellationException) throw t
    Log.e(TAG, "startCombineSession failed", t)
    persistError.value = true
    null
}

suspend fun appendCombineShot(
    sessionId: Long,
    shotIndex: Int,
    stationIndex: Int,
    restLie: String,
    restDistanceM: Double,
): Long? = try {
    combineShotDao.insert(
        CombineShotEntity(
            sessionId = sessionId,
            shotIndex = shotIndex,
            stationIndex = stationIndex,
            restLie = restLie,
            restDistanceM = restDistanceM,
        )
    ).also { persistError.value = false }
} catch (t: Throwable) {
    if (t is CancellationException) throw t
    Log.e(TAG, "appendCombineShot failed", t)
    persistError.value = true
    null
}

suspend fun completeCombineSession(id: Long, completedAtMs: Long): Unit = try {
    combineSessionDao.complete(id, completedAtMs)
    persistError.value = false
} catch (t: Throwable) {
    if (t is CancellationException) throw t
    Log.e(TAG, "completeCombineSession failed", t)
    persistError.value = true
}

suspend fun discardCombineSession(id: Long): Unit = try {
    db.withTransaction {
        combineShotDao.deleteShots(id)
        combineSessionDao.delete(id)
    }
    persistError.value = false
} catch (t: Throwable) {
    if (t is CancellationException) throw t
    Log.e(TAG, "discardCombineSession failed", t)
    persistError.value = true
}

suspend fun combineCompletedSessions(combineId: String): List<CombineSessionEntity> =
    combineSessionDao.completedForCombine(combineId)

suspend fun combineShotsForSessions(sessionIds: List<Long>): List<CombineShotEntity> =
    if (sessionIds.isEmpty()) emptyList() else combineShotDao.shotsForSessions(sessionIds)
```

Match imports/helpers to the file's existing conventions (the `db`, DAO getters, `persistError`, `TAG`, `withTransaction` are already there; add DAO getters `combineSessionDao` / `combineShotDao` mirroring the other private DAO getters).

- [ ] **Step 4: Run repository tests to verify they pass**

Run: `.\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.CombineRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Write `MigrationFrom5Test.kt`**

Open `MigrationFrom4Test.kt` and clone it — same scaffolding (hand-built SQLite file `golfsim.db`, raw DDL, seeded rows, `room_master_table` insert, `SessionRepository.open(context).initializeAndRestore()`), with these exact changes:

- DDL set = v4 DDL already in `MigrationFrom4Test` **plus the two `CREATE TABLE` statements and the index from `Mlm2proDatabase.MIGRATION_4_5`, copied verbatim**.
- `room_master_table` identity hash: `10534aeb76d0cd662d5454dbaed2b27a` (from `5.json`).
- `raw.version = 5`.
- Keep the existing prior-data assertions (summaries/rows survive).

Add these combine assertions at the end:

```kotlin
// combine tables created by MIGRATION_5_6 are usable
val combineId = repository.startCombineSession("APPROACH", "APPROACH", 1, 1_000L, ShotSource.LIVE)
assertNotNull(combineId)
for (index in 0 until 18) {
    repository.appendCombineShot(combineId!!, index, index / 6, "FAIRWAY", 12.0)
}
assertEquals(18, repository.observeCombineShots(combineId).first().size)
repository.completeCombineSession(combineId, 2_000L)
assertEquals(1, repository.combineHistory.first().size)
```

- [ ] **Step 6: Run the migration test**

Run: `.\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.MigrationFrom5Test"`
Expected: PASS (Room validates the migrated schema against the v6 entities).

- [ ] **Step 7: Commit**

```powershell
git add core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/MigrationFrom5Test.kt core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/CombineRepositoryTest.kt
git commit -m "feat(data): combine repository ops + migration v5->v6 test"
```

---

### Task 7: `CombineCatalog` + `CombinePlan` (+ app dependency)

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineCatalog.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombinePlan.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombinePlanTest.kt`

- [ ] **Step 0: Add the module dependency**

In `app/build.gradle.kts` dependencies block: `implementation(project(":core:scoring"))`.

- [ ] **Step 1: Write the failing test**

`CombinePlanTest.kt`:

```kotlin
package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinePlanTest {

    private val wedges = CombineCatalog.byId("WEDGES")!!
    private val approach = CombineCatalog.byId("APPROACH")!!

    @Test
    fun `station sequence is an interleaved round-robin`() {
        val sequence = (0 until CombinePlan.SHOTS_TOTAL).map { CombinePlan.stationIndexFor(it) }
        assertEquals(
            listOf(0, 0, 1, 1, 2, 2, 0, 0, 1, 1, 2, 2, 0, 0, 1, 1, 2, 2),
            sequence,
        )
    }

    @Test
    fun `visit numbers advance every cycle`() {
        assertEquals(0, CombinePlan.visitFor(5))
        assertEquals(1, CombinePlan.visitFor(6))
        assertEquals(2, CombinePlan.visitFor(17))
    }

    @Test
    fun `banner shows on visit boundaries`() {
        assertTrue(CombinePlan.isBannerShot(2))
        assertTrue(CombinePlan.isBannerShot(6))
        assertTrue(CombinePlan.isBannerShot(12))
        assertFalse(CombinePlan.isBannerShot(0))
        assertFalse(CombinePlan.isBannerShot(1))
        assertFalse(CombinePlan.isBannerShot(18))
        assertEquals("NEXT: 70 m · VISIT 1", CombinePlan.bannerLabel(wedges, 2))
        assertEquals("NEXT: 50 m · VISIT 2", CombinePlan.bannerLabel(wedges, 6))
    }

    @Test
    fun `counts track shots per station`() {
        assertEquals(listOf(0, 0, 0), CombinePlan.countsFor(wedges, 0))
        assertEquals(listOf(3, 2, 2), CombinePlan.countsFor(wedges, 7))
        assertEquals(listOf(6, 6, 6), CombinePlan.countsFor(wedges, 18))
    }

    @Test
    fun `pins follow the visit for approach stations and are absent for the driver`() {
        assertEquals(CombineCatalog.visitPins[0], CombinePlan.pinFor(approach, 0))
        assertEquals(CombineCatalog.visitPins[1], CombinePlan.pinFor(approach, 6))
        assertEquals(CombineCatalog.visitPins[2], CombinePlan.pinFor(approach, 12))
        assertNull(CombinePlan.pinFor(approach, 4))
    }

    @Test
    fun `approach rest distance is measured to the visit pin`() {
        // WEDGES 50 m, visit 1 pin at (0,0); rest 3 m long and 1 m left
        val distance = CombinePlan.restDistanceM(wedges, 0, 3.0, 49.0)
        assertEquals(kotlin.math.sqrt(10.0), distance, 1e-9)
    }

    @Test
    fun `driver rest distance is measured to the invisible pin at the hole baseline`() {
        val distance = CombinePlan.restDistanceM(approach, 4, 10.0, 300.0)
        assertEquals(kotlin.math.sqrt(100.0 + 40.0 * 40.0), distance, 1e-9)
    }

    @Test
    fun `shot inputs map lies and distances`() {
        val approachInput = CombinePlan.shotInput(wedges, 0, Lie.GREEN, 3.2)
        assertEquals(Lie.FAIRWAY, approachInput.startLie)
        assertEquals(50.0, approachInput.startM, 1e-9)
        val driverInput = CombinePlan.shotInput(approach, 4, Lie.FAIRWAY, 41.2)
        assertEquals(Lie.TEE, driverInput.startLie)
        assertEquals(340.0, driverInput.startM, 1e-9)
    }
}
```

(Execution note 2026-10-10: driver test indices in this section were corrected 2 → 4 — the interleave `[0,0,1,1,2,2]×3` puts DRIVER at shot indices 4,5,10,11,16,17; correction landed with T7 implementer commit 23ee8d3.)

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombinePlanTest"`
Expected: FAIL — `unresolved reference: CombineCatalog`.

- [ ] **Step 3: Write `CombineCatalog.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

/** Driver station hole baseline in metres (scoring-only, invisible in the scene). */
const val DRIVER_HOLE_M = 340.0

enum class StationKind { APPROACH, DRIVER }

data class PinOffset(val dx: Double, val dy: Double)

data class CombineStation(
    val label: String,
    val startDistanceM: Double,
    val kind: StationKind,
    val greenRadiusM: Double = DEFAULT_GREEN_RADIUS_M,
    val pins: List<PinOffset> = emptyList(),
) {
    companion object {
        const val DEFAULT_GREEN_RADIUS_M = 15.0
    }
}

data class CombineCatalogEntry(
    val id: String,
    val name: String,
    val blurb: String,
    val stations: List<CombineStation>,
    val layoutVersion: Int = 1,
)

object CombineCatalog {

    val visitPins = listOf(
        PinOffset(0.0, 0.0),
        PinOffset(2.2, -1.4),
        PinOffset(-1.8, 1.9),
    )

    val entries: List<CombineCatalogEntry> = listOf(
        CombineCatalogEntry(
            id = "WEDGES",
            name = "WEDGES",
            blurb = "50 / 70 / 90 m scoring wedges against the pin",
            stations = listOf(
                CombineStation("50 m", 50.0, StationKind.APPROACH, pins = visitPins),
                CombineStation("70 m", 70.0, StationKind.APPROACH, pins = visitPins),
                CombineStation("90 m", 90.0, StationKind.APPROACH, pins = visitPins),
            ),
        ),
        CombineCatalogEntry(
            id = "APPROACH",
            name = "APPROACH",
            blurb = "100 m / 150 m approaches and one drive",
            stations = listOf(
                CombineStation("100 m", 100.0, StationKind.APPROACH, pins = visitPins),
                CombineStation("150 m", 150.0, StationKind.APPROACH, pins = visitPins),
                CombineStation("DRIVER", DRIVER_HOLE_M, StationKind.DRIVER),
            ),
        ),
    )

    fun byId(id: String): CombineCatalogEntry? = entries.firstOrNull { it.id == id }
}
```

- [ ] **Step 4: Write `CombinePlan.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie
import com.hpsmiles.golfsim.core.scoring.ShotInput
import kotlin.math.sqrt

/** Pure round-robin / geometry math for a combine session. */
object CombinePlan {
    const val SHOTS_PER_VISIT = 2
    const val STATIONS_PER_CYCLE = 3
    const val CYCLES = 3
    const val SHOTS_TOTAL = STATIONS_PER_CYCLE * CYCLES * SHOTS_PER_VISIT

    /** 0,0,1,1,2,2 repeating. */
    fun stationIndexFor(shotIndex: Int): Int =
        (shotIndex % (STATIONS_PER_CYCLE * SHOTS_PER_VISIT)) / SHOTS_PER_VISIT

    fun visitFor(shotIndex: Int): Int = shotIndex / (STATIONS_PER_CYCLE * SHOTS_PER_VISIT)

    fun isBannerShot(takenCount: Int): Boolean =
        takenCount > 0 && takenCount < SHOTS_TOTAL && takenCount % SHOTS_PER_VISIT == 0

    fun bannerLabel(entry: CombineCatalogEntry, takenCount: Int): String {
        val station = entry.stations[stationIndexFor(takenCount)]
        return "NEXT: ${station.label} · VISIT ${visitFor(takenCount) + 1}"
    }

    fun countsFor(entry: CombineCatalogEntry, takenCount: Int): List<Int> =
        entry.stations.indices.map { station ->
            (0 until takenCount).count { stationIndexFor(it) == station }
        }

    fun pinFor(entry: CombineCatalogEntry, shotIndex: Int): PinOffset? {
        val station = entry.stations[stationIndexFor(shotIndex)]
        if (station.kind == StationKind.DRIVER) return null
        return station.pins[visitFor(shotIndex) % station.pins.size]
    }

    /** Approach: distance to the visit pin. Driver: distance to the invisible pin at (0, [DRIVER_HOLE_M]). */
    fun restDistanceM(entry: CombineCatalogEntry, shotIndex: Int, restX: Double, restY: Double): Double {
        val station = entry.stations[stationIndexFor(shotIndex)]
        return if (station.kind == StationKind.DRIVER) {
            sqrt(restX * restX + (DRIVER_HOLE_M - restY) * (DRIVER_HOLE_M - restY))
        } else {
            val pin = pinFor(entry, shotIndex)!!
            val cy = station.startDistanceM + pin.dy
            sqrt((restX - pin.dx) * (restX - pin.dx) + (restY - cy) * (restY - cy))
        }
    }

    fun shotInput(entry: CombineCatalogEntry, shotIndex: Int, restLie: Lie, restDistanceM: Double): ShotInput {
        val station = entry.stations[stationIndexFor(shotIndex)]
        val startLie = if (station.kind == StationKind.DRIVER) Lie.TEE else Lie.FAIRWAY
        return ShotInput(startLie, station.startDistanceM, restLie, restDistanceM)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombinePlanTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/build.gradle.kts app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineCatalog.kt app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombinePlan.kt app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombinePlanTest.kt
git commit -m "feat(combine): catalog + round-robin plan math"
```

---

### Task 8: `CombineLie` + physics surface provider

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineLie.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineLieTest.kt`

- [ ] **Step 1: Write the failing test**

`CombineLieTest.kt`:

```kotlin
package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie
import org.junit.Assert.assertEquals
import org.junit.Test

class CombineLieTest {

    private val wedges = CombineCatalog.byId("WEDGES")!!
    private val approach = CombineCatalog.byId("APPROACH")!!

    @Test
    fun `inside the green oval is green`() {
        // WEDGES 50 m, visit-1 pin at (0, 50), radius 15
        assertEquals(Lie.GREEN, CombineLie.classify(wedges, 0, 0.0, 50.0))
        assertEquals(Lie.GREEN, CombineLie.classify(wedges, 0, 10.0, 45.0))
    }

    @Test
    fun `short of the green inside the fairway is fairway`() {
        assertEquals(Lie.FAIRWAY, CombineLie.classify(wedges, 0, 0.0, 30.0))
        assertEquals(Lie.FAIRWAY, CombineLie.classify(wedges, 0, 15.0, 40.0))
    }

    @Test
    fun `wide of the fairway is rough`() {
        assertEquals(Lie.ROUGH, CombineLie.classify(wedges, 0, 30.0, 30.0))
        assertEquals(Lie.ROUGH, CombineLie.classify(wedges, 0, 0.0, 340.0))
    }

    @Test
    fun `driver has no green - fairway or rough only`() {
        // APPROACH shotIndex 4 = DRIVER (driver shot indices are 4,5,10,11,16,17)
        assertEquals(Lie.FAIRWAY, CombineLie.classify(approach, 4, 5.0, 280.0))
        assertEquals(Lie.ROUGH, CombineLie.classify(approach, 4, 28.0, 280.0))
        assertEquals(Lie.ROUGH, CombineLie.classify(approach, 4, 0.0, 350.0))
    }
}
```

Note: the second driver case relies on the fairway corridor half-width (~20–23 m from `RangeScene.fairwayHalfWidth`). The implementation must call the real `RangeScene` predicate — if its name differs from `isInFairway`, adjust the call only (the predicate is the one used for the fairway region in `RangeScene.kt` around line 66).

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombineLieTest"`
Expected: FAIL — `unresolved reference: CombineLie`.

- [ ] **Step 3: Write `CombineLie.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.physics.Surface
import com.hpsmiles.golfsim.core.physics.SurfaceProvider
import com.hpsmiles.golfsim.core.scoring.Lie
import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.sqrt

/**
 * Single source of truth for the lie a resting ball scores from:
 * green oval (approach stations only) > fairway corridor (the visible RangeScene region) > rough.
 * The same classifier decides the physics surface, so what the player sees is what is scored.
 */
object CombineLie {

    fun classify(entry: CombineCatalogEntry, shotIndex: Int, restX: Double, restY: Double): Lie {
        val station = entry.stations[CombinePlan.stationIndexFor(shotIndex)]
        if (station.kind == StationKind.APPROACH) {
            val pin = CombinePlan.pinFor(entry, shotIndex)!!
            val cx = pin.dx
            val cy = station.startDistanceM + pin.dy
            val dx = restX - cx
            val dy = restY - cy
            if (sqrt(dx * dx + dy * dy) <= station.greenRadiusM) return Lie.GREEN
        }
        return if (RangeScene.isInFairway(restX, restY)) Lie.FAIRWAY else Lie.ROUGH
    }
}

/** Physics surfaces mirroring [CombineLie] for one specific shot's scene. */
class CombineSurfaceProvider(
    private val entry: CombineCatalogEntry,
    private val shotIndex: Int,
    private val green: Surface,
    private val fairway: Surface,
    private val rough: Surface,
) : SurfaceProvider {

    override fun surfaceAt(x: Double, y: Double): Surface =
        when (CombineLie.classify(entry, shotIndex, x, y)) {
            Lie.GREEN -> green
            Lie.FAIRWAY -> fairway
            Lie.ROUGH -> rough
            Lie.TEE -> fairway
        }
}
```

(Verify the exact `SurfaceProvider` interface/method name in `core:physics` — reuse whatever `GreenZoneSurfaceProvider` implements.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombineLieTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineLie.kt app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineLieTest.kt
git commit -m "feat(combine): lie classification + physics surface provider"
```

---

### Task 9: `CombineFormats`, `CombineRecord`, `CombineGame` state holder

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineFormats.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineRecord.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineGame.kt`

(Execution note 2026-10-10: `CombineFormats.kt` + `CombineRecord.kt` moved here from Task 10 — `CombineGame.kt` references `CombineRecordComparison` and `CombineRecord.detail` references `CombineFormats`, so all three must compile together at this task's build step. Task 10 keeps `CombineResults` + its test.)

(Thin Compose state holder; all decision logic lives in the tested pure files — no unit test here, matching `TargetPracticeGame`.)

- [ ] **Step 1: Write `CombineFormats.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.HandicapEstimate
import com.hpsmiles.golfsim.core.scoring.SkillBand
import java.util.Locale
import kotlin.math.roundToInt

/** Display formatting for handicap estimates and per-shot chips. */
object CombineFormats {

    fun formatHc(est: Double): String = when {
        est < SkillBand.TOUR.anchorHc -> "tour-or-better"
        est < 0.0 -> "+" + oneDp(-est)
        est <= 25.0 -> oneDp(est)
        else -> "25+"
    }

    fun formatHcWithUncertainty(estimate: HandicapEstimate): String {
        val low = estimate.low
        val high = estimate.high
        return if (low == null || high == null) {
            "≈ ${formatHc(estimate.est)}"
        } else {
            "≈ ${formatHc(estimate.est)} ± ${zeroDp((high - low) / 2.0)}"
        }
    }

    fun chip(est: Double): String = when {
        est < SkillBand.TOUR.anchorHc -> "TOUR+"
        est < 0.0 -> "SCR"
        est <= 25.0 -> "≈" + est.roundToInt()
        else -> "25+"
    }

    fun oneDp(value: Double): String = String.format(Locale.US, "%.1f", value)

    fun zeroDp(value: Double): String = String.format(Locale.US, "%.0f", value)
}
```

- [ ] **Step 2: Write `CombineRecord.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

enum class CombineRecordOutcome { FIRST_SCORE, NEW_RECORD, OFF_RECORD }

data class CombineRecordComparison(
    val outcome: CombineRecordOutcome,
    val best: Double?,
    val delta: Double,
)

/** Lower overall HC is better. */
object CombineRecord {
    fun compare(previousBest: Double?, est: Double): CombineRecordComparison = when {
        previousBest == null -> CombineRecordComparison(CombineRecordOutcome.FIRST_SCORE, null, 0.0)
        est < previousBest -> CombineRecordComparison(CombineRecordOutcome.NEW_RECORD, previousBest, previousBest - est)
        else -> CombineRecordComparison(CombineRecordOutcome.OFF_RECORD, previousBest, est - previousBest)
    }

    fun headline(comparison: CombineRecordComparison): String = when (comparison.outcome) {
        CombineRecordOutcome.FIRST_SCORE -> "FIRST SCORE"
        CombineRecordOutcome.NEW_RECORD -> "NEW RECORD"
        CombineRecordOutcome.OFF_RECORD -> "OFF YOUR BEST"
    }

    fun detail(comparison: CombineRecordComparison): String = when (comparison.outcome) {
        CombineRecordOutcome.FIRST_SCORE -> "First completed combine"
        CombineRecordOutcome.NEW_RECORD -> "Previous best ${CombineFormats.formatHc(comparison.best!!)}"
        CombineRecordOutcome.OFF_RECORD ->
            "Best ${CombineFormats.formatHc(comparison.best!!)} · ${CombineFormats.oneDp(comparison.delta)} off"
    }
}
```

- [ ] **Step 3: Write `CombineGame.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.mutableStateListOf
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.Surface
import com.hpsmiles.golfsim.core.scoring.Lie
import com.hpsmiles.golfsim.range.DisplayShot

data class CombineShot(
    val index: Int,
    val stationIndex: Int,
    val display: DisplayShot,
    val restLie: Lie,
    val restDistanceM: Double,
)

class CombineGame {
    var entry: CombineCatalogEntry? = null
    var sessionId: Long = 0L
    var baselineTaken: Int = 0
    val shots: SnapshotStateList<CombineShot> = mutableStateListOf()
    var tick by mutableIntStateOf(0)
    var record: CombineRecordComparison? by mutableStateOf(null)

    var clockMs: () -> Long = { System.currentTimeMillis() }
    var greenSurface: Surface = Surface.GREEN_NORMAL
    var fairwaySurface: Surface = Surface.FAIRWAY_NORMAL
    var roughSurface: Surface = Surface.ROUGH_NORMAL

    var simulator: (LaunchConditions) -> ShotResult = { launch ->
        val currentEntry = requireNotNull(entry) { "combine not started" }
        val index = nextShotIndex
        BallFlightEngine.simulate(
            launch,
            Environment(),
            CombineSurfaceProvider(currentEntry, index, greenSurface, fairwaySurface, roughSurface),
        )
    }

    val takenCount: Int get() = baselineTaken + shots.size
    val nextShotIndex: Int get() = takenCount
    val complete: Boolean get() = takenCount >= CombinePlan.SHOTS_TOTAL

    fun start(entry: CombineCatalogEntry, sessionId: Long, baselineTaken: Int) {
        this.entry = entry
        this.sessionId = sessionId
        this.baselineTaken = baselineTaken
        shots.clear()
        record = null
        tick = 0
    }

    fun reset() {
        entry = null
        sessionId = 0L
        baselineTaken = 0
        shots.clear()
        record = null
        tick = 0
    }

    fun add(ballData: BallData): CombineShot? {
        val currentEntry = entry ?: return null
        if (complete) return null
        val launch = try {
            LaunchConditions(
                ballSpeed = ballData.ballSpeed,
                launchAngleDeg = ballData.launchAngle,
                spinRpm = ballData.totalSpin,
                spinAxisDeg = ballData.spinAxis,
                launchDirDeg = ballData.launchDirection,
            )
        } catch (_: IllegalArgumentException) {
            return null
        }
        val index = nextShotIndex
        val result = simulator(launch)
        val restLie = CombineLie.classify(currentEntry, index, result.restX, result.restY)
        val restDistanceM = CombinePlan.restDistanceM(currentEntry, index, result.restX, result.restY)
        val shot = CombineShot(
            index = index,
            stationIndex = CombinePlan.stationIndexFor(index),
            display = DisplayShot(ballData, launch, result, clockMs()),
            restLie = restLie,
            restDistanceM = restDistanceM,
        )
        shots.add(shot)
        tick += 1
        return shot
    }
}
```

(Copy `LaunchConditions` argument names from `TargetPracticeGame.add` verbatim — same fields in the same order.)

- [ ] **Step 4: Build to verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineFormats.kt app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineRecord.kt app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineGame.kt
git commit -m "feat(combine): record/formats + session state holder"
```

---

### Task 10: `CombineResults` + test

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineResults.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineResultsTest.kt`

(Execution note 2026-10-10: `CombineFormats.kt` + `CombineRecord.kt` were moved to Task 9 to satisfy the compile dependency — this task now only adds results aggregation + its test.)

- [ ] **Step 1: Write the failing test**

`CombineResultsTest.kt`:

```kotlin
package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombineResultsTest {

    private val approach = CombineCatalog.byId("APPROACH")!!

    @Test
    fun `golden fixture reproduces at the app layer`() {
        // 100 m station (indices 0,1,6,7,12,13), rests 1,6,7,8,10,12 m, all on green
        val rests = listOf(1.0, 6.0, 7.0, 8.0, 10.0, 12.0)
        val indices = listOf(0, 1, 6, 7, 12, 13)
        val facts = indices.zip(rests).map { (index, rest) ->
            CombineShotFacts(index, Lie.GREEN, rest)
        }
        val results = combineResults(approach, facts)
        assertEquals(1, results.stations.size)
        assertEquals(-5.84, results.overall.est, 0.05)
        assertTrue(results.overall.clampedLow)
        assertEquals("TOUR+", results.stations[0].chips.first())
        assertEquals("AVG TO PIN", results.stations[0].avgLabel)
        assertEquals(rests.average(), results.stations[0].avgValue, 1e-9)
    }

    @Test
    fun `driver average is distance hit toward the baseline`() {
        val indices = listOf(4, 5, 10, 11, 16, 17) // DRIVER station indices
        val facts = indices.map { CombineShotFacts(it, Lie.FAIRWAY, 41.23) }
        val results = combineResults(approach, facts)
        assertEquals(1, results.stations.size)
        assertEquals("AVG TOTAL", results.stations[0].avgLabel)
        assertEquals(340.0 - 41.23, results.stations[0].avgValue, 1e-6)
    }

    @Test
    fun `formats follow the display rules`() {
        assertEquals("tour-or-better", CombineFormats.formatHc(-5.5))
        assertEquals("+1.3", CombineFormats.formatHc(-1.3))
        assertEquals("6.2", CombineFormats.formatHc(6.2))
        assertEquals("25+", CombineFormats.formatHc(26.0))
        assertEquals("TOUR+", CombineFormats.chip(-6.0))
        assertEquals("SCR", CombineFormats.chip(-2.0))
        assertEquals("≈7", CombineFormats.chip(7.2))
    }

    @Test
    fun `record compare is lower-is-better`() {
        assertEquals(CombineRecordOutcome.FIRST_SCORE, CombineRecord.compare(null, 6.0).outcome)
        assertEquals(CombineRecordOutcome.NEW_RECORD, CombineRecord.compare(8.9, 6.0).outcome)
        assertEquals(CombineRecordOutcome.OFF_RECORD, CombineRecord.compare(5.0, 6.0).outcome)
        assertEquals(2.9, CombineRecord.compare(8.9, 6.0).delta, 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombineResultsTest"`
Expected: FAIL — `unresolved reference: combineResults`.

- [ ] **Step 3: Write `CombineResults.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.data.CombineShotEntity
import com.hpsmiles.golfsim.core.scoring.HandicapEstimate
import com.hpsmiles.golfsim.core.scoring.Lie
import com.hpsmiles.golfsim.core.scoring.handicapMatch

/** Raw scoring facts for one shot — the only fields the SG engine needs. */
data class CombineShotFacts(val shotIndex: Int, val restLie: Lie, val restDistanceM: Double)

fun CombineShot.toFacts(): CombineShotFacts = CombineShotFacts(index, restLie, restDistanceM)

fun CombineShotEntity.toFacts(): CombineShotFacts =
    CombineShotFacts(shotIndex, Lie.valueOf(restLie), restDistanceM)

data class StationResult(
    val stationIndex: Int,
    val label: String,
    val estimate: HandicapEstimate,
    val avgLabel: String,
    val avgValue: Double,
    val chips: List<String>,
)

data class CombineResults(
    val overall: HandicapEstimate,
    val stations: List<StationResult>,
)

fun combineResults(entry: CombineCatalogEntry, facts: List<CombineShotFacts>): CombineResults {
    require(facts.isNotEmpty()) { "combineResults requires at least one shot" }
    val sorted = facts.sortedBy { it.shotIndex }
    val overall = handicapMatch(
        sorted.map { CombinePlan.shotInput(entry, it.shotIndex, it.restLie, it.restDistanceM) }
    )
    val stations = entry.stations.indices.mapNotNull { stationIndex ->
        val stationFacts = sorted.filter { CombinePlan.stationIndexFor(it.shotIndex) == stationIndex }
        if (stationFacts.isEmpty()) return@mapNotNull null
        val station = entry.stations[stationIndex]
        val inputs = stationFacts.map { CombinePlan.shotInput(entry, it.shotIndex, it.restLie, it.restDistanceM) }
        val estimate = handicapMatch(inputs)
        val avgRest = stationFacts.map { it.restDistanceM }.average()
        val isDriver = station.kind == StationKind.DRIVER
        StationResult(
            stationIndex = stationIndex,
            label = station.label,
            estimate = estimate,
            avgLabel = if (isDriver) "AVG TOTAL" else "AVG TO PIN",
            avgValue = if (isDriver) DRIVER_HOLE_M - avgRest else avgRest,
            chips = stationFacts.map { fact ->
                CombineFormats.chip(
                    handicapMatch(listOf(CombinePlan.shotInput(entry, fact.shotIndex, fact.restLie, fact.restDistanceM))).est
                )
            },
        )
    }
    return CombineResults(overall, stations)
}

fun overallEstimate(entry: CombineCatalogEntry, facts: List<CombineShotFacts>): Double =
    handicapMatch(
        facts.sortedBy { it.shotIndex }
            .map { CombinePlan.shotInput(entry, it.shotIndex, it.restLie, it.restDistanceM) }
    ).est
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombineResultsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineResults.kt app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineResultsTest.kt
git commit -m "feat(combine): results aggregation"
```

---

### Task 11: `CombineCanvas` + `CombineMetricsPanel`

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineCanvas.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineMetricsPanel.kt`

- [ ] **Step 1: Write `CombineCanvas.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hpsmiles.golfsim.games.GameScene
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.RangeCamera
import com.hpsmiles.golfsim.range.DisplayShot

/** Combine play canvas: ground + (approach only) visit green & pin + tracer. No rings, no labels. */
@Composable
internal fun CombineCanvas(
    camera: RangeCamera,
    entry: CombineCatalogEntry,
    shotIndex: Int,
    tracer: DisplayShot?,
    playFraction: Float,
    modifier: Modifier = Modifier,
) {
    // Copy the labelPaint remember block verbatim from TargetPracticeCanvas.kt.
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    }
    Canvas(modifier) {
        with(GameScene) {
            drawGameGround(camera, size.width, size.height, labelPaint)
            val station = entry.stations[CombinePlan.stationIndexFor(shotIndex)]
            if (station.kind == StationKind.APPROACH) {
                val pin = CombinePlan.pinFor(entry, shotIndex)!!
                drawGreen(camera, size.width, size.height, pin.dx, station.startDistanceM + pin.dy, station.greenRadiusM)
                drawPin(camera, size.width, size.height, pin.dx, station.startDistanceM + pin.dy)
            }
            if (tracer != null) {
                drawTracer(
                    camera,
                    size.width,
                    size.height,
                    tracer.shotResult,
                    playFraction,
                    GameScene.apexVMin(size.width, size.height),
                )
            }
        }
    }
}
```

(If `drawGameGround`'s paint parameter or `drawTracer`'s argument order differs, copy the exact call shape from `TargetPracticeCanvas.kt` — that file is the reference.)

- [ ] **Step 2: Write `CombineMetricsPanel.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.range.DisplayShot
import com.hpsmiles.golfsim.range.LastShotChips

/** Right panel: LAST SHOT (shared chip strip verbatim) + TARGETS (per-station counts). */
@Composable
internal fun CombineMetricsPanel(
    lastShot: DisplayShot?,
    entry: CombineCatalogEntry,
    takenCount: Int,
    modifier: Modifier = Modifier,
) {
    val counts = CombinePlan.countsFor(entry, takenCount)
    val currentStation = CombinePlan.stationIndexFor(takenCount.coerceAtMost(CombinePlan.SHOTS_TOTAL - 1))
    Column(
        modifier = modifier
            .width(210.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .background(GolfColors.Panel)
            .padding(GolfSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
    ) {
        SectionCard("LAST SHOT") {
            if (lastShot == null) {
                Text("Fire a shot", style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
            } else {
                LastShotChips(lastShot)
            }
        }
        SectionCard("TARGETS") {
            Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                entry.stations.forEachIndexed { index, station ->
                    val highlight = index == currentStation
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            station.label,
                            style = GolfTypography.BodySmall,
                            color = if (highlight) GolfColors.Teal else GolfColors.TextSecondary,
                        )
                        Text(
                            "${counts[index]}",
                            style = GolfTypography.BodySmall,
                            color = if (highlight) GolfColors.Teal else GolfColors.TextPrimary,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL", style = GolfTypography.BodySmall, color = GolfColors.TextSecondary)
                    Text(
                        "$takenCount/${CombinePlan.SHOTS_TOTAL}",
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextPrimary,
                    )
                }
            }
        }
    }
}
```

(Copy the exact import paths for `GolfColors`/`GolfSpacing`/`GolfTypography`/`SectionCard` from `GameMetricsPanel.kt` — same tokens, same package.)

- [ ] **Step 3: Build to verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineCanvas.kt app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineMetricsPanel.kt
git commit -m "feat(combine): play canvas + metrics panel"
```

---

### Task 12: `CombinePlay` (play screen)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombinePlay.kt`

(Execution note 2026-10-10: `CombinePlay` references `CombineResultsView`, so **Task 13 is built before Task 12** — T12 cannot compile standalone. Nothing else about this task changes.)

Clone `TargetPracticePlay.kt` structure (canvas Box + right panel; HUD TopStart; gear TopEnd; animation loop; result gate) with these combine-specific contents:

- [ ] **Step 1: Write `CombinePlay.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.games.GameQuickSettingsButton
import com.hpsmiles.golfsim.games.GameQuickSettingsDialog
import com.hpsmiles.golfsim.games.GameScene
import com.hpsmiles.golfsim.games.GameSpeedMult
import com.hpsmiles.golfsim.games.SpeedChipRow
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.games.rememberGameResultOverlayGate
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.RangeCamera
import kotlinx.coroutines.delay

@Composable
internal fun CombinePlay(
    entry: CombineCatalogEntry,
    game: CombineGame,
    soundsEnabled: Boolean,
    onSoundsChange: (Boolean) -> Unit,
    onPlayAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var speedMult by remember { mutableStateOf(GameSpeedMult.X15) }
    var playFraction by remember { mutableFloatStateOf(1f) }
    var showSettings by remember { mutableStateOf(false) }

    val lastShot = game.shots.lastOrNull()

    LaunchedEffect(game.tick, speedMult) {
        val shot = game.shots.lastOrNull() ?: return@LaunchedEffect
        val result = shot.display.shotResult
        if (result.samples.isEmpty()) {
            playFraction = FollowCam.endFraction(result)
            return@LaunchedEffect
        }
        val durationMs = result.flightTimeSec * 1000.0 / speedMult.divisor
        val end = FollowCam.endFraction(result).toFloat()
        playFraction = 0f
        while (playFraction < end) {
            withFrameNanos { delta ->
                playFraction = (playFraction + (delta / 1e6 / durationMs).toFloat()).coerceAtMost(end)
            }
        }
    }

    val showResults = rememberGameResultOverlayGate(game.complete, lastShot?.display?.shotResult, playFraction)

    if (showResults) {
        CombineResultsView(
            entry = entry,
            facts = game.shots.map { it.toFacts() },
            record = game.record,
            readOnly = false,
            onPlayAgain = onPlayAgain,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    val takenCount = game.takenCount
    val currentIndex = takenCount.coerceAtMost(CombinePlan.SHOTS_TOTAL - 1)
    val currentStation = entry.stations[CombinePlan.stationIndexFor(currentIndex)]
    val currentVisit = CombinePlan.visitFor(currentIndex) + 1

    var bannerVisible by remember { mutableStateOf(false) }
    var bannerText by remember { mutableStateOf("") }
    LaunchedEffect(takenCount) {
        if (CombinePlan.isBannerShot(takenCount)) {
            bannerText = CombinePlan.bannerLabel(entry, takenCount)
            bannerVisible = true
            delay(2000)
            bannerVisible = false
        }
    }

    BoxWithConstraints(modifier.background(GolfColors.Base)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).clipToBounds()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wPx = constraints.maxWidth.toFloat()
                    val hPx = constraints.maxHeight.toFloat()
                    val apexVMin = GameScene.apexVMin(wPx, hPx)
                    val camera = if (lastShot == null) {
                        RangeCamera.STATIC
                    } else {
                        FollowCam.cameraAt(lastShot.display.shotResult, playFraction, apexVMin)
                    }
                    CombineCanvas(
                        camera = camera,
                        entry = entry,
                        shotIndex = currentIndex,
                        tracer = lastShot?.display,
                        playFraction = playFraction,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(GolfSpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
                ) {
                    SpeedChipRow(selected = speedMult, onSelect = { speedMult = it })
                    Text(
                        "${currentStation.label} · VISIT $currentVisit/${CombinePlan.CYCLES}",
                        color = GolfColors.TextSecondary,
                    )
                }
                if (bannerVisible) {
                    Box(Modifier.align(Alignment.Center)) {
                        SectionCard("NEXT") {
                            Text(bannerText, color = GolfColors.Teal)
                        }
                    }
                }
                GameQuickSettingsButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(GolfSpacing.Sm),
                )
            }
            CombineMetricsPanel(
                lastShot = lastShot?.display,
                entry = entry,
                takenCount = takenCount,
            )
        }
    }

    if (showSettings) {
        GameQuickSettingsDialog(
            soundsEnabled = soundsEnabled,
            onSoundsChange = onSoundsChange,
            onDismiss = { showSettings = false },
        )
    }
}
```

Notes: copy exact call shapes (`SpeedChipRow`, `GameQuickSettingsButton/Dialog`, `SectionCard`, text styles) from `TargetPracticePlay.kt`; align HUD chip styling with that file. The `showResults` gate hides HUD/panel — matches TargetPracticePlay behavior.

- [ ] **Step 2: Build to verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombinePlay.kt
git commit -m "feat(combine): play screen with banner and result gate"
```

---

### Task 13: `CombineResultsView` (full screen + read-only drill)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineResultsView.kt`

(Execution note 2026-10-10: built **before** Task 12 — `CombinePlay` consumes this view, so this task ships first to keep every task independently compilable.)

- [ ] **Step 1: Write `CombineResultsView.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.scoring.SkillBand
import com.hpsmiles.golfsim.games.ConfettiBurst
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import java.util.Locale

private val NegativeBarColor = Color(0xFFE4565A) // tune to palette if a token exists

@Composable
internal fun CombineResultsView(
    entry: CombineCatalogEntry,
    facts: List<CombineShotFacts>,
    record: CombineRecordComparison?,
    readOnly: Boolean,
    onPlayAgain: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (facts.isEmpty()) return
    val results = remember(entry, facts) { combineResults(entry, facts) }
    val sortedFacts = remember(facts) { facts.sortedBy { it.shotIndex } }

    Box(modifier.background(GolfColors.Base)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(GolfSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
        ) {
            Text(entry.name, style = GolfTypography.ScreenTitle, color = GolfColors.Teal)
            Text(CombineFormats.formatHcWithUncertainty(results.overall), style = GolfTypography.MetricValue, color = GolfColors.TextPrimary)
            Text("OVERALL HANDICAP", style = GolfTypography.MetricLabel, color = GolfColors.TextSecondary)

            SectionCard("VS BANDS") {
                Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                    for (band in SkillBand.entries) {
                        val sg = results.overall.perBandSg.getValue(band)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(bandLabel(band), style = GolfTypography.BodySmall, color = GolfColors.TextSecondary)
                            Text(String.format(Locale.US, "%+.2f", sg), style = GolfTypography.BodySmall, color = GolfColors.TextPrimary)
                        }
                    }
                }
            }

            for (station in results.stations) {
                SectionCard(station.label) {
                    Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                        Text(CombineFormats.formatHcWithUncertainty(station.estimate), style = GolfTypography.MetricValue, color = GolfColors.TextPrimary)
                        Text(
                            "${station.avgLabel} ${CombineFormats.oneDp(station.avgValue)} m",
                            style = GolfTypography.BodySmall,
                            color = GolfColors.TextSecondary,
                        )
                        for (band in SkillBand.entries) {
                            SgBar(station.estimate.perBandSg.getValue(band))
                        }
                    }
                }
            }

            SectionCard("SHOTS") {
                Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                    for (station in results.stations) {
                        val stationFacts = sortedFacts.filter {
                            CombinePlan.stationIndexFor(it.shotIndex) == station.stationIndex
                        }
                        stationFacts.forEachIndexed { i, fact ->
                            val stationDef = entry.stations[station.stationIndex]
                            val distanceText = if (stationDef.kind == StationKind.DRIVER) {
                                "${CombineFormats.zeroDp(DRIVER_HOLE_M - fact.restDistanceM)} m"
                            } else {
                                "${CombineFormats.oneDp(fact.restDistanceM)} m"
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "$distanceText · ${fact.restLie}",
                                    style = GolfTypography.BodySmall,
                                    color = GolfColors.TextSecondary,
                                )
                                Text(
                                    station.chips.getOrElse(i) { "" },
                                    style = GolfTypography.BodySmall,
                                    color = GolfColors.TextPrimary,
                                )
                            }
                        }
                    }
                }
            }

            if (!readOnly && record != null) {
                SectionCard("RECORD") {
                    Column {
                        Text(CombineRecord.headline(record), style = GolfTypography.MetricValue, color = GolfColors.Teal)
                        Text(CombineRecord.detail(record), style = GolfTypography.BodySmall, color = GolfColors.TextSecondary)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                if (!readOnly && onPlayAgain != null) {
                    Button(onClick = onPlayAgain) { Text("PLAY AGAIN") }
                }
                OutlinedButton(onClick = onBack) { Text(if (readOnly) "BACK" else "BACK") }
            }
        }
        if (!readOnly && record?.outcome == CombineRecordOutcome.NEW_RECORD) {
            ConfettiBurst(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SgBar(sg: Double) {
    val fraction = (Math.abs(sg) / 0.5).coerceAtMost(1.0).toFloat()
    Row(Modifier.fillMaxWidth().height(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (sg < 0) {
                Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(NegativeBarColor))
            }
        }
        Box(Modifier.width(1.dp).height(12.dp).background(GolfColors.TextMuted))
        Box(Modifier.weight(1f)) {
            if (sg > 0) {
                Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(GolfColors.Teal))
            }
        }
    }
}

private fun bandLabel(band: SkillBand): String = when (band) {
    SkillBand.TOUR -> "TOUR"
    SkillBand.SCRATCH -> "SCRATCH"
    SkillBand.HCP10 -> "10"
    SkillBand.HCP18 -> "18"
}
```

`station.chips` is aligned with the station's facts sorted by `shotIndex` (both come from `combineResults` / `sortedFacts`), so the `forEachIndexed` pairing above is exact.

- [ ] **Step 2: Build to verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineResultsView.kt
git commit -m "feat(combine): full-screen results view"
```

---

### Task 14: `CombineSetup` (catalog picker + resume + discard)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineSetup.kt`

- [ ] **Step 1: Write `CombineSetup.kt`**

```kotlin
package com.hpsmiles.golfsim.combine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.data.CombineSessionEntity
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.games.GameCard

@Composable
internal fun CombineSetup(
    combineActive: CombineSessionEntity?,
    takenCount: Int,
    onStart: (CombineCatalogEntry) -> Unit,
    onResume: () -> Unit,
    onDiscardAndStart: (CombineCatalogEntry) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingEntry by remember { mutableStateOf<CombineCatalogEntry?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
    ) {
        Text("COMBINES", style = GolfTypography.ScreenTitle, color = GolfColors.Teal)

        if (combineActive != null) {
            GameCard(
                title = "RESUME ${combineActive.combineName}",
                blurb = "$takenCount/${CombinePlan.SHOTS_TOTAL} shots · pick up where you left off",
                onClick = onResume,
            )
        }

        for (entry in CombineCatalog.entries) {
            GameCard(
                title = entry.name,
                blurb = entry.blurb + " · " + entry.stations.joinToString(" / ") { it.label },
                onClick = {
                    when {
                        combineActive == null -> onStart(entry)
                        combineActive.combineId == entry.id -> onResume()
                        else -> pendingEntry = entry
                    }
                },
            )
        }

        OutlinedButton(onClick = onCancel) { Text("CANCEL") }
    }

    pendingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingEntry = null },
            title = { Text("DISCARD UNFINISHED COMBINE?") },
            text = {
                Text(
                    "Your in-progress ${combineActive?.combineName ?: ""} " +
                        "(${takenCount}/${CombinePlan.SHOTS_TOTAL} shots) will be deleted."
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingEntry = null
                    onDiscardAndStart(entry)
                }) { Text("DISCARD") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingEntry = null }) { Text("KEEP") }
            },
        )
    }
}
```

Check `GameCard`'s visibility from `GamesScreen.kt` — if it is private there, make it internal in `GamesScreen.kt` and use it from this file (single small edit); the same applies to any shared tokens.

- [ ] **Step 2: Build to verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineSetup.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt
git commit -m "feat(combine): catalog setup with resume and discard"
```

---

### Task 15: `GamesScreen` integration

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt`

- [ ] **Step 1: Add `COMBINE` to the mode enum**

```kotlin
enum class GameMode { NONE, TARGET_PRACTICE, BREAK_PANE, COMBINE }
```

- [ ] **Step 2: Extend the `GamesScreen` signature**

Add parameters (mirroring the existing ones' style):

```kotlin
combineGame: CombineGame,
combineActive: CombineSessionEntity?,
combineTakenCount: Int,
onStartCombine: (CombineCatalogEntry) -> Unit,
onResumeCombine: () -> Unit,
onDiscardAndStartCombine: (CombineCatalogEntry) -> Unit,
```

with the combine-package imports (`com.hpsmiles.golfsim.combine.*`) and `com.hpsmiles.golfsim.core.data.CombineSessionEntity`.

- [ ] **Step 3: Add the third picker card**

In the picker `Column`, after the BREAK THE PANE `GameCard`:

```kotlin
GameCard(
    title = "COMBINES",
    blurb = if (combineActive != null) {
        "RESUME · ${combineTakenCount}/${CombinePlan.SHOTS_TOTAL}"
    } else {
        "Stock combines scored against your handicap"
    },
    onClick = { pickerState = "SETUP_COMBINE" },
)
```

- [ ] **Step 4: Add the setup branch**

In the `when` that dispatches `pickerState`, alongside `SETUP_TP`/`SETUP_BP`:

```kotlin
"SETUP_COMBINE" -> CombineSetup(
    combineActive = combineActive,
    takenCount = combineTakenCount,
    onStart = onStartCombine,
    onResume = onResumeCombine,
    onDiscardAndStart = onDiscardAndStartCombine,
    onCancel = { pickerState = "PICKER" },
    modifier = Modifier.weight(1f),
)
```

- [ ] **Step 5: Add the play branch**

Where `activeGame == TARGET_PRACTICE` / `BREAK_PANE` render their play views, add:

```kotlin
GameMode.COMBINE -> {
    val entry = combineGame.entry
    if (entry != null) {
        CombinePlay(
            entry = entry,
            game = combineGame,
            soundsEnabled = soundsEnabled,
            onSoundsChange = onSoundsChange,
            onPlayAgain = onPlayAgainCombine,
            onBack = {
                onActiveGameChange(GameMode.NONE)
                pickerState = "PICKER"
            },
            modifier = Modifier.weight(1f),
        )
    }
}
```

`onPlayAgainCombine: () -> Unit` is one more parameter (AppRoot provides it).

- [ ] **Step 6: Build to verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (errors will point at any missed parameter threading; fix file/line as flagged).

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt
git commit -m "feat(games): COMBINES card, setup and play wiring"
```

---

### Task 16: `AppRoot` wiring

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`

- [ ] **Step 1: State + surfaces**

Next to `val breakPane = remember { BreakThePaneGame() }`:

```kotlin
val combineGame = remember { CombineGame() }
```

In the existing `LaunchedEffect(greenCondition)` surface sync, add `combineGame.greenSurface = surface`; in `LaunchedEffect(turfCondition)`, add `combineGame.fairwaySurface = surface`.

- [ ] **Step 2: Flows**

After the fitting flows block (`fitActive`/`fitHistory`/`fitShots`/…), mirror the bag pattern:

```kotlin
val combineActive = gatedFlow(repoReady, { sessionRepository.combineActive }, flowOf(null))
    .collectAsState(initial = null).value
val combineActiveShots = remember(repoReady, combineActive?.id) {
    if (combineActive == null) flowOf(emptyList())
    else sessionRepository.observeCombineShots(combineActive.id)
}.collectAsState(initial = emptyList()).value
val combineHistory = gatedFlow(repoReady, { sessionRepository.combineHistory }, flowOf(emptyList()))
    .collectAsState(initial = emptyList()).value
val combineHistoryShots = gatedFlow(repoReady, { sessionRepository.combineHistoryShots }, flowOf(emptyList()))
    .collectAsState(initial = emptyList()).value
```

- [ ] **Step 3: `routeShot` combine branch**

After the `tab == BAG && bagCollecting` branch and before `when (activeGame)`:

```kotlin
if (activeGame == GameMode.COMBINE) {
    val entry = combineGame.entry ?: return
    val shot = combineGame.add(ballData) ?: return
    scope.launch {
        val id = combineGame.sessionId.takeIf { it > 0L }
            ?: sessionRepository.startCombineSession(
                entry.id, entry.name, entry.layoutVersion,
                System.currentTimeMillis(), source,
            )?.also { combineGame.sessionId = it }
            ?: return@launch
        sessionRepository.appendCombineShot(id, shot.index, shot.stationIndex, shot.restLie.name, shot.restDistanceM)
    }
    return
}
```

- [ ] **Step 4: Completion effect**

After the bag-done `LaunchedEffect` (~line 719), add:

```kotlin
LaunchedEffect(combineGame.complete, combineActive?.id) {
    val active = combineActive ?: return@LaunchedEffect
    if (!combineGame.complete || combineGame.sessionId != active.id) return@LaunchedEffect
    val entry = combineGame.entry ?: return@LaunchedEffect
    if (active.source == ShotSource.LIVE.code) {
        val priorSessions = sessionRepository.combineCompletedSessions(entry.id)
            .filter { it.source == ShotSource.LIVE.code }
        val priorShots = sessionRepository.combineShotsForSessions(priorSessions.map { it.id })
        val previousBest = priorShots.groupBy { it.sessionId }.values
            .filter { it.size >= CombinePlan.SHOTS_TOTAL }
            .mapNotNull { shots ->
                val facts = shots.map { it.toFacts() }
                runCatching { overallEstimate(entry, facts) }.getOrNull()
            }
            .minOrNull()
        combineGame.record = CombineRecord.compare(previousBest, overallEstimate(entry, combineGame.shots.map { it.toFacts() }))
    } else {
        combineGame.record = null
    }
    sessionRepository.completeCombineSession(active.id, System.currentTimeMillis())
}
```

- [ ] **Step 5: Session control functions**

After `completeFittingTest()`:

```kotlin
fun startCombine(entry: CombineCatalogEntry) {
    scope.launch {
        val id = sessionRepository.startCombineSession(
            entry.id, entry.name, entry.layoutVersion,
            System.currentTimeMillis(),
            if (demo) ShotSource.DEMO else ShotSource.LIVE,
        ) ?: return@launch
        combineGame.start(entry, id, baselineTaken = 0)
        activeGame = GameMode.COMBINE
    }
}

fun resumeCombine() {
    val active = combineActive ?: return
    val entry = CombineCatalog.byId(active.combineId) ?: return
    combineGame.start(entry, active.id, baselineTaken = combineActiveShots.size)
    activeGame = GameMode.COMBINE
}

fun discardCombineThenStart(entry: CombineCatalogEntry) {
    val active = combineActive
    scope.launch {
        if (active != null) sessionRepository.discardCombineSession(active.id)
        val id = sessionRepository.startCombineSession(
            entry.id, entry.name, entry.layoutVersion,
            System.currentTimeMillis(),
            if (demo) ShotSource.DEMO else ShotSource.LIVE,
        ) ?: return@launch
        combineGame.start(entry, id, baselineTaken = 0)
        activeGame = GameMode.COMBINE
    }
}

fun playAgainCombine() {
    val entry = combineGame.entry ?: return
    combineGame.reset()
    startCombine(entry)
}
```

- [ ] **Step 6: `requestTab` — leaving is safe**

In the `activeShots` / `activeComplete` when-blocks add (design: no destructive-leave dialog for combines):

```kotlin
GameMode.COMBINE -> false   // activeShots
GameMode.COMBINE -> true    // activeComplete
```

- [ ] **Step 7: Wire `GamesScreen` and `HistoryScreen` calls**

`GamesScreen(...)` gains:

```kotlin
combineGame = combineGame,
combineActive = combineActive,
combineTakenCount = combineActiveShots.size,
onStartCombine = ::startCombine,
onResumeCombine = ::resumeCombine,
onDiscardAndStartCombine = ::discardCombineThenStart,
onPlayAgainCombine = ::playAgainCombine,
```

`HistoryScreen(...)` gains:

```kotlin
combineHistory = combineHistory,
combineHistoryShots = combineHistoryShots,
```

- [ ] **Step 8: Build + run app tests**

Run: `.\gradlew.bat :app:compileDebugKotlin` then `.\gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all tests green.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(approot): combine wiring - routing, completion, resume, history feeds"
```

---

### Task 17: HISTORY integration

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryScreen.kt`

- [ ] **Step 1: Extend the `HistoryScreen` signature**

```kotlin
combineHistory: List<CombineSessionEntity> = emptyList(),
combineHistoryShots: List<CombineShotEntity> = emptyList(),
```

Add the imports (`com.hpsmiles.golfsim.core.data.CombineSessionEntity`, `CombineShotEntity`, `com.hpsmiles.golfsim.combine.*`).

- [ ] **Step 2: Hoist the read-only drill state**

In `HistoryScreen`:

```kotlin
var viewedCombineId by remember { mutableStateOf<Long?>(null) }
```

When `viewedCombineId != null`: render (instead of the tab content)

```kotlin
val session = combineHistory.firstOrNull { it.id == viewedCombineId }
val entry = session?.let { CombineCatalog.byId(it.combineId) }
if (session != null && entry != null) {
    CombineResultsView(
        entry = entry,
        facts = combineHistoryShots.filter { it.sessionId == session.id }.map { it.toFacts() },
        record = null,
        readOnly = true,
        onBack = { viewedCombineId = null },
        modifier = Modifier.weight(1f),
    )
} else {
    // session vanished (discarded): reset after composition, the list renders next
    LaunchedEffect(Unit) { viewedCombineId = null }
}
```

- [ ] **Step 3: Add the COMBINE section inside `GamesHistory`**

Pass the new lists into `GamesHistory` and, after the existing per-mode game groups, add:

```kotlin
val combineRows = remember(combineHistory, combineHistoryShots, order, onOpenCombine) {
    combineHistory
        .mapNotNull { session ->
            val entry = CombineCatalog.byId(session.combineId) ?: return@mapNotNull null
            val facts = combineHistoryShots.filter { it.sessionId == session.id }.map { it.toFacts() }
            if (facts.size < CombinePlan.SHOTS_TOTAL) return@mapNotNull null
            session to overallEstimate(entry, facts)
        }
        .let { rows ->
            when (order) {
                GameHistoryOrder.RECENCY -> rows.sortedByDescending { it.first.startedAtMs }
                GameHistoryOrder.SCORE -> rows.sortedBy { it.second }  // BEST = lowest overall HC
            }
        }
}
if (combineRows.isNotEmpty()) {
    Text("COMBINES", style = GolfTypography.MetricLabel, color = GolfColors.TextSecondary, modifier = Modifier.padding(top = GolfSpacing.Sm))
    for ((session, est) in combineRows) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(GolfSpacing.CornerCard))
                .background(GolfColors.Panel)
                .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.CornerCard))
                .clickable { onOpenCombine(session.id) }
                .padding(GolfSpacing.Md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(session.combineName, style = GolfTypography.Body, color = GolfColors.TextPrimary)
                Text(
                    GameHistoryFormats.dateTime(session.startedAtMs) + if (session.source == 1) " · DEMO" else "",
                    style = GolfTypography.BodySmall,
                    color = GolfColors.TextMuted,
                )
            }
            Text(CombineFormats.formatHc(est), style = GolfTypography.MetricValue, color = GolfColors.Teal)
        }
    }
}
```

Add `onOpenCombine: (Long) -> Unit` to `GamesHistory`'s parameters and pass `onOpenCombine = { viewedCombineId = it }` at the call site. Copy the exact styling tokens/shape from `GameResultRow` in the same file (that row is the reference for padding/border/colors).

- [ ] **Step 4: Build + run app tests**

Run: `.\gradlew.bat :app:compileDebugKotlin` then `.\gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; tests green.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryScreen.kt
git commit -m "feat(history): COMBINES group + read-only results drill"
```

---

### Task 18: Final gate + on-device checklist

**Files:** none (verification).

- [ ] **Step 1: Full build + all tests**

Run: `.\gradlew.bat build`
Expected: `BUILD SUCCESSFUL` (includes `:core:scoring:test`).

- [ ] **Step 2: Install on the tablet**

Run: `.\gradlew.bat :app:installDebug`

- [ ] **Step 3: Manual checklist (spec §8)**

- [ ] GAMES picker shows COMBINES; SETUP lists WEDGES + APPROACH with stations; CANCEL returns to picker.
- [ ] Start APPROACH with demo mode ON: fire 18 shots; TARGETS counts advance 0/18 → 18/18; banners appear after shots 2, 6, 12 (2 s); pin relocates each visit; driver station has no green and no target marking; LAST SHOT chips match RANGE chips.
- [ ] RESULTS appear after the final animation: overall HC ± , band strip, per-station cards (100/150 show AVG TO PIN; driver AVG TOTAL), diverging bars, per-shot ledger (approach: rest·lie·chip; driver: distance hit·lie·chip), record banner.
- [ ] LIVE test with the MLM2PRO (arm, record): shots route only to the combine while on GAMES > COMBINE; misreads do nothing bad.
- [ ] Kill the app mid-combine (after ~7 shots) → reopen: GAMES picker shows RESUME · 7/18; resuming continues at the right station/visit; completing it lands in HISTORY.
- [ ] HISTORY > GAMES shows the COMBINES group; RECENT/BEST toggle re-orders (BEST = lowest overall HC); tapping a row opens the read-only results; BACK returns.
- [ ] Starting a different combine while one is unfinished shows the discard dialog; DISCARD deletes the old session and starts the new one.
- [ ] Leaving GAMES mid-combine (to RANGE etc.) requires no confirm; the unfinished session is resumable; range shots are unaffected.
- [ ] Demo sessions appear in HISTORY with the DEMO marker and never set records; LIVE records only.

- [ ] **Step 4: Final commit (if any checklist fixes were needed)**

```powershell
git add -A
git commit -m "fix(combine): on-device checklist corrections"
```

---

## Self-review notes (already applied)

- Spec coverage: §1 scoring (Tasks 3–4), §2 data (Task 2), §3 module (Tasks 1–4), §4 flow (Tasks 7, 9, 12, 16), §5 screens (Tasks 11–15), §6 persistence/history (Tasks 5–6, 17), §7 architecture (file layout), §8 testing (every test task + Task 18 checklist).
- Fixture values: `-5.84`, `±6.5`, `0.0254` slope — matches the corrected spec (a1488d6).
- Type consistency: `CombineShotFacts`/`toFacts` used by results, play, history; `HandicapEstimate` field names consistent across formats/results/views.
