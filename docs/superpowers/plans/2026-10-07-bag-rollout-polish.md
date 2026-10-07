# Bag/Rollout Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show Launch Direction on all metric displays, make bag mapping wedge-first/random with a live range view + dual carry/total results, and recalibrate ground rollout (driver band + rough ordering).

**Architecture:** Four lanes from the approved spec (`docs/superpowers/specs/2026-10-07-bag-rollout-polish-design.md`). Lane A (HLA display) and Lane C (core/physics) are file-disjoint from Lane B (bag UI + RangeScreen extraction); Lane D wires AppRoot/collector after B lands. Compile must stay green at every commit — B gives all new Compose params defaults so call sites keep compiling until D wires them.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4, Room (no schema change), Gradle on Windows.

**Build conventions (AGENTS.md):** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before any gradle command. PowerShell: `;` separators, never `&&`. Gradle flags precede task names. Unit tests use typed variants: `:app:testDebugUnitTest`, `:core:physics:test`.

**File ownership (one writer per file):**
- Lane A: `HistoryFormats.kt`(+test), `HistoryGrouping.kt`(+test), `HistoryScreen.kt`, `GameMetricsPanel.kt`
- Lane B: `RangeScreen.kt` (extraction + DIR chip), `RangeLiveView.kt` (new), `BagMappingIntro.kt`, `BagMappingScreen.kt`, `BagMappingCollecting.kt`, `BagMappingResult.kt`, `CarryMatrixCanvas.kt`, `BagMappingFormats.kt`, `BagPlanOrder.kt` (enum only, new)
- Lane C: `core/physics` only (`BounceRollModel.kt`, `Surface.kt`, new tests)
- Lane D (after B): `AppRoot.kt`, `BagMappingCollector.kt`(+test touch-ups), `BagPlanOrder.kt` helper(+test), `BagMappingScreen.kt` pass-through

---

## Lane A — Launch Direction (HLA) on all metric displays

Sign convention is settled (do not re-litigate): **negative = left of target, positive = right** — verified on-device M4c (`docs/superpowers/HANDOVER-M4c-shed-capture-2026-09-20.md:160`).

### Task A1: `HistoryFormats.dir` formatter + test

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryFormats.kt` (after `axis` at :42-43)
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/history/HistoryFormatsTest.kt`

- [ ] **Step 1: Write the failing test** (append inside `class HistoryFormatsTest`):

```kotlin
@Test
fun `dir formats signed degrees with unicode minus`() {
    assertEquals("+3.5°", HistoryFormats.dir(3.46))
    assertEquals("−2.0°", HistoryFormats.dir(-2.04))
    assertEquals("+0.0°", HistoryFormats.dir(0.0))
}
```

- [ ] **Step 2: Run it to verify it fails**
`.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.history.HistoryFormatsTest"` → FAIL (unresolved reference `dir`).

- [ ] **Step 3: Implement** (mirror `axis`, HistoryFormats.kt:42-43):

```kotlin
/** Signed HLA (°): − left / + right of target (sign verified on-device, M4c handover). */
fun dir(deg: Double): String = String.format(Locale.US, "%+.1f°", deg).replace('-', '−')
```

- [ ] **Step 4: Run the test again** → PASS.
- [ ] **Step 5: Commit** `feat(history): signed HLA dir formatter (U+2212)`.

