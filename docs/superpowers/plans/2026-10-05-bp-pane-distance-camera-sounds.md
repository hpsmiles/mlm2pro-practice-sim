# Break the Pane — Pane Distance, Camera Hold, Sounds — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Break the Pane gets a custom pane-distance slider at setup, the camera holds the static player view until the ball crosses the pane plane, and three cue sounds (glass break / success ding / fail) play during playback with a Settings toggle.

**Architecture:** Thread a nullable `paneDistanceM` override from the setup UI through `BreakThePaneGame.start` into `PaneGeom` (middle-row anchor re-uses the existing reference-trajectory API with the effective fraction). Extend `FollowCam.cameraAt` with an optional `holdUntilSec` and `PaneIntersection.Mark` with the crossing `tSec` so playback can gate the camera on the real crossing moment. Add a thin `GameAudio` SoundPool wrapper + `res/raw` assets + a `PaneSoundCues` pure trigger object driven by the existing playback loop.

**Tech Stack:** Kotlin, Jetpack Compose (M3), JUnit4 JVM tests, Android `SoundPool`, SharedPreferences (existing `golfsim_prefs` pattern).

**Spec:** `docs/superpowers/specs/2026-10-05-bp-pane-distance-camera-sounds-design.md`

**Repo conventions (AGENTS.md):**
- Windows: set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before ANY Gradle command.
- Gradle flags precede task names (`--console=plain`); configure can be quiet for 5 s–2 min — not hung.
- Aggregate `test` task rejects `--tests`; always use `:app:testDebugUnitTest --tests "<FQN>"`.
- Room schema untouched (no migration in this feature).
- Shell is PowerShell: `&&` is invalid — use `;`.

---

### Task 0: Feature branch

- [ ] **Step 0.1: Create the branch**

```powershell
git checkout -b m5.8-pane-distance-camera-sounds
```

---

### Task 1: PaneGeom — optional custom plane distance

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneGeom.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneGeomTest.kt`

- [ ] **Step 1.1: Write the failing tests** — add to `PaneGeomTest.kt` (add import `com.hpsmiles.golfsim.core.physics.ReferenceTrajectory`):

```kotlin
@Test
fun `custom pane distance overrides the plane distance`() {
    val custom = PaneGeom(140.0, 42.0)
    assertEquals(42.0, custom.planeYM, 1e-9)
    // Cell sizes stay % of target — only the distance changes.
    assertEquals(2.24, custom.cellHM, 1e-9)
    assertEquals(2.80, custom.cellWM, 1e-9)
}

@Test
fun `custom pane distance anchors the middle row at the effective fraction`() {
    val custom = PaneGeom(140.0, 42.0)
    assertEquals(
        ReferenceTrajectory.paneCrossingHeightM(140.0, 42.0 / 140.0),
        custom.zRefM,
        1e-9,
    )
}

@Test
fun `slider bound constants`() {
    assertEquals(0.10, PaneGeom.MIN_FRACTION, 1e-9)
    assertEquals(0.50, PaneGeom.MAX_FRACTION, 1e-9)
}
```

- [ ] **Step 1.2: Run to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.PaneGeomTest" --console=plain
```

Expected: FAIL — `paneDistanceM` overload does not exist (compile error), `MIN_FRACTION` unresolved.

- [ ] **Step 1.3: Implement** — change `PaneGeom` head (companion + first four vals; everything below `zRefM` is unchanged):

```kotlin
class PaneGeom(val targetM: Double, planeDistanceM: Double? = null) {

    companion object {
        /** Default pane distance as a fraction of the target (item 5, 2026-10-01: 25% -> 20%). */
        const val PLANE_FRACTION = 0.20

        /** Custom-pane-distance slider bounds as a fraction of the target (2026-10-05 spec). */
        const val MIN_FRACTION = 0.10
        const val MAX_FRACTION = 0.50
    }

    /** Plane distance from the tee (m): the override when given, else PLANE_FRACTION x target. */
    val planeYM: Double = planeDistanceM ?: (PLANE_FRACTION * targetM)
    val cellHM: Double = 0.016 * targetM
    val cellWM: Double = 0.020 * targetM
    val zRefM: Double = ReferenceTrajectory.paneCrossingHeightM(targetM, planeYM / targetM)
```

(The KDoc block above the class stays; append one line to it: `* A custom pane distance moves the plane only — cells and the middle-row anchor follow.`)

- [ ] **Step 1.4: Run to verify pass** — same command as Step 1.2. Expected: PASS.

- [ ] **Step 1.5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneGeom.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneGeomTest.kt; git commit -m "feat: PaneGeom accepts a custom plane distance override"
```

---

### Task 2: BreakThePaneGame threads pane distance; PLAY AGAIN preserves it

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGame.kt:55-74` (fields + `start`)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt:154` (PLAY AGAIN)
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGameTest.kt`

- [ ] **Step 2.1: Write the failing tests** — refactor the seam builder so shots can cross a CUSTOM plane, then add two tests. Replace the existing `syntheticShot` helper with:

