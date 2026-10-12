# Sim-Condition Combine Scoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace real-course strokes-gained pricing in combine scoring with a two-layer model: sim-native position pricing (Layer 1) plus fitted per-station shed→real SG conversion constants (Layer 2), calibrated so the 2026-10-12 session solves to the owner's anchors.

**Architecture:** New pure module `core/scoring/SimExpectedStrokes.kt` prices positions in shed terms (flat-green putt model, discounted real tables for fairway/rough, analytic start-value distributions). New `CombineConversions.kt` holds three fitted SG offsets applied per shot at the single seam `CombineResults.shotSgForBand`. Everything downstream (band solve, locked band, chips, DB, UI) unchanged. Spec: `docs/superpowers/specs/2026-10-12-sim-condition-combine-scoring-design.md`.

**Tech Stack:** Kotlin, JUnit4, JVM-pure `core/scoring` module; app-module tests for seam + gate. Gradle on Windows: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` first; flags BEFORE task names (`.\gradlew.bat --console=plain ...`); quiet configure ≠ hung (5 s–2 min).

**Calibration facts (from the fitting study, `rapsodo_compare.cjs`):** raw layer-1 station estimates for the 2026-10-12 session at seed knobs: 100 m −7.41, 150 m −5.20, driver +3.31, overall −3.94; κ_rough scan hits the 0.12 boundary; single-approach-constant constraint FAILED (raw station gap 2.21 vs anchor gap 4) → per-station constants are the primary path; driver conversion ≈ −0.08 SG/shot; emergent overall ≈ 4.3–4.8 vs gate 2.5±1 → fitting harness must iterate knobs (γ/κ_f/shedTightening) until overall ∈ [1.5, 3.5].

---

## File Structure

| File | Responsibility |
|---|---|
| Create: `core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokes.kt` | Layer 1: simEndValue (GREEN/FAIRWAY/ROUGH), simStartValue, outcomeGrid, simStrokesGained, simDrivingStrokesGained. Reuses `expectedStrokes`, `interpolate`, `DRIVER_HOLE_M` from `ExpectedStrokes.kt` (all already public/internal in same package — do NOT re-declare DRIVER_HOLE_M). |
| Create: `core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/CombineConversions.kt` | Layer 2: three public conversion vars (pinned to fitted vals in task 9) + `approachOffsetFor(startDistanceM)` linear interp/extrapolation through (100, c100) and (150, c150). |
| Create: `core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokesTest.kt` | Guards: monotone, band order, relief ≥ 0 (d ≥ 3.0), tap-in floor, prob sums, zero-sum. RELATIONAL assertions only (knobs are internal vars — no absolute-value tests except documented seed sanity in comments). |
| Create: `core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/CombineConversionsTest.kt` | Interp/extrapolation arithmetic; var set + @After restore. |
| Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineResults.kt` (lines 7–9 imports, 121–129 `shotSgForBand`) | Seam swap to sim SG + conversions; KDoc update. |
| Create: `app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineScoringGateTest.kt` | Permanent acceptance gate: 18 stored rows → anchors ±1.0, overall 2.5±1.0, lockedBand SCRATCH, WEDGES smoke. |
| Create: `app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineTestFixtures.kt` | Extracted `goldenFacts`/`driverDemoFacts` shared by CombineResultsTest + fitting harness (committed). |
| Modify: `app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineResultsTest.kt` | Use fixtures; rewrite 2 routing tests as equality assertions; update numerics from harness printout (task 9). |
| Modify: `docs/superpowers/specs/2026-10-12-sim-condition-combine-scoring-design.md` | Task 1 corrections (§3.1, §3.4, §4, §6) + task 12 tuning log. |
| Temporary (never committed): `app/src/test/kotlin/com/hpsmiles/golfsim/combine/ScratchCombineFitting.kt` | Fitting harness, created + run + deleted in task 9. |

---

### Task 0: Preflight baseline

**Files:** none.