### Task A2: History table DIR column + per-club average

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryGrouping.kt` (ClubStats :25-35, statsFor :63-79)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryScreen.kt` (HeaderRow :508-518, AvgRow :541-562, ShotRow :605-616)
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/history/HistoryGroupingTest.kt`

- [ ] **Step 1: Failing test** — add `launchDir: Double = -1.0` param to the `shot(...)` helper (after `side`), pass it into `BallData(33.0, 48.0, launchDir, 12.0, -4.0, 8000, 5, 10)`, then:

```kotlin
@Test
fun `club stats average launch direction over included shots`() {
    val group = ClubGroup("D", listOf(shot(1, "D", launchDir = -2.0), shot(2, "D", launchDir = 4.0), shot(3, "D", launchDir = 6.0, excluded = true)))
    assertEquals(1.0, statsFor(group).avgDirDeg!!, 1e-9)
}
```

- [ ] **Step 2: Verify FAIL** (no `avgDirDeg`).
- [ ] **Step 3: Implement** — ClubStats gains `val avgDirDeg: Double?,` after `avgLaunchDeg`; `statsFor` gains `avgDirDeg = avg(incl.map { it.ballData.launchDirection }),`.
- [ ] **Step 4: HistoryScreen** — three insertions, all after the LAUNCH entries:
  - `HeaderRow` (:510-514): `"LAUNCH" to 72.dp, "DIR" to 72.dp,` then `"AXIS" to 72.dp,`.
  - `AvgRow` (:557-558): `AvgCell(HistoryFormats::dir, stats.avgDirDeg, 72.dp)` between launch and axis cells.
  - `ShotRow` (:614-615): `ShotCell(HistoryFormats.dir(shot.ballData.launchDirection), 72.dp, shot.excluded)` between launch and axis cells.
  - `GroupHeaderRow` stays unchanged (no σ dir).
- [ ] **Step 5: Run** `:app:testDebugUnitTest --tests "com.hpsmiles.golfsim.history.*"` → PASS.
- [ ] **Step 6: Commit** `feat(history): DIR column + per-club avg launch direction`.

### Task A3: GameMetricsPanel DIR chip

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GameMetricsPanel.kt:84-86`

- [ ] **Step 1:** After the `launch` chip (:84) and its Spacer, insert (panels use plain ASCII minus — match `side`-style signed display):

```kotlin
Spacer(Modifier.size(GolfSpacing.Xs))
// HLA sign verified on-device M4c: − left / + right of target.
MetricChip("dir", String.format(Locale.US, "%+.1f", currentShot.ballData.launchDirection), "DEG")
```

- [ ] **Step 2:** `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.*"` → PASS (panel has no JVM test; this verifies compile).
- [ ] **Step 3: Commit** `feat(games): HLA chip in game metrics panel (mirrors range)`.

Note: the Range LAST SHOT chip lives in `RangeScreen.kt`, owned by Lane B this cycle — Task B1 adds the identical chip there. Do NOT touch RangeScreen.

---

## Lane B — Bag UI: live range view, dual-box results, intro order selector

Design taste authority: @designer. Constraints below are the contract; visuals (colors/spacing/labels) are designer's within the design system (`GolfColors/GolfSpacing/GolfTypography`).

### Task B1: RangeScreen — extract RangeLiveView + DIR chip

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeLiveView.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt:95-260` (delegation + chip)

- [ ] **Step 1: Extract** the POV live-view machinery (RangeScreen.kt:104-186: `playFraction`/`speedMult`/tracer state + both `LaunchedEffect`s + `previousShots` + `BoxWithConstraints`/camera/`PovRangeCanvas` + tracer chips at :209-234) into:

```kotlin
/**
 * Live POV range view: tracer playback, follow cam, previous-shot lines and
 * tracer controls. Extracted from RangeScreen so the BAG collecting view can
 * render the same shot visual (spec item 3) without duplicating rendering.
 */
