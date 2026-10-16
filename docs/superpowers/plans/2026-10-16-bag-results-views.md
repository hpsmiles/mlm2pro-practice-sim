# Bag Mapping Results — SIDE & TOP-DOWN Views Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the BAG MAPPING RESULT page's single long box-plot list with two new charts — a SIDE view of every club's real simulated flight arc on one shared distance axis, and a TOP-DOWN dispersion view — both driven by a CARRY | TOTAL toggle, with the existing box-plot matrix and gap block demoted into a collapsed panel.

**Architecture:** All data preparation and geometry is pure Kotlin in the `bag` package (JVM unit tested, no Compose, no Android framework). Compose files are thin canvases and layouts over that data, following the existing bag/games pattern — plain `remember` state, no ViewModels, no Navigation-Compose. Nothing new is persisted: no Room change, no migration. The design rationale, the settled decisions and the edge-case table live in `docs/superpowers/specs/2026-10-16-bag-results-views-design.md` — read it before starting.

**Tech Stack:** Kotlin, Jetpack Compose (canvas + layout), Room (read-only here), JUnit4, Gradle.

**Environment:** Windows. Set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before every Gradle command. Gradle flags precede task names. Tablet is `TB373FU`; `adb` is at `C:\Users\smiles\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

---

## Frozen interfaces

These signatures are fixed. Every task below uses them exactly; do not rename or re-shape them.

```kotlin
// bag/BagResultsView.kt
enum class BagResultsView { SIDE, TOP_DOWN }
enum class DistanceBasis { CARRY, TOTAL }
fun BagMappingShotEntity.distanceFor(basis: DistanceBasis): Double

// bag/BagRamp.kt
object BagRamp {
    val hues: List<Color>
    val hueCount: Int
    fun clubColor(index: Int): Color
}

// bag/BagTrace.kt
data class TracePoint(val downrangeM: Double, val heightM: Double)
data class ClubTrace(val clubName: String, val points: List<TracePoint>, val distanceM: Double, val apexM: Double)
data class ClubDots(val clubName: String, val points: List<Pair<Double, Double>>) // (lateralM, distanceM)
object BagTrace {
    fun launchFor(shot: BagMappingShotEntity): LaunchConditions?
    fun medianLaunch(shots: List<BagMappingShotEntity>): LaunchConditions?
    fun medianDistance(shots: List<BagMappingShotEntity>, basis: DistanceBasis): Double?
    fun trace(clubName: String, shots: List<BagMappingShotEntity>, basis: DistanceBasis): ClubTrace?
    fun dots(clubName: String, shots: List<BagMappingShotEntity>, basis: DistanceBasis): ClubDots
}

// bag/BagSideGeom.kt
data class SideFit(val maxDistanceM: Double, val pxPerMM: Double, val originX: Double,
                   val baselineY: Double, val pxPerMHeight: Double) {
    fun x(distanceM: Double): Float
    fun y(heightM: Double): Float
}
const val SIDE_HEIGHT_FRACTION = 0.85
fun computeSideFit(widthPx: Double, heightPx: Double, maxDistanceM: Double, maxApexM: Double,
                   leftPadPx: Double = 44.0, rightPadPx: Double = 16.0, bottomPadPx: Double = 18.0): SideFit
fun labelRow(index: Int): Int

// bag/BagSummary.kt
data class GapSummary(val averageGapM: Double?, val closest: GapEntry?, val farthest: GapEntry?,
                      val goodCount: Int, val wideCount: Int, val tightCount: Int)
fun summarizeGaps(gaps: List<GapEntry>): GapSummary

// bag/BagSideView.kt
@Composable fun BagSideView(traces: List<ClubTrace>, colors: Map<String, Color>,
                            onClubTap: (String) -> Unit, modifier: Modifier = Modifier)

// bag/BagTopDownView.kt
@Composable fun BagTopDownView(clubs: List<ClubDots>, colors: Map<String, Color>,
                               onClubTap: (String) -> Unit, modifier: Modifier = Modifier)

// bag/BagResultsSummary.kt
@Composable fun BagResultsSummary(summary: GapSummary, basis: DistanceBasis, modifier: Modifier = Modifier)

// bag/BagDistPanel.kt
@Composable fun BagDistPanel(open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier,
                             content: @Composable () -> Unit)
```

**Existing APIs to reuse (do not reinvent):** `BallFlightEngine.simulate(launch, environment, surfaces)`, `RangeRollout.samples(shot)`, `DispersionEllipse.fromKept(points)` (+ `areaM2()`, `projectedHalfSideM()`, `projectedHalfTotalM()`, `BUFFER_FRACTION`), `TopDownFitH` / `computeTopDownFitH(...)`, `BoxPlotGeom.axis/niceTicks/x`, `BagMappingStats.median/distribution`, `GapAnalysis` (`bandLowM`, `bandHighM`, `flagFor`, `analyze`, `orderedByBag`, `analyzeByClubIdentity`, `SPECIALIST_TYPES`, `BAND_LOW_FRACTION`, `BAND_HIGH_FRACTION`), `GapEntry(longerClub, shorterClub, gapM, flag, bandLowM, bandHighM)`, `GapFlag { TIGHT, HEALTHY, WIDE, INVERTED }`, `BagMappingFormats`, `GolfColors`, `GolfTypography`, `GolfSpacing`, `RangeScene`.

**Files touched by this plan:**

| File | Action |
|---|---|
| `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagResultsView.kt` | create |
| `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagRamp.kt` | create |
| `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTrace.kt` | create |
| `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagSideGeom.kt` | create |
| `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagSummary.kt` | create |
| `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagSideView.kt` | create |
| `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTopDownGeom.kt` | create |
| `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTopDownView.kt` | create |
| `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagResultsSummary.kt` | create |
| `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagDistPanel.kt` | create |
| `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingResult.kt` | modify (page composition; `CarryMatrixSection` signature) |
| `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagResultsViewTest.kt` | create |
| `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagRampTest.kt` | create |
| `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagTraceTest.kt` | create |
| `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagSideGeomTest.kt` | create |
| `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagSummaryTest.kt` | create |

Nothing else. In particular do **not** touch `GapAnalysis.kt`, `GapAnalysisTest.kt`, `BoxPlotGeom.kt`, `CarryMatrixCanvas.kt`, `BagMappingStats.kt`, `FittingColors.kt`, `CombineTopDownView.kt`, or any Room entity/DAO/schema.

---

## Task 1: Distance basis enum and the per-shot cache accessor

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagResultsView.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagResultsViewTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BagResultsViewTest {

    private fun shot(carryM: Double, totalM: Double) = BagMappingShotEntity(
        sessionId = 1L,
        clubName = "7i",
        clubType = "IRON",
        timestampMs = 0L,
        clubHeadSpeedMps = 35.0,
        ballSpeedMps = 45.0,
        launchAngleDeg = 20.0,
        launchDirDeg = 0.0,
        spinAxisDeg = 0.0,
        totalSpinRpm = 6000,
        carryM = carryM,
        totalM = totalM,
    )

    @Test
    fun `distanceFor selects the persisted cache for the basis`() {
        val s = shot(carryM = 140.0, totalM = 152.0)
        assertEquals(140.0, s.distanceFor(DistanceBasis.CARRY), 1e-9)
        assertEquals(152.0, s.distanceFor(DistanceBasis.TOTAL), 1e-9)
    }

    @Test
    fun `view enum has side as the first entry`() {
        assertEquals(BagResultsView.SIDE, BagResultsView.entries.first())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagResultsViewTest"`
Expected: FAIL — `Unresolved reference: DistanceBasis`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity

/** Which chart the results page leads with (2026-10-16 redesign). */
enum class BagResultsView { SIDE, TOP_DOWN }

/**
 * Which distance the new views and the gap analysis use. CARRY is the gapping
 * truth (total varies with ground conditions), so it is the default; TOTAL runs
 * the ball through the rollout and is what the user picks when they want to
 * see the whole shot.
 */
enum class DistanceBasis { CARRY, TOTAL }

/**
 * The persisted distance cache for this shot under [basis]. Every number on the
 * page — arcs, dots, labels, the summary strip and the gap block — comes from
 * here, never from a fresh simulation, so the page can never disagree with
 * itself.
 */
