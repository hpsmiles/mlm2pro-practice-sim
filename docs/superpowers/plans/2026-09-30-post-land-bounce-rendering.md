# Post-Land Bounce Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the modelled bounce chain after touchdown — visible hop arcs in the ball path and tracer, with a thinner/fainter ground stroke — using the physics the engine already computes (spec `docs/superpowers/specs/2026-09-30-post-land-bounce-rendering-design.md`).

**Architecture:** Additive physics data (`GroundHop`, `GroundResult.hops`, `ShotResult.groundHops`) recorded by `BounceRollModel`'s existing bounce loop; `RangeRollout` replays the recorded chain (parabolic arc per hop + flat ease-out roll to the exact rest point, uniformly time-scaled into the 0.3–1.8 s hold window); `PovRangeCanvas` splits the tracer stroke at the landing boundary. No engine numbers change, no Room schema change, no call-site changes (FollowCam/GameScene keep calling `RangeRollout.samples`).

**Tech Stack:** Kotlin, JVM JUnit4 tests (`org.junit.Test`/`org.junit.Assert`), Jetpack Compose canvas. No new dependencies.

---

## Environment preamble (every task)

All Gradle commands run from the repo root in PowerShell and assume:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

Set it once per shell. Gradle is quiet during configure — quiet ≠ hung (5 s–2 min). `--console=plain` must precede task names.

- Physics module (pure JVM): `.\gradlew.bat :core:physics:test --tests "<Fqcn>" --console=plain`
- App module (Android): `.\gradlew.bat :app:testDebugUnitTest --tests "<Fqcn>" --console=plain`
- Full census: `.\gradlew.bat build --console=plain`

## Working-tree note (Task 0)

The tree currently holds the verified, uncommitted green spin-dominance rollout fix (4 files: `core/physics/.../Surface.kt`, `core/physics/.../BounceRollModel.kt`, `core/physics/.../BounceRollModelTest.kt`, `docs/superpowers/specs/2026-09-29-games-target-practice-break-pane-design.md`). Task 1 modifies `BounceRollModel.kt`/`BounceRollModelTest.kt` again, so the pending fix MUST be committed first to keep commits clean. The user already reviewed the fix results (all 82 physics tests + full build green).

## File structure

| File | Action | Responsibility |
|---|---|---|
| `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/GroundHop.kt` | Create | One recorded bounce hop (cumulative touch point, apex, air time) |
| `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/GroundResult.kt` | Modify | Gains `hops: List<GroundHop> = emptyList()` |
| `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModel.kt` | Modify | Record one `GroundHop` per `FlightSolver` hop solve |
| `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ShotResult.kt` | Modify | Gains `groundHops: List<GroundHop> = emptyList()` |
| `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BallFlightEngine.kt` | Modify | Pass `ground.hops` into `ShotResult` |
| `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModelTest.kt` | Modify | Hop recording invariants (2 new tests) |
| `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BallFlightEngineTest.kt` | Modify | Engine exposes `groundHops` (1 new test) |
| `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeRollout.kt` | Modify | Hop-aware ground phase (arcs + roll, scaled timing) |
| `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeRolloutTest.kt` | Modify | Hop rendering pins (3 new tests + helper) |
| `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt` | Modify | Two-pass tracer stroke (flight vs ground) |

Out of scope (spec §8): `TopDownCanvas` hops, ground effects, ball shadow.

---

### Task 0: Commit the pending verified rollout fix

**Files:** commit only (no edits).

- [ ] **Step 1: Verify the working tree holds exactly the expected 4 files**

Run: `git status --short`
Expected: exactly these modified files (no others):

```
 M core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModel.kt
 M core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModelTest.kt
 M core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/Surface.kt
 M docs/superpowers/specs/2026-09-29-games-target-practice-break-pane-design.md
```

If anything else is dirty, STOP and ask the user before continuing.

- [ ] **Step 2: Commit**

```powershell
git add core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/Surface.kt core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModel.kt core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModelTest.kt docs/superpowers/specs/2026-09-29-games-target-practice-break-pane-design.md
git commit -m "fix(physics): gate green spin-back by spin dominance (8i live capture)"
```

---

### Task 1: Record bounce hops in `GroundResult` (core/physics)

