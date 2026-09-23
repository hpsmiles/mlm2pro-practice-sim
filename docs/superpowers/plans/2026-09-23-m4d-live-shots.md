# M4d Implementation Plan — Live Shots in the Range UI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Real decoded MLM2PRO shots appear in the Range UI (shot list, tracer, both canvases) and live misreads surface as a dismissible "no read" pill — replacing the demo-only pipeline.

**Architecture:** Hoist the Range shot list out of `RangeScreen` into a plain `RangeSession` state holder (repo convention: shared plain objects owned by `AppRoot`, no ViewModels/DI). `AppRoot` assigns the existing-but-unwired `Mlm2proGattClient.onMeasurement` callback plus a NEW `onMisread` callback, hopping binder→main via the existing `rememberCoroutineScope()`. A pure conversion function (BallData → physics → DisplayShot) guards against `LaunchConditions` `require()` throws so a weird live decode can never crash the UI thread.

**Tech Stack:** Kotlin, Jetpack Compose (`mutableStateListOf`/`mutableIntStateOf`/`DisposableEffect`), core:connect GATT client, core:physics `BallFlightEngine`, JUnit4 (pure JVM — no Robolectric/Compose-test in this repo).

**Environment:** Before every gradle command: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`. PowerShell 5.1: no `&&`; write multi-line commit messages to a file and use `git commit -F`.

**Branch:** `m4d-live-shots` off `master` (M4b/M4c pattern: branch → PR → boxes → merge).

---

## Background facts (verified 2026-09-23, M4c shed session)

- `Mlm2proGattClient` (core/connect) already declares `var onMeasurement: ((BallData) -> Unit)? = null` (line ~79) and invokes it for every decoded shot, **but nothing in `:app` ever assigns it**. `handleNotification` (line ~336) currently DROPS `BallDataResult.Misread`, `Malformed`, and all EVENTS including `Mlm2proEvent.MisreadAlert`.
- GATT callbacks fire on a **binder thread**; there is no main-thread hop anywhere in `:app` yet. Mutating Compose snapshot state off-main is unsafe.
- `RangeScreen.kt:87` owns `val shots = remember { mutableStateListOf<DisplayShot>() }` privately; `fire()` (line ~118) returns early when `!demo`, so LIVE shots cannot reach the UI at all today.
- `DisplayShot(ballData, launch, shotResult)` — conversion = build `LaunchConditions(ballSpeedMps, launchAngleDeg, spinRpm, spinAxisDeg, launchDirDeg)` then `BallFlightEngine.simulate(launch, Environment(), UniformSurface(Surface.FAIRWAY_NORMAL))`.
- `LaunchConditions` `require()` throws on: ballSpeed ∉ [0.5, 100] m/s, spin ∉ [0, 12000] rpm, VLA ∉ [0, 85], launchDir/spinAxis ∉ [−90, +90]. Demo data always passes; **live data may not**.
- M4c live evidence: one real mishit produces BOTH the EVENTS `05 00` MisreadAlert AND an all-zero MEASUREMENT payload (~200 ms apart) — the UI must coalesce those into one pill increment.
- Sign conventions (documented in `BallData.kt`, `[Verified on-device]`): launchDirection − = left; spinAxis + = fade / − = draw.
- Repo test style: pure JVM JUnit4 objects in `range/` (pattern: `PovProjector`/`PovProjectorTest`). GATT/UI glue is bench-validated, NOT unit-tested (documented module-split decision).

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt` | **Create** | Shot list + tick + misread-coalescing state holder; pure conversion BallData→DisplayShot. |
| `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSessionTest.kt` | **Create** | JUnit4 tests for conversion, guard, misread coalescing. |
| `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt` | Modify | Take `session: RangeSession` param; delete private `shots`; demo `fire()` writes through session; tracer resets on tick; no-read pill UI. |
| `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` | Modify | Own `RangeSession`; `DisposableEffect` assigns `onMeasurement`/`onMisread` with main-thread hop. |
| `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt` | Modify | Add `onMisread` callback; route MEASUREMENT `Misread` + EVENTS `MisreadAlert`. |

---

