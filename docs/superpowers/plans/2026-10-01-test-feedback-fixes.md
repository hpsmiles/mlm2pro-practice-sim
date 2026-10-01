# Test-Feedback Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the 7 user-reported issues from tablet testing: side-spin run-out, punch rollout, custom range green, mat in all modes, pane distance/size, pane green sizes, and panel overdraw.

**Architecture:** Pure-JVM physics changes in `core/physics` (BounceRollModel skid + side-spin kick) pinned by deterministic tests; app-layer changes in `app` (RangeSession custom green state, shared mat painter, clipToBounds, PaneGeom/BreakPaneGreen constants). Spec: `docs/superpowers/specs/2026-10-01-test-feedback-fixes-design.md`.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas/DrawScope), JUnit4. Physics modules are pure JVM — no Robolectric, no Android deps in tests.

## Global Constraints

- Windows: set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before ANY gradle command.
- Gradle flags precede task names; runs take 5s–2min — quiet ≠ hung.
- Full build + all tests: `.\gradlew.bat build`. One test class: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.RangeSurfaceTest" --console=plain` (the aggregate `test` task rejects `--tests`; always use `testDebugUnitTest`). Physics module tests: `.\gradlew.bat :core:physics:testDebugUnitTest --tests "..." --console=plain` or `:core:physics:test`.
- Physics/geometry code must stay pure/deterministic (same inputs → same outputs), unit-testable without a physical MLM2PRO.
- The custom range green is session-scoped state on `RangeSession` — NEVER persisted to Room (no schema change, no migration). Game code untouched except rendering/surface wiring.
- Room schema: untouched by this plan.
- Do not add cloud/PC dependencies.
- Commit after each task with the message given in the task.

---

### Task 1: Clip canvas containers so panels never get overdrawn (item 7)

The range POV canvas draws follow-cam frames wider than its composable bounds, so the flight paints over the NavRail on the left. Compose does NOT clip drawing to bounds by default. Fix: `.clipToBounds()` on the three canvas containers.

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt:160`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt:79`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticePlay.kt:95`

**Interfaces:**
- Consumes: nothing.
- Produces: clipped canvas containers (no API change).

- [ ] **Step 1: RangeScreen — clip the POV/top-down canvas container**

In `RangeScreen.kt`, line 160, change:

```kotlin
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
```

to:

```kotlin
            BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
```

Add the import at the top of the file (with the other `androidx.compose.ui` imports):

```kotlin
import androidx.compose.ui.draw.clipToBounds
```

- [ ] **Step 2: BreakPanePlay — clip the camera/canvas Box**

In `BreakPanePlay.kt`, line 79, change:

```kotlin
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
```

to:

```kotlin
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
```

Add the import:

```kotlin
import androidx.compose.ui.draw.clipToBounds
```

- [ ] **Step 3: TargetPracticePlay — clip the canvas BoxWithConstraints**

In `TargetPracticePlay.kt`, line 95, change:

```kotlin
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
```

to:

```kotlin
                BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
```

Add the import:

```kotlin
import androidx.compose.ui.draw.clipToBounds
```

- [ ] **Step 4: Compile**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticePlay.kt
git commit -m "fix: clip canvas containers so flight no longer paints over panels"
```

---

### Task 2: Range runs on firm fairway (item 2a)

The range simulates every shot on `Surface.FAIRWAY_NORMAL` (`RangeSession.kt:63`). Switch to FIRM firmness: rollout decel ×0.60, cor ×1.08.

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSurfaceTest.kt` (create)

**Interfaces:**
- Consumes: `Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM)` from `core/physics`.
- Produces: `RangeSession.RANGE_SURFACE: Surface` (internal companion val, JVM-visible) — used by Task 5's `surfaceProviderFor`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSurfaceTest.kt`:

```kotlin
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeSurfaceTest {

    @Test
    fun `range turf is firm fairway`() {
        assertEquals("fairway (firm)", RangeSession.RANGE_SURFACE.name)
        assertTrue(RangeSession.RANGE_SURFACE.rollDecelMps2 < Surface.FAIRWAY_NORMAL.rollDecelMps2)
        assertTrue(RangeSession.RANGE_SURFACE.cor > Surface.FAIRWAY_NORMAL.cor)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.RangeSurfaceTest" --console=plain`
Expected: FAIL — `RANGE_SURFACE` unresolved.

- [ ] **Step 3: Implement**

In `RangeSession.kt` add the import (next to the other `core.physics` imports):

```kotlin
import com.hpsmiles.golfsim.core.physics.Firmness
```

Change the `private companion object` (line 118) to `internal companion object` and add `RANGE_SURFACE`:

```kotlin
    internal companion object {
        /** Range turf (item 2): firm fairway — punch shots must release, not die. */
        val RANGE_SURFACE: Surface = Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM)

        const val MISREAD_COALESCE_MS = 500L
    }
```

Change `add()` line 63 from:

```kotlin
            UniformSurface(Surface.FAIRWAY_NORMAL),
```

to:

```kotlin
            UniformSurface(RANGE_SURFACE),
```

(`UniformSurface` and `Surface` imports already exist.)

- [ ] **Step 4: Run test to verify it passes**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.RangeSurfaceTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSurfaceTest.kt
git commit -m "feat: simulate range shots on firm fairway"
```

---

### Task 3: Shallow low-spin impacts skid and release (item 2b)

In `BounceRollModel`, the non-Penner branch multiplies horizontal speed by `retention <= 0.55`, so a shallow punch lands, loses half its speed, and dies. Add an angle-aware skid boost: shallow impacts (below `thetaCritRad`) with low spin retain more speed; high-spin wedges get zero boost.

**Files:**
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModel.kt` (constants near line 23; branch at lines 71-73)
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollSkidTest.kt` (create)

**Interfaces:**
- Consumes: `LandingState`, `Surface`, `Environment`, `GroundResult` (existing).
- Produces: changed `BounceRollModel.bounceAndRoll` behavior for shallow impacts only. No signature changes. New private constants `SHALLOW_SKID_MAX`, `SHALLOW_SKID_SPIN_RPM`.

- [ ] **Step 1: Write the failing tests**

Create `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollSkidTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 2 (2026-10-01): punch shots landing shallow on firm fairway must
 * release and run out; high-spin wedges keep checking up.
 */