- [ ] **Step 1: Verify clean baseline**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :core:scoring:test
.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombineResultsTest"
```

Expected: both PASS (current real-course scoring green). If red, STOP and report.

- [ ] **Step 2: Confirm repo clean**

```powershell
git status --short
```

Expected: empty (except untracked session CSV if present — leave it).

---

### Task 1: Spec corrections (fitting-study findings)

**Files:** Modify `docs/superpowers/specs/2026-10-12-sim-condition-combine-scoring-design.md`.

- [ ] **Step 1: Apply the six corrections** (find → replace):

1. §3.4 guard `Cross-lie ordering: GREEN ≤ FAIRWAY ≤ ROUGH at equal distance.` → `Per-lie monotone in distance; E_sim ≤ E_real (relief ≥ 0) for d ≥ 3.0 m (below 3.0 the flat-sim model may conservatively overprice tap-ins; the overcharge is absorbed by Layer 2); tap-in E(0.3) ∈ (1.0, 1.15]; no cross-lie ordering guard (chipping from tight fairway beats an equal-length putt on the real substrate).`
2. §3.4 `**Zero-sum:** ... (layer 1 only, pre-conversion).` stays; add sentence: `Zero-sum is exact by construction (E_start = 1 + Σ p·E_end).`
3. §3.1 ROUGH bullet: replace `The driver anchor pins this knob (§6): ... pins it near 0.15. (The 0.25–0.4 brainstorm range overshoots the gate — recorded here so the constraint survives.)` with `Seed range 0.12–0.20; the calibration scan settles at the 0.12 boundary and the driver anchor is absorbed by CONVERSION_DRIVER instead.`
4. §3.1 GREEN relief sentence `≈ 0.05–0.2 strokes there` → `grows with distance (long-range 3-putt removal); no upper bound is guarded — the honest-zone constraint is relief ≥ 0 for d ≥ 3.0 m`.
5. §4 bullet `CONVERSION_DRIVER` (driver expected ≈ 0 ...) → `fitted ≈ −0.08…−0.12 SG/shot (encodes the missing penalty stakes for offline drives)`. Replace the **Single-constant constraint** paragraph with: `**Per-station constants (empirical outcome):** the single APPROACH constant failed on the calibration data (raw station gap 2.21 HC vs anchor gap 4 HC), so the primary path is three fitted constants — CONVERSION_APPROACH_100M, CONVERSION_APPROACH_150M, CONVERSION_DRIVER. The WEDGES combine uses `approachOffsetFor(startDistanceM)`: linear interpolation between the two fitted approach stations, linear extrapolation outside [100, 150].`
6. §6 after the table, add: `Fitting procedure: per-combo knob grid (κ_rough, γ, κ_fairway, shedTightening within §3/§8 ranges), three 1-D bisects (one per station anchor), accept when emergent overall ∈ [1.5, 3.5]; report back if no combo passes.`

- [ ] **Step 2: Commit**

```powershell
git add docs/superpowers/specs/2026-10-12-sim-condition-combine-scoring-design.md
git commit -m "Spec: sim-scoring corrections from fitting study (per-station constants, guard fix)"
```

---

### Task 2: GREEN end value (TDD)

**Files:** Create `core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokesTest.kt`; Create `core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokes.kt`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimExpectedStrokesTest {

    private val bands = SkillBand.entries

    @Test
    fun `green end value is monotone in distance`() {
        for (band in bands) {
            var prev = 0.0
            var d = 0.3
            while (d <= 15.0 + 1e-9) {
                val e = SimExpectedStrokes.simEndValue(band, Lie.GREEN, d)
                assertTrue("non-monotone at d=$d band=$band", e >= prev - 1e-9)
                prev = e
                d += 0.1
            }
        }
    }

    @Test
    fun `green end value respects band ordering`() {
        for (d in listOf(0.3, 1.0, 2.0, 3.2, 5.49, 9.0, 15.0)) {
            for (i in 0 until bands.size - 1) {
                val better = SimExpectedStrokes.simEndValue(bands[i], Lie.GREEN, d)
                val worse = SimExpectedStrokes.simEndValue(bands[i + 1], Lie.GREEN, d)
                assertTrue("band order violated at d=$d", better <= worse + 1e-9)
            }
        }
    }

    @Test
    fun `green relief versus real table is positive at mid range`() {
        // Seed sanity (comments only — knobs are fitting vars):
        // E_scratch(5.49)=1.787, E_tour(5.49)=1.750, E_hcp18(5.49)=1.855, E_scratch(0.3)=1.086
        for (band in bands) {
            val relief = expectedStrokes(band, Lie.GREEN, 5.49) -
                SimExpectedStrokes.simEndValue(band, Lie.GREEN, 5.49)
            assertTrue("relief must be positive at 5.49 for $band", relief > 0.0)
        }
    }

    @Test
    fun `tap-in green value stays near 1`() {
        for (band in bands) {
            val e = SimExpectedStrokes.simEndValue(band, Lie.GREEN, 0.3)
            assertTrue("tap-in out of range for $band: $e", e > 1.0 && e <= 1.15)
        }
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat --console=plain :core:scoring:test` → compilation error: unresolved `SimExpectedStrokes`.

- [ ] **Step 3: Implement the GREEN slice**

```kotlin
package com.hpsmiles.golfsim.core.scoring

import kotlin.math.exp

/**
 * Layer 1 of the sim-condition scoring model: positions priced at their in-shed cost.
 * Real-course tables remain the substrate; bounded relief terms remove costs the shed
 * does not impose (break/speed variance, rough severity). Spec §3.
 */
object SimExpectedStrokes {

    // Fitting knobs — public vars so the app-module fitting harness can scan them; task 9 pins them as private consts.
    var GAMMA = 1.05
    var K_FAIRWAY = 0.10
    var K_ROUGH = 0.15
    var SHED_TIGHTENING = 0.25

    private const val K_SLOPE = 0.4
    private const val RAMP_TAPIN_M = 1.0

    private val D50 = mapOf(
        SkillBand.TOUR to 3.2,
        SkillBand.SCRATCH to 2.75,
        SkillBand.HCP10 to 2.2,
        SkillBand.HCP18 to 1.8,
    )

    fun simEndValue(band: SkillBand, lie: Lie, metres: Double): Double = when (lie) {
        Lie.GREEN -> greenEndValue(band, metres)
        Lie.FAIRWAY -> max(1.0, (1.0 - K_FAIRWAY) * expectedStrokes(band, Lie.FAIRWAY, metres))
        Lie.ROUGH -> max(1.0, (1.0 - K_ROUGH) * expectedStrokes(band, Lie.ROUGH, metres))
        Lie.TEE -> error("TEE is not priced by the sim model")
    }

    /**
     * Two-state flat-green putt model: E = 1 + (1 - p)·γ with p the one-putt probability
     * on a dead-flat, known-speed green (logistic in d, per-band inflection d50, tap-in ramp).
     */
    private fun greenEndValue(band: SkillBand, d: Double): Double {
        val d50 = D50.getValue(band)
        val pLogit = 1.0 / (1.0 + exp(K_SLOPE * (d - d50)))
        val ramp = min(1.0, d / RAMP_TAPIN_M)
        val p = 1.0 - (1.0 - pLogit) * ramp
        return 1.0 + (1.0 - p) * GAMMA
    }
}
```