### Task 1: `RangeSession` state holder (TDD, pure JVM)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt`
- Create: `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSessionTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.ble.BallData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeSessionTest {

    // Real on-device 8i shot (M4c golden fixture, event-008: fade).
    private fun fade() = BallData(
        clubHeadSpeed = 35.5, ballSpeed = 44.2, launchDirection = -6.9,
        launchAngle = 21.7, spinAxis = 11.3, totalSpin = 7463,
        unknown1 = 118, unknown2 = 125,
    )

    // Same shot with ball speed zeroed — violates LaunchConditions require.
    private fun garbage() = fade().copy(ballSpeed = 0.0)

    @Test
    fun `add returns true and appends a DisplayShot with simulated result`() {
        val session = RangeSession()
        assertTrue(session.add(fade()))
        assertEquals(1, session.shots.size)
        val shot = session.shots[0]
        assertEquals(44.2, shot.ballData.ballSpeed, 1e-9)
        assertEquals(21.7, shot.launch.launchAngleDeg, 1e-9)
        assertTrue(shot.shotResult.carryDistanceM > 0.0)
    }

    @Test
    fun `add advances tick by one per shot`() {
        val session = RangeSession()
        val before = session.tick.intValue
        session.add(fade())
        assertEquals(before + 1, session.tick.intValue)
    }

    @Test
    fun `add returns false and appends nothing when LaunchConditions require fails`() {
        val session = RangeSession()
        assertFalse(session.add(garbage()))
        assertEquals(0, session.shots.size)
        assertEquals(0, session.tick.intValue)
    }

    @Test
    fun `misreads coalesce inside 500 ms window`() {
        val session = RangeSession()
        var now = 1_000L
        session.clockMs = { now }
        session.markMisread(atMs = now)
        session.markMisread(atMs = now + 200)   // EVENTS + MEASUREMENT pair
        assertEquals(1, session.misreadCount.intValue)
        session.markMisread(atMs = now + 1_000) // genuinely new misread
        assertEquals(2, session.misreadCount.intValue)
    }

    @Test
    fun `dismissMisreads clears the pill counter`() {
        val session = RangeSession()
        session.markMisread(atMs = 1_000L)
        session.dismissMisreads()
        assertEquals(0, session.misreadCount.intValue)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:test --tests "com.hpsmiles.golfsim.range.RangeSessionTest"`
Expected: FAIL — `RangeSession` unresolved (compile error is the red state).

- [ ] **Step 3: Write the implementation**

```kotlin
package com.hpsmiles.golfsim.range

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.Surface
import com.hpsmiles.golfsim.core.physics.UniformSurface

/**
 * Owns the Range shot list plus the no-read pill counter. Lives in AppRoot so
 * that BOTH the demo fold (RangeScreen) and live BLE callbacks (binder thread,
 * hopped to main by AppRoot) append to the same list.
 *
 * `tick` advances once per accepted shot and is the RangeScreen tracer's
 * reset signal (playFraction → 0 when a new shot lands).
 *
 * `clockMs` is injectable so misread coalescing is unit-testable on the JVM.
 */
class RangeSession {

    val shots: SnapshotStateList<DisplayShot> = mutableStateListOf()
    val tick = mutableIntStateOf(0)
    val misreadCount = mutableIntStateOf(0)

    /** Injectable clock (ms). AppRoot can leave the default. */
    var clockMs: () -> Long = { System.currentTimeMillis() }

    private var lastMisreadMs = Long.MIN_VALUE

    /**
     * Converts one decoded measurement into a rendered shot. Returns false —
     * appending nothing — when the data violates LaunchConditions require()
     * guards, so a bad live decode can never crash the UI thread.
     * M4c evidence + golden fixtures pin the expected live values.
     */
    fun add(ballData: BallData): Boolean {
        val launch = try {
            LaunchConditions(
                ballSpeedMps = ballData.ballSpeed,
                launchAngleDeg = ballData.launchAngle,
                spinRpm = ballData.totalSpin,
                spinAxisDeg = ballData.spinAxis,
                launchDirDeg = ballData.launchDirection,
            )
        } catch (_: IllegalArgumentException) {
            return false
        }
        val result = BallFlightEngine.simulate(
            launch,
            Environment(),
            UniformSurface(Surface.FAIRWAY_NORMAL),
        )
        shots.add(DisplayShot(ballData, launch, result))
        tick.intValue++
        return true
    }

    /**
     * One real mishit emits BOTH the EVENTS MisreadAlert and the all-zero
     * MEASUREMENT sentinel ~200 ms apart (M4c, event-010 + event-027).
     * Coalesce that pair into a single pill increment.
     */
    fun markMisread(atMs: Long = clockMs()) {
        if (atMs - lastMisreadMs < MISREAD_COALESCE_MS) return
        lastMisreadMs = atMs
        misreadCount.intValue++
    }

    fun dismissMisreads() {
        misreadCount.intValue = 0
    }

    private companion object {
        const val MISREAD_COALESCE_MS = 500L
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:test --tests "com.hpsmiles.golfsim.range.RangeSessionTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```powershell
Set-Content -Path "$env:TEMP\opencode\commit-m4d-task1.txt" -Encoding ascii -Value "feat(app): RangeSession state holder for live + demo shots"
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSessionTest.kt
git commit -F "$env:TEMP\opencode\commit-m4d-task1.txt"
```

---

### Task 2: `onMisread` callback in `Mlm2proGattClient`

No unit test: repo convention bench-validates GATT glue (handover §7). The decode paths themselves are already pinned by `EventParserTest`/`MeasurementParserTest`/`LiveShapetestCaptureTest`.

**Files:**
- Modify: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt`

