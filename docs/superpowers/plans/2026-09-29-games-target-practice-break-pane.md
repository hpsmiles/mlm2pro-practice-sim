# Games (Target Practice & Break the Pane, M5.5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two arcade-practice game modes — Target Practice (5 shots, points by rest-distance bands) and Break the Pane (9 breakable cells at 25% of target, score = shots to break all) — on the proven shot pipeline, with one Room summary row per completed game.

**Architecture:** New `com.hpsmiles.golfsim.games` package in `:app`. Pure game logic (scoring, pane geometry, reference trajectory, quad clipping) is JVM-tested; game state holders mirror `RangeSession` (plain Compose state holders, injectable clock + injectable simulator); AppRoot routes BLE/DEMO shots to the range OR the active game, never both. Physics prerequisites (rest position, green-zone surface, trajectory-plane crossing) land in `:core:physics` first. Room v2→v3 adds a `game_results` summary table.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas + PovProjector), Room, plain JUnit4 (no mocking framework), Robolectric only for the data-module migration test.

**Spec:** `docs/superpowers/specs/2026-09-29-games-target-practice-break-pane-design.md` (authoritative for all numbers).

## Global Constraints

- Windows: set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before ANY gradle command.
- Gradle flags precede task names; always use `--console=plain`. Quiet configure ≠ hung (5s–2min).
- `:core:physics` is a PURE Kotlin module — its test task is plain `test` (NOT `testDebugUnitTest`): `.\gradlew.bat :core:physics:test --console=plain`
- `:app` and `:core:data` unit tests use the typed variant: `.\gradlew.bat :app:testDebugUnitTest --console=plain --tests "..."` (aggregate `test` rejects `--tests`).
- Room schema is versioned: new `MIGRATION_2_3` in `Mlm2proDatabase`, committed schema JSON under `core/data/schemas/`, migration test. NEVER enable destructive migration fallback.
- Pure modules stay unit-testable without hardware: given inputs → same outputs. No Android imports in pure game logic (`PaneGeom`, `TargetPracticeScoring`, `PaneClip`, `ReferenceTrajectory`, `FlightCrossing`, `GreenZoneSurfaceProvider`).
- Single-device constraint: no cloud/PC dependencies.
- Amber (`GolfColors.Amber`) is reserved for the live moment only (tracer, landing dot) — never a category color. Teal = structure/targets.
- No ViewModels, no Navigation-Compose — plain `when(enum)` navigation + Compose state holders.
- Full build + all tests: `.\gradlew.bat build --console=plain`

---

### Task 1: Rest position on ShotResult (physics)

**Files:**
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ShotResult.kt`
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BallFlightEngine.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt` (`toDisplayShot` extension)
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/RestPositionTest.kt`

**Interfaces:**
- Produces: `ShotResult.restX: Double`, `ShotResult.restY: Double` (defaults `0.0` so existing constructors keep compiling). Rest = bounce/roll end position: `restX == sideM`, `hypot(restX, restY) == totalM`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Rest position is the bounce/roll end position — the scored position in both games. */
class RestPositionTest {

    private fun stock8i(): LaunchConditions = LaunchConditions(44.0, 21.0, 7500)

    @Test
    fun `rest position consistent with sideM and totalM`() {
        val result = BallFlightEngine.simulate(stock8i())
        assertEquals(result.sideM, result.restX, 1e-9)
        assertEquals(result.totalM, hypot(result.restX, result.restY), 1e-9)
        assertEquals(result.carryM + result.rolloutM, result.totalM, 1e-9)
    }

    @Test
    fun `rest stays within carry and total on fairway`() {
        val result = BallFlightEngine.simulate(stock8i())
        val restFromTee = hypot(result.restX, result.restY)
        assertTrue("rest $restFromTee must be >= carry ${result.carryM}", restFromTee >= result.carryM - 1e-9)
        assertTrue("rest $restFromTee must be <= total ${result.totalM}", restFromTee <= result.totalM + 1e-9)
    }

    @Test
    fun `engine rest matches spec 140m table - stock 8i lands about 20m short of pin`() {
        // Spec §2 calc table: 44 m/s / 21° / 7500 rpm → carry 118.1, rest ~20.5 m from a 140 m pin.
        val surfaces = GreenZoneSurfaceProvider(centerX = 0.0, centerY = 140.0, radiusM = 6.0)
        val result = BallFlightEngine.simulate(stock8i(), Environment(), surfaces)
        assertEquals(118.1, result.carryM, 0.5)
        val restFromPin = hypot(result.restX, result.restY - 140.0)
        assertEquals(20.5, restFromPin, 1.0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:physics:test --console=plain`
Expected: FAIL — `restX`/`restY` unresolved; `GreenZoneSurfaceProvider` unresolved.

- [ ] **Step 3: Write minimal implementation**

`ShotResult.kt` — add two fields with defaults:

```kotlin
data class ShotResult(
    val carryM: Double,
    val rolloutM: Double,
    val totalM: Double,
    val sideM: Double,
    val apexM: Double,
    val flightTimeSec: Double,
    val samples: List<TrajectorySample> = emptyList(),
    val restX: Double = 0.0,
    val restY: Double = 0.0,
)
```

Doc comment addition on the class: `restX/restY = bounce/roll end position (restX == sideM, hypot(restX, restY) == totalM).`

`BallFlightEngine.simulate` — add the two fields to the returned `ShotResult`:

```kotlin
        return ShotResult(
            carryM = carryM,
            rolloutM = totalM - carryM,
            totalM = totalM,
            sideM = endX,
            apexM = landing.apexM,
            flightTimeSec = landing.flightTimeSec,
            samples = landing.samples,
            restX = endX,
            restY = endY,
        )
```

`RangeSession.kt` `toDisplayShot()` (restored scalar shots have no bounce/roll replay — reconstruct rest from cached scalars):

```kotlin
    val result = ShotResult(
        carryM = carryM,
        rolloutM = totalM - carryM,
        totalM = totalM,
        sideM = sideM,
        apexM = apexM,
        flightTimeSec = flightTimeSec,
        restX = sideM,
        restY = sqrt(totalM * totalM - sideM * sideM),
    )
```

(add `import kotlin.math.sqrt`).

- [ ] **Step 4: Run tests to verify they pass (after Task 2)**

Task 1's third test needs `GreenZoneSurfaceProvider` (Task 2). Implement Tasks 1 and 2, then run:

Run: `.\gradlew.bat :core:physics:test --console=plain`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ShotResult.kt core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BallFlightEngine.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/RestPositionTest.kt
git commit -m "feat(physics): expose rest position on ShotResult (games prerequisite)"
```

---

### Task 2: GreenZoneSurfaceProvider (physics)

**Files:**
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/GreenZoneSurfaceProvider.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/GreenZoneSurfaceProviderTest.kt`

**Interfaces:**
- Produces: `GreenZoneSurfaceProvider(centerX: Double, centerY: Double, radiusM: Double, green: Surface = Surface.GREEN_NORMAL, fairway: Surface = Surface.FAIRWAY_NORMAL) : SurfaceProvider` — GREEN inside/on the oval (inclusive boundary), FAIRWAY outside.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Test

class GreenZoneSurfaceProviderTest {

    private val provider = GreenZoneSurfaceProvider(centerX = 0.0, centerY = 140.0, radiusM = 6.0)

    @Test
    fun `inside oval is green`() {
        assertEquals(Surface.GREEN_NORMAL, provider.surfaceAt(0.0, 140.0))
        assertEquals(Surface.GREEN_NORMAL, provider.surfaceAt(3.0, 142.0))
    }

    @Test
    fun `oval boundary is green - inclusive`() {
        assertEquals(Surface.GREEN_NORMAL, provider.surfaceAt(0.0, 146.0))
    }

