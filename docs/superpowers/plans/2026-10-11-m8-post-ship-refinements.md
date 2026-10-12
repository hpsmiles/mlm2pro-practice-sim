# M8 Post-Ship Refinements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the combine results screen (benchmark-locked ledger, collapsible shots) and add a top-down dispersion view.

**Architecture:** One small scoring addition (benchmark lock), one Room migration (shot rest coordinates), and two app-side view changes. All maths stays in pure, JVM-tested code; the views stay plain Compose.

**Tech Stack:** Kotlin, Jetpack Compose, Room (v7), JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-11-m8-post-ship-refinements-design.md`

**Build commands (Windows, per AGENTS.md)**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat build
.\gradlew.bat :core:scoring:test                        # plain test task (pure JVM module)
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.CombineRepositoryTest"
```

**Branch:** `m8-custom-combines` (current HEAD `9631f91`).

## File Structure

- `core/scoring/src/main/kotlin/.../SkillBand.kt` (mod) — add `nearestTo(hc)`.
- `core/scoring/src/test/kotlin/.../SkillBandTest.kt` (new) — lock-rule pins.
- `app/src/main/kotlin/.../combine/CombineResults.kt` (mod) — `lockedBand` + per-band per-shot SG helper.
- `app/src/main/kotlin/.../combine/CombineResultsTest.kt` (mod) — locked-band pins.
- `core/data/src/main/kotlin/.../CombineEntities.kt` (mod) — `restXM`/`restYM`.
- `core/data/src/main/kotlin/.../Mlm2proDatabase.kt` (mod) — v7 + `MIGRATION_6_7`.
- `core/data/schemas/.../7.json` (new) — generated + committed.
- `core/data/src/main/kotlin/.../SessionRepository.kt` (mod) — append coordinates.
- `core/data/src/test/kotlin/.../MigrationFrom6Test.kt` (new).
- `app/src/main/kotlin/.../range/AppRoot.kt` (mod) — pass rest coordinates at append.
- `app/src/main/kotlin/.../combine/CombineResultsView.kt` (rewrite) — new layout + collapsible ledger.
- `app/src/main/kotlin/.../combine/CombineTopDownView.kt` (new) — dispersion view.

---

### Task 1: Benchmark lock

**Files:**
- Modify: `core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SkillBand.kt`
- Create: `core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/SkillBandTest.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineResults.kt`
- Modify: `app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineResultsTest.kt`

- [x] **Step 1: failing test for the lock rule**

In `SkillBandTest.kt` pin `SkillBand.nearestTo(hc)`:

```kotlin
assertEquals(SkillBand.TOUR, SkillBand.nearestTo(-9.0))
assertEquals(SkillBand.TOUR, SkillBand.nearestTo(-5.84))   // golden-fixture overall estimate
assertEquals(SkillBand.SCRATCH, SkillBand.nearestTo(-1.3))
assertEquals(SkillBand.SCRATCH, SkillBand.nearestTo(5.0)) // tie 0 vs 10 -> first wins
assertEquals(SkillBand.HCP10, SkillBand.nearestTo(14.0))  // tie 10 vs 18 -> first wins
assertEquals(SkillBand.HCP18, SkillBand.nearestTo(21.83)) // driver demo run
assertEquals(SkillBand.HCP18, SkillBand.nearestTo(Double.POSITIVE_INFINITY))
assertEquals(SkillBand.HCP18, SkillBand.nearestTo(Double.NaN))
```

- [x] **Step 2: run it and watch it fail**

Run: `.\gradlew.bat --console=plain :core:scoring:test`
Expected: FAIL — unresolved reference `nearestTo`.

- [x] **Step 3: implement the lock rule**