@Composable
fun RangeLiveView(
    shots: List<DisplayShot>,
    modifier: Modifier = Modifier,
    speedMult: SpeedMult = SpeedMult.X15,
)
```

Internally: `currentShot = shots.lastOrNull()`; keep the animation loops, follow cam, `PovRangeCanvas(currentShot?.shotResult, previousShots, playFraction, showTracer, showHistory, camera = camera, customGreen = null, ...)` — **customGreen = null** (bag sim is uniform fairway; no green oval). Tracer/history toggles become internal state (default ON/ON), speed slider only rendered when the caller opts in (`showControls: Boolean = true`) — RangeScreen passes its existing `speedMult` through; bag overlay uses defaults without the slider if the designer prefers a cleaner overlay (either is acceptable).

- [ ] **Step 2: RangeScreen delegates** — the canvas `Box` (formerly :164-186) becomes:

```kotlin
Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
    if (viewMode == ViewMode.POV) {
        RangeLiveView(
            shots = session.shots,
            modifier = Modifier.fillMaxSize(),
            speedMult = speedMult,
        )
    } else {
        TopDownCanvas(session.shots, Modifier.fillMaxSize())
    }
    // no-read pill stays here (range-specific) — unchanged
}
```

Keep `speedMult` + its control and `viewMode` ownership in RangeScreen. **Every existing RANGE tab behavior must still work** (tracer chips, prev-lines slider, follow cam, speed control).

- [ ] **Step 3: DIR chip** in the LAST SHOT panel — after the `launch` chip (:355), before `axis` (:357), mirroring Task A3:

```kotlin
Spacer(Modifier.size(GolfSpacing.Xs))
// HLA sign verified on-device M4c: − left / + right of target.
MetricChip("dir", String.format(Locale.US, "%+.1f", shot.ballData.launchDirection), "DEG")
```

- [ ] **Step 4:** `.\gradlew.bat :app:compileDebugKotlin` → green; run `:app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.*"` → PASS (RangeSurfaceTest, RangeSignsTest, etc. must stay green).
- [ ] **Step 5: Commit** `refactor(range): extract RangeLiveView; add HLA chip`.

### Task B2: Bag order enum (type only — logic lands in Lane D)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagPlanOrder.kt`

```kotlin
package com.hpsmiles.golfsim.bag

/** Bag-mapping test order chosen on the intro screen (spec item 2). */
enum class BagOrderMode {
    /** Shortest club first (LW → driver), ascending to driver. */
    WEDGE_FIRST,
    /** Full plan shuffled (kotlin.random, default seed). */
    RANDOM,
}
```

- [ ] **Step 1: Commit** `feat(bag): BagOrderMode enum`.

### Task B3: Intro order selector (seam only)

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingIntro.kt`

- [ ] **Step 1:** Change `onStartTest: () -> Unit` → `onStartTest: (BagOrderMode) -> Unit`. Add internal state + a two-option selector (designer's visual treatment; grounded wording, e.g. "ORDER" label, options "SHORT → LONG" (default `WEDGE_FIRST`) and "RANDOM"):

```kotlin
var orderMode by remember { mutableStateOf(BagOrderMode.WEDGE_FIRST) }
// ... selector row bound to orderMode ...
Button(onClick = { onStartTest(orderMode) }, ...)
```

- [ ] **Step 2:** In `BagMappingScreen.kt:91-96` the INTRO call keeps compiling: pass `{ onStartTest() }` (mode deliberately ignored until Lane D flips the Screen param type). Add a `// TODO(lane-D): thread order mode` comment.
- [ ] **Step 3:** `.\gradlew.bat :app:compileDebugKotlin` → green. **Commit** `feat(bag): intro order selector (wedge-first default + random)`.

