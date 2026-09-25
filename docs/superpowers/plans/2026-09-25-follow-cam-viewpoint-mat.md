# Follow Cam, Raised Viewpoint & Range Mat — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fixed 1.7 m player-POV camera with an 8 m crane view, add a ball-follow camera that ends overlooking the landing area at ~45°, and draw a 2.5 × 2.5 m range mat with the ball on the right.

**Architecture:** `PovProjector` is generalized from hard-coded constants to an explicit `RangeCamera(x, y, z, pitchRad)` value with nose-down pitch rotation (pitch = 0 collapses to today's math exactly). A new pure module `FollowCam` maps `(ShotResult, playFraction) → RangeCamera` through a phase machine (static → blend → chase → descent pitch-in → landing hold → snap back). `RangeScreen` extends the playback fraction past 1 to cover the 2.5 s hold and feeds the per-frame camera to `PovRangeCanvas`, which derives the horizon from the camera pitch and projects everything through the active camera. The mat is a new pure geometry object `RangeMat` drawn in both POV and top-down views.

**Tech Stack:** Kotlin, Jetpack Compose Canvas (no new dependencies), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-25-follow-cam-viewpoint-mat-design.md` (approved 2026-09-25).

## Global Constraints

- Windows: set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before every Gradle command.
- Full build: `.\gradlew.bat build`; unit tests: `.\gradlew.bat test`.
- `FollowCam`, `PovProjector`, `RangeMat` stay pure Kotlin — no Android imports (repo convention: unit-testable on JVM, deterministic).
- Physics goldens (`core/physics`) are untouched — no changes to `FlightSolver`, `TrajectorySample`, `ShotResult`.
- Focal stays `1.1w`, horizon anchor stays `0.30h` — no FOV change (spec "Out of scope").
- Tunable constants live at the top of `FollowCam`: `FOLLOW_DELAY_SEC=1.5`, `BLEND_SEC=0.8`, `CHASE_BACK_M=20.0`, `CHASE_UP_M=2.5`, `OVERLOOK_DIST_M=25.0`, `LAND_PITCH_DEG=45.0` (user: "45° may not be right but it is a starting value"), `LAND_HOLD_SEC=2.5`.
- Mat colors (spec, mockup B2): base `#22262B`, edge `#16191D`, turf strip `#3B7D4C`, strip edge `#2E5F3B`, hitting line `#356B44`.
- Every task ends green: run the task's test command before committing.

---

### Task 1: Generalize `PovProjector` with `RangeCamera` + pitch

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovProjector.kt` (full rewrite)
- Rewrite: `app/src/test/kotlin/com/hpsmiles/golfsim/range/PovProjectorTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces (later tasks rely on these exact signatures):
  - `data class RangeCamera(val x: Double, val y: Double, val z: Double, val pitchRad: Double)` with `companion object { val STATIC: RangeCamera }`
  - `PovProjector.CAM_HEIGHT_M = 8.0`, `PovProjector.CAM_BACK_M = 21.2`
  - `PovProjector.project(cam: RangeCamera, x: Double, y: Double, z: Double): ProjectedPoint?`
  - `PovProjector.bandV(cam: RangeCamera, distanceM: Double): Double`
  - Temporary `@Deprecated` compat overloads `project(x, y, z)` / `bandV(distanceM)` (deleted in Task 3) so `PovRangeCanvas` keeps compiling between tasks.

- [x] **Step 1: Rewrite the failing tests**

Replace the whole of `app/src/test/kotlin/com/hpsmiles/golfsim/range/PovProjectorTest.kt`:

```kotlin
// app/src/test/kotlin/com/hpsmiles/golfsim/range/PovProjectorTest.kt
package com.hpsmiles.golfsim.range

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the camera-based pinhole projection used by the POV range canvas.
 *
 * Static rig (spec 2026-09-25, mockup C): crane position 8 m above the
 * ground, [PovProjector.CAM_BACK_M] behind the ball. The tee (0, 0) sits
 * at v = 8 / 21.2 = 0.377, just inside the bottom-edge budget (0.397 at
 * focal 1.1w on the 2000x1250 tablet).
 *
 * Pitch is nose-down radians about the lateral axis. At pitch = 0 the
 * generalized math collapses to the old pinhole: u = x / depth,
 * v = (camZ - z) / depth, depth = y - camY.
 */
class PovProjectorTest {

    private val cam = RangeCamera.STATIC

    @Test
    fun staticRigIsTheRaisedCranePosition() {
        assertEquals(0.0, cam.x, 1e-12)
        assertEquals(-PovProjector.CAM_BACK_M, cam.y, 1e-12)
        assertEquals(PovProjector.CAM_HEIGHT_M, cam.z, 1e-12)
        assertEquals(0.0, cam.pitchRad, 0.0)
        assertEquals(8.0, PovProjector.CAM_HEIGHT_M, 1e-12)
        assertEquals(21.2, PovProjector.CAM_BACK_M, 1e-12)
    }

    @Test
    fun groundBandsProjectAtExactFractions() {
        assertEquals(8.0 / (50.0 + 21.2), PovProjector.bandV(cam, 50.0), 1e-12)
        assertEquals(8.0 / (100.0 + 21.2), PovProjector.bandV(cam, 100.0), 1e-12)
        assertEquals(8.0 / (150.0 + 21.2), PovProjector.bandV(cam, 150.0), 1e-12)
        assertEquals(8.0 / (200.0 + 21.2), PovProjector.bandV(cam, 200.0), 1e-12)
    }

    @Test
    fun nearerBandsAreLowerOnScreen() {
        assertTrue(PovProjector.bandV(cam, 50.0) > PovProjector.bandV(cam, 100.0))
        assertTrue(PovProjector.bandV(cam, 100.0) > PovProjector.bandV(cam, 150.0))
        assertTrue(PovProjector.bandV(cam, 150.0) > PovProjector.bandV(cam, 200.0))
    }

    @Test
    fun straightAheadGroundPointIsCentered() {
        val p = PovProjector.project(cam, 0.0, 100.0, 0.0)!!
        assertEquals(0.0, p.u, 1e-12)
        assertEquals(8.0 / (100.0 + 21.2), p.v, 1e-12)
        assertEquals(1.0 / (100.0 + 21.2), p.scale, 1e-12)
    }

    @Test
    fun launchPointIsVisibleNearBottomOfFrame() {
        val tee = PovProjector.project(cam, 0.0, 0.0, 0.0)!!
        assertEquals(8.0 / 21.2, tee.v, 1e-12) // = 0.377
        assertEquals(0.0, tee.u, 1e-12)
        // Bottom-edge budget at focal 1.1w: v_bottom = 0.7h / 1.1w = 0.397
        // on the 2000x1250 tablet — the tee must stay inside it.
        assertTrue(tee.v < 0.397)
    }

    @Test
    fun apexAboveEyeLevelProjectsAboveHorizon() {
        // Apex 30 m up, 150 m out: (8 - 30) / 171.2 < 0.
        val p = PovProjector.project(cam, 0.0, 150.0, 30.0)!!
        assertTrue(p.v < 0.0)
    }

    @Test
    fun typicalLandingPointIsInBounds() {
        val p = PovProjector.project(cam, 10.0, 163.0, 0.0)!!
        assertEquals(10.0 / (163.0 + 21.2), p.u, 1e-12)
        assertTrue(p.v > 0.0)
        assertTrue(p.v < PovProjector.bandV(cam, 100.0)) // farther than the 100 m band
    }

    @Test
    fun pitchZeroCollapsesToTheLevelPinhole() {
        // Camera (0, 0, 3, 0), point (2, 10, 7): the level-camera textbook case.
        val c = RangeCamera(0.0, 0.0, 3.0, 0.0)
        val p = PovProjector.project(c, 2.0, 10.0, 7.0)!!
        assertEquals(2.0 / 10.0, p.u, 1e-12)
        assertEquals((3.0 - 7.0) / 10.0, p.v, 1e-12)
        assertEquals(1.0 / 10.0, p.scale, 1e-12)
    }

    @Test
    fun noseDownPitchKeepsOnAxisPointCentered() {
        // Camera at (0, 0, 25) pitched 45 deg down. A point 10 m out along
        // the camera axis (direction (0, cos45, -sin45)) projects to the
        // exact screen centre at scale 1/10.
        val c = RangeCamera(0.0, 0.0, 25.0, Math.toRadians(45.0))
        val p = PovProjector.project(
            c,
            0.0,
            10.0 * Math.cos(Math.toRadians(45.0)),
            25.0 - 10.0 * Math.sin(Math.toRadians(45.0)),
        )!!
        assertEquals(0.0, p.u, 1e-12)
        assertEquals(0.0, p.v, 1e-12)
        assertEquals(0.1, p.scale, 1e-12)
    }

    @Test
    fun pitchedHorizonShiftsAboveFrameCentre() {
        // A far ground point under nose-down pitch tends to v = -tan(theta);
        // at 45 deg the horizon sits at v = -1 (above the top of frame).
        val c = RangeCamera(0.0, 0.0, 8.0, Math.toRadians(45.0))
        val far = PovProjector.project(c, 0.0, 1.0e9, 0.0)!!
        assertEquals(-1.0, far.v, 1e-6)
    }

    @Test
    fun pitchRaisesGroundPointsOnScreen() {
        val level = PovProjector.project(cam, 0.0, 100.0, 0.0)!!
        val pitched = PovProjector.project(
            RangeCamera(0.0, -21.2, 8.0, Math.toRadians(45.0)), 0.0, 100.0, 0.0,
        )!!
        assertTrue(pitched.v < level.v)
    }

    @Test
    fun pointsAtOrBehindTheCameraPlaneAreRejected() {
        assertNull(PovProjector.project(cam, 1.0, -PovProjector.CAM_BACK_M, 0.0))
        assertNull(PovProjector.project(cam, 1.0, -PovProjector.CAM_BACK_M - 1.0, 2.0))
        // Under nose-down pitch the camera plane tilts: a point above the
        // camera falls behind the plane even though its world y is ahead.
        val c = RangeCamera(0.0, 0.0, 25.0, Math.toRadians(45.0))
        assertNull(PovProjector.project(c, 0.0, 5.0, 35.0)) // depth = (5-10)*cos45 < 0
    }

    @Test
    fun scaleShrinksWithDistance() {
        val near = PovProjector.project(cam, 0.0, 50.0, 0.0)!!
        val far = PovProjector.project(cam, 0.0, 200.0, 0.0)!!
        assertTrue(near.scale > far.scale)
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.PovProjectorTest"
```

Expected: compile FAIL — `RangeCamera` unresolved (the new API does not exist yet).

- [x] **Step 3: Rewrite `PovProjector.kt`**

Replace the whole file:

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/range/PovProjector.kt
package com.hpsmiles.golfsim.range

import kotlin.math.cos
import kotlin.math.sin

/**
 * A range camera: world position (metres) + pitch (nose-down radians,
 * 0 = level). No yaw, no roll — one rotation axis only (spec 2026-09-25).
 */
data class RangeCamera(
    val x: Double,
    val y: Double,
    val z: Double,
    val pitchRad: Double,
) {
    companion object {
        /**
         * The raised static rig behind the tee: an 8 m crane position
         * ("mockup C", 2026-09-25 brainstorm). The tee stays at
         * v = 8 / 21.2 = 0.377 — just above the bottom edge.
         */
        val STATIC = RangeCamera(0.0, -PovProjector.CAM_BACK_M, PovProjector.CAM_HEIGHT_M, 0.0)
    }
}

/**
 * Pinhole projection from range world coordinates to normalized screen
 * coordinates. Pure Kotlin (no Android types) so it is unit-testable on
 * the JVM. The canvas layer converts these normalized values to pixels.
 *
 * World axes (metres): x lateral (+right), y down-range (+away from the
 * hitter), z up. The camera ([RangeCamera]) carries its own world
 * position and nose-down pitch; all distances inside the projection are
 * measured relative to the camera.
 *
 * With camera pitch theta and relative point (dx, dy, dz), dz = z - camZ:
 *   depth = dy*cos(theta) - dz*sin(theta)
 *   u     = dx / depth
 *   v     = -(dy*sin(theta) + dz*cos(theta)) / depth
 * At pitch = 0 this collapses exactly to the old level pinhole:
 *   u = x / (y + CAM_BACK_M), v = (CAM_HEIGHT_M - z) / (y + CAM_BACK_M).
 */
object PovProjector {

    /** Camera height above the hitting mat in the static rig, in metres. */
    const val CAM_HEIGHT_M = 8.0

    /** Camera distance behind the ball (y = 0) in the static rig, in metres. */
    const val CAM_BACK_M = 21.2

    /**
     * Normalized projection of a world point. `u` is lateral position (0 =
     * dead ahead, +right), `v` is vertical position (0 = camera level,
     * +toward the viewer's feet), `scale` is the relative on-screen size
     * of a 1 m object at that depth. All values are independent of
     * viewport size.
     */
    data class ProjectedPoint(val u: Double, val v: Double, val scale: Double)

    /**
     * Projects a world point through [cam], or returns null when the point
     * is at or behind the camera plane (depth <= 0) — such points must not
     * be drawn.
     */
    fun project(cam: RangeCamera, x: Double, y: Double, z: Double): ProjectedPoint? {
        val dx = x - cam.x
        val dy = y - cam.y
        val dz = z - cam.z
        val cosPitch = cos(cam.pitchRad)
        val sinPitch = sin(cam.pitchRad)
        val depth = dy * cosPitch - dz * sinPitch
        if (depth <= 0.0) return null
        return ProjectedPoint(
            u = dx / depth,
            v = -(dy * sinPitch + dz * cosPitch) / depth,
            scale = 1.0 / depth,
        )
    }

    /**
     * The `v` of the ground at a given down-range distance (a horizontal
     * distance band line on the POV canvas — rotation is about the lateral
     * axis, so bands stay screen-horizontal under pitch). Requires
     * `cam.x == 0.0` (bands span the full width). The [distanceM] argument
     * is a world y.
     */
    fun bandV(cam: RangeCamera, distanceM: Double): Double {
        val dy = distanceM - cam.y
        val dz = -cam.z
        val cosPitch = cos(cam.pitchRad)
        val sinPitch = sin(cam.pitchRad)
        val depth = dy * cosPitch - dz * sinPitch
        return -(dy * sinPitch + dz * cosPitch) / depth
    }

    // ------------------------------------------------------------------
    // Temporary compat overloads (old call sites in PovRangeCanvas).
    // DELETED in the canvas task — do not use in new code.
    // ------------------------------------------------------------------

    @Deprecated("Use project(cam, x, y, z)")
    fun project(x: Double, y: Double, z: Double): ProjectedPoint? =
        project(RangeCamera.STATIC, x, y, z)

    @Deprecated("Use bandV(cam, distanceM)")
    fun bandV(distanceM: Double): Double = bandV(RangeCamera.STATIC, distanceM)
}
```

Note: the compat overloads keep `PovRangeCanvas` compiling with the old `y + CAM_BACK_M` depth semantics — identical to the new pitch-0 static rig, so this task changes framing (8 m / 21.2 m) but no math.

- [x] **Step 4: Run the tests to verify they pass**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.PovProjectorTest"
```

Expected: PASS (all 13 tests). Also run `.\gradlew.bat :app:compileDebugKotlin` — expected SUCCESS (compat overloads keep the canvas green).

- [x] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/PovProjector.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/PovProjectorTest.kt
git commit -m "feat(range): generalize PovProjector with RangeCamera pitch support"
```

---

### Task 2: `FollowCam` — pure phase machine

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/FollowCam.kt`
- Create: `app/src/test/kotlin/com/hpsmiles/golfsim/range/FollowCamTest.kt`

**Interfaces:**
- Consumes: `RangeCamera` + `RangeCamera.STATIC`, `PovProjector.project(cam, x, y, z)` (Task 1), `com.hpsmiles.golfsim.core.physics.ShotResult` (`carryM`, `sideM`, `flightTimeSec`, `samples: List<TrajectorySample>`), `TrajectorySample` (`px, py, pz, tSec`).
- Produces:
  - `FollowCam.cameraAt(shot: ShotResult, playFraction: Float): RangeCamera`
  - `FollowCam.endFraction(shot: ShotResult): Double` — playback runs `0..endFraction`, where `>1` covers the hold.
  - Tunable constants `FOLLOW_DELAY_SEC`, `BLEND_SEC`, `CHASE_BACK_M`, `CHASE_UP_M`, `OVERLOOK_DIST_M`, `LAND_PITCH_DEG`, `LAND_HOLD_SEC`, `EARLY_ENGAGE_V`.

- [x] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/hpsmiles/golfsim/range/FollowCamTest.kt`:

```kotlin
// app/src/test/kotlin/com/hpsmiles/golfsim/range/FollowCamTest.kt
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the FollowCam phase machine (spec 2026-09-25): static window ->
 * blend -> chase -> descent pitch-in -> landing hold -> snap back.
 *
 * Synthetic shots: straight-line lateral drift, py proportional to t, a
 * pz parabola peaking at t = T/2, samples every 50 ms. The default shot
 * (carry 180, apex 30, T = 6) early-engages at ~1.0 s (apex heads for the
 * top of frame); the low wedge (apex 10, carry 60, T = 3.5) never does,
 * so it engages on the 1.5 s timer.
 */
class FollowCamTest {

    private fun parabolicShot(
        carryM: Double = 180.0,
        apexM: Double = 30.0,
        flightTimeSec: Double = 6.0,
        sideM: Double = 5.0,
    ): ShotResult {
        val samples = ArrayList<TrajectorySample>()
        var t = 0.0
        while (t < flightTimeSec) {
            val f = t / flightTimeSec
            samples.add(
                TrajectorySample(
                    px = sideM * f,
                    py = carryM * f,
                    pz = apexM * 4.0 * f * (1.0 - f),
                    tSec = t,
                )
            )
            t += 0.05
        }
        samples.add(TrajectorySample(sideM, carryM, 0.0, flightTimeSec))
        return ShotResult(
            carryM = carryM,
            rolloutM = 5.0,
            totalM = carryM + 5.0,
            sideM = sideM,
            apexM = apexM,
            flightTimeSec = flightTimeSec,
            samples = samples,
        )
    }

    private fun frac(shot: ShotResult, tSec: Double): Float =
        (tSec / shot.flightTimeSec).toFloat()

    @Test
    fun staticBeforeTheDelay() {
        val shot = parabolicShot()
        // t = 0.7 s is inside the static window (early engage ~1.0 s here).
        assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(shot, frac(shot, 0.7)))
    }

    @Test
    fun noShotTimeMeansStatic() {
        val broken = ShotResult(180.0, 5.0, 185.0, 5.0, 30.0, 0.0, emptyList())
        assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(broken, 0.5f))
    }

    @Test
    fun highApexEngagesBeforeTheTimer() {
        val driver = parabolicShot(carryM = 240.0, apexM = 45.0)
        // t = 1.0 s is inside the 1.5 s static window, but a big apex has
        // already pushed the ball near the top of the frame -> early engage.
        assertNotEquals(RangeCamera.STATIC, FollowCam.cameraAt(driver, frac(driver, 1.0)))
    }

    @Test
    fun chaseTracksBehindAndAboveTheBall() {
        val shot = parabolicShot()
        // t = 2.8 s: past blend end (~1.8 s), before apex (3.0 s) -> chase.
        val head = shot.samples.last { it.tSec <= 2.8 }
        val cam = FollowCam.cameraAt(shot, frac(shot, 2.8))
        assertEquals(0.0, cam.pitchRad, 1e-12)
        assertEquals(head.px, cam.x, 1e-9)
        assertEquals(head.py - FollowCam.CHASE_BACK_M, cam.y, 1e-9)
        assertEquals(head.pz + FollowCam.CHASE_UP_M, cam.z, 1e-9)
    }

    @Test
    fun blendCrossFadesFromStaticToChase() {
        val wedge = parabolicShot(carryM = 60.0, apexM = 10.0, flightTimeSec = 3.5)
        // Low apex never leaves the frame -> engages on the 1.5 s timer,
        // blend spans 1.5-2.3 s. t = 1.9 s is mid-blend.
        val cam = FollowCam.cameraAt(wedge, frac(wedge, 1.9))
        val head = wedge.samples.last { it.tSec <= 1.9 }
        assertEquals(0.0, cam.pitchRad, 1e-12)
        assertTrue(cam.y > RangeCamera.STATIC.y)             // left -21.2 m ...
        assertTrue(cam.y < head.py - FollowCam.CHASE_BACK_M)  // ... but not at the chase rig
        assertTrue(cam.z > RangeCamera.STATIC.z)             // rose off the crane height
    }

    @Test
    fun blendIsMonotonicTowardTheChaseRig() {
        // A shot that is still RISING through the whole blend window
        // (apex at 4.0 s > blend end 2.3 s), so both the smoothstep factor
        // and the chase rig move monotonically — the camera must too.
        val rising = parabolicShot(carryM = 80.0, apexM = 12.0, flightTimeSec = 8.0)
        var lastY = RangeCamera.STATIC.y
        var lastZ = RangeCamera.STATIC.z
        var t = FollowCam.FOLLOW_DELAY_SEC
        while (t < FollowCam.FOLLOW_DELAY_SEC + FollowCam.BLEND_SEC) {
            val cam = FollowCam.cameraAt(rising, frac(rising, t))
            assertTrue(cam.y >= lastY) // forward only
            assertTrue(cam.z >= lastZ) // rising only
            assertEquals(0.0, cam.pitchRad, 1e-12)
            lastY = cam.y
            lastZ = cam.z
            t += 0.1
        }
    }

    @Test
    fun touchdownIsTheFullOverlookRig() {
        val shot = parabolicShot()
        val cam = FollowCam.cameraAt(shot, 1f) // fraction = 1 -> touchdown, hold begins
        val pitch = Math.toRadians(FollowCam.LAND_PITCH_DEG)
        assertEquals(pitch, cam.pitchRad, 1e-12)
        assertEquals(shot.sideM, cam.x, 1e-9)
        assertEquals(shot.carryM - FollowCam.OVERLOOK_DIST_M * Math.cos(pitch), cam.y, 1e-9)
        assertEquals(FollowCam.OVERLOOK_DIST_M * Math.sin(pitch), cam.z, 1e-9)
    }

    @Test
    fun descentPitchEasesFromLevelToLandPitch() {
        val shot = parabolicShot()
        // Descent spans apex (3.0 s) -> touchdown (6.0 s).
        val mid = FollowCam.cameraAt(shot, frac(shot, 4.5))
        assertTrue(mid.pitchRad > 0.0)
        assertTrue(mid.pitchRad < Math.toRadians(FollowCam.LAND_PITCH_DEG))
    }

    @Test
    fun holdParksAtTheOverlookUntilTheEnd() {
        val shot = parabolicShot()
        val touchdown = FollowCam.cameraAt(shot, 1f)
        val midHold = FollowCam.cameraAt(shot, frac(shot, 6.0 + 1.25))
        val beforeEnd = FollowCam.cameraAt(shot, (FollowCam.endFraction(shot) - 0.01).toFloat())
        assertEquals(touchdown, midHold)
        assertEquals(touchdown, beforeEnd)
    }

    @Test
    fun snapBackToStaticAfterTheHold() {
        val shot = parabolicShot()
        assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(shot, FollowCam.endFraction(shot).toFloat()))
        assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(shot, (FollowCam.endFraction(shot) + 0.05).toFloat()))
    }

    @Test
    fun shortShotSkipsStraightToTheOverlook() {
        val chip = parabolicShot(carryM = 15.0, apexM = 6.0, flightTimeSec = 1.6)
        val cam = FollowCam.cameraAt(chip, 0.2f)
        val pitch = Math.toRadians(FollowCam.LAND_PITCH_DEG)
        assertEquals(pitch, cam.pitchRad, 1e-12)
        assertEquals(chip.carryM - FollowCam.OVERLOOK_DIST_M * Math.cos(pitch), cam.y, 1e-9)
    }

    @Test
    fun shortShotAlsoSnapsBack() {
        val chip = parabolicShot(carryM = 15.0, apexM = 6.0, flightTimeSec = 1.6)
        assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(chip, FollowCam.endFraction(chip).toFloat()))
    }

    @Test
    fun endFractionExtendsByTheHold() {
        val shot = parabolicShot(flightTimeSec = 6.0)
        assertEquals(1.0 + FollowCam.LAND_HOLD_SEC / 6.0, FollowCam.endFraction(shot), 1e-12)
    }

    @Test
    fun deterministic() {
        val shot = parabolicShot()
        for (f in listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f, 1.2f)) {
            assertEquals(FollowCam.cameraAt(shot, f), FollowCam.cameraAt(shot, f))
        }
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.FollowCamTest"
```

Expected: compile FAIL — `FollowCam` unresolved.

- [x] **Step 3: Implement `FollowCam.kt`**

Create `app/src/main/kotlin/com/hpsmiles/golfsim/range/FollowCam.kt`:

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/range/FollowCam.kt
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ball follow camera (spec 2026-09-25): a pure phase machine mapping
 * (ShotResult, playFraction) -> RangeCamera. No Android deps; same inputs
 * always produce the same camera.
 *
 * Timeline (t = seconds since impact, T = flightTimeSec):
 *   static   t < engageT        STATIC rig (or early engage, whichever first)
 *   blend    0.8 s              smoothstep STATIC -> chase rig (target moves)
 *   chase    -> apex            ball + (0, -CHASE_BACK_M, +CHASE_UP_M), pitch 0
 *   descent  apex -> touchdown  ease chase rig -> overlook rig; pitch eases
 *                              0 -> LAND_PITCH_DEG on the same progress
 *   hold     LAND_HOLD_SEC      overlook rig parked
 *   snap back after hold        cut to STATIC (tracer + landing marker persist)
 */
object FollowCam {

    /** Static window before the follow cam engages. */
    const val FOLLOW_DELAY_SEC = 1.5

    /** Cross-fade from the static rig to the chase rig. */
    const val BLEND_SEC = 0.8

    /** Chase position: this far behind the ball. */
    const val CHASE_BACK_M = 20.0

    /** Chase position: this far above the ball. */
    const val CHASE_UP_M = 2.5

    /** Landing overlook: distance from the landing point to the camera. */
    const val OVERLOOK_DIST_M = 25.0

    /** Landing overlook pitch (deg). Tuning knob — user: "45 may not be right but it is a starting value". */
    const val LAND_PITCH_DEG = 45.0

    /** How long the overlook holds before cutting back to STATIC. */
    const val LAND_HOLD_SEC = 2.5

    /**
     * Early-engage threshold: STATIC-rig projected v of the ball at which
     * the follow cam engages immediately (big apexes are never lost to the
     * timer). -0.17 is the top of the frame on the reference tablet
     * (horizon 0.30h, focal 1.1w, 2000x1250: (0 - 0.30h) / 1.1w).
     */
    const val EARLY_ENGAGE_V = -0.17

    /** The playback fraction runs 0..endFraction; values > 1 cover the hold. */
    fun endFraction(shot: ShotResult): Double = 1.0 + LAND_HOLD_SEC / shot.flightTimeSec

    /** The camera for a shot at [playFraction] (0 = impact, 1 = touchdown). */
    fun cameraAt(shot: ShotResult, playFraction: Float): RangeCamera {
        val t = shot.flightTimeSec
        if (t <= 0.0 || shot.samples.isEmpty()) return RangeCamera.STATIC

        val timeSec = playFraction.toDouble() * t
        if (timeSec >= t + LAND_HOLD_SEC) return RangeCamera.STATIC // snapped back

        val overlook = overlookRig(shot)

        // Short shots: the flight ends before delay/blend ever would —
        // never point the camera at empty sky; park at the overlook.
        if (t <= FOLLOW_DELAY_SEC + BLEND_SEC) return overlook

        val engageT = earlyEngageT(shot) ?: FOLLOW_DELAY_SEC
        val blendEnd = engageT + BLEND_SEC
        val apexT = shot.samples.maxByOrNull { it.pz }?.tSec ?: (t / 2.0)
        val descentT = maxOf(apexT, blendEnd)

        return when {
            timeSec < engageT -> RangeCamera.STATIC
            timeSec < blendEnd ->
                lerpCam(
                    RangeCamera.STATIC,
                    chaseRig(sampleAt(shot, timeSec)),
                    smoothstep((timeSec - engageT) / BLEND_SEC),
                )
            timeSec < descentT -> chaseRig(sampleAt(shot, timeSec))
            timeSec < t -> lerpCam(
                chaseRig(sampleAt(shot, descentT)),
                overlook,
                smoothstep(((timeSec - descentT) / (t - descentT)).coerceIn(0.0, 1.0)),
            )
            else -> overlook // landing hold
        }
    }

    /**
     * Behind-and-above the landing point so the camera axis passes through
     * the landing spot at the pitch angle.
     */
    private fun overlookRig(shot: ShotResult): RangeCamera {
        val pitch = Math.toRadians(LAND_PITCH_DEG)
        return RangeCamera(
            x = shot.sideM,
            y = shot.carryM - OVERLOOK_DIST_M * cos(pitch),
            z = OVERLOOK_DIST_M * sin(pitch),
            pitchRad = pitch,
        )
    }

    private fun chaseRig(head: TrajectorySample): RangeCamera =
        RangeCamera(head.px, head.py - CHASE_BACK_M, head.pz + CHASE_UP_M, 0.0)

    private fun sampleAt(shot: ShotResult, t: Double): TrajectorySample =
        shot.samples.lastOrNull { it.tSec <= t } ?: shot.samples.first()

    /** First time the ball's STATIC projection reaches the top of frame. */
    private fun earlyEngageT(shot: ShotResult): Double? {
        for (s in shot.samples) {
            val p = PovProjector.project(RangeCamera.STATIC, s.px, s.py, s.pz) ?: continue
            if (p.v <= EARLY_ENGAGE_V) return s.tSec
        }
        return null
    }

    private fun lerpCam(a: RangeCamera, b: RangeCamera, s: Double): RangeCamera =
        RangeCamera(
            x = a.x + (b.x - a.x) * s,
            y = a.y + (b.y - a.y) * s,
            z = a.z + (b.z - a.z) * s,
            pitchRad = a.pitchRad + (b.pitchRad - a.pitchRad) * s,
        )

    private fun smoothstep(x: Double): Double {
        val c = x.coerceIn(0.0, 1.0)
        return c * c * (3.0 - 2.0 * c)
    }
}
```