**Files:**
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/GroundHop.kt`
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/GroundResult.kt`
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModel.kt:51-53,135-146`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModelTest.kt`

- [ ] **Step 1: Write the failing tests**

Append inside `class BounceRollModelTest { ... }` (file uses `org.junit.Assert.assertEquals/assertTrue`, `org.junit.Test`):

```kotlin
    /** Spec 2026-09-30: the bounce loop records one hop per FlightSolver solve. */
    @Test
    fun wedgeBackspinHopsBackwardOnGreen() {
        // Same steep, fast, high-spin impact as wedgeBackspinRollsBackwardOnGreen:
        // the Penner reversal makes the FIRST recorded touch land behind carry.
        val landing = LandingState(
            position = Vec3(0.0, 50.0, 0.0),
            velocity = Vec3(0.0, 14.0, -12.0),
            spin = Vec3(700.0, 0.0, 0.0),   // ~6685 rpm backspin
            apexM = 10.0,
            flightTimeSec = 3.0,
        )
        val ground = BounceRollModel.bounceAndRoll(landing, Surface.GREEN_NORMAL, 45.0, env)
        assertTrue("expected recorded hops, got ${ground.hops.size}", ground.hops.isNotEmpty())
        assertTrue("hop count ${ground.hops.size} exceeds MAX_BOUNCES - 1", ground.hops.size <= 3)
        for (hop in ground.hops) {
            assertTrue("apex must be positive, got ${hop.apexM}", hop.apexM > 0.0)
            assertTrue("duration must be positive, got ${hop.durationSec}", hop.durationSec > 0.0)
        }
        assertTrue(
            "expected backward first touch, got ${ground.hops.first().landingY}",
            ground.hops.first().landingY < 0.0,
        )
    }

    /**
     * 2026-09-30 live 8i capture (shot-1) on a green: non-spin-dominant
     * impact keeps the whole hop chain forward, and the final touch->rest
     * roll reconstructs the remaining ground delta (spec invariant).
     */
    @Test
    fun midIronLiveProfileHopsForwardOnGreen() {
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, 16.60, -15.62),
            spin = Vec3(469.0, 0.0, 0.0),   // ~4478 rpm backspin at impact
            apexM = 20.0,
            flightTimeSec = 5.0,
        )
        val ground = BounceRollModel.bounceAndRoll(landing, Surface.GREEN_NORMAL, 20.3, env)
        assertTrue("expected recorded hops, got ${ground.hops.size}", ground.hops.isNotEmpty())
        assertTrue("hop count ${ground.hops.size} exceeds MAX_BOUNCES - 1", ground.hops.size <= 3)
        var prevY = 0.0
        for (hop in ground.hops) {
            assertTrue(hop.apexM > 0.0)
            assertTrue(hop.durationSec > 0.0)
            assertTrue("touches must stay forward, got ${hop.landingY}", hop.landingY > 0.0)
            assertTrue("cumulative touches must advance, got ${hop.landingY}", hop.landingY > prevY)
            prevY = hop.landingY
        }
        // Spec invariant: last recorded touch + final roll == total ground delta,
        // and the remaining roll is forward (or zero).
        val last = ground.hops.last()
        assertTrue("roll must not reverse", ground.deltaY >= last.landingY)
        assertTrue((ground.deltaX - last.landingX).isFinite())
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.BounceRollModelTest" --console=plain`
Expected: compilation FAILS with `Unresolved reference: hops` (and `GroundHop` does not exist yet). Compile failure = red.

- [ ] **Step 3: Create `GroundHop.kt`**

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * One recorded bounce hop (spec 2026-09-30): the landing touch point,
 * CUMULATIVE from the first ground touch (metres; x lateral, y downrange),
 * the hop's apex height, and its air time. The chain of cumulative touches
 * plus the final touch->rest roll reconstructs the full ground displacement.
 */
data class GroundHop(
    val landingX: Double,
    val landingY: Double,
    val apexM: Double,
    val durationSec: Double,
)
```

- [ ] **Step 4: Add `hops` to `GroundResult`**

Replace the whole of `GroundResult.kt` with:

```kotlin
package com.hpsmiles.golfsim.core.physics