Add imports `import kotlin.math.max` and `import kotlin.math.min` (both used; `max` lands with task 3's lines — include now to compile).

- [ ] **Step 4: Run, expect PASS** — same command; 4 tests green.
- [ ] **Step 5: Commit**

```powershell
git add core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokes.kt core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokesTest.kt
git commit -m "feat(scoring): sim GREEN end value (flat-green putt model)"
```

---

### Task 3: FAIRWAY/ROUGH end values + dispatch guard

**Files:** same as task 2.

- [ ] **Step 1: Add failing tests** (append to `SimExpectedStrokesTest`):

```kotlin
    @Test
    fun `fairway and rough relief is nonnegative from 3m`() {
        for (band in bands) {
            var d = 3.0
            while (d <= 60.0 + 1e-9) {
                for (lie in listOf(Lie.FAIRWAY, Lie.ROUGH)) {
                    val relief = expectedStrokes(band, lie, d) -
                        SimExpectedStrokes.simEndValue(band, lie, d)
                    assertTrue("negative relief at $lie d=$d band=$band", relief >= -1e-9)
                }
                d += 1.0
            }
        }
    }

    @Test
    fun `end values are monotone per lie and floored at 1`() {
        for (band in bands) {
            for (lie in listOf(Lie.FAIRWAY, Lie.ROUGH)) {
                var prev = 0.0
                var d = 3.0
                while (d <= 200.0 + 1e-9) {
                    val e = SimExpectedStrokes.simEndValue(band, lie, d)
                    assertTrue("non-monotone $lie at $d", e >= prev - 1e-9)
                    assertTrue("below floor $lie at $d", e >= 1.0 - 1e-9)
                    prev = e
                    d += 5.0
                }
            }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `tee is not priced`() {
        SimExpectedStrokes.simEndValue(SkillBand.SCRATCH, Lie.TEE, 100.0)
    }
```

- [ ] **Step 2: Run, expect FAIL** (`error(...)` on TEE test passes already; relief tests pass with current code — the failing assertion is none yet: this task's tests are guard-form; they should PASS immediately. If green, proceed — they pin the guards against future knob changes.) Run: `.\gradlew.bat --console=plain :core:scoring:test` → PASS.
- [ ] **Step 3: Commit**

```powershell
git add core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokesTest.kt
git commit -m "test(scoring): FAIRWAY/ROUGH relief + monotone + floor guards"
```

---

### Task 4: Start values + outcomeGrid (TDD)

**Files:** same as task 2.

- [ ] **Step 1: Failing tests** (append):

```kotlin
    @Test
    fun `outcome grid probabilities sum to one`() {
        for (band in bands) {
            for (start in SimExpectedStrokes.START_DISTANCES) {
                val total = SimExpectedStrokes.outcomeGrid(band, start).sumOf { it.probability }
                assertEquals("prob sum band=$band start=$start", 1.0, total, 1e-9)
            }
        }
    }

    @Test
    fun `start value is monotone in distance and band ordered`() {
        for (band in bands) {
            var prev = 0.0
            var d = 50.0
            while (d <= 150.0 + 1e-9) {
                val e = SimExpectedStrokes.simStartValue(band, d)
                assertTrue("start non-monotone at $d", e >= prev - 1e-9)
                prev = e
                d += 5.0
            }
        }
        for (d in listOf(50.0, 70.0, 90.0, 100.0, 150.0)) {
            for (i in 0 until bands.size - 1) {
                assertTrue(
                    "start band order at $d",
                    SimExpectedStrokes.simStartValue(bands[i], d) <=
                        SimExpectedStrokes.simStartValue(bands[i + 1], d) + 1e-9,
                )
            }
        }
    }
```

- [ ] **Step 2: Run, expect FAIL** — unresolved `outcomeGrid`/`simStartValue`/`START_DISTANCES`.
- [ ] **Step 3: Implement** — add to `SimExpectedStrokes` (seed tables per spec §3.2; rows aligned TOUR/SCRATCH/HCP10/HCP18 at distances [50, 70, 90, 100, 150]):

```kotlin
    val START_DISTANCES = doubleArrayOf(50.0, 70.0, 90.0, 100.0, 150.0)

    private val GIR = mapOf(
        SkillBand.TOUR to doubleArrayOf(0.98, 0.95, 0.95, 0.95, 0.85),
        SkillBand.SCRATCH to doubleArrayOf(0.95, 0.92, 0.89, 0.88, 0.70),
        SkillBand.HCP10 to doubleArrayOf(0.90, 0.84, 0.77, 0.75, 0.55),
        SkillBand.HCP18 to doubleArrayOf(0.80, 0.70, 0.62, 0.60, 0.40),
    )
    private val PROX_MEAN = mapOf(
        SkillBand.TOUR to doubleArrayOf(4.0, 4.8, 5.5, 5.8, 7.5),
        SkillBand.SCRATCH to doubleArrayOf(5.0, 6.0, 7.0, 7.5, 10.5),
        SkillBand.HCP10 to doubleArrayOf(6.5, 8.0, 9.5, 10.0, 13.5),
        SkillBand.HCP18 to doubleArrayOf(8.5, 10.5, 12.0, 12.5, 16.0),
    )
    private val PROX_SD = mapOf(
        SkillBand.TOUR to doubleArrayOf(5.0, 6.0, 6.5, 7.0, 8.5),
        SkillBand.SCRATCH to doubleArrayOf(6.0, 7.0, 7.5, 8.0, 9.5),
        SkillBand.HCP10 to doubleArrayOf(7.5, 8.5, 9.0, 9.0, 10.5),
        SkillBand.HCP18 to doubleArrayOf(9.0, 10.0, 10.5, 10.0, 11.5),
    )
    private val MISS_ROUGH_SHARE = mapOf(
        SkillBand.TOUR to 0.35, SkillBand.SCRATCH to 0.45,
        SkillBand.HCP10 to 0.55, SkillBand.HCP18 to 0.65,
    )

    // 9-point equal-weight normal quantiles (green rest nodes).
    private val GREEN_Z = doubleArrayOf(
        -1.5927, -0.9674, -0.5895, -0.2822, 0.0, 0.2822, 0.5895, 0.9674, 1.5927,
    )
    // Triangular miss band beyond the 15 m green edge: [16.0, 27.5] m, mode 18.0.
    private const val MISS_A = 16.0
    private const val MISS_C = 27.5
    private const val MISS_MODE = 18.0
    private const val GREEN_NODE_FLOOR_M = 0.3
    private const val GREEN_NODE_CEILING_M = 15.0

    data class Outcome(val lie: Lie, val metres: Double, val probability: Double)

    fun outcomeGrid(band: SkillBand, startM: Double): List<Outcome> {
        val g = interpolate(START_DISTANCES, GIR.getValue(band), startM)
        val mean = interpolate(START_DISTANCES, PROX_MEAN.getValue(band), startM)
        val sd = interpolate(START_DISTANCES, PROX_SD.getValue(band), startM) * (1.0 - SHED_TIGHTENING)
        val roughShare = MISS_ROUGH_SHARE.getValue(band)
        val nodes = ArrayList<Outcome>(14)
        for (z in GREEN_Z) {
            val x = (mean + z * sd).coerceIn(GREEN_NODE_FLOOR_M, GREEN_NODE_CEILING_M)
            nodes.add(Outcome(Lie.GREEN, x, g / GREEN_Z.size))
        }
        val missP = (1.0 - g) / 5.0
        for (i in 0 until 5) {
            val u = (i + 0.5) / 5.0
            val x = triangularQuantile(u)
            nodes.add(Outcome(Lie.FAIRWAY, x, missP * (1.0 - roughShare)))
            nodes.add(Outcome(Lie.ROUGH, x, missP * roughShare))
        }
        return nodes
    }

    private fun triangularQuantile(u: Double): Double {
        val modeSplit = (MISS_MODE - MISS_A) / (MISS_C - MISS_A)
        return if (u <= modeSplit) {
            MISS_A + sqrt(u * (MISS_C - MISS_A) * (MISS_MODE - MISS_A))
        } else {
            MISS_C - sqrt((1.0 - u) * (MISS_C - MISS_A) * (MISS_C - MISS_MODE))
        }
    }

    fun simStartValue(band: SkillBand, startM: Double): Double =
        1.0 + outcomeGrid(band, startM).sumOf { it.probability * simEndValue(band, it.lie, it.metres) }
```

Imports to add: `import kotlin.math.sqrt`, `import com.hpsmiles.golfsim.core.scoring.ExpectedStrokesKt.interpolate` — NOT needed: `interpolate` is internal in the same package, call it directly.

- [ ] **Step 4: Run, expect PASS.** `.\gradlew.bat --console=plain :core:scoring:test`
- [ ] **Step 5: Commit**

```powershell
git add core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokes.kt core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokesTest.kt
git commit -m "feat(scoring): sim start values + analytic outcome grid"
```

### Task 5: SG functions

**Files:** `SimExpectedStrokes.kt`, `SimExpectedStrokesTest.kt`.

- [ ] **Step 1: Failing tests** (append to `SimExpectedStrokesTest`):

```kotlin
    @Test
    fun `drive sg is zero at the scratch typical-drive reference`() {
        // DRIVER_HOLE_M 340 − SCRATCH typicalDriveM 239.6 = 100.4 (reuses public const from ExpectedStrokes.kt)
        assertEquals(
            0.0,
            SimExpectedStrokes.simDrivingStrokesGained(SkillBand.SCRATCH, Lie.FAIRWAY, 100.4),
            1e-9,
        )
    }

    @Test
    fun `drive sg punishes rough more than fairway and decreases with distance`() {
        for (band in bands) {
            var d = 50.0
            while (d <= 300.0 + 1e-9) {
                val fair = SimExpectedStrokes.simDrivingStrokesGained(band, Lie.FAIRWAY, d)
                val rough = SimExpectedStrokes.simDrivingStrokesGained(band, Lie.ROUGH, d)
                assertTrue("rough not punished at $d band=$band", rough <= fair + 1e-9)
                d += 10.0
            }
            var prev = Double.MAX_VALUE
            d = 50.0
            while (d <= 300.0 + 1e-9) {
                val sg = SimExpectedStrokes.simDrivingStrokesGained(band, Lie.FAIRWAY, d)
                assertTrue("drive sg not decreasing at $d", sg <= prev + 1e-9)
                prev = sg
                d += 10.0
            }
        }
    }
```

- [ ] **Step 2: Run, expect FAIL** — unresolved `simStrokesGained`/`simDrivingStrokesGained` (referenced by name in test; add the zero-reference test first so compile failure names the symbol).
- [ ] **Step 3: Implement** — add to `SimExpectedStrokes`:

```kotlin
    /**
     * Position-to-position SG for an approach: sim-priced start (FAIRWAY only — the app's
     * approach stations always start from the fairway) minus sim-priced finish, minus 1.
     */
    fun simStrokesGained(
        band: SkillBand,
        startLie: Lie,
        startM: Double,
        endLie: Lie,
        endM: Double,
    ): Double {
        require(startLie == Lie.FAIRWAY) { "sim approach model prices FAIRWAY starts only" }
        return simStartValue(band, startM) - simEndValue(band, endLie, endM) - 1.0
    }

    /**
     * Baseline-relative drive SG: sim cost where the band's typical drive leaves them vs
     * sim cost of this drive's rest position. Same shape as real-course drivingStrokesGained;
     * no −1 (baseline-relative). Rough relief on rests is the material term.
     */
    fun simDrivingStrokesGained(band: SkillBand, restLie: Lie, restDistanceM: Double): Double =
        simEndValue(band, Lie.FAIRWAY, DRIVER_HOLE_M - band.typicalDriveM) -
            simEndValue(band, restLie, restDistanceM)
```

- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit**

```powershell
git add core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokes.kt core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokesTest.kt
git commit -m "feat(scoring): simStrokesGained + simDrivingStrokesGained"
```

---

### Task 6: Zero-sum guard test

**Files:** `SimExpectedStrokesTest.kt`.

- [ ] **Step 1: Add test** (exact identity: E_start = 1 + Σ p·E_end ⇒ Σ p·SG = 0):

```kotlin
    @Test
    fun `zero sum - band golfer scores zero sg against own band`() {
        for (band in bands) {
            for (start in SimExpectedStrokes.START_DISTANCES) {
                val eStart = SimExpectedStrokes.simStartValue(band, start)
                val sum = SimExpectedStrokes.outcomeGrid(band, start).sumOf { o ->
                    o.probability *
                        (eStart - SimExpectedStrokes.simEndValue(band, o.lie, o.metres) - 1.0)
                }
                assertEquals("zero-sum band=$band start=$start", 0.0, sum, 1e-9)
            }
        }
    }
```

- [ ] **Step 2: Run, expect PASS** (identity holds by construction; the test pins it against future edits).
- [ ] **Step 3: Commit**

```powershell
git add core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokesTest.kt
git commit -m "test(scoring): zero-sum identity for sim model"
```

---

### Task 7: CombineConversions (Layer 2)

**Files:** Create `core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/CombineConversions.kt`; Create `core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/CombineConversionsTest.kt`.

- [ ] **Step 1: Failing test**

```kotlin
package com.hpsmiles.golfsim.core.scoring

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CombineConversionsTest {

    private val old100 = CombineConversions.APPROACH_100M
    private val old150 = CombineConversions.APPROACH_150M
    private val oldDriver = CombineConversions.DRIVER

    @Before
    fun snapshot() {
        // values captured in field initializers above; nothing else needed
    }

    @After
    fun restore() {
        CombineConversions.APPROACH_100M = old100
        CombineConversions.APPROACH_150M = old150
        CombineConversions.DRIVER = oldDriver
    }

    @Test
    fun `interpolates linearly between fitted stations`() {
        CombineConversions.APPROACH_100M = -0.24
        CombineConversions.APPROACH_150M = -0.10
        assertEquals(-0.24, CombineConversions.approachOffsetFor(100.0), 1e-9)
        assertEquals(-0.10, CombineConversions.approachOffsetFor(150.0), 1e-9)
        assertEquals(-0.17, CombineConversions.approachOffsetFor(125.0), 1e-9)
    }

    @Test
    fun `extrapolates linearly outside the fitted range`() {
        CombineConversions.APPROACH_100M = -0.24
        CombineConversions.APPROACH_150M = -0.10
        assertEquals(-0.38, CombineConversions.approachOffsetFor(50.0), 1e-9)
        assertEquals(-0.03, CombineConversions.approachOffsetFor(175.0), 1e-9)
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — unresolved `CombineConversions`.
- [ ] **Step 3: Implement**

```kotlin
package com.hpsmiles.golfsim.core.scoring

/**
 * Layer 2: fitted shed→real conversion offsets (SG per shot, per station kind).
 * Public vars exist for the fitting harness; task 9 pins these to fitted literals
 * (vars become vals) and the calibration record lives in the spec §8 tuning log. Spec §4.
 */
object CombineConversions {
    var APPROACH_100M = 0.0
    var APPROACH_150M = 0.0
    var DRIVER = 0.0

    /**
     * Approach conversion at a start distance: linear interpolation between the two fitted
     * stations (100 m, 150 m), linear extrapolation outside [100, 150] (WEDGES 50/70/90).
     */
    fun approachOffsetFor(startDistanceM: Double): Double =
        APPROACH_100M + (startDistanceM - 100.0) / 50.0 * (APPROACH_150M - APPROACH_100M)
}
```

Note: knobs on `SimExpectedStrokes` (GAMMA, K_FAIRWAY, K_ROUGH, SHED_TIGHTENING) must also be **public vars** (not `internal`) — the app-module fitting harness mutates them across the module boundary. Adjust the task 2 declarations accordingly when implementing (same KDoc note).

- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit**

```powershell
git add core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/CombineConversions.kt core/scoring/src/test/kotlin/com/hpsmiles/golfsim/core/scoring/CombineConversionsTest.kt
git commit -m "feat(scoring): per-station shed-to-real conversion constants"
```

### Task 8: Extract shared combine test fixtures

**Files:** Create `app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineTestFixtures.kt`; Modify `app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineResultsTest.kt`.

- [ ] **Step 1: Create the fixture file** (move the two inline fixture lists out of CombineResultsTest so the fitting harness can reuse them):

```kotlin
package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie

/** Shared shot fixtures for combine scoring tests and the fitting harness (spec §7). */
internal val goldenFacts: List<CombineShotFacts> = listOf(
    CombineShotFacts(0, Lie.GREEN, 1.0),
    CombineShotFacts(1, Lie.GREEN, 6.0),
    CombineShotFacts(6, Lie.GREEN, 7.0),
    CombineShotFacts(7, Lie.GREEN, 8.0),
    CombineShotFacts(12, Lie.GREEN, 10.0),
    CombineShotFacts(13, Lie.GREEN, 12.0),
)

internal val driverDemoFacts: List<CombineShotFacts> = listOf(
    CombineShotFacts(4, Lie.FAIRWAY, 74.48),
    CombineShotFacts(5, Lie.FAIRWAY, 138.55),
    CombineShotFacts(10, Lie.FAIRWAY, 184.30),
    CombineShotFacts(11, Lie.FAIRWAY, 205.57),
    CombineShotFacts(16, Lie.FAIRWAY, 102.32),
    CombineShotFacts(17, Lie.FAIRWAY, 169.54),
)
```

- [ ] **Step 2: Refactor CombineResultsTest** — replace the two inline fixture lists with `goldenFacts` / `driverDemoFacts` references (same values; no assertion changes). Pure refactor.
- [ ] **Step 3: Run, expect PASS** — `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombineResultsTest"`
- [ ] **Step 4: Commit**

```powershell
git add app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineTestFixtures.kt app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineResultsTest.kt
git commit -m "refactor(app): extract shared combine test fixtures"
```

---

### Task 9: Gate test + calibration fitting + seam swap (single green commit)

This task ships the whole model in one commit: the acceptance gate is written first (red evidence), the fitting harness derives the final knobs/constants, then the seam swaps and every numeric expectation updates from the harness printout. Committing red is never allowed, so everything lands together once green.

**Files:** Create `app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineScoringGateTest.kt`; Temporary (delete in step 7, never commit) `app/src/test/kotlin/com/hpsmiles/golfsim/combine/ScratchCombineFitting.kt`; Modify `core/scoring/.../CombineConversions.kt`, `core/scoring/.../SimExpectedStrokes.kt`, `app/.../combine/CombineResults.kt`, `app/.../combine/CombineResultsTest.kt`.

- [ ] **Step 1: Write the gate test** (acceptance gate per spec §6; `sessionFacts` is `internal` so the harness reuses it):

```kotlin
package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie
import com.hpsmiles.golfsim.core.scoring.SkillBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance gate (spec §6): stored 2026-10-12 session must solve to the owner's anchors. */
class CombineScoringGateTest {

    private val approach = CombineCatalog.byId("APPROACH")!!

    // Session 2026-10-12 20:24 — (shotIndex, restLie, restDistanceM) per shot, as stored.
    internal val sessionFacts = listOf(
        CombineShotFacts(0, Lie.GREEN, 5.7467795104),
        CombineShotFacts(1, Lie.GREEN, 2.5404337667),
        CombineShotFacts(2, Lie.GREEN, 1.1771882365),
        CombineShotFacts(3, Lie.GREEN, 4.8562422345),
        CombineShotFacts(4, Lie.FAIRWAY, 100.4606075935),
        CombineShotFacts(5, Lie.FAIRWAY, 104.4336680986),
        CombineShotFacts(6, Lie.GREEN, 6.8259315358),
        CombineShotFacts(7, Lie.GREEN, 5.8023789631),
        CombineShotFacts(8, Lie.GREEN, 12.7218677946),
        CombineShotFacts(9, Lie.GREEN, 6.9508719257),
        CombineShotFacts(10, Lie.ROUGH, 111.7846085790),
        CombineShotFacts(11, Lie.FAIRWAY, 99.8080235322),
        CombineShotFacts(12, Lie.GREEN, 8.7002514811),
        CombineShotFacts(13, Lie.GREEN, 2.8138289139),
        CombineShotFacts(14, Lie.GREEN, 5.7762255659),
        CombineShotFacts(15, Lie.GREEN, 9.0652470737),
        CombineShotFacts(16, Lie.ROUGH, 111.2628087007),
        CombineShotFacts(17, Lie.FAIRWAY, 94.7890870845),
    )

    @Test
    fun `calibration session solves to declared anchors`() {
        val results = combineResults(approach, sessionFacts)
        assertEquals(3.0, results.stations[0].estimate.est, 1.0)  // 100 m
        assertEquals(-1.0, results.stations[1].estimate.est, 1.0) // 150 m
        assertEquals(8.0, results.stations[2].estimate.est, 1.0)  // DRIVER
    }

    @Test
    fun `calibration session overall is emergent 2_5`() {
        assertEquals(2.5, combineResults(approach, sessionFacts).overall.est, 1.0)
    }

    @Test
    fun `calibration session locks to scratch`() {
        assertEquals(SkillBand.SCRATCH, combineResults(approach, sessionFacts).lockedBand)
    }

    @Test
    fun `wedges combine still produces finite results`() {
        val wedges = CombineCatalog.byId("WEDGES")!!
        val facts = (0 until 18).map { i ->
            CombineShotFacts(i, if (i % 2 == 0) Lie.GREEN else Lie.FAIRWAY, if (i % 2 == 0) 4.0 else 20.0)
        }
        val results = combineResults(wedges, facts)
        assertEquals(3, results.stations.size)
        results.stations.forEach { assertTrue(it.estimate.est.isFinite()) }
        assertTrue(results.overall.est.isFinite())
    }
}
```

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombineScoringGateTest"` → **Expected: FAIL** (real-course tables still rate the session −5.8) — this is the red evidence; do NOT commit.

- [ ] **Step 2: Create the fitting harness** (never committed). It computes SG from the **sim functions directly** (the seam still routes real tables — do not call `shotSgForBand` here):

```kotlin
package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.CombineConversions
import com.hpsmiles.golfsim.core.scoring.SimExpectedStrokes
import com.hpsmiles.golfsim.core.scoring.SkillBand
import com.hpsmiles.golfsim.core.scoring.handicapMatchSg
import org.junit.Test

/** FITTING HARNESS — run once, copy printed values into code/spec, then DELETE. Never commit. */
class ScratchCombineFitting {

    private val approach = CombineCatalog.byId("APPROACH")!!
    private val facts get() = CombineScoringGateTest().sessionFacts

    private fun sgMap(fact: CombineShotFacts): Map<SkillBand, Double> {
        val station = approach.stations[CombinePlan.stationIndexFor(fact.shotIndex)]
        return SkillBand.entries.associateWith { band ->
            if (station.kind == StationKind.DRIVER) {
                simDrivingStrokesGained(band, fact.restLie, fact.restDistanceM) + CombineConversions.DRIVER
            } else {
                val input = CombinePlan.shotInput(approach, fact.shotIndex, fact.restLie, fact.restDistanceM)
                simStrokesGained(band, input.startLie, input.startM, fact.restLie, fact.restDistanceM) +
                    CombineConversions.approachOffsetFor(station.startDistanceM)
            }
        }
    }

    private fun solve(est: List<Map<SkillBand, Double>>) = handicapMatchSg(est)

    private fun stationEst(station: Int) =
        solve(facts.filter { CombinePlan.stationIndexFor(it.shotIndex) == station }.map { sgMap(it) }).est

    private fun overallEst() = solve(facts.map { sgMap(it) }).est

    /** est is monotonically DECREASING in the offset c (more negative c ⇒ higher est). */
    private fun bisect(target: Double, set: (Double) -> Unit, est: () -> Double): Double {
        var lo = -3.0
        var hi = 0.0
        set(lo)
        var fLo = est() - target
        while (fLo <= 0.0) { lo *= 2.0; set(lo); fLo = est() - target }
        for (i in 0 until 40) {
            val mid = (lo + hi) / 2.0
            set(mid)
            if (est() - target > 0.0) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }

    @Test
    fun fit() {
        for (kr in listOf(0.12, 0.15, 0.18, 0.20))
            for (gamma in listOf(1.05, 1.0))
                for (kf in listOf(0.10, 0.05))
                    for (st in listOf(0.25, 0.20, 0.30)) {
                        SimExpectedStrokes.K_ROUGH = kr
                        SimExpectedStrokes.GAMMA = gamma
                        SimExpectedStrokes.K_FAIRWAY = kf
                        SimExpectedStrokes.SHED_TIGHTENING = st
                        CombineConversions.APPROACH_100M = 0.0
                        CombineConversions.APPROACH_150M = 0.0
                        CombineConversions.DRIVER = 0.0
                        val c100 = bisect(3.0, { CombineConversions.APPROACH_100M = it }) { stationEst(0) }
                        CombineConversions.APPROACH_100M = c100
                        val c150 = bisect(-1.0, { CombineConversions.APPROACH_150M = it }) { stationEst(1) }
                        CombineConversions.APPROACH_150M = c150
                        val cd = bisect(8.0, { CombineConversions.DRIVER = it }) { stationEst(2) }
                        CombineConversions.DRIVER = cd
                        val overall = overallEst()
                        val ok = overall in 1.5..3.5
                        println(
                            "kr=$kr g=$gamma kf=$kf st=$st c100=%.6f c150=%.6f cd=%.6f e100=%.3f e150=%.3f ed=%.3f overall=%.3f %s"
                                .format(c100, c150, cd, stationEst(0), stationEst(1), stationEst(2), overall, if (ok) "PASS" else "")
                        )
                    }
        printFixtures()
    }

    private fun printFixtures() {
        val golden = solve(goldenFacts.map { sgMap(it) })
        println("GOLDEN est=%.4f clampedLow=%b chips=%s locked=%s".format(
            golden.est, golden.clampedLow,
            goldenFacts.map { CombineFormats.chip(solve(listOf(sgMap(it))).est) },
            SkillBand.nearestTo(golden.est),
        ))
        val driver = solve(driverDemoFacts.map { sgMap(it) })
        println("DRIVERDEMO est=%.4f chips=%s locked=%s".format(
            driver.est,
            driverDemoFacts.map { CombineFormats.chip(solve(listOf(sgMap(it))).est) },
            SkillBand.nearestTo(driver.est),
        ))
        val scratchDriver = handicapMatchSg(
            (0 until 6).map { i ->
                val fact = CombineShotFacts(listOf(4, 5, 10, 11, 16, 17)[i],
                    com.hpsmiles.golfsim.core.scoring.Lie.FAIRWAY, 100.4)
                SkillBand.entries.associateWith { simDrivingStrokesGained(it, fact.restLie, fact.restDistanceM) + CombineConversions.DRIVER }
            }
        )
        println("SCRATCHDRIVER est=%.4f locked=%s".format(scratchDriver.est, SkillBand.nearestTo(scratchDriver.est)))
    }
}
```

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.ScratchCombineFitting"`. From the printed lines pick the PASS combo with the best margin (overall closest to 2.5); copy its kr/gamma/kf/st, c100/c150/cd (6 dp), and the three FIXTURES lines. If **no** combo prints PASS, widen per spec §6 ranges (γ ∈ [1.0, 1.1], κ_f ∈ [0.05, 0.15], st ∈ [0.15, 0.35]) and rerun; if still none, STOP and report (gate unmet — do not ship).

- [ ] **Step 3: Pin constants** — in `CombineConversions.kt` replace the three `var` seeds with `val` fitted literals from the printout, e.g.:

```kotlin
object CombineConversions {
    val APPROACH_100M = -0.243123   // ← paste printed value (6 dp)
    val APPROACH_150M = -0.101122   // ← paste printed value
    val DRIVER = -0.080455          // ← paste printed value

    fun approachOffsetFor(startDistanceM: Double): Double =
        APPROACH_100M + (startDistanceM - 100.0) / 50.0 * (APPROACH_150M - APPROACH_100M)
}
```

In `SimExpectedStrokes.kt` pin the four knobs to the winning literals (`private const val GAMMA = …`, `K_FAIRWAY`, `K_ROUGH`, `SHED_TIGHTENING`).

- [ ] **Step 4: Seam swap** — in `CombineResults.kt`:

Imports (lines 7–9): drop `drivingStrokesGained` and `strokesGained`, add `simDrivingStrokesGained` and `simStrokesGained` (keep `handicapMatchSg`).

```kotlin
/**
 * SG for one combine shot against a single [band]: DRIVER-station shots are rated against the
 * band's typical drive in sim terms ([simDrivingStrokesGained]) plus the fitted driver
 * conversion; every other station uses the sim model from its [CombinePlan.shotInput] start
 * plus the fitted per-station approach conversion. Spec §3–§5.
 */
fun shotSgForBand(entry: CombineCatalogEntry, fact: CombineShotFacts, band: SkillBand): Double {
    val station = entry.stations[CombinePlan.stationIndexFor(fact.shotIndex)]
    return if (station.kind == StationKind.DRIVER) {
        simDrivingStrokesGained(band, fact.restLie, fact.restDistanceM) + CombineConversions.DRIVER
    } else {
        val input = CombinePlan.shotInput(entry, fact.shotIndex, fact.restLie, fact.restDistanceM)
        simStrokesGained(band, input.startLie, input.startM, input.endLie, input.endM) +
            CombineConversions.approachOffsetFor(station.startDistanceM)
    }
}
```

- [ ] **Step 5: Update CombineResultsTest** — (a) rewrite the two routing tests as constant-independent equality assertions:

```kotlin
    @Test
    fun `shotSgForBand routes approach shots through the sim model plus approach conversion`() {
        val fact = CombineShotFacts(0, Lie.GREEN, 1.0)
        val input = CombinePlan.shotInput(approach, 0, fact.restLie, fact.restDistanceM)
        val expected = simStrokesGained(
            SkillBand.SCRATCH, input.startLie, input.startM, fact.restLie, fact.restDistanceM,
        ) + CombineConversions.approachOffsetFor(100.0)
        assertEquals(expected, shotSgForBand(approach, fact, SkillBand.SCRATCH), 1e-9)
    }

    @Test
    fun `shotSgForBand routes driver shots through the sim drive model plus driver conversion`() {
        val fact = CombineShotFacts(4, Lie.FAIRWAY, 41.23)
        val expected = simDrivingStrokesGained(SkillBand.HCP18, fact.restLie, fact.restDistanceM) +
            CombineConversions.DRIVER
        assertEquals(expected, shotSgForBand(approach, fact, SkillBand.HCP18), 1e-9)
    }
```

(b) paste the printed numerics: golden `overall.est` (±0.05), `clampedLow`, chips list; driver-demo `est` (±0.1), chips; scratch-driver `est` (±0.01, line ~141). The scratch-driver `lockedBand == SCRATCH` assertion stays (printed locked is still SCRATCH — verify from the printout; if not, update both lines and note why). Imports for the test file: add `simStrokesGained`, `simDrivingStrokesGained`, `CombineConversions`; drop the real-table imports if now unused.

- [ ] **Step 6: Verify green** — run all three:

```powershell
.\gradlew.bat --console=plain :core:scoring:test
.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombineScoringGateTest"
.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.combine.CombineResultsTest"
```

Expected: all PASS. If the gate's overall test fails (no PASS combo existed), STOP — report to the user before pinning anything.

- [ ] **Step 7: Delete the harness**

```powershell
Remove-Item app\src\test\kotlin\com\hpsmiles\golfsim\combine\ScratchCombineFitting.kt
git status --short   # harness must NOT appear
```

- [ ] **Step 8: Commit**

```powershell
git add core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/CombineConversions.kt core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/SimExpectedStrokes.kt app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineResults.kt app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineScoringGateTest.kt app/src/test/kotlin/com/hpsmiles/golfsim/combine/CombineResultsTest.kt
git commit -m "feat(combine): sim-condition scoring — seam swap, fitted conversions, acceptance gate"
```

---

### Task 10: Spec tuning log + full build

**Files:** Modify `docs/superpowers/specs/2026-10-12-sim-condition-combine-scoring-design.md`.

- [ ] **Step 1: Fill §8 tuning log** — Final column for κ_fairway, κ_rough, γ, slope k, shedTightening, d50 row (unchanged seeds), and add rows for `CONVERSION_APPROACH_100M`, `CONVERSION_APPROACH_150M`, `CONVERSION_DRIVER` with the fitted values from task 9, plus a one-line note of the winning combo and the emergent overall value.
- [ ] **Step 2: Full build** — `.\gradlew.bat build` → expected: BUILD SUCCESSFUL (assemble + all module tests).
- [ ] **Step 3: Commit**

```powershell
git add docs/superpowers/specs/2026-10-12-sim-condition-combine-scoring-design.md
git commit -m "docs(spec): sim scoring tuning log — fitted constants and final knobs"
```

---

## Self-Review (completed at plan-writing time)

- **Spec coverage:** §3.1 → tasks 2–3; §3.2 → task 4; §3.3 → task 5; §3.4 guards → tasks 2, 3, 4, 6; §4 constants + WEDGES rule → tasks 7, 9; §5 seam → task 9; §6 gate + fitting procedure → task 9; §7 tests → tasks 2–9; §8 log → task 10. No gaps.
- **Placeholders:** none — task 9's "paste printed value" steps are an explicit dataflow (values do not exist until the harness runs in step 2 of the same task).
- **Type consistency:** `Outcome(lie, metres, probability)` used consistently; `approachOffsetFor(startDistanceM)` matches seam call; `DRIVER_HOLE_M` reused from `ExpectedStrokes.kt` (public const) — no re-declaration; knobs are public vars until task 9 pins them as private consts / vals.