- [x] **Step 4: Run the tests to verify they pass**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.FollowCamTest"
```

Expected: PASS (all 14 tests).

- [x] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/FollowCam.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/FollowCamTest.kt
git commit -m "feat(range): add pure FollowCam phase machine"
```

---

### Task 3: Plumb the per-frame camera through `PovRangeCanvas`

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt` (full rewrite)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovProjector.kt` (delete the compat overloads)

**Interfaces:**
- Consumes: `RangeCamera`, `PovProjector.project(cam, …)`, `PovProjector.bandV(cam, …)` (Task 1).
- Produces: `PovRangeCanvas(…, camera: RangeCamera = RangeCamera.STATIC, modifier: Modifier)` — new `camera` parameter in position 6 (after `showHistory`, before `modifier`). Task 4 and Task 5 call it with this exact shape.

No behavior change is visible in this task beyond the Task-1 rig constants: the default camera is `STATIC` (pitch 0 → identical horizon math). Type-checked by compile + all unit tests green.

- [x] **Step 1: Rewrite `PovRangeCanvas.kt`**

Replace the whole file (changes vs. old: `camera` param; `v0Px` anchor + pitch-derived `horizonPx`; every projection goes through `camera`; target ovals use `centre.scale`; apex clamp computed against the STATIC rig; tee ball gets a 4 px minimum radius):