- [ ] **Step 1: Add the callback next to `onMeasurement` (~line 79)**

```kotlin
    /** Fired on any live misread indication (EVENTS 05 00 or all-zero MEASUREMENT). */
    var onMisread: (() -> Unit)? = null
```

- [ ] **Step 2: Route both misread forms in `handleNotification` (~line 334)**

Replace:

```kotlin
        when (msg) {
            is Mlm2proMessage.Measurement ->
                (msg.result as? BallDataResult.Shot)?.let { onMeasurement?.invoke(it.data) }
            is Mlm2proMessage.Event ->
                if (msg.event is Mlm2proEvent.ShotDetected ||
                    msg.event is Mlm2proEvent.Ready
                ) _state.value = _state.value // state kept; sequencer owns protocol
            else -> Unit
        }
```

with:

```kotlin
        when (msg) {
            is Mlm2proMessage.Measurement -> when (val result = msg.result) {
                is BallDataResult.Shot -> onMeasurement?.invoke(result.data)
                is BallDataResult.Misread -> onMisread?.invoke()
                // Malformed MEASUREMENT already recorded in captureLog; drop.
                is BallDataResult.Malformed -> Unit
            }
            is Mlm2proMessage.Event ->
                if (msg.event is Mlm2proEvent.MisreadAlert) onMisread?.invoke()
                // ShotDetected/Ready/battery: state kept; sequencer owns protocol.
                else -> Unit
            else -> Unit
        }
```

Note: this deliberately drops the old no-op `_state.value = _state.value` branch — it was dead code.

- [ ] **Step 3: Verify nothing broke**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:test :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
Set-Content -Path "$env:TEMP\opencode\commit-m4d-task2.txt" -Encoding ascii -Value "feat(core-connect): onMisread callback routes live misread indications"
git add core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt
git commit -F "$env:TEMP\opencode\commit-m4d-task2.txt"
```

---

### Task 3: `RangeScreen` takes the session; tracer reset + no-read pill

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt`

- [ ] **Step 1: New signature + delete the private list**

```kotlin
@Composable
fun RangeScreen(
    modifier: Modifier = Modifier,
    demo: Boolean,
    onDemoChanged: (Boolean) -> Unit,
    onConnectRequested: () -> Unit = {},
    session: RangeSession,
) {
    val demoSource = remember { DemoShotSource() }
    var playFraction by remember { mutableFloatStateOf(1f) }
```

Delete line ~87 (`val shots = remember { mutableStateListOf<DisplayShot>() }`). Then **replace every reference to the local `shots` in the file** with `session.shots` (grep `shots` in the file first and list occurrences: `currentShot`, `TopDownCanvas(shots, ...)`, POV `previousShots` derivation, etc. — no reference is left behind).

- [ ] **Step 2: Demo fold writes through the session**

Replace the body of `fire()` (keep the demo gate):

```kotlin
    fun fire() {
        // Demo pipeline only fires in DEMO mode (live shots arrive via
        // AppRoot's onMeasurement → session.add).
        if (!demo) return
        session.add(demoSource.nextShot())
    }
```

(The session handles LaunchConditions construction, simulation, list append, and tick.)

- [ ] **Step 3: Reset the tracer when a new shot lands**

Immediately BEFORE the existing `LaunchedEffect(currentShot, speedMult) { ... }` block, add:

```kotlin
    // New shot (demo or live): restart the tracer animation.
    LaunchedEffect(session.tick.intValue) {
        playFraction = 0f
    }
```

Keep the existing tracer block unchanged (it keys on `currentShot`, which changes on every append).

- [ ] **Step 4: No-read pill**

In the composable near where the button row / status content is composed (alongside the demo toggle row — match the spacing used there), add:

```kotlin
    if (session.misreadCount.intValue > 0) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                androidx.compose.material3.Text(
                    text = "no read (${session.misreadCount.intValue})",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer,
                )
                androidx.compose.material3.TextButton(onClick = { session.dismissMisreads() }) {
                    androidx.compose.material3.Text("dismiss")
                }
            }
        }
    }
```

(If the file already imports these `material3`/`foundation.layout` symbols, prefer the short names over fully-qualified references; match the file's existing import style. Copy wording is final: "no read", lowercase, per M4d scope notes — surface it, don't drop it.)