```kotlin
private fun syntheticShot(crossX: Double, crossZ: Double, restX: Double, restY: Double): (LaunchConditions) -> ShotResult =
    syntheticShotFor(PaneGeom(target), crossX, crossZ, restX, restY)

/** Same seam, but the samples cross the plane of the given PaneGeom (custom distances). */
private fun syntheticShotFor(
    pane: PaneGeom,
    crossX: Double,
    crossZ: Double,
    restX: Double,
    restY: Double,
): (LaunchConditions) -> ShotResult =
    { _ ->
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
```

Add the tests (uses `assertNull` — already imported):

```kotlin
@Test
fun `custom pane distance threads into the pane geometry`() {
    val customPane = PaneGeom(target, 42.0)
    val game = BreakThePaneGame()
    game.simulator = syntheticShotFor(customPane, 0.0, customPane.zRefM, 0.0, target + 1.0)
    game.start(target, paneDistanceM = 42.0)
    assertEquals(42.0, game.pane!!.planeYM, 1e-9)
    assertEquals(42.0, game.paneDistanceM!!, 1e-9)
    val shot = game.add(ball())!!
    assertEquals(BreakOutcomeKind.BROKE, shot.outcome.kind)
    assertEquals(4, shot.outcome.brokenCell)
}

@Test
fun `default start keeps the 20 percent plane`() {
    val game = BreakThePaneGame()
    game.start(target)
    assertEquals(PaneGeom.PLANE_FRACTION * target, game.pane!!.planeYM, 1e-9)
    assertNull(game.paneDistanceM)
}
```

- [ ] **Step 2.2: Run to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.BreakThePaneGameTest" --console=plain
```

Expected: FAIL — `start` has no `paneDistanceM` param, `game.paneDistanceM` unresolved (compile error).

- [ ] **Step 2.3: Implement** — in `BreakThePaneGame`, next to the `difficulty` field:

```kotlin
var paneDistanceM: Double? = null
    private set
```

Replace `start`:

```kotlin
fun start(targetM: Double, difficulty: Difficulty = Difficulty.MEDIUM, paneDistanceM: Double? = null) {
    this.targetM = targetM
    this.difficulty = difficulty
    this.paneDistanceM = paneDistanceM
    pane = PaneGeom(targetM, paneDistanceM)
    shots.clear()
    brokenCells.clear()
    lastFeedback = ""
    resultTaken = false
    record.value = null
}
```

In `BreakPanePlay.kt:154`, make PLAY AGAIN replay with the same pane distance:

```kotlin
Button(onClick = { game.start(game.targetM, game.difficulty, game.paneDistanceM) }) { Text("PLAY AGAIN") }
```

- [ ] **Step 2.4: Run to verify pass** — same command as Step 2.2. Expected: PASS (all existing tests still pass — seam refactor is behavior-identical).

- [ ] **Step 2.5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGame.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGameTest.kt; git commit -m "feat: BreakThePaneGame accepts and preserves a custom pane distance"
```

---

### Task 3: PaneIntersection.Mark carries the crossing time

The camera hold needs the crossing moment in `cameraAt`'s seconds base. `revealFraction` is measured over the drawn (flight + rollout) sample list — the right scale for the visual dot reveal, not for the camera. The crossing samples carry `tSec`, so compute it in the same traversal. `Mark` is only constructed inside `mark()` (verified: `PaneIntersectionTest` reads fields only), so adding a field is safe.

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneIntersection.kt:16-33`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneIntersectionTest.kt`

- [ ] **Step 3.1: Write the failing test** — add to `PaneIntersectionTest`:

```kotlin
@Test
fun `mark reports the crossing time in flight seconds`() {
    val mark = PaneIntersection.mark(flight(0.5, 10.0), planeY, apexVMin = -1.0)
    assertNotNull(mark)
    // The crossing sample sits at tSec = 3.0 in the synthetic flight.
    assertEquals(3.0, mark!!.tSec, 1e-9)
}
```

- [ ] **Step 3.2: Run to verify it fails**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.PaneIntersectionTest" --console=plain
```

Expected: FAIL — `tSec` unresolved (compile error).

- [ ] **Step 3.3: Implement** — in `PaneIntersection.kt`:

```kotlin
/** Crossing point plus when it becomes visible along the tracer (0..1) and the crossing time in flight seconds. */
data class Mark(val xM: Double, val zM: Double, val revealFraction: Float, val tSec: Double)
```

In `mark(...)`, replace the return:

```kotlin
val t = (planeYM - a.py) / (b.py - a.py)
val x = a.px + (b.px - a.px) * t
val z = a.pz + (b.pz - a.pz) * t
val frac = ((i - 1) + t) / (samples.size - 1)
val tSec = a.tSec + (b.tSec - a.tSec) * t
return Mark(x, z, frac.coerceIn(0.0, 1.0).toFloat(), tSec)
```

- [ ] **Step 3.4: Run to verify pass** — same command as Step 3.2. Expected: PASS.

- [ ] **Step 3.5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneIntersection.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneIntersectionTest.kt; git commit -m "feat: PaneIntersection.Mark reports the crossing time in flight seconds"
```