```kotlin
package com.hpsmiles.golfsim.range

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import kotlin.math.max
import kotlin.math.tan

// Painted-ground palette A — "Tour Broadcast" (approved spec palette).
// Sky switched to a white background at user request (2026-09-24).
private val SKY_TOP = Color(0xFFFCFDFE)
private val SKY_BOTTOM = Color(0xFFD9E4EA)
private val ROUGH_BASE = Color(0xFF1E4D26)
private val FAIRWAY = Color(0xFF3E8E43)
private val STRIPE_LIGHT = Color(0xFF47A04C)
private val STRIPE_DARK = Color(0xFF3A8440)
private val GREEN_SURFACE = Color(0xFF5FBF63)
private val FRINGE = Color(0xFF2F6E35)
private val HAZE = Color.White.copy(alpha = 0.22f)

// Previous-shot tracer lines: faded teal per the M3 design language
// (history = teal, live moment = amber).
private val HISTORY_LINE = GolfColors.Teal.copy(alpha = 0.35f)

/**
 * Player-perspective range view. Painted "Tour Broadcast" scene (rough base,
 * fairway, mow stripes, greens with fringes, horizon haze; bands and target
 * ovals stay on top) plus the current shot's tracer and landing pulse, driven
 * by [playFraction] in 0..1 (1 = flight complete), and faded tracer lines for
 * [previousShots].
 *
 * Everything is projected through [camera] (spec 2026-09-25: the FollowCam
 * moves the rig per frame, and the static rig is the 8 m crane position).
 * The world horizon is derived from the camera pitch (v = -tan(pitch) at
 * infinite distance); the sky is only drawn above it.
 *
 * Visual clamps (user demo-gate feedback): the apex scale is computed
 * against the STATIC rig only (so trajectory geometry stays constant while
 * the follow cam moves), and the waiting tee ball gets a minimum on-screen
 * radius (a true-scale ball is ~2 px at 21.2 m).
 */
@Composable
fun PovRangeCanvas(
    currentShot: ShotResult?,
    previousShots: List<ShotResult>,
    playFraction: Float,
    showTracer: Boolean,
    showHistory: Boolean,
    camera: RangeCamera = RangeCamera.STATIC,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val focalPx = w * 1.10f
        val centerX = w / 2f
        // Screen y of v = 0 (camera level). The world horizon sits at
        // v = -tan(pitch) — it moves up the frame as the camera noses down.
        val v0Px = h * 0.30f
        val horizonPx = v0Px - tan(camera.pitchRad).toFloat() * focalPx

        // Sky, then painted ground back-to-front: rough base (full bleed),
        // fairway, mow stripes, greens with fringes. All layers are flat
        // polygons at z = groundHeight in front of the camera.
        if (horizonPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(SKY_TOP, SKY_BOTTOM), startY = 0f, endY = horizonPx,
                ),
                size = Size(w, horizonPx),
            )
        }
        val groundTop = horizonPx.coerceAtLeast(0f)
        drawRect(ROUGH_BASE, topLeft = Offset(0f, groundTop), size = Size(w, h - groundTop))

        // Flat-ground polygon: project world vertices (at ground height) into
        // a closed Path. Vertices at/behind the camera (y <= 0.5) are clipped
        // away; if any remaining vertex still fails to project, the layer is
        // skipped rather than drawn malformed.
        fun groundPath(vertices: List<Pair<Double, Double>>): Path? {
            val visible = vertices.filter { it.second > 0.5 }
            if (visible.size < 3) return null
            val path = Path()
            var first = true
            for ((x, y) in visible) {
                val p = worldToScreen(camera, v0Px, focalPx, centerX, x, y, RangeScene.groundHeight(x, y))
                    ?: return null
                if (first) { path.moveTo(p.x, p.y); first = false } else path.lineTo(p.x, p.y)
            }
            path.close()
            return path
        }

        val fairway = groundPath(RangeScene.fairwayOutline())
        if (fairway != null) drawPath(fairway, FAIRWAY)

        // Alternating 12 m mow stripes as trapezoids clipped to the fairway.
        for (index in 0..14) {
            val (yFrom, yTo) = RangeScene.stripeBand(index)
            if (yFrom >= RangeScene.FAIRWAY_END_Y) break
            val halfFrom = RangeScene.fairwayHalfWidth(yFrom)
            val halfTo = RangeScene.fairwayHalfWidth(yTo)
            val stripe = groundPath(
                listOf(-halfFrom to yFrom, halfFrom to yFrom, halfTo to yTo, -halfTo to yTo),
            )
            if (stripe != null) {
                drawPath(stripe, if (RangeScene.stripeIsLight(0.0, yFrom)) STRIPE_LIGHT else STRIPE_DARK)
            }
        }

        // Greens: fringe ring first, putting surface on top.
        for (green in RangeScene.greens) {
            if (green.distanceM < 8.0) continue // near edge would sit at/behind the camera
            val fringe = groundPath(
                RangeScene.circleOutline(green.lateralM, green.distanceM, green.fringeRadiusM),
            ) ?: continue
            drawPath(fringe, FRINGE)
            val surface = groundPath(
                RangeScene.circleOutline(green.lateralM, green.distanceM, green.radiusM),
            ) ?: continue
            drawPath(surface, GREEN_SURFACE)
        }

        // Plan-verbatim correction: use android.graphics.Paint directly for
        // native text (androidx Paint.asFrameworkPaint() was wrong in the draft).
        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 10.sp.toPx()
            color = Color.White.toArgb()
        }

        // Distance bands on the ground: screen-horizontal under pitch
        // (rotation is about the lateral axis). White for "Tour Broadcast"
        // crispness against the painted fairway.
        val bandDistances = listOf(50f, 100f, 150f, 200f)
        for (d in bandDistances) {
            val y = v0Px + PovProjector.bandV(camera, d.toDouble()) * focalPx
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(0f, y.toFloat()),
                end = Offset(w, y.toFloat()),
                strokeWidth = 1f,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${d.toInt()} M", w - 8.sp.toPx() * 3, y.toFloat() - 4.sp.toPx(), labelPaint,
            )
        }

        // Teal target ovals at (lateral, distance) metres.
        val targets = listOf(-12f to 75f, 0f to 100f, 12f to 150f)
        val targetRadiusM = 5f
        for ((lateralM, distM) in targets) {
            val centre = PovProjector.project(camera, lateralM.toDouble(), distM.toDouble(), 0.0) ?: continue
            val cx = centerX + centre.u * focalPx
            val cy = v0Px + centre.v * focalPx
            // Ground circle: horizontal radius from the projection scale;
            // vertical extent from the near/far edge band difference.
            val rx = (targetRadiusM * centre.scale * focalPx).toFloat()
            val nearV = PovProjector.bandV(camera, (distM - targetRadiusM).toDouble())
            val farV = PovProjector.bandV(camera, (distM + targetRadiusM).toDouble())
            val ry = (((nearV - farV) / 2.0) * focalPx).toFloat()
            drawOval(
                color = GolfColors.Teal,
                topLeft = Offset((cx - rx).toFloat(), (cy - ry).toFloat()),
                size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
                style = Stroke(width = 1.5f),
            )
            drawOval(
                color = GolfColors.Teal.copy(alpha = 0.4f),
                topLeft = Offset((cx - rx / 2f).toFloat(), (cy - ry / 2f).toFloat()),
                size = androidx.compose.ui.geometry.Size(rx, ry),
                style = Stroke(width = 1f),
            )
        }

        // Horizon haze, last over the painted ground: a soft white fade at
        // the world horizon blending the scene into the sky.
        drawRect(
            brush = Brush.verticalGradient(
                0f to HAZE,
                1f to Color.Transparent,
                startY = horizonPx,
                endY = horizonPx + h * 0.15f,
            ),
            topLeft = Offset(0f, horizonPx),
            size = Size(w, h * 0.15f),
        )

        // The launch anchor: the tracer attaches here while the ball is too
        // close to project inside the frame, so the ball is visible leaving.
        val launchAnchor = Offset(centerX, h - 24f)
        // Most-negative projected v allowed (apex must stay below the top 8%).
        val vMin = (h * 0.08f - v0Px) / focalPx

        // Real-trajectory polyline: project the modelled flight samples.
        // apexScale (single scalar, top-8% rule) keeps big apexes in frame;
        // it is computed against the STATIC rig only so the drawn geometry
        // does not breathe while the follow cam moves.
        fun scaledSamples(s: ShotResult): List<TrajectorySample> {
            if (s.samples.isEmpty()) return emptyList()
            val yApex = s.carryM / 2.0
            val depthApex = yApex + PovProjector.CAM_BACK_M
            val vApex = (PovProjector.CAM_HEIGHT_M - s.apexM) / depthApex
            val apexScale = if (vApex < vMin) {
                ((PovProjector.CAM_HEIGHT_M - vMin * depthApex) / s.apexM).coerceIn(0.1, 1.0)
            } else {
                1.0
            }
            if (apexScale >= 1.0) return s.samples
            return s.samples.map { it.copy(pz = it.pz * apexScale) }
        }

        fun drawTracer(s: ShotResult, timeSec: Double, color: Color, width: Float) {
            val samples = scaledSamples(s)
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
            val path = Path()
            path.moveTo(pts[0].second.x, pts[0].second.y)
            for (j in 1 until pts.size) {
                if (pts[j].first - pts[j - 1].first > 30) {
                    // Frame exit gap: resume as a new sub-path, never draw
                    // a straight line across it.
                    path.moveTo(pts[j].second.x, pts[j].second.y)
                } else {
                    path.lineTo(pts[j].second.x, pts[j].second.y)
                }
            }
            // Tangent lead-in: extend the arc's own first segment backwards to
            // the bottom edge (when the arc starts low in frame), so the launch
            // lead-in is exactly collinear with the curve — no fixed anchor
            // point, no elbow where it joins.
            if (pts.size >= 2 && pts[1].first == pts[0].first + 1 && pts[0].second.y > h * 0.6f) {
                val p0 = pts[0].second
                val p1 = pts[1].second
                val dy = p1.y - p0.y
                if (dy < -1f) {
                    val u = ((h - 8f) - p0.y) / dy
                    val qx = p0.x + u * (p1.x - p0.x)
                    if (u > 0.02f && qx >= 0f && qx <= w) {
                        path.moveTo(qx, h - 8f)
                        path.lineTo(p0.x, p0.y)
                    }
                }
            }
            drawPath(path, color, style = Stroke(width = width))
        }

        // Previous shots first (under the current tracer).
        if (showHistory) {
            for (prev in previousShots.asReversed()) {
                drawTracer(prev, Double.POSITIVE_INFINITY, HISTORY_LINE, 2f)
            }
        }

        // Static ball on the tee while waiting for a shot: a true world-ball
        // (~42.7 mm) projected at the tee position, sized by the projection
        // so it reads as an object at that depth, with a 4 px minimum
        // on-screen radius (spec 2026-09-25: a true-scale ball is ~2 px at
        // the 21.2 m crane rig).
        val ballRadiusM = 0.02135
        val waiting = currentShot == null || playFraction <= 0f || playFraction >= 1f
        if (showTracer && waiting) {
            val teeP = PovProjector.project(camera, 0.0, 0.0, ballRadiusM)
            if (teeP != null) {
                val tee = Offset(
                    (centerX + teeP.u * focalPx).toFloat(),
                    (v0Px + teeP.v * focalPx).toFloat(),
                )
                val rPx = max(4f, (ballRadiusM * teeP.scale * focalPx).toFloat())
                drawCircle(GolfColors.AmberGlow, radius = rPx * 2f, center = tee)
                drawCircle(GolfColors.Amber, radius = rPx, center = tee)
            }
        }

        if (currentShot != null) {
            val s = currentShot

            // Current tracer: full line persists after landing (fraction = 1).
            if (showTracer) {
                drawTracer(s, playFraction * s.flightTimeSec, GolfColors.Amber, 2.5f)
            }

            // Ball at the tracer head — the sample nearest the animation time —
            // clamped to the anchor until it clears the bottom edge, so the
            // ball is visible leaving the club.
            if (showTracer) {
                val timeSec = playFraction * s.flightTimeSec
                val samples = scaledSamples(s)
                val head = samples.lastOrNull { it.tSec <= timeSec } ?: samples.firstOrNull()
                var headPos = head?.let { worldToScreen(camera, v0Px, focalPx, centerX, it.px, it.py, it.pz) }
                if (headPos == null || headPos.y > h - 8f) headPos = launchAnchor
                drawCircle(GolfColors.AmberGlow, radius = 12.sp.toPx(), center = headPos)
                drawCircle(GolfColors.Amber, radius = 6.sp.toPx(), center = headPos)
            }

            // Landing dot plus ring once the flight has completed.
            if (playFraction >= 1f) {
                val landing = worldToScreen(camera, v0Px, focalPx, centerX, s.sideM, s.carryM, 0.0)
                if (landing != null) {
                    drawCircle(
                        GolfColors.AmberHalo,
                        radius = (6f * 0.8f).sp.toPx(),
                        center = landing,
                        style = Stroke(width = 2f),
                    )
                    drawCircle(GolfColors.Amber, radius = 4.sp.toPx(), center = landing)
                }
            }
        }
    }
}

/** Screen-space projection helper shared by the tracer routines. */
private fun worldToScreen(
    camera: RangeCamera,
    v0Px: Float,
    focalPx: Float,
    centerX: Float,
    x: Double,
    y: Double,
    z: Double,
): Offset? {
    val p = PovProjector.project(camera, x, y, z) ?: return null
    return Offset((centerX + p.u * focalPx).toFloat(), (v0Px + p.v * focalPx).toFloat())
}
```