### Task B4: Collecting = live range view + compact overlay card

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingCollecting.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingScreen.kt:97-102`

- [ ] **Step 1:** `BagMappingCollecting` gains `latestShot: DisplayShot? = null` (default keeps all call sites compiling). Rebuild the body as a `Box(Modifier.fillMaxSize())`:
  - **Base:** `RangeLiveView(shots = listOfNotNull(latestShot), modifier = Modifier.fillMaxSize())` — full-size, same tracer/follow-cam visual as the RANGE tab.
  - **Overlay card:** the existing collecting content (club banner, progress dots, last carry, no-read pill, next-up, gate prompt, SKIP/END) shrinks to a compact card (designer: placement + translucency so the target view stays readable; keep every existing control and the `LaunchedEffect` gate logic untouched).
- [ ] **Step 2:** `BagMappingScreen` gains `latestShot: DisplayShot? = null` and forwards it to `BagMappingCollecting` (COLLECTING branch :97-102).
- [ ] **Step 3:** `.\gradlew.bat :app:compileDebugKotlin`; `:app:testDebugUnitTest --tests "com.hpsmiles.golfsim.*"` → PASS. **Commit** `feat(bag): collecting view overlays the live range view`.

### Task B5: Dual-box results matrix (carry + total per club row)

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingResult.kt` (`CarryMatrixSection` :102-156, `ClubRow` :158-208, `DrillDownPanel` :210-260)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/CarryMatrixCanvas.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingFormats.kt`

- [ ] **Step 1:** `BagMappingFormats` gains `fun total(m: Double): String = String.format(Locale.US, "%.0f m", m)`.
- [ ] **Step 2:** Restructure the matrix (data contract fixed, visuals designer-owned):
  - Per club row: **carry box + total box** drawn from `BagMappingStats.distribution` over kept carries and kept totals respectively, on **one shared metre axis** computed over every club's kept carry **and** kept total values (`BoxPlotGeom.axis(rows.flatMap { it.keptCarry + it.keptTotal }, ...)` — same nominal-1f pattern as :119-123).
  - Two-color legend shown once above the matrix ("CARRY" / "TOTAL"); median labels for both; σ/kept counts stay visible (carry σ + total σ on one line is fine). Filtered dots keep their existing treatment (carry axis).
  - Gap flags stay carry-based (`mappedMedians` unchanged); RETEST/HISTORY/TEST-IN-PROGRESS banners unchanged.
- [ ] **Step 3:** `DrillDownPanel` rows gain total distance: add `Text(BagMappingFormats.total(shot.totalM), ...)` between the carry and smash texts (:241-246).
- [ ] **Step 4:** `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.*"` → PASS (GapAnalysis/BoxPlotGeom/Collector tests untouched and green). **Commit** `feat(bag): dual carry+total box-plot matrix and drill-down totals`.

---

## Lane C — Physics recalibration (driver rollout + rough ordering)

All work in `core/physics`. Requirements (spec §5): (1) tangential speed continuous/monotonic vs impact angle across `thetaCrit` — no cliff; (2) driver fairway rollout ≈ 18–35 m on normal fairway (driver-class landings); (3) rough ≤ fairway for identical landings at ALL angles, esp. the 15–25° driver band; (4) all existing pins stay green; (5) recalibration documented in code with date. **Root cause** (verified): `BounceRollModel.kt:110-141` — steep-branch `angleBlend` (span `STEEP_BLEND_SPAN_RAD = 0.35`, `:59`) collapses driver post-bounce speed (~11 → ~7.6 m/s ⇒ ~10 m rollout), while just-under-thetaCrit landings take the shallow skid branch (~12.7 m/s ⇒ ~30 m) — ±1° ⇒ ~3× rollout. Rough's higher `thetaCritRad` (0.35 vs fairway 0.29, `Surface.kt:88-95`) makes the same driver impact "shallow/fast" on rough but "steep/slow" on fairway.

### Task C1: Sweep diagnostic (steering tool)

**Files:**
- Create: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/RolloutSweepDiagnostic.kt`

- [ ] **Step 1:** Pure sweep table (always passes; diagnostic only, print via println):

```kotlin
class RolloutSweepDiagnostic {
    private val env = Environment()

    private fun landing(angleDeg: Double, spinRpm: Double): LandingState {
        val vh = 35.0
        val vn = vh * Math.tan(Math.toRadians(angleDeg))
        val omega = spinRpm * 2.0 * Math.PI / 60.0
        return LandingState(Vec3(0.0, 0.0, 0.0), Vec3(0.0, vh, -vn), Vec3(omega, 0.0, 0.0), 30.0, 6.0)
    }