---

### Task 4: FollowCam holdUntilSec — stay static until the pane crossing

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/FollowCam.kt:129-195` (`cameraAt`)
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/range/FollowCamTest.kt`

- [ ] **Step 4.1: Write the failing tests** — add to `FollowCamTest`:

```kotlin
@Test
fun holdUntilSecKeepsStaticPastTheNormalEngage() {
    val shot = parabolicShot() // normally engages on the 1.0 s timer
    val hold = 3.0
    // 2.0 s would already be blending today; with the hold it must still be STATIC.
    assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(shot, frac(shot, 2.0), holdUntilSec = hold))
    // 3.5 s: past the hold, mid-blend (3.0-4.4 s) -> camera is moving.
    assertNotEquals(RangeCamera.STATIC, FollowCam.cameraAt(shot, frac(shot, 3.5), holdUntilSec = hold))
}

@Test
fun holdUntilSecNullKeepsTodayBehavior() {
    val shot = parabolicShot()
    assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(shot, frac(shot, 0.7), holdUntilSec = null))
    assertNotEquals(RangeCamera.STATIC, FollowCam.cameraAt(shot, frac(shot, 1.6), holdUntilSec = null))
}

@Test
fun holdUntilSecAlsoAppliesToShortShots() {
    val chip = parabolicShot(carryM = 15.0, apexM = 6.0, flightTimeSec = 1.6)
    // t = 0.64 s < hold -> STATIC even though the short-shot branch would park at the overlook.
    assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(chip, frac(chip, 0.4), holdUntilSec = 0.8))
    // t = 1.0 s > hold -> back on the short-shot overlook path.
    val pitch = Math.toRadians(FollowCam.LAND_PITCH_DEG)
    val cam = FollowCam.cameraAt(chip, frac(chip, 1.0), holdUntilSec = 0.8)
    assertEquals(pitch, cam.pitchRad, 1e-12)
}
```

- [ ] **Step 4.2: Run to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.FollowCamTest" --console=plain
```

Expected: FAIL — `holdUntilSec` unresolved (compile error).

- [ ] **Step 4.3: Implement** — in `cameraAt`, add the parameter and gate both branches. Signature + doc first line:

```kotlin
/**
 * The camera for a shot at [playFraction] (0 = impact, 1 = touchdown).
 * [apexVMin] is the frame's top-8% clamp line — pass the same value
 * the canvas uses so the chase follows the exact drawn flight.
 * [holdUntilSec] optionally forces the STATIC rig until that flight time
 * (Break the Pane holds the player view until the ball has crossed the
 * pane plane, 2026-10-05); the normal blend, chase, descent, hold and
 * snap-back then run unchanged with engageT shifted to the hold.
 */