```kotlin
/**
 * The benchmark band whose anchor is nearest [hc], after clamping out-of-range
 * and non-finite values to the anchor range. Ties resolve to the earlier entry
 * (SCRATCH before HCP10, HCP10 before HCP18).
 */
fun nearestTo(hc: Double): SkillBand {
    val h = when {
        hc.isNaN() || hc > HCP18.anchorHc -> HCP18.anchorHc
        hc < TOUR.anchorHc -> TOUR.anchorHc
        else -> hc
    }
    return entries.minByOrNull { kotlin.math.abs(it.anchorHc - h) }!!
}
```

- [x] **Step 4: app side — locked band + per-band shot SG**

In `CombineResults.kt`:
- add `val lockedBand: SkillBand` to the `CombineResults` data class, computed in `combineResults(...)` as
  `SkillBand.nearestTo(overall.est)`;
- refactor the existing private per-shot routing so it takes a band:
  `fun shotSgForBand(entry: CombineCatalogEntry, fact: CombineShotFacts, band: SkillBand): Double`
  (DRIVER station → `drivingStrokesGained(band, fact.restLie, fact.restDistanceM)`; otherwise
  `strokesGained(band, startLie, startM, endLie, endM)` from `CombinePlan.shotInput`), and keep the existing
  per-shot chip behaviour working (chips may keep using the per-shot nearest band, or use `lockedBand` —
  choose `lockedBand` for consistency and note it).

- [x] **Step 5: app tests**

In `CombineResultsTest.kt` add: the golden 100 m fixture (overall ≈ −5.84) → `lockedBand == SkillBand.TOUR`;
the driver demo facts (overall ≈ 21.83) → `lockedBand == SkillBand.HCP18`; and `shotSgForBand` for one
approach fact and one driver fact against a fixed band (values recomputed from the tables, tolerance 0.01).

- [x] **Step 6: verify + commit**

Run: `.\gradlew.bat --console=plain :core:scoring:test` then `.\gradlew.bat --console=plain :app:testDebugUnitTest --rerun-tasks`
Expected: all green (25 scoring + 308 app + the new tests).

```bash
git add core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SkillBand.kt \
        core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/SkillBandTest.kt \
        app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineResults.kt \
        app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineResultsTest.kt
git commit -m "feat(combine): lock each completed session to its nearest benchmark band"
```

---

### Task 2: Store combine shot rest coordinates (Room v7)

**Files:**
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/CombineEntities.kt`
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabase.kt`
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt`
- Create: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/MigrationFrom6Test.kt`
- Create: `core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/7.json`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`

- [x] **Step 1: entity + migration**

`CombineShotEntity` gains `val restXM: Double?` and `val restYM: Double?` (nullable — old rows have none).
`Mlm2proDatabase`: version 6 → 7; `MIGRATION_6_7` = two `ALTER TABLE combine_shots ADD COLUMN ... REAL`
statements (nullable, no default). Register it in `SessionRepository.build()`.

- [x] **Step 2: repository**

`appendCombineShot(...)` gains `restXM: Double? = null, restYM: Double? = null` (defaults keep existing call
sites compiling) and writes them.

- [x] **Step 3: AppRoot passes the real coordinates**

At the COMBINE append call site pass `restXM = shot.display.shotResult.restX`, `restYM = shot.display.shotResult.restY`.

- [x] **Step 4: migration test + schema**

Clone the `MigrationFrom5Test` pattern for v6 → v7 (hand-built v6 file + the v6 identity hash from `6.json`,
`raw.version = 6`), assert prior data survives and that a combine session can be started, appended to (with
coordinates) and completed. Generate `7.json` via a build, then commit it.

- [x] **Step 5: verify + commit**

Run: `.\gradlew.bat --console=plain :core:data:testDebugUnitTest --rerun-tasks`
Expected: all green (including the new migration test and the existing `CombineRepositoryTest`, which should
gain a case asserting the coordinates round-trip).

```bash
git commit -m "feat(data): store combine shot rest coordinates (Room v7)"
```

---

### Task 3: Results screen redesign

**Files:**
- Rewrite: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineResultsView.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineFormats.kt` (if a band-label helper is needed)