/** Total ground displacement from first bounce to rest. */
data class GroundResult(
    val deltaX: Double,
    val deltaY: Double,
    val bounces: Int,
    val hops: List<GroundHop> = emptyList(),
)
```

- [ ] **Step 5: Record hops in `BounceRollModel.bounceAndRoll`**

In `BounceRollModel.kt`, add a hop accumulator next to the delta accumulators (after `var dy = 0.0`):

```kotlin
        var dx = 0.0
        var dy = 0.0
        val hops = ArrayList<GroundHop>()
```

Then, at the bottom of the loop, record each hop immediately after its displacement is accumulated — replace:

```kotlin
            dx += hop.position.x
            dy += hop.position.y
```

with:

```kotlin
            dx += hop.position.x
            dy += hop.position.y
            hops.add(GroundHop(dx, dy, hop.apexM, hop.flightTimeSec))
```

Finally change the return statement:

```kotlin
        return GroundResult(dx, dy, bounces, hops)
```

(`repeatRunsAreBitIdentical` needs no edit — `assertEquals(a, b)` now also pins hop lists via data-class equality.)

- [ ] **Step 6: Run the full physics suite to verify green**

Run: `.\gradlew.bat :core:physics:test --console=plain`
Expected: BUILD SUCCESSFUL, all tests pass (existing wedge reversal, spin-dominance gate, Tour pins untouched — engine numbers are unchanged by construction).

- [ ] **Step 7: Commit**

```powershell
git add core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/GroundHop.kt core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/GroundResult.kt core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModel.kt core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModelTest.kt
git commit -m "feat(physics): record bounce hops in GroundResult"
```

---

### Task 2: Expose `groundHops` on `ShotResult` (core/physics)

**Files:**
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ShotResult.kt`
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BallFlightEngine.kt:43-53`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BallFlightEngineTest.kt`

- [ ] **Step 1: Write the failing test**

Append inside `class BallFlightEngineTest { ... }`:

```kotlin
    /** Spec 2026-09-30: the engine surfaces the recorded hop chain for rendering. */
    @Test
    fun groundHopsAreExposedOnShotResult() {
        val wedge = LaunchConditions(30.0, 45.0, 9000)
        val shot = BallFlightEngine.simulate(wedge, surfaces = UniformSurface(Surface.GREEN_NORMAL))
        assertTrue("expected recorded hops", shot.groundHops.isNotEmpty())
        assertTrue("hop count ${shot.groundHops.size} exceeds MAX_BOUNCES - 1", shot.groundHops.size <= 3)
        for (hop in shot.groundHops) {
            assertTrue(hop.apexM > 0.0)
            assertTrue(hop.durationSec > 0.0)
        }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.BallFlightEngineTest" --console=plain`
Expected: compilation FAILS with `Unresolved reference: groundHops`.

- [ ] **Step 3: Add the field to `ShotResult`**

In `ShotResult.kt`, extend the doc comment and add one trailing field — replace:

```kotlin
/**
 * End-to-end shot outcome, SI, full precision (display converts at M3+).
 * rolloutM = totalM - carryM (can be negative — spin-back on soft greens).
 * sideM: +x is right of the target line looking down-range.
 * restX/restY = bounce/roll end position (restX == sideM, hypot(restX, restY) == totalM).
 */
```

with:

```kotlin
/**
 * End-to-end shot outcome, SI, full precision (display converts at M3+).
 * rolloutM = totalM - carryM (can be negative — spin-back on soft greens).
 * sideM: +x is right of the target line looking down-range.
 * restX/restY = bounce/roll end position (restX == sideM, hypot(restX, restY) == totalM).
 * groundHops = recorded bounce chain (cumulative touches relative to the
 * first ground touch) for hop-aware ground rendering.
 */
```

and add after `val restY: Double = 0.0,`:

```kotlin
    val groundHops: List<GroundHop> = emptyList(),
```

- [ ] **Step 4: Populate it in `BallFlightEngine.simulate`**

In the `ShotResult(...)` construction, add after `restY = endY,`:

```kotlin
            groundHops = ground.hops,
```

- [ ] **Step 5: Run the full physics suite to verify green**

Run: `.\gradlew.bat :core:physics:test --console=plain`
Expected: BUILD SUCCESSFUL (all pins green — engine numbers unchanged).

- [ ] **Step 6: Commit**

```powershell
git add core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ShotResult.kt core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BallFlightEngine.kt core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BallFlightEngineTest.kt
git commit -m "feat(physics): expose groundHops on ShotResult"
```