    @Test
    fun printSweepTable() {
        val surfaces = listOf(
            "FAIRWAY" to Surface.FAIRWAY_NORMAL,
            "FIRM" to Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM),
            "ROUGH" to Surface.ROUGH_NORMAL,
            "GREEN" to Surface.GREEN_NORMAL,
        )
        println("angle | spin | " + surfaces.joinToString(" | ") { it.first })
        for (angle in 10..50 step 2) {
            for (spin in doubleArrayOf(1500.0, 2600.0, 4500.0, 8000.0, 10000.0)) {
                val cells = surfaces.joinToString(" | ") { (_, s) ->
                    "%.1f".format(BounceRollModel.bounceAndRoll(landing(angle.toDouble(), spin), s, 12.0, env).deltaY)
                }
                println("${angle}deg | ${spin.toInt()}rpm | $cells")
            }
        }
        assertTrue(true)
    }
}
```

- [ ] **Step 2:** Run `.\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.RolloutSweepDiagnostic"` and read `core/physics/build/test-results/test/TEST-*.xml` (or `-i` stdout) — capture the PRE-change table (drivers ≈ 2600 rpm band must currently show ~10 m fairway and ROUGH > FAIRWAY around 16–20°).

### Task C2: Failing regression tests

**Files:**
- Create: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/RolloutRecalibrationTest.kt`

- [ ] **Step 1:** Write (all three FAIL pre-change):

```kotlin
class RolloutRecalibrationTest {
    private val env = Environment()

    private fun landing(angleDeg: Double, spinRpm: Double, vh: Double = 35.0): LandingState {
        val vn = vh * Math.tan(Math.toRadians(angleDeg))
        val omega = spinRpm * 2.0 * Math.PI / 60.0
        return LandingState(Vec3(0.0, 0.0, 0.0), Vec3(0.0, vh, -vn), Vec3(omega, 0.0, 0.0), 30.0, 6.0)
    }

    /** Spec req 2: driver-class fairway rollout lands in the realistic band. */
    @Test
    fun driverFairwayRolloutRealistic() {
        for (angle in 17..24) for (spin in intArrayOf(2000, 2600, 3000)) {
            val rollout = BounceRollModel.bounceAndRoll(
                landing(angle.toDouble(), spin.toDouble()), Surface.FAIRWAY_NORMAL, 12.0, env,
            ).deltaY
            assertTrue(
                "driver ${angle}deg/${spin}rpm rollout ${"%.1f".format(rollout)} m outside [18, 35]",
                rollout in 18.0..35.0,
            )
        }
    }

    /** Spec req 1: no cliff — adjacent-degree rollout ratio stays bounded. */
    @Test
    fun rolloutContinuousAcrossThetaCrit() {
        var prev = -1.0
        for (angle in 12..32) {
            val r = BounceRollModel.bounceAndRoll(
                landing(angle.toDouble(), 2600.0), Surface.FAIRWAY_NORMAL, 12.0, env,
            ).deltaY
            if (prev > 0) assertTrue(
                "rollout jumped $prev -> $r at ${angle}deg",
                r <= prev * 1.5 + 1.0,
            )
            prev = r
        }
    }