    @Test
    fun `outside oval is fairway`() {
        assertEquals(Surface.FAIRWAY_NORMAL, provider.surfaceAt(0.0, 150.0))
        assertEquals(Surface.FAIRWAY_NORMAL, provider.surfaceAt(10.0, 140.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:physics:test --console=plain`
Expected: FAIL — `GreenZoneSurfaceProvider` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * Green oval centred on (centerX, centerY) — games simulate with the ball
 * rolling on the green when it lands there, fairway otherwise (spec §6.2).
 * Generalizes [ZoneTable]'s down-range bands to a real landing oval.
 */
class GreenZoneSurfaceProvider(
    private val centerX: Double,
    private val centerY: Double,
    private val radiusM: Double,
    private val green: Surface = Surface.GREEN_NORMAL,
    private val fairway: Surface = Surface.FAIRWAY_NORMAL,
) : SurfaceProvider {
    override fun surfaceAt(x: Double, y: Double): Surface {
        val dx = x - centerX
        val dy = y - centerY
        return if (dx * dx + dy * dy <= radiusM * radiusM) green else fairway
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :core:physics:test --console=plain`
Expected: PASS — including Task 1's third test now.

- [ ] **Step 5: Commit**

```bash
git add core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/GreenZoneSurfaceProvider.kt core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/GreenZoneSurfaceProviderTest.kt
git commit -m "feat(physics): GreenZoneSurfaceProvider for game landing zones"
```

---

### Task 3: FlightCrossing — trajectory vs plane (physics)

**Files:**
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/FlightCrossing.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/FlightCrossingTest.kt`

**Interfaces:**
- Produces: `FlightCrossing.firstForwardCrossing(samples: List<TrajectorySample>, planeY: Double): Pair<Double, Double>?` — the first py-INCREASING crossing of the vertical plane y = planeY, linearly interpolated; returns `(x, z)` or null.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightCrossingTest {

    @Test
    fun `interpolates first forward crossing`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.5, 30.0, 8.0, 1.0),
            TrajectorySample(0.8, 40.0, 14.0, 2.0),
        )
        val crossing = FlightCrossing.firstForwardCrossing(samples, planeY = 35.0)!!
        assertEquals(0.65, crossing.first, 1e-9)  // 0.5 + 0.5*(0.8-0.5)
        assertEquals(11.0, crossing.second, 1e-9) // 8 + 0.5*(14-8)
    }

    @Test
    fun `backward crossing ignored`() {
        // Ball starts beyond the plane and comes back through it — not forward.
        val samples = listOf(
            TrajectorySample(0.0, 40.0, 10.0, 0.0),
            TrajectorySample(0.0, 30.0, 5.0, 1.0),
        )
        assertNull(FlightCrossing.firstForwardCrossing(samples, planeY = 35.0))
    }

    @Test
    fun `no crossing returns null`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.0, 10.0, 2.0, 1.0),
        )
        assertNull(FlightCrossing.firstForwardCrossing(samples, planeY = 35.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:physics:test --console=plain`
Expected: FAIL — `FlightCrossing` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * Where a flight crosses a vertical plane y = constant (pure, deterministic).
 * Used by ReferenceTrajectory (physics) and PaneGeom (app) — one definition,
 * shared, so both games agree on the crossing math.
 */
object FlightCrossing {

    /**
     * First py-INCREASING crossing of y = [planeY], linearly interpolated
     * between consecutive samples. Returns (x, z), or null when the flight
     * never crosses the plane forward.
     */
    fun firstForwardCrossing(
        samples: List<TrajectorySample>,
        planeY: Double,
    ): Pair<Double, Double>? {
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            if (a.py < planeY && b.py >= planeY) {
                val t = (planeY - a.py) / (b.py - a.py)
                return (a.px + (b.px - a.px) * t) to (a.pz + (b.pz - a.pz) * t)
            }
        }
        return null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:physics:test --console=plain`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/FlightCrossing.kt core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/FlightCrossingTest.kt
git commit -m "feat(physics): FlightCrossing plane-crossing helper"
```

---

### Task 4: ReferenceTrajectory — stock-shot solver (physics)

**Files:**
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ReferenceTrajectory.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/ReferenceTrajectoryTest.kt`

**Interfaces:**
- Produces:
  - `ReferenceTrajectory.stockShot(targetM: Double, toleranceM: Double = 0.5): ShotResult` — deterministic stock shot (21°, 7500 rpm, flat fairway) whose carry ≈ target.
  - `ReferenceTrajectory.paneCrossingHeightM(targetM: Double, planeFraction: Double = 0.25): Double` — the z at which that stock shot crosses the pane plane.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceTrajectoryTest {

    @Test
    fun `converges for every spec distance bracket`() {
        for (target in listOf(50.0, 100.0, 140.0, 200.0, 300.0)) {
            val shot = ReferenceTrajectory.stockShot(target)
            assertEquals("target $target", target, shot.carryM, 1.0)
        }
    }

    @Test
    fun `deterministic`() {
        val a = ReferenceTrajectory.stockShot(140.0)
        val b = ReferenceTrajectory.stockShot(140.0)
        assertEquals(a.carryM, b.carryM, 0.0)
        assertEquals(a.samples, b.samples)
    }

    @Test
    fun `pane crossing height inside reachable band for 140m`() {
        // Spec §2: 140 m target → crossing band ~10.3–15.0 m, mid row ≈ 12.6 m.
        val z = ReferenceTrajectory.paneCrossingHeightM(140.0)
        assertTrue("zRef $z outside [10, 16]", z in 10.0..16.0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:physics:test --console=plain`
Expected: FAIL — `ReferenceTrajectory` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * Solves a stock reference shot for a target distance by binary search on
 * ball speed (carry is monotonic in ball speed). Pure + deterministic —
 * anchors the Break-the-Pane middle row so all three rows stay reachable
 * by flighting (spec §5 geometry, engine-verified calcs in §2).
 */
object ReferenceTrajectory {

    private const val REF_LAUNCH_ANGLE_DEG = 21.0
    private const val REF_SPIN_RPM = 7500
    const val SPEED_MIN_MPS = 20.0
    const val SPEED_MAX_MPS = 95.0

    /** Stock shot whose carry is closest to [targetM]. 60 iterations cap the ODE work. */
    fun stockShot(targetM: Double, toleranceM: Double = 0.5): ShotResult {
        var lo = SPEED_MIN_MPS
        var hi = SPEED_MAX_MPS
        var best = simulate(lo)
        var bestErr = kotlin.math.abs(best.carryM - targetM)
        repeat(60) {
            val mid = (lo + hi) / 2.0
            val shot = simulate(mid)
            val err = kotlin.math.abs(shot.carryM - targetM)
            if (err < bestErr) {
                bestErr = err
                best = shot
            }
            if (err <= toleranceM) return shot
            if (shot.carryM < targetM) lo = mid else hi = mid
        }
        return best
    }

    /** z height where the stock shot for [targetM] crosses the pane plane (y = fraction × target). */
    fun paneCrossingHeightM(targetM: Double, planeFraction: Double = 0.25): Double {
        val shot = stockShot(targetM)
        val planeY = targetM * planeFraction
        return FlightCrossing.firstForwardCrossing(shot.samples, planeY)?.second
            ?: error("reference trajectory never crosses the pane plane at y=$planeY")
    }

    private fun simulate(ballSpeedMps: Double): ShotResult =
        BallFlightEngine.simulate(
            LaunchConditions(
                ballSpeedMps = ballSpeedMps,
                launchAngleDeg = REF_LAUNCH_ANGLE_DEG,
                spinRpm = REF_SPIN_RPM,
            ),
            Environment(),
            UniformSurface(Surface.FAIRWAY_NORMAL),
        )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:physics:test --console=plain`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ReferenceTrajectory.kt core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/ReferenceTrajectoryTest.kt
git commit -m "feat(physics): ReferenceTrajectory stock-shot solver for pane anchoring"
```

---

### Task 5: PaneGeom — cell geometry + crossing (app, pure)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneGeom.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneGeomTest.kt`

**Interfaces:**
- Consumes: `FlightCrossing.firstForwardCrossing`, `ReferenceTrajectory.paneCrossingHeightM` (Tasks 3/4).
- Produces:
  - `class PaneGeom(val targetM: Double)` with `planeYM` (25% target), `cellHM` (2% target), `cellWM` (4% target), `zRefM`, `bottomZM` (zRef − 1.5×cellH), `topZM`, `leftXM` (−1.5×cellW), `rightXM`.
  - `fun cellAt(xM: Double, zM: Double): Int?` — cell index 0..8 (row bottom-up × 3 + col left-right), null outside the pane. Boundary inclusive upward (a z exactly on a row top belongs to the row above; an x exactly on a col edge belongs to the col right of it — floor semantics).
  - `data class PaneCrossing(xM: Double, zM: Double, cell: Int?)`; `fun firstCrossing(samples: List<TrajectorySample>): PaneCrossing?` — first forward plane crossing, even outside the pane extent (cell null → caller derives under/over/left/right feedback).
  - `fun cellCorners(cell: Int): List<Triple<Double, Double, Double>>` — 4 (x, y, z) world corners at the pane plane, order BL→BR→TR→TL (rendering).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.physics.TrajectorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaneGeomTest {

    private val pane = PaneGeom(140.0)
    // 140 m: plane y=35, cellH=2.8, cellW=5.6, bottom≈8.4 (zRef≈12.6 from engine)

    @Test
    fun `geometry scales with target`() {
        assertEquals(35.0, pane.planeYM, 1e-9)
        assertEquals(2.8, pane.cellHM, 1e-9)
        assertEquals(5.6, pane.cellWM, 1e-9)
    }

    @Test
    fun `row boundaries inclusive upward`() {
        assertEquals(0, pane.cellAt(0.0, pane.bottomZM))                   // exact bottom
        assertEquals(1, pane.cellAt(0.0, pane.bottomZM + pane.cellHM))     // on row 0's top
        assertEquals(2, pane.cellAt(0.0, pane.bottomZM + 2 * pane.cellHM)) // on row 1's top
        assertNull(pane.cellAt(0.0, pane.bottomZM + 3 * pane.cellHM))      // exact top → outside
    }

    @Test
    fun `col boundaries inclusive rightward - aim line is middle col`() {
        assertEquals(1, pane.cellAt(0.0, pane.zRefM))
        assertEquals(0, pane.cellAt(pane.leftXM, pane.zRefM))              // exact left edge
        assertNull(pane.cellAt(pane.rightXM, pane.zRefM))                  // exact right edge → outside
    }

    @Test
    fun `reference crossing breaks middle cell`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.0, pane.planeYM, pane.zRefM, 1.0),
            TrajectorySample(0.0, 140.0, 0.0, 2.0),
        )
        val crossing = pane.firstCrossing(samples)!!
        assertEquals(4, crossing.cell) // middle row, middle col
    }

    @Test
    fun `crossing below pane reports point with null cell`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.0, 35.0, 2.0, 1.0),
            TrajectorySample(0.0, 100.0, 0.0, 2.0),
        )
        val crossing = pane.firstCrossing(samples)!!
        assertEquals(2.0, crossing.zM, 1e-9)
        assertNull(crossing.cell)
    }

