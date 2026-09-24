# Real-trajectory tracer + painted range (Option B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** POV tracer follows the actual ODE flight (not a stylized Bézier) and the range renders as a painted "Tour Broadcast" scene: fairway, rough, mow stripes, greens with fringes, haze — still pure Compose Canvas via `PovProjector`.

**Architecture:** `:core:physics` collects per-step `(px,py,pz,t)` samples during integration (math untouched — golden values stay bit-identical) and exposes them additively on `LandingState`/`ShotResult`. `:app` replaces the stylized tracer with projected sample polylines and adds `RangeScene.kt` (pure Kotlin, JVM-testable) describing painted ground layers drawn in a fixed order.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, JUnit. No new dependencies.

**Env note (Windows):** Prefix every Gradle command with:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```
Build/test everything: `.\gradlew.bat build` · Physics only: `.\gradlew.bat :core:physics:test`

**Spec:** `docs/superpowers/specs/2026-09-24-real-tracer-painted-range-design.md`

---

## Palette A — "Tour Broadcast" (authoritative values)

```kotlin
private val SKY_TOP = Color(0xFF6FA8DC)      // blue sky
private val SKY_BOTTOM = Color(0xFFC9E2F5)
private val ROUGH_BASE = Color(0xFF1E4D26)   // dark emerald
private val FAIRWAY = Color(0xFF3E8E43)      // vivid saturated
private val STRIPE_LIGHT = Color(0xFF47A04C)
private val STRIPE_DARK = Color(0xFF3A8440)
private val GREEN_SURFACE = Color(0xFF5FBF63)
private val FRINGE = Color(0xFF2F6E35)
private val HAZE = Color.White.copy(alpha = 0.22f)
```
History tracer stays `GolfColors.Teal.copy(alpha = 0.35f)`; live tracer stays `GolfColors.Amber`.

---

### Task 1: `TrajectorySample` + solver collection (`:core:physics`)

**Files:**
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/TrajectorySample.kt`
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/FlightSolver.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/TrajectorySampleTest.kt`
- Do NOT modify: `FlightSolverTest` golden values (they must stay bit-identical)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.core.physics

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrajectorySampleTest {

    private fun launch() = LaunchConditions(
        ballSpeedMps = 70.0, launchAngleDeg = 13.0, launchDirDeg = 0.0, spinRpm = 3000.0, spinAxisDeg = 0.0,
    )

    @Test
    fun `samples start at launch and are monotonic in time`() {
        val s = FlightSolver.solve(Vec3(60.0, 10.0, 12.0), Vec3(300.0, 0.0, 0.0), 13.0, Environment())
        val samples = s.samples
        assertTrue(samples.isNotEmpty())
        assertEquals(0.0, samples[0].tSec, 1e-12)
        assertEquals(0.0, samples[0].px, 1e-12)
        assertEquals(0.0, samples[0].py, 1e-12)
        assertEquals(0.0, samples[0].pz, 1e-12)
        for (i in 1 until samples.size) {
            assertTrue(samples[i].tSec > samples[i - 1].tSec)
        }
    }

    @Test
    fun `sample count matches flight time at 10ms resolution`() {
        val s = FlightSolver.solve(Vec3(60.0, 10.0, 12.0), Vec3(300.0, 0.0, 0.0), 13.0, Environment())
        val dt = 0.01
        assertTrue(abs(s.samples.size - (s.flightTimeSec / dt + 1)) <= 3)
    }

    @Test
    fun `last sample is the interpolated landing point at z = 0`() {
        val s = FlightSolver.solve(Vec3(60.0, 10.0, 12.0), Vec3(300.0, 0.0, 0.0), 13.0, Environment())
        val last = s.samples.last()
        assertEquals(0.0, last.pz, 1e-9)
        assertEquals(s.flightTimeSec, last.tSec, 1e-12)
        assertEquals(s.position.x, last.px, 1e-9)
        assertEquals(s.position.y, last.py, 1e-9)
    }
}
```