- [x] **Step 2: Delete the compat overloads from `PovProjector.kt`**

Remove this entire block (added in Task 1):

```kotlin
    // ------------------------------------------------------------------
    // Temporary compat overloads (old call sites in PovRangeCanvas).
    // DELETED in the canvas task — do not use in new code.
    // ------------------------------------------------------------------

    @Deprecated("Use project(cam, x, y, z)")
    fun project(x: Double, y: Double, z: Double): ProjectedPoint? =
        project(RangeCamera.STATIC, x, y, z)

    @Deprecated("Use bandV(cam, distanceM)")
    fun bandV(distanceM: Double): Double = bandV(RangeCamera.STATIC, distanceM)
```

- [x] **Step 3: Compile and run all app unit tests**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS (PovProjectorTest, FollowCamTest, RangeSessionTest, RangeSceneTest, ScaffoldSmokeTest — the canvas now uses the new API, overloads gone).

- [x] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/PovProjector.kt
git commit -m "feat(range): plumb per-frame camera through PovRangeCanvas"
```

---

### Task 4: Drive the follow cam from `RangeScreen` + extended playback

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt:88-140`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt` (three small edits)

**Interfaces:**
- Consumes: `FollowCam.cameraAt(shot, playFraction)`, `FollowCam.endFraction(shot)` (Task 2), `PovRangeCanvas(…, camera = …)` (Task 3).
- Produces: none (leaf). The POV view now follows the ball: static 1.5 s → blend → chase → 45° overlook at touchdown → 2.5 s hold → cut back to the crane view.

- [x] **Step 1: Wire the camera + extended animation in `RangeScreen.kt`**

Edit 1 — after `val currentShot = session.shots.lastOrNull()` (line 98), add the per-frame camera:

```kotlin
    val currentShot = session.shots.lastOrNull()

    // Follow cam (spec 2026-09-25): the per-frame camera from the pure
    // phase machine; STATIC whenever there is nothing in flight.
    val camera = when (val shot = currentShot) {
        null -> RangeCamera.STATIC
        else -> FollowCam.cameraAt(shot.shotResult, playFraction)
    }