    @Test
    fun `first forward crossing wins when samples dip through twice`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.0, 35.0, 10.0, 1.0), // bottom row, middle col (index 1)
            TrajectorySample(0.0, 36.0, 16.0, 1.1), // rises steeply above the plane
            TrajectorySample(0.0, 37.0, 10.0, 1.2), // back down — NOT a forward crossing
        )
        val crossing = pane.firstCrossing(samples)!!
        assertEquals(1, crossing.cell)
    }

    @Test
    fun `cell corners span the cell`() {
        val corners = pane.cellCorners(4) // middle
        assertEquals(4, corners.size)
        val (blx, bly, blz) = corners[0]
        assertEquals(pane.planeYM, bly, 1e-9)
        assertEquals(pane.bottomZM + pane.cellHM, blz, 1e-9) // middle row bottom = zRef − cellH/2
        assertEquals(-pane.cellWM / 2.0, blx, 1e-9)
        val (_, _, trz) = corners[2]
        assertEquals(pane.zRefM + pane.cellHM / 2.0, trz, 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.games.PaneGeomTest"`
Expected: FAIL — `PaneGeom` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.physics.FlightCrossing
import com.hpsmiles.golfsim.core.physics.ReferenceTrajectory
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import kotlin.math.floor

/**
 * Break-the-Pane geometry, pure config + crossing math (spec §5).
 * Pane floats at y = 25% of target, centred on the aim line. Cells are
 * 2% of target tall / 4% wide; the middle row is centred on the
 * ReferenceTrajectory crossing height so all rows stay reachable by flighting.
 *
 * Cell indices: row (bottom-up) * 3 + col (left-right) → 0..8, middle = 4.
 * Boundary rule: inclusive upward — a point exactly on a row top belongs to
 * the row above; exactly on a col edge belongs to the col right of it
 * (floor semantics on the cell-local coordinate).
 */
class PaneGeom(val targetM: Double) {

    val planeYM: Double = 0.25 * targetM
    val cellHM: Double = 0.02 * targetM
    val cellWM: Double = 0.04 * targetM
    val zRefM: Double = ReferenceTrajectory.paneCrossingHeightM(targetM)
    val bottomZM: Double = zRefM - 1.5 * cellHM
    val topZM: Double = bottomZM + 3 * cellHM
    val leftXM: Double = -1.5 * cellWM
    val rightXM: Double = 1.5 * cellWM

    /** Cell index for a crossing point, or null when outside the pane. */
    fun cellAt(xM: Double, zM: Double): Int? {
        val col = floor((xM - leftXM) / cellWM).toInt()
        val row = floor((zM - bottomZM) / cellHM).toInt()
        if (col !in 0..2 || row !in 0..2) return null
        return row * 3 + col
    }

    /**
     * First forward crossing of the pane plane — reported even when outside
     * the pane ([PaneCrossing.cell] null) so the caller can say under/over/left/right.
     */
    fun firstCrossing(samples: List<TrajectorySample>): PaneCrossing? =
        FlightCrossing.firstForwardCrossing(samples, planeYM)?.let { (x, z) ->
            PaneCrossing(x, z, cellAt(x, z))
        }

    /** 4 world corners (x, y=plane, z), order BL → BR → TR → TL. */
    fun cellCorners(cell: Int): List<Triple<Double, Double, Double>> {
        val row = cell / 3
        val col = cell % 3
        val xL = leftXM + col * cellWM
        val zB = bottomZM + row * cellHM
        val zT = zB + cellHM
        return listOf(
            Triple(xL, planeYM, zB),
            Triple(xL + cellWM, planeYM, zB),
            Triple(xL + cellWM, planeYM, zT),
            Triple(xL, planeYM, zT),
        )
    }
}

data class PaneCrossing(val xM: Double, val zM: Double, val cell: Int?)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.games.PaneGeomTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneGeom.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneGeomTest.kt
git commit -m "feat(games): PaneGeom cell geometry and pane-plane crossing"
```

---

### Task 6: TargetPracticeScoring — bands (app, pure)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticeScoring.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/TargetPracticeScoringTest.kt`

**Interfaces:**
- Produces:
  - `enum class Difficulty { EASY, MEDIUM, HARD }`
  - `object TargetPracticeScoring` with `const val SHOTS_PER_GAME = 5`, `const val MAX_SCORE = 125`, `data class Band(val points: Int, val maxMissM: Double)`, `fun bands(difficulty: Difficulty): List<Band>`, `fun points(missM: Double, difficulty: Difficulty): Int`, `fun missDistanceM(restX: Double, restY: Double, targetM: Double): Double`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.games

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetPracticeScoringTest {

    @Test
    fun `medium bands per spec table`() {
        val bands = TargetPracticeScoring.bands(Difficulty.MEDIUM)
        assertEquals(listOf(25 to 2.0, 15 to 5.0, 10 to 10.0, 5 to 15.0), bands.map { it.points to it.maxMissM })
    }

    @Test
    fun `band edges are inclusive`() {
        assertEquals(25, TargetPracticeScoring.points(2.0, Difficulty.MEDIUM))
        assertEquals(15, TargetPracticeScoring.points(2.0001, Difficulty.MEDIUM))
        assertEquals(15, TargetPracticeScoring.points(5.0, Difficulty.MEDIUM))
        assertEquals(5, TargetPracticeScoring.points(15.0, Difficulty.MEDIUM))
        assertEquals(0, TargetPracticeScoring.points(15.0001, Difficulty.MEDIUM))
    }

    @Test
    fun `easy and hard inner bands per spec table`() {
        assertEquals(3.0, TargetPracticeScoring.bands(Difficulty.EASY).first().maxMissM, 0.0)
        assertEquals(1.5, TargetPracticeScoring.bands(Difficulty.HARD).first().maxMissM, 0.0)
        assertEquals(10.0, TargetPracticeScoring.bands(Difficulty.HARD).last().maxMissM, 0.0)
    }

    @Test
    fun `miss distance from rest position`() {
        assertEquals(2.0, TargetPracticeScoring.missDistanceM(restX = 0.0, restY = 142.0, targetM = 140.0), 1e-9)
        assertEquals(5.0, TargetPracticeScoring.missDistanceM(restX = 3.0, restY = 140.0, targetM = 140.0), 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.games.TargetPracticeScoringTest"`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hpsmiles.golfsim.games

import kotlin.math.hypot

enum class Difficulty { EASY, MEDIUM, HARD }

/**
 * Target Practice scoring bands (spec §4 table). Miss distance is measured
 * from the REST position (after bounce/roll), not carry.
 */
object TargetPracticeScoring {

    const val SHOTS_PER_GAME = 5
    const val MAX_SCORE = 125

    /** Points for landing within maxMissM of the pin; bands are inclusive. */
    data class Band(val points: Int, val maxMissM: Double)

    fun bands(difficulty: Difficulty): List<Band> = when (difficulty) {
        Difficulty.EASY -> listOf(Band(25, 3.0), Band(15, 7.0), Band(10, 12.0), Band(5, 18.0))
        Difficulty.MEDIUM -> listOf(Band(25, 2.0), Band(15, 5.0), Band(10, 10.0), Band(5, 15.0))
        Difficulty.HARD -> listOf(Band(25, 1.5), Band(15, 3.5), Band(10, 6.0), Band(5, 10.0))
    }

    fun points(missM: Double, difficulty: Difficulty): Int =
        bands(difficulty).firstOrNull { missM <= it.maxMissM }?.points ?: 0

    fun missDistanceM(restX: Double, restY: Double, targetM: Double): Double =
        hypot(restX, restY - targetM)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.games.TargetPracticeScoringTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticeScoring.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/TargetPracticeScoringTest.kt
git commit -m "feat(games): Target Practice scoring bands"
```

---

### Task 7: Room v3 — game_results table

**Files:**
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/GameResultEntity.kt`
- Create: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/GameResultDao.kt`
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabase.kt` (version 3, entity, DAO, `MIGRATION_2_3`)
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt` (DAO accessor, `saveGameResult`, migration registration)
- Test: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/MigrationFrom2Test.kt`
- Schema: commit generated `core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/3.json`

**Interfaces:**
- Produces:
  - `GameResultEntity(id, mode: String, difficulty: String?, distanceBin: Int, score: Int, source: Int, playedAtEpochMs: Long)`; `GameModes.TARGET_PRACTICE = "TARGET_PRACTICE"`, `GameModes.BREAK_PANE = "BREAK_PANE"`.
  - `GameResultDao.insert(result): Long`, `GameResultDao.getAll(): List<GameResultEntity>`.
  - `SessionRepository.saveGameResult(mode: String, difficulty: String?, distanceM: Double, score: Int, source: ShotSource, playedAtEpochMs: Long)` — never throws, flags `persistError` (same contract as `appendShot`); `distanceBin = round(distanceM / 10.0) * 10`.

- [ ] **Step 1: Write the failing test**

Mirror `MigrationFrom1Test` (raw SQLite v2 file built by hand, identity hash from `core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/2.json` = `94014c8b5c8f2a1fda6d5bef72244606`). The v2 DDL below is the v1 DDL from `MigrationFrom1Test` plus the four v2 columns (`clubs.type`, `clubs.isTemp`, `shots.excluded`, `shots.clubWasTemp`) — verify column names/defaults against `2.json` before running.

```kotlin
package com.hpsmiles.golfsim.core.data

import android.database.sqlite.SQLiteDatabase
import com.hpsmiles.golfsim.core.data.record.ShotSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * MIGRATION_2_3 against a hand-built v2 file (same pattern as
 * MigrationFrom1Test — no MigrationTestHelper in this repo). After opening,
 * a game result can be saved and read back through Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MigrationFrom2Test {

    @Test
    fun `v2 file migrates - game_results usable - prior data intact`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("golfsim.db")
        dbFile.parentFile!!.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        // v2 DDL (sessions, shots, clubs) — mirrors schemas/…/2.json
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
        raw.version = 2
        raw.close()

        val repo = SessionRepository.open(context)
        repo.initializeAndRestore() // migration runs here; prior data must survive

        repo.saveGameResult(
            mode = GameModes.TARGET_PRACTICE,
            difficulty = "MEDIUM",
            distanceM = 143.0,
            score = 85,
            source = ShotSource.LIVE,
            playedAtEpochMs = 2000,
        )
        repo.saveGameResult(
            mode = GameModes.BREAK_PANE,
            difficulty = null,
            distanceM = 137.0,
            score = 12,
            source = ShotSource.DEMO,
            playedAtEpochMs = 3000,
        )

        val all = repo.gameResultDao().getAll()
        assertEquals(2, all.size)
        val tp = all.first { it.mode == GameModes.TARGET_PRACTICE }
        assertEquals("MEDIUM", tp.difficulty)
        assertEquals(140, tp.distanceBin) // 143 rounds to nearest 10
        assertEquals(85, tp.score)
        assertEquals(0, tp.source)
        val bp = all.first { it.mode == GameModes.BREAK_PANE }
        assertEquals(null, bp.difficulty)
        assertEquals(140, bp.distanceBin) // 137 rounds to 140
        assertEquals(12, bp.score)
        assertEquals(1, bp.source)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:data:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.core.data.MigrationFrom2Test"`
Expected: FAIL — `saveGameResult`/`GameModes`/`gameResultDao` unresolved.

- [ ] **Step 3: Write implementation**

`GameResultEntity.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Game mode discriminator for [GameResultEntity]. */
object GameModes {
    const val TARGET_PRACTICE = "TARGET_PRACTICE"
    const val BREAK_PANE = "BREAK_PANE"
}

/**
 * One summary row per completed game (spec §7). No per-shot game rows —
 * game shots never enter the range session pipeline. `score` means points
 * (max 125) for TARGET_PRACTICE and shots-taken (lower is better) for BREAK_PANE.
 */
@Entity(tableName = "game_results")
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    /** EASY/MEDIUM/HARD; null for Break the Pane. */
    val difficulty: String?,
    /** Target distance rounded to the nearest 10 m. */
    val distanceBin: Int,
    val score: Int,
    /** Mirrors ShotSource: LIVE=0, DEMO=1. */
    val source: Int,
    val playedAtEpochMs: Long,
)
```

`GameResultDao.kt`:

```kotlin
package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.GameResultEntity

@Dao
interface GameResultDao {

    @Insert
    suspend fun insert(result: GameResultEntity): Long

    @Query("SELECT * FROM game_results ORDER BY playedAtEpochMs DESC, id DESC")
    suspend fun getAll(): List<GameResultEntity>
}
```

`Mlm2proDatabase.kt` — version bump, entity, DAO, migration (keep `MIGRATION_1_2` unchanged):

```kotlin
@Database(
    entities = [SessionEntity::class, ShotEntity::class, ClubEntity::class, GameResultEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class Mlm2proDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun shotDao(): ShotDao
    abstract fun clubDao(): ClubDao
    abstract fun gameResultDao(): GameResultDao

    companion object {
        // … MIGRATION_1_2 unchanged …

        /**
         * M5.5 spec §7: game_results summary table. No DEFAULT clauses —
         * difficulty is genuinely nullable and the rest are NOT NULL, so the
         * entity needs no @ColumnInfo defaultValue (there is nothing to match).
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `game_results` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`mode` TEXT NOT NULL, `difficulty` TEXT, `distanceBin` INTEGER NOT NULL, " +
                        "`score` INTEGER NOT NULL, `source` INTEGER NOT NULL, " +
                        "`playedAtEpochMs` INTEGER NOT NULL)",
                )
            }
        }
    }
}
```

(add imports for `GameResultEntity`, `GameResultDao`).

`SessionRepository.kt` — accessor (next to the other DAO fields), method, and registration:

```kotlin
    private val gameResultDao get() = db.gameResultDao()
```

```kotlin
    /**
     * One summary row per completed game (M5.5 spec §7). Never throws —
     * same contract as [appendShot]. Game shots themselves are never persisted.
     */
    suspend fun saveGameResult(
        mode: String,
        difficulty: String?,
        distanceM: Double,
        score: Int,
        source: ShotSource,
        playedAtEpochMs: Long,
    ) {
        try {
            gameResultDao.insert(
                GameResultEntity(
                    mode = mode,
                    difficulty = difficulty,
                    distanceBin = (Math.round(distanceM / 10.0) * 10).toInt(),
                    score = score,
                    source = source.code,
                    playedAtEpochMs = playedAtEpochMs,
                ),
            )
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "saveGameResult failed", t)
            persistError.value = true
        }
    }
```

```kotlin
    private fun build(): Mlm2proDatabase =
        Room.databaseBuilder(context, Mlm2proDatabase::class.java, DB_NAME)
            .addMigrations(Mlm2proDatabase.MIGRATION_1_2, Mlm2proDatabase.MIGRATION_2_3)
            .build()
```

(add imports `GameResultEntity`; verify `ShotSource.code` is the existing int property name in `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/record/ShotSource.kt`).

- [ ] **Step 4: Run test + verify schema JSON**

Run: `.\gradlew.bat :core:data:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.core.data.MigrationFrom2Test"`
Expected: PASS

Then run the full data suite `.\gradlew.bat :core:data:testDebugUnitTest --console=plain` and verify `core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/3.json` was generated (`git status` shows it untracked). If Room's exported `3.json` disagrees with the migration DDL (e.g. nullability of `difficulty`), fix until they match exactly.

- [ ] **Step 5: Commit (including the schema JSON)**

```bash
git add core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/GameResultEntity.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/GameResultDao.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/Mlm2proDatabase.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/MigrationFrom2Test.kt core/data/schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/3.json
git commit -m "feat(data): Room v3 game_results table + saveGameResult (M5.5)"
```

---

### Task 8: TargetPracticeGame state holder (app)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GameResultPayload.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticeGame.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/TargetPracticeGameTest.kt`

**Interfaces:**
- Consumes: `TargetPracticeScoring`, `Difficulty`, `GreenZoneSurfaceProvider`, `BallFlightEngine`, `GameModes`.
- Produces:
  - `data class GameResultPayload(mode: String, difficulty: String?, targetM: Double, score: Int)`
  - `data class GameShot(shot: DisplayShot, missM: Double, points: Int)`
  - `class TargetPracticeGame`:
    - `val shots: SnapshotStateList<GameShot>`, `val tick: MutableIntState` (bumps per accepted shot — animation reset signal)
    - `val targetM: Double`, `val difficulty: Difficulty` (setup state, set via `start`)
    - `var clockMs: () -> Long` (injectable, default `System.currentTimeMillis()`)
    - `var simulator: (LaunchConditions) -> ShotResult` — injectable seam; default simulates with `GreenZoneSurfaceProvider(0.0, targetM, greenRadiusM())`. Tests inject synthetic results; production never touches it.
    - `fun start(targetM: Double, difficulty: Difficulty)`, `fun add(ballData: BallData): GameShot?` (null on guard violation or completed game), `fun greenRadiusM(): Double` (= 6.0 × target/140), `val complete: Boolean`, `val totalPoints: Int`, `fun takeResult(): GameResultPayload?` (non-null exactly once once complete).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetPracticeGameTest {

    /** Injects a synthetic rest position so scoring is deterministic on the JVM. */
    private fun gameWithRest(restX: Double, restY: Double): TargetPracticeGame {
        val game = TargetPracticeGame()
        game.simulator = { _ ->
            ShotResult(
                carryM = 138.0, rolloutM = 2.0, totalM = 140.0, sideM = restX,
                apexM = 25.0, flightTimeSec = 6.0,
                samples = emptyList(),
                restX = restX, restY = restY,
            )
        }
        game.start(140.0, Difficulty.MEDIUM)
        return game
    }

    private fun ball() = BallData(33.0, 44.0, 0.0, 21.0, 0.0, 7500, 5, 10)

    @Test
    fun `five accepted shots complete the game with summed points`() {
        val game = gameWithRest(restX = 0.0, restY = 141.5) // 1.5 m miss → 25 pts (MEDIUM)
        repeat(5) { assertTrue(game.add(ball()) != null) }
        assertTrue(game.complete)
        assertEquals(5, game.shots.size)
        assertEquals(125, game.totalPoints)
    }

    @Test
    fun `add after completion returns null`() {
        val game = gameWithRest(0.0, 141.5)
        repeat(5) { game.add(ball()) }
        assertNull(game.add(ball()))
        assertEquals(5, game.shots.size)
    }

    @Test
    fun `guard-violating ball data is ignored`() {
        val game = gameWithRest(0.0, 141.5)
        // ball speed 0.1 m/s violates LaunchConditions require(0.5..100)
        assertNull(game.add(BallData(1.0, 0.1, 0.0, 21.0, 0.0, 7500, 5, 10)))
        assertEquals(0, game.shots.size)
    }

    @Test
    fun `miss distance uses rest position not carry`() {
        val game = gameWithRest(restX = 3.0, restY = 145.0) // miss = 5.0 → 15 pts (MEDIUM)
        game.add(ball())
        assertEquals(15, game.shots.single().points)
        assertEquals(5.0, game.shots.single().missM, 1e-9)
    }

    @Test
    fun `takeResult returns exactly once then null`() {
        val game = gameWithRest(0.0, 141.5)
        repeat(5) { game.add(ball()) }
        val first = game.takeResult()
        assertEquals(GameModes.TARGET_PRACTICE, first!!.mode)
        assertEquals("MEDIUM", first.difficulty)
        assertEquals(140.0, first.targetM, 1e-9)
        assertEquals(125, first.score)
        assertNull(game.takeResult())
        // PLAY AGAIN resets everything
        game.start(140.0, Difficulty.MEDIUM)
        assertFalse(game.complete)
        assertEquals(0, game.shots.size)
        assertNull(game.takeResult())
    }

    @Test
    fun `green radius scales with target`() {
        val game = TargetPracticeGame()
        game.start(280.0, Difficulty.MEDIUM)
        assertEquals(12.0, game.greenRadiusM(), 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.games.TargetPracticeGameTest"`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write implementation**

`GameResultPayload.kt`:

```kotlin
package com.hpsmiles.golfsim.games

/** Summary handed to SessionRepository.saveGameResult once per completed game. */
data class GameResultPayload(
    val mode: String,
    val difficulty: String?,
    val targetM: Double,
    val score: Int,
)
```

`TargetPracticeGame.kt`:

```kotlin
package com.hpsmiles.golfsim.games

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.GreenZoneSurfaceProvider
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.range.DisplayShot

/**
 * Target Practice state holder (RangeSession pattern: plain Compose state,
 * injectable clock — spec §6). 5 shots, points by rest-distance bands.
 * `simulator` is an injectable seam so JVM tests pin scoring without the ODE;
 * the default wires the real engine with the green-zone surface provider.
 */
class TargetPracticeGame {

    val shots: SnapshotStateList<GameShot> = mutableStateListOf()
    val tick: MutableIntState = mutableIntStateOf(0)

    /** Injectable clock (ms). AppRoot can leave the default. */
    var clockMs: () -> Long = { System.currentTimeMillis() }

    /**
     * Injectable simulation seam. The default closure reads the CURRENT
     * targetM/green radius so start() re-targets it.
     */
    var simulator: (LaunchConditions) -> ShotResult = { launch ->
        BallFlightEngine.simulate(
            launch,
            Environment(),
            GreenZoneSurfaceProvider(0.0, targetM, greenRadiusM()),
        )
    }

    var targetM: Double = 140.0
        private set
    var difficulty: Difficulty = Difficulty.MEDIUM
        private set
    private var resultTaken = false

    fun start(targetM: Double, difficulty: Difficulty) {
        this.targetM = targetM
        this.difficulty = difficulty
        shots.clear()
        resultTaken = false
    }

    val complete: Boolean get() = shots.size >= TargetPracticeScoring.SHOTS_PER_GAME
    val totalPoints: Int get() = shots.sumOf { it.points }

    /** Green oval radius, scaled to the target (spec: ~6 m at 140 m). */
    fun greenRadiusM(): Double = 6.0 * targetM / 140.0

    /** One decoded measurement → scored shot. Null on guard violation or completed game. */
    fun add(ballData: BallData): GameShot? {
        if (complete) return null
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
        val miss = TargetPracticeScoring.missDistanceM(result.restX, result.restY, targetM)
        val pts = TargetPracticeScoring.points(miss, difficulty)
        val gameShot = GameShot(DisplayShot(ballData, launch, result, clockMs()), miss, pts)
        shots.add(gameShot)
        tick.intValue++
        return gameShot
    }

    /** Non-null exactly once once [complete]; the summary AppRoot persists. */
    fun takeResult(): GameResultPayload? {
        if (!complete || resultTaken) return null
        resultTaken = true
        return GameResultPayload(GameModes.TARGET_PRACTICE, difficulty.name, targetM, totalPoints)
    }
}

data class GameShot(val shot: DisplayShot, val missM: Double, val points: Int)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.games.TargetPracticeGameTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/GameResultPayload.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticeGame.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/TargetPracticeGameTest.kt
git commit -m "feat(games): TargetPracticeGame state holder with injectable simulator"
```

---

### Task 9: BreakThePaneGame state holder (app)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGame.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGameTest.kt`

**Interfaces:**
- Consumes: `PaneGeom`, `PaneCrossing`, `GreenZoneSurfaceProvider`, `GameModes`, `GameResultPayload`.
- Produces:
  - `enum class BreakOutcomeKind { BROKE, HIT_PANE_MISSED_GREEN, GREEN_MISSED_PANE, MISSED_BOTH }`
  - `data class BreakOutcome(kind: BreakOutcomeKind, brokenCell: Int?, feedback: String)`
  - `data class PaneShot(shot: DisplayShot, outcome: BreakOutcome)`
  - `class BreakThePaneGame`:
    - `val shots: SnapshotStateList<PaneShot>`, `val brokenCells: SnapshotStateList<Int>`, `val tick: MutableIntState`
    - `var pane: PaneGeom?` (set by `start`), `val targetM: Double`, `var lastFeedback: String` (observable, for the HUD)
    - `var clockMs`, `var simulator` (same seams as Task 8)
    - `fun start(targetM: Double)`, `fun add(ballData: BallData): PaneShot?`, `fun greenRadiusM()`, `val complete: Boolean` (all 9 broken), `val shotCount: Int`, `fun takeResult(): GameResultPayload?` (score = shots taken).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakThePaneGameTest {

    private val target = 140.0

    /**
     * Simulator seam: builds a ShotResult whose samples cross the pane plane
     * exactly once at (crossX, crossZ) and rest at (restX, restY).
     */
    private fun syntheticShot(crossX: Double, crossZ: Double, restX: Double, restY: Double): (LaunchConditions) -> ShotResult =
        { _ ->
            val pane = PaneGeom(target)
            ShotResult(
                carryM = target, rolloutM = 1.0, totalM = target + 1.0, sideM = restX,
                apexM = crossZ + 10.0, flightTimeSec = 6.0,
                samples = listOf(
                    TrajectorySample(0.0, 0.0, 0.0, 0.0),
                    TrajectorySample(crossX * 0.5, pane.planeYM * 0.5, crossZ * 0.5, 1.0),
                    TrajectorySample(crossX, pane.planeYM, crossZ, 2.0),
                    TrajectorySample(restX, restY, 0.0, 6.0),
                ),
                restX = restX, restY = restY,
            )
        }

    private fun newGame(crossX: Double, crossZ: Double, restX: Double, restY: Double): BreakThePaneGame {
        val game = BreakThePaneGame()
        game.simulator = syntheticShot(crossX, crossZ, restX, restY)
        game.start(target)
        return game
    }

    private fun ball() = BallData(33.0, 46.0, 0.0, 21.0, 0.0, 7500, 5, 10)

    @Test
    fun `cell breaks when crossing unbroken cell and resting on green`() {
        val game = newGame(0.0, PaneGeom(target).zRefM, 0.0, target + 1.0)
        val shot = game.add(ball())
        assertNotNull(shot)
        assertEquals(BreakOutcomeKind.BROKE, shot!!.outcome.kind)
        assertEquals(4, shot.outcome.brokenCell)
        assertTrue(4 in game.brokenCells)
    }

    @Test
    fun `hit pane but off green breaks nothing`() {
        val game = newGame(0.0, PaneGeom(target).zRefM, 0.0, target - 20.0)
        val shot = game.add(ball())!!
        assertEquals(BreakOutcomeKind.HIT_PANE_MISSED_GREEN, shot.outcome.kind)
        assertEquals(0, game.brokenCells.size)
        assertEquals(1, game.shotCount)
    }

    @Test
    fun `on green but under the pane breaks nothing`() {
        val game = newGame(0.0, crossZ = 2.0, restX = 0.0, restY = target + 1.0)
        val shot = game.add(ball())!!
        assertEquals(BreakOutcomeKind.GREEN_MISSED_PANE, shot.outcome.kind)
        assertEquals(0, game.brokenCells.size)
    }

    @Test
    fun `missed both - short of green below pane`() {
        val game = newGame(0.0, crossZ = 2.0, restX = 0.0, restY = target - 20.0)
        val shot = game.add(ball())!!
        assertEquals(BreakOutcomeKind.MISSED_BOTH, shot.outcome.kind)
    }

    @Test
    fun `crossing an already broken cell breaks nothing`() {
        val game = BreakThePaneGame()
        game.simulator = syntheticShot(0.0, PaneGeom(target).zRefM, 0.0, target + 1.0)
        game.start(target)
        game.add(ball())
        // Same cell again (middle), different crossing point, still on green.
        game.simulator = syntheticShot(0.5, PaneGeom(target).zRefM + 0.2, 0.5, target + 1.5)
        val second = game.add(ball())!!
        assertEquals(BreakOutcomeKind.GREEN_MISSED_PANE, second.outcome.kind)
        assertEquals(1, game.brokenCells.size)
        assertEquals(2, game.shotCount)
    }

    @Test
    fun `completes after all nine cells broken and takeResult fires once`() {
        val game = BreakThePaneGame()
        game.start(target)
        val pane = game.pane!!
        // Nine deterministic shots: one per cell, each resting on the green.
        for (cell in 0 until 9) {
            val row = cell / 3
            val col = cell % 3
            val z = pane.bottomZM + (row + 0.5) * pane.cellHM
            val x = pane.leftXM + (col + 0.5) * pane.cellWM
            game.simulator = syntheticShot(x, z, restX = 0.0, restY = target + 1.0)
            val shot = game.add(ball())
            assertNotNull("cell $cell", shot)
            assertEquals("cell $cell", BreakOutcomeKind.BROKE, shot!!.outcome.kind)
        }
        assertTrue(game.complete)
        assertEquals(9, game.shotCount)
        val result = game.takeResult()
        assertEquals(GameModes.BREAK_PANE, result!!.mode)
        assertEquals(null, result.difficulty)
        assertEquals(9, result.score) // shots taken
        assertNull(game.takeResult())
        game.start(target)
        assertFalse(game.complete)
        assertEquals(0, game.brokenCells.size)
    }

    @Test
    fun `feedback strings are populated per outcome`() {
        val game = newGame(0.0, PaneGeom(target).zRefM, 0.0, target + 1.0)
        game.add(ball())
        assertTrue(game.lastFeedback.isNotBlank())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.games.BreakThePaneGameTest"`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write implementation**

```kotlin
package com.hpsmiles.golfsim.games

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.GreenZoneSurfaceProvider
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.range.DisplayShot
import kotlin.math.hypot

enum class BreakOutcomeKind { BROKE, HIT_PANE_MISSED_GREEN, GREEN_MISSED_PANE, MISSED_BOTH }

data class BreakOutcome(val kind: BreakOutcomeKind, val brokenCell: Int?, val feedback: String)

/**
 * Break the Pane state holder (spec §5). A shot breaks a cell only when it
 * crosses the pane plane inside an UNBROKEN cell AND comes to rest on the
 * green. Score = shots taken until all 9 cells are broken (lower is better).
 */
class BreakThePaneGame {

    val shots: SnapshotStateList<PaneShot> = mutableStateListOf()
    val brokenCells: SnapshotStateList<Int> = mutableStateListOf()
    val tick: MutableIntState = mutableIntStateOf(0)

    var clockMs: () -> Long = { System.currentTimeMillis() }

    /** Injectable seam, same contract as TargetPracticeGame.simulator. */
    var simulator: (LaunchConditions) -> ShotResult = { launch ->
        BallFlightEngine.simulate(
            launch,
            Environment(),
            GreenZoneSurfaceProvider(0.0, targetM, greenRadiusM()),
        )
    }

    var pane: PaneGeom? = null
        private set
    var targetM: Double = 140.0
        private set
    var lastFeedback: String = ""
        private set
    private var resultTaken = false

    fun start(targetM: Double) {
        this.targetM = targetM
        pane = PaneGeom(targetM)
        shots.clear()
        brokenCells.clear()
        lastFeedback = ""
        resultTaken = false
    }

    val complete: Boolean get() = brokenCells.size == PaneCellCount
    val shotCount: Int get() = shots.size

    /** Green oval radius, scaled to the target (spec §5: ~6 m at 140 m). */
    fun greenRadiusM(): Double = 6.0 * targetM / 140.0

    /** One decoded measurement → evaluated shot. Null on guard violation or completed game. */
    fun add(ballData: BallData): PaneShot? {
        val p = pane ?: return null
        if (complete) return null
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
        val onGreen = hypot(result.restX, result.restY - targetM) <= greenRadiusM()
        val crossing = p.firstCrossing(result.samples)
        val cell = crossing?.cell
        val outcome = when {
            cell != null && onGreen && cell !in brokenCells -> {
                brokenCells.add(cell)
                BreakOutcome(Broke, cell, "BROKE CELL ${cell + 1}")
            }
            cell != null && onGreen -> BreakOutcome(GREEN_MISSED_PANE, null, "GREEN OK - CELL ALREADY BROKEN")
            cell != null -> BreakOutcome(HIT_PANE_MISSED_GREEN, null, "PANE OK - MISSED GREEN")
            onGreen -> {
                val dir = when {
                    crossing == null -> "MISSED PANE"
                    crossing.zM < p.bottomZM -> "GREEN OK - UNDER THE PANE"
                    crossing.zM > p.topZM -> "GREEN OK - OVER THE PANE"
                    crossing.xM < p.leftXM -> "GREEN OK - MISSED LEFT"
                    else -> "GREEN OK - MISSED RIGHT"
                }
                BreakOutcome(GREEN_MISSED_PANE, null, dir)
            }
            else -> {
                val dir = when {
                    crossing == null -> "MISSED BOTH"
                    crossing.zM < p.bottomZM -> "SHORT - UNDER THE PANE"
                    crossing.zM > p.topZM -> "OVER THE PANE"
                    result.restY < targetM - greenRadiusM() -> "SHORT"
                    result.restX < 0.0 -> "MISSED LEFT"
                    else -> "MISSED RIGHT"
                }
                BreakOutcome(MISSED_BOTH, null, dir)
            }
        }
        lastFeedback = outcome.feedback
        val paneShot = PaneShot(DisplayShot(ballData, launch, result, clockMs()), outcome)
        shots.add(paneShot)
        tick.intValue++
        return paneShot
    }

    /** Non-null exactly once once [complete]; the summary AppRoot persists. */
    fun takeResult(): GameResultPayload? {
        if (!complete || resultTaken) return null
        resultTaken = true
        return GameResultPayload(GameModes.BREAK_PANE, null, targetM, shotCount)
    }

    private companion object {
        const val PaneCellCount = 9
        // Local alias so the `when` above reads cleanly.
        val Broke = BreakOutcomeKind.BROKE
    }
}

data class PaneShot(val shot: DisplayShot, val outcome: BreakOutcome)
```

Note: replace the `Broke` alias with `BreakOutcomeKind.BROKE` directly if the companion-alias reads awkwardly — keep exactly one style.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.games.BreakThePaneGameTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGame.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGameTest.kt
git commit -m "feat(games): BreakThePaneGame state holder with break rule"
```

---

### Task 10: GameScene painter + Target Practice play UI (app)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GameScene.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticeCanvas.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticePlay.kt`
- Test: build-only (Canvas code); the pure math it uses is already tested. Visual check lands in Task 13 device verification.

**Interfaces:**
- Consumes: `TargetPracticeGame`, `GameShot`, `TargetPracticeScoring`, `Difficulty`, `PovProjector`, `RangeCamera`, `RangeScene`, `FollowCam`, `GolfColors`, `GolfMotion.TracerDrawMs`.
- Produces:
  - `object GameScene` — `focalPx(w: Float): Float` (= 1.10w), `v0Px(h: Float): Float` (= 0.30h), `apexVMin(w, h): Double`, `toScreen(w, h, p): Offset`, `project(cam, w, h, x, y, z): Offset?`, `groundPath(cam, w, h, world): Path?`, `quadPath(cam, w, h, corners): Path?`, `DrawScope.drawGameGround(cam, w, h)`, `DrawScope.drawGreen(cam, w, h, cx, cy, radiusM)`, `DrawScope.drawPin(cam, w, h, cx, cy)`, `DrawScope.drawTracer(cam, w, h, result, fraction)`.
  - `@Composable fun TargetPracticeCanvas(game, current: GameShot?, playFraction: Float, camera: RangeCamera, modifier)`
  - `@Composable fun TargetPracticePlay(game: TargetPracticeGame, onBack: () -> Unit, modifier)` — full-screen play view incl. HUD + result overlay. RENDERED but NOT yet routed (Task 12 wires it).

- [ ] **Step 1: Write GameScene.kt**

```kotlin
package com.hpsmiles.golfsim.games

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera
import com.hpsmiles.golfsim.range.RangeScene

/**
 * Shared POV drawing for both game canvases. Mirrors PovRangeCanvas's screen
 * mapping (focal = 1.10w, v0 = 0.30h) and ground clipping so the follow cam
 * chases the exact drawn geometry, same as the range.
 */
object GameScene {

    fun focalPx(w: Float): Float = w * 1.10f
    fun v0Px(h: Float): Float = h * 0.30f

    /** Same apex-clamp line RangeScreen derives (v0 − 8% top margin). */
    fun apexVMin(w: Float, h: Float): Double = (0.08f * h - v0Px(h)) / focalPx(w)

    fun toScreen(w: Float, h: Float, p: PovProjector.ProjectedPoint): Offset =
        Offset(w / 2f + (p.u * focalPx(w)).toFloat(), v0Px(h) + (p.v * focalPx(w)).toFloat())

    fun project(cam: RangeCamera, w: Float, h: Float, x: Double, y: Double, z: Double): Offset? =
        PovProjector.project(cam, x, y, z)?.let { toScreen(w, h, it) }

    /** Ground polygon (world x,y vertices, z=0) → near-clipped screen Path. */
    fun groundPath(cam: RangeCamera, w: Float, h: Float, world: List<Pair<Double, Double>>): Path? {
        val clipped = PovProjector.clipGroundPath(world, cam)
        if (clipped.size < 3) return null
        val path = Path()
        var first = true
        for ((x, y) in clipped) {
            val p = PovProjector.project(cam, x, y, 0.0) ?: continue
            val s = toScreen(w, h, p)
            if (first) { path.moveTo(s.x, s.y); first = false } else path.lineTo(s.x, s.y)
        }
        path.close()
        return if (first) null else path
    }

    /** Screen path for a closed polygon of world corners (any z). */
    fun quadPath(cam: RangeCamera, w: Float, h: Float, corners: List<Triple<Double, Double, Double>>): Path? {
        val path = Path()
        var first = true
        for ((x, y, z) in corners) {
            val p = PovProjector.project(cam, x, y, z) ?: return null
            val s = toScreen(w, h, p)
            if (first) { path.moveTo(s.x, s.y); first = false } else path.lineTo(s.x, s.y)
        }
        path.close()
        return if (first) null else path
    }

    private val GreenTurf = Color(0xFF1C3A30)
    private val FringeTurf = Color(0xFF16291F)

    fun DrawScope.drawGameGround(cam: RangeCamera, w: Float, h: Float) {
        drawRect(GolfColors.Base) // sky/backdrop
        groundPath(cam, w, h, RangeScene.fairwayOutline())?.let { drawPath(it, GolfColors.HoleFairway) }
    }

    fun DrawScope.drawGreen(cam: RangeCamera, w: Float, h: Float, cx: Double, cy: Double, radiusM: Double) {
        groundPath(cam, w, h, RangeScene.circleOutline(cx, cy, radiusM * 1.35))?.let { drawPath(it, FringeTurf) }
        groundPath(cam, w, h, RangeScene.circleOutline(cx, cy, radiusM))?.let { drawPath(it, GreenTurf) }
    }

    /** Graphic pin: flagstick + pennant, flat style (spec §4/§5). */
    fun DrawScope.drawPin(cam: RangeCamera, w: Float, h: Float, cx: Double, cy: Double, heightM: Double = 2.4) {
        val base = PovProjector.project(cam, cx, cy, 0.0) ?: return
        val top = PovProjector.project(cam, cx, cy, heightM) ?: return
        val b = toScreen(w, h, base)
        val t = toScreen(w, h, top)
        drawLine(GolfColors.TextPrimary, b, t, strokeWidth = 2f)
        val px = 0.6 * focalPx(w) * top.scale // ~0.6 m pennant at that depth
        val pennant = Path().apply {
            moveTo(t.x, t.y)
            lineTo(t.x + px.toFloat(), t.y + (px * 0.35).toFloat())
            lineTo(t.x, t.y + (px * 0.7).toFloat())
            close()
        }
        drawPath(pennant, GolfColors.TextPrimary)
    }

    /** Amber live-moment tracer + rest dot (amber reserved for the live moment). */
    fun DrawScope.drawTracer(cam: RangeCamera, w: Float, h: Float, result: ShotResult, fraction: Float) {
        val samples = result.samples
        if (samples.size < 2) return
        val n = (samples.size * fraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(2)
        val path = Path()
        var first = true
        for (s in samples.subList(0, n)) {
            val p = PovProjector.project(cam, s.px, s.py, s.pz) ?: continue
            val sc = toScreen(w, h, p)
            if (first) { path.moveTo(sc.x, sc.y); first = false } else path.lineTo(sc.x, sc.y)
        }
        if (!first) drawPath(path, GolfColors.Amber, style = Stroke(3f))
        if (fraction >= 1f) {
            project(cam, w, h, result.restX, result.restY, 0.0)?.let { drawCircle(GolfColors.Amber, 5f, it) }
        }
    }
}
```

- [ ] **Step 2: Write TargetPracticeCanvas.kt**

```kotlin
package com.hpsmiles.golfsim.games

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.range.RangeCamera
import com.hpsmiles.golfsim.range.RangeScene

/** POV canvas for Target Practice: green, scoring rings, pin, tracer, rest dot. */
@Composable
fun TargetPracticeCanvas(
    game: TargetPracticeGame,
    current: GameShot?,
    playFraction: Float,
    camera: RangeCamera,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        with(GameScene) {
            drawGameGround(camera, w, h)
            drawGreen(camera, w, h, 0.0, game.targetM, game.greenRadiusM())
            // Shaded scoring rings, densest at the centre (draw largest first).
            TargetPracticeScoring.bands(game.difficulty).asReversed().forEachIndexed { i, band ->
                groundPath(camera, w, h, RangeScene.circleOutline(0.0, game.targetM, band.maxMissM))?.let {
                    drawPath(it, GolfColors.Teal.copy(alpha = 0.08f + 0.06f * i))
                }
            }
            drawPin(camera, w, h, 0.0, game.targetM)
            project(camera, w, h, 0.0, 0.0, 0.05)?.let { drawCircle(GolfColors.TextPrimary, 4f, it) }
            current?.let { drawTracer(camera, w, h, it.shot.shotResult, playFraction) }
        }
    }
}
```

- [ ] **Step 3: Write TargetPracticePlay.kt** (play view + HUD + result overlay)

```kotlin
package com.hpsmiles.golfsim.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfMotion
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.RangeCamera

/** Target Practice play view: canvas, HUD, result overlay (spec §4). */
@Composable
fun TargetPracticePlay(
    game: TargetPracticeGame,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playFraction by remember { mutableFloatStateOf(1f) }

    // Restart the flight animation each time a shot lands (tick bump).
    LaunchedEffect(game.tick.intValue) {
        if (game.shots.isEmpty()) return@LaunchedEffect
        val durationMs = GolfMotion.TracerDrawMs.toFloat()
        var last = withFrameNanos { it }
        playFraction = 0f
        while (playFraction < 1f) {
            val now = withFrameNanos { it }
            playFraction = (playFraction + ((now - last) / 1_000_000.0 / durationMs).toFloat()).coerceAtMost(1f)
            last = now
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(GolfColors.Base)) {
        val camera = when (val shot = game.shots.lastOrNull()) {
            null -> RangeCamera.STATIC
            else -> FollowCam.cameraAt(shot.shot.shotResult, playFraction, GameScene.apexVMin(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
        }
        TargetPracticeCanvas(
            game = game,
            current = game.shots.lastOrNull(),
            playFraction = playFraction,
            camera = camera,
            modifier = Modifier.fillMaxSize(),
        )

        // HUD: per-shot point indicators + running total.
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(GolfSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(TargetPracticeScoring.SHOTS_PER_GAME) { i ->
                val pts = game.shots.getOrNull(i)?.points
                Box(
                    modifier = Modifier
                        .background(if (pts != null) GolfColors.Teal55 else GolfColors.Panel)
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
                        .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
                ) {
                    Text(text = pts?.toString() ?: "·", style = GolfTypography.MetricValue, color = GolfColors.TextPrimary)
                }
            }
            Text(
                text = "TOTAL ${game.totalPoints}",
                style = GolfTypography.MetricValue,
                color = GolfColors.TextPrimary,
                modifier = Modifier.padding(start = GolfSpacing.Sm),
            )
        }

        // Result overlay after shot 5.
        if (game.complete) {
            val resultShots = game.shots.toList()
            SectionCard(
                title = "RESULT",
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(GolfSpacing.Xl),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                    resultShots.forEachIndexed { i, s ->
                        Text(
                            text = "${i + 1}.  ${s.points} pts   miss ${"%.1f".format(s.missM)} m",
                            style = GolfTypography.Body,
                            color = GolfColors.TextPrimary,
                        )
                    }
                    Text(
                        text = "TOTAL ${game.totalPoints} / ${TargetPracticeScoring.MAX_SCORE}",
                        style = GolfTypography.MetricValue,
                        color = GolfColors.Teal,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                        Button(onClick = { game.start(game.targetM, game.difficulty) }) {
                            Text("PLAY AGAIN")
                        }
                        OutlinedButton(onClick = onBack) { Text("BACK") }
                    }
                }
            }
        }
    }
}
```

Note: fix any import the compiler flags (e.g. `getFloatState` is not a real import — use plain `mutableFloatStateOf` + `getValue`/`setValue` as written; delete the bogus import). Verify `GolfMotion.TracerDrawMs` and `SectionCard` against the real design-system names.

- [ ] **Step 4: Build**

Run: `.\gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL (fix compile errors: imports, the two notes above).

- [ ] **Step 5: Run app tests (no regressions)**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/GameScene.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticeCanvas.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticePlay.kt
git commit -m "feat(games): Target Practice POV canvas, HUD and result overlay"
```

---

### Task 11: PaneClip + Break the Pane play UI (app)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneClip.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneClipTest.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPaneCanvas.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt`

**Interfaces:**
- Consumes: `BreakThePaneGame`, `PaneGeom`, `GameScene`, `PovProjector`, `RangeCamera`, `FollowCam`.
- Produces:
  - `object PaneClip` with `data class Vertex(x, y, z)`, `fun depth(v, cam): Double`, `fun clip(corners: List<Vertex>, cam: RangeCamera, minDepthM: Double = PovProjector.GROUND_MIN_DEPTH_M): List<Vertex>` — Sutherland-Hodgman clip of a vertical quad against the camera near plane (ground clip does not apply — spec §5).
  - `@Composable fun BreakPaneCanvas(game, playFraction: Float, camera: RangeCamera, modifier)`
  - `@Composable fun BreakPanePlay(game, onBack: () -> Unit, modifier)` — RENDERED but NOT yet routed (Task 12 wires it).

- [ ] **Step 1: Write the failing PaneClip test**

```kotlin
package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaneClipTest {

    private val cam = RangeCamera.STATIC // pitch 0 → depth = y + 21.2

    private fun quad(y: Double) = listOf(
        PaneClip.Vertex(-2.8, y, 10.0),
        PaneClip.Vertex(2.8, y, 10.0),
        PaneClip.Vertex(2.8, y, 14.0),
        PaneClip.Vertex(-2.8, y, 14.0),
    )

    @Test
    fun `fully visible quad passes through unchanged`() {
        val out = PaneClip.clip(quad(100.0), cam)
        assertEquals(4, out.size)
    }

    @Test
    fun `partially clipped quad gains interpolated vertices`() {
        // Plane at y = -21.2 + 0.5 = -20.7 → a quad spanning y -22..-20 straddles it.
        val corners = listOf(
            PaneClip.Vertex(-2.8, -22.0, 10.0),
            PaneClip.Vertex(2.8, -22.0, 10.0),
            PaneClip.Vertex(2.8, -20.0, 14.0),
            PaneClip.Vertex(-2.8, -20.0, 14.0),
        )
        val out = PaneClip.clip(corners, cam)
        assertTrue("expected 5 vertices, got ${out.size}", out.size == 5)
        // Interpolated vertices sit exactly on the near plane.
        assertTrue(out.all { PaneClip.depth(it, cam) >= PovProjector.GROUND_MIN_DEPTH_M - 1e-9 })
    }

    @Test
    fun `fully behind quad clips to empty`() {
        val out = PaneClip.clip(quad(-100.0), cam)
        assertTrue(out.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.games.PaneClipTest"`
Expected: FAIL — `PaneClip` unresolved.

- [ ] **Step 3: Write PaneClip**

```kotlin
package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera
import kotlin.math.cos
import kotlin.math.sin

/**
 * Near-plane clip for VERTICAL quads (the glass pane). PovProjector's
 * clipGroundPath only handles z=0 polygons; the pane needs the general
 * camera-depth Sutherland-Hodgman (spec §5). Pure + JVM-tested.
 */
object PaneClip {

    data class Vertex(val x: Double, val y: Double, val z: Double)

    /** Same camera-plane depth test PovProjector.project uses. */
    fun depth(v: Vertex, cam: RangeCamera): Double {
        val dy = v.y - cam.y
        val dz = v.z - cam.z
        return dy * cos(cam.pitchRad) - dz * sin(cam.pitchRad)
    }

    fun clip(
        corners: List<Vertex>,
        cam: RangeCamera,
        minDepthM: Double = PovProjector.GROUND_MIN_DEPTH_M,
    ): List<Vertex> {
        if (corners.size < 3) return emptyList()
        val out = ArrayList<Vertex>(corners.size + 4)
        var prev = corners.last()
        var prevD = depth(prev, cam)
        var prevIn = prevD >= minDepthM
        for (curr in corners) {
            val d = depth(curr, cam)
            val inside = d >= minDepthM
            if (inside != prevIn) {
                val t = (minDepthM - prevD) / (d - prevD)
                out.add(
                    Vertex(
                        prev.x + (curr.x - prev.x) * t,
                        prev.y + (curr.y - prev.y) * t,
                        prev.z + (curr.z - prev.z) * t,
                    ),
                )
            }
            if (inside) out.add(curr)
            prev = curr
            prevD = d
            prevIn = inside
        }
        return out
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain --tests "com.hpsmiles.golfsim.games.PaneClipTest"`
Expected: PASS

- [ ] **Step 5: Write BreakPaneCanvas.kt**

```kotlin
package com.hpsmiles.golfsim.games

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera
import com.hpsmiles.golfsim.range.RangeScene

/** POV canvas for Break the Pane: ground, green, pin, 3×3 glass pane, tracer. */
@Composable
fun BreakPaneCanvas(
    game: BreakThePaneGame,
    playFraction: Float,
    camera: RangeCamera,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        with(GameScene) {
            drawGameGround(camera, w, h)
            game.pane?.let { pane ->
                drawGreen(camera, w, h, 0.0, game.targetM, game.greenRadiusM())
                drawPin(camera, w, h, 0.0, game.targetM)
                // Pane cells, top row first so nearer (lower) cells draw on top.
                for (cell in 8 downTo 0) {
                    val broken = cell in game.brokenCells
                    val clipped = PaneClip.clip(
                        pane.cellCorners(cell).map { (x, y, z) -> PaneClip.Vertex(x, y, z) },
                        camera,
                    )
                    if (clipped.size < 3) continue
                    val path = Path()
                    var first = true
                    for (v in clipped) {
                        val p = PovProjector.project(camera, v.x, v.y, v.z) ?: continue
                        val s = GameScene.toScreen(w, h, p)
                        if (first) { path.moveTo(s.x, s.y); first = false } else path.lineTo(s.x, s.y)
                    }
                    path.close()
                    if (first) continue
                    if (broken) {
                        drawPath(path, GolfColors.Teal.copy(alpha = 0.03f))
                        drawPath(path, GolfColors.Teal.copy(alpha = 0.25f), style = Stroke(1f))
                    } else {
                        drawPath(path, GolfColors.Teal.copy(alpha = 0.18f))
                        drawPath(path, GolfColors.Teal, style = Stroke(2f))
                    }
                }
            }
            project(camera, w, h, 0.0, 0.0, 0.05)?.let { drawCircle(GolfColors.TextPrimary, 4f, it) }
            game.shots.lastOrNull()?.let { drawTracer(camera, w, h, it.shot.shotResult, playFraction) }
        }
    }
}
```

- [ ] **Step 6: Write BreakPanePlay.kt** (play view + HUD with 3×3 minimap + feedback + result overlay)

```kotlin
package com.hpsmiles.golfsim.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfMotion
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.RangeCamera

/** Break the Pane play view: canvas, pane-state minimap, feedback, overlay (spec §5). */
@Composable
fun BreakPanePlay(
    game: BreakThePaneGame,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playFraction by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(game.tick.intValue) {
        if (game.shots.isEmpty()) return@LaunchedEffect
        val durationMs = GolfMotion.TracerDrawMs.toFloat()
        var last = withFrameNanos { it }
        playFraction = 0f
        while (playFraction < 1f) {
            val now = withFrameNanos { it }
            playFraction = (playFraction + ((now - last) / 1_000_000.0 / durationMs).toFloat()).coerceAtMost(1f)
            last = now
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(GolfColors.Base)) {
        val camera = when (val shot = game.shots.lastOrNull()) {
            null -> RangeCamera.STATIC
            else -> FollowCam.cameraAt(shot.shot.shotResult, playFraction, GameScene.apexVMin(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
        }
        BreakPaneCanvas(
            game = game,
            playFraction = playFraction,
            camera = camera,
            modifier = Modifier.fillMaxSize(),
        )

        // HUD: pane-state minimap (3×3, filled teal = broken), shots counter, feedback.
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(GolfSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (row in 2 downTo 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (col in 0..2) {
                        val cell = row * 3 + col
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(if (cell in game.brokenCells) GolfColors.Teal else GolfColors.Panel)
                                .border(1.dp, GolfColors.Line, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
            Text("SHOTS ${game.shotCount}", style = GolfTypography.MetricValue, color = GolfColors.TextPrimary)
        }
        if (game.lastFeedback.isNotBlank()) {
            Text(
                text = game.lastFeedback,
                style = GolfTypography.Status,
                color = GolfColors.Teal,
                modifier = Modifier.align(Alignment.BottomCenter).padding(GolfSpacing.Md),
            )
        }

        if (game.complete) {
            SectionCard(
                title = "PANE BROKEN",
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(GolfSpacing.Xl),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                    Text("Total shots: ${game.shotCount}", style = GolfTypography.MetricValue, color = GolfColors.Teal)
                    Text("Lower is better.", style = GolfTypography.Body, color = GolfColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                        Button(onClick = { game.start(game.targetM) }) { Text("PLAY AGAIN") }
                        OutlinedButton(onClick = onBack) { Text("BACK") }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 7: Build + run app tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneClip.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneClipTest.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPaneCanvas.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt
git commit -m "feat(games): PaneClip + Break the Pane canvas, HUD and overlay"
```

---

### Task 12: GamesScreen picker + setup + AppRoot wiring (app)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`

**Interfaces:**
- Consumes: `TargetPracticeGame`, `BreakThePaneGame`, `TargetPracticePlay`, `BreakPanePlay`, `GameResultPayload` (takeResult), `SessionRepository.saveGameResult`, `NavRailButton`.
- Produces:
  - `enum class GameMode { NONE, TARGET_PRACTICE, BREAK_PANE }` (top-level in `GamesScreen.kt`)
  - `@Composable fun GamesScreen(targetPractice, breakPane, activeGame, onActiveGameChange: (GameMode) -> Unit, modifier)` — sub-state enum `PICKER → SETUP → PLAYING → RESULT` per game (plain when-enum).
  - AppRoot: `GAMES` rail entry; shot routing; confirm-leave dialog; game summary persistence.

- [ ] **Step 1: Write GamesScreen.kt**

```kotlin
package com.hpsmiles.golfsim.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/** Which game (if any) currently owns the shot stream. Hoisted to AppRoot for dispatch. */
enum class GameMode { NONE, TARGET_PRACTICE, BREAK_PANE }

private const val DIST_MIN_M = 50.0
private const val DIST_MAX_M = 350.0

/**
 * Games tab: picker → per-game setup → play → result. Plain when-enum
 * sub-state, repo convention (no Navigation-Compose). Setup sliders and
 * typed fields stay bidirectionally synced, typed values clamp (spec §4/§8).
 */
@Composable
fun GamesScreen(
    targetPractice: TargetPracticeGame,
    breakPane: BreakThePaneGame,
    activeGame: GameMode,
    onActiveGameChange: (GameMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerState by remember { mutableStateOf("PICKER") } // PICKER | SETUP | PLAYING

    when {
        pickerState == "PICKER" && activeGame == GameMode.NONE -> {
            Column(
                modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
            ) {
                GameCard(
                    title = "TARGET PRACTICE",
                    blurb = "5 shots at a pinned green. Points by miss distance at rest. Max 125.",
                    onClick = { pickerState = "SETUP_TP" },
                )
                GameCard(
                    title = "BREAK THE PANE",
                    blurb = "Break all 9 glass cells floating at 25% of the target. Fewer shots wins.",
                    onClick = { pickerState = "SETUP_BP" },
                )
            }
        }
        pickerState == "SETUP_TP" -> {
            Column(modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl)) {
                TargetPracticeSetup(
                    onStart = { target, difficulty ->
                        targetPractice.start(target, difficulty)
                        onActiveGameChange(GameMode.TARGET_PRACTICE)
                        pickerState = "PLAYING"
                    },
                    onCancel = { pickerState = "PICKER" },
                )
            }
        }
        pickerState == "SETUP_BP" -> {
            Column(modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl)) {
                BreakPaneSetup(
                    onStart = { target ->
                        breakPane.start(target)
                        onActiveGameChange(GameMode.BREAK_PANE)
                        pickerState = "PLAYING"
                    },
                    onCancel = { pickerState = "PICKER" },
                )
            }
        }
        activeGame == GameMode.TARGET_PRACTICE -> {
            TargetPracticePlay(
                game = targetPractice,
                onBack = {
                    onActiveGameChange(GameMode.NONE)
                    pickerState = "PICKER"
                },
                modifier = modifier,
            )
        }
        activeGame == GameMode.BREAK_PANE -> {
            BreakPanePlay(
                game = breakPane,
                onBack = {
                    onActiveGameChange(GameMode.NONE)
                    pickerState = "PICKER"
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun GameCard(title: String, blurb: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.CornerCard))
            .clickable(onClick = onClick)
            .padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
    ) {
        Text(title, style = GolfTypography.ScreenTitle, color = GolfColors.Teal)
        Text(blurb, style = GolfTypography.Body, color = GolfColors.TextSecondary)
    }
}

@Composable
private fun TargetPracticeSetup(onStart: (Double, Difficulty) -> Unit, onCancel: () -> Unit) {
    var sliderM by remember { mutableStateOf(140.0) }
    var text by remember { mutableStateOf("140") }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg)) {
        Text("TARGET PRACTICE", style = GolfTypography.ScreenTitle, color = GolfColors.Teal)
        Slider(
            value = sliderM.toFloat(),
            onValueChange = {
                sliderM = it.toDouble()
                text = "%.0f".format(sliderM)
            },
            valueRange = DIST_MIN_M.toFloat()..DIST_MAX_M.toFloat(),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw
                raw.toDoubleOrNull()?.let { sliderM = it.coerceIn(DIST_MIN_M, DIST_MAX_M) }
            },
            label = { Text("Distance (m, $DIST_MIN_M–$DIST_MAX_M)") },
            modifier = Modifier.width(260.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            Difficulty.entries.forEach { d ->
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (difficulty == d) GolfColors.Teal else GolfColors.Line,
                            RoundedCornerShape(GolfSpacing.Sm),
                        )
                        .clickable { difficulty = d }
                        .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs),
                ) {
                    Text(d.name, style = GolfTypography.Status, color = if (difficulty == d) GolfColors.Teal else GolfColors.TextSecondary)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            Button(onClick = { onStart(sliderM, difficulty) }) { Text("START") }
            OutlinedButton(onClick = onCancel) { Text("CANCEL") }
        }
    }
}

@Composable
private fun BreakPaneSetup(onStart: (Double) -> Unit, onCancel: () -> Unit) {
    var sliderM by remember { mutableStateOf(140.0) }
    var text by remember { mutableStateOf("140") }

    Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg)) {
        Text("BREAK THE PANE", style = GolfTypography.ScreenTitle, color = GolfColors.Teal)
        Slider(
            value = sliderM.toFloat(),
            onValueChange = {
                sliderM = it.toDouble()
                text = "%.0f".format(sliderM)
            },
            valueRange = DIST_MIN_M.toFloat()..DIST_MAX_M.toFloat(),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw
                raw.toDoubleOrNull()?.let { sliderM = it.coerceIn(DIST_MIN_M, DIST_MAX_M) }
            },
            label = { Text("Distance (m, $DIST_MIN_M–$DIST_MAX_M)") },
            modifier = Modifier.width(260.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            Button(onClick = { onStart(sliderM) }) { Text("START") }
            OutlinedButton(onClick = onCancel) { Text("CANCEL") }
        }
    }
}
```

Note: verify `GolfTypography.ScreenTitle`, `SectionCard`, `GolfSpacing.CornerCard` exist with these exact names (per explorer recon of `core/designsystem`) — adjust to the real names if the compiler flags them; keep visual intent identical. `Difficulty.entries` requires Kotlin 1.9+; fall back to `Difficulty.values()` if the toolchain is older.

- [ ] **Step 2: Wire AppRoot.kt**

Edits, in order:

(a) Move the tab declaration UP so the dispatcher can read it (currently line 260, needed before the `DisposableEffect` at ~line 149). Right after `val session = remember { RangeSession() }` (line ~81) add:

```kotlin
    // M5.5: tab + game mode hoisted above the shot dispatcher so
    // onMeasurement/fireDemo can route before first composition.
    var tab by remember { mutableStateOf(RangeTab.RANGE) }
    val targetPractice = remember { TargetPracticeGame() }
    val breakPane = remember { BreakThePaneGame() }
    var activeGame by remember { mutableStateOf(GameMode.NONE) }
```

(imports: `com.hpsmiles.golfsim.games.BreakThePaneGame`, `com.hpsmiles.golfsim.games.GameMode`, `com.hpsmiles.golfsim.games.GamesScreen`, `com.hpsmiles.golfsim.games.TargetPracticeGame`), and DELETE the old `var tab by remember { mutableStateOf(RangeTab.RANGE) }` at line 260.

(b) Extend `RangeTab` (line 63):

```kotlin
private enum class RangeTab { RANGE, GAMES, SETTINGS, HISTORY }
```

(c) Replace the shot-routing helpers — `persist` stays; add `routeShot` after it:

```kotlin
    /**
     * M5.5 dispatch (spec §3): a shot goes to the active game OR the range
     * session — never both. Game shots are never range-persisted; completed
     * games emit exactly one summary row via takeResult().
     */
    fun routeShot(ballData: com.hpsmiles.golfsim.core.ble.BallData, source: com.hpsmiles.golfsim.core.data.record.ShotSource) {
        when (activeGame) {
            GameMode.NONE -> persist(session.add(ballData), source)
            GameMode.TARGET_PRACTICE -> {
                targetPractice.add(ballData)
                targetPractice.takeResult()?.let { p ->
                    scope.launch {
                        sessionRepository.saveGameResult(p.mode, p.difficulty, p.targetM, p.score, source, targetPractice.clockMs())
                    }
                }
            }
            GameMode.BREAK_PANE -> {
                breakPane.add(ballData)
                breakPane.takeResult()?.let { p ->
                    scope.launch {
                        sessionRepository.saveGameResult(p.mode, p.difficulty, p.targetM, p.score, source, breakPane.clockMs())
                    }
                }
            }
        }
    }
```

(d) Retarget the two ingest points:

```kotlin
        gattClient.onMeasurement = { ballData ->
            scope.launch { routeShot(ballData, ShotSource.LIVE) }
        }
```

```kotlin
    fun fireDemo() {
        if (demo) routeShot(demoSource.nextShot(), ShotSource.DEMO)
    }
```

(e) Rail: add the GAMES button between RANGE and SETTINGS (line ~270):

```kotlin
                    NavRailButton("GAMES", tab == RangeTab.GAMES, onClick = { tab = RangeTab.GAMES })
```

(f) `when (tab)` — add the GAMES branch:

```kotlin
                    RangeTab.GAMES -> {
                        GamesScreen(
                            modifier = Modifier.weight(1f),
                            targetPractice = targetPractice,
                            breakPane = breakPane,
                            activeGame = activeGame,
                            onActiveGameChange = { activeGame = it },
                        )
                    }
```

(g) Confirm-leave guard (spec §8: leaving mid-game discards, confirm only when shots taken). Wrap the four tab-button `onClick`s and the GAMES picker transitions: extract a helper inside `AppRoot`:

```kotlin
    var confirmLeaveGame by remember { mutableStateOf(false) }
    fun requestTab(to: RangeTab) {
        val midGame = activeGame != GameMode.NONE &&
            (targetPractice.shots.isNotEmpty() || breakPane.shots.isNotEmpty())
        if (midGame && tab != RangeTab.GAMES) { tab = to } // already leaving handled below
        if (midGame && to != RangeTab.GAMES) {
            confirmLeaveGame = true
        } else {
            tab = to
        }
    }
```

and use `onClick = { requestTab(RangeTab.SETTINGS) }` etc. for SETTINGS/HISTORY/RANGE buttons (GAMES stays direct). Then render the dialog at the end of the `Column` (inside `GolfTheme`, after `StatusStrip`):

```kotlin
        if (confirmLeaveGame) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmLeaveGame = false },
                title = { Text("LEAVE GAME?") },
                text = { Text("Shots already taken in this game will be discarded.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        confirmLeaveGame = false
                        activeGame = GameMode.NONE
                        tab = pendingTab ?: RangeTab.RANGE
                    }) { Text("LEAVE") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmLeaveGame = false }) { Text("STAY") }
                },
            )
        }
```

with `var pendingTab by remember { mutableStateOf<RangeTab?>(null) }` set in `requestTab` before showing the dialog, and cleared after. Final `requestTab`:

```kotlin
    fun requestTab(to: RangeTab) {
        val midGame = activeGame != GameMode.NONE &&
            (targetPractice.shots.isNotEmpty() || breakPane.shots.isNotEmpty())
        if (midGame && to != RangeTab.GAMES) {
            pendingTab = to
            confirmLeaveGame = true
        } else {
            tab = to
        }
    }
```

- [ ] **Step 3: Build + run full app test suite**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(games): GAMES tab, mode picker, setup forms and AppRoot shot dispatch"
```

---

### Task 13: Final build + device verification

**Files:**
- None new. Verification only.

**Interfaces:**
- Consumes: everything above. Exit criteria per spec §10.

- [ ] **Step 1: Full build + all unit tests**

Run: `.\gradlew.bat build --console=plain`
Expected: BUILD SUCCESSFUL — includes new physics/app/data tests and the migration test. Fix anything red before continuing.

- [ ] **Step 2: Regression check — range unchanged**

Run: `.\gradlew.bat :app:testDebugUnitTest :core:physics:test :core:data:testDebugUnitTest --console=plain`
Expected: PASS. Range behaviour must be byte-for-byte unchanged (spec §10): the only range-code edits in this plan are `ShotResult` fields (additive) and `toDisplayShot` rest reconstruction.

- [ ] **Step 3: Install on the tablet**

Connect the Lenovo tablet, then:

Run: `.\gradlew.bat :app:installDebug --console=plain`
Expected: Installed.

- [ ] **Step 4: Device checklist (LIVE and DEMO)**

On-device, over a live MLM2PRO session:

1. GAMES tab opens; both picker cards visible.
2. Target Practice: set 140 m (slider + typed field sync), MEDIUM; play 5 DEMO shots → HUD points, running total, result overlay; PLAY AGAIN resets; BACK returns to picker.
3. Target Practice: one LIVE shot lands on the green — points match the band table for the measured miss.
4. Break the Pane: set 140 m; the 3×3 pane floats at 35 m, middle row centred on the stock crossing height (~12.6 m); cell breaks only when the shot ALSO rests on the green; feedback text per outcome (under/over/missed green); minimap fills; total shots overlay appears when all 9 break.
5. Completed game writes exactly one `game_results` row per completion (inspect via `adb shell` + debug DB browser or Room debug logging) — mode/difficulty/distanceBin/score/source correct; DEMO games carry `source = DEMO`.
6. Range tab: history, sessions, exclusions all unchanged; no game shots leak into range history.
7. Leaving a game mid-way with shots taken prompts the confirm dialog; no partial summary row persists.

- [ ] **Step 5: Commit any verification fixes**

```bash
git add -A
git commit -m "fix(games): device-verification fixes (M5.5)"
```

(Only if fixes were needed — otherwise skip; an empty commit is not allowed.)

---

## Self-Review Record

- **Spec coverage:** §4 Target Practice (Tasks 6/8/10/12), §5 Break the Pane (Tasks 4/5/9/11/12), §6 architecture + physics prerequisites (Tasks 1–4), §7 Room v3 (Task 7), §3 nav/dispatch (Task 12), §8 edge cases (confirm-leave in Task 12, misreads never counted in holders' guard path, takeResult-once, boundary inclusivity in Tasks 5/6), §9 test list (all named tests present), §10 exit criteria (Task 13). History view for game_results intentionally deferred (spec §7/§11).
- **Type consistency:** `restX/restY` (Task 1) consumed by Tasks 4/8/9/10; `FlightCrossing` (3) consumed by 4/5; `PaneGeom.firstCrossing` (5) consumed by 9/11; `GameResultPayload` (8) consumed by 9/12; `GameModes`/`saveGameResult` (7) consumed by 12; `GameScene.apexVMin` (10) consumed by 11.
- **Known engine-derived numbers** (spec §2, verified 2026-09-29): 140 m → plane 35 m, cellH 2.8 m, cellW 5.6 m, zRef ≈ 12.6 m, rows 8.4–11.2 / 11.2–14.0 / 14.0–16.8 m. Tests assert bands loosely (zRef in [10,16]) to stay robust against minor solver drift while still catching geometry regressions.