(If `LaunchConditions` field names differ, match the real ones in `LaunchConditions.kt` — the test only needs any realistic launch.)

- [ ] **Step 2: Run test to verify it fails**

`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.TrajectorySampleTest"` → FAIL: unresolved `TrajectorySample` / `samples`.

- [ ] **Step 3: Implement**

Create `TrajectorySample.kt`:
```kotlin
package com.hpsmiles.golfsim.core.physics

/** One point of the modelled flight. tSec is solver time (first sample = 0 at launch). */
data class TrajectorySample(
    val px: Double,
    val py: Double,
    val pz: Double,
    val tSec: Double,
)
```

In `FlightSolver.kt`, add BEFORE the `while (true)` loop:
```kotlin
        val samples = ArrayList<TrajectorySample>()
        samples.add(TrajectorySample(px, py, pz, t))
```
Inside the loop, immediately AFTER `if (pz > apex) apex = pz` (pure bookkeeping — integration math untouched so golden values stay bit-identical):
```kotlin
            samples.add(TrajectorySample(px, py, pz, t))
```
Change the `return` to build the interpolated landing sample and pass `samples`:
```kotlin
        val f = if (vz != 0.0) Math.min(Math.max(pz / (vz * DT), 0.0), 1.0) else 0.5
        val landingTime = t - f * DT
        samples.add(TrajectorySample(
            px - f * vx * DT, py - f * vy * DT, pz - f * vz * DT, landingTime,
        ))
        return LandingState(
            position = Vec3(px - f * vx * DT, py - f * vy * DT, pz - f * vz * DT),
            velocity = Vec3(vx, vy, vz),
            spin = spin,
            apexM = apex,
            flightTimeSec = landingTime,
            samples = samples,
        )
```

Add `val samples: List<TrajectorySample> = emptyList()` to `LandingState` (defaulted, so other constructor callers compile).

- [ ] **Step 4: Run full physics test suite — all existing tests must pass unchanged**

`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test` → PASS.

- [ ] **Step 5: Commit**
```powershell
git add core/physics
git commit -m "physics: collect trajectory samples during flight solve (additive, goldens unchanged)"
```

### Task 2: Expose samples on `ShotResult` (`:core:physics`)

**Files:**
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ShotResult.kt`
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BallFlightEngine.kt:43-50`

- [ ] **Step 1: Add field + copy in engine**

`ShotResult` gains (additive; named-arg construction elsewhere is unaffected):
```kotlin
    val samples: List<TrajectorySample> = emptyList(),
```
In `BallFlightEngine.simulate`, add to the `ShotResult(...)` construction:
```kotlin
            samples = landing.samples,
```

- [ ] **Step 2: Run full module tests**

`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test` → PASS.

- [ ] **Step 3: Commit**
```powershell
git add core/physics
git commit -m "physics: expose trajectory samples on ShotResult"
```