- [x] **Step 1: rebuild the layout** (per spec §2)

Single vertical column: centred hero overall handicap (large, e.g. 44–52 sp) with the ± uncertainty smaller
beneath; a row of three per-target items (station label, `formatHc(estimate)`, then "avg to pin"/"avg total"
with the value); the VS BANDS strip (unchanged); the record banner (non-read-only); a collapsible SHOTS
section **collapsed by default** whose header shows `"SHOTS · ${facts.size} shots · scored vs ${bandLabel}"`;
expanded → the ledger table (#, target, result, lie, SG vs `lockedBand`, colour-coded); BACK / PLAY AGAIN
buttons.

- [x] **Step 2: read-only behaviour unchanged** — no PLAY AGAIN, no record card, no confetti; the drill's
  session-context header (date + DEMO) stays where it is (HistoryScreen owns it).

- [x] **Step 3: verify + commit**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --rerun-tasks`
Expected: all green (no new tests — screens are untested by convention; keep logic in `CombineResults`).

```bash
git commit -m "feat(combine): results redesign (hero, per-target row, collapsible benchmark-locked ledger)"
```

---

### Task 4: Top-down dispersion view

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineTopDownView.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineResultsView.kt` (a SUMMARY / TOP-DOWN toggle)
- Create: `app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineDispersionTest.kt` (pure geometry/stats)

- [x] **Step 1: pure dispersion maths (tested)**

A small pure object (e.g. `CombineDispersion`) with, per group: centroid, `sigmaX`, `sigmaY` (population or
n−1 — pick one and document), long–short spread, left–right spread, and a `hasRing` flag for groups with
fewer than 2 shots. Pin it in `CombineDispersionTest` with hand-computed values.

- [x] **Step 2: the view**

`CombineTopDownView(entry, facts, modifier)`: horizontal plan view, tee left, 0–330 m right; the constant
28 m corridor (reuse `RangeScene.HALF_WIDTH_M`/`fairwayHalfWidth`), gridlines + labels every 50 m, the 100 m
and 150 m greens as circles with pins (at each station's visit-1 pin), the three groups plotted in absolute
hole coordinates from `restXM`/`restYM` — finish dots, centroid, one dashed 1σ ellipse — colour-coded per
group (reuse `FittingColors.clubColor(index)`); a side panel with the per-group stats. If no fact has
coordinates, render "no dispersion data for this session".

- [x] **Step 3: toggle from the results screen**

A SUMMARY / TOP-DOWN toggle in the results screen switches between the redesigned results view and the
top-down view (state hoisted in the results screen so it also works in the read-only history drill).

- [x] **Step 4: verify + commit**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --rerun-tasks`
Expected: all green.

```bash
git commit -m "feat(combine): top-down dispersion view"
```

---

### Task 5: Final gate

- [x] **Step 1:** `.\gradlew.bat build` → BUILD SUCCESSFUL.
- [x] **Step 2:** `.\gradlew.bat :app:installDebug` (tablet `TB373FU - 16`).
- [x] **Step 3:** hand the user the on-device checklist: results hero + per-target row + VS BANDS + record
  banner; SHOTS collapsed by default and expanding to the ledger scored against the locked band; TOP-DOWN
  toggle showing the three groups with 1σ rings, the 100 m/150 m greens + pins and the per-group stats;
  HISTORY drill read-only version of both views; a new demo run storing coordinates (older sessions show
  "no dispersion data").
- [x] **Step 4:** commit any checklist corrections.

## Self-review

- Spec coverage: §1 → T1; §2 → T3; §3 → T4; §4 → T2. Out-of-scope items are not implemented.
- The ledger's per-shot SG now uses the locked band; `CombineResults`' chips should follow the same band for
  consistency (documented in T1 Step 4).
- Types: `restXM`/`restYM` nullable everywhere; `lockedBand` is a `SkillBand`; `shotSgForBand` takes an
  explicit band.
- No placeholder steps; every task names its files, verification command and commit message.