fun BagMappingShotEntity.distanceFor(basis: DistanceBasis): Double = when (basis) {
    DistanceBasis.CARRY -> carryM
    DistanceBasis.TOTAL -> totalM
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagResultsViewTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagResultsView.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagResultsViewTest.kt
git commit -m "feat(bag): distance basis enum + per-shot cache accessor"
```

---

## Task 2: The 13-hue club ramp

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagRamp.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagRampTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.designsystem.GolfColors
import org.junit.Assert.assertEquals
import org.junit.Test

class BagRampTest {

    @Test
    fun `thirteen distinct hues`() {
        assertEquals(13, BagRamp.hueCount)
        assertEquals(BagRamp.hueCount, BagRamp.hues.toSet().size)
    }

    @Test
    fun `first six are the app comparison colours`() {
        assertEquals(
            listOf(
                GolfColors.Comparison.A, GolfColors.Comparison.B, GolfColors.Comparison.C,
                GolfColors.Comparison.D, GolfColors.Comparison.E, GolfColors.Comparison.F,
            ),
            BagRamp.hues.take(6),
        )
    }

    @Test
    fun `assignment is positional by bag order and cycles past the end`() {
        assertEquals(BagRamp.hues[0], BagRamp.clubColor(0))
        assertEquals(BagRamp.hues[12], BagRamp.clubColor(12))
        assertEquals(BagRamp.hues[0], BagRamp.clubColor(13))
        assertEquals(BagRamp.hues[12], BagRamp.clubColor(-1))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagRampTest"`
Expected: FAIL — `Unresolved reference: BagRamp`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.ui.graphics.Color
import com.hpsmiles.golfsim.core.designsystem.GolfColors

/**
 * One distinct hue per club, assigned positionally by bag order — the same rule
 * as FittingColors.clubColor, so the arcs, the landing labels, the top-down
 * legend and the rings always agree with each other.
 *
 * The first six are GolfColors.Comparison.A..F so a short bag looks like every
 * other comparison view in the app; seven more follow so a full bag (up to 13
 * clubs — putters and TEST clubs are excluded from mapping) never repeats.
 * Beyond 13 the ramp cycles, which is documented and not expected.
 *
 * Known, accepted collision: hue 0 is Comparison.A, which is also
 * GolfColors.Teal — the "good" gap verdict colour. The ramp appears in the
 * chart and the top-down legend; the verdict colours appear in the summary
 * strip and the panel's gap block, so they never sit in the same region.
 */
object BagRamp {

    val hues: List<Color> = listOf(
        GolfColors.Comparison.A, // teal
        GolfColors.Comparison.B, // rose
        GolfColors.Comparison.C, // yellow
        GolfColors.Comparison.D, // indigo
        GolfColors.Comparison.E, // magenta
        GolfColors.Comparison.F, // green
        Color(0xFFE4572E),       // orange
        Color(0xFF5B9DF9),       // sky
        Color(0xFF8FBF4A),       // olive
        Color(0xFFC77DFF),       // violet
        Color(0xFF57D6C4),       // aqua
        Color(0xFFFFB86B),       // apricot
        Color(0xFFB0BEC5),       // slate
    )

    val hueCount: Int get() = hues.size

    fun clubColor(index: Int): Color = hues[index.mod(hues.size)]
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagRampTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagRamp.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagRampTest.kt
git commit -m "feat(bag): 13-hue club colour ramp for the results views"
```

---

## Task 3: Launch conditions and median distance from a club's kept shots

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTrace.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagTraceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BagTraceTest {

    private fun shot(
        ballSpeedMps: Double = 45.0,
        launchAngleDeg: Double = 20.0,
        launchDirDeg: Double = 0.0,
        spinAxisDeg: Double = 0.0,
        totalSpinRpm: Int = 6000,
        carryM: Double = 140.0,
        totalM: Double = 152.0,
        included: Boolean = true,
    ) = BagMappingShotEntity(
        sessionId = 1L,
        clubName = "7i",
        clubType = "IRON",
        timestampMs = 0L,
        clubHeadSpeedMps = 35.0,
        ballSpeedMps = ballSpeedMps,
        launchAngleDeg = launchAngleDeg,
        launchDirDeg = launchDirDeg,
        spinAxisDeg = spinAxisDeg,
        totalSpinRpm = totalSpinRpm,
        carryM = carryM,
        totalM = totalM,
        includeOverride = if (included) null else false,
    )

    @Test
    fun `launchFor builds conditions from one shot`() {
        val launch = BagTrace.launchFor(shot(ballSpeedMps = 44.0, totalSpinRpm = 5500))
        assertNotNull(launch)
        assertEquals(44.0, launch!!.ballSpeedMps, 1e-9)
        assertEquals(5500, launch.spinRpm)
    }

    @Test
    fun `medianLaunch takes the median of each metric over kept shots only`() {
        val shots = listOf(
            shot(ballSpeedMps = 40.0, totalSpinRpm = 5000),
            shot(ballSpeedMps = 45.0, totalSpinRpm = 6000),
            shot(ballSpeedMps = 50.0, totalSpinRpm = 7000),
            shot(ballSpeedMps = 99.0, totalSpinRpm = 9000, included = false),
        )
        val launch = BagTrace.medianLaunch(shots)
        assertNotNull(launch)
        assertEquals(45.0, launch!!.ballSpeedMps, 1e-9)
        assertEquals(6000, launch.spinRpm)
    }

    @Test
    fun `medianLaunch is null when no shots are kept`() {
        assertNull(BagTrace.medianLaunch(listOf(shot(included = false))))
        assertNull(BagTrace.medianLaunch(emptyList()))
    }

    @Test
    fun `medianLaunch is null when a median falls outside the engine's range`() {
        // launch angle 90 deg cannot build LaunchConditions (valid 0..85).
        assertNull(BagTrace.medianLaunch(listOf(shot(launchAngleDeg = 90.0))))
    }

    @Test
    fun `medianDistance follows the basis and ignores excluded shots`() {
        val shots = listOf(
            shot(carryM = 130.0, totalM = 141.0),
            shot(carryM = 140.0, totalM = 152.0),
            shot(carryM = 150.0, totalM = 163.0),
            shot(carryM = 300.0, totalM = 320.0, included = false),
        )
        assertEquals(140.0, BagTrace.medianDistance(shots, DistanceBasis.CARRY)!!, 1e-9)
        assertEquals(152.0, BagTrace.medianDistance(shots, DistanceBasis.TOTAL)!!, 1e-9)
    }

    @Test
    fun `medianDistance is null with no kept shots`() {
        assertNull(BagTrace.medianDistance(emptyList(), DistanceBasis.CARRY))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagTraceTest"`
Expected: FAIL — `Unresolved reference: BagTrace`.

- [ ] **Step 3: Write the implementation**

Create `BagTrace.kt` with the data classes from the Frozen interfaces section and these three functions. Leave `trace` and `dots` for Tasks 4 and 5 — but declare them now with a temporary body so the file compiles:

```kotlin
package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import kotlin.math.hypot
import kotlin.math.roundToInt

/** One point of a club's drawn flight, in the side view's 2-D frame. */
data class TracePoint(val downrangeM: Double, val heightM: Double)

/** A club's drawn flight: the median trace, scaled to its authoritative distance. */
data class ClubTrace(
    val clubName: String,
    val points: List<TracePoint>,
    /** The authoritative median distance for the current basis (metres). */
    val distanceM: Double,
    /** Apex of the drawn (scaled) trace, metres. */
    val apexM: Double,
)

/** One club's kept-shot landing points in the top-down frame. */
data class ClubDots(
    val clubName: String,
    /** (lateralM, distanceM) per kept shot. */
    val points: List<Pair<Double, Double>>,
)

object BagTrace {

    /**
     * Launch conditions for a single stored shot, or null when its raw block
     * cannot build valid conditions (legacy or tampered rows). Mirrors the
     * range-checked construction the engine requires; validated explicitly
     * rather than by catching, so the failure mode is a skip, never a throw.
     */
    fun launchFor(shot: BagMappingShotEntity): LaunchConditions? {
        val spinRpm = shot.totalSpinRpm
        if (shot.ballSpeedMps !in 0.5..100.0) return null
        if (spinRpm !in 0..12000) return null
        if (shot.launchAngleDeg !in 0.0..85.0) return null
        if (shot.launchDirDeg !in -90.0..90.0) return null
        if (shot.spinAxisDeg !in -90.0..90.0) return null
        return LaunchConditions(
            ballSpeedMps = shot.ballSpeedMps,
            launchAngleDeg = shot.launchAngleDeg,
            spinRpm = spinRpm,
            launchDirDeg = shot.launchDirDeg,
            spinAxisDeg = shot.spinAxisDeg,
        )
    }

    /** The median launch conditions over a club's kept shots, or null. */
    fun medianLaunch(shots: List<BagMappingShotEntity>): LaunchConditions? {
        val kept = shots.filter { it.effectiveIncluded() }
        if (kept.isEmpty()) return null
        val ballSpeed = BagMappingStats.median(kept.map { it.ballSpeedMps }) ?: return null
        val launchAngle = BagMappingStats.median(kept.map { it.launchAngleDeg }) ?: return null
        val spin = BagMappingStats.median(kept.map { it.totalSpinRpm.toDouble() }) ?: return null
        val launchDir = BagMappingStats.median(kept.map { it.launchDirDeg }) ?: return null
        val spinAxis = BagMappingStats.median(kept.map { it.spinAxisDeg }) ?: return null
        val spinRpm = spin.roundToInt()
        if (ballSpeed !in 0.5..100.0) return null
        if (spinRpm !in 0..12000) return null
        if (launchAngle !in 0.0..85.0) return null
        if (launchDir !in -90.0..90.0) return null
        if (spinAxis !in -90.0..90.0) return null
        return LaunchConditions(ballSpeed, launchAngle, spinRpm, launchDir, spinAxis)
    }

    /** The median of the basis distance over a club's kept shots, or null. */
    fun medianDistance(shots: List<BagMappingShotEntity>, basis: DistanceBasis): Double? =
        BagMappingStats.median(shots.filter { it.effectiveIncluded() }.map { it.distanceFor(basis) })

    /** Task 4 implements this. */
    fun trace(clubName: String, shots: List<BagMappingShotEntity>, basis: DistanceBasis): ClubTrace? = null

    /** Task 5 implements this. */
    fun dots(clubName: String, shots: List<BagMappingShotEntity>, basis: DistanceBasis): ClubDots =
        ClubDots(clubName, emptyList())
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagTraceTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTrace.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagTraceTest.kt
git commit -m "feat(bag): median launch conditions and median distance per club"
```

---

## Task 4: The drawn trace, scaled to the authoritative distance

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTrace.kt` (replace the `trace` stub)
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagTraceTest.kt` (append)

- [ ] **Step 1: Write the failing tests**

Append to `BagTraceTest`:

```kotlin
    @Test
    fun `trace ends exactly on the authoritative median distance`() {
        val shots = listOf(
            shot(carryM = 130.0), shot(carryM = 140.0), shot(carryM = 150.0),
        )
        val trace = BagTrace.trace("7i", shots, DistanceBasis.CARRY)
        assertNotNull(trace)
        assertEquals(140.0, trace!!.distanceM, 1e-9)
        // Scale-to-fit: the drawn trace lands on the printed number, whatever
        // the simulation's own carry was.
        assertEquals(140.0, trace.points.last().downrangeM, 1e-6)
        assertTrue(trace.points.size > 1)
    }

    @Test
    fun `trace starts at the tee and its apex is positive`() {
        val trace = BagTrace.trace("7i", listOf(shot()), DistanceBasis.CARRY)!!
        assertEquals(0.0, trace.points.first().downrangeM, 1e-9)
        assertEquals(0.0, trace.points.first().heightM, 1e-9)
        assertTrue(trace.apexM > 0.0)
    }

    @Test
    fun `total basis runs further than carry basis`() {
        val shots = listOf(shot(carryM = 140.0, totalM = 152.0))
        val carry = BagTrace.trace("7i", shots, DistanceBasis.CARRY)!!
        val total = BagTrace.trace("7i", shots, DistanceBasis.TOTAL)!!
        assertTrue(total.distanceM > carry.distanceM)
        assertTrue(total.points.size > carry.points.size)
    }

    @Test
    fun `trace is null with no kept shots or unbuildable launch`() {
        assertNull(BagTrace.trace("7i", emptyList(), DistanceBasis.CARRY))
        assertNull(BagTrace.trace("7i", listOf(shot(included = false)), DistanceBasis.CARRY))
        assertNull(BagTrace.trace("7i", listOf(shot(launchAngleDeg = 90.0)), DistanceBasis.CARRY))
    }
```

Add `import org.junit.Assert.assertTrue` to the test file's imports.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagTraceTest"`
Expected: FAIL — `trace` returns null (`expected not null`).

- [ ] **Step 3: Write the implementation**

Replace the `trace` stub with:

```kotlin
    /**
     * The club's median flight, ready to draw.
     *
     * The trace is simulated from the **median of the launch metrics**, which
     * does not land at the **median of the distances** (the median of a function
     * is not the function of the medians). It is therefore uniformly scaled by
     * `medianDistance / traceEndDistance` so it ends exactly on the number
     * printed under it — one number per club across the whole page. The
     * persisted caches were also written at capture time by an engine whose
     * bounce/roll law is periodically recalibrated, so a fresh simulation's
     * total can drift from the stored one. Scaling keeps the shape (which is
     * what carries the launch-angle and spin information) and discards an
     * absolute distance the trace was never authoritative for.
     *
     * Null when the club has no kept shots, its medians cannot build launch
     * conditions, or the trace has no forward extent.
     */
    fun trace(clubName: String, shots: List<BagMappingShotEntity>, basis: DistanceBasis): ClubTrace? {
        val launch = medianLaunch(shots) ?: return null
        val distance = medianDistance(shots, basis) ?: return null
        if (distance <= 0.0) return null
        val simulated = BallFlightEngine.simulate(launch)
        val samples = when (basis) {
            DistanceBasis.CARRY -> simulated.samples
            DistanceBasis.TOTAL -> simulated.samples + RangeRollout.samples(simulated)
        }
        if (samples.isEmpty()) return null
        val raw = samples.map { TracePoint(hypot(it.px, it.py), it.pz) }
        val end = raw.last().downrangeM
        if (end <= 0.0) return null
        val k = distance / end
        val scaled = raw.map { TracePoint(it.downrangeM * k, it.heightM * k) }
        return ClubTrace(clubName, scaled, distance, scaled.maxOf { it.heightM })
    }
```

Add the import: `import com.hpsmiles.golfsim.range.RangeRollout`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagTraceTest"`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTrace.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagTraceTest.kt
git commit -m "feat(bag): simulated median flight trace scaled to the authoritative distance"
```

---

## Task 5: Per-shot landing points for the top-down view

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTrace.kt` (replace the `dots` stub)
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagTraceTest.kt` (append)

- [ ] **Step 1: Write the failing tests**

Append to `BagTraceTest`:

```kotlin
    @Test
    fun `dots returns one point per kept shot`() {
        val shots = listOf(shot(), shot(), shot(carryM = 150.0), shot(included = false))
        val dots = BagTrace.dots("7i", shots, DistanceBasis.CARRY)
        assertEquals(3, dots.points.size)
        assertEquals("7i", dots.clubName)
    }

    @Test
    fun `dots distance comes from the persisted cache, not the simulation`() {
        val shots = listOf(shot(carryM = 137.5, totalM = 149.25))
        assertEquals(137.5, BagTrace.dots("7i", shots, DistanceBasis.CARRY).points.single().second, 1e-9)
        assertEquals(149.25, BagTrace.dots("7i", shots, DistanceBasis.TOTAL).points.single().second, 1e-9)
    }

    @Test
    fun `dots skips shots whose launch block cannot be built`() {
        val shots = listOf(shot(), shot(launchAngleDeg = 90.0))
        assertEquals(1, BagTrace.dots("7i", shots, DistanceBasis.CARRY).points.size)
    }

    @Test
    fun `dots lateral is a finite offset and a straight shot sits near zero`() {
        val straight = BagTrace.dots("7i", listOf(shot(launchDirDeg = 0.0, spinAxisDeg = 0.0)), DistanceBasis.CARRY)
        assertTrue(straight.points.single().first.isFinite())
        assertTrue(kotlin.math.abs(straight.points.single().first) < 1.0)

        val pushed = BagTrace.dots("7i", listOf(shot(launchDirDeg = 10.0, spinAxisDeg = 0.0)), DistanceBasis.CARRY)
        assertTrue(pushed.points.single().first > 1.0)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagTraceTest"`
Expected: FAIL — `dots` returns an empty list.

- [ ] **Step 3: Write the implementation**

Replace the `dots` stub with:

```kotlin
    /**
     * Landing points for the top-down view, one per kept shot.
     *
     * The entity stores no lateral column, so the lateral offset comes from a
     * per-shot simulation: for CARRY the `px` of the last flight sample (the
     * carry landing), for TOTAL `ShotResult.sideM` (the rest position's lateral
     * offset). The **distance** always comes from the persisted cache via
     * [distanceFor], never from the simulation — the roll law is the part that
     * gets recalibrated, and it moves distance but never lateral deviation, so
     * this keeps every dot's distance identical to every other number on the
     * page.
     */
    fun dots(clubName: String, shots: List<BagMappingShotEntity>, basis: DistanceBasis): ClubDots {
        val points = shots.filter { it.effectiveIncluded() }.mapNotNull { shot ->
            val launch = launchFor(shot) ?: return@mapNotNull null
            val simulated = BallFlightEngine.simulate(launch)
            val lateral = when (basis) {
                DistanceBasis.CARRY -> simulated.samples.lastOrNull()?.px ?: return@mapNotNull null
                DistanceBasis.TOTAL -> simulated.sideM
            }
            lateral to shot.distanceFor(basis)
        }
        return ClubDots(clubName, points)
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagTraceTest"`
Expected: PASS (14 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTrace.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagTraceTest.kt
git commit -m "feat(bag): per-shot top-down landing points (simulated lateral, cached distance)"
```

---

## Task 6: Side-view axis fit and label stagger

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagSideGeom.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagSideGeomTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BagSideGeomTest {

    private val fit = computeSideFit(
        widthPx = 1000.0,
        heightPx = 500.0,
        maxDistanceM = 237.0,
        maxApexM = 30.0,
    )

    @Test
    fun `origin sits at the left pad and the max distance near the right edge`() {
        assertEquals(44.0f, fit.x(0.0), 1e-3f)
        // 6 % headroom on the span, so the longest club stops short of the edge.
        assertTrue(fit.x(237.0) < 1000f - 16f)
        assertTrue(fit.x(237.0) > 1000f - 100f)
    }

    @Test
    fun `x is linear and monotonic`() {
        assertEquals((fit.x(0.0) + fit.x(200.0)) / 2f, fit.x(100.0), 1e-3f)
        assertTrue(fit.x(100.0) < fit.x(200.0))
    }

    @Test
    fun `baseline sits above the bottom pad and height maps upward`() {
        assertEquals(482.0f, fit.baselineY.toFloat(), 1e-3f)
        assertEquals(482.0f, fit.y(0.0), 1e-3f)
        assertTrue(fit.y(30.0) < fit.y(0.0))
    }

    @Test
    fun `the tallest apex fills the height fraction of the plot`() {
        val usable = 500.0 - 18.0
        assertEquals(SIDE_HEIGHT_FRACTION, (fit.y(0.0) - fit.y(30.0)) / usable.toFloat(), 1e-4f)
    }

    @Test
    fun `a flat bag does not divide by zero`() {
        val flat = computeSideFit(1000.0, 500.0, maxDistanceM = 0.0, maxApexM = 0.0)
        assertTrue(flat.pxPerMM.isFinite())
        assertTrue(flat.pxPerMHeight.isFinite())
        assertTrue(flat.pxPerMHeight > 0.0)
    }

    @Test
    fun `label rows alternate`() {
        assertEquals(0, labelRow(0))
        assertEquals(1, labelRow(1))
        assertEquals(0, labelRow(2))
        assertEquals(1, labelRow(13))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagSideGeomTest"`
Expected: FAIL — `Unresolved reference: computeSideFit`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.hpsmiles.golfsim.bag

/**
 * The side view's axis fit: distance metres → screen x, height metres → screen
 * y. Pure geometry over doubles, in the spirit of [BoxPlotGeom] — the canvas
 * and the label layout both derive from one fit, so labels can never drift off
 * their landing points.
 */
data class SideFit(
    /** The right-hand end of the axis, metres (the longest club plus headroom). */
    val maxDistanceM: Double,
    val pxPerMM: Double,
    /** Screen x of distance 0 (the tee). */
    val originX: Double,
    /** Screen y of height 0 (the ground line). */
    val baselineY: Double,
    val pxPerMHeight: Double,
) {
    fun x(distanceM: Double): Float = (originX + distanceM * pxPerMM).toFloat()
    fun y(heightM: Double): Float = (baselineY - heightM * pxPerMHeight).toFloat()
}

/**
 * Fraction of the plot height the tallest apex is allowed to fill. Real
 * trajectories on a 260 m-wide axis would otherwise be flat lines; one shared
 * vertical scale keeps the arcs' *relative* heights truthful, and the y-axis is
 * deliberately unlabelled ("height, not to scale").
 */
const val SIDE_HEIGHT_FRACTION = 0.85

/** Distance headroom past the longest club, so its marker is not on the edge. */
private const val SIDE_DISTANCE_HEADROOM = 1.06

fun computeSideFit(
    widthPx: Double,
    heightPx: Double,
    maxDistanceM: Double,
    maxApexM: Double,
    leftPadPx: Double = 44.0,
    rightPadPx: Double = 16.0,
    bottomPadPx: Double = 18.0,
): SideFit {
    val span = maxOf(maxDistanceM * SIDE_DISTANCE_HEADROOM, 1.0)
    val plotWidth = maxOf(widthPx - leftPadPx - rightPadPx, 1.0)
    val usableHeight = maxOf(heightPx - bottomPadPx, 1.0)
    val apex = maxOf(maxApexM, 1e-6)
    return SideFit(
        maxDistanceM = span,
        pxPerMM = plotWidth / span,
        originX = leftPadPx,
        baselineY = usableHeight,
        pxPerMHeight = SIDE_HEIGHT_FRACTION * usableHeight / apex,
    )
}

/**
 * Two-row stagger for the landing labels: alternating rows keep neighbouring
 * clubs' labels apart without rotating the text. Not a collision solver — the
 * accepted limitation is that two clubs ~48 px apart on the same row can begin
 * to touch with unusually long names.
 */
fun labelRow(index: Int): Int = index.mod(2)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagSideGeomTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagSideGeom.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagSideGeomTest.kt
git commit -m "feat(bag): side-view axis fit and label stagger geometry"
```

---

## Task 7: Summary strip statistics

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagSummary.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagSummaryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BagSummaryTest {

    private fun entry(longer: String, shorter: String, gapM: Double, flag: GapFlag) =
        GapEntry(longerClub = longer, shorterClub = shorter, gapM = gapM, flag = flag,
            bandLowM = 0.0, bandHighM = 0.0)

    @Test
    fun `average, closest, farthest and the counts`() {
        val gaps = listOf(
            entry("7i", "6i", 6.0, GapFlag.TIGHT),
            entry("6i", "5i", 10.0, GapFlag.HEALTHY),
            entry("5i", "4i", 4.0, GapFlag.TIGHT),
            entry("4i", "3i", 22.0, GapFlag.WIDE),
            entry("3i", "5w", 12.0, GapFlag.HEALTHY),
        )
        val summary = summarizeGaps(gaps)
        assertEquals(10.8, summary.averageGapM!!, 1e-9)
        assertEquals(4.0, summary.closest!!.gapM, 1e-9)
        assertEquals("5i", summary.closest!!.shorterClub)
        assertEquals(22.0, summary.farthest!!.gapM, 1e-9)
        assertEquals("4i", summary.farthest!!.longerClub)
        assertEquals(2, summary.goodCount)
        assertEquals(1, summary.wideCount)
        assertEquals(2, summary.tightCount)
    }

    @Test
    fun `no gaps yields a null-bearing summary`() {
        val summary = summarizeGaps(emptyList())
        assertNull(summary.averageGapM)
        assertNull(summary.closest)
        assertNull(summary.farthest)
        assertEquals(0, summary.goodCount)
        assertEquals(0, summary.wideCount)
        assertEquals(0, summary.tightCount)
    }

    @Test
    fun `inverted gaps are counted in no bucket`() {
        val summary = summarizeGaps(listOf(entry("7i", "6i", 0.0, GapFlag.INVERTED)))
        assertEquals(0, summary.goodCount + summary.wideCount + summary.tightCount)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagSummaryTest"`
Expected: FAIL — `Unresolved reference: summarizeGaps`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.hpsmiles.golfsim.bag

/**
 * The headline gap statistics shown above the chart. Built from the same
 * [GapEntry] list the panel's DISTANCE GAPS block renders, so the strip and the
 * block can never disagree.
 */
data class GapSummary(
    val averageGapM: Double?,
    val closest: GapEntry?,
    val farthest: GapEntry?,
    val goodCount: Int,
    val wideCount: Int,
    val tightCount: Int,
)

fun summarizeGaps(gaps: List<GapEntry>): GapSummary = GapSummary(
    averageGapM = if (gaps.isEmpty()) null else gaps.map { it.gapM }.average(),
    closest = gaps.minByOrNull { it.gapM },
    farthest = gaps.maxByOrNull { it.gapM },
    goodCount = gaps.count { it.flag == GapFlag.HEALTHY },
    wideCount = gaps.count { it.flag == GapFlag.WIDE },
    tightCount = gaps.count { it.flag == GapFlag.TIGHT },
)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagSummaryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagSummary.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagSummaryTest.kt
git commit -m "feat(bag): gap summary statistics for the results strip"
```

---

## Task 8: The SIDE view

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagSideView.kt`

This is a canvas plus a positioned-label layout. It has no unit tests (canvas drawing is verified on the tablet in Task 12); the geometry it draws from is already tested in Task 6.

- [ ] **Step 1: Write the file**

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import java.util.Locale
import kotlin.math.roundToInt

private val LabelStripHeight = 36.dp
private val LabelRowGap = 2.dp
private val ArcWidth = 2.dp
private val LandingDotRadius = 4.dp
private val LandingDotRim = 1.dp
private val AxisTickLabelSp = 10.sp
private val AxisTickLength = 4.dp

/**
 * SIDE view (spec §6): every club's real simulated median flight on one shared
 * distance axis, arcs first, landing markers and labels on top. Labels are real
 * composables in a two-row stagger beneath the axis, so they are accessible and
 * tappable and cannot be clipped by the canvas.
 */
@Composable
fun BagSideView(
    traces: List<ClubTrace>,
    colors: Map<String, Color>,
    onClubTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (traces.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No shots to plot.", style = GolfTypography.Body, color = GolfColors.TextSecondary)
        }
        return
    }
    val maxDistance = traces.maxOf { it.distanceM }
    val maxApex = traces.maxOf { it.apexM }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boxWidth = constraints.maxWidth
        val stripPx = with(density) { LabelStripHeight.toPx() }
        val rowGapPx = with(density) { LabelRowGap.toPx() }.roundToInt()
        val plotHeightPx = (constraints.maxHeight - stripPx).coerceAtLeast(1f)
        val fit = remember(boxWidth, plotHeightPx, maxDistance, maxApex) {
            computeSideFit(
                widthPx = boxWidth.toDouble(),
                heightPx = plotHeightPx.toDouble(),
                maxDistanceM = maxDistance,
                maxApexM = maxApex,
            )
        }
        Column(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { plotHeightPx.toDp() }),
            ) {
                drawSideChart(fit, traces, colors)
            }
            Layout(
                content = {
                    traces.forEach { trace ->
                        Text(
                            text = "${trace.clubName} ${trace.distanceM.roundToInt()}",
                            style = GolfTypography.Status,
                            color = colors[trace.clubName] ?: GolfColors.TextSecondary,
                            maxLines = 1,
                            modifier = Modifier.clickable { onClubTap(trace.clubName) },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { measurables, layoutConstraints ->
                val placeables = measurables.map { it.measure(Constraints()) }
                val rowHeight = placeables.maxOfOrNull { it.height } ?: 0
                layout(layoutConstraints.maxWidth, rowHeight * 2 + rowGapPx) {
                    placeables.forEachIndexed { index, placeable ->
                        val centreX = fit.x(traces[index].distanceM).roundToInt()
                        val x = (centreX - placeable.width / 2)
                            .coerceIn(0, (layoutConstraints.maxWidth - placeable.width).coerceAtLeast(0))
                        val y = labelRow(index) * (rowHeight + rowGapPx)
                        placeable.place(x, y)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSideChart(fit: SideFit, traces: List<ClubTrace>, colors: Map<String, Color>) {
    val baseline = fit.y(0.0)
    val tickPaint = android.graphics.Paint().apply {
        color = GolfColors.TextSecondary.toArgb()
        textSize = AxisTickLabelSp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    // Ground line, then ticks and their labels in the reserved bottom pad.
    drawLine(
        color = GolfColors.Line,
        start = androidx.compose.ui.geometry.Offset(fit.x(0.0), baseline),
        end = androidx.compose.ui.geometry.Offset(fit.x(fit.maxDistanceM), baseline),
        strokeWidth = 1f,
    )
    val tickLength = AxisTickLength.toPx()
    val labelBaseline = size.height - tickLength
    BoxPlotGeom.niceTicks(0.0, fit.maxDistanceM).forEach { tick ->
        val x = fit.x(tick)
        drawLine(
            color = GolfColors.Line,
            start = androidx.compose.ui.geometry.Offset(x, baseline),
            end = androidx.compose.ui.geometry.Offset(x, baseline + tickLength),
            strokeWidth = 1f,
        )
        drawContext.canvas.nativeCanvas.drawText(
            String.format(Locale.US, "%.0f", tick),
            x,
            labelBaseline,
            tickPaint,
        )
    }
    // Arcs first, all of them, so nothing crosses a marker or a label.
    traces.forEach { trace ->
        val color = colors[trace.clubName] ?: GolfColors.TextSecondary
        val path = Path()
        trace.points.forEachIndexed { index, point ->
            val x = fit.x(point.downrangeM)
            val y = fit.y(point.heightM)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = ArcWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    // Landing markers on top (white rim + club colour), matching the top-down dot.
    traces.forEach { trace ->
        val color = colors[trace.clubName] ?: GolfColors.TextSecondary
        val centre = androidx.compose.ui.geometry.Offset(fit.x(trace.distanceM), baseline)
        drawCircle(Color.White.copy(alpha = 0.6f), LandingDotRadius.toPx() + LandingDotRim.toPx(), centre)
        drawCircle(color, LandingDotRadius.toPx(), centre)
    }
    // The y-axis is deliberately unlabelled; say so rather than let it mislead.
    drawContext.canvas.nativeCanvas.drawText(
        "height, not to scale",
        fit.x(0.0),
        tickPaint.textSize,
        tickPaint.apply { textAlign = android.graphics.Paint.Align.LEFT },
    )
}
```

- [ ] **Step 2: Compile**

Run: `.\gradlew.bat --console=plain :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagSideView.kt
git commit -m "feat(bag): SIDE view canvas with staggered landing labels"
```

---

## Task 9: The TOP-DOWN view

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTopDownView.kt`

Read `app/src/main/kotlin/com/hpsmiles/golfsim/combine/CombineTopDownView.kt` first — this mirrors its window crop, ground treatment and draw recipe. No unit tests (verified on the tablet in Task 12).

- [ ] **Step 1: Write the file**

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private const val SNAP_M = 25.0
private const val MIN_WINDOW_M = 50.0
private const val CORRIDOR_VISIBILITY_SCALE = 1.15
private const val LATERAL_CONTENT_MARGIN_M = 2.0
private const val RING_STEPS = 72
private const val GRID_STEP_M = 50.0

/**
 * TOP-DOWN view (spec §7): one dot per kept shot plus a fitted minimum-enclosing
 * dispersion ring per club, in the club's ramp colour, over the range ground
 * treatment. Legend chips carry colour + club name only — deliberately no shot
 * count (user ruling 2026-10-16).
 */
@Composable
fun BagTopDownView(
    clubs: List<ClubDots>,
    colors: Map<String, Color>,
    onClubTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val withData = clubs.filter { it.points.isNotEmpty() }
    if (withData.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No shots to plot.", style = GolfTypography.Body, color = GolfColors.TextSecondary)
        }
        return
    }
    Column(modifier.fillMaxSize()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
        ) {
            withData.forEach { club ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors[club.clubName] ?: GolfColors.TextSecondary),
                    )
                    Text(
                        text = club.clubName,
                        style = GolfTypography.Status,
                        color = GolfColors.TextSecondary,
                    )
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)) { drawTopDown(withData, colors) }
    }
}

private fun DrawScope.drawTopDown(clubs: List<ClubDots>, colors: Map<String, Color>) {
    val allPoints = clubs.flatMap { it.points }
    val minDistance = allPoints.minOf { it.second }
    val maxDistance = allPoints.maxOf { it.second }
    val maxAbsSide = allPoints.maxOf { kotlin.math.abs(it.first) }

    // Mirror CombineTopDownView: crop the distance window to this session's
    // content, snapped outward to 25 m, with a 50 m floor so pxPerM stays sane.
    var windowMinM = (floor(minDistance / SNAP_M) * SNAP_M).coerceAtLeast(0.0)
    var windowMaxM = (ceil(maxDistance / SNAP_M) * SNAP_M).coerceAtMost(RangeScene.FAIRWAY_END_Y)
    if (windowMaxM - windowMinM < MIN_WINDOW_M) {
        val centre = (windowMinM + windowMaxM) / 2.0
        windowMinM = (centre - MIN_WINDOW_M / 2.0).coerceAtLeast(0.0)
        windowMaxM = (windowMinM + MIN_WINDOW_M).coerceAtMost(RangeScene.FAIRWAY_END_Y)
        windowMinM = windowMaxM - MIN_WINDOW_M
    }
    val leftPad = 26f
    val rightPad = 12f
    val pxPerM = (size.width - leftPad - rightPad) / (windowMaxM - windowMinM).toFloat()
    fun sx(distanceM: Double): Float = leftPad + ((distanceM - windowMinM) * pxPerM).toFloat()

    val lateralHalfM = max(
        RangeScene.fairwayHalfWidth(0.0) * CORRIDOR_VISIBILITY_SCALE,
        maxAbsSide + LATERAL_CONTENT_MARGIN_M,
    )
    val pxPerMSide = (size.height / 2f) / lateralHalfM.toFloat()
    val midY = size.height / 2f
    fun sy(sideM: Double): Float = midY - (sideM * pxPerMSide).toFloat()

    // Ground: rough base, mown fairway corridor, stripes clipped to the window.
    drawRect(GolfColors.RangeRough, size = size)
    val corridorHalf = RangeScene.fairwayHalfWidth(0.0).toFloat() * pxPerMSide
    drawRect(
        color = GolfColors.RangeFairway,
        topLeft = Offset(sx(windowMinM), midY - corridorHalf),
        size = Size(sx(windowMaxM) - sx(windowMinM), corridorHalf * 2f),
    )
    var stripeM = floor(windowMinM / GRID_STEP_M) * GRID_STEP_M
    while (stripeM < windowMaxM) {
        val x0 = sx(stripeM)
        val x1 = sx(stripeM + GRID_STEP_M / 2.0)
        drawRect(
            color = RangeScene.stripeBand,
            topLeft = Offset(x0, midY - corridorHalf),
            size = Size(max(x1 - x0, 0f), corridorHalf * 2f),
        )
        stripeM += GRID_STEP_M
    }
    // Distance gridlines with labels, inside the window only.
    val gridPaint = android.graphics.Paint().apply {
        color = GolfColors.TextMuted.toArgb()
        textSize = 10.sp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    var gridM = ceil(windowMinM / GRID_STEP_M) * GRID_STEP_M
    while (gridM <= windowMaxM) {
        val x = sx(gridM)
        drawLine(GolfColors.Line, Offset(x, 0f), Offset(x, size.height), 1f)
        drawContext.canvas.nativeCanvas.drawText(
            String.format(java.util.Locale.US, "%.0f", gridM), x, size.height - 4f, gridPaint,
        )
        gridM += GRID_STEP_M
    }

    // Rings under dots: minimum-enclosing ellipse per club, buffered 5 %.
    clubs.forEach { club ->
        val color = colors[club.clubName] ?: GolfColors.TextSecondary
        val ellipse = DispersionEllipse.fromKept(club.points) ?: return@forEach
        val path = Path()
        for (step in 0..RING_STEPS) {
            val t = 2.0 * PI * step / RING_STEPS
            val a = ellipse.semiAxisM * cos(t)
            val b = ellipse.semiCrossM * sin(t)
            val sideM = ellipse.centreSideM + a * cos(ellipse.angleRad) - b * sin(ellipse.angleRad)
            val distanceM = ellipse.centreTotalM + a * sin(ellipse.angleRad) + b * cos(ellipse.angleRad)
            val x = sx(distanceM)
            val y = sy(sideM)
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(width = 3.5.dp.toPx()))
        drawPath(path, color.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx()))
    }
    // Dots on top.
    clubs.forEach { club ->
        val color = colors[club.clubName] ?: GolfColors.TextSecondary
        club.points.forEach { (lateralM, distanceM) ->
            val centre = Offset(sx(distanceM), sy(lateralM))
            drawCircle(Color.White.copy(alpha = 0.6f), 4.dp.toPx(), centre)
            drawCircle(color.copy(alpha = 0.9f), 3.dp.toPx(), centre)
        }
    }
}
```

Add the missing imports the code needs (`androidx.compose.foundation.background`, `androidx.compose.ui.graphics.toArgb`, `androidx.compose.ui.unit.sp`, `kotlin.math.abs`) and check the exact names of `RangeScene.fairwayHalfWidth`, `RangeScene.FAIRWAY_END_Y` and `RangeScene.stripeBand` in `range/RangeScene.kt` before compiling — use whatever the combine view uses for the same three things.

- [ ] **Step 2: Compile**

Run: `.\gradlew.bat --console=plain :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTopDownView.kt
git commit -m "feat(bag): TOP-DOWN dispersion view with fitted rings and name-only legend"
```

---

## Task 10: Summary strip and collapsible panel

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagResultsSummary.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagDistPanel.kt`

- [ ] **Step 1: Write `BagResultsSummary.kt`**

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The headline gap statistics (spec §8), built from the same [GapEntry] list the
 * panel's gap block renders. Follows the current basis — carry in CARRY mode,
 * total in TOTAL mode — and the driver is already excluded upstream.
 */
@Composable
fun BagResultsSummary(summary: GapSummary, basis: DistanceBasis, modifier: Modifier = Modifier) {
    if (summary.averageGapM == null) {
        Text(
            text = "No gaps to summarise — map at least two clubs.",
            style = GolfTypography.BodySmall,
            color = GolfColors.TextSecondary,
            modifier = modifier,
        )
        return
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
    ) {
        SummaryItem("AVG GAP", String.format(Locale.US, "%.1f m", summary.averageGapM), GolfColors.TextPrimary)
        summary.closest?.let {
            SummaryItem(
                "CLOSEST",
                "${it.gapM.roundToInt()} m (${it.shorterClub}–${it.longerClub})",
                GolfColors.TextPrimary,
            )
        }
        summary.farthest?.let {
            SummaryItem(
                "FARTHEST",
                "${it.gapM.roundToInt()} m (${it.shorterClub}–${it.longerClub})",
                GolfColors.TextPrimary,
            )
        }
        SummaryItem(
            "VERDICTS",
            "${summary.goodCount} good · ${summary.wideCount} big · ${summary.tightCount} tight",
            GolfColors.TextPrimary,
        )
    }
}

@Composable
private fun SummaryItem(label: String, value: String, valueColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
        Text(label, style = GolfTypography.MetricLabel, color = GolfColors.TextMuted)
        Text(value, style = GolfTypography.BodySmall, color = valueColor)
    }
}
```

- [ ] **Step 2: Write `BagDistPanel.kt`**

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * Collapsed-by-default "DISTRIBUTION & GAPS" panel (spec §9): the box-plot
 * matrix and the gap block live in here, so the top of the page belongs to the
 * chart. Collapsed by default; the caller owns the open state.
 */
@Composable
fun BagDistPanel(
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(GolfSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
        ) {
            Chevron(open)
            Text("DISTRIBUTION & GAPS", style = GolfTypography.MetricLabel, color = GolfColors.TextSecondary)
            Text(
                if (open) "tap to collapse" else "box plots and gap detail",
                style = GolfTypography.Status,
                color = GolfColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
        }
        if (open) content()
    }
}

@Composable
private fun Chevron(open: Boolean) {
    Canvas(Modifier.size(10.dp).rotate(if (open) 90f else 0f)) {
        val path = Path().apply {
            moveTo(size.width * 0.25f, 0f)
            lineTo(size.width * 0.75f, size.height / 2f)
            lineTo(size.width * 0.25f, size.height)
        }
        drawPath(path, GolfColors.TextSecondary, style = Stroke(width = 2f))
    }
}
```

- [ ] **Step 3: Compile**

Run: `.\gradlew.bat --console=plain :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Both new files are unreferenced so far; the compiler will still check them.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagResultsSummary.kt app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagDistPanel.kt
git commit -m "feat(bag): results summary strip and collapsible distribution panel"
```

---

## Task 11: Rewire the results page

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingResult.kt`

Read the whole file first. Three changes only — do not restyle anything else.

- [ ] **Step 1: Make `CarryMatrixSection` a plain composable**

Currently `@Composable private fun ColumnScope.CarryMatrixSection(session, shots, onDrillDown)` creating a `LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), ...)`. Change the signature to take a modifier and drop the receiver and the internal weight:

```kotlin
@Composable
private fun CarryMatrixSection(
    session: BagMappingSessionEntity,
    shots: List<BagMappingShotEntity>,
    basis: DistanceBasis,
    onDrillDown: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
```

and its `LazyColumn(modifier = modifier.fillMaxWidth(), ...)`. Remove the now-unused `ColumnScope` import if nothing else uses it.

- [ ] **Step 2: Make the gap medians follow the basis**

Inside `CarryMatrixSection`, change the medians build so the distance follows the basis (the box-plot lanes themselves stay carry + total, and `sharedAxis` stays over both):

```kotlin
    val mappedMedians = rows.mapNotNull { (name, type, clubShots) ->
        val kept = clubShots.filter { it.effectiveIncluded() }.map { it.distanceFor(basis) }
        val dist = BagMappingStats.distribution(kept) ?: return@mapNotNull null
        Triple(name, type, dist.median)
    }
```

and give the gap heading its basis suffix:

```kotlin
                    Text(
                        "DISTANCE GAPS · ${basis.name}",
                        style = GolfTypography.MetricLabel,
                        color = GolfColors.TextMuted,
                    )
```

- [ ] **Step 3: Rebuild the page root**

Replace the body of `BagMappingResult` from the stale-resume banner down to (but not including) the RETEST/HISTORY `Row` with the new composition. Keep the banner, the buttons row, the `drillClub` state and the `DrillDownPanel` exactly as they are.

```kotlin
    var drillClub by remember { mutableStateOf<String?>(null) }
    var view by remember { mutableStateOf(BagResultsView.SIDE) }
    var basis by remember { mutableStateOf(DistanceBasis.CARRY) }
    var panelOpen by remember { mutableStateOf(false) }

    val rows = session.clubSnapshot()
    val colorByClub = rows.mapIndexed { index, (name, _) -> name to BagRamp.clubColor(index) }.toMap()
    val traces = remember(shots, basis) {
        rows.mapNotNull { (name, _) ->
            BagTrace.trace(name, shots.filter { it.clubName == name }, basis)
        }
    }
    val dots = remember(shots, basis) {
        rows.map { (name, _) -> BagTrace.dots(name, shots.filter { it.clubName == name }, basis) }
    }
    val gaps = remember(shots, basis) {
        GapAnalysis.analyzeByClubIdentity(
            rows.mapNotNull { (name, type) ->
                val kept = shots.filter { it.clubName == name && it.effectiveIncluded() }
                    .map { it.distanceFor(basis) }
                val dist = BagMappingStats.distribution(kept) ?: return@mapNotNull null
                Triple(name, type, dist.median)
            },
        )
    }
    val summary = remember(gaps) { summarizeGaps(gaps) }
```

then, in the `Column`, after the stale banner:

```kotlin
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            SegmentedChoice("SIDE", view == BagResultsView.SIDE) { view = BagResultsView.SIDE }
            SegmentedChoice("TOP-DOWN", view == BagResultsView.TOP_DOWN) { view = BagResultsView.TOP_DOWN }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            SegmentedChoice("CARRY", basis == DistanceBasis.CARRY) { basis = DistanceBasis.CARRY }
            SegmentedChoice("TOTAL", basis == DistanceBasis.TOTAL) { basis = DistanceBasis.TOTAL }
        }
        BagResultsSummary(summary, basis)
        when (view) {
            BagResultsView.SIDE -> BagSideView(
                traces = traces,
                colors = colorByClub,
                onClubTap = { drillClub = it },
                modifier = Modifier.weight(1f),
            )
            BagResultsView.TOP_DOWN -> BagTopDownView(
                clubs = dots,
                colors = colorByClub,
                onClubTap = { drillClub = it },
                modifier = Modifier.weight(1f),
            )
        }
        BagDistPanel(
            open = panelOpen,
            onToggle = { panelOpen = !panelOpen },
            modifier = if (panelOpen) Modifier.weight(1f) else Modifier,
        ) {
            CarryMatrixSection(
                session = session,
                shots = shots,
                basis = basis,
                onDrillDown = { drillClub = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
```

Add a small local segmented-control composable at the bottom of the file (the range VIEW chip treatment is the precedent):

```kotlin
@Composable
private fun SegmentedChoice(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (active) GolfColors.Teal else GolfColors.Line,
                shape = RoundedCornerShape(GolfSpacing.Sm),
            )
            .clickable { onClick() }
            .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs),
    ) {
        Text(
            text = label,
            style = GolfTypography.MetricLabel,
            color = if (active) GolfColors.Teal else GolfColors.TextSecondary,
        )
    }
}
```

- [ ] **Step 4: Build and test**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.*"`
Expected: BUILD SUCCESSFUL, 44 + 28 new tests, 0 failures.

Run: `.\gradlew.bat --console=plain :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingResult.kt
git commit -m "feat(bag): results page leads with SIDE/TOP-DOWN, basis toggle, collapsed distribution panel"
```

---

## Task 12: On-device verification

**Files:** none (verification only).

- [ ] **Step 1: Install and launch**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :app:installDebug
$adb = "C:\Users\smiles\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input keyevent KEYCODE_WAKEUP
& $adb shell am force-stop com.hpsmiles.golfsim
& $adb shell monkey -p com.hpsmiles.golfsim -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 8
& $adb shell input tap 102 347   # BAG nav item
Start-Sleep -Seconds 3
& $adb shell uiautomator dump /sdcard/ui.xml | Out-Null
& $adb shell cat /sdcard/ui.xml | Select-String "SIDE|TOP-DOWN|CARRY|TOTAL|AVG GAP|DISTRIBUTION"
```

Expected: the dump contains `SIDE`, `TOP-DOWN`, `CARRY`, `TOTAL`, the summary strip text, and `DISTRIBUTION & GAPS` — and **not** any box-plot row text, because the panel starts collapsed.

- [ ] **Step 2: Capture both views and both bases**

Four screenshots into `C:\Users\smiles\AppData\Local\Temp\opencode\bagsim\`: SIDE/CARRY (default), SIDE/TOTAL, TOP-DOWN/CARRY, TOP-DOWN/TOTAL — plus one with the panel expanded. Use `cmd /c "<adb> exec-out screencap -p > <file>"` and re-verify the screen state with `uiautomator dump` before each capture.

- [ ] **Step 3: Have the observer review the captures**

Check: arcs differ in shape between clubs and a wedge arcs visibly higher than a driver; labels stagger across two rows and none collide or clip; the metre scale is linear, shared and aligned with the landing markers; TOTAL runs further right than CARRY; the top-down legend carries no shot counts; rings sit on their dots and are absent for clubs with fewer than 3 kept shots; the panel starts collapsed and expands to the unchanged matrix; nothing overlaps the RETEST/HISTORY row.

- [ ] **Step 4: Report**

Summarise what was verified, with the exact evidence, and list anything the observer flagged as still wrong.

---

## Deviations from the spec

- **Spec §6.3's separate "tee" label is dropped.** The distance axis already carries a labelled `0` tick at the origin and every arc visibly converges there, so a second label is redundant clutter.
- **Spec §6.5 says "the landing marker and the label both open the drill-down"** — implemented as written; the arc body is still not hit-tested.
- **The 13 ramp hues in Task 2 are the proposed values from the spec.** If the rendered separation is poor on the dark base, adjust `BagRamp.hues` (keeping the first six) and re-run `BagRampTest` — no other file needs to change.

### Deviations added during the 2026-10-16 results rework

- **Spec section 7's lateral half-window ("corridor visibility scale x maxAbsSide + margin", as the combine view does) is replaced by true-scale parity.** `pxPerMSide` now equals `pxPerMM` - one px per metre on BOTH axes - because an independent lateral fit made rings read as tall, narrow ellipses that contradicted the tick spacing. Containment is instead guaranteed by (a) growing the distance window (zoom-out only, re-snapped to the 25 m grid, window start shifted down when the 330 m range cap clamps) until the vertical reach covers `maxAbsSide + margin`, (b) a page-level minimum chart-pane height (`minPaneHeightPx`) when the panel is open, and (c) a documented fallback that squashes the vertical scale only when even the full range cannot contain the data.
- **Spec section 9's "sticky metre scale" is a header pinned above the matrix list, not a LazyColumn `stickyHeader`.** A sticky overlay strikes through whatever scrolls beneath it, and the DISTANCE GAPS block is taller than the viewport below the sticky on the target tablet - the heading could never fully clear it. Pinned outside the list, the scale never obscures a row or the gap block at any scroll position.

---

# Plan corrections (authoritative)

The self-review found three defects in the tasks above. **Where this section conflicts with a task above, this section wins.** Also add `| app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagTopDownGeomTest.kt | create |` to the file table.

## Correction 1 — Task 8: the landing marker must be a tap target

Add these two imports to `BagSideView.kt` (keep the rest of the import list):

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
```

Replace the `Canvas(...)` call in `BagSideView` with this — the spec (§6.5) makes the landing marker a tap target as well as the label, so the canvas hit-tests the markers. The arc body stays un-hit-tested:

```kotlin
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { plotHeightPx.toDp() })
                    .pointerInput(fit, traces) {
                        detectTapGestures { offset ->
                            val hit = traces.minByOrNull { trace ->
                                val dx = fit.x(trace.distanceM) - offset.x
                                val dy = fit.y(0.0) - offset.y
                                dx * dx + dy * dy
                            } ?: return@detectTapGestures
                            val dx = fit.x(hit.distanceM) - offset.x
                            val dy = fit.y(0.0) - offset.y
                            val tolerance = 24.dp.toPx()
                            if (dx * dx + dy * dy <= tolerance * tolerance) onClubTap(hit.clubName)
                        }
                    },
            ) {
                drawSideChart(fit, traces, colors)
            }
```

## Correction 2 — Task 9 replaced in full

Task 9 becomes seven steps: the geometry file and its test first (steps 1–4), then the view (steps 5–7). `BagTopDownGeom.kt` owns the window crop and the fit; the canvas and the tap hit-test both derive from it, so a dot can never be drawn where it cannot be tapped.

- [ ] **Step 1: Write the failing geometry test** — `app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagTopDownGeomTest.kt`

```kotlin
package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagTopDownGeomTest {

    private val clubs = listOf(
        ClubDots("LW", listOf(-2.0 to 76.5, 1.0 to 77.0, 0.0 to 76.0)),
        ClubDots("D", listOf(-8.0 to 237.0, 5.0 to 240.0, 0.0 to 238.0)),
    )

    @Test
    fun `no points yields no fit`() {
        assertNull(computeBagTopDownFit(1000f, 400f, listOf(ClubDots("LW", emptyList()))))
        assertNull(computeBagTopDownFit(1000f, 400f, emptyList()))
    }

    @Test
    fun `distance window snaps outward to 25 m`() {
        val fit = computeBagTopDownFit(1000f, 400f, clubs)!!
        assertEquals(75.0, fit.windowMinM, 1e-9)
        assertEquals(250.0, fit.windowMaxM, 1e-9)
    }

    @Test
    fun `a narrow session keeps the 50 m floor`() {
        val tight = listOf(ClubDots("PW", listOf(0.0 to 121.0, 1.0 to 122.0, -1.0 to 121.5)))
        val fit = computeBagTopDownFit(1000f, 400f, tight)!!
        assertTrue(fit.windowMaxM - fit.windowMinM >= TOP_DOWN_MIN_WINDOW_M - 1e-9)
    }

    @Test
    fun `sx maps the window edges to the pads and is monotonic`() {
        val fit = computeBagTopDownFit(1000f, 400f, clubs)!!
        assertEquals(26f, fit.sx(fit.windowMinM), 1e-3f)
        assertEquals(1000f - 12f, fit.sx(fit.windowMaxM), 1e-3f)
        assertTrue(fit.sx(100.0) < fit.sx(200.0))
    }

    @Test
    fun `sy puts lateral zero at the mid line and positive side above it`() {
        val fit = computeBagTopDownFit(1000f, 400f, clubs)!!
        assertEquals(200f, fit.sy(0.0), 1e-3f)
        assertTrue(fit.sy(10.0) < fit.sy(0.0))
    }

    @Test
    fun `the widest lateral content fits inside the pane`() {
        val wide = listOf(ClubDots("D", listOf(-40.0 to 200.0, 40.0 to 210.0, 0.0 to 205.0)))
        val fit = computeBagTopDownFit(1000f, 400f, wide)!!
        assertTrue(fit.sy(-40.0) <= 400f)
        assertTrue(fit.sy(40.0) >= 0f)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagTopDownGeomTest"`
Expected: FAIL — `Unresolved reference: computeBagTopDownFit`.

- [ ] **Step 3: Write the geometry** — `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTopDownGeom.kt`

```kotlin
package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Distance-window snap, metres — mirrors CombineTopDownView. */
const val TOP_DOWN_SNAP_M = 25.0

/** Smallest distance window, metres — keeps pxPerMM sane on a thin session. */
const val TOP_DOWN_MIN_WINDOW_M = 50.0

/** Corridor visibility scale — the fitting top-down's constant. */
const val TOP_DOWN_CORRIDOR_VISIBILITY_SCALE = 1.15

/** Lateral breathing room past the widest shot, metres. */
const val TOP_DOWN_LATERAL_MARGIN_M = 2.0

/**
 * The TOP-DOWN view's fit. One instance drives both the canvas and the tap
 * hit-test, so a dot can never be drawn somewhere it cannot be tapped.
 */
data class BagTopDownFit(
    val windowMinM: Double,
    val windowMaxM: Double,
    val pxPerMM: Float,
    val leftPadPx: Float,
    val pxPerMSide: Float,
    val midY: Float,
) {
    /** Screen x of a downrange distance. */
    fun sx(distanceM: Double): Float = leftPadPx + ((distanceM - windowMinM) * pxPerMM).toFloat()

    /** Screen y of a lateral offset (positive side drawn upward). */
    fun sy(sideM: Double): Float = midY - (sideM * pxPerMSide).toFloat()
}

/**
 * Fits the session's content: the distance window is cropped to the actual
 * points and snapped outward to [TOP_DOWN_SNAP_M] (floor
 * [TOP_DOWN_MIN_WINDOW_M]) so empty range either side of the action is trimmed
 * away; the lateral half-window fits the widest content. Null when there is
 * nothing to draw.
 */
fun computeBagTopDownFit(
    widthPx: Float,
    heightPx: Float,
    clubs: List<ClubDots>,
    leftPadPx: Float = 26f,
    rightPadPx: Float = 12f,
): BagTopDownFit? {
    val points = clubs.flatMap { it.points }
    if (points.isEmpty()) return null
    val minDistance = points.minOf { it.second }
    val maxDistance = points.maxOf { it.second }
    val maxAbsSide = points.maxOf { abs(it.first) }

    var windowMinM = (floor(minDistance / TOP_DOWN_SNAP_M) * TOP_DOWN_SNAP_M).coerceAtLeast(0.0)
    var windowMaxM = (ceil(maxDistance / TOP_DOWN_SNAP_M) * TOP_DOWN_SNAP_M)
        .coerceAtMost(RangeScene.FAIRWAY_END_Y)
    if (windowMaxM - windowMinM < TOP_DOWN_MIN_WINDOW_M) {
        val centre = (windowMinM + windowMaxM) / 2.0
        windowMinM = (centre - TOP_DOWN_MIN_WINDOW_M / 2.0).coerceAtLeast(0.0)
        windowMaxM = (windowMinM + TOP_DOWN_MIN_WINDOW_M).coerceAtMost(RangeScene.FAIRWAY_END_Y)
        windowMinM = windowMaxM - TOP_DOWN_MIN_WINDOW_M
    }

    val span = max(windowMaxM - windowMinM, 1.0)
    val lateralHalfM = max(
        RangeScene.fairwayHalfWidth(0.0) * TOP_DOWN_CORRIDOR_VISIBILITY_SCALE,
        maxAbsSide + TOP_DOWN_LATERAL_MARGIN_M,
    )
    return BagTopDownFit(
        windowMinM = windowMinM,
        windowMaxM = windowMaxM,
        pxPerMM = (widthPx - leftPadPx - rightPadPx) / span.toFloat(),
        leftPadPx = leftPadPx,
        pxPerMSide = (heightPx / 2f) / lateralHalfM.toFloat(),
        midY = heightPx / 2f,
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.BagTopDownGeomTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Write the view** — `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTopDownView.kt`

```kotlin
package com.hpsmiles.golfsim.bag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.range.RangeScene
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val RING_STEPS = 72
private const val GRID_STEP_M = 50.0
private val RingKeylineWidth = 3.5.dp
private val RingColorWidth = 2.dp
private val DotRimRadius = 4.dp
private val DotRadius = 3.dp
private val TapTolerance = 16.dp

/**
 * TOP-DOWN view (spec §7): one dot per kept shot plus a fitted minimum-enclosing
 * dispersion ring per club, in the club's ramp colour, over the range ground
 * treatment. The legend carries colour + club name only — deliberately no shot
 * count (user ruling 2026-10-16).
 */
@Composable
fun BagTopDownView(
    clubs: List<ClubDots>,
    colors: Map<String, Color>,
    onClubTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val withData = clubs.filter { it.points.isNotEmpty() }
    if (withData.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No shots to plot.", style = GolfTypography.Body, color = GolfColors.TextSecondary)
        }
        return
    }
    Column(modifier.fillMaxSize()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
        ) {
            withData.forEach { club ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors[club.clubName] ?: GolfColors.TextSecondary),
                    )
                    Text(club.clubName, style = GolfTypography.Status, color = GolfColors.TextSecondary)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val fit = remember(widthPx, heightPx, withData) {
                computeBagTopDownFit(widthPx, heightPx, withData)
            }
            val tolerance = with(LocalDensity.current) { TapTolerance.toPx() }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(fit) {
                        detectTapGestures { offset ->
                            val current = fit ?: return@detectTapGestures
                            val hit = withData
                                .flatMap { club -> club.points.map { club.clubName to it } }
                                .minByOrNull { (_, point) ->
                                    val dx = current.sx(point.second) - offset.x
                                    val dy = current.sy(point.first) - offset.y
                                    dx * dx + dy * dy
                                } ?: return@detectTapGestures
                            val dx = current.sx(hit.second.second) - offset.x
                            val dy = current.sy(hit.second.first) - offset.y
                            if (dx * dx + dy * dy <= tolerance * tolerance) onClubTap(hit.first)
                        }
                    },
            ) {
                fit?.let { drawTopDown(it, withData, colors) }
            }
        }
    }
}

private fun DrawScope.drawTopDown(fit: BagTopDownFit, clubs: List<ClubDots>, colors: Map<String, Color>) {
    // Ground: rough base, mown fairway corridor, 10 m stripes clipped to the
    // window — the same treatment as the fitting top-down (FittingTopDown.kt
    // 172-198), with the distance axis on x instead of y.
    drawRect(GolfColors.RangeRough, size = size)
    val corridorHalfM = RangeScene.fairwayHalfWidth(0.0)
    val corridorTop = fit.sy(corridorHalfM)
    val corridorBottom = fit.sy(-corridorHalfM)
    drawRect(
        color = GolfColors.RangeFairway,
        topLeft = Offset(fit.sx(RangeScene.FAIRWAY_TEE_Y), corridorTop),
        size = Size(
            fit.sx(RangeScene.FAIRWAY_END_Y) - fit.sx(RangeScene.FAIRWAY_TEE_Y),
            corridorBottom - corridorTop,
        ),
    )
    for (band in 0 until RangeScene.stripeBandCount()) {
        val (fromM, toM) = RangeScene.stripeBand(band)
        val lo = maxOf(fromM, fit.windowMinM)
        val hi = minOf(toM, fit.windowMaxM)
        if (hi <= lo) continue
        drawRect(
            color = if (RangeScene.stripeIsLight(0.0, fromM)) {
                GolfColors.RangeStripeLight
            } else {
                GolfColors.RangeStripeDark
            },
            topLeft = Offset(fit.sx(lo), corridorTop),
            size = Size(fit.sx(hi) - fit.sx(lo), corridorBottom - corridorTop),
        )
    }
    // Distance gridlines with labels, inside the window only.
    val gridPaint = android.graphics.Paint().apply {
        color = GolfColors.TextMuted.toArgb()
        textSize = 10.sp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    var gridM = ceil(fit.windowMinM / GRID_STEP_M) * GRID_STEP_M
    while (gridM <= fit.windowMaxM) {
        val x = fit.sx(gridM)
        drawLine(GolfColors.Line, Offset(x, 0f), Offset(x, size.height), 1f)
        drawContext.canvas.nativeCanvas.drawText(
            String.format(Locale.US, "%.0f", gridM),
            x,
            size.height - 4f,
            gridPaint,
        )
        gridM += GRID_STEP_M
    }

    // Rings under dots: minimum-enclosing ellipse per club, buffered 5 %.
    clubs.forEach { club ->
        val color = colors[club.clubName] ?: GolfColors.TextSecondary
        val ellipse = DispersionEllipse.fromKept(club.points) ?: return@forEach
        val path = Path()
        for (step in 0..RING_STEPS) {
            val t = 2.0 * PI * step / RING_STEPS
            val a = ellipse.semiAxisM * cos(t)
            val b = ellipse.semiCrossM * sin(t)
            val sideM = ellipse.centreSideM + a * cos(ellipse.angleRad) - b * sin(ellipse.angleRad)
            val distanceM = ellipse.centreTotalM + a * sin(ellipse.angleRad) + b * cos(ellipse.angleRad)
            val x = fit.sx(distanceM)
            val y = fit.sy(sideM)
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(width = RingKeylineWidth.toPx()))
        drawPath(path, color.copy(alpha = 0.7f), style = Stroke(width = RingColorWidth.toPx()))
    }
    // Dots on top.
    clubs.forEach { club ->
        val color = colors[club.clubName] ?: GolfColors.TextSecondary
        club.points.forEach { (lateralM, distanceM) ->
            val centre = Offset(fit.sx(distanceM), fit.sy(lateralM))
            drawCircle(Color.White.copy(alpha = 0.6f), DotRimRadius.toPx(), centre)
            drawCircle(color.copy(alpha = 0.9f), DotRadius.toPx(), centre)
        }
    }
}
```

- [ ] **Step 6: Compile and test**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.*"` then `.\gradlew.bat --console=plain :app:assembleDebug`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTopDownGeom.kt app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagTopDownView.kt app/src/test/kotlin/com/hpsmiles/golfsim/bag/BagTopDownGeomTest.kt
git commit -m "feat(bag): TOP-DOWN dispersion view with fitted rings, name-only legend and tappable dots"
```

Discard the original Task 9 code block entirely (its `SNAP_M`, `MIN_WINDOW_M`, `CORRIDOR_VISIBILITY_SCALE`, `LATERAL_CONTENT_MARGIN_M` and in-draw fit belong to `BagTopDownGeom.kt` now, and `RangeScene.stripeBand` takes a band *index* — it is not a colour; the stripe colour comes from `RangeScene.stripeIsLight(0.0, fromM)` selecting `GolfColors.RangeStripeLight` / `RangeStripeDark`).

## Correction 3 — Task 10: the strip must name its basis

In `BagResultsSummary`, the first item is basis-labelled (spec §8 says the strip follows the basis and says so). Replace:

```kotlin
        SummaryItem("AVG GAP", String.format(Locale.US, "%.1f m", summary.averageGapM), GolfColors.TextPrimary)
```

with:

```kotlin
        SummaryItem(
            "AVG GAP · ${basis.name}",
            String.format(Locale.US, "%.1f m", summary.averageGapM),
            GolfColors.TextPrimary,
        )
```

## Correction 4 — Task 7: `summarizeGaps` must exclude INVERTED gaps from the aggregates

`summarizeGaps` in `BagSummary.kt` originally computed `averageGapM`, `closest` and `farthest` over every `GapEntry`, but an inverted pair's `gapM` is `abs(shorter − longer)` — a small positive number for a club that carries *further* than the one above it — so a mislabelled club could win `CLOSEST` and print as a healthy tight gap while the DISTANCE GAPS block colours the same pair red.

Fix (Task 7, applied): `averageGapM` / `closest` / `farthest` are computed over `gaps.filterNot { it.flag == GapFlag.INVERTED }` only; the three counts (`goodCount` / `wideCount` / `tightCount`) still cover the full list (INVERTED contributes to none of them by construction). `BagSummaryTest` gains cases: a smallest `gapM` that is an INVERTED entry is skipped for `closest`, a largest `gapM` that is an INVERTED entry is skipped for `farthest`, the average excludes the inverted entry's magnitude, and an all-INVERTED list yields null aggregates with all three counts zero.

**Shared contract for the lanes that build the summary strip:** any consumer rendering `closest` / `farthest` must render the returned pair as given — it is the real smallest/largest non-inverted gap, so its `longerClub` / `shorterClub` / `gapM` / `bandLowM` / `bandHighM` are already authoritative — and must not re-derive or re-flag it.