class BounceRollSkidTest {

    /** Landing state with a given descent angle (rad) and backspin (rpm), straight at the target. */
    private fun landing(impactAngleRad: Double, spinRpm: Double, vh: Double = 25.0): LandingState {
        val vn = vh * Math.tan(impactAngleRad)
        return LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, vh, -vn),
            spin = Vec3(spinRpm * 2.0 * Math.PI / 60.0, 0.0, 0.0),
            apexM = 10.0,
            flightTimeSec = 4.0,
        )
    }

    private fun rollDistance(g: GroundResult): Double = Math.hypot(g.deltaX, g.deltaY)

    @Test
    fun `shallow low-spin punch releases instead of dying`() {
        val fairway = Surface.FAIRWAY_NORMAL
        val shallow = BounceRollModel.bounceAndRoll(landing(Math.toRadians(7.0), 2500.0), fairway, 10.0, Environment())
        val steep = BounceRollModel.bounceAndRoll(landing(Math.toRadians(25.0), 2500.0), fairway, 10.0, Environment())
        assertTrue(
            "shallow (${rollDistance(shallow)}) must out-roll steep (${rollDistance(steep)})",
            rollDistance(shallow) > rollDistance(steep),
        )
        assertTrue("punch run-out must be substantial, got ${rollDistance(shallow)}", rollDistance(shallow) > 25.0)
    }

    @Test
    fun `high-spin wedge keeps checking up on shallow impacts`() {
        val fairway = Surface.FAIRWAY_NORMAL
        val wedge = BounceRollModel.bounceAndRoll(landing(Math.toRadians(7.0), 9000.0), fairway, 10.0, Environment())
        val punch = BounceRollModel.bounceAndRoll(landing(Math.toRadians(7.0), 2500.0), fairway, 10.0, Environment())
        assertTrue(
            "wedge (${rollDistance(wedge)}) must out-check punch (${rollDistance(punch)})",
            rollDistance(wedge) < rollDistance(punch),
        )
    }

    @Test
    fun `firm fairway out-rolls normal fairway for the same punch`() {
        val punch = landing(Math.toRadians(7.0), 2500.0)
        val normal = BounceRollModel.bounceAndRoll(punch, Surface.FAIRWAY_NORMAL, 10.0, Environment())
        val firm = BounceRollModel.bounceAndRoll(punch, Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM), 10.0, Environment())
        assertTrue(
            "firm (${rollDistance(firm)}) must out-roll normal (${rollDistance(normal)})",
            rollDistance(firm) > rollDistance(normal),
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.physics.BounceRollSkidTest" --console=plain`
Expected: FAIL on `shallow low-spin punch releases instead of dying` (punch run-out currently small).

- [ ] **Step 3: Implement the skid boost**

In `BounceRollModel.kt`, add constants after `NON_DOMINANT_FORWARD_KEEP` (line 24):

```kotlin
    // Item 2 (2026-10-01): shallow, low-spin impacts skid and release instead
    // of dying — punch shots must run out on firm fairway. The boost ramps in
    // as the impact angle drops below theta_crit and fades to zero as spin
    // approaches SHALLOW_SKID_SPIN_RPM, so high-spin wedges keep checking up.
    private const val SHALLOW_SKID_MAX = 0.30
    private const val SHALLOW_SKID_SPIN_RPM = 4000.0
```

In `bounceAndRoll`, replace lines 72–73:

```kotlin
                retention = 0.55 * Math.max(Math.min(1.0 - rpmNow / 8000.0, 1.0), 0.40)
                val steep = impactAngle >= surface.thetaCritRad
```

with:

```kotlin
                retention = 0.55 * Math.max(Math.min(1.0 - rpmNow / 8000.0, 1.0), 0.40)
                val steep = impactAngle >= surface.thetaCritRad
                if (!steep) {
                    val shallow = (surface.thetaCritRad - impactAngle) / surface.thetaCritRad
                    val lowSpin = Math.max(0.0, 1.0 - rpmNow / SHALLOW_SKID_SPIN_RPM)
                    retention += SHALLOW_SKID_MAX * shallow * lowSpin
                }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.physics.BounceRollSkidTest" --console=plain`
Expected: PASS (all 3).

- [ ] **Step 5: Run the whole physics module — no regressions**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test --console=plain`
Expected: PASS. If a tour-fixture test pins exact rollout numbers and now fails, STOP and report — those pins are deliberate (spec §8); constants may need retuning with the user, not silent fixture edits.

- [ ] **Step 6: Commit**

```bash
git add core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModel.kt core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollSkidTest.kt
git commit -m "feat: shallow low-spin bounces skid and release for punch rollout"
```

---

### Task 4: Side-spin kicks the run out toward the curve direction (item 1)

`spin.z` (the vertical-axis/yaw component that curves the ball in flight) never enters the ground model — the run-out direction is hard-wired to the incoming horizontal vector. Add a lateral friction kick per bounce, proportional to yaw and impact speed, capped, scaled by `surface.spinbackScale`, and preserve yaw across bounces. Sign: per `BallFlightEngine.kt:31`, +spinAxisDeg (right-curving flight) yields `spin.z < 0`, so negate to get "+yaw kicks right".

**Files:**
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModel.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollGroundKickTest.kt` (create)

**Interfaces:**
- Consumes: `LandingState.spin` (rad/s vector; `.z` = yaw).
- Produces: `GroundResult.deltaX/deltaY` now include lateral kick; `spin.z` persists across bounces (decayed by `spinRetention`). No signature changes.

- [ ] **Step 1: Write the failing tests**

Create `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollGroundKickTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 1 (2026-10-01): the ball always ran out straight after landing.
 * Yaw spin (spin.z) must deflect the bounce/roll toward the direction the
 * ball was curving. Per BallFlightEngine, +spinAxisDeg (right-curving flight)
 * gives spin.z < 0, so a RIGHT-curving ball has NEGATIVE spin.z.
 */
class BounceRollGroundKickTest {

    /** Straight-down-range landing with pure backspin plus a yaw component (rad/s). */
    private fun landing(yawRadPerSec: Double): LandingState = LandingState(
        position = Vec3(0.0, 0.0, 0.0),
        velocity = Vec3(0.0, 28.0, -6.0),
        spin = Vec3(550.0, 0.0, yawRadPerSec),
        apexM = 12.0,
        flightTimeSec = 4.5,
    )

    @Test
    fun `side spin deflects run out toward the curve direction`() {
        val right = BounceRollModel.bounceAndRoll(landing(-250.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        val left = BounceRollModel.bounceAndRoll(landing(250.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        assertTrue("right-curving ball must kick right, deltaX=${right.deltaX}", right.deltaX > 0.0)
        assertTrue("left-curving ball must kick left, deltaX=${left.deltaX}", left.deltaX < 0.0)
        assertEquals("mirror symmetry", -left.deltaX, right.deltaX, 1e-6)
    }

    @Test
    fun `zero side spin keeps the run out straight`() {
        val straight = BounceRollModel.bounceAndRoll(landing(0.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        assertEquals(0.0, straight.deltaX, 1e-6)
    }

    @Test
    fun `kick is capped so extreme sidespin cannot sling the ball sideways`() {
        val huge = BounceRollModel.bounceAndRoll(landing(-4000.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        val modest = BounceRollModel.bounceAndRoll(landing(-250.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        assertTrue("capped deflection ${huge.deltaX} must stay bounded", huge.deltaX < 10.0)
        assertTrue("capped kick must still exceed modest kick", huge.deltaX > modest.deltaX)
    }

    @Test
    fun `softer surfaces kick more than firm ones`() {
        val firm = BounceRollModel.bounceAndRoll(landing(-250.0), Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM), 12.0, Environment())
        val normal = BounceRollModel.bounceAndRoll(landing(-250.0), Surface.FAIRWAY_NORMAL, 12.0, Environment())
        assertTrue(normal.deltaX > firm.deltaX)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.physics.BounceRollGroundKickTest" --console=plain`
Expected: FAIL on `side spin deflects run out toward the curve direction` (deltaX == 0 today).

- [ ] **Step 3: Implement the kick**

In `BounceRollModel.kt`, add constants next to `SHALLOW_SKID_MAX` (Task 3):

```kotlin
    // Item 1 (2026-10-01): vertical-axis (yaw) spin — the sidespin that curves
    // the ball in flight — must also shape the ground phase. Friction on a
    // yawing ball deflects each bounce and the final roll toward the curve
    // direction. Per BallFlightEngine, +spinAxisDeg (right curve) gives
    // spin.z < 0, so the kick uses -spin.z ("+" = kick right). Capped and
    // scaled by the surface's spinbackScale (greens bite harder than fairway).
    private const val SIDE_KICK_GAIN = 0.0008
    private const val SIDE_KICK_CAP_MPS = 2.5
```

In `bounceAndRoll`, replace lines 113–121:

```kotlin
            val vzNew = vn * cor
            val mag = Math.abs(newTan)
            val sgn = Math.signum(newTan)
            vx = hx * mag * sgn
            vy = hy * mag * sgn
            vz = vzNew
            spin = Vec3(tAx, tAy, 0.0).times(
                (if (fromFlight) mag / BallPhysical.RADIUS_M else Math.abs(omegaT)) * surface.spinRetention
            )
```

with:

```kotlin
            val vzNew = vn * cor
            val mag = Math.abs(newTan)
            val sgn = Math.signum(newTan)
            vx = hx * mag * sgn
            vy = hy * mag * sgn
            // Side-spin ground kick: deflect the new horizontal velocity toward
            // the curve direction. Right of travel (hx, hy) is (hy, -hx).
            val yawRight = -spin.z
            if (yawRight != 0.0) {
                val kick = (yawRight * SIDE_KICK_GAIN * vh * surface.spinbackScale)
                    .coerceIn(-SIDE_KICK_CAP_MPS, SIDE_KICK_CAP_MPS)
                vx += hy * kick
                vy -= hx * kick
            }
            vz = vzNew
            // Preserve the yaw component across bounces (decayed by spin
            // retention); the tangential components keep the existing law.
            val spinScale = (if (fromFlight) mag / BallPhysical.RADIUS_M else Math.abs(omegaT)) * surface.spinRetention
            spin = Vec3(tAx * spinScale, tAy * spinScale, spin.z * surface.spinRetention)
```

The kick runs BEFORE the hop solve (lines 136-145), so hop trajectories also curve — `vx/vy` feed `FlightSolver.solve`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.physics.BounceRollGroundKickTest" --console=plain`
Expected: PASS (all 4).

- [ ] **Step 5: Run the whole physics module — no regressions**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test --console=plain`
Expected: PASS. If a test pinning exact rest positions fails (e.g. `RestPositionTest`, `BallFlightEngineTest` crosswind pins), check its shot's `spinAxisDeg`: zero-yaw shots are bit-identical to before (kick is 0), so those must NOT fail. A failing pin on a nonzero-axis shot is an EXPECTED behavior change: update the pin only if the new value is physically sensible, and note it in the commit message.

- [ ] **Step 6: Commit**

```bash
git add core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModel.kt core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollGroundKickTest.kt
git commit -m "feat: side-spin kick deflects bounce and roll toward the curve direction"
```

---

### Task 5: Custom range green — session state + physics wiring (item 3, part 1)

Add an optional user-defined green to `RangeSession` (session-scoped, never persisted) and switch range simulation to a green-oval provider when it exists.

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSurfaceTest.kt` (extend)

**Interfaces:**
- Consumes: `RangeScene.Green` (existing data class — kept; only the hardcoded `greens` list dies in Task 6), `GreenZoneSurfaceProvider(centerX, centerY, radiusM)` from core/physics, `RangeSession.RANGE_SURFACE` from Task 2.
- Produces (used by Task 6):
  - `RangeSession.customGreen: androidx.compose.runtime.State<RangeScene.Green?>`
  - `RangeSession.setGreen(distanceM: Double, radiusM: Double)`
  - `RangeSession.clearGreen()`
  - Top-level `internal fun surfaceProviderFor(green: Green?): SurfaceProvider` in RangeSession.kt (JVM-testable)

- [ ] **Step 1: Write the failing tests**

Append to `RangeSurfaceTest.kt`:

```kotlin
    @Test
    fun `no custom green means firm fairway everywhere`() {
        val provider = surfaceProviderFor(null)
        assertEquals(RangeSession.RANGE_SURFACE, provider.surfaceAt(0.0, 120.0))
        assertEquals(RangeSession.RANGE_SURFACE, provider.surfaceAt(50.0, 300.0))
    }

    @Test
    fun `custom green is green inside the oval and firm fairway outside`() {
        val provider = surfaceProviderFor(RangeScene.Green(0.0, 120.0, 8.0, 10.8))
        assertEquals(Surface.GREEN_NORMAL, provider.surfaceAt(0.0, 120.0))
        assertEquals(Surface.GREEN_NORMAL, provider.surfaceAt(5.0, 123.0))
        assertEquals(RangeSession.RANGE_SURFACE, provider.surfaceAt(0.0, 140.0))
    }
```

(`RangeScene.Green` is in the same `com.hpsmiles.golfsim.range` package — no import needed.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.RangeSurfaceTest" --console=plain`
Expected: FAIL — `surfaceProviderFor` unresolved.

- [ ] **Step 3: Implement**

In `RangeSession.kt` add imports:

```kotlin
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.hpsmiles.golfsim.core.physics.GreenZoneSurfaceProvider
import com.hpsmiles.golfsim.core.physics.SurfaceProvider
```

Inside the class, after `val misreadCount = mutableIntStateOf(0)` (line 31), add:

```kotlin
    /**
     * Item 3 (2026-10-01): optional user-defined practice green. Session-scoped,
     * NEVER persisted — a restart starts with no green. Null = no green: the
     * range is plain (firm) fairway.
     */
    private val _customGreen = mutableStateOf<Green?>(null)
    val customGreen: State<Green?> get() = _customGreen

    fun setGreen(distanceM: Double, radiusM: Double) {
        _customGreen.value = Green(
            lateralM = 0.0,
            distanceM = distanceM,
            radiusM = radiusM,
            fringeRadiusM = radiusM * 1.35,
        )
    }

    fun clearGreen() {
        _customGreen.value = null
    }
```

At the bottom of the file (top level, after the `RangeSession` class), add:

```kotlin
/**
 * Item 3 (2026-10-01): the range simulates on the user's green oval when one
 * is set (green inside the oval, firm fairway outside — same pattern as the
 * games' GreenZoneSurfaceProvider wiring), and on plain firm fairway otherwise.
 */
internal fun surfaceProviderFor(green: Green?): SurfaceProvider =
    green?.let { GreenZoneSurfaceProvider(it.lateralM, it.distanceM, it.radiusM) }
        ?: UniformSurface(RangeSession.RANGE_SURFACE)
```

In `add()`, replace line 63:

```kotlin
            UniformSurface(RANGE_SURFACE),
```

with:

```kotlin
            surfaceProviderFor(_customGreen.value),
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.RangeSurfaceTest" --console=plain`
Expected: PASS (all 3 tests in the class).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSurfaceTest.kt
git commit -m "feat: optional user-defined range green drives simulation surfaces"
```

---

### Task 6: Custom range green — rendering + setup UI, remove hardcoded greens and target ovals (item 3, part 2)

Delete the 3 hardcoded greens (`RangeScene.greens`) and the duplicate teal target ovals (`PovRangeCanvas.kt:158-186`). Render the user's green from `RangeSession.customGreen` and add a GREEN setup chip + overlay on the range screen.

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScene.kt:56-60`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSceneTest.kt` (replace greens test)
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSignsTest.kt` (drop green-clearance clause)

**Interfaces:**
- Consumes: `RangeSession.customGreen`, `setGreen`, `clearGreen` from Task 5.
- Produces: `PovRangeCanvas` gains parameter `customGreen: RangeScene.Green? = null` (defaults null — existing callers compile unchanged).

- [ ] **Step 1: Update the tests**

In `RangeSceneTest.kt`, REPLACE the test `greens and fringes at target positions` (lines 28-37) with:

```kotlin
    @Test
    fun `green oval membership geometry`() {
        val g = RangeScene.Green(0.0, 100.0, 7.0, 9.5)
        assertTrue(g.isSurface(0.0, 100.0))
        assertFalse(g.isSurface(0.0, 110.0))
        assertTrue(g.isFringe(0.0, 108.0))
        assertFalse(g.isFringe(0.0, 105.0))
        assertFalse(g.isSurface(0.0, 60.0))
    }
```

(The `abs` import becomes unused — remove it. `assertFalse`/`assertTrue` already imported.)

In `RangeSignsTest.kt`, REPLACE the test `signs sit just outside the fairway edge and clear of the greens` (lines 16-24) with:

```kotlin
    @Test
    fun `signs sit just outside the fairway edge`() {
        RangeSigns.signPlan().forEach { s ->
            val expected = RangeScene.fairwayHalfWidth(s.distanceM.toDouble()) + RangeSigns.EDGE_OFFSET_M
            assertEquals(expected, kotlin.math.abs(s.xM), 1e-9)
        }
    }
```

- [ ] **Step 2: Delete the hardcoded greens list**

In `RangeScene.kt`, DELETE lines 56-60:

```kotlin
    val greens = listOf(
        Green(-12.0, 75.0, 7.0, 9.5),
        Green(0.0, 100.0, 7.0, 9.5),
        Green(12.0, 150.0, 7.0, 9.5),
    )
```

Keep the `Green` data class (used by `RangeSession` and rendering).

- [ ] **Step 3: PovRangeCanvas — render the custom green, drop the target ovals**

In `PovRangeCanvas.kt`:

Add parameter (after `camera`, line 57):

```kotlin
    camera: RangeCamera = RangeCamera.STATIC,
    customGreen: RangeScene.Green? = null,
```

REPLACE the greens loop (lines 103-114):

```kotlin
        // Greens: fringe ring first, putting surface on top.
        for (green in RangeScene.greens) {
            if (green.distanceM < 8.0) continue // near edge would sit at/behind the camera
            val fringe = localGroundPath(
                RangeScene.circleOutline(green.lateralM, green.distanceM, green.fringeRadiusM),
            ) ?: continue
            drawPath(fringe.first, FRINGE.copy(alpha = fringe.second))
            val surface = localGroundPath(
                RangeScene.circleOutline(green.lateralM, green.distanceM, green.radiusM),
            ) ?: continue
            drawPath(surface.first, GREEN_SURFACE.copy(alpha = surface.second))
        }
```

with:

```kotlin
        // User-defined green (item 3): fringe ring first, putting surface on
        // top. No green set -> plain fairway, nothing drawn.
        customGreen?.let { green ->
            if (green.distanceM >= 8.0) { // near edge would sit at/behind the camera
                val fringe = localGroundPath(
                    RangeScene.circleOutline(green.lateralM, green.distanceM, green.fringeRadiusM),
                )
                if (fringe != null) drawPath(fringe.first, FRINGE.copy(alpha = fringe.second))
                val surface = localGroundPath(
                    RangeScene.circleOutline(green.lateralM, green.distanceM, green.radiusM),
                )
                if (surface != null) drawPath(surface.first, GREEN_SURFACE.copy(alpha = surface.second))
            }
        }
```

DELETE the teal target ovals block (lines 158-186, from `// Teal target ovals at (lateral, distance) metres.` through the for-loop's closing brace). Remove the now-unused import `androidx.compose.ui.geometry.Size` (the only `Size(...)` uses were the ovals). Keep `Stroke` (tracers + mat strokes still use it).

- [ ] **Step 4: RangeScreen — GREEN chip + setup overlay, pass green to the canvas**

In `RangeScreen.kt`:

First READ `ClubPickerOverlay` (later in the same file) and copy its scrim/dismiss mechanics exactly — the overlay below mirrors that in-file precedent.

Add missing imports (check what's already there first):

```kotlin
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
```

Declare state near `showClubPicker`:

```kotlin
    var showGreenPicker by remember { mutableStateOf(false) }
```

In the top-right column (after `ActiveClubButton(...)` at line 256), add the GREEN chip:

```kotlin
                Text(
                    "GREEN: ${session.customGreen.value?.let { "${it.distanceM.toInt()} M" } ?: "OFF"}",
                    color = GolfColors.TextSecondary,
                    style = ChipFont,
                    modifier = Modifier
                        .clickable { showGreenPicker = !showGreenPicker }
                        .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                        .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                )
```

Add the overlay next to the club-picker overlay block (after line 283):

```kotlin
            if (showGreenPicker) {
                GreenSetupOverlay(
                    current = session.customGreen.value,
                    onSet = { dist, radius ->
                        session.setGreen(dist, radius)
                        showGreenPicker = false
                    },
                    onClear = {
                        session.clearGreen()
                        showGreenPicker = false
                    },
                    onDismiss = { showGreenPicker = false },
                )
            }
```

Pass the green to the canvas (lines 172-176):

```kotlin
                if (viewMode == ViewMode.POV) {
                    PovRangeCanvas(
                        currentShot?.shotResult, previousShots, playFraction, showTracer, showHistory,
                        camera = camera,
                        customGreen = session.customGreen.value,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
```

Add the overlay composable at the end of the file (after `ActiveClubButton`):

```kotlin
/**
 * Item 3 (2026-10-01): user-defined practice green — distance + radius, or
 * CLEAR for no green (plain fairway). Mirrors GameSetupScreen's slider+field
 * sync pattern.
 */
@Composable
private fun GreenSetupOverlay(
    current: Green?,
    onSet: (Double, Double) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val distMin = 20.0
    val distMax = 320.0
    val radiusMin = 3.0
    val radiusMax = 20.0

    var distM by remember { mutableStateOf(current?.distanceM ?: 100.0) }
    var distText by remember { mutableStateOf(String.format(Locale.US, "%.0f", current?.distanceM ?: 100.0)) }
    var radiusM by remember { mutableStateOf(current?.radiusM ?: 8.0) }
    var radiusText by remember { mutableStateOf(String.format(Locale.US, "%.0f", current?.radiusM ?: 8.0)) }

    // Scrim covers the range area; tap outside dismisses (same mechanic as
    // ClubPickerOverlay — copy its exact scrim modifiers when adapting).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GolfColors.Base.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        SectionCard(title = "PRACTICE GREEN", modifier = Modifier.padding(GolfSpacing.Xl)) {
            Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md)) {
                Slider(
                    value = distM.toFloat(),
                    onValueChange = {
                        distM = it.toDouble()
                        distText = String.format(Locale.US, "%.0f", distM)
                    },
                    valueRange = distMin.toFloat()..distMax.toFloat(),
                )
                OutlinedTextField(
                    value = distText,
                    onValueChange = { raw ->
                        distText = raw
                        raw.toDoubleOrNull()?.let { distM = it.coerceIn(distMin, distMax) }
                    },
                    label = { Text("Distance (m, 20-320)") },
                    modifier = Modifier.width(260.dp),
                )
                Slider(
                    value = radiusM.toFloat(),
                    onValueChange = {
                        radiusM = it.toDouble()
                        radiusText = String.format(Locale.US, "%.0f", radiusM)
                    },
                    valueRange = radiusMin.toFloat()..radiusMax.toFloat(),
                )
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = { raw ->
                        radiusText = raw
                        raw.toDoubleOrNull()?.let { radiusM = it.coerceIn(radiusMin, radiusMax) }
                    },
                    label = { Text("Radius (m, 3-20)") },
                    modifier = Modifier.width(260.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                    Button(onClick = { onSet(distM, radiusM) }) { Text("SET GREEN") }
                    OutlinedButton(onClick = onClear) { Text("CLEAR") }
                    OutlinedButton(onClick = onDismiss) { Text("CANCEL") }
                }
            }
        }
    }
}
```

Adjust imports/section-card signature to match what the file actually provides (`SectionCard`, `ChipFont`, `GolfColors`, `Locale` are already used in `RangeScreen.kt`).

- [ ] **Step 5: Run app unit tests**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: PASS. Verify no other references remain: `git grep -n "\.greens"` → no matches.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScene.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSceneTest.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSignsTest.kt
git commit -m "feat: user-defined range green replaces hardcoded greens and target ovals"
```

---

### Task 7: Range mat in all modes (item 4)

Extract the mat painter from `PovRangeCanvas` into `RangeDecorations.drawRangeMat(cam, w, h)`; call it from `GameScene.drawGameGround` so both games show the mat, and from `PovRangeCanvas` (unchanged visuals). `TopDownCanvas` already draws the mat.

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeDecorations.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GameScene.kt:76-81`

**Interfaces:**
- Consumes: `RangeMat` constants (BASE_*/STRIP_*/LINE_HALF_WIDTH_M + color vals), `RangeScene.groundHeight`, `PovProjector.project`.
- Produces: `RangeDecorations.drawRangeMat(cam: RangeCamera, w: Float, h: Float)` — a `DrawScope` extension used by both the range and the games.

- [ ] **Step 1: Add drawRangeMat to RangeDecorations**

In `RangeDecorations.kt`, add inside `object RangeDecorations` (after `drawRangeSigns`):

```kotlin
    /** The mat is skipped once the camera is this close to it (chase sweep). */
    private const val MAT_MIN_DRAW_DEPTH_M = 6.0

    /**
     * Range mat (spec 2026-09-25): rubber base + turf strip + hitting line
     * under the ball, projected through [cam]. Item 4 (2026-10-01): shared by
     * the range AND both games so every mode shows the hitting mat. Unlike
     * [groundPath] there is no near-vertex filter (the near edge must be
     * allowed to fall below the frame bottom); instead the mat is skipped
     * entirely once the camera is within MAT_MIN_DRAW_DEPTH_M of any corner.
     */
    fun DrawScope.drawRangeMat(cam: RangeCamera, w: Float, h: Float) {
        val focalPx = w * 1.10f
        val v0Px = h * 0.30f
        val centerX = w / 2f
        val cosPitch = kotlin.math.cos(cam.pitchRad)
        val sinPitch = kotlin.math.sin(cam.pitchRad)

        fun matPath(vertices: List<Pair<Double, Double>>): Path? {
            val clear = vertices.all { (x, y) ->
                val dy = y - cam.y
                val dz = RangeScene.groundHeight(x, y) - cam.z
                (dy * cosPitch - dz * sinPitch) > MAT_MIN_DRAW_DEPTH_M
            }
            if (!clear) return null
            val path = Path()
            var first = true
            for ((x, y) in vertices) {
                val p = PovProjector.project(cam, x, y, RangeScene.groundHeight(x, y)) ?: return null
                val sx = (centerX + p.u * focalPx).toFloat()
                val sy = (v0Px + p.v * focalPx).toFloat()
                if (first) { path.moveTo(sx, sy); first = false } else path.lineTo(sx, sy)
            }
            path.close()
            return path
        }

        val matBase = matPath(
            listOf(
                RangeMat.BASE_X_MIN to RangeMat.BASE_Y_MIN,
                RangeMat.BASE_X_MAX to RangeMat.BASE_Y_MIN,
                RangeMat.BASE_X_MAX to RangeMat.BASE_Y_MAX,
                RangeMat.BASE_X_MIN to RangeMat.BASE_Y_MAX,
            ),
        )
        if (matBase != null) {
            drawPath(matBase, RangeMat.BASE)
            drawPath(matBase, RangeMat.BASE_EDGE, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        }
        val matStrip = matPath(
            listOf(
                RangeMat.STRIP_X_MIN to RangeMat.STRIP_Y_MIN,
                RangeMat.STRIP_X_MAX to RangeMat.STRIP_Y_MIN,
                RangeMat.STRIP_X_MAX to RangeMat.STRIP_Y_MAX,
                RangeMat.STRIP_X_MIN to RangeMat.STRIP_Y_MAX,
            ),
        )
        if (matStrip != null) {
            drawPath(matStrip, RangeMat.STRIP)
            drawPath(matStrip, RangeMat.STRIP_EDGE, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
        }
        val matLine = matPath(
            listOf(
                -RangeMat.LINE_HALF_WIDTH_M to RangeMat.STRIP_Y_MIN,
                RangeMat.LINE_HALF_WIDTH_M to RangeMat.STRIP_Y_MIN,
                RangeMat.LINE_HALF_WIDTH_M to RangeMat.STRIP_Y_MAX,
                -RangeMat.LINE_HALF_WIDTH_M to RangeMat.STRIP_Y_MAX,
            ),
        )
        if (matLine != null) drawPath(matLine, RangeMat.HITTING_LINE)
    }
```

- [ ] **Step 2: PovRangeCanvas — delete the local mat painter, call the shared one**

In `PovRangeCanvas.kt`:

DELETE the private const at line 30 (`private const val MAT_MIN_DRAW_DEPTH_M = 6.0` — now lives in RangeDecorations).

REPLACE the whole mat block (lines 196-253, from the `// Range mat (spec 2026-09-25)` comment through `if (matLine != null) drawPath(matLine, RangeMat.HITTING_LINE)`) with:

```kotlin
        // Range mat: shared painter, identical projection to before (item 4:
        // the same mat now also renders in both games via GameScene).
        with(RangeDecorations) {
            drawRangeMat(camera, w, h)
        }
```

Keep the `// The launch anchor` code that follows. The `matPath`/`localGroundPath` distinction: `localGroundPath` is still used by the green/grid layers — do not delete it.

- [ ] **Step 3: GameScene — draw the mat in both games**

In `GameScene.kt`, replace `drawGameGround` (lines 76-81):

```kotlin
    fun DrawScope.drawGameGround(cam: RangeCamera, w: Float, h: Float, labelPaint: Paint) {
        with(RangeDecorations) {
            drawRangeGround(cam, w, h)
            drawRangeMat(cam, w, h)
            drawRangeSigns(cam, w, h, labelPaint)
        }
    }
```

(Update the doc comment to mention the mat. `RangeDecorations` import already exists at line 14.)

- [ ] **Step 4: Compile + run app tests**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeDecorations.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/GameScene.kt
git commit -m "feat: draw the range mat in all modes via shared RangeDecorations painter"
```

---

### Task 8: Pane plane moves closer — 20% of target (item 5)

Panels were "too far". Move the pane plane from 25% to 20% of the target distance; cell sizes stay unchanged. `zRefM` must re-anchor on the reference trajectory crossing at the NEW plane fraction, so the middle row stays flightable.

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneGeom.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneGeomTest.kt`

**Interfaces:**
- Consumes: `ReferenceTrajectory.paneCrossingHeightM(targetM, planeFraction)` (the `planeFraction` parameter already exists, defaulting to 0.25).
- Produces: `PaneGeom.PLANE_FRACTION = 0.20` (public const); `planeYM = 0.20 × targetM`; `zRefM` re-anchored. All other PaneGeom math (`cellAt`, `cellCorners`, `firstCrossing`) is fraction-agnostic — no changes.

- [ ] **Step 1: Update the tests (they pin the old geometry)**

In `PaneGeomTest.kt`:

REPLACE the `geometry scales with target` test (lines 13-18) with:

```kotlin
    @Test
    fun `geometry scales with target`() {
        assertEquals(28.0, pane.planeYM, 1e-9)   // 0.20 x 140 (was 0.25 -> 35.0)
        assertEquals(2.24, pane.cellHM, 1e-9)    // cell sizes unchanged (item 5 keeps cells)
        assertEquals(2.80, pane.cellWM, 1e-9)
        assertEquals(0.20, PaneGeom.PLANE_FRACTION, 1e-9)
    }
```

UPDATE the class doc comment at line 11 (`// 140 m: plane y=35, ...`) to `// 140 m: plane y=28, cellH=2.24, cellW=2.80.`

UPDATE the `crossing below pane reports point with null cell` test samples (lines 49-58) — the sample at `y = 35.0` must sit exactly on the new plane:

```kotlin
    @Test
    fun `crossing below pane reports point with null cell`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.0, pane.planeYM, 2.0, 1.0),
            TrajectorySample(0.0, 100.0, 0.0, 2.0),
        )
        val crossing = pane.firstCrossing(samples)!!
        assertEquals(2.0, crossing.zM, 1e-9)
        assertNull(crossing.cell)
    }
```

UPDATE the `first forward crossing wins when samples dip through twice` test (lines 60-70): replace the literal `35.0` in the second sample with `pane.planeYM`:

```kotlin
    @Test
    fun `first forward crossing wins when samples dip through twice`() {
        val samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(0.0, pane.planeYM, pane.zRefM, 1.0), // first plane crossing: middle row/col
            TrajectorySample(0.0, 36.0, pane.topZM + 1.0, 1.1), // rises steeply above the plane
            TrajectorySample(0.0, 37.0, pane.zRefM, 1.2), // continues forward - NOT a forward crossing
        )
        val crossing = pane.firstCrossing(samples)!!
        assertEquals(4, crossing.cell)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.PaneGeomTest" --console=plain`
Expected: FAIL — `planeYM` is 35.0 and `PLANE_FRACTION` unresolved.

- [ ] **Step 3: Implement**

In `PaneGeom.kt`, replace lines 21-24:

```kotlin
    val planeYM: Double = 0.25 * targetM
    val cellHM: Double = 0.016 * targetM
    val cellWM: Double = 0.020 * targetM
    val zRefM: Double = ReferenceTrajectory.paneCrossingHeightM(targetM)
```

with:

```kotlin
    companion object {
        /** Pane distance as a fraction of the target (item 5, 2026-10-01: 25% -> 20%). */
        const val PLANE_FRACTION = 0.20
    }

    val planeYM: Double = PLANE_FRACTION * targetM
    val cellHM: Double = 0.016 * targetM
    val cellWM: Double = 0.020 * targetM
    val zRefM: Double = ReferenceTrajectory.paneCrossingHeightM(targetM, PLANE_FRACTION)
```

Also update the class doc comment (lines 8-18): change "Pane floats at y = 25% of target" to "Pane floats at y = 20% of target (item 5, 2026-10-01)".

- [ ] **Step 4: Run the pane-adjacent tests — re-anchoring must hold**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.PaneGeomTest" --tests "com.hpsmiles.golfsim.games.BreakPaneReachabilityTest" --tests "com.hpsmiles.golfsim.games.PaneClipTest" --tests "com.hpsmiles.golfsim.games.PaneIntersectionTest" --tests "com.hpsmiles.golfsim.games.PaneRawConsistencyTest" --tests "com.hpsmiles.golfsim.games.BreakThePaneGameTest" --console=plain`
Expected: PASS. `BreakPaneReachabilityTest` is the critical one: the middle row re-anchors via `paneCrossingHeightM(target, 0.20)`, so every row stays reachable. If reachability fails, STOP and report — do not fudge cell constants.

Note: `PaneIntersectionTest` and `PaneRawConsistencyTest` use an explicit plane y (`35.0`) as a direct `PaneIntersection` parameter — they are fraction-independent and must pass unchanged.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneGeom.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneGeomTest.kt
git commit -m "feat: move break-the-pane plane closer to 20% of target"
```

---

### Task 9: Bigger pane-game greens on every difficulty (item 6, amended)

User amended: raise the green on ALL difficulties, including hard. Radii at the 140 m reference: EASY 10→12, MEDIUM 8→10, HARD 6→8.

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPaneGreen.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakPaneGreenTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: new radius table; scaled downstream by `BreakThePaneGame.greenRadiusM()` (unchanged) and consumed by `PaneRawConsistencyTest` (which reads the function, not literals — unaffected).

- [ ] **Step 1: Update the failing test**

In `BreakPaneGreenTest.kt`, REPLACE lines 9-22 with:

```kotlin
    @Test
    fun `green radii at 140m per difficulty`() {
        assertEquals(12.0, BreakPaneGreen.radiusAt140m(Difficulty.EASY), 1e-9)
        assertEquals(10.0, BreakPaneGreen.radiusAt140m(Difficulty.MEDIUM), 1e-9)
        assertEquals(8.0, BreakPaneGreen.radiusAt140m(Difficulty.HARD), 1e-9)
    }

    @Test
    fun `game green radius scales the difficulty radius by target`() {
        val game = BreakThePaneGame()
        game.start(70.0, Difficulty.EASY)
        assertEquals(6.0, game.greenRadiusM(), 1e-9) // 12 * 70/140
        game.start(140.0, Difficulty.HARD)
        assertEquals(8.0, game.greenRadiusM(), 1e-9)
    }
```

(The third test `difficulty is persisted on the result` stays unchanged.)

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.BreakPaneGreenTest" --console=plain`
Expected: FAIL — radii still 10/8/6.

- [ ] **Step 3: Implement**

In `BreakPaneGreen.kt`, replace the `when` body:

```kotlin
/**
 * Break the Pane green radius per difficulty, at the 140 m reference target
 * (item 6 amended 2026-10-01: raised on ALL difficulties). Scaled by target
 * in [BreakThePaneGame.greenRadiusM].
 */
object BreakPaneGreen {

    fun radiusAt140m(difficulty: Difficulty): Double = when (difficulty) {
        Difficulty.EASY -> 12.0
        Difficulty.MEDIUM -> 10.0
        Difficulty.HARD -> 8.0
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.BreakPaneGreenTest" --tests "com.hpsmiles.golfsim.games.PaneRawConsistencyTest" --tests "com.hpsmiles.golfsim.games.BreakThePaneGameTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPaneGreen.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakPaneGreenTest.kt
git commit -m "feat: enlarge break-the-pane greens on all difficulties"
```

---

### Task 10: Final verification

**Files:** none (verification only).

- [ ] **Step 1: Full build with all unit tests**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build --console=plain`
Expected: `BUILD SUCCESSFUL` — assemble + every module's tests. Any failure here must be fixed before proceeding; do not commit fixes silently into this gate's commit.

- [ ] **Step 2: Review the full diff**

Run: `git log --oneline -10` and `git diff c70c9c0..HEAD --stat`
Expected: 9 commits (Tasks 1-9), touching only the files listed in this plan.

- [ ] **Step 3: Install on the tablet (user does the visual pass)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:installDebug --console=plain`
Expected: `Installed on 1 device`. The user then verifies on the tablet: side-spin run-out curves, punch rollout on firm fairway, custom green setup + rendering, mat visible in both games, pane distance/size, pane green sizes, and no more panel overdraw.

---

## Self-Review Notes (completed at plan time)

- **Spec coverage:** item 1 → Task 4; item 2 → Tasks 2+3; item 3 → Tasks 5+6; item 4 → Task 7; item 5 → Task 8; item 6 (amended: all difficulties) → Task 9; item 7 → Task 1. No gaps.
- **Risk watch-list:** (a) tour-fixture pins in `core/physics` may fail after Tasks 3/4 — zero-yaw pins must be bit-identical; nonzero-yaw pin changes are expected and must be justified. (b) `BreakPaneReachabilityTest` is the gate for Task 8's re-anchoring. (c) `RangeSession.restore()` does not touch `customGreen` — correct (session-scoped, not persisted).
- **Type consistency:** `surfaceProviderFor(Green?): SurfaceProvider` defined in Task 5, consumed in Task 6 via `session.customGreen.value`; `drawRangeMat(cam, w, h)` defined in Task 7 Step 1, consumed in Task 7 Steps 2-3; `PLANE_FRACTION` defined and consumed in Task 8 only.