---

### Task 3: Hop-aware ground phase in `RangeRollout` (app)

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeRollout.kt` (full rewrite below)
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeRolloutTest.kt`

Contract notes:
- Hop-less shots keep today's flat-roll behavior bit-for-bit (all 5 existing tests pass unchanged; `samples()` stays empty when `rolloutM <= 0` and no hops).
- Hop touch points are drawn on the shot's lateral line (`sideM`) + the hop's cumulative `landingX`, starting from the carry point — same convention as the existing landing/rest dots at `x = sideM`.
- The final roll ends exactly at `(sideM, totalM)` so the follow-cam rest parking and the rest dot stay continuous.

- [ ] **Step 1: Write the failing tests**

In `RangeRolloutTest.kt`, add the `GroundHop` import next to the existing `ShotResult` import:

```kotlin
import com.hpsmiles.golfsim.core.physics.GroundHop
```

Add a helper and three tests inside `class RangeRolloutTest { ... }`:

```kotlin
    /** Builds a hop-chain shot ending at (sideM, totalM) like the engine does. */
    private fun hopShot(
        carryM: Double,
        totalM: Double,
        vararg hops: GroundHop,
    ): ShotResult = ShotResult(
        carryM = carryM,
        rolloutM = totalM - carryM,
        totalM = totalM,
        sideM = 0.0,
        apexM = 20.0,
        flightTimeSec = 5.0,
        restX = 0.0,
        restY = totalM,
        groundHops = hops.toList(),
    )

    @Test
    fun hopChainRendersArcsAndEndsExactlyAtRest() {
        val s = hopShot(
            carryM = 100.0,
            totalM = 103.0,
            GroundHop(1.2, 1.2, 0.45, 0.55),
            GroundHop(2.1, 2.1, 0.22, 0.40),
        )
        val roll = RangeRollout.samples(s)
        assertTrue(roll.isNotEmpty())
        // Starts at the carry point, ends exactly at the modelled rest.
        assertTrue("first sample must start at carry, got ${roll.first().py}", roll.first().py >= s.carryM)
        assertEquals(s.totalM, roll.last().py, 1e-9)
        assertEquals(s.sideM, roll.last().px, 1e-9)
        assertEquals(0.0, roll.last().pz, 1e-9)
        assertEquals(s.flightTimeSec + RangeRollout.durationSec(s), roll.last().tSec, 1e-9)
        // Hops leave the ground and come back: airborne samples exist and the
        // apex never exceeds the largest recorded hop apex.
        val maxPz = roll.maxOf { it.pz }
        assertTrue("expected airborne samples, max pz $maxPz", maxPz > 0.0)
        assertTrue("pz $maxPz exceeds hop apex", maxPz <= 0.45 + 1e-9)
        for (sample in roll) {
            assertTrue("pz must stay >= 0, got ${sample.pz}", sample.pz >= 0.0)
        }
    }

    @Test
    fun hopTimingIsMonotonicAndCapped() {
        // Hop air times totalling ~5 s are uniformly scaled down to the 1.8 s cap.
        val s = hopShot(
            carryM = 100.0,
            totalM = 104.0,
            GroundHop(1.0, 1.0, 0.5, 1.4),
            GroundHop(2.0, 2.0, 0.3, 1.2),
            GroundHop(3.0, 3.0, 0.15, 1.0),
        )
        assertEquals(1.8, RangeRollout.durationSec(s), 1e-9)
        val roll = RangeRollout.samples(s)
        var prevT = Double.NEGATIVE_INFINITY
        for (sample in roll) {
            assertTrue("tSec must strictly increase", sample.tSec > prevT)
            if (prevT != Double.NEGATIVE_INFINITY) {
                assertTrue("gaps must not exceed STEP", sample.tSec - prevT <= 0.05 + 1e-9)
            }
            prevT = sample.tSec
        }
        assertEquals(s.flightTimeSec + RangeRollout.durationSec(s), roll.last().tSec, 1e-9)
    }

    @Test
    fun spinBackRendersBackwardHopsAboveGround() {
        // Wedge spin-back: hop touches land behind carry, arcs stay above ground.
        val s = hopShot(
            carryM = 100.0,
            totalM = 98.5,
            GroundHop(1.5, -0.8, 0.35, 0.6),
            GroundHop(0.5, -1.5, 0.18, 0.45),
        )
        val roll = RangeRollout.samples(s)
        assertTrue(roll.isNotEmpty())
        assertTrue("expected airborne samples", roll.maxOf { it.pz } > 0.0)
        // Net ground phase is backward: rest lands behind the carry point.
        assertEquals(s.totalM, roll.last().py, 1e-9)
        assertTrue("rest must be behind carry", roll.last().py < s.carryM)
        for (sample in roll) {
            assertTrue("pz must stay >= 0, got ${sample.pz}", sample.pz >= 0.0)
        }
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.RangeRolloutTest" --console=plain`
Expected: FAIL — `hopChainRendersArcsAndEndsExactlyAtRest` and `spinBackRendersBackwardHopsAboveGround` assert on empty/flat samples; `hopTimingIsMonotonicAndCapped` fails its 1.8 s pin (legacy formula returns ~1.51 s).

