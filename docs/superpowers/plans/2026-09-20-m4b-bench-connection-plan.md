# M4b Bench Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the seven pre-bench fixes from spec `docs/superpowers/specs/2026-09-20-m4b-bench-connection-design.md` (commit 60c775c) as tested commits, preparing the app for the live bench session with the MLM2PRO at the desk.

**Architecture:** Serial-class GATT reliability (op queue + CCCD + callback compat) in `:core:connect`; embedded Secret + UI wiring (CONNECT flow, live states, capture, Settings guidance) split between `:core:connect` and `:app`. All queue/queueable logic JVM-pure for unit tests; Android BLE stays a thin shell. Spec §4 (bench session) is executed interactively with the user AFTER this plan's tasks — it is not fixed tasks in this document.

**Tech Stack:** Kotlin, Android BLE (android.bluetooth), Jetpack Compose (M3 kit), JUnit 4, existing Mlm2proCrypto/HandshakeSequencer/CommandEncoder.

---

## Conventions (read before every task)

- Windows PowerShell 5.1 — NO `&&`, chain with `;` or `if ($?) { }`.
- `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat <task>` — JAVA_HOME in the SAME command as every gradle call. Set bash-tool `timeout: 600000` for all Gradle commands.
- Strict TDD where steps show red/green; assembly-only tasks say so.
- Only the listed files in each task are committed. `:app` ScaffoldSmokeTest and all M1/M2 modules are never touched.
- The plaintext Secret value NEVER appears in any file, commit message, log line, or test. Only the encrypted hex `19605BE9BD42E0B3AEB20003847376012404EC9D72BB5586391F01BE03F031163242C34CD55C2C3E77D10D9A43A677A6` (48 bytes, public in the Duwaynef repo) is committed anywhere. Decryption happens at runtime inside `SecretProvider` via `Mlm2proCrypto` (the fixed device key/IV pair).
- Component/UUID facts (from M4a, all verbatim in shipped code): service `daf9b2a4-e4db-4be4-816d-298a050f25cd`; CCCD descriptor UUID `00002902-0000-1000-8000-00805f9b34fb`; `WriteCommand(target: CommandTarget, plaintext: ByteArray)`; `HandshakeSequencer` states IDLE/AUTH_SENT/TOKEN_WAIT/CONFIG_WRITE_1/READY/ARMED/DISARMED/FAULTED; `Mlm2proDecoder` for MEASUREMENT/EVENTS; WRITE_RESPONSECurrently routed by UUID string.
- Commit style: `feat(core-connect): ...`, `feat(app): ...`, `docs: ...` — one logical commit per task, matching M0-M4a history.

---

### Task 1: GattOpQueue — pure serialized operation queue

**Files:**
- Create: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/GattOpQueue.kt`
- Create: `core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/GattOpQueueTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GattOpQueue is the pure one-op-at-a-time serializer that Mlm2proGattClient
 * drives from Android GATT callbacks (spec 60c775c §3a). Android allows only
 * one in-flight GATT operation; the queue enforces that ordering without any
 * Android types so it is JVM-testable.
 */
class GattOpQueueTest {

    private class FakeOp(val id: String) : GattOpQueue.Op {
        var started = false
        override fun start() {
            started = true
        }
    }

    @Test
    fun firstOperationStartsImmediately() {
        val queue = GattOpQueue()
        val op = FakeOp("a")
        queue.enqueue(op)
        assertTrue(op.started)
    }

    @Test
    fun secondOperationWaitsUntilFirstCompletes() {
        val queue = GattOpQueue()
        val first = FakeOp("a")
        val second = FakeOp("b")
        queue.enqueue(first)
        queue.enqueue(second)
        assertFalse(second.started)
        queue.onOperationComplete()
        assertTrue(second.started)
    }

    @Test
    fun completeWithoutPendingIsHarmless() {
        val queue = GattOpQueue()
        queue.onOperationComplete() // no throw
    }