    /** Spec req 3: rough never out-rolls fairway for identical landings. */
    @Test
    fun roughNeverOutRollsFairway() {
        for (angle in 12..40 step 2) for (spin in doubleArrayOf(1500.0, 2600.0, 8000.0)) {
            val fairway = BounceRollModel.bounceAndRoll(landing(angle, spin), Surface.FAIRWAY_NORMAL, 12.0, env).deltaY
            val rough = BounceRollModel.bounceAndRoll(landing(angle, spin), Surface.ROUGH_NORMAL, 12.0, env).deltaY
            assertTrue("rough $rough > fairway $fairway at ${angle}deg/${spin.toInt()}rpm", rough <= fairway)
        }
    }
}
```

- [ ] **Step 2:** Run → verify all three FAIL with the documented symptoms (band ~10 m; jump at 16.6°; rough > fairway 16–20°).

### Task C3: Calibrate

**Files:**
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModel.kt` (:53-64 constants, :117-141 steep branch)
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/Surface.kt` (:88-95)

- [ ] **Step 1 (rough ordering):** set `ROUGH_NORMAL.thetaCritRad = 0.29` (same split as fairway — the split angle must not differ between turf types; rough's harshness stays in `rollDecelMps2 = 3.14` and `spinbackScale = 0.70`). Update the Surface.kt class-doc comment: dated 2026-10-07 recalibration note ("rough shares the fairway branch split; ordering invariant is enforced by tests, not by a higher thetaCrit").
- [ ] **Step 2 (driver band):** first pass — widen `STEEP_BLEND_SPAN_RAD` 0.35 → 0.55 (ramp spans ~31°; deep-steep tour irons are pinned by `STEEP_BLEND_CAP = 0.55` and stay saturating, so TrackMan 7i pins are unaffected). Re-run the sweep; iterate on these knobs ONLY, in this order: (a) span, (b) a low-spin relief on `angleBlend` inside the non-gate branch (e.g. scale `angleBlend` by `(rpmNow / 4500.0).coerceIn(0.35, 1.0)` — driver-class impacts keep more skid, wedge-class unchanged), (c) `STEEP_BLEND_CAP` (last resort; it is pin-adjacent). Document each deliberate change with a dated comment at the constant (frozen-M2-law comments must state recalibration is intentional, 2026-10-07, spec item 5).
- [ ] **Step 3:** `RolloutRecalibrationTest` → all PASS. Keep a sweep table in the test class KDoc (post-change) as the calibration record.
- [ ] **Step 4 (pin gate):** `.\gradlew.bat :core:physics:test` — ALL green (`TourAveragesTest`, `BounceRollModelTest`, `BounceRollSkidTest`, `SurfaceTest`, `RestPositionTest`, `TourOrderingTest`, app-side `RangeRolloutTest` via `:app:testDebugUnitTest`). **If any existing pin breaks after the first full calibration pass: STOP, record the failing pins + sweep table, and report back — do not thrash parameters.** (Orchestrator escalates to @oracle per spec.)
- [ ] **Step 5: Commit** `fix(physics): continuous bounce law across thetaCrit; rough ≤ fairway; driver rollout band (2026-10-07 recalibration)`.

---

## Lane D — Bag order logic + AppRoot wiring (AFTER Lane B lands)

### Task D1: Pure plan-order helper + test

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagPlanOrder.kt` (add helper below the enum)
- Create: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagPlanOrderTest.kt`

- [ ] **Step 1: Failing test:**

```kotlin
class BagPlanOrderTest {
    private val plan = listOf("DRIVER", "3W", "5i", "7i", "PW", "LW").map { BagPlanClub(it, ClubType.IRON) }

    @Test
    fun `wedge first reverses the bag-order plan`() {
        assertEquals(plan.reversed(), orderPlan(plan, BagOrderMode.WEDGE_FIRST))
    }

    @Test
    fun `random returns a permutation and is deterministic per seed`() {
        val a = orderPlan(plan, BagOrderMode.RANDOM, Random(42))
        val b = orderPlan(plan, BagOrderMode.RANDOM, Random(42))
        assertEquals(a, b)
        assertEquals(plan.map { it.name }.sorted(), a.map { it.name }.sorted())
    }

    @Test
    fun `random differs across seeds for realistic bags`() {
        assertTrue(orderPlan(plan, BagOrderMode.RANDOM, Random(1)) != orderPlan(plan, BagOrderMode.RANDOM, Random(2)))
    }
}
```

- [ ] **Step 2: Verify FAIL** → **Step 3: implement:**

```kotlin
/**
 * Orders the mapping plan BEFORE the session snapshot (spec item 2): the
 * ordered list is what gets persisted, so resume semantics are unchanged.
 */