fun cameraAt(
    shot: ShotResult,
    playFraction: Float,
    apexVMin: Double = REFERENCE_APEX_VMIN,
    holdUntilSec: Double? = null,
): RangeCamera {
```

Short-shot branch (currently `if (t <= FOLLOW_DELAY_SEC + BLEND_SEC) { return if (timeSec < t) overlook ... }`) becomes:

```kotlin
if (t <= FOLLOW_DELAY_SEC + BLEND_SEC) {
    val hold = holdUntilSec ?: 0.0
    return when {
        timeSec < hold -> RangeCamera.STATIC
        timeSec < t -> overlook
        else -> descentRig(sampleAt(drawn, timeSec), overlook, 1.0)
    }
}
```

Engage line (currently `val engageT = earlyEngageT(drawn)?.coerceAtMost(FOLLOW_DELAY_SEC) ?: FOLLOW_DELAY_SEC`) becomes:

```kotlin
val engageT = maxOf(
    earlyEngageT(drawn)?.coerceAtMost(FOLLOW_DELAY_SEC) ?: FOLLOW_DELAY_SEC,
    holdUntilSec ?: 0.0,
)
```

- [ ] **Step 4.4: Run to verify pass** — same command as Step 4.2. Expected: PASS (all 15 existing camera tests unchanged behavior with default null).

- [ ] **Step 4.5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/FollowCam.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/FollowCamTest.kt; git commit -m "feat: FollowCam holds the static rig until a requested flight time"
```

---

### Task 5: Reachability probes at the custom-distance extremes

**Files:**
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakPaneReachabilityTest.kt`

- [ ] **Step 5.1: Write the tests** — extend `cellFor` with a pane-distance param (only the `PaneGeom(...)` line changes) and add two probes:

```kotlin
private fun cellFor(
    targetM: Double,
    paneDistanceM: Double? = null,
    ballSpeedMps: Double = STOCK_BALL_SPEED,
    launchAngleDeg: Double = STOCK_LAUNCH_ANGLE,
    spinRpm: Int = STOCK_SPIN,
    spinAxisDeg: Double = 0.0,
    launchDirDeg: Double = 0.0,
): Int {
    val result = simulate(ballSpeedMps, launchAngleDeg, spinRpm, spinAxisDeg, launchDirDeg)
    return PaneGeom(targetM, paneDistanceM).firstCrossing(result.samples)?.cell
        ?: error("probe shot did not cross the pane plane")
}

@Test
fun `stock 8i crosses the pane at the 10 percent custom distance`() {
    val cell = cellFor(targetM = 140.0, paneDistanceM = 14.0) // 10% of 140
    assertTrue("expected middle column (3..5) but got cell=$cell", cell in 3..5)
}

@Test
fun `stock 8i crosses the pane at the 50 percent custom distance`() {
    val cell = cellFor(targetM = 140.0, paneDistanceM = 70.0) // 50% of 140
    assertTrue("expected middle column (3..5) but got cell=$cell", cell in 3..5)
}
```

- [ ] **Step 5.2: Run to verify pass** (no implementation — this pins that the custom extremes stay hittable):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.BreakPaneReachabilityTest" --console=plain
```

Expected: PASS. (If a probe misses the middle column, STOP and investigate `ReferenceTrajectory` anchoring before proceeding — do not loosen the assertion.)

- [ ] **Step 5.3: Commit**

```powershell
git add app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakPaneReachabilityTest.kt; git commit -m "test: reachability probes for custom pane distances at 10 and 50 percent"
```

---

### Task 6: Sound assets — generate the three cue WAVs

**Files:**
- Create: `tools/make-pane-sounds.ps1` (committed for provenance)
- Create: `app/src/main/res/raw/pane_success_ding.wav`, `app/src/main/res/raw/pane_fail.wav`, `app/src/main/res/raw/pane_glass_break.wav`

- [ ] **Step 6.1: Write the generator script** `tools/make-pane-sounds.ps1`:

```powershell
# Generates the three Break-the-Pane cue WAVs into app/src/main/res/raw.
# Deterministic 44.1 kHz 16-bit mono; the committed binaries come from this script.
$ErrorActionPreference = "Stop"
$rate = 44100
$outDir = Join-Path $PSScriptRoot "..\app\src\main\res\raw"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Write-Wav([string]$name, [double[]]$samples) {
    $path = Join-Path $outDir $name
    $bytes = [byte[]]::new(44 + $samples.Length * 2)
    $enc = [Text.Encoding]::ASCII
    $enc.GetBytes("RIFF").CopyTo($bytes, 0)
    [BitConverter]::GetBytes([uint32]($bytes.Length - 8)).CopyTo($bytes, 4)
    $enc.GetBytes("WAVE").CopyTo($bytes, 8)
    $enc.GetBytes("fmt ").CopyTo($bytes, 12)
    [BitConverter]::GetBytes([uint32]16).CopyTo($bytes, 16)
    [BitConverter]::GetBytes([uint16]1).CopyTo($bytes, 20)   # PCM
    [BitConverter]::GetBytes([uint16]1).CopyTo($bytes, 22)   # mono
    [BitConverter]::GetBytes([uint32]$rate).CopyTo($bytes, 24)
    [BitConverter]::GetBytes([uint32]($rate * 2)).CopyTo($bytes, 28)
    [BitConverter]::GetBytes([uint16]2).CopyTo($bytes, 32)
    [BitConverter]::GetBytes([uint16]16).CopyTo($bytes, 34)
    $enc.GetBytes("data").CopyTo($bytes, 36)
    [BitConverter]::GetBytes([uint32]($samples.Length * 2)).CopyTo($bytes, 40)
    for ($i = 0; $i -lt $samples.Length; $i++) {
        $v = [int][Math]::Max(-32768, [Math]::Min(32767, [int]($samples[$i] * 32767)))
        [BitConverter]::GetBytes([int16]$v).CopyTo($bytes, 44 + $i * 2)
    }
    [IO.File]::WriteAllBytes($path, $bytes)
    Write-Host "wrote $path"
}

# Ding (success): 880 + 1760 Hz partials, 0.55 s exponential decay.
$n = [int](0.55 * $rate); $ding = [double[]]::new($n)
for ($i = 0; $i -lt $n; $i++) {
    $t = $i / $rate; $env = [Math]::Exp(-6.0 * $t)
    $ding[$i] = 0.6 * $env * ([Math]::Sin(2 * [Math]::PI * 880 * $t) + 0.5 * [Math]::Sin(2 * [Math]::PI * 1760 * $t))
}
Write-Wav "pane_success_ding.wav" $ding

# Fail: 150 Hz thud with pitch drop + noise tick, 0.4 s.
$n = [int](0.4 * $rate); $fail = [double[]]::new($n); $rnd = [System.Random]::new(42)
for ($i = 0; $i -lt $n; $i++) {
    $t = $i / $rate; $env = [Math]::Exp(-9.0 * $t)
    $f = 150.0 - 60.0 * $t
    $fail[$i] = 0.8 * $env * [Math]::Sin(2 * [Math]::PI * $f * $t) + 0.15 * $env * ($rnd.NextDouble() * 2 - 1)
}
Write-Wav "pane_fail.wav" $fail

# Glass break: burst of decaying high partials + noise crackle, 0.5 s.
$n = [int](0.5 * $rate); $glass = [double[]]::new($n); $rnd2 = [System.Random]::new(7)
$partials = @()
for ($p = 0; $p -lt 18; $p++) {
    $partials += @(1800.0 + 4200.0 * $rnd2.NextDouble(), 0.35 * $rnd2.NextDouble(), 18.0 + 40.0 * $rnd2.NextDouble())
}
for ($i = 0; $i -lt $n; $i++) {
    $t = $i / $rate
    $v = 0.35 * [Math]::Exp(-7.0 * $t) * ($rnd2.NextDouble() * 2 - 1)
    for ($p = 0; $p -lt $partials.Count; $p += 3) {
        $v += $partials[$p + 1] * [Math]::Exp(-$partials[$p + 2] * $t) * [Math]::Sin(2 * [Math]::PI * $partials[$p] * $t)
    }
    $glass[$i] = $v
}
Write-Wav "pane_glass_break.wav" $glass
```

- [ ] **Step 6.2: Run it and verify the three files exist**

```powershell
powershell -File tools\make-pane-sounds.ps1; Get-ChildItem app\src\main\res\raw | Select-Object Name, Length
```

Expected: three `.wav` files, each a few KB (25–45 KB).

- [ ] **Step 6.3: Commit**

```powershell
git add tools/make-pane-sounds.ps1 app/src/main/res/raw; git commit -m "feat: generated Break-the-Pane cue sounds (glass, ding, fail)"
```

---

### Task 7: PaneSoundCues — pure trigger logic

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneSoundCues.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneSoundCuesTest.kt`

- [ ] **Step 7.1: Write the failing test** — `PaneSoundCuesTest.kt`:

```kotlin
package com.hpsmiles.golfsim.games

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaneSoundCuesTest {

    @Test
    fun `green rests ding - broke and green-missed-pane - others fail`() {
        assertTrue(PaneSoundCues.restsOnGreen(BreakOutcomeKind.BROKE))
        assertTrue(PaneSoundCues.restsOnGreen(BreakOutcomeKind.GREEN_MISSED_PANE))
        assertFalse(PaneSoundCues.restsOnGreen(BreakOutcomeKind.HIT_PANE_MISSED_GREEN))
        assertFalse(PaneSoundCues.restsOnGreen(BreakOutcomeKind.MISSED_BOTH))
    }

    @Test
    fun `glass fires once at the reveal fraction`() {
        assertFalse(PaneSoundCues.glassDue(0.4f, 0.5f, alreadyPlayed = false))
        assertTrue(PaneSoundCues.glassDue(0.5f, 0.5f, alreadyPlayed = false))
        assertTrue(PaneSoundCues.glassDue(0.9f, 0.5f, alreadyPlayed = false))
        assertFalse(PaneSoundCues.glassDue(0.9f, 0.5f, alreadyPlayed = true))
    }
}
```

- [ ] **Step 7.2: Run to verify it fails**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.PaneSoundCuesTest" --console=plain
```

Expected: FAIL — `PaneSoundCues` unresolved (compile error).

- [ ] **Step 7.3: Implement** — `PaneSoundCues.kt`:

```kotlin
package com.hpsmiles.golfsim.games

/**
 * Pure cue logic for Break-the-Pane sounds (2026-10-05 spec): the playback
 * loop asks whether a cue is due; GameAudio performs it. Event-based so
 * 1x-4x playback speed stays in sync; replays (speed change restarts the
 * loop) legitimately re-trigger.
 */
object PaneSoundCues {

    /** True when the shot's ball rests on the green (ding); false is a miss (fail). */
    fun restsOnGreen(kind: BreakOutcomeKind): Boolean =
        kind == BreakOutcomeKind.BROKE || kind == BreakOutcomeKind.GREEN_MISSED_PANE

    /** Glass fires once per playback, at/after the pane-crossing reveal fraction. */
    fun glassDue(playFraction: Float, revealFraction: Float, alreadyPlayed: Boolean): Boolean =
        !alreadyPlayed && playFraction >= revealFraction
}
```

- [ ] **Step 7.4: Run to verify pass** — same command as Step 7.2. Expected: PASS.

- [ ] **Step 7.5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneSoundCues.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneSoundCuesTest.kt; git commit -m "feat: pure sound-cue logic for Break the Pane"
```

---

### Task 8: GameAudio + Sounds setting + wiring

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/audio/GameAudio.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/settings/SoundPrefStore.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt` (params + SOUND card after the TURF card)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` (own + wire + dispose; pass to `GamesScreen`/`SettingsScreen`)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt` (thread `gameAudio` through to `BreakPanePlay` — param only; the play screen consumes it in Task 10)

- [ ] **Step 8.1: Create `GameAudio.kt`:**

```kotlin
package com.hpsmiles.golfsim.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.hpsmiles.golfsim.R

/**
 * Thin SoundPool wrapper for the three Break-the-Pane cues (2026-10-05 spec).
 * No-ops when disabled or until clips finish loading (SoundPool.play is a
 * silent no-op before load completes, so nothing blocks playback).
 */
class GameAudio(context: Context) {

    var enabled: Boolean = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val glassId: Int = pool.load(context, R.raw.pane_glass_break, 1)
    private val dingId: Int = pool.load(context, R.raw.pane_success_ding, 1)
    private val failId: Int = pool.load(context, R.raw.pane_fail, 1)

    fun glassBreak() = play(glassId)
    fun success() = play(dingId)
    fun fail() = play(failId)

    private fun play(id: Int) {
        if (!enabled) return
        pool.play(id, 0.9f, 0.9f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
```

- [ ] **Step 8.2: Create `SoundPrefStore.kt`:**

```kotlin
package com.hpsmiles.golfsim.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Sounds on/off (Settings > SOUND). SharedPreferences alongside the other
 * single-value stores (GreenConditionStore pattern) — DataStore unnecessary.
 */
class SoundPrefStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun get(): Boolean = prefs.getBoolean(KEY, true)

    fun set(enabled: Boolean) {
        prefs.edit().putBoolean(KEY, enabled).apply()
    }

    private companion object {
        const val PREFS_FILE = "golfsim_prefs"
        const val KEY = "sounds_enabled"
    }
}
```

- [ ] **Step 8.3: SettingsScreen — add the toggle.** Extend the signature (after the turf params):

```kotlin
    turfCondition: TurfCondition = TurfCondition.FIRM,
    onTurfConditionChange: (TurfCondition) -> Unit = {},
    soundsEnabled: Boolean = true,
    onSoundsChange: (Boolean) -> Unit = {},
```

Add a card between the TURF and DEBUG cards:

```kotlin
        SectionCard("SOUND") {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(
                    text = if (soundsEnabled) "SOUNDS: ON" else "SOUNDS: OFF",
                    style = GolfTypography.MetricLabel,
                    color = chipStyle(soundsEnabled),
                    modifier = Modifier
                        .border(1.dp, if (soundsEnabled) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                        .clickable { onSoundsChange(!soundsEnabled) }
                        .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                )
            }
            Text(
                text = "Game cue sounds (glass break, success, miss).",
                style = GolfTypography.Status,
                color = GolfColors.TextMuted,
            )
        }
```

- [ ] **Step 8.4: AppRoot wiring.** After the turf store block (~line 133), add (imports: `androidx.compose.runtime.DisposableEffect` if absent, `com.hpsmiles.golfsim.audio.GameAudio`, `com.hpsmiles.golfsim.settings.SoundPrefStore`):

```kotlin
    val gameAudio = remember { GameAudio(context) }
    DisposableEffect(Unit) {
        onDispose { gameAudio.release() }
    }

    val soundPrefStore = remember { SoundPrefStore(context) }
    var soundsEnabled by remember { mutableStateOf(soundPrefStore.get()) }
    LaunchedEffect(soundsEnabled) { gameAudio.enabled = soundsEnabled }

    fun selectSoundsEnabled(enabled: Boolean) {
        soundsEnabled = enabled
        soundPrefStore.set(enabled)
    }
```

`GamesScreen(...)` call (~line 613) gains `gameAudio = gameAudio,`; `SettingsScreen(...)` call (~line 622) gains `soundsEnabled = soundsEnabled, onSoundsChange = ::selectSoundsEnabled,`.

- [ ] **Step 8.5: GamesScreen threading.** Signature gains `gameAudio: GameAudio` (import `com.hpsmiles.golfsim.audio.GameAudio`); the `activeGame == GameMode.BREAK_PANE` branch passes it: `BreakPanePlay(game = breakPane, gameAudio = gameAudio, onBack = ..., modifier = modifier)`. (`TargetPracticePlay` unchanged.)

- [ ] **Step 8.6: Compile-verify** (Android glue — no JVM test; device checks in Task 11):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8.7: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/audio/GameAudio.kt app/src/main/kotlin/com/hpsmiles/golfsim/settings/SoundPrefStore.kt app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt; git commit -m "feat: GameAudio SoundPool wrapper, Sounds setting, AppRoot wiring"
```

---

### Task 9: Setup UI — custom pane distance toggle + slider

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt` (`GameSetupScreen`, `BreakPaneSetup`, `SETUP_BP` branch, picker blurb)

- [ ] **Step 9.1: `GameSetupScreen` gains a slot + target callback.** New signature and head (imports: add `androidx.compose.foundation.layout.ColumnScope`):

```kotlin
@Composable
private fun GameSetupScreen(
    title: String,
    onStart: (Double, Difficulty) -> Unit,
    onCancel: () -> Unit,
    onTargetChange: (Double) -> Unit = {},
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var sliderM by remember { mutableStateOf(140.0) }
    var text by remember { mutableStateOf("140") }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
```

Slider `onValueChange` becomes:

```kotlin
            onValueChange = {
                sliderM = it.toDouble()
                text = String.format(Locale.US, "%.0f", sliderM)
                onTargetChange(sliderM)
            },
```

Text field `onValueChange` becomes (keeps the do-not-rewrite-text-while-typing nuance):

```kotlin
            onValueChange = { raw ->
                text = raw
                raw.toDoubleOrNull()?.let {
                    sliderM = it.coerceIn(DIST_MIN_M, DIST_MAX_M)
                    onTargetChange(sliderM)
                }
            },
```

Insert the slot after the text field, before the difficulty chips:

```kotlin
        extraContent?.invoke(this)
```

- [ ] **Step 9.2: `BreakPaneSetup` — toggle + slider.** Replace the whole composable:

```kotlin
@Composable
private fun BreakPaneSetup(onStart: (Double?, Double, Difficulty) -> Unit, onCancel: () -> Unit) {
    var targetM by remember { mutableStateOf(140.0) }
    var customPane by remember { mutableStateOf(false) }
    var paneM by remember { mutableStateOf(PaneGeom.PLANE_FRACTION * 140.0) }

    val paneMinM = targetM * PaneGeom.MIN_FRACTION
    val paneMaxM = targetM * PaneGeom.MAX_FRACTION

    GameSetupScreen(
        title = "BREAK THE PANE",
        onStart = { target, difficulty ->
            val custom = if (customPane) {
                paneM.coerceIn(target * PaneGeom.MIN_FRACTION, target * PaneGeom.MAX_FRACTION)
            } else null
            onStart(custom, target, difficulty)
        },
        onCancel = onCancel,
        onTargetChange = { newTarget ->
            targetM = newTarget
            // Spec: target changes clamp the custom value; OFF->ON re-seeds at 20%.
            if (customPane) {
                paneM = paneM.coerceIn(newTarget * PaneGeom.MIN_FRACTION, newTarget * PaneGeom.MAX_FRACTION)
            }
        },
        extraContent = {
            Text(
                text = if (customPane) "CUSTOM PANE DISTANCE: ON" else "CUSTOM PANE DISTANCE: OFF",
                style = GolfTypography.MetricLabel,
                color = if (customPane) GolfColors.Teal else GolfColors.TextMuted,
                modifier = Modifier
                    .border(1.dp, if (customPane) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                    .clickable {
                        customPane = !customPane
                        if (customPane) paneM = targetM * PaneGeom.PLANE_FRACTION
                    }
                    .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
            )
            if (customPane) {
                Text(
                    text = "PANE DISTANCE (m): " + String.format(Locale.US, "%.0f", paneM) +
                        "  (" + (paneM / targetM * 100).toInt() + "% of target)",
                    style = GolfTypography.Body,
                    color = GolfColors.TextPrimary,
                )
                Slider(
                    value = paneM.toFloat(),
                    onValueChange = { paneM = it.toDouble() },
                    valueRange = paneMinM.toFloat()..paneMaxM.toFloat(),
                    steps = ((paneMaxM - paneMinM) - 1.0).toInt().coerceAtLeast(0),
                )
            }
        },
    )
}
```

- [ ] **Step 9.3: `SETUP_BP` branch + blurb.** The branch becomes:

```kotlin
                BreakPaneSetup(
                    onStart = { paneDistanceM, target, difficulty ->
                        breakPane.start(target, difficulty, paneDistanceM)
                        onActiveGameChange(GameMode.BREAK_PANE)
                        pickerState = "PLAYING"
                    },
                    onCancel = { pickerState = "PICKER" },
                )
```

Picker blurb (line 68) — fix the stale 25% copy:

```kotlin
                    blurb = "Break all 9 glass cells floating 20% of the way to the target (customizable in setup). Fewer shots wins.",
```

- [ ] **Step 9.4: Compile-verify**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9.5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt; git commit -m "feat: custom pane distance toggle and slider on Break-the-Pane setup"
```

---

### Task 10: BreakPanePlay — camera hold + sound triggers

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt`

- [ ] **Step 10.1: Camera hold.** Add import `com.hpsmiles.golfsim.audio.GameAudio`. Add a per-shot mark right after the `speedMult` state:

```kotlin
    val lastShot = game.shots.lastOrNull()
    // One traversal per shot: tSec gates the camera, revealFraction gates the glass cue
    // (same quantity the canvas uses for the intersection dot, so they stay in sync).
    val paneMark = remember(lastShot, game.pane) {
        val result = lastShot?.shot?.shotResult
        val pane = game.pane
        if (result == null || pane == null) null
        else PaneIntersection.mark(result, pane.planeYM, FollowCam.RAW_APEX_VMIN)
    }
```

Camera selection becomes:

```kotlin
                val camera = when (val shot = lastShot) {
                    null -> RangeCamera.STATIC
                    else -> FollowCam.cameraAt(shot.shot.shotResult, playFraction, apexVMin, paneMark?.tSec)
                }
```

- [ ] **Step 10.2: Sound triggers in the playback loop.** Replace the `LaunchedEffect` body:

```kotlin
    LaunchedEffect(game.tick.intValue, speedMult) {
        val shot = game.shots.lastOrNull() ?: return@LaunchedEffect
        val result = shot.shot.shotResult
        if (result.samples.isEmpty()) {
            playFraction = FollowCam.endFraction(result).toFloat()
            return@LaunchedEffect
        }
        if (result.flightTimeSec <= 0.0) return@LaunchedEffect
        val durationMs = result.flightTimeSec * 1000.0 / speedMult.divisor
        val end = FollowCam.endFraction(result).toFloat()
        val reveal = paneMark?.revealFraction
        var glassPlayed = false
        var last = withFrameNanos { it }
        playFraction = 0f
        while (playFraction < end) {
            val now = withFrameNanos { it }
            playFraction = (playFraction + ((now - last) / 1_000_000.0 / durationMs).toFloat()).coerceAtMost(end)
            last = now
            // Glass fires at the pane-crossing moment, synced with the visual mark.
            if (shot.outcome.kind == BreakOutcomeKind.BROKE &&
                reveal != null && PaneSoundCues.glassDue(playFraction, reveal, glassPlayed)
            ) {
                glassPlayed = true
                gameAudio.glassBreak()
            }
        }
        // Ball at rest: ding on the green, fail on a miss (2026-10-05 spec).
        if (PaneSoundCues.restsOnGreen(shot.outcome.kind)) gameAudio.success() else gameAudio.fail()
    }
```

(Speed changes relaunch this effect and restart playback from 0 — the glass cue legitimately re-fires, matching the spec's "replays re-trigger". Zero-sample/zero-flight shots skip sounds: nothing visible plays.)

- [ ] **Step 10.3: Compile + full unit suite**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`, all app tests pass.

- [ ] **Step 10.4: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt; git commit -m "feat: pane-crossing camera hold and cue sounds in Break-the-Pane playback"
```

---

### Task 11: Tidy-ups, full build, device verification

**Files:**
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ReferenceTrajectory.kt:44-45`

- [ ] **Step 11.1: Align the stale default param** (harmless today — `PaneGeom` passes explicitly — but stale at 0.25):

```kotlin
    /** z height where the stock shot for [targetM] crosses the pane plane (y = fraction x target). */
    fun paneCrossingHeightM(targetM: Double, planeFraction: Double = 0.20): Double {
```

- [ ] **Step 11.2: Full build (assemble + all tests):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build --console=plain
```

Expected: `BUILD SUCCESSFUL` (all modules assemble, all JVM tests pass).

- [ ] **Step 11.3: Commit**

```powershell
git add core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ReferenceTrajectory.kt; git commit -m "chore: align paneCrossingHeightM default fraction with the 0.20 pane"
```

- [ ] **Step 11.4: Device verification (tablet, user in the loop).** Install and check with real shots:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:installDebug --console=plain
```

Checklist (from spec §Testing):
1. Games > BREAK THE PANE: setup shows CUSTOM PANE DISTANCE OFF by default; START → pane floats at 20% of target (28 m @ 140 m).
2. Toggle ON: slider 10–50% of target in 1 m steps, seeded at 20%; drag to 35 m, target 140 m → pane visibly nearer/farther; PLAY AGAIN after completion keeps the custom distance.
3. Set target 140 m, pane 20 m and 70 m: shot stays in the static player view until the ball passes the pane plane, then follow-cam blends in; dufts (never crossing) behave as before.
4. Sounds: glass at the pane break (matching the amber dot), ding when the ball rests on the green, fail when it misses; works at 1x and 4x; replays (speed change) re-fire glass.
5. Settings > SOUND: OFF silences all three; ON restores. Preference survives app restart.

---

## Self-review notes (writing-plans checklist)

- **Spec coverage:** slider (Tasks 1, 2, 9), camera hold (Tasks 3, 4, 10), sounds + settings toggle (Tasks 6, 7, 8, 10), reachability extremes (Task 5), blurb + default-param tidy-ups (Tasks 9, 11), device verification (Task 11.4). Pane distance NOT persisted to `game_results` (spec non-goal) — no Room change anywhere.
- **Placeholders:** none — every code step carries complete code.
- **Type consistency:** `paneDistanceM: Double?` threads `GamesScreen.BreakPaneSetup.onStart(paneDistanceM, targetM, difficulty)` → `BreakThePaneGame.start(targetM, difficulty, paneDistanceM)` → `PaneGeom(targetM, planeDistanceM)`. `Mark.tSec: Double` feeds `cameraAt(..., holdUntilSec: Double?)`. `PaneSoundCues.restsOnGreen(BreakOutcomeKind): Boolean` / `glassDue(playFraction, revealFraction, alreadyPlayed): Boolean` used verbatim in Task 10.