```

Edit 2 — replace the playback `LaunchedEffect` block (lines 108-120) with the extended end:

```kotlin
    // Tracer playback: animate playFraction over the shot's real duration,
    // EXTENDED past 1 by the follow-cam landing hold (FollowCam.endFraction).
    // The speed multiplier scales the whole timeline, hold included —
    // consistent with slow-mo review.
    LaunchedEffect(currentShot, speedMult) {
        val shot = currentShot ?: return@LaunchedEffect
        if (shot.shotResult.flightTimeSec <= 0.0) return@LaunchedEffect
        val durationMs = shot.shotResult.flightTimeSec * 1000.0 / speedMult.divisor
        val end = FollowCam.endFraction(shot.shotResult).toFloat()
        var lastNanos = withFrameNanos { it }
        while (playFraction < end) {
            val now = withFrameNanos { it }
            val deltaMs = (now - lastNanos) / 1_000_000.0
            lastNanos = now
            playFraction = (playFraction + (deltaMs / durationMs)).coerceAtMost(end)
        }
    }
```

Edit 3 — pass the camera to the canvas call site (around line 134):

```kotlin
                PovRangeCanvas(
                    currentShot?.shotResult, previousShots, playFraction, showTracer, showHistory,
                    camera = camera,
                    Modifier.fillMaxSize(),
                )
