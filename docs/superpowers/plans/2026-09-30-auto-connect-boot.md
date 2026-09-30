# Auto-Connect on Boot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app opens in LIVE mode and immediately scans → connects → auto-arms with zero taps; failures retry 3 times with 5/10/20 s backoff, then fall back to the manual CONNECT ritual.

**Architecture:** A pure, JVM-tested `AutoConnectPolicy` state machine in `core/connect` decides every action (attempt now, wait-then-retry, arm, give up, stop); `AppRoot` keeps only thin wiring: a boot permission gate, one attempt runner, and a client-state observer that feeds events into the policy. No changes to `Mlm2proGattClient` (it already auto-arms via its ticker, `Mlm2proGattClient.kt:248-253`) or `Mlm2proScanner`.

**Tech Stack:** Kotlin, Jetpack Compose, coroutines/Flow, JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-30-auto-connect-boot-design.md` (user-approved)

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/AutoConnectPolicy.kt` | Create | Pure retry/arm/stop decision machine (no Android imports) |
| `core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/AutoConnectPolicyTest.kt` | Create | JUnit4 tests for every policy rule |
| `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` | Modify | Boot permission gate, attempt runner, state observer, `demo=false`, retry label in status strip |

Repo conventions (from AGENTS.md):
- Windows: set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before any Gradle command.
- `core/connect` is a JVM module — its test task is `:core:connect:test` (NOT `testDebugUnitTest`).
- Gradle flags precede task names; quiet configure ≠ hung (runs take 5 s–2 min).
- Compose UI logic is not unit-tested; correctness is pinned by the pure policy tests + on-tablet manual verification.

---

### Task 1: `AutoConnectPolicy` (pure state machine, TDD)

**Files:**
- Test: `core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/AutoConnectPolicyTest.kt`
- Create: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/AutoConnectPolicy.kt`

- [ ] **Step 1: Write the failing test**

Create `core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/AutoConnectPolicyTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins every rule of the auto-connect cycle (spec
 * 2026-09-30-auto-connect-boot-design.md §2): immediate first attempt,
 * 3 retries with 5/10/20 s backoff, arm-on-establish with budget reset,
 * stop-on-disconnect, manual restart.
 */
class AutoConnectPolicyTest {

    @Test
    fun startEmitsStartAttemptAndClearsStop() {
        val p = AutoConnectPolicy()
        p.userDisconnect()
        assertEquals(AutoConnectPolicy.Action.StartAttempt, p.start())
        assertFalse(p.stopped)
    }