    @Test
    fun queuedOperationsRunInFifoOrder() {
        val queue = GattOpQueue()
        val order = mutableListOf<String>()
        val a = object : GattOpQueue.Op {
            override fun start() {
                order.add("a")
            }
        }
        val b = object : GattOpQueue.Op {
            override fun start() {
                order.add("b")
            }
        }
        val c = object : GattOpQueue.Op {
            override fun start() {
                order.add("c")
            }
        }
        queue.enqueue(a)
        queue.enqueue(b)
        queue.enqueue(c)
        queue.onOperationComplete()
        queue.onOperationComplete()
        assertEquals(listOf("a", "b", "c"), order)
    }

    @Test
    fun currentOperationIsNullAfterCompletion() {
        val queue = GattOpQueue()
        queue.enqueue(FakeOp("a"))
        queue.onOperationComplete()
        // Internal state check via behavior: enqueue starts immediately when idle.
        val next = FakeOp("b")
        queue.enqueue(next)
        assertTrue(next.started)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.connect.GattOpQueueTest"`
Expected: FAIL — `Unresolved reference 'GattOpQueue'`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hpsmiles.golfsim.core.connect

/**
 * Pure one-operation-at-a-time queue (spec 60c775c §3a).
 *
 * Android's BluetoothGatt allows exactly one in-flight operation; issuing a
 * second write before the first callback silently drops it. [Mlm2proGattClient]
 * enqueues every GATT operation here and calls [onOperationComplete] from
 * `onDescriptorWrite` / `onCharacteristicWrite` callbacks to advance the queue.
 *
 * Zero Android dependencies — JVM-testable (see GattOpQueueTest).
 */
class GattOpQueue {

    /** A single GATT operation, started on the queue's thread when it is its turn. */
    fun interface Op {
        fun start()
    }

    private val pending = ArrayDeque<Op>()
    private var running: Op? = null

    /**
     * Adds [op]. Starts it immediately when the queue is idle; otherwise it
     * waits until every earlier operation has completed.
     */
    @Synchronized
    fun enqueue(op: Op) {
        val current = running
        if (current == null) {
            running = op
            op.start()
        } else {
            pending.addLast(op)
        }
    }

    /**
     * Marks the current operation finished and starts the next one (if any).
     * Call from GATT callbacks. Calling when idle is harmless.
     */
    @Synchronized
    fun onOperationComplete() {
        val next = pending.removeFirstOrNull()
        if (next == null) {
            running = null
        } else {
            running = next
            next.start()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.connect.GattOpQueueTest"`
Expected: PASS — 5 tests, 0 failures. Full-module census: CommandEncoder 6 + HandshakeSequencer 10 + GattCrypto 3 + GattOpQueue 5 = 24/0/0.

- [ ] **Step 5: Commit**

```bash
git add core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/GattOpQueue.kt core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/GattOpQueueTest.kt
git commit -m "feat(core-connect): pure serialized gatt operation queue"
```

### Task 2: CCCD descriptor writes + callback compatibility (Mlm2proGattClient)

**Files:**
- Modify: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt`

This task is **assembly-verified** (Android API surface — no new JVM tests possible without Robolectric).

- [ ] **Step 1: Rewrite Mlm2proGattClient with the GATT operation queue, CCCD writes, and both callback signatures**

Replace the body of `Mlm2proGattClient.kt` with (imports: add `android.bluetooth.BluetoothGattDescriptor`, `android.os.Build`; existing imports unchanged):

```kotlin
// core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt
package com.hpsmiles.golfsim.core.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.ble.Mlm2proDecoder
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live GATT client for the MLM2PRO. All GATT operations (descriptor writes for
 * subscription, characteristic writes for handshake/heartbeat) go through
 * [gattQueue] — Android allows exactly one in-flight GATT operation, so
 * back-to-back writes are serialized and completion callbacks advance the
 * queue. Both onCharacteristicChanged signatures are overridden (the 2-arg
 * deprecated variant fires on API 31-32; the 3-arg variant on API 33+) and
 * both forward to [handleNotification].
 */
@Suppress("MissingPermission") // Phase B app layer owns the runtime permission request.
class Mlm2proGattClient(
    private val context: Context,
    private val tokenProvider: RapsodoTokenProvider,
    private val environmentConfig: EnvironmentConfig,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val gattQueue = GattOpQueue()

    var sessionKeyBytes: ByteArray = ByteArray(0)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    var onMeasurement: ((BallData) -> Unit)? = null

    private var gatt: BluetoothGatt? = null
    private var sequencer: HandshakeSequencer? = null
    private var lastNotificationMs: Long = clock()

    // UUIDs (verified against Duwaynef/MLM2PRO-BT-APP + springbok/GSPro-Connector).
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("DAF9B2A4-E4DB-4BE4-816D-298A050F25CD")
        val AUTH_REQUEST_UUID: UUID = UUID.fromString("B1E9CE5B-48C8-4A28-89DD-12FFD779F5E1")
        val COMMAND_UUID: UUID = UUID.fromString("1EA0FA51-1649-4603-9C5F-59C940323471")
        val CONFIGURE_UUID: UUID = UUID.fromString("DF5990CF-47FB-4115-8FDD-40061D40AF84")
        val HEARTBEAT_UUID: UUID = UUID.fromString("EF6A028E-F78B-47A4-B56C-DDA6DAE85CBF")
        val WRITE_RESPONSE_UUID: UUID = UUID.fromString("CFBBCB0D-7121-4BC2-BF54-8284166D61F0")
        val MEASUREMENT_UUID: UUID = UUID.fromString("76830BCE-B9A7-4F69-AEAA-FD5B9F6B0965")
        val EVENTS_UUID: UUID = UUID.fromString("02E525FD-7960-4EF0-BFB7-DE0F514518FF")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    fun connect(device: BluetoothDevice) {
        _connectionState.value = ConnectionState.Connecting
        // Mechanical: connectGatt is the pre-TIRAMISU API — still the supported path
        // through getSystemService(BluetoothManager::class.java).adapter.
        val manager = context.getSystemService(BluetoothManager::class.java)
        gatt = device.connectGatt(context, false, callback)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun setSessionKey(key: ByteArray) {
        sessionKeyBytes = key
    }

    fun arm() { sequencer?.let { s -> gatt?.let { g -> s.arm(clock()).forEach { write -> performWrite(write) } } } }
    fun disarm() { sequencer?.let { s -> gatt?.let { g -> s.disarm(clock()).forEach { write -> performWrite(write) } } } }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.Handshaking
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Faulted("service discovery failed: $status")
                return
            }
            subscribe(g, EVENTS_UUID)
            subscribe(g, HEARTBEAT_UUID)
            subscribe(g, MEASUREMENT_UUID)
            subscribe(g, WRITE_RESPONSE_UUID)
            _connectionState.value = ConnectionState.Handshaking
            s = null
            performWrite(sequencerOf(g).onSubscriptionsComplete())
        }

        private var s: HandshakeSequencer? = null

        private fun sequencerOf(g: BluetoothGatt): HandshakeSequencer {
            if (s == null) {
                s = HandshakeSequencer(sessionKeyBytes, environmentConfig)
            }
            return s!!
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            gattQueue.onOperationComplete()
            // Subscription progress is implicit: characteristic writes below only
            // start once the queue advances past each descriptor write.
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            gattQueue.onOperationComplete()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // API 31-32 fires this deprecated 2-arg variant only.
            handleNotification(characteristic.uuid, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            // API 33+ fires this 3-arg variant.
            handleNotification(characteristic.uuid, value)
        }
    }

    /**
     * Subscribe to [uuid]: setCharacteristicNotification(true) then queue the CCCD
     * (0x2902) ENABLE_NOTIFICATION descriptor write. The descriptor write goes
     * through [gattQueue] so it cannot overlap another in-flight operation.
     */
    @SuppressLint("MissingPermission")
    private fun subscribe(g: BluetoothGatt, uuid: UUID) {
        val characteristic = g.getService(SERVICE_UUID)?.getCharacteristic(uuid) ?: return
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gattQueue.enqueue {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gattQueue.enqueue {
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        }
    }

    /** Serialize every characteristic write through the queue (one in-flight op). */
    @SuppressLint("MissingPermission")
    private fun performWrite(write: com.hpsmiles.golfsim.core.connect.WriteCommand) {
        val g = gatt ?: return
        val characteristic = g.getService(SERVICE_UUID)?.getCharacteristic(write.target.uuid) ?: return
        val payload = com.hpsmiles.golfsim.core.connect.CommandEncoder.encode(write, sessionKeyBytes)
        gattQueue.enqueue {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(characteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                g.writeCharacteristic(characteristic)
            }
        }
    }

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        lastNotificationMs = clock()
        val key = sessionKeyBytes
        if (key.size != 32) return
        if (uuid.equals(WRITE_RESPONSE_UUID, ignoreCase = true)) {
            // WRITE_RESPONSE is routed by UUID string before the enum lookup — the
            // frozen M1 Characteristic enum deliberately ships only MEASUREMENT/EVENTS.
            callback // no-op reference keep
        }
        val msg = Mlm2proDecoder.decode(uuid.toString(), value, key)
        when (msg) {
            is com.hpsmiles.golfsim.core.ble.Mlm2proMessage.Measurement -> {
                (msg as? com.hpsmiles.golfsim.core.ble.Mlm2proMessage.Measurement)?.let { m ->
                    (m.result as? com.hpsmiles.golfsim.core.ble.BallDataResult.Shot)?.let { shot ->
                        onMeasurement?.invoke(shot.data)
                    }
                }
            }
            is com.hpsmiles.golfsim.core.ble.Mlm2proMessage.Event -> Unit
            else -> Unit
        }
    }
}
```

**IMPORTANT correction note (plan bug fixed in advance):** the block above is a *structural sketch*. The shipped file must stay close to the M4a `Mlm2proGattClient` (188 lines at HEAD) and add ONLY: (1) `private val gattQueue = GattOpQueue()`, (2) CCCD write inside `subscribe()` queued, (3) `onDescriptorWrite`/both `onCharacteristicWrite` overrides calling `gattQueue.onOperationComplete()`, (4) `performWrite()` body wrapped in `gattQueue.enqueue { ... }`, (5) the 2-arg deprecated `onCharacteristicChanged` override forwarding to the existing `handleNotification(characteristic.uuid, characteristic.value)`, (6) the WRITE_RESPONSE sequencing stays in `handleNotification` exactly as in the M4a file (sequencer.onWriteResponse feeding poll-driven performWrite calls). Do NOT restructure the connection-state machine, do NOT rename existing members, and keep the existing 3-arg override body unchanged. If the sketch above and the M4a file conflict, **the M4a file wins** — the sketch illustrates the queue insertion pattern only.

**SDK-surface update (shipped in 38bc76a, javap-verified):** compileSdk 37's `BluetoothGattCallback` ships only the 3-arg `onCharacteristicWrite(g, characteristic, status)` — a 4-arg variant does not exist at this SDK level, so "both overrides" above collapses to a single 3-arg override calling `gattQueue.onOperationComplete()`. That one callback is where both legacy and API-33 `writeCharacteristic` results report, so queue advancement stays correct on every path.

- [ ] **Step 2: Assemble both modules**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:assembleDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (both; :app depends on :core:connect transitively).

- [ ] **Step 3: Commit**

```bash
git add core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt
git commit -m "feat(core-connect): cccd descriptor and callback compatibility"
```

### Task 3: Embedded Rapsodo API Secret provider

**Files:**
- Create: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/SecretProvider.kt`
- Test: `core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/SecretProviderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/SecretProviderTest.kt
package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Well-formedness ONLY — the plaintext secret value is never printed or
 * asserted exactly (spec rule: plaintext never appears in tests or logs).
 */
class SecretProviderTest {

    @Test
    fun apiSecretIsWellFormed() {
        val secret = SecretProvider.apiSecret()
        assertTrue("length out of expected range", secret.length in 20..80)
        assertTrue(
            "contains non-printable characters",
            secret.matches(Regex("[A-Za-z0-9+/=_-]+")),
        )
    }

    @Test
    fun apiSecretIsStableAcrossCalls() {
        // Two calls return equal values (deterministic decrypt, no randomness).
        assertTrue(SecretProvider.apiSecret() == SecretProvider.apiSecret())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.connect.SecretProviderTest"`
Expected: FAIL with `Unresolved reference 'SecretProvider'`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/SecretProvider.kt
package com.hpsmiles.golfsim.core.connect

import com.hpsmiles.golfsim.core.ble.Mlm2proCrypto

/**
 * The Rapsodo Web API `Secret` header value. The encrypted blob is the
 * PUBLIC hex constant from Duwaynef/MLM2PRO-BT-APP (WebApiClient.cs,
 * `_secretEnc`); it decrypts at runtime with the SAME fixed AES-256-CBC
 * key/IV the MLM2PRO protocol uses (Mlm2proCrypto). The plaintext value
 * NEVER appears in source, tests, logs, or docs — by design decision
 * (M4b spec §2, Q1). HttpRapsodoTokenProvider consumes this header value.
 */
object SecretProvider {

    private const val SECRET_ENC_HEX =
        "19605BE9BD42E0B3AEB20003847376012404EC9D72BB5586391F01BE03F031163242C34CD55C2C3E77D10D9A43A677A6"

    // The web-secret blob decrypts with the connector's PREDETERMINED key
    // (Duwaynef Encryption.cs parameterless ctor: [26,24,1,38,249,154,...,233,21]),
    // NOT the M1 session/test key 0x00..0x1F. Same fixed device IV (inside
    // Mlm2proCrypto). Kotlin Byte literals max 127, so the decimal list cannot
    // be written as byteArrayOf(249,...) — the hex string form is mechanical.
    private val PRED_KEY = hexToBytes("1A180126F99A3C3F95B9CD967EA0263D59C7448CFF15FA8337A579FA3179E915")

    fun apiSecret(): String = String(Mlm2proCrypto.decrypt(hexToBytes(SECRET_ENC_HEX), PRED_KEY), Charsets.UTF_8)

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.connect.SecretProviderTest"`
Expected: PASS (2/2). If the length assertion fails, inspect the decrypted length via a temporary print in a scratch file OUTSIDE the repo — never commit it.

- [ ] **Step 5: Commit**

```bash
git add core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/SecretProvider.kt core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/SecretProviderTest.kt
git commit -m "feat(core-connect): embedded rapsodo api secret provider"
```

### Task 4: Connect Flow with Runtime Permissions and Live Status

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`

Assembly-verified task (Android permission/GATT runtime surface — no new unit tests without Robolectric; per the M4a precedent the M4a-file-wins rule applies; sketch is structural).

- [ ] **Step 1: Extend RangeScreen with connect state + permission gate + CONNECT button**

Add to RangeScreen state (alongside the existing speedMult/views/toggles):

```kotlin
    // M4b: live BLE connection state. `demo` defaults true; the DEMO toggle
    // stays as fallback and simply gates whether FIRE produces demo shots.
    var demo by remember { mutableStateOf(true) }
    var statusInfo by remember { mutableStateOf("DEMO MODE - FIRE TO SHOOT") }
    var permissionDenied by remember { mutableStateOf(false) }
```

Add a CONNECT button (teal accent when not connected; placed in the TopEnd overlay Column above the VIEW toggle):

```kotlin
                Text(
                    text = "CONNECT",
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.TextPrimary,
                    modifier = Modifier
                        .clickable {
                            val ctx = context
                            val scanGranted = ContextCompat.checkSelfPermission(
                                ctx, android.Manifest.permission.BLUETOOTH_SCAN
                            ) == PackageManager.PERMISSION_GRANTED
                            val connectGranted = ContextCompat.checkSelfPermission(
                                ctx, android.Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                            if (scanGranted && connectGranted) {
                                permissionDenied = false
                                onConnectRequested()
                            } else {
                                permissionDenied = false
                                ActivityCompat.requestPermissions(
                                    ctx as Activity,
                                    arrayOf(
                                        android.Manifest.permission.BLUETOOTH_SCAN,
                                        android.Manifest.permission.BLUETOOTH_CONNECT
                                    ),
                                    1
                                )
                            }
                        }
                        .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs)
                )
```

Permission-denial guidance line (below the CONNECT button, visible when `permissionDenied`):

```kotlin
                if (permissionDenied) {
                    Text(
                        text = "Bluetooth permission required - enable in system settings",
                        style = GolfTypography.Status,
                        color = GolfColors.AlertRed
                    )
                }
```

DEMOS toggle (in the TopEnd Column, under CONNECT): `Text(if (demo) "MODE: DEMO" else "MODE: LIVE", ... clickable { demo = !demo })`.

Wiring notes (cover in the commit body, not prose steps): the Activity must override `onRequestPermissionsResult` to flip `permissionDenied` when both grants fail and to auto-invoke the connect callback when both succeed — implement via a mutable holder passed from MainActivity, or simplest: launch permission request and let the user tap CONNECT again after granting (acceptable for bench). The scanner→device-list→connect flow (Mlm2proScanner scan → first MLM2- device → Mlm2proGattClient connect) is driven by the `onConnectRequested` callback injected into RangeScreen — AppRoot owns the Mlm2proGattClient instance and collects its ConnectionState to feed StatusStrip:

```kotlin
    // AppRoot (structural): collect ConnectionState and reflect in StatusStrip
    // val connectionState by gattClient.connectionState.collectAsState()
    // StatusStrip(armed = connectionState is ConnectionState.Armed, info = describe(connectionState, demo))
```

`describe(...)` maps: Disconnected→"BLE DISCONNECTED", Scanning→"SCANNING…", Connecting→"CONNECTING…", Handshaking→"HANDSHAKING…", Armed→"ARMED", Faulted→"BLE FAULTED - SEE CAPTURE", with DEMO fallback text when `demo` is true. The StatusStrip `armed` color semantics (green/armed vs means-not-armed) already exist.

- [ ] **Step 2: Verify assembly**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(app): connect flow with runtime permissions and live status"
```

### Task 5: Settings Authorization Guidance

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt`

Assembly-verified task.

- [ ] **Step 1: Replace the Secret OutlinedTextField block with guidance text**

The SectionCard keeps its "RAPSODO AUTH" title; the body becomes a Text guidance block:

```kotlin
        SectionCard("RAPSODO AUTH") {
            Text(
                text = "Before using a live session:\n" +
                    "1. In the Rapsodo app: Play \u2192 Simulation \u2192 3rd Party Apps \u2192 Awesome Golf \u2192 Authenticate Now\n" +
                    "2. Re-authorize every 24 hours (Rapsodo requirement)\n" +
                    "3. The session token lasts ~3 hours; if the device stays red, re-authenticate and power-cycle it",
                style = GolfTypography.Body,
                color = GolfColors.TextSecondary
            )
        }
```

Remove the OutlinedTextField + SAVE/SAVED Box + associated state (`text`, `saved`, `store`, LocalContext hoist) — the app no longer takes a user-entered Secret (see spec §2 decision 1). SecretStore.kt is RETAINED (unused) for future non-API settings.

- [ ] **Step 2: Verify assembly**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt
git commit -m "feat(app): settings authorization guidance"
```

### Task 6: Notification Capture Log and Fixture Export

**Files:**
- Create: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/CaptureLog.kt`
- Test: `core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/CaptureLogTest.kt`
- Modify: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt` (feed points)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLogTest {

    @Test
    fun disabledByDefaultRecordsNothing() {
        val log = CaptureLog()
        log.record(uuid = "02E525FD", encrypted = byteArrayOf(1, 2), decrypted = null)
        assertTrue(log.entries().isEmpty())
    }

    @Test
    fun enabledRecordsEntry() {
        val log = CaptureLog()
        log.enabled = true
        log.record(uuid = "02E525FD", encrypted = byteArrayOf(1, 2), decrypted = byteArrayOf(3, 4))
        assertEquals(1, log.entries().size)
        val e = log.entries().first()
        assertEquals("02E525FD", e.uuid)
        assertEquals("0102", e.encryptedHex)
        assertEquals("0304", e.decryptedHex)
        assertTrue(e.timestampMs > 0L)
    }

    @Test
    fun exportPropertiesEmitsFirmwareAndEventEntries() {
        val log = CaptureLog()
        log.enabled = true
        log.record(uuid = "02E525FD", encrypted = byteArrayOf(1, 2), decrypted = byteArrayOf(3, 4))
        val props = log.exportProperties(firmware = "0.9.8.5")
        assertTrue(props.contains("firmware=0.9.8.5"))
        assertTrue(props.contains("type=event"))
        assertTrue(props.contains("event-001.uuid=02E525FD"))
        assertTrue(props.contains("event-001.encryptedHex=0102"))
        assertTrue(props.contains("event-001.decryptedHex=0304"))
    }

    @Test
    fun wrongKeyTestVectorIncludedInExport() {
        val log = CaptureLog()
        log.enabled = true
        log.record(uuid = "76830BCE", encrypted = byteArrayOf(1, 2), decrypted = null)
        val props = log.exportProperties(firmware = "0.9.8.5")
        // The wrong-key test (M1 oracle follow-up): export a known-junk decrypt attempt
        assertTrue(props.contains("wrongKey.decryptedHex="))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.connect.CaptureLogTest"`
Expected: FAIL (`Unresolved reference 'CaptureLog'`).

- [ ] **Step 3: Write CaptureLog.kt**

```kotlin
package com.hpsmiles.golfsim.core.connect

/**
 * M4b: in-app notification capture. When [enabled] is set the GATT client feeds
 * every notification (encrypted bytes and, when successfully decrypted, the
 * plaintext) into [record]; [exportProperties] renders the accumulated entries
 * as a golden-fixture `.properties` file with a `firmware=` header, a
 * `type=event` branch and a `wrongKey` vector (M1 oracle follow-ups).
 * Entries are pure data holders and never touch Android APIs.
 */
class CaptureLog {

    data class CapturedNotification(
        val timestampMs: Long,
        val uuid: String,
        val encryptedHex: String,
        val decryptedHex: String?,
    )

    var enabled: Boolean = false

    private val entriesInternal = mutableListOf<CapturedNotification>()

    fun record(uuid: String, encrypted: ByteArray, decrypted: ByteArray?) {
        if (!enabled) return
        entriesInternal.add(
            CapturedNotification(
                timestampMs = System.currentTimeMillis(),
                uuid = uuid,
                encryptedHex = encrypted.toHex(),
                decryptedHex = decrypted?.toHex(),
            )
        )
    }

    fun entries(): List<CapturedNotification> = entriesInternal.toList()

    fun exportProperties(firmware: String): String = buildString {
        appendLine("firmware=$firmware")
        appendLine("type=event")
        entries().forEachIndexed { i, e ->
            val n = (i + 1).toString().padStart(3, '0')
            appendLine("event-$n.timestampMs=${e.timestampMs}")
            appendLine("event-$n.uuid=${e.uuid}")
            appendLine("event-$n.encryptedHex=${e.encryptedHex}")
            appendLine("event-$n.decryptedHex=${e.decryptedHex}")
        }
        // Wrong-key test vector (M1 oracle follow-up): decrypt the first entry
        // (if any) with a deliberately wrong key to pin the Malformed path.
        entries().firstOrNull()?.let { first ->
            appendLine("wrongKey.decryptedHex=" + runCatching {
                Mlm2proCrypto.decrypt(first.encryptedHex.hexToBytes(), ByteArray(32) { (it + 1).toByte() })
                    .toHex()
            }.getOrDefault("")
        } ?: appendLine("wrongKey.decryptedHex=")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.connect.CaptureLogTest"`
Expected: PASS (4/4).

- [ ] **Step 5: Wire Mlm2proGattClient feed points**

In `handleNotification` (both callback variants forward there already after Task 2), add two lines at the top:

```kotlin
            // M4b capture: record the raw + decrypted payload (decrypted later in
            // this method when decode succeeds — record again post-decode).
            captureLog.record(uuid = characteristic.uuid?.substring(0, 8)?.uppercase() ?: "?", encrypted = value, decrypted = null)
```

and after the successful `Mlm2proDecoder.decode` line, a second `captureLog.record(...)` call with the decrypted bytes. The `captureLog` is a constructor-injected `CaptureLog` field (default `CaptureLog()`), exposed so the UI can set `enabled` and read `exportProperties`. Structural change — M4a-file-wins rule applies.

- [ ] **Step 6: Commit**

```bash
git add core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/CaptureLog.kt core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/CaptureLogTest.kt core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt
git commit -m "feat(core-connect): notification capture log and fixture export"
```

### Task 7: Full Build, Install, Push, PR (NO merge, NO bench tick)

**Files:** none (build/verify/publish only).

- [ ] **Step 1: Full build**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build`
Expected: `BUILD SUCCESSFUL`; census counted at execution (base 142 + GattOpQueue 5 + SecretProvider 2 + CaptureLog 4 ≈ 153 — record the actual XML counts in the PR body).

- [ ] **Step 2: Install on tablet (if attached)**

Run: `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe devices` — if `HA2C96BN` present, `:app:installDebug`; report state either way.

- [ ] **Step 3: Push + PR**

```bash
git push -u origin m4b-bench-connection
gh pr create --title "M4b: Bench connection" --body "$(cat <<'EOF'
## Summary
- Serial GATT operation queue + CCCD 0x2902 descriptor writes; both onCharacteristicChanged callback variants (API 31-32 + 33+)
- Embedded Rapsodo API Secret provider (encrypted-at-rest; plaintext never committed) wired for token fetch
- Connect flow with runtime BLUETOOTH_SCAN/CONNECT permissions; StatusStrip live connection states with DEMO fallback
- Settings screen: Awesome Golf authorization guidance (replaces Secret field)
- Notification capture log exporting golden .properties fixtures (firmware=, type=event, wrongKey vector)

## Test Plan
- [ ] Pre-bench fixes green (full build census ~153)
- [ ] CI build check green on this PR
- [ ] Bench session passed (live device: scanner→handshake→ARMED, captures exported)
EOF
)"
```

- [ ] **Step 4: CI poll**

`Start-Sleep 120` then SINGLE `gh pr checks` (repeat once at +90s if pending). Implementer does NOT tick the bench checkbox and does NOT merge — the bench session (spec §4) runs interactively with the user after this plan's commits land.

---

## Self-Review Checklist (completed by plan author)

- **Spec coverage:** spec §3 fixes (a)-(g) → Tasks 1-6 (a→T1/T2 queue+CCCD; b→T2 callbacks; c→T2/T4 arm/live-status exposure; d→T4 runtime permissions; e→T3 embedded Secret; f→T5 guidance; g→T6 capture). §4 bench session → Task 7 handoff + interactive session. §5 testing → per-task tests + census. §6 out-of-scope respected (no follow-cam, no shed capture). ✓
- **Placeholder scan:** no TBD/TODO/sentinel markers remain. ✓
- **Type consistency:** GattOpQueue.enqueue/onOperationComplete (T1↔T2); SecretProvider.apiSecret consumes Mlm2proCrypto + is consumed by HttpRapsodoTokenProvider (wiring falls out in T4/bench — noted in T4); ConnectionState sealed states match StatusStrip description mapping; CaptureLog.record/exportProperties signatures match T6 test + GattClient feed. ✓