- [ ] **Step 3: Rewrite `RangeRollout.kt`**

Replace the whole file with:

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeRollout.kt
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.GroundHop
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Post-landing ground phase (spec 2026-09-25, hop-aware 2026-09-30). When the
 * engine recorded bounce hops, the ground phase replays the real chain: one
 * parabolic arc per hop (real cumulative touch points, apex height and air
 * time) followed by a flat quadratic ease-out roll to the exact rest
 * position. Without hops the legacy single flat roll from carry to totalM is
 * kept unchanged. The whole ground phase is uniformly time-scaled into the
 * follow-cam hold window (capped at 1.8 s, well under LAND_HOLD_SEC = 2.5 s).
 * No Android deps; same inputs always produce the same samples.
 */
object RangeRollout {
    private const val DECEL_MPS2 = 3.5
    private const val MIN_DURATION_SEC = 0.3
    private const val MAX_DURATION_SEC = 1.8
    private const val STEP_SEC = 0.05

    /** One ground-phase segment from (startX, startY) to (endX, endY). */
    private class Segment(
        val startX: Double,
        val startY: Double,
        val endX: Double,
        val endY: Double,
        val apexM: Double,
        val durationSec: Double,
        private val easeOut: Boolean,
    ) {
        /** Sample at fraction [tau] of the segment: linear along hop arcs
         *  (constant horizontal speed), quadratic ease-out along the roll,
         *  parabolic height pz = 4 * apex * tau * (1 - tau) for hops. */
        fun sampleAt(tau: Double, timeSec: Double): TrajectorySample {
            val progress = if (easeOut) 2.0 * tau - tau * tau else tau
            val x = startX + (endX - startX) * progress
            val y = startY + (endY - startY) * progress
            val pz = if (apexM > 0.0) 4.0 * apexM * tau * (1.0 - tau) else 0.0
            return TrajectorySample(x, y, pz, timeSec)
        }

        fun scaled(scale: Double): Segment =
            Segment(startX, startY, endX, endY, apexM, durationSec * scale, easeOut)
    }

