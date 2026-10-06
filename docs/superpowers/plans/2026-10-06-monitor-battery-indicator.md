# Monitor Battery Indicator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the MLM2PRO's battery level (already decoded from EVENTS `0x03`) on the bottom status strip while connected.

**Architecture:** Surface the decoded `Mlm2proEvent.Battery` as a client-owned `StateFlow<Int?>` (sibling of the existing connection `state` flow), cleared at the single link-teardown choke point (`cleanupConnection`). Render it via a new `StatusStrip` trailing parameter backed by a pure, JVM-tested display mapper (`BatteryDisplay`) and a small custom-drawn glyph composable.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle. Modules: `:core:designsystem`, `:core:connect`, `:app`.

**Spec:** `docs/superpowers/specs/2026-10-06-monitor-battery-indicator-design.md`

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryIndicator.kt` | Create | Pure display mapper (`BatteryDisplay` + `batteryDisplay`) and the drawn glyph + percent readout composable. |
| `core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryDisplayTest.kt` | Create | Pins clamp (0–100) and low-flag (< 20) behavior. |
| `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/StatusStrip.kt` | Modify | New `batteryPercent: Int? = null` trailing slot; info text constrained so the indicator owns the right edge. |
| `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt` | Modify | `batteryPercent: StateFlow<Int?>` set on `Mlm2proEvent.Battery`; cleared in `cleanupConnection()`. |
| `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` | Modify | Collect the flow; pass it to `StatusStrip`. |

**Conventions (Windows):** set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before every Gradle command. Gradle flags (e.g. `--console=plain`) precede task names. Use the typed `testDebugUnitTest` variants — the aggregate `test` task rejects `--tests`. Gradle is quiet during configure; runs take 5 s–2 min.

---

### Task 1: Battery display mapper (pure, JVM-tested)

**Files:**
- Create: `core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryDisplayTest.kt`
- Create: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryIndicator.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the battery readout mapping: clamp to 0..100 (byte semantics are
 * "believed percent", formally unverified) and low strictly below 20.
 */
class BatteryDisplayTest {

    @Test
    fun normalPercentPassesThrough() {
        assertEquals(BatteryDisplay(percent = 85, low = false), batteryDisplay(85))
    }

    @Test
    fun above100ClampsTo100() {
        assertEquals(BatteryDisplay(percent = 100, low = false), batteryDisplay(105))
    }

    @Test
    fun below0ClampsTo0AndIsLow() {
        assertEquals(BatteryDisplay(percent = 0, low = true), batteryDisplay(-3))
    }

    @Test
    fun lowBoundaryIsStrictlyBelow20() {
        assertTrue(batteryDisplay(19).low)
        assertFalse(batteryDisplay(20).low)
    }

    @Test
    fun zeroIsLowAndHundredIsNot() {
        assertTrue(batteryDisplay(0).low)
        assertFalse(batteryDisplay(100).low)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :core:designsystem:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.designsystem.BatteryDisplayTest"
```
Expected: COMPILATION FAILURE — `unresolved reference: BatteryDisplay` / `batteryDisplay`.

- [ ] **Step 3: Write the minimal implementation**

Create `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryIndicator.kt`:

```kotlin
// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryIndicator.kt
package com.hpsmiles.golfsim.core.designsystem

/** Clamped battery readout state; the readout renders entirely from this. */
internal data class BatteryDisplay(val percent: Int, val low: Boolean)

/**
 * Maps a raw EVENTS 0x03 battery byte to its display state: clamped to
 * 0..100 (semantics "believed percent", formally unverified) and low
 * strictly below 20. Pure so the JVM test pins the boundary.
 */
internal fun batteryDisplay(percent: Int): BatteryDisplay {
    val clamped = percent.coerceIn(0, 100)
    return BatteryDisplay(percent = clamped, low = clamped < 20)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the Step 2 command again.
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 5: Commit**

```powershell
git add core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryIndicator.kt core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryDisplayTest.kt
git commit -m "feat(designsystem): battery display mapping (clamp 0-100, low under 20)"
```

---

### Task 2: Drawn battery glyph + percent readout

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryIndicator.kt` (append composable + imports)

- [ ] **Step 1: Add the composable to `BatteryIndicator.kt`**

Replace the file's package/imports section so it reads exactly:

```kotlin
// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryIndicator.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Clamped battery readout state; the readout renders entirely from this. */
internal data class BatteryDisplay(val percent: Int, val low: Boolean)

/**
 * Maps a raw EVENTS 0x03 battery byte to its display state: clamped to
 * 0..100 (semantics "believed percent", formally unverified) and low
 * strictly below 20. Pure so the JVM test pins the boundary.
 */
internal fun batteryDisplay(percent: Int): BatteryDisplay {
    val clamped = percent.coerceIn(0, 100)
    return BatteryDisplay(percent = clamped, low = clamped < 20)
}

/**
 * Right-edge status-strip readout: a drawn battery glyph (no icon
 * dependency, matching the custom-painter house style) + percent label.
 * TextMuted normally, AlertRed under 20 % (Amber stays reserved for
 * live-moment UI).
 */
@Composable
internal fun BatteryIndicator(percent: Int, modifier: Modifier = Modifier) {
    val display = batteryDisplay(percent)
    val color = if (display.low) GolfColors.AlertRed else GolfColors.TextMuted
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 20.dp, height = 10.dp)) {
            val stroke = 1.dp.toPx()
            val nubWidth = 2.dp.toPx()
            val nubHeight = 4.dp.toPx()
            // 1 dp gap between body and terminal nub.
            val bodyRight = size.width - nubWidth - stroke
            // Terminal nub, vertically centered on the right edge.
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - nubWidth, (size.height - nubHeight) / 2f),
                size = Size(nubWidth, nubHeight),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
            // Body outline.
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(bodyRight - stroke, size.height - stroke),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = stroke),
            )
            // Fill level: inset from the outline, width = percent of interior.
            if (display.percent > 0) {
                val inset = stroke * 2f
                val innerWidth = bodyRight - inset * 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(inset, inset),
                    size = Size(innerWidth * display.percent / 100f, size.height - inset * 2f),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            }
        }
        Text(
            text = "${display.percent}%",
            style = GolfTypography.Status,
            color = color,
            modifier = Modifier.padding(start = GolfSpacing.Xs),
        )
    }
}
```

- [ ] **Step 2: Compile and run the module tests**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :core:designsystem:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL` — `BatteryDisplayTest` plus the existing token tests (`GolfSpacingTest`, `GolfColorsTest`, `GolfTypographyTest`, `GolfMotionTest`) pass.

(No UI-render unit test: the module has no Compose UI test infrastructure; the pure mapper is already pinned in Task 1 and the visual pass happens on-device in Task 6.)

- [ ] **Step 3: Commit**

```powershell
git add core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryIndicator.kt
git commit -m "feat(designsystem): drawn battery glyph + percent readout"
```

---

### Task 3: `StatusStrip` trailing battery slot

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/StatusStrip.kt`

- [ ] **Step 1: Replace the file content**

`StatusStrip.kt` becomes exactly:

```kotlin
// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/StatusStrip.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Bottom status strip: BLE armed dot + session info, plus an optional
 * monitor battery readout pinned to the trailing edge while connected.
 * The info text is weight-constrained (not displaced) so the battery
 * keeps the right edge even for the longest failure labels.
 */
@Composable
fun StatusStrip(
    armed: Boolean,
    info: String,
    modifier: Modifier = Modifier,
    batteryPercent: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GolfSpacing.StatusStripHeight)
            .background(GolfColors.Panel)
            .padding(horizontal = GolfSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (armed) GolfColors.BleArmedGreen else GolfColors.AlertRed,
                    CircleShape,
                ),
        )
        Text(
            text = info,
            style = GolfTypography.Status,
            color = GolfColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = GolfSpacing.Sm)
                .weight(1f, fill = false),
        )
        if (batteryPercent != null) {
            BatteryIndicator(
                percent = batteryPercent,
                modifier = Modifier.padding(start = GolfSpacing.Sm),
            )
        }
    }
}
```

- [ ] **Step 2: Run the module tests**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :core:designsystem:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL` (all module tests still pass; `GolfSpacingTest` pins the unchanged 24 dp strip height).

- [ ] **Step 3: Commit**

```powershell
git add core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/StatusStrip.kt
git commit -m "feat(designsystem): optional battery slot on StatusStrip"
```

---

### Task 4: Surface battery events in `Mlm2proGattClient`

**Files:**
- Modify: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt`

Context: EVENTS `0x03` already decodes to `Mlm2proEvent.Battery(percent)` and is swallowed in `handleNotification` ("battery: state kept"). No decoder change — only a UI-facing flow. No new unit test: the class is Android-GATT-bound (no existing direct tests); the decode path is already pinned by `EventParserTest`, the golden `battery-event-*` fixtures, and `BenchCaptureRoundtripTest` (`Battery(85..83)`), which must stay green.

- [ ] **Step 1: Add the flow next to `state` (currently lines 76–77)**

```kotlin
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    private val _batteryPercent = MutableStateFlow<Int?>(null)

    /**
     * Latest EVENTS 0x03 battery percent for the status strip; null until the
     * device first reports (~30 s cadence) and cleared on any link teardown.
     */
    val batteryPercent: StateFlow<Int?> = _batteryPercent
```

- [ ] **Step 2: Mirror battery events in `handleNotification`'s Event branch**

Replace the current Event branch:

```kotlin
            is Mlm2proMessage.Event -> {
                // ShotDetected/Ready/battery: state kept; sequencer owns protocol.
                // Gate EVENTS misread alerts: handshake emits a single 0x05-0x00
                // "misread" frame around ~1.75 s with no accompanying MEASUREMENT.
                // That artifact arrives while the sequencer is still READY/CONFIG,
                // so only surface it as a user-facing misread once truly armed.
                if (msg.event is Mlm2proEvent.MisreadAlert && sequencer.state == HandshakeState.ARMED) {
                    onMisread?.invoke()
                }
            }
```

with:

```kotlin
            is Mlm2proMessage.Event -> when (val event = msg.event) {
                // Battery: mirror to the UI readout; all other sequencing
                // (shot detected / processing / ready) stays with the sequencer.
                is Mlm2proEvent.Battery -> _batteryPercent.value = event.percent
                // Gate EVENTS misread alerts: handshake emits a single 0x05-0x00
                // "misread" frame around ~1.75 s with no accompanying MEASUREMENT.
                // That artifact arrives while the sequencer is still READY/CONFIG,
                // so only surface it as a user-facing misread once truly armed.
                is Mlm2proEvent.MisreadAlert ->
                    if (sequencer.state == HandshakeState.ARMED) onMisread?.invoke()
                else -> Unit
            }
```

- [ ] **Step 3: Clear the reading in `cleanupConnection`**

In `cleanupConnection(status: Int)`, after `gatt = null`, insert:

```kotlin
        // A battery reading must not outlive its link. All teardown paths
        // route here: link loss, explicit disconnect(), pre-retry teardown.
        _batteryPercent.value = null
```

so the block reads:

```kotlin
        tickerJob?.cancel()
        tickerJob = null
        gattQueue.reset()
        gatt?.close()
        gatt = null
        // A battery reading must not outlive its link. All teardown paths
        // route here: link loss, explicit disconnect(), pre-retry teardown.
        _batteryPercent.value = null
        _state.value =
            if (status == BluetoothGatt.GATT_SUCCESS) ConnectionState.Disconnected
            else ConnectionState.Faulted("gatt $status")
```

- [ ] **Step 4: Compile and run the module tests**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :core:connect:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL` — all existing tests pass, including `BenchCaptureRoundtripTest` (battery-event decode unchanged).

- [ ] **Step 5: Commit**

```powershell
git add core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt
git commit -m "feat(connect): surface battery events as batteryPercent flow"
```

---

### Task 5: Wire the readout into `AppRoot`

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`

- [ ] **Step 1: Collect the flow next to `connectionState` (currently line 101)**

```kotlin
    val connectionState by gattClient.state.collectAsState()
    val batteryPercent by gattClient.batteryPercent.collectAsState()
```

- [ ] **Step 2: Pass it to the `StatusStrip` call (currently lines 819–832)**

```kotlin
            StatusStrip(
                armed = demo || connectionState is ConnectionState.Armed,
                info = if (persistError) {
                    "DB WRITE FAILING"
                } else {
                    describe(
                        connectionState,
                        demo = demo,
                        scanning = scanning,
                        retryLabel = autoRetryLabel,
                        failureReason = lastFailure,
                    )
                },
                batteryPercent = batteryPercent,
            )
```

No other call sites exist (the strip is whole-app, emitted only here). Demo mode needs no special case: the client never connects, so the flow stays null and the indicator stays hidden.

- [ ] **Step 3: Compile and run the app tests**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL` — `DescribeLabelTest` and the other app tests pass unchanged (`describe` untouched).

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(app): show monitor battery on the status strip"
```

---

### Task 6: Full build + on-device verification

**Files:** none (verification task; fix-forward in the owning task if a check fails).

- [ ] **Step 1: Full build (assemble + all JVM tests)**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain build
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Install on the tablet**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :app:installDebug
```
Expected: `BUILD SUCCESSFUL`, app installed on the connected Lenovo tablet.

- [ ] **Step 3: Bench checklist (manual)**

1. Launch the app, connect to the MLM2PRO, reach ARMED.
2. Within ~30 s (first EVENTS `0x03`) a glyph + percent appears flush right on the bottom strip with a plausible value — cross-check the Settings capture export for the raw `0x03` bytes if unsure.
3. The percent updates as further battery events arrive (last event wins).
4. Tap DISCONNECT → the readout disappears immediately (cleared, not stale).
5. Reconnect and power the monitor off (or walk out of range) → readout disappears with the link.
6. With the monitor connected, switch tabs (RANGE / GAMES / BAG / HISTORY / SETTINGS) → the readout is present on each.
7. Landscape visual pass: glyph legible, no clipping of the strip's info text, indicator flush right at 24 dp height.
8. The < 20 % red state cannot be forced on a charged monitor — `BatteryDisplayTest` is its authoritative check (Task 1).

- [ ] **Step 4: Record the outcome**

If all checks pass, no commit is needed. If a visual tweak is required (e.g. glyph proportions), fix it in `BatteryIndicator.kt`, re-run `:core:designsystem:testDebugUnitTest` + `:app:testDebugUnitTest`, and commit as `fix(designsystem): battery glyph proportions`.