fun <T> orderPlan(items: List<T>, mode: BagOrderMode, random: Random = Random.Default): List<T> = when (mode) {
    BagOrderMode.WEDGE_FIRST -> items.asReversed()
    BagOrderMode.RANDOM -> items.shuffled(random)
}
```

(`import kotlin.random.Random`.) Note the repo sort is long→short (`SessionRepository.kt:78`), so reversal = LW first, ascending to driver.
- [ ] **Step 4:** `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagPlanOrderTest"` → PASS. **Commit** `feat(bag): pure plan-order helper (wedge-first/random)`.

### Task D2: Thread the order mode through

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingScreen.kt` (:38 param, :91-96 call)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` (:575-582 `startBagTest`, :756 `onStartTest = ::startBagTest`)

- [ ] **Step 1:** `BagMappingScreen.onStartTest: () -> Unit` → `(BagOrderMode) -> Unit`; INTRO call becomes `{ onStartTest(it) }` (replaces Lane B's TODO).
- [ ] **Step 2:** `AppRoot.startBagTest` gains the mode and orders the eligible list BEFORE snapshotting (ordered `ClubRecord` list → `encodeClubSnapshot` persists the order; collector iterates the same ordered plan):

```kotlin
fun startBagTest(orderMode: BagOrderMode) {
    scope.launch {
        val eligible = clubRecords.filter { !it.isTemp && it.type != ClubType.PUTTER }
        if (eligible.isEmpty()) return@launch
        val ordered = orderPlan(eligible, orderMode)
        val id = sessionRepository.startBagMappingSession(ordered, System.currentTimeMillis()) ?: return@launch
        bagCollector.begin(id, ordered.map { BagPlanClub(it.name, it.type) })
    }
}
```

- [ ] **Step 3:** `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.*"` → PASS. **Commit** `feat(bag): wedge-first/random order wired end-to-end`.

### Task D3: Live bag shot on the range view

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingCollector.kt` (BagMappedShot :18-25, `add` :88-104)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` (routeShot :259-277, BAG render :743-759, `completeBagTest` :585-591)

- [ ] **Step 1:** `BagMappedShot` gains `val launch: LaunchConditions` and `val result: ShotResult`; `add` returns them (`BagMappedShot(club.name, club.type, clockMs(), ballData, launch, result, result.carryM, result.totalM)`). Check `app/src/test/.../BagMappingCollectorTest.kt` for `BagMappedShot`/`add` usages and update constructor calls; the existing simulation assertions stay green (collector still uses `UniformSurface(FAIRWAY_NORMAL)` — bag shots must NOT adopt range surfaces).
- [ ] **Step 2:** AppRoot holds `var bagDisplayShot by mutableStateOf<DisplayShot?>(null)`; in the bag branch of `routeShot`, after persisting, set `bagDisplayShot = DisplayShot(shot.ballData, mapped.launch, mapped.result)` (check exact `DisplayShot` field names in `app/.../range/DisplayShot.kt:12`); clear it in `completeBagTest`. This is display-only state — bag shots still never enter `session.shots`.
- [ ] **Step 3:** Pass `latestShot = bagDisplayShot` into `BagMappingScreen` (param exists with default from Lane B).
- [ ] **Step 4:** `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.*"` → PASS. **Commit** `feat(bag): live bag shots render on the range view tracer`.

---

## Integration (orchestrator)

- [ ] After A + B + C + D all land: `.\gradlew.bat build` (assemble + all tests) → green.
- [ ] Reconcile: no file owned by two lanes was edited concurrently; commit log linear.
- [ ] Report: summary of all five items + how to verify on tablet (HLA chip/column, intro selector, overlay, dual boxes, driver rollout).

## Spec coverage check
Items 1→A1-A3(+B1 chip), 2→B2/B3+D1/D2, 3→B4+D3, 4→B5, 5→C1-C3. Testing conventions per AGENTS.md. No schema changes anywhere.