    /**
     * Ground-phase duration, coerced into [MIN_DURATION_SEC, MAX_DURATION_SEC].
     * Zero when there is no ground phase (no hops and no forward rollout).
     */
    fun durationSec(shot: ShotResult): Double {
        if (shot.groundHops.isEmpty()) {
            if (shot.rolloutM <= 0.0) return 0.0
            val raw = sqrt(2.0 * shot.rolloutM / DECEL_MPS2)
            return raw.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC)
        }
        return scaledSegments(shot).sumOf { it.durationSec }
    }

    /**
     * Ground-phase samples appended after the flight: hop arcs with positive
     * pz (or the legacy flat roll), ending exactly at the modelled rest
     * position (sideM, totalM). Empty when there is no ground phase.
     */
    fun samples(shot: ShotResult): List<TrajectorySample> {
        if (shot.groundHops.isEmpty()) {
            if (shot.rolloutM <= 0.0) return emptyList()
            val t = shot.flightTimeSec
            val duration = durationSec(shot)
            val out = ArrayList<TrajectorySample>()
            var step = 1
            var time = t + STEP_SEC
            while (time < t + duration) {
                val tau = (time - t) / duration
                val y = shot.carryM + shot.rolloutM * (2.0 * tau - tau * tau)
                out.add(TrajectorySample(shot.sideM, y, 0.0, time))
                step++
                time = t + step * STEP_SEC
            }
            out.add(TrajectorySample(shot.sideM, shot.totalM, 0.0, t + duration))
            return out
        }

        val segments = scaledSegments(shot)
        val duration = segments.sumOf { it.durationSec }
        if (duration <= 0.0) return emptyList()
        val t0 = shot.flightTimeSec
        val out = ArrayList<TrajectorySample>()
        var step = 1
        var time = t0 + STEP_SEC
        while (time < t0 + duration) {
            var ground = time - t0
            var segIdx = 0
            while (segIdx < segments.size - 1 && ground > segments[segIdx].durationSec) {
                ground -= segments[segIdx].durationSec
                segIdx++
            }
            val seg = segments[segIdx]
            val tau = if (seg.durationSec > 1e-12) {
                (ground / seg.durationSec).coerceIn(0.0, 1.0)
            } else {
                1.0
            }
            out.add(seg.sampleAt(tau, time))
            step++
            time = t0 + step * STEP_SEC
        }
        val last = segments.last()
        out.add(last.sampleAt(1.0, t0 + duration))
        return out
    }

    /**
     * The hop chain as uniformly time-scaled segments: first touch at the
     * drawn carry point (sideM, carryM), each hop to its cumulative landing,
     * then the final ease-out roll to the exact rest position (sideM, totalM).
     */
    private fun scaledSegments(shot: ShotResult): List<Segment> {
        val raw = ArrayList<Segment>(shot.groundHops.size + 1)
        var px = shot.sideM
        var py = shot.carryM
        for (hop in shot.groundHops) {
            val tx = shot.sideM + hop.landingX
            val ty = shot.carryM + hop.landingY
            raw.add(Segment(px, py, tx, ty, hop.apexM, hop.durationSec, easeOut = false))
            px = tx
            py = ty
        }
        val dRoll = hypot(shot.sideM - px, shot.totalM - py)
        raw.add(Segment(px, py, shot.sideM, shot.totalM, 0.0, sqrt(2.0 * dRoll / DECEL_MPS2), easeOut = true))

        val rawTotal = raw.sumOf { it.durationSec }
        val scale = if (rawTotal > 0.0) {
            rawTotal.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC) / rawTotal
        } else {
            1.0
        }
        return raw.map { it.scaled(scale) }
    }
}
```

- [ ] **Step 4: Run the test class to verify green**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.RangeRolloutTest" --console=plain`
Expected: BUILD SUCCESSFUL — 9 tests (6 existing + 3 new) pass.

- [ ] **Step 5: Run the follow-cam tests (call-site neighbours)**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.FollowCamTest" --console=plain`
Expected: BUILD SUCCESSFUL (hop-less test shots take the legacy path).

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeRollout.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeRolloutTest.kt
git commit -m "feat(range): hop-aware ground phase in RangeRollout"
```

---

### Task 4: Two-pass tracer stroke in `PovRangeCanvas` (app)

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt:276-319` (`drawTracer` only; `pathSamples` and all dots unchanged)

No unit test: this is Compose canvas drawing (repo discipline — pure logic is already covered by `RangeRollout` tests in Task 3). Verified by compilation + the app test suite + the Task 5 visual check.

- [ ] **Step 1: Replace `drawTracer`**

Replace the existing `drawTracer` (from `fun drawTracer(s: ShotResult, timeSec: Double, color: Color, width: Float) {` through its closing brace, currently ending `drawPath(path, color, style = Stroke(width = width))` + `}`) with:

```kotlin
        fun drawTracer(s: ShotResult, timeSec: Double, color: Color, width: Float) {
            val samples = pathSamples(s)
            if (samples.size < 2) return
            // Screen-space clip: collect only in-frame projections, keeping
            // sample indices so we can break the path across frame-exit gaps.
            val pts = ArrayList<Pair<Int, Offset>>()
            for (i in samples.indices) {
                if (samples[i].tSec > timeSec) break
                val p = worldToScreen(camera, v0Px, focalPx, centerX, samples[i].px, samples[i].py, samples[i].pz)
                    ?: continue
                if (p.y > h - 8f) continue
                pts.add(i to p)
            }
            if (pts.isEmpty()) return

            // Spec 2026-09-30: split the stroke at the landing boundary so
            // the ground phase reads thinner and fainter than the flight.
            val boundary = pts.indexOfFirst { samples[it.first].tSec > s.flightTimeSec }
            val flightPts = if (boundary < 0) pts else pts.subList(0, boundary)
            // The ground stroke keeps the last flight point so the two
            // strokes join exactly at the touchdown.
            val groundPts = when {
                boundary < 0 -> emptyList()
                boundary > 0 -> listOf(pts[boundary - 1]) + pts.subList(boundary, pts.size)
                else -> pts.subList(boundary, pts.size)
            }

            fun trace(slice: List<Pair<Int, Offset>>): Path? {
                if (slice.isEmpty()) return null
                val path = Path()
                path.moveTo(slice[0].second.x, slice[0].second.y)
                for (j in 1 until slice.size) {
                    if (slice[j].first - slice[j - 1].first > 30) {
                        // Frame exit gap: resume as a new sub-path, never
                        // draw a straight line across it.
                        path.moveTo(slice[j].second.x, slice[j].second.y)
                    } else {
                        path.lineTo(slice[j].second.x, slice[j].second.y)
                    }
                }
                return path
            }

            val flightPath = trace(flightPts)
            if (flightPath != null) {
                // Tangent lead-in: extend the arc's own first segment backwards
                // to the bottom edge (when the arc starts low in frame), so the
                // launch lead-in is exactly collinear with the curve — no fixed
                // anchor point, no elbow where it joins.
                if (flightPts.size >= 2 && flightPts[1].first == flightPts[0].first + 1 && flightPts[0].second.y > h * 0.6f) {
                    val p0 = flightPts[0].second
                    val p1 = flightPts[1].second
                    val dy = p1.y - p0.y
                    if (dy < -1f) {
                        val u = ((h - 8f) - p0.y) / dy
                        val qx = p0.x + u * (p1.x - p0.x)
                        if (u > 0.02f && qx >= 0f && qx <= w) {
                            flightPath.moveTo(qx, h - 8f)
                            flightPath.lineTo(p0.x, p0.y)
                        }
                    }
                }
                drawPath(flightPath, color, style = Stroke(width = width))
            }
            val groundPath = trace(groundPts)
            if (groundPath != null) {
                drawPath(
                    groundPath,
                    color.copy(alpha = color.alpha * 0.8f),
                    style = Stroke(width = width * 0.6f),
                )
            }
        }
```

Call sites are unchanged: current-shot tracer passes `(Amber, 2.5f)` → ground stroke 1.5 px @ alpha 0.8 (spec values); history tracers pass `(HISTORY_LINE, 2f)` → 1.2 px @ alpha 0.28.

- [ ] **Step 2: Run the app test suite to verify green**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL (canvas code has no JVM tests; this catches accidental breakage elsewhere).

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt
git commit -m "feat(range): split tracer into flight and ground strokes"
```

---

### Task 5: Full census + tablet visual check

**Files:** none (verification only).

- [ ] **Step 1: Full build census**

Run: `.\gradlew.bat build --console=plain`
Expected: BUILD SUCCESSFUL (assemble + all module tests).

- [ ] **Step 2: Install on the attached tablet for the visual check**

Run: `.\gradlew.bat :app:installDebug --console=plain`
Expected: BUILD SUCCESSFUL, `Installed on 1 device` (tablet HA2C96BN).

Then ask the user to eyeball on the tablet: ball visibly bounces after touchdown in the range view; tracer thinner/fainter on the ground; spin-back shots bounce backward; follow-cam hold and snap-back unchanged. This is the spec §7 acceptance gate — the user signs off.

---

## Self-review notes (already applied)

- Spec coverage: §3 data model → Tasks 1–2; §4 geometry/timing → Task 3; §5 rendering → Task 4; §6 tests → Tasks 1–3 + census; §7 acceptance → Task 5. No gaps.
- Type consistency: `GroundHop(landingX, landingY, apexM, durationSec)` used identically in physics and app; `GroundResult.hops` / `ShotResult.groundHops` named as in the spec.
- Existing app tests need no edits: hop-less `ShotResult`s take the preserved legacy branch (including the `rolloutM <= 0 → empty` rule the spec mandates for no-hop shots).