```

- [x] **Step 2: Cap the flight drawing and extend the waiting-ball condition in `PovRangeCanvas.kt`**

Edit 1 — add the import (top of file, with the other `kotlin.math` imports):

```kotlin
import kotlin.math.min
```

Edit 2 — extend the waiting-ball condition so the waiting ball returns only after the snap-back, not during the hold. In `PovRangeCanvas.kt` the tee-ball block from Task 3 currently reads:

```kotlin
        val ballRadiusM = 0.02135
        val waiting = currentShot == null || playFraction <= 0f || playFraction >= 1f
        if (showTracer && waiting) {
```

MOVE the two declarations out of the tee-ball block and place them immediately BEFORE the horizon-haze `drawRect` (the block starting `// Horizon haze, last over the painted ground:`), replacing them with the extended versions:

```kotlin
        // Waiting = no shot, before impact, or after the follow cam has cut
        // back (the hold is over — the monitor is ready for the next shot).
        val endF = currentShot?.let {
            if (it.flightTimeSec > 0.0) FollowCam.endFraction(it).toFloat() else 1f
        } ?: 1f
        val waiting = currentShot == null || playFraction <= 0f || playFraction >= endF
        val ballRadiusM = 0.02135
```

The tee-ball block then simply starts at `if (showTracer && waiting) {` and uses the declarations above. (The forward placement also gives the Task-5 mat block, which draws between the haze and the tracer, access to `waiting`.)

Edit 3 — cap the current tracer and head at the flight (they must not move during the hold):

```kotlin
            // Current tracer: full line persists after landing (fraction = 1).
            if (showTracer) {
                drawTracer(s, min(playFraction, 1f) * s.flightTimeSec, GolfColors.Amber, 2.5f)
            }
```

```kotlin
            if (showTracer) {
                val timeSec = min(playFraction, 1f) * s.flightTimeSec
```

- [x] **Step 3: Compile and run all app unit tests**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS (all green — the FollowCam driver math is pinned by FollowCamTest; this task is glue).

- [x] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt
git commit -m "feat(range): drive follow cam + landing hold from RangeScreen"
```

---

### Task 5: Range mat

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeMat.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt` (add mat drawing)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/TopDownCanvas.kt` (add mat quad)

**Interfaces:**
- Consumes: `RangeCamera` + `PovProjector.project(cam, …)` (Task 1), `groundPath` shape conventions (Task 3 canvas), `RangeScene.groundHeight`.
- Produces: `RangeMat` object — geometry constants only (`BASE_X_MIN = -2.15`, `BASE_X_MAX = 0.35`, `BASE_Y_MIN = -1.25`, `BASE_Y_MAX = 1.25`, `STRIP_X_MIN = -1.75`, `STRIP_X_MAX = 0.05`, `STRIP_Y_MIN = -0.95`, `STRIP_Y_MAX = 0.95`, `LINE_HALF_WIDTH_M = 0.01`) plus the five B2 palette colors as `Color` vals.

- [x] **Step 1: Create `RangeMat.kt`**

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeMat.kt
package com.hpsmiles.golfsim.range

import androidx.compose.ui.graphics.Color

/**
 * Range mat geometry (spec 2026-09-25, approved mockup B2): commercial
 * rubber-base + turf-strip mat, 2.5 x 2.5 m, ball 0.35 m from the right
 * edge (right-handed golfer — the base extends left toward the stance
 * area). The ball sits at world (0, 0); the mat's near edge extends
 * behind the tee, running "up to the hitter's feet".
 *
 * Pure constants, no behavior — drawing lives in the canvases.
 */
object RangeMat {

    // Rubber base: 2.5 x 2.5 m.
    const val BASE_X_MIN = -2.15   // ball 0.35 m from the right edge
    const val BASE_X_MAX = 0.35
    const val BASE_Y_MIN = -1.25   // world y (down-range); near edge behind the tee
    const val BASE_Y_MAX = 1.25

    // Turf strip inset into the base.
    const val STRIP_X_MIN = -1.75
    const val STRIP_X_MAX = 0.05
    const val STRIP_Y_MIN = -0.95
    const val STRIP_Y_MAX = 0.95

    // Thin hitting line through the ball, length of the strip.
    const val LINE_HALF_WIDTH_M = 0.01

    // Palette (approved mockup B2).
    val BASE = Color(0xFF22262B)
    val BASE_EDGE = Color(0xFF16191D)
    val STRIP = Color(0xFF3B7D4C)
    val STRIP_EDGE = Color(0xFF2E5F3B)
    val HITTING_LINE = Color(0xFF356B44)
}
```

Note: `Color` is a plain Compose value class (no Android framework types) — the geometry constants themselves stay plain Doubles/Ints, so this remains JVM-test-safe. No unit test: constants only, nothing to pin.

- [x] **Step 2: Draw the mat in `PovRangeCanvas.kt`**

Add imports at the top of `PovRangeCanvas.kt`:

```kotlin
import kotlin.math.cos
import kotlin.math.sin
```

Add a private constant with the other private vals at the top of the file:

```kotlin
/** The mat is skipped once the camera is this close to it (chase sweep). */
private const val MAT_MIN_DRAW_DEPTH_M = 6.0
```

Insert this block AFTER the horizon-haze `drawRect` and BEFORE the `// The launch anchor:` comment (inside `Canvas {`, same scope as `groundPath` — `waiting` and `ballRadiusM` are already in scope from Task 4's forward placement). Draw order: ground → bands/targets → haze → **mat** → history tracer → tee ball → current tracer/head/landing — so the mat paints over the ground layers but under the ball and tracer:

```kotlin
        // Range mat (spec 2026-09-25): rubber base + turf strip under the
        // ball, projected through the per-frame camera — it sweeps past
        // during the chase. Unlike groundPath there is no near-vertex
        // filter (the near edge must be allowed to fall below the frame
        // bottom); instead the mat is skipped entirely once the camera is
        // within MAT_MIN_DRAW_DEPTH_M of any corner, so it never blows up
        // across the frame mid-sweep.
        val cosPitch = cos(camera.pitchRad)
        val sinPitch = sin(camera.pitchRad)
        fun matPath(vertices: List<Pair<Double, Double>>): Path? {
            val clear = vertices.all { (x, y) ->
                val dy = y - camera.y
                val dz = RangeScene.groundHeight(x, y) - camera.z
                (dy * cosPitch - dz * sinPitch) > MAT_MIN_DRAW_DEPTH_M
            }
            if (!clear) return null
            val path = Path()
            var first = true
            for ((x, y) in vertices) {
                val p = worldToScreen(camera, v0Px, focalPx, centerX, x, y, RangeScene.groundHeight(x, y))
                    ?: return null
                if (first) { path.moveTo(p.x, p.y); first = false } else path.lineTo(p.x, p.y)
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
            drawPath(matBase, RangeMat.BASE_EDGE, style = Stroke(width = 2f))
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
            drawPath(matStrip, RangeMat.STRIP_EDGE, style = Stroke(width = 1.5f))
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
```

(The waiting tee ball already draws at world (0, 0, ballRadius) from Task 3 — that amber ball IS the ball on the mat; no extra dot needed.)

- [x] **Step 3: Add the mat quad to `TopDownCanvas.kt`**

Insert after the lateral-gridlines `while` loop and BEFORE the `val history = shots.dropLast(1)` line:

```kotlin
        // Range mat quad at the origin (orientation cue; ball at (0, 0)
        // right of center — right-handed golfer). Spec 2026-09-25.
        val matLeft = originX + (RangeMat.BASE_X_MIN * pxPerM)
        val matRight = originX + (RangeMat.BASE_X_MAX * pxPerM)
        val matTop = originY - (RangeMat.BASE_Y_MAX * pxPerM)
        val matBottom = originY - (RangeMat.BASE_Y_MIN * pxPerM)
        drawRect(
            color = RangeMat.BASE,
            topLeft = Offset(matLeft.toFloat(), matTop.toFloat()),
            size = Size((matRight - matLeft).toFloat(), (matBottom - matTop).toFloat()),
        )
        val stripLeft = originX + (RangeMat.STRIP_X_MIN * pxPerM)
        val stripRight = originX + (RangeMat.STRIP_X_MAX * pxPerM)
        val stripTop = originY - (RangeMat.STRIP_Y_MAX * pxPerM)
        val stripBottom = originY - (RangeMat.STRIP_Y_MIN * pxPerM)
        drawRect(
            color = RangeMat.STRIP,
            topLeft = Offset(stripLeft.toFloat(), stripTop.toFloat()),
            size = Size((stripRight - stripLeft).toFloat(), (stripBottom - stripTop).toFloat()),
        )
        // Ball dot at the origin (same world point as the POV tee ball).
        drawCircle(Color.White, radius = 2.sp.toPx(), center = Offset(originX, originY))
```

And add/verify these imports in `TopDownCanvas.kt`:

```kotlin
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
```

(`Offset`, `Path`, `Stroke`, `nativeCanvas`, `toArgb`, `sp`, `GolfColors` are already imported.)

- [x] **Step 4: Compile and run all app unit tests**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

- [x] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeMat.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/TopDownCanvas.kt
git commit -m "feat(range): add range mat to POV and top-down views"
```

---

### Task 6: Full build + tablet verification

**Files:** none (verification only).

**Interfaces:**
- Consumes: everything above.

- [x] **Step 1: Full build (assemble + all tests)**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build
```

Expected: BUILD SUCCESSFUL — physics goldens untouched and green.

- [x] **Step 2: Install on the tablet**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:installDebug
```

Expected: `Installed on 1 device` (TB373FU).

- [ ] **Step 3: Manual verification pass (user)**

Have the user watch one full sequence per shot type and confirm:

1. **Crane static view**: tee + mat visible at bottom, horizon at 30% height, targets/bands/greens unchanged in style.
2. **Driver (big apex)**: static for ~1 s → early engage (ball never exits the top) → blend → chase; ball head dot stays comfortably in frame.
3. **Mid-iron**: engage at the 1.5 s timer; blend visibly smooth; the world sweeps past during chase.
4. **Wedge**: descent pitch-in — camera noses down toward the landing area, reaching ~45° exactly at touchdown with the landing centered.
5. **Landing hold**: overlook holds ~2.5 s; full tracer + landing marker persist; then a CUT (not glide) back to the crane view; the waiting tee ball returns after the cut.
6. **Short chip**: no static/chase — the view goes straight to the overlook; ball lands; hold; cut back.
7. **History**: previous-shot teal lines stay visible and sensible during chase/overlook phases.
8. **Top-down view**: mat quad at the origin with the ball dot on the right side; dispersion dots unchanged.
9. **New shot during any phase**: animation + camera restart cleanly.

If the 45° overlook feels wrong, tune `FollowCam.LAND_PITCH_DEG` first (that constant is the designated knob), then `OVERLOOK_DIST_M` / `LAND_HOLD_SEC`.

- [ ] **Step 4: Update the handover doc TODOs if the pass succeeds**

Mark TODO#2 (ball follow-cam) done in `docs/HANDOVER-2026-09-24.md`, and commit:

```powershell
git add docs/HANDOVER-2026-09-24.md
git commit -m "docs: mark follow-cam handover TODO complete"
```