### Task 3: `RangeScene` — painted-range definition (pure Kotlin, JVM-testable)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScene.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSceneTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.range

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeSceneTest {

    @Test
    fun `groundHeight is flat zero this phase`() {
        assertEquals(0.0, RangeScene.groundHeight(5.0, 100.0), 0.0)
        assertEquals(0.0, RangeScene.groundHeight(-30.0, 12.0), 0.0)
    }

    @Test
    fun `fairway bounds taper from tee to end`() {
        assertTrue(RangeScene.isOnFairway(0.0, 100.0))
        assertFalse(RangeScene.isOnFairway(30.0, 100.0))
        assertFalse(RangeScene.isOnFairway(0.0, 190.0))
        assertFalse(RangeScene.isOnFairway(0.0, 5.0))
        // Width ~40 m at tee, widening slightly toward y = 180.
        assertTrue(abs(RangeScene.fairwayHalfWidth(10.0) - 20.0) < 0.5)
        assertTrue(RangeScene.fairwayHalfWidth(180.0) > 20.0)
    }

    @Test
    fun `greens and fringes at target positions`() {
        assertEquals(3, RangeScene.greens.size)
        val g = RangeScene.greens.first { abs(it.lateralM) < 1e-9 }
        assertEquals(100.0, g.distanceM, 1e-9)
        assertEquals(7.0, g.radiusM, 1e-9)
        assertEquals(9.5, g.fringeRadiusM, 1e-9)
        assertTrue(g.isFringe(g.lateralM + 8.0, g.distanceM))   // 8 m off-centre: fringe
        assertFalse(g.isFringe(g.lateralM + 5.0, g.distanceM))  // inside surface
    }

    @Test
    fun `stripe parity alternates every 12 m`() {
        assertEquals(RangeScene.stripeIsLight(0.0, 12.0), !RangeScene.stripeIsLight(0.0, 0.0))
        assertEquals(RangeScene.stripeIsLight(0.0, 24.0), RangeScene.stripeIsLight(0.0, 0.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.RangeSceneTest"` → FAIL (unresolved `RangeScene`). *(If the app unit-test task is named differently, use `.\gradlew.bat :app:test`.)*

- [ ] **Step 3: Implement**

```kotlin
package com.hpsmiles.golfsim.range

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Painted-range scene definition (pure data + geometry, no rendering).
 * All coordinates are the shared metres world frame: x lateral, y down-range,
 * z up. Topography seam: [groundHeight] returns 0 this phase; future terrain
 * is a data change here, not a renderer rewrite.
 */
object RangeScene {

    const val FAIRWAY_TEE_Y = 10.0
    const val FAIRWAY_END_Y = 180.0
    const val GROUND_END_Y = 200.0
    const val STRIPE_WIDTH_M = 12.0

    private const val HALF_WIDTH_TEE = 20.0
    private const val HALF_WIDTH_END = 23.0

    /** Green with fringe (matching target-oval positions). */
    data class Green(
        val lateralM: Double,
        val distanceM: Double,
        val radiusM: Double,
        val fringeRadiusM: Double,
    ) {
        fun isFringe(x: Double, y: Double): Boolean {
            val dx = x - lateralM
            val dy = y - distanceM
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            return r <= fringeRadiusM
        }

        fun isSurface(x: Double, y: Double): Boolean {
            val dx = x - lateralM
            val dy = y - distanceM
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            return r <= radiusM
        }
    }

    val greens = listOf(
        Green(-12.0, 75.0, 7.0, 9.5),
        Green(0.0, 100.0, 7.0, 9.5),
        Green(12.0, 150.0, 7.0, 9.5),
    )

    /** Topography seam — flat this phase (spec non-goal: no topography yet). */
    fun groundHeight(x: Double, y: Double): Double = 0.0

    /** ~40 m wide at the tee, widening to ~46 m at y = 180. */
    fun fairwayHalfWidth(y: Double): Double {
        val t = ((y - FAIRWAY_TEE_Y) / (FAIRWAY_END_Y - FAIRWAY_TEE_Y)).coerceIn(0.0, 1.0)
        return HALF_WIDTH_TEE + (HALF_WIDTH_END - HALF_WIDTH_TEE) * t
    }

    fun isOnFairway(x: Double, y: Double): Boolean =
        y in FAIRWAY_TEE_Y..FAIRWAY_END_Y && abs(x) <= fairwayHalfWidth(y) && greens.none { it.isSurface(x, y) }

    /** Even bands from the tee; true = lighter mow stripe. */
    fun stripeIsLight(x: Double, y: Double): Boolean =
        kotlin.math.floor((y - FAIRWAY_TEE_Y) / STRIPE_WIDTH_M).toInt() % 2 == 0

    /** Fairway outline as world vertices (closed as polygon by caller). */
    fun fairwayOutline(): List<Pair<Double, Double>> {
        val steps = 17
        val out = ArrayList<Pair<Double, Double>>(steps * 2)
        for (i in 0..steps) {
            val y = FAIRWAY_TEE_Y + (FAIRWAY_END_Y - FAIRWAY_TEE_Y) * i / steps
            out.add(fairwayHalfWidth(y) to y)
        }
        for (i in steps downTo 0) {
            val y = FAIRWAY_TEE_Y + (FAIRWAY_END_Y - FAIRWAY_TEE_Y) * i / steps
            out.add(-fairwayHalfWidth(y) to y)
        }
        return out
    }

    /** Circle outline as world vertices (n points), projected as a ground polygon. */
    fun circleOutline(cx: Double, cy: Double, r: Double, n: Int = 40): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>(n)
        for (i in 0 until n) {
            val a = 2.0 * PI * i / n
            out.add(cx + r * cos(a) to cy + r * sin(a))
        }
        return out
    }

    /** Stripe band trapezoid on the fairway between yFrom and yTo (clipped to fairway extent). */
    fun stripeBand(index: Int): Pair<Double, Double> {
        val yFrom = FAIRWAY_TEE_Y + index * STRIPE_WIDTH_M
        val yTo = minOf(yFrom + STRIPE_WIDTH_M, FAIRWAY_END_Y)
        return yFrom to yTo
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.RangeSceneTest"` → PASS.

- [ ] **Step 5: Commit**
```powershell
git add app/src
git commit -m "range: add RangeScene painted-ground definition (pure Kotlin, JVM-tested)"
```

### Task 4: Paint the range in `PovRangeCanvas.kt` (palette A)

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem` — no changes; use local `private val` colors per palette block above.

- [ ] **Step 1: Replace flat ground gradients with painted ground layers**

After the sky `drawRect`, replace the single ground gradient with, in order:
1. Rough base polygon: world rectangle corners (±80, 10) … (±80, `RangeScene.GROUND_END_Y`) projected via `worldToScreen` → `drawPath` fill `ROUGH_BASE`. Simplest robust fill: draw a full-ground rect gradient `ROUGH_BASE → ROUGH_BASE` from horizon to bottom, then draw fairway on top.
2. Fairway: project `RangeScene.fairwayOutline()` vertices with `groundHeight` z = 0 → closed `Path`, fill `FAIRWAY`.
3. Mow stripes: for `index in 0..14` (`RangeScene.stripeBand(index)` gives the y-range), build a trapezoid from `fairwayHalfWidth(yFrom)`/`fairwayHalfWidth(yTo)` (4 world points at z=0), fill `STRIPE_LIGHT` if `RangeScene.stripeIsLight(0.0, yFrom)` else `STRIPE_DARK`. Stop when `yFrom >= FAIRWAY_END_Y`.
4. Greens: for each `RangeScene.green` — fringe circle outline (`RangeScene.circleOutline(lateral, distance, fringeRadiusM)`) filled `FRINGE`, then surface circle filled `GREEN_SURFACE`.
5. Existing distance-band lines + labels: drawn AFTER the ground layers (they already are below the targets — keep that order), recolor label paint to `Color.White` argb for "Tour Broadcast" crispness; band lines white `alpha = 0.45f` instead of `GolfColors.Line`.
6. Existing teal target ovals unchanged, still on top.
7. Horizon haze LAST among ground layers: a vertical gradient rect from `horizonPx` down ~`h * 0.15` with color `HAZE` → transparent (`Brush.verticalGradient`), drawn after everything ground-level (before tracers is acceptable; draw right after labels/ovals).

- [ ] **Step 2: Sanity build**

`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:compileDebugKotlin` → succeeds.

- [ ] **Step 3: Commit**
```powershell
git add app/src
git commit -m "range: paint Tour Broadcast ground — fairway, stripes, greens, haze"
```

### Task 5: Real-arc tracer in `PovRangeCanvas.kt`

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt` (delete lines ~129-156 stylized `tracerPath`, rework ball-head block ~179-198)
- Modify: doc comment at top of file (remove "stylized quadratic / Phase C deviation" note; state tracer = modelled ODE samples)

- [ ] **Step 1: Replace stylized tracer with sample projector**

Delete `tracerPath()` and the duplicated apex-scale block in the ball-head section. Add:

```kotlin
        // Real-trajectory polyline: project the modelled flight samples.
        // apexScale (single scalar, top-8% rule) keeps big apexes in frame.
        fun scaledSamples(s: ShotResult): List<TrajectorySample> {
            if (s.samples.isEmpty()) return emptyList()
            val yApex = s.carryM / 2.0
            val vApex = (PovProjector.CAM_HEIGHT_M - s.apexM) / yApex
            val apexScale = if (vApex < vMin) {
                ((PovProjector.CAM_HEIGHT_M - vMin * yApex) / s.apexM).coerceIn(0.1, 1.0)
            } else 1.0
            if (apexScale >= 1.0) return s.samples
            return s.samples.map { it.copy(pz = it.pz * apexScale) }
        }

        fun drawTracer(s: ShotResult, timeSec: Double, color: Color, width: Float) {
            val samples = scaledSamples(s)
            if (samples.size < 2) return
            val path = androidx.compose.ui.graphics.Path()
            var first = true
            for (sample in samples) {
                if (sample.tSec > timeSec && !first) break
                var point = worldToScreen(centerX, focalPx, horizonPx, sample.px, sample.py, sample.pz)
                if (point == null || point.y > h - 8f) point = launchAnchor
                if (first) { path.moveTo(point.x, point.y); first = false } else path.lineTo(point.x, point.y)
            }
            if (!first) drawPath(path, color, style = Stroke(width = width))
        }
```

History (replace the `tracerPath(prev, 1f)` call):
```kotlin
                drawTracer(prev, Double.POSITIVE_INFINITY, HISTORY_LINE, 2f)
```

Current tracer:
```kotlin
                drawTracer(s, playFraction * s.flightTimeSec, GolfColors.Amber, 2.5f)
```

Ball head — project the sample nearest the animation time (replace the whole ball-head block):
```kotlin
            if (showTracer) {
                val timeSec = playFraction * s.flightTimeSec
                val samples = scaledSamples(s)
                val head = samples.lastOrNull { it.tSec <= timeSec } ?: samples.firstOrNull()
                var headPos = head?.let { worldToScreen(centerX, focalPx, horizonPx, it.px, it.py, it.pz) }
                if (headPos == null || headPos.y > h - 8f) headPos = launchAnchor
                drawCircle(GolfColors.AmberGlow, radius = 12.sp.toPx(), center = headPos)
                drawCircle(GolfColors.Amber, radius = 6.sp.toPx(), center = headPos)
            }
```
Landing dot/ring block unchanged.

Add import: `import com.hpsmiles.golfsim.core.physics.TrajectorySample`.

- [ ] **Step 2: Full build + all tests**

`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build` → BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Commit**
```powershell
git add app/src
git commit -m "range: tracer follows real ODE trajectory samples"
```

### Task 6: Manual smoke + closeout

- [ ] **Step 1: Install on tablet**
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:installDebug
```
- [ ] **Step 2: Manual checks (FIRE button, demo source)**
  - Tracer bends realistically (curves with spin axis), not a symmetric parabola.
  - Big driver apexes stay in frame (top-8% clamp works) — expect near-vertical launch, ball exits top, re-enters onto the ring (matches approved mockup).
  - Stripes/rough/greens render; distance labels legible white; haze fades near horizon.
  - 1x/1.5x/2x/4x speed multipliers and history fading still work; top-down canvas unchanged.
- [ ] **Step 3: Commit any fixes, then**
```powershell
git add -A; git commit -m "range: manual smoke fixes for painted range + real tracer"
```

---

## Out of scope (recorded)

Topography via `groundHeight`, trees, 3D engines, top-down changes, metric UI changes.