- [ ] **Step 5: Verify compile + existing tests**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:test :app:assembleDebug`
Expected: BUILD SUCCESSFUL (this step ends RED against AppRoot until Task 4 — if `:app` compilation of AppRoot fails on the missing `session` argument, that is expected mid-refactor; proceed to Task 4 before re-running).

- [ ] **Step 6: Commit (after Task 4 compiles green — commit together with Task 4)**

---

### Task 4: `AppRoot` owns the session + wires the BLE callbacks

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`

- [ ] **Step 1: Create the session at the composition root (~line 57, next to `captureLog`)**

```kotlin
    val session = remember { RangeSession() }
```

Add the import `com.hpsmiles.golfsim.range.RangeSession` (same package as AppRoot — no import needed if AppRoot is in `range`; skip).

- [ ] **Step 2: Pass it down**

Change the existing `RangeScreen(...)` call (line ~114-119) to also pass

```kotlin
        session = session,
```

- [ ] **Step 3: Wire the callbacks with a main-thread hop**

After the existing `val scope = rememberCoroutineScope()` declaration, add:

```kotlin
    // Live shot delivery: GATT callbacks fire on a binder thread; hop to main
    // before touching Compose snapshot state.
    DisposableEffect(gattClient) {
        gattClient.onMeasurement = { ballData ->
            scope.launch { session.add(ballData) }
        }
        gattClient.onMisread = {
            scope.launch { session.markMisread() }
        }
        onDispose {
            gattClient.onMeasurement = null
            gattClient.onMisread = null
        }
    }
```

Imports to add if absent: `androidx.compose.runtime.DisposableEffect`, `kotlinx.coroutines.launch`.

- [ ] **Step 4: Verify everything**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build --console=plain`
Expected: BUILD SUCCESSFUL — full census green (app tests now 8 + 5 new RangeSessionTest = 13; total 178 + 5 = 183).

- [ ] **Step 5: Commit Tasks 3+4**

```powershell
Set-Content -Path "$env:TEMP\opencode\commit-m4d-task34.txt" -Encoding ascii -Value "feat(app): live BLE shots render in Range UI + no-read pill"
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -F "$env:TEMP\opencode\commit-m4d-task34.txt"
```

---

### Task 5: Bench validation (needs the shed + tablet — NOT automatable)

**Files:** none (verification only).

- [ ] **Step 1: Install on the tablet**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:installDebug` (then `adb shell am start -n com.hpsmiles.golfsim/.MainActivity`).
If INSTALL_FAILED_UPDATE_INCOMPATIBLE: `adb uninstall com.hpsmiles.golfsim` first (debug-keystore change), reinstall.

- [ ] **Step 2: Bench checklist (all must pass before the PR)**

1. CONNECT (single tap, green ARMED LED) — no regression from M4b/M4c.
2. DEMO mode ON: FIRE still appends demo shots + tracer animates (demo fold intact).
3. DEMO OFF, monitor live: hit the bucket → **real shots appear in the list within ~2 s**, tracer draws, metrics plausible vs. the club (8i ≈ ball speed 99–107 mph per M4c batch 1).
4. Hit one deliberate duff → **"no read (1)" pill appears ONCE** (not twice — the Events/Measurement pair must coalesce); dismiss works.
5. Follow-up clean shot still appends normally after a misread (auto-ARM re-fire intact).
6. Settings → CAPTURE ON → EXPORT still works (capture log untouched by refactor).

- [ ] **Step 3: PR (M4b/M4c pattern)**

Branch `m4d-live-shots`, PR title `M4d: Live shots in Range UI`, body with ticked boxes mirroring the checklist above, merge, delete branch.

---

## Self-review notes

- Spec coverage: all four M4d scope-note requirements land (non-shot misreads = pill requirement 4 + never appended as BallData → Task 1 guard; auto-ARM untouched → Task 2 drops only dead code; duff/smash heuristic explicitly out of scope; no-read surfaced, not dropped → Task 3 pill). Live display goal → Tasks 1–4.
- Type consistency: `RangeSession.add(ballData): Boolean`, `markMisread(atMs)`, `misreadCount`, `tick` are used identically in Tasks 1, 3, 4; `onMisread: (() -> Unit)?` matches Task 2's assignment and Task 4's lambda.
- LaunchConditions param names verified against RangeScreen.kt:120-129 verbatim quote; `Surface.FAIRWAY_NORMAL` likewise.
- Compose state off-main is only touched after `scope.launch` (main-dispatched `rememberCoroutineScope`) — the one threading trap in this codebase.