    @Test
    fun immediateAttemptThenThreeBackoffRetriesThenGiveUp() {
        val p = AutoConnectPolicy()
        assertEquals(AutoConnectPolicy.Action.StartAttempt, p.start())
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(1, 5_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(2, 10_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(3, 20_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.GiveUp, p.attemptFailed())
        // A given-up machine ignores further failure events.
        assertNull(p.attemptFailed())
    }

    @Test
    fun linkEstablishedArms() {
        val p = AutoConnectPolicy()
        p.start()
        assertEquals(AutoConnectPolicy.Action.Arm, p.linkEstablished())
    }

    @Test
    fun linkDropAfterEstablishStartsFreshRetryCycle() {
        val p = AutoConnectPolicy()
        p.start()
        p.attemptFailed() // burned one retry (2 left)
        p.linkEstablished() // resets the budget
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(1, 5_000L), p.linkDropped())
    }

    @Test
    fun dropWithoutEstablishIsIgnored() {
        val p = AutoConnectPolicy()
        assertNull(p.linkDropped())
    }

    @Test
    fun attemptFailedWhileEstablishedIsIgnored() {
        val p = AutoConnectPolicy()
        p.start()
        p.linkEstablished()
        assertNull(p.attemptFailed())
    }

    @Test
    fun secondEstablishIsIgnored() {
        val p = AutoConnectPolicy()
        p.start()
        p.linkEstablished()
        assertNull(p.linkEstablished())
    }

    @Test
    fun userDisconnectStopsMachine() {
        val p = AutoConnectPolicy()
        p.start()
        p.linkEstablished()
        assertEquals(AutoConnectPolicy.Action.Stop, p.userDisconnect())
        assertTrue(p.stopped)
        assertNull(p.attemptFailed())
        assertNull(p.linkDropped())
        assertNull(p.linkEstablished())
    }

    @Test
    fun manualConnectAfterStopRestartsFreshCycle() {
        val p = AutoConnectPolicy()
        p.start()
        p.userDisconnect()
        assertEquals(AutoConnectPolicy.Action.StartAttempt, p.manualConnect())
        assertFalse(p.stopped)
        // Full retry budget again after the restart.
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(1, 5_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(2, 10_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(3, 20_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.GiveUp, p.attemptFailed())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :core:connect:test
```

Expected: FAIL — compilation error `unresolved reference: AutoConnectPolicy` (the class does not exist yet).

- [ ] **Step 3: Write the minimal implementation**

Create `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/AutoConnectPolicy.kt`:

```kotlin
// core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/AutoConnectPolicy.kt
package com.hpsmiles.golfsim.core.connect

/**
 * Pure decision machine for the auto-connect-on-boot cycle (spec
 * docs/superpowers/specs/2026-09-30-auto-connect-boot-design.md §2). No
 * Android imports; every event returns the single [Action] the caller should
 * perform, or null when the event must be ignored (stopped machine, state
 * guard). Deterministic: same event sequence → same action sequence.
 *
 * Cycle: an immediate first attempt ([Action.StartAttempt]), then up to
 * [maxRetries] retries with growing backoff ([Action.WaitThenAttempt]). A
 * newly established link emits [Action.Arm] and resets the retry budget, so
 * a later link drop starts a fresh cycle. DISCONNECT stops the machine until
 * the next manual connect. Failure sequence: attempt 1 (immediate), then
 * waits 5 s → 10 s → 20 s before retries 1–3; the 4th failure gives up.
 */
class AutoConnectPolicy(
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val backoffMs: List<Long> = DEFAULT_BACKOFF_MS,
) {
    sealed interface Action {
        /** Run one scan+connect attempt immediately. */
        data object StartAttempt : Action

        /** Wait [delayMs], then run attempt [retryNumber] (1-based). */
        data class WaitThenAttempt(val retryNumber: Int, val delayMs: Long) : Action

        /** Link established — arm the device (client auto-arms too; belt). */
        data object Arm : Action

        /** Retry budget exhausted — surface BLE DISCONNECTED, stop cycling. */
        data object GiveUp : Action

        /** User disconnect — no further retries until a connect event. */
        data object Stop : Action
    }

    /** True after [userDisconnect] until the next connect/start event. */
    var stopped = false
        private set

    private var retriesLeft = maxRetries
    private var established = false
    private var givenUp = false

    /** Boot or app-open trigger. */
    fun start(): Action = begin()

    /** Manual CONNECT chip / permission-grant trigger. Fresh cycle. */
    fun manualConnect(): Action = begin()

    /** Scan timeout, connect fault or handshake fault before establishment. */
    fun attemptFailed(): Action? {
        if (stopped || established || givenUp) return null
        return retryOrGiveUp()
    }

    /** Handshake completed (client reached Armed/Disarmed). */
    fun linkEstablished(): Action? {
        if (stopped || established) return null
        established = true
        givenUp = false
        retriesLeft = maxRetries
        return Action.Arm
    }

    /** A previously-established link was lost (drop or fault). */
    fun linkDropped(): Action? {
        if (stopped || !established) return null
        established = false
        retriesLeft = maxRetries
        return retryOrGiveUp()
    }

    /** DISCONNECT chip — cancel cycling; manualConnect() restarts. */
    fun userDisconnect(): Action {
        stopped = true
        established = false
        return Action.Stop
    }

    private fun begin(): Action {
        stopped = false
        established = false
        givenUp = false
        retriesLeft = maxRetries
        return Action.StartAttempt
    }

    private fun retryOrGiveUp(): Action? {
        if (retriesLeft <= 0) {
            givenUp = true
            return Action.GiveUp
        }
        val n = maxRetries - retriesLeft + 1
        retriesLeft--
        val delayMs = backoffMs[(n - 1).coerceAtMost(backoffMs.lastIndex)]
        return Action.WaitThenAttempt(n, delayMs)
    }

    companion object {
        const val DEFAULT_MAX_RETRIES = 3
        val DEFAULT_BACKOFF_MS = listOf(5_000L, 10_000L, 20_000L)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :core:connect:test
```

Expected: PASS — BUILD SUCCESSFUL, `AutoConnectPolicyTest` all green alongside the existing `core/connect` suites.

- [ ] **Step 5: Commit**

```powershell
git add core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/AutoConnectPolicy.kt core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/AutoConnectPolicyTest.kt
git commit -m "feat(connect): pure auto-connect retry policy"
```

---

### Task 2: `AppRoot` wiring (boot cycle, LIVE default, retry label)

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`

All edits are against the current file (560 lines). Read the file first; anchors reference current content.

- [ ] **Step 1: Add imports**

In the import block (lines 42–66), add these five imports (the file groups imports alphabetically):

```kotlin
import com.hpsmiles.golfsim.core.connect.AutoConnectPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull
```

- [ ] **Step 2: Boot in LIVE mode**

Replace (lines 206–207):

```kotlin
    // MODE toggle state lives here so the StatusStrip demo fallback mirrors it.
    var demo by remember { mutableStateOf(true) }
```

with:

```kotlin
    // MODE toggle state lives here so the StatusStrip demo fallback mirrors it.
    // Auto-connect boot (2026-09-30 spec §1): the app boots LIVE, connects and
    // auto-arms on its own; MODE: DEMO is an explicit opt-in for the FIRE
    // shot simulator.
    var demo by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Replace the permission gate + connect flow with the auto-connect machinery**

Replace the entire block from

```kotlin
    // BLE permission gate hoisted from RangeScreen (CONNECT moved to the rail).
    var permissionDenied by remember { mutableStateOf(false) }
```

(line 217) down to the end of `connectTapped()` (the closing brace just before the line `    // M5 Task 9: history tab state.` at line 277) with the block below.

```kotlin
    // --- Auto-connect boot cycle (spec 2026-09-30 §2) ------------------------
    // Pure policy decides; this block only executes its actions. All event
    // routing happens on the main thread (collectors, launcher callback and
    // chip onClicks), so there are no cross-thread races on the policy.
    val autoPolicy = remember { AutoConnectPolicy() }
    var autoRetryLabel by remember { mutableStateOf<String?>(null) }
    // Once-per-link latch: Arm fires on the first Armed/Disarmed observation
    // of a link; manual STANDBY afterwards never re-triggers it.
    var linkEstablishedOnce by remember { mutableStateOf(false) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    var attemptJob by remember { mutableStateOf<Job?>(null) }

    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val allGranted = grants.values.all { it }
        permissionDenied = !allGranted
        // Grant (first boot or after a deny) begins a fresh cycle immediately —
        // no second CONNECT tap (spec §1). Same behavior for a CONNECT tap that
        // had to request permissions first.
        if (allGranted) handleAutoAction(autoPolicy.manualConnect())
    }

    /**
     * One cycle attempt: scan with a 10 s budget, then GATT-connect.
     * Returns true only when the caller must report a failure to the policy
     * (scan timeout — no state transition follows). Every other failure
     * arrives later as a Faulted/Disconnected state and is routed by the
     * observer; connect() itself is guarded against forked links.
     */
    @Suppress("MissingPermission")
    suspend fun runConnectAttempt(): Boolean {
        if (scanning) return false
        val s = connectionState
        if (s != ConnectionState.Disconnected && s !is ConnectionState.Faulted) return false
        linkEstablishedOnce = false
        scanning = true
        try {
            val device = withTimeoutOrNull(10_000L) {
                Mlm2proScanner(context).scan().first()
            }
            if (device == null) {
                Log.w(Mlm2proGattClient.TAG, "auto-connect: scan timeout")
                return true
            }
            if (autoPolicy.stopped) return false // user disconnected mid-scan
            Log.i(Mlm2proGattClient.TAG, "scan found device=${device.address}")
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            gattClient.connect(manager.adapter.getRemoteDevice(device.address))
            return false
        } catch (e: Exception) {
            Log.w(Mlm2proGattClient.TAG, "connect flow failed", e)
            gattClient.reportFault(e.javaClass.simpleName) // observer routes the retry
            return false
        } finally {
            scanning = false
        }
    }

    /** Execute one policy action on the main thread. */
    fun handleAutoAction(action: AutoConnectPolicy.Action?) {
        when (action) {
            is AutoConnectPolicy.Action.StartAttempt -> {
                retryJob?.cancel(); retryJob = null
                autoRetryLabel = null
                attemptJob?.cancel()
                attemptJob = scope.launch {
                    if (runConnectAttempt()) handleAutoAction(autoPolicy.attemptFailed())
                }
            }
            is AutoConnectPolicy.Action.WaitThenAttempt -> {
                attemptJob?.cancel()
                autoRetryLabel = "RETRYING (${action.retryNumber}/${autoPolicy.maxRetries})…"
                retryJob = scope.launch {
                    delay(action.delayMs)
                    autoRetryLabel = null
                    attemptJob = scope.launch {
                        if (runConnectAttempt()) handleAutoAction(autoPolicy.attemptFailed())
                    }
                }
            }
            // The client's ticker already arms 500 ms after READY; this is a
            // guarded no-op belt (sequencer.arm() returns null when invalid).
            AutoConnectPolicy.Action.Arm -> gattClient.arm()
            AutoConnectPolicy.Action.GiveUp -> autoRetryLabel = null
            AutoConnectPolicy.Action.Stop -> {
                retryJob?.cancel(); retryJob = null
                attemptJob?.cancel(); attemptJob = null
                autoRetryLabel = null
            }
            null -> Unit
        }
    }

    /** Route client state transitions into policy events (spec §2 observer). */
    fun routeConnectionState(s: ConnectionState) {
        when (s) {
            is ConnectionState.Armed, is ConnectionState.Disarmed ->
                if (!linkEstablishedOnce) {
                    linkEstablishedOnce = true
                    handleAutoAction(autoPolicy.linkEstablished())
                }
            is ConnectionState.Faulted ->
                if (linkEstablishedOnce) {
                    linkEstablishedOnce = false
                    handleAutoAction(autoPolicy.linkDropped())
                } else {
                    handleAutoAction(autoPolicy.attemptFailed())
                }
            is ConnectionState.Disconnected ->
                if (linkEstablishedOnce) {
                    linkEstablishedOnce = false
                    handleAutoAction(autoPolicy.linkDropped())
                }
            ConnectionState.Connecting, ConnectionState.Handshaking -> Unit
        }
    }

    LaunchedEffect(gattClient, autoPolicy) {
        gattClient.state.collect { routeConnectionState(it) }
    }

    // Boot: connect as soon as the app opens (spec §1). Missing permissions
    // are requested immediately; the launcher callback starts the cycle on
    // grant. Runs once per composition lifetime (key Unit).
    LaunchedEffect(Unit) {
        val scanGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_SCAN,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val connectGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (scanGranted && connectGranted) {
            handleAutoAction(autoPolicy.start())
        } else {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                ),
            )
        }
    }

    fun connectTapped() {
        val scanGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_SCAN,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val connectGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (scanGranted && connectGranted) {
            permissionDenied = false
            handleAutoAction(autoPolicy.manualConnect())
        } else {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                ),
            )
        }
    }
```

Implementer notes:
- `scanning`, `connectionState`, `scope`, `gattClient`, `context` already exist above this block; keep them.
- The old `onConnectRequested()` is deleted (its body became `runConnectAttempt()`); the old launcher callback and `connectTapped()` bodies are replaced.
- `kotlinx.coroutines.flow.first` is already imported; do not re-add.
- Local function order matters: `runConnectAttempt` must be declared before `handleAutoAction` (Kotlin locals cannot forward-reference).

- [ ] **Step 4: DISCONNECT chip cancels the cycle**

Replace (lines 370–375):

```kotlin
                        RailChip(
                            label = "DISCONNECT",
                            border = GolfColors.Line,
                            onClick = { disconnectClient(gattClient) },
                        )
```

with:

```kotlin
                        RailChip(
                            label = "DISCONNECT",
                            border = GolfColors.Line,
                            onClick = {
                                // Stop the cycle BEFORE tearing the link down so a
                                // pending wait/attempt cannot resurrect the connection.
                                handleAutoAction(autoPolicy.userDisconnect())
                                linkEstablishedOnce = false
                                disconnectClient(gattClient)
                            },
                        )
```

- [ ] **Step 5: Retry label in the status strip**

Replace `describe()` (lines 536–548) with:

```kotlin
/** StatusStrip text mapping per the M4b plan (demo fallback first). */
private fun describe(
    state: ConnectionState,
    demo: Boolean,
    scanning: Boolean,
    retryLabel: String? = null,
): String = when {
    demo -> "DEMO MODE - FIRE TO SHOOT"
    retryLabel != null -> retryLabel
    scanning -> "SCANNING…"
    else -> when (state) {
        ConnectionState.Disconnected -> "BLE DISCONNECTED"
        ConnectionState.Connecting -> "CONNECTING…"
        ConnectionState.Handshaking -> "HANDSHAKING…"
        ConnectionState.Armed -> "ARMED"
        ConnectionState.Disarmed -> "DISARMED - STANDBY"
        is ConnectionState.Faulted -> "BLE FAULTED - SEE CAPTURE"
    }
}
```

(Note: keep the existing `\u2026` escape style used in the file for the ellipses if preferred — either renders identically.)

And update the StatusStrip call (line 470–473) to pass the label:

```kotlin
            StatusStrip(
                armed = demo || connectionState is ConnectionState.Armed,
                info = if (persistError) "DB WRITE FAILING" else describe(connectionState, demo = demo, scanning = scanning, retryLabel = autoRetryLabel),
            )
```

- [ ] **Step 6: Build + install**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :app:installDebug :core:connect:test
```

Expected: BUILD SUCCESSFUL (APK installed on the tablet). Compose compile errors here are almost always the local-function declaration order from Step 3 — `runConnectAttempt` before `handleAutoAction`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(app): auto-connect on boot (LIVE, bounded retries)"
```

---

### Task 3: Verification

- [ ] **Step 1: Full build + all unit tests**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain build
```

Expected: BUILD SUCCESSFUL — assemble plus every module's tests (`:core:ble`, `:core:connect`, `:app`, …).

- [ ] **Step 2: On-tablet manual verification** (tablet `HA2C96BN`, package `com.hpsmiles.golfsim`)

1. **Cold start with monitor ON:** open the app → strip cycles `SCANNING…` → `CONNECTING…` → `HANDSHAKING…` → `ARMED` within ~15 s, in LIVE mode (MODE chip reads `MODE: LIVE`). No taps.
2. **Cold start with monitor OFF:** strip shows `SCANNING…` → `RETRYING (1/3)…` → `SCANNING…` → `RETRYING (2/3)…` → `SCANNING…` → `RETRYING (3/3)…` → `BLE DISCONNECTED` (~40 s total). No `NO PERM` chip.
3. **Recovery:** power the monitor on → tap CONNECT (or restart the app) → reaches `ARMED`.
4. **Mid-session drop:** turn the monitor off after ARMED → strip leaves ARMED, shows the retry cycle → with monitor back on within retries, returns to `ARMED`.
5. **DISCONNECT cancels:** tap DISCONNECT while linked → strip `BLE DISCONNECTED`, monitor LED settles, no retry label appears afterward.
6. **STANDBY override:** tap STANDBY → `DISARMED - STANDBY` stays; the app never re-arms by itself.
7. **DEMO opt-in:** tap MODE → `MODE: DEMO`, FIRE produces a demo shot; MODE back → LIVE.
8. **First-run permissions** (only if the app is freshly installed): open → system dialog appears immediately → Allow → cycle starts with no further tap.

- [ ] **Step 3: Final commit (if verification required fixes)**

Any fix discovered during verification gets its own commit; re-run `.\gradlew.bat --console=plain build` before finishing.
