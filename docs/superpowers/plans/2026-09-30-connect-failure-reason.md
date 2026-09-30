# Connect Failure Reason on Status Strip — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a human-readable outline of *why* the MLM2PRO connection failed on the bottom status strip, during the retry cycle and after the final failure.

**Architecture:** AppRoot wiring records the last failure cause (`lastFailure: String?`, display-ready); a pure mapper (`ConnectFailureHints`) translates raw `Faulted` reasons into short uppercase phrases; `describe()` combines the reason with the existing label. No core changes, no composable changes.

**Tech Stack:** Kotlin, Jetpack Compose (existing AppRoot), plain JUnit4 JVM tests in the `:app` module.

**Spec:** `docs/superpowers/specs/2026-09-30-connect-failure-reason-design.md`

**Environment (Windows):** every Gradle command needs
`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` first. Gradle flags precede task names (`--console=plain`). The aggregate `test` task rejects `--tests`; always use the typed `:app:testDebugUnitTest` variant. Gradle runs take 5 s–2 min; quiet ≠ hung.

**Raw reasons produced today** (input domain of the mapper, from `Mlm2proGattClient` + `RapsodoTokenProvider` + AppRoot):
`gatt <status>`, `service discovery <status>`, `discoverServices rejected`,
`connectGatt returned null`, `token fetch: <cause>`, `SecurityException`
(from AppRoot's `reportFault(e.javaClass.simpleName)`), and the wiring-only
scan-timeout sentinel.

---

### Task 1: `ConnectFailureHints` pure mapper + tests

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/ConnectFailureHints.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/range/ConnectFailureHintsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/hpsmiles/golfsim/range/ConnectFailureHintsTest.kt`:

```kotlin
package com.hpsmiles.golfsim.range

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectFailureHintsTest {

    @Test
    fun `scan timeout sentinel maps to no monitor found`() {
        assertEquals(
            "NO MONITOR FOUND",
            ConnectFailureHints.phrase(ConnectFailureHints.SCAN_TIMEOUT),
        )
    }

    @Test
    fun `gatt 8 is monitor not responding`() {
        assertEquals("MONITOR NOT RESPONDING (GATT 8)", ConnectFailureHints.phrase("gatt 8"))
    }

    @Test
    fun `gatt 19 is monitor closed link`() {
        assertEquals("MONITOR CLOSED LINK (GATT 19)", ConnectFailureHints.phrase("gatt 19"))
    }

    @Test
    fun `gatt 133 is connect rejected`() {
        assertEquals("CONNECT REJECTED (GATT 133)", ConnectFailureHints.phrase("gatt 133"))
    }

    @Test
    fun `unmapped gatt status falls back to link error`() {
        assertEquals("LINK ERROR (GATT 34)", ConnectFailureHints.phrase("gatt 34"))
    }

    @Test
    fun `service discovery failures collapse to one phrase`() {
        assertEquals("SERVICE DISCOVERY FAILED", ConnectFailureHints.phrase("service discovery 8"))
        assertEquals("SERVICE DISCOVERY FAILED", ConnectFailureHints.phrase("discoverServices rejected"))
    }

    @Test
    fun `connectGatt null refusal maps to stack refused`() {
        assertEquals("BLUETOOTH STACK REFUSED", ConnectFailureHints.phrase("connectGatt returned null"))
    }

    @Test
    fun `token fetch keeps the underlying cause`() {
        assertEquals("TOKEN FETCH FAILED: HTTP 503", ConnectFailureHints.phrase("token fetch: HTTP 503"))
    }

    @Test
    fun `SecurityException maps to missing permission`() {
        assertEquals("BLUETOOTH PERMISSION MISSING", ConnectFailureHints.phrase("SecurityException"))
    }

    @Test
    fun `unknown reasons fall back with the raw text`() {
        assertEquals("CONNECT FAILED: something odd", ConnectFailureHints.phrase("something odd"))
    }

    @Test
    fun `blank reasons map to bare connect failed`() {
        assertEquals("CONNECT FAILED", ConnectFailureHints.phrase(""))
        assertEquals("CONNECT FAILED", ConnectFailureHints.phrase("   "))
    }
}
```

- [ ] **Step 2: Run test to verify it fails (unresolved reference)**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.ConnectFailureHintsTest"
```

Expected: **compile error** — `unresolved reference: ConnectFailureHints`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/kotlin/com/hpsmiles/golfsim/range/ConnectFailureHints.kt`:

```kotlin
package com.hpsmiles.golfsim.range

/**
 * Display-only translation of connection-failure reasons into short
 * uppercase status-strip phrases (spec 2026-09-30 §4). Raw reasons are
 * load-bearing in core (`ConnectionState.Faulted.reason`), logcat and
 * capture exports — this layer never rewrites them.
 */
object ConnectFailureHints {
    /** Wiring sentinel: the 10 s scan budget expired with no advertisement. */
    const val SCAN_TIMEOUT = "scan-timeout"

    /**
     * Map a raw failure reason (`Faulted.reason` or [SCAN_TIMEOUT]) to a
     * strip phrase. Unknown input falls back to `CONNECT FAILED: <raw>`;
     * blank input to bare `CONNECT FAILED`.
     */
    fun phrase(raw: String): String = when {
        raw == SCAN_TIMEOUT -> "NO MONITOR FOUND"
        raw == "gatt 8" -> "MONITOR NOT RESPONDING (GATT 8)"
        raw == "gatt 19" -> "MONITOR CLOSED LINK (GATT 19)"
        raw == "gatt 133" -> "CONNECT REJECTED (GATT 133)"
        raw.startsWith("gatt ") -> "LINK ERROR (${raw.uppercase()})"
        raw.startsWith("service discovery ") || raw == "discoverServices rejected" ->
            "SERVICE DISCOVERY FAILED"
        raw == "connectGatt returned null" -> "BLUETOOTH STACK REFUSED"
        raw.startsWith("token fetch: ") -> "TOKEN FETCH FAILED: ${raw.removePrefix("token fetch: ")}"
        raw == "SecurityException" -> "BLUETOOTH PERMISSION MISSING"
        raw.isBlank() -> "CONNECT FAILED"
        else -> "CONNECT FAILED: $raw"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.ConnectFailureHintsTest"
```

Expected: **BUILD SUCCESSFUL**, 11 tests passing.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/ConnectFailureHints.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/ConnectFailureHintsTest.kt
git commit -m "feat(app): connect failure reason phrases (pure mapper)"
```

---

### Task 2: `describe()` reason parameter + label tests

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt:663-681` (the `describe` function)
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/range/DescribeLabelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/hpsmiles/golfsim/range/DescribeLabelTest.kt`:

```kotlin
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.connect.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DescribeLabelTest {

    @Test
    fun `demo suppresses the failure reason`() {
        assertEquals(
            "DEMO MODE - FIRE TO SHOOT",
            describe(
                ConnectionState.Faulted("gatt 133"),
                demo = true,
                scanning = false,
                failureReason = "CONNECT REJECTED (GATT 133)",
            ),
        )
    }

    @Test
    fun `retry label combines with the reason`() {
        assertEquals(
            "RETRYING (2/3)… NO MONITOR FOUND",
            describe(
                ConnectionState.Disconnected,
                demo = false,
                scanning = false,
                retryLabel = "RETRYING (2/3)…",
                failureReason = "NO MONITOR FOUND",
            ),
        )
    }

    @Test
    fun `retry label without reason stays bare`() {
        assertEquals(
            "RETRYING (2/3)…",
            describe(
                ConnectionState.Disconnected,
                demo = false,
                scanning = false,
                retryLabel = "RETRYING (2/3)…",
            ),
        )
    }

    @Test
    fun `scanning hides the reason`() {
        assertEquals(
            "SCANNING…",
            describe(
                ConnectionState.Disconnected,
                demo = false,
                scanning = true,
                failureReason = "NO MONITOR FOUND",
            ),
        )
    }

    @Test
    fun `faulted shows the reason and never SEE CAPTURE`() {
        assertEquals(
            "BLE FAULTED - CONNECT REJECTED (GATT 133)",
            describe(
                ConnectionState.Faulted("gatt 133"),
                demo = false,
                scanning = false,
                failureReason = "CONNECT REJECTED (GATT 133)",
            ),
        )
        val bare = describe(ConnectionState.Faulted("gatt 133"), demo = false, scanning = false)
        assertEquals("BLE FAULTED", bare)
        assertFalse(bare.contains("SEE CAPTURE"))
    }

    @Test
    fun `disconnected after giveup keeps the reason`() {
        assertEquals(
            "BLE DISCONNECTED - NO MONITOR FOUND",
            describe(
                ConnectionState.Disconnected,
                demo = false,
                scanning = false,
                failureReason = "NO MONITOR FOUND",
            ),
        )
        assertEquals(
            "BLE DISCONNECTED",
            describe(ConnectionState.Disconnected, demo = false, scanning = false),
        )
    }

    @Test
    fun `armed and standby labels are untouched`() {
        assertEquals(
            "ARMED",
            describe(ConnectionState.Armed, demo = false, scanning = false),
        )
        assertEquals(
            "DISARMED - STANDBY",
            describe(ConnectionState.Disarmed, demo = false, scanning = false),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails (compile error)**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.DescribeLabelTest"
```

Expected: **compile error** — `describe` has no `failureReason` parameter (and is `private`).

- [ ] **Step 3: Update `describe()`**

In `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`, replace the
function (currently lines 663-681):

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

with (`private` → `internal` so the app-module tests reach it; new optional
`failureReason`; SEE CAPTURE removed per spec §2.4):

```kotlin
/**
 * StatusStrip text mapping (M4b plan; failure reason per the 2026-09-30
 * connect-failure-reason spec §5 — SEE CAPTURE removed, raw reasons live in
 * logcat/capture). Retry labels append the reason with a space; state labels
 * with " - " matching existing style.
 */
internal fun describe(
    state: ConnectionState,
    demo: Boolean,
    scanning: Boolean,
    retryLabel: String? = null,
    failureReason: String? = null,
): String = when {
    demo -> "DEMO MODE - FIRE TO SHOOT"
    retryLabel != null ->
        if (failureReason != null) "$retryLabel $failureReason" else retryLabel
    scanning -> "SCANNING…"
    else -> when (state) {
        ConnectionState.Disconnected ->
            if (failureReason != null) "BLE DISCONNECTED - $failureReason" else "BLE DISCONNECTED"
        ConnectionState.Connecting -> "CONNECTING…"
        ConnectionState.Handshaking -> "HANDSHAKING…"
        ConnectionState.Armed -> "ARMED"
        ConnectionState.Disarmed -> "DISARMED - STANDBY"
        is ConnectionState.Faulted ->
            if (failureReason != null) "BLE FAULTED - $failureReason" else "BLE FAULTED"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.DescribeLabelTest"
```

Expected: **BUILD SUCCESSFUL**, 7 tests passing.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/DescribeLabelTest.kt
git commit -m "feat(app): describe() combines failure reason into status label"
```

---

### Task 3: AppRoot wiring — record/clear the cause, feed the strip

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` (four spots: state declaration, `runConnectAttempt`, `handleAutoAction`, `routeConnectionState`, `StatusStrip` call)

No new tests — the wiring is thin execution of logic already pinned by Tasks 1–2; verified by the full build + on-tablet checks below.

- [ ] **Step 1: Declare `lastFailure` state**

In the auto-connect block, directly after the `autoRetryLabel` declaration
(currently line 229), add:

```kotlin
    var autoRetryLabel by remember { mutableStateOf<String?>(null) }
```

becomes

```kotlin
    var autoRetryLabel by remember { mutableStateOf<String?>(null) }
    // Last failure cause, display-ready (spec 2026-09-30 connect-failure-reason
    // §3): set on scan timeout and on every Faulted; cleared when a new attempt
    // starts, the link establishes, or the user disconnects. GiveUp keeps it so
    // the final BLE DISCONNECTED label explains itself.
    var lastFailure by remember { mutableStateOf<String?>(null) }
```

- [ ] **Step 2: Record the scan-timeout cause**

In `runConnectAttempt`, the scan-timeout branch (currently lines 255-258):

```kotlin
            if (device == null) {
                Log.w(Mlm2proGattClient.TAG, "auto-connect: scan timeout")
                return true
            }
```

becomes

```kotlin
            if (device == null) {
                Log.w(Mlm2proGattClient.TAG, "auto-connect: scan timeout")
                lastFailure = ConnectFailureHints.SCAN_TIMEOUT
                return true
            }
```

- [ ] **Step 3: Clear on new attempt and on user disconnect**

In `handleAutoAction` — the `StartAttempt` branch:

```kotlin
            is AutoConnectPolicy.Action.StartAttempt -> {
                retryJob?.cancel(); retryJob = null
                autoRetryLabel = null
                attemptJob?.cancel()
```

becomes

```kotlin
            is AutoConnectPolicy.Action.StartAttempt -> {
                retryJob?.cancel(); retryJob = null
                autoRetryLabel = null
                lastFailure = null
                attemptJob?.cancel()
```

and the `Stop` branch:

```kotlin
            AutoConnectPolicy.Action.Stop -> {
                retryJob?.cancel(); retryJob = null
                attemptJob?.cancel(); attemptJob = null
                autoRetryLabel = null
            }
```

becomes

```kotlin
            AutoConnectPolicy.Action.Stop -> {
                retryJob?.cancel(); retryJob = null
                attemptJob?.cancel(); attemptJob = null
                autoRetryLabel = null
                lastFailure = null
            }
```

Leave `GiveUp` untouched — the final label must keep its reason.

- [ ] **Step 4: Record Faulted causes, clear on establishment**

In `routeConnectionState` (currently lines 325-346):

```kotlin
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
```

becomes

```kotlin
    fun routeConnectionState(s: ConnectionState) {
        when (s) {
            is ConnectionState.Armed, is ConnectionState.Disarmed ->
                if (!linkEstablishedOnce) {
                    linkEstablishedOnce = true
                    lastFailure = null
                    handleAutoAction(autoPolicy.linkEstablished())
                }
            is ConnectionState.Faulted -> {
                lastFailure = ConnectFailureHints.phrase(s.reason)
                if (linkEstablishedOnce) {
                    linkEstablishedOnce = false
                    handleAutoAction(autoPolicy.linkDropped())
                } else {
                    handleAutoAction(autoPolicy.attemptFailed())
                }
            }
            is ConnectionState.Disconnected ->
                if (linkEstablishedOnce) {
                    linkEstablishedOnce = false
                    handleAutoAction(autoPolicy.linkDropped())
                }
            ConnectionState.Connecting, ConnectionState.Handshaking -> Unit
        }
    }
```

- [ ] **Step 5: Feed the reason into the strip**

The `StatusStrip` call (currently lines 593-600):

```kotlin
            StatusStrip(
                armed = demo || connectionState is ConnectionState.Armed,
                info = if (persistError) {
                    "DB WRITE FAILING"
                } else {
                    describe(connectionState, demo = demo, scanning = scanning, retryLabel = autoRetryLabel)
                },
            )
```

becomes

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
            )
```

- [ ] **Step 6: Full build + all tests**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain build
```

Expected: **BUILD SUCCESSFUL** (assemble debug+release, every module's tests, lint).

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(app): surface connect failure reason on status strip"
```

- [ ] **Step 8: Install on the tablet**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:installDebug
```

Expected: `Installed on 1 device.`

---

### Task 4: On-tablet verification (user, physical MLM2PRO)

No code. With monitor OFF, let the auto-connect cycle give up (~40 s):

- [ ] During retries the strip reads `RETRYING (n/3)… NO MONITOR FOUND`
- [ ] After GiveUp it reads `BLE DISCONNECTED - NO MONITOR FOUND`
- [ ] Monitor ON mid-cycle: retry labels continue until link; `ARMED` shows (reason gone)
- [ ] Monitor ON after GiveUp + CONNECT tap: reaches `ARMED`, no stale reason
- [ ] Link mid-session, then monitor off: retry cycle shows the GATT-based phrase (e.g. `MONITOR NOT RESPONDING (GATT 8)` or `MONITOR CLOSED LINK (GATT 19)`, exact code varies)
- [ ] DISCONNECT tap: strip returns to `BLE DISCONNECTED` with no reason
- [ ] DEMO mode: label stays `DEMO MODE - FIRE TO SHOOT`, FIRE works
