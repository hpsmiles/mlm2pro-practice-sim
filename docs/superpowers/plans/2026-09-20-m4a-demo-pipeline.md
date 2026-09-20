# M4a Demo Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase A demo pipeline: fire synthetic demo shots on the tablet, solve them through the M2 ball-flight engine, and render live range UI (POV + top-down) with instant metrics — plus the full BLE client code (`:core:connect`), JVM-tested but not device-validated.

**Architecture:** `ShotSource` (demo now, BLE in Phase B/C) emits M1 `BallData`; `:app` maps it field-for-field to M2 `LaunchConditions` and solves; the range UI renders via a pure-JVM `PovProjector` plus a top-down canvas, with tracer animation that never gates instant metrics. The new `:core:connect` module holds the BLE handshake as a pure state machine (`HandshakeSequencer`) with an injected clock, thin Android wrappers, and `CommandEncoder` reusing M1 crypto.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas), M1 decoder, M2 physics, M3 design system. No new external dependencies.

---

## Conventions

- PowerShell 5.1. Never `&&`. Chain with `;` or `if ($?) { ... }`.
- Gradle commands must set JAVA_HOME in the SAME command:
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat <target>`
- bash-tool timeout 600000 for ALL Gradle commands.
- TDD where the plan shows red/green steps; assembly-only tasks (composables) say so.
- Only commit the files each task lists. M0/M1/M2/M3 code is not modified (one exception: `:app` files replaced in Tasks 5-6).
- `:app`'s `ScaffoldSmokeTest.kt` is NEVER touched.
- Known deviation (design-approved): the tracer arc is stylized (quadratic through solve endpoints) because `ShotResult` carries no trajectory samples; physical arcs are a Phase C design decision. Metrics themselves are real engine outputs.

**Crypto test key/IV (fixed, from M1 tests):** key = bytes 0x00..0x1F (32 bytes); IV = fixed device IV `6D2E5213213204456F2C794810656D42`. AES-256-CBC + PKCS7 via `Mlm2proCrypto`.

**Pinned ciphertext vectors (aes-256-cbc, key 0x00-0x1F, device IV):**
- ARM plaintext `010D0001000000` → `4D40E953E85F1672CAC463A695E8C9D8`
- DISARM plaintext `010D0000000000` → `D5BC053B9452BA6D4406F8659EDFC066`
- CONFIG sample plaintext `010200007DC8DC05A63C5A440000` (ballType=0x02, indoor=0x00, pressureRaw so LE bytes are `7DC8`, tempCentiC=1500 → `DC05`, token 0x445A3CA6 → `A63C5A44`, pad `0000`) → `4A4552B700BFBFC3D19C87B2B9379766`

---

### Task 1: ShotSource interface + seeded DemoShotSource

**Files:**
- Create: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/ShotSource.kt`
- Create: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/DemoShotSource.kt`
- Test: `core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/DemoShotSourceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/DemoShotSourceTest.kt
package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoShotSourceTest {

    private fun sourceA() = DemoShotSource(seed = 42L)
    private fun sourceB() = DemoShotSource(seed = 42L)
    private fun sourceC() = DemoShotSource(seed = 7L)

    @Test
    fun sameSeedProducesIdenticalSequence() {
        repeat(20) {
            assertEquals(sourceA().nextShot(), sourceB().nextShot())
        }
        // Fresh instances too: construction order must not matter.
        assertEquals(DemoShotSource(42L).nextShot(), DemoShotSource(42L).nextShot())
    }

    @Test
    fun differentSeedProducesDifferentShot() {
        var different = false
        repeat(10) {
            if (sourceA().nextShot() != sourceC().nextShot()) different = true
        }
        assertTrue(different)
    }

    @Test
    fun valuesStayWithinClubBoundsOverManyShots() {
        val source = DemoShotSource(seed = 1L)
        repeat(400) {
            val shot = source.nextShot()
            assertTrue("ball speed ${shot.ballSpeed}", shot.ballSpeed in 40.0..85.0)
            assertTrue("vla ${shot.launchAngle}", shot.launchAngle in 9.0..28.0)
            assertTrue("spin ${shot.totalSpin}", shot.totalSpin.toDouble() in 2000.0..10500.0)
            assertTrue("hla ${shot.launchDirection}", Math.abs(shot.launchDirection) <= 2.5)
            assertTrue("axis ${shot.spinAxis}", Math.abs(shot.spinAxis) <= 7.0)
            assertTrue("smash", shot.clubHeadSpeed > 0.0 && shot.clubHeadSpeed < shot.ballSpeed)
            assertEquals(0, shot.unknown1)
            assertEquals(0, shot.unknown2)
        }
    }

    @Test
    fun clubsCycleInOrder() {
        val source = DemoShotSource(seed = 99L)
        val firstFour = List(4) { source.nextShot().ballSpeed }
        // Centers: Driver 75, 5-iron 60, 7-iron 55, PW 46 — each within ±9%.
        assertTrue(firstFour[0] in 68.3..81.8) // Driver ±9%
        assertTrue(firstFour[1] in 54.6..65.4)  // 5-iron ±9%
        assertTrue(firstFour[2] in 50.1..60.0)  // 7-iron ±9%
        assertTrue(firstFour[3] in 41.9..50.1) // PW ±9%
        val fifth = source.nextShot().ballSpeed
        assertTrue(fifth in 68.3..81.8)        // cycles back to Driver
    }

    @Test
    fun isAShotSource() {
        val source: ShotSource = DemoShotSource()
        assertTrue(source.nextShot().ballSpeed > 0.0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.DemoShotSourceTest"`
Expected: FAIL — `Unresolved reference 'DemoShotSource'` (and `'ShotSource'`).

- [ ] **Step 3: Write the implementation**

```kotlin
// core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/ShotSource.kt
package com.hpsmiles.golfsim.core.ble

/**
 * A source of decoded ball measurements. The range UI consumes this
 * interface only — demo and (later) live BLE sources are interchangeable.
 */
fun interface ShotSource {
    fun nextShot(): BallData
}
```

```kotlin
// core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/DemoShotSource.kt
package com.hpsmiles.golfsim.core.ble

import kotlin.random.Random

/**
 * Deterministic demo source: cycles Driver / 5-iron / 7-iron / PW with
 * ±9% jitter on speed/spin/launch and bounded directional noise. Same
 * seed always yields the same sequence (JVM unit-testable).
 *
 * Phase A placeholder until the live BLE source lands in Phase B/C.
 */
class DemoShotSource(
    seed: Long = DEFAULT_SEED,
) : ShotSource {

    private val random = Random(seed)
    private var nextClub = 0

    override fun nextShot(): BallData {
        val club = CLUBS[nextClub]
        nextClub = (nextClub + 1) % CLUBS.size

        val ballSpeed = club.ballSpeedMps * jitter()
        val spin = club.spinRpm * jitter()
        val vla = club.vlaDeg * jitter()
        val hla = random.nextDouble() * 5.0 - 2.5          // ±2.5°
        val axis = random.nextDouble() * 14.0 - 7.0        // ±7.0°
        // BallData's shipped constructor parameter names (M1 API) are
        // clubHeadSpeed/ballSpeed/launchDirection/launchAngle/spinAxis/totalSpin —
        // mapped 1:1 from this plan's original clubHeadSpeedMps/ballSpeedMps/
        // hlaDeg/vlaDeg/spinAxisDeg/totalSpinRpm wording (amended to the real API).
        return BallData(
            clubHeadSpeed = ballSpeed / SMASH_FACTOR,
            ballSpeed = ballSpeed,
            launchDirection = hla,
            launchAngle = vla,
            spinAxis = axis,
            totalSpin = spin.toInt(),
            unknown1 = 0,
            unknown2 = 0,
        )
    }

    /** Multiplicative jitter in [0.91, 1.09]. */
    private fun jitter(): Double = 1.0 + (random.nextDouble() * JITTER_SPAN - JITTER)

    private data class DemoClub(
        val ballSpeedMps: Double,
        val spinRpm: Double,
        val vlaDeg: Double,
    )

    companion object {
        const val DEFAULT_SEED = 42L
        private const val JITTER = 0.09
        private const val JITTER_SPAN = 2 * JITTER
        private const val SMASH_FACTOR = 1.49
        private val CLUBS = listOf(
            DemoClub(ballSpeedMps = 75.0, spinRpm = 2500.0, vlaDeg = 12.0), // Driver
            DemoClub(ballSpeedMps = 60.0, spinRpm = 5300.0, vlaDeg = 15.0), // 5-iron
            DemoClub(ballSpeedMps = 55.0, spinRpm = 7100.0, vlaDeg = 16.5), // 7-iron
            DemoClub(ballSpeedMps = 46.0, spinRpm = 9200.0, vlaDeg = 24.5), // PW
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.DemoShotSourceTest"`
Expected: PASS — DemoShotSourceTest `tests="5" failures="0" errors="0"`; full `:core:ble:test` still 36 green (31 prior + 5 new).

- [ ] **Step 5: Commit**

```bash
git add core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/ShotSource.kt core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/DemoShotSource.kt core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/DemoShotSourceTest.kt
git commit -m "feat(core-ble): shot source interface with seeded demo source"
```

### Task 2: :core:connect module — CommandEncoder + HandshakeSequencer (TDD)

**Files:**
- Modify: `settings.gradle.kts` (append `include(":core:connect")` as the last include)
- Create: `core/connect/build.gradle.kts`
- Create: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/CommandEncoder.kt`
- Create: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/HandshakeSequencer.kt`
- Test: `core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/CommandEncoderTest.kt`
- Test: `core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/HandshakeSequencerTest.kt`

- [ ] **Step 1: Register the module**

`settings.gradle.kts` — append as the final line:

```kotlin
include(":core:connect")
```

`core/connect/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.golf.android.library)
}

android {
    namespace = "com.hpsmiles.golfsim.core.connect"
}

dependencies {
    implementation(project(":core:ble"))
    // Env fix (disclosed deviation): the golf-android-library convention
    // applies the Compose compiler plugin to every consumer; :core:connect is
    // non-Compose, so the runtime must still reach the compiler. This mirrors
    // :core:designsystem's proven implementation-scoped BOM+ui pattern.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    testImplementation(libs.junit)
}
```

- [ ] **Step 2: Write the failing tests**

`core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/CommandEncoderTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CommandEncoderTest {

    private val key = ByteArray(32) { it.toByte() }
    private val deviceKey = "6D2E5213213204456F2C794810656D42"

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { String.format(Locale.ROOT, "%02X", it) }

    @Test
    fun authRequestCarriesHeaderAndPlainKey() {
        val cmd = CommandEncoder.authRequest(key)
        assertEquals(CommandTarget.AUTH_REQUEST, cmd.target)
        // Header: version 0x01 + three zero bytes + encryption-type 0x01, then the raw key.
        val expected = byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x01) + key
        assertArrayEquals(expected, cmd.plaintext)
    }

    @Test
    fun armPinsCiphertext() {
        val cmd = CommandEncoder.arm(key)
        assertEquals(CommandTarget.COMMAND, cmd.target)
        assertEquals("4D40E953E85F1672CAC463A695E8C9D8", hex(cmd.plaintext))
    }

    @Test
    fun disarmPinsCiphertext() {
        val cmd = CommandEncoder.disarm(key)
        assertEquals(CommandTarget.COMMAND, cmd.target)
        assertEquals("D5BC053B9452BA6D4406F8659EDFC066", hex(cmd.plaintext))
    }

    @Test
    fun heartbeatEncryptsSingleByte() {
        val cmd = CommandEncoder.heartbeat(key)
        assertEquals(CommandTarget.HEARTBEAT, cmd.target)
        // Decrypt straight back: plaintext must be exactly [0x01].
        val decrypted = com.hpsmiles.golfsim.core.ble.Mlm2proCrypto.decrypt(cmd.plaintext, key)
        assertArrayEquals(byteArrayOf(0x01), decrypted)
    }

    @Test
    fun configPinsCiphertextAndLayout() {
        val cfg = EnvironmentConfig(
            ballType = 0x02,
            indoor = false,
            pressureRaw = 0x7DC8,
            tempCentiC = 1500,
        )
        // Deviation (mechanical): 0xA63C5A44 > Int.MAX_VALUE, so the hex
        // literal is a Long — same .toInt() narrowing the plan itself uses
        // for onToken; bit pattern and pinned ciphertext unchanged.
        val cmd = CommandEncoder.config(cfg, token = 0xA63C5A44.toInt(), key = key)
        assertEquals(CommandTarget.CONFIGURE, cmd.target)
        assertEquals("4A4552B700BFBFC3D19C87B2B9379766", hex(cmd.plaintext))
        // Decrypt back and verify the 14-byte layout field by field.
        val p = com.hpsmiles.golfsim.core.ble.Mlm2proCrypto.decrypt(cmd.plaintext, key)
        assertEquals(14, p.size)
        assertEquals(0x01, p[0].toInt() and 0xFF)
        assertEquals(0x02, p[1].toInt() and 0xFF)
        assertEquals(0x00, p[2].toInt() and 0xFF)
        assertEquals(0x00, p[3].toInt() and 0xFF)
        // Deviation (matches the pin-verified big-endian layout; the plan's
        // LE reads contradicted its own verified ciphertext pin):
        assertEquals(0x7DC8, ((p[4].toInt() and 0xFF) shl 8) or (p[5].toInt() and 0xFF))
        // temp is LE16 in the pin-verified layout (plan's LE read was right here):
        assertEquals(1500, (p[6].toInt() and 0xFF) or ((p[7].toInt() and 0xFF) shl 8))
        assertEquals(0xA63C5A44.toInt(), ((p[8].toInt() and 0xFF) shl 24) or
            ((p[9].toInt() and 0xFF) shl 16) or ((p[10].toInt() and 0xFF) shl 8) or
            (p[11].toInt() and 0xFF))
        assertEquals(0, p[12].toInt())
        assertEquals(0, p[13].toInt())
    }

    @Test
    fun authRequestRejectsWrongKeyLength() {
        try {
            CommandEncoder.authRequest(ByteArray(16))
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
```

`core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/HandshakeSequencerTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HandshakeSequencerTest {

    private val key = ByteArray(32) { it.toByte() }
    private val cfg = EnvironmentConfig()

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { String.format(Locale.ROOT, "%02X", it) }

    private fun authedSequencer(): HandshakeSequencer {
        val s = HandshakeSequencer(key, cfg)
        s.onSubscriptionsComplete(nowMs = 0)
        // Deviation (mechanical, test-fixture only): the plan's resp array had
        // bytes [1] and [2] transposed — as written it parses to 0x00A63C5A,
        // contradicting the plan's own pinned assert 0x00A63C44. Corrected to
        // (02, 5A, 44, 3C, A6, 00) so bytes [2..5] LE = 0x00A63C44.
        val resp = byteArrayOf(0x02, 0x5A, 0x44, 0x3C, 0xA6.toByte(), 0x00)
        s.onWriteResponse(resp, nowMs = 100)
        return s
    }

    @Test
    fun subscriptionsCompleteSendsAuthAndMovesToAuthSent() {
        val s = HandshakeSequencer(key, cfg)
        val write = s.onSubscriptionsComplete(nowMs = 0)
        assertEquals(HandshakeState.AUTH_SENT, s.state)
        assertEquals(CommandTarget.AUTH_REQUEST, write.target)
        assertEquals(38, write.plaintext.size) // 6-byte header + 32-byte key
    }

    @Test
    fun writeResponseAcceptParsesUserId() {
        val s = authedSequencer()
        assertEquals(HandshakeState.TOKEN_WAIT, s.state)
        assertEquals(0x00A63C44, s.userId)
    }

    @Test
    fun writeResponseRejectFaults() {
        val s = HandshakeSequencer(key, cfg)
        s.onSubscriptionsComplete(nowMs = 0)
        s.onWriteResponse(byteArrayOf(0x03, 0, 0, 0, 0, 0), nowMs = 100)
        assertEquals(HandshakeState.FAULTED, s.state)
    }

    @Test
    fun tokenArrivalSendsFirstConfig() {
        val s = authedSequencer()
        val w = s.onToken(0xA63C5A44.toInt(), nowMs = 200)
        assertNotNull(w)
        assertEquals(CommandTarget.CONFIGURE, w!!.target)
        // Wire pin: the CONFIG-sample ciphertext from the header conventions.
        assertEquals("4A4552B700BFBFC3D19C87B2B9379766", hex(w.plaintext))
        assertEquals(HandshakeState.CONFIG_WRITE_1, s.state)
    }

    @Test
    fun tokenFailureFaults() {
        val s = authedSequencer()
        s.onTokenFailed(nowMs = 500)
        assertEquals(HandshakeState.FAULTED, s.state)
    }

    @Test
    fun secondConfigWaits200Ms() {
        val s = authedSequencer()
        s.onToken(1, nowMs = 200)
        assertTrue(s.poll(nowMs = 399).isEmpty())     // 199 ms after config #1
        val at400 = s.poll(nowMs = 400)               // exactly +200 ms
        assertEquals(1, at400.size)
        assertEquals(CommandTarget.CONFIGURE, at400[0].target)
        assertEquals(HandshakeState.READY, s.state)
    }

    @Test
    fun heartbeatsEvery2sOnceReady() {
        val s = authedSequencer()
        s.onToken(1, nowMs = 200)
        s.poll(nowMs = 400) // config #2 -> READY (heartbeat clock starts here)
        assertTrue(s.poll(nowMs = 2399).isEmpty())
        val beat = s.poll(nowMs = 2400)
        assertEquals(1, beat.size)
        assertEquals(CommandTarget.HEARTBEAT, beat[0].target)
    }

    @Test
    fun armDisarmRoundTripFromReady() {
        val s = authedSequencer()
        s.onToken(1, nowMs = 200)
        s.poll(nowMs = 400) // READY
        val arm = s.arm(nowMs = 1000)!!
        assertEquals(HandshakeState.ARMED, s.state)
        assertEquals("4D40E953E85F1672CAC463A695E8C9D8", hex(arm.plaintext))
        val disarm = s.disarm(nowMs = 1200)!!
        assertEquals(HandshakeState.DISARMED, s.state)
        assertEquals("D5BC053B9452BA6D4406F8659EDFC066", hex(disarm.plaintext))
        // Arm again from DISARMED is legal.
        assertNotNull(s.arm(nowMs = 1300))
    }

    @Test
    fun armFromIdleIsRefused() {
        val s = HandshakeSequencer(key, cfg)
        assertNull(s.arm(nowMs = 0))
        assertEquals(HandshakeState.IDLE, s.state)
    }

    @Test
    fun resubscribePredicateUses20Seconds() {
        val s = HandshakeSequencer(key, cfg)
        assertFalse(s.isResubscribeDue(nowMs = 999_999)) // no notification yet
        s.onNotification(nowMs = 0)
        assertFalse(s.isResubscribeDue(nowMs = 19_999))
        assertTrue(s.isResubscribeDue(nowMs = 20_000))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:testDebugUnitTest`
Expected: FAIL — `Unresolved reference 'CommandEncoder'` / `'HandshakeSequencer'`.

- [ ] **Step 4: Write the implementation**

`core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/CommandEncoder.kt`:

```kotlin
package com.hpsmiles.golfsim.core.connect

import com.hpsmiles.golfsim.core.ble.Mlm2proCrypto

/** GATT characteristic a command targets (UUIDs live in Mlm2proGattClient KDoc). */
enum class CommandTarget { AUTH_REQUEST, COMMAND, CONFIGURE, HEARTBEAT }

/**
 * A protocol write produced by [HandshakeSequencer]. `plaintext` is the
 * on-wire payload: ciphered by [CommandEncoder] where the protocol encrypts,
 * left raw where it does not (the auth request carries the key in the clear).
 */
data class WriteCommand(
    val target: CommandTarget,
    val plaintext: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is WriteCommand && target == other.target && plaintext.contentEquals(other.plaintext)

    override fun hashCode(): Int = 31 * target.hashCode() + plaintext.contentHashCode()
}

/**
 * Inputs to the CONFIGURE write. Field semantics per the reverse-engineered
 * protocol spec (`mlm2pro.md`): ball type byte, indoor/outdoor flag, barometric
 * pressure and temperature — PROVENANCE NOTE: pressure/temp encodings are
 * unverified against a live device and are re-checked in Phase B.
 */
data class EnvironmentConfig(
    val ballType: Int = 0x02,
    val indoor: Boolean = false,
    val pressureRaw: Int = 0x7DC8,
    val tempCentiC: Int = 1500,
)

/** Builds the protocol writes; all encryption goes through :core:ble Mlm2proCrypto. */
object CommandEncoder {

    /** Auth request: 6-byte header (version 1 LE + encryption type 1) + the raw 32-byte key. */
    fun authRequest(key: ByteArray): WriteCommand {
        require(key.size == 32) { "session key must be 32 bytes" }
        val payload = byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x01) + key
        return WriteCommand(CommandTarget.AUTH_REQUEST, payload)
    }

    fun arm(key: ByteArray): WriteCommand =
        WriteCommand(CommandTarget.COMMAND, Mlm2proCrypto.encrypt(ARM_PLAINTEXT, key))

    fun disarm(key: ByteArray): WriteCommand =
        WriteCommand(CommandTarget.COMMAND, Mlm2proCrypto.encrypt(DISARM_PLAINTEXT, key))

    fun heartbeat(key: ByteArray): WriteCommand =
        WriteCommand(CommandTarget.HEARTBEAT, Mlm2proCrypto.encrypt(byteArrayOf(0x01), key))

    /** CONFIGURE write: 14-byte plaintext, then encrypted. Token from the Rapsodo API. */
    fun config(cfg: EnvironmentConfig, token: Int, key: ByteArray): WriteCommand {
        val p = ByteArray(14)
        p[0] = 0x01
        p[1] = cfg.ballType.toByte()
        p[2] = if (cfg.indoor) 1 else 0
        p[3] = 0x00
        // Plan deviation (mechanical, pin-verified): the verified CONFIG
        // ciphertext decrypts to 01 02 00 00 7DC8 DC05 A63C5A44 0000 — i.e.
        // pressure BE16, temperature LE16, token BE32 (the plan's LE helpers
        // produced a different ciphertext and self-contradicted the pin).
        p[4] = ((cfg.pressureRaw shr 8) and 0xFF).toByte()
        p[5] = (cfg.pressureRaw and 0xFF).toByte()
        p[6] = (cfg.tempCentiC and 0xFF).toByte()
        p[7] = ((cfg.tempCentiC shr 8) and 0xFF).toByte()
        p[8] = ((token shr 24) and 0xFF).toByte()
        p[9] = ((token shr 16) and 0xFF).toByte()
        p[10] = ((token shr 8) and 0xFF).toByte()
        p[11] = (token and 0xFF).toByte()
        p[12] = 0x00
        p[13] = 0x00
        return WriteCommand(CommandTarget.CONFIGURE, Mlm2proCrypto.encrypt(p, key))
    }

    private val ARM_PLAINTEXT = byteArrayOf(0x01, 0x0D, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00)
    private val DISARM_PLAINTEXT = byteArrayOf(0x01, 0x0D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
}
```

`core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/HandshakeSequencer.kt`:

```kotlin
package com.hpsmiles.golfsim.core.connect

/**
 * Pure handshake/arbitrary-session state machine for the MLM2PRO protocol.
 * Zero Android imports; all timing is driven by the caller-supplied
 * `nowMs` clock, so tests run on virtual time with no sleeping.
 *
 * State flow:
 * IDLE -> (subscriptions complete) AUTH_SENT -> (WRITE_RESPONSE 0x02)
 * TOKEN_WAIT -> (token fetched) CONFIG_WRITE_1 -> (+200 ms) READY
 * -> arm/disarm at will; heartbeat every 2 s while connected.
 */
class HandshakeSequencer(
    private val key: ByteArray,
    private val config: EnvironmentConfig,
) {
    var state: HandshakeState = HandshakeState.IDLE
        private set

    /** User id parsed from the WRITE_RESPONSE; -1 until authed. */
    var userId: Int = -1
        private set

    var lastNotificationAtMs: Long = Long.MIN_VALUE
        private set

    private var token = 0
    private var config1AtMs = Long.MIN_VALUE
    private var lastHeartbeatAtMs = Long.MIN_VALUE

    fun onSubscriptionsComplete(nowMs: Long): WriteCommand {
        state = HandshakeState.AUTH_SENT
        return CommandEncoder.authRequest(key)
    }

    /** Feed the decrypted WRITE_RESPONSE payload. Returns null (no write needed). */
    fun onWriteResponse(payload: ByteArray, nowMs: Long) {
        if (state != HandshakeState.AUTH_SENT) return
        if (payload.isEmpty() || payload[0] != 0x02.toByte()) {
            state = HandshakeState.FAULTED
            return
        }
        userId = (payload[2].toInt() and 0xFF) or
            ((payload[3].toInt() and 0xFF) shl 8) or
            ((payload[4].toInt() and 0xFF) shl 16) or
            ((payload[5].toInt() and 0xFF) shl 24)
        state = HandshakeState.TOKEN_WAIT
    }

    /** Token fetched from the Rapsodo API; returns the first CONFIGURE write. */
    fun onToken(token: Int, nowMs: Long): WriteCommand {
        check(state == HandshakeState.TOKEN_WAIT) { "token arrives in TOKEN_WAIT, was $state" }
        this.token = token
        state = HandshakeState.CONFIG_WRITE_1
        config1AtMs = nowMs
        return CommandEncoder.config(config, token, key)
    }

    fun onTokenFailed(nowMs: Long) {
        if (state == HandshakeState.TOKEN_WAIT) state = HandshakeState.FAULTED
    }

    /** Time-driven writes: CONFIGURE #2 after the 200 ms gap; heartbeats every 2 s. */
    fun poll(nowMs: Long): List<WriteCommand> {
        val out = mutableListOf<WriteCommand>()
        if (state == HandshakeState.CONFIG_WRITE_1 && nowMs - config1AtMs >= CONFIG_GAP_MS) {
            state = HandshakeState.READY
            lastHeartbeatAtMs = nowMs
            out += CommandEncoder.config(config, token, key)
        }
        if (state == HandshakeState.READY || state == HandshakeState.ARMED || state == HandshakeState.DISARMED) {
            if (nowMs - lastHeartbeatAtMs >= HEARTBEAT_PERIOD_MS) {
                lastHeartbeatAtMs = nowMs
                out += CommandEncoder.heartbeat(key)
            }
        }
        return out
    }

    fun arm(nowMs: Long): WriteCommand? {
        if (state != HandshakeState.READY && state != HandshakeState.DISARMED) return null
        state = HandshakeState.ARMED
        return CommandEncoder.arm(key)
    }

    fun disarm(nowMs: Long): WriteCommand? {
        if (state != HandshakeState.ARMED) return null
        state = HandshakeState.DISARMED
        return CommandEncoder.disarm(key)
    }

    /** The device stops notifying after ~20 s of missed resubscription. */
    fun isResubscribeDue(nowMs: Long): Boolean =
        lastNotificationAtMs != Long.MIN_VALUE && nowMs - lastNotificationAtMs >= RESUBSCRIBE_PERIOD_MS

    /** Call for EVERY notification received; refreshes the resubscribe window. */
    fun onNotification(nowMs: Long) {
        lastNotificationAtMs = nowMs
    }

    companion object {
        const val CONFIG_GAP_MS = 200L
        const val HEARTBEAT_PERIOD_MS = 2_000L
        const val RESUBSCRIBE_PERIOD_MS = 20_000L
    }
}

enum class HandshakeState {
    IDLE, AUTH_SENT, TOKEN_WAIT, CONFIG_WRITE_1, READY, ARMED, DISARMED, FAULTED
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:testDebugUnitTest`
Expected: PASS — CommandEncoderTest `tests="6" failures="0" errors="0"`, HandshakeSequencerTest `tests="10" failures="0" errors="0"` (16 new; module total 16).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts core/connect/build.gradle.kts core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/CommandEncoder.kt core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/HandshakeSequencer.kt core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/CommandEncoderTest.kt core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/HandshakeSequencerTest.kt
git commit -m "feat(core-connect): handshake sequencer with command encoder"
```

### Task 3: :core:connect — scanner, GATT client, token provider

Andoid-platform code: compile/assembling verified only (device behavior is Phase B).
Pure crypto-routing logic (`maybeDecrypt`) is TDD.

**Files:**
- Create: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proScanner.kt`
- Create: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt`
- Create: `core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/RapsodoTokenProvider.kt`
- Test: `core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/GattCryptoTest.kt`

- [ ] **Step 1: Write the failing test**

`core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/GattCryptoTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.connect

import com.hpsmiles.golfsim.core.ble.Mlm2proCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.security.GeneralSecurityException

class GattCryptoTest {

    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun maybeDecryptPassesPlaintextThroughWhenNot16ByteAligned() {
        // Auth responses and event payloads arrive raw; malformed ciphertext too.
        val raw = byteArrayOf(0x02, 0x44, 0x5A, 0x3C, 0xA6.toByte(), 0x00)
        val out = Mlm2proGattClient.maybeDecrypt(raw, key)
        assertSame(raw, out)
    }

    @Test
    fun maybeDecryptDecryptsValidCiphertext() {
        val secret = "44004F00E2FF0A01C8FFFC0705000A0000000000"
        val plaintext = ByteArray(20)
        for (i in 0 until 20) {
            plaintext[i] = ((Character.digit(secret[i * 2], 16) shl 4) or
                Character.digit(secret[i * 2 + 1], 16)).toByte()
        }
        val ciphertext = Mlm2proCrypto.encrypt(plaintext, key)
        val out = Mlm2proGattClient.maybeDecrypt(ciphertext, key)
        assertArrayEquals(plaintext, out)
    }

    @Test
    fun maybeDecryptFallsBackToRawOnBadPadding() {
        // A 32-byte blob that is NOT valid ciphertext must not crash the client;
        // it passes through raw for the decoder to reject as Malformed.
        val junk = ByteArray(32) { (it * 7 + 3).toByte() }
        val out = Mlm2proGattClient.maybeDecrypt(junk, key)
        assertSame(junk, out)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.connect.GattCryptoTest"`
Expected: FAIL — `Unresolved reference 'Mlm2proGattClient'`.

- [ ] **Step 3: Write the implementation**

`core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proScanner.kt`:

```kotlin
package com.hpsmiles.golfsim.core.connect

import android.Manifest
import androidx.annotation.RequiresPermission
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A discovered MLM2PRO. `name` includes the MLM2- prefix. */
data class Mlm2proDevice(val name: String, val address: String, val rssi: Int)

/**
 * Scans for MLM2PRO launch monitors (BLE names prefixed "MLM2-").
 * Thin Android wrapper; filtering/found-dedup logic lives with callers.
 * Requires BLUETOOTH_SCAN (neverForLocation) — declared in the :app manifest.
 */
class Mlm2proScanner(private val context: Context) {

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun scan(): Flow<Mlm2proDevice> = callbackFlow {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: throw IllegalStateException("no Bluetooth adapter")
        val scanner = adapter.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth is off")
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: return
                if (!name.startsWith(NAME_PREFIX)) return
                trySend(Mlm2proDevice(name, result.device.address, result.rssi))
            }
        }
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED
        ) {
            scanner.startScan(filters, settings, callback)
        }
        awaitClose { try { scanner.stopScan(callback) } catch (ignored: Exception) { } }
    }

    companion object {
        const val NAME_PREFIX = "MLM2-"
        const val SERVICE_UUID = "daf9b2a4-e4db-4be4-816d-298a050f25cd"
    }
}
```

`core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/RapsodoTokenProvider.kt`:

```kotlin
package com.hpsmiles.golfsim.core.connect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Result of a token fetch. */
sealed interface TokenResult {
    data class Success(val token: Int) : TokenResult
    data class Failure(val reason: String) : TokenResult
}

/** Fetches the ~3-hour simulator token for a user id using the account Secret. */
interface RapsodoTokenProvider {
    suspend fun fetch(userId: Int, secret: String): TokenResult
}

/**
 * HTTP implementation. Endpoint and header per the reverse-engineered spec
 * (`mlm2pro.md`): GET /api/simulator/user/{id}, header `Secret: <secret>`,
 * response carries a JSON body with the token as a 32-bit integer — the exact
 * response SHAPE is a Phase B verification item; this parser is deliberately
 * tolerant (first "token" JSON field, numeric).
 */
class HttpRapsodoTokenProvider : RapsodoTokenProvider {

    override suspend fun fetch(userId: Int, secret: String): TokenResult =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("$BASE_URL/user/$userId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Secret", secret)
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val code = connection.responseCode
                if (code != 200) {
                    return@withContext TokenResult.Failure("HTTP $code")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val token = parseToken(body)
                    ?: return@withContext TokenResult.Failure("token missing in response")
                TokenResult.Success(token)
            } catch (e: IOException) {
                TokenResult.Failure(e.message ?: "network error")
            } finally {
                connection?.disconnect()
            }
        }

    companion object {
        const val BASE_URL = "https://mlm.rapsodo.com/api/simulator"

        /** Minimal tolerant parse: the first `"token": <int>` occurrence. */
        fun parseToken(body: String): Int? {
            val idx = body.indexOf("\"token\"")
            if (idx < 0) return null
            val tail = body.substring(idx)
            val match = Regex("\"token\"\\s*:\\s*(-?\\d+)").find(tail) ?: return null
            return match.groupValues[1].toIntOrNull()
        }
    }
}
```

`core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt`:

```kotlin
package com.hpsmiles.golfsim.core.connect

import android.Manifest
import androidx.annotation.RequiresPermission
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.ble.Characteristic
import com.hpsmiles.golfsim.core.ble.Mlm2proDecoder
import com.hpsmiles.golfsim.core.ble.Mlm2proCrypto
import com.hpsmiles.golfsim.core.ble.BallDataResult
import com.hpsmiles.golfsim.core.ble.Mlm2proMessage
import com.hpsmiles.golfsim.core.ble.Mlm2proEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle state surfaced to the UI (rendered in the range status strip). */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    /** Handshake in progress (auth -> token -> config). */
    data object Handshaking : ConnectionState
    data object Armed : ConnectionState
    data class Faulted(val reason: String) : ConnectionState
}

/**
 * Owns the GATT connection to a bonded MLM2PRO and drives
 * [HandshakeSequencer] with real device events.
 *
 * PROTOCOL FACTS (from the reverse-engineered spec, verify in Phase B):
 * - service UUID daf9b2a4-e4db-4be4-816d-298a050f25cd
 * - notify characteristics: EVENTS 02E525FD-…, HEARTBEAT EF6A028E-…,
 *   MEASUREMENT 76830BCE-…, WRITE_RESPONSE CFBBCB0D-…
 * - notifications are AES-encrypted with the session key, except
 *   WRITE_RESPONSE and possibly raw event payloads — [maybeDecrypt]
 *   routes each notification accordingly.
 */
class Mlm2proGattClient(
    private val context: Context,
    private val sequencer: HandshakeSequencer,
) {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    /** Fired for every decoded shot measurement. */
    var onMeasurement: ((BallData) -> Unit)? = null

    private var gatt: BluetoothGatt? = null
    private var clockMs: () -> Long = { System.currentTimeMillis() }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        _state.value = ConnectionState.Connecting
        gatt = device.connectGatt(
            context,
            /* autoConnect = */ false,
            object : BluetoothGattCallback() {
                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        _state.value = ConnectionState.Faulted("service discovery $status")
                        return
                    }
                    subscribe(g, EVENTS_UUID)
                    subscribe(g, HEARTBEAT_UUID)
                    subscribe(g, MEASUREMENT_UUID)
                    subscribe(g, WRITE_RESPONSE_UUID)
                    _state.value = ConnectionState.Handshaking
                    performWrite(sequencer.onSubscriptionsComplete(clockMs()))
                }

                override fun onCharacteristicChanged(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) {
                    handleNotification(characteristic.uuid.toString(), value)
                }
            },
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = ConnectionState.Disconnected
    }

    /** Route one decrypted/raw notification through the M1 decoder. */
    internal fun handleNotification(uuid: String, value: ByteArray) {
        sequencer.onNotification(clockMs())
        // Plan deviation (mechanical): the M1 Characteristic enum deliberately
        // excludes WRITE_RESPONSE (M4 scope there), and adding an enum constant
        // would alter the frozen M1 decoder surface — so WRITE_RESPONSE is
        // routed by UUID string here; everything else goes through the enum.
        if (uuid.equals(WRITE_RESPONSE_UUID, ignoreCase = true)) {
            sequencer.onWriteResponse(maybeDecrypt(value, sessionKeyBytes), clockMs())
            sequencer.poll(clockMs()).forEach { performWrite(it) }
            return
        }
        val fromUuid = Characteristic.fromUuid(uuid) ?: return
        val plain = maybeDecrypt(value, sessionKeyBytes)
        when (val msg = Mlm2proDecoder.decode(fromUuid, value, sessionKeyBytes)) {
            is Mlm2proMessage.Measurement ->
                (msg.result as? BallDataResult.Shot)?.let { onMeasurement?.invoke(it.data) }
            is Mlm2proMessage.Event ->
                if (msg.event is Mlm2proEvent.ShotDetected ||
                    msg.event is Mlm2proEvent.Ready
                ) _state.value = _state.value // state kept; sequencer owns protocol
            else -> Unit
        }
        sequencer.poll(clockMs()).forEach { performWrite(it) }
    }

    private fun performWrite(write: WriteCommand) {
        val g = gatt ?: return
        val characteristic = when (write.target) {
            CommandTarget.AUTH_REQUEST -> findCharacteristic(g, AUTH_UUID)
            CommandTarget.COMMAND -> findCharacteristic(g, COMMAND_UUID)
            CommandTarget.CONFIGURE -> findCharacteristic(g, CONFIGURE_UUID)
            CommandTarget.HEARTBEAT -> findCharacteristic(g, HEARTBEAT_UUID)
        } ?: return
        characteristic.value = write.plaintext
        g.writeCharacteristic(characteristic)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun subscribe(g: BluetoothGatt, uuid: String) {
        val characteristic = findCharacteristic(g, uuid) ?: return
        g.setCharacteristicNotification(characteristic, true)
    }

    private fun findCharacteristic(g: BluetoothGatt, uuid: String): BluetoothGattCharacteristic? =
        g.getService(java.util.UUID.fromString(SERVICE_UUID))
            ?.getCharacteristic(java.util.UUID.fromString(uuid))

    private var sessionKeyBytes: ByteArray = ByteArray(32)

    /** Session key generated per connection; the auth write carries it raw. */
    fun setSessionKey(key: ByteArray) {
        require(key.size == 32)
        sessionKeyBytes = key
    }

    companion object {
        const val SERVICE_UUID = "daf9b2a4-e4db-4be4-816d-298a050f25cd"
        const val EVENTS_UUID = "02e525fd-7960-4ef0-bfb7-de0f514518ff"
        const val HEARTBEAT_UUID = "ef6a028e-f78b-47a4-b56c-dda6dae85cbf"
        const val MEASUREMENT_UUID = "76830bce-b9a7-4f69-aeaa-fd5b9f6b0965"
        const val WRITE_RESPONSE_UUID = "cfbbcb0d-7121-4bc2-bf54-8284166d61f0"
        const val AUTH_UUID = "b1e9ce5b-48c8-4a28-89dd-12ffd779f5e1"
        const val COMMAND_UUID = "1ea0fa51-1649-4603-9c5f-59c940323471"
        const val CONFIGURE_UUID = "df5990cf-47fb-4115-8fdd-40061d40af84"

        /**
         * BLE payloads from this device are usually AES-encrypted (multiple of
         * 16 bytes with the session key); raw ones (auth accept, unencrypted
         * events) pass through. An encrypted-looking blob that fails to decrypt
         * passes through raw so the decoder can report Malformed.
         * Pure function — unit tested in GattCryptoTest.
         */
        fun maybeDecrypt(payload: ByteArray, key: ByteArray): ByteArray {
            if (payload.isEmpty() || payload.size % 16 != 0) return payload
            return try {
                Mlm2proCrypto.decrypt(payload, key)
            } catch (ignored: java.security.GeneralSecurityException) {
                payload
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.connect.GattCryptoTest"`
Expected: PASS — GattCryptoTest `tests="3" failures="0" errors="0"`.

- [ ] **Step 5: Verify module assembles (covers the Android wrappers)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:connect:assembleDebug :core:connect:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; module unit-test census 19 (16 prior + 3 new).

- [ ] **Step 6: Commit**

```bash
git add core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proScanner.kt core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/Mlm2proGattClient.kt core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/RapsodoTokenProvider.kt core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/GattCryptoTest.kt
git commit -m "feat(core-connect): gatt client scanner and token provider"
```

### Task 4: PovProjector (pure projection math, :app)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovProjector.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/range/PovProjectorTest.kt`

A pure Kotlin object with no Android imports, so it is JVM-unit-testable. The
camera sits at the origin, 1.7 m above the ground, looking straight down-range
(+y). A world point (x, y, z) — x lateral metres (+right), y down-range metres,
z up metres — projects to normalized screen coordinates:

- `u = x / y` (lateral screen position; 0 is dead ahead)
- `v = (1.7 - z) / y` (vertical screen position; 0 is exactly at eye level / horizon; positive is BELOW the horizon i.e. toward the viewer's feet)
- `scale = 1 / y` (proxy for on-screen size of a 1 m object at that distance)

The canvas layer multiplies these by a focal length in pixels and centers them.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/kotlin/com/hpsmiles/golfsim/range/PovProjectorTest.kt
package com.hpsmiles.golfsim.range

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pinhole projection used by the POV range canvas. Values verified
 * by hand: camera height 1.7 m at the origin looking down +y, so a point on
 * the ground at distance d has v = 1.7 / d and scale = 1 / d.
 */
class PovProjectorTest {

    @Test
    fun groundBandsProjectAtExactFractions() {
        // v for a ground point at distance d is 1.7 / d.
        assertEquals(1.7 / 50.0, PovProjector.bandV(50.0), 1e-12)
        assertEquals(1.7 / 100.0, PovProjector.bandV(100.0), 1e-12)
        assertEquals(1.7 / 150.0, PovProjector.bandV(150.0), 1e-12)
        assertEquals(1.7 / 200.0, PovProjector.bandV(200.0), 1e-12)
    }

    @Test
    fun nearerBandsAreLowerOnScreen() {
        // v grows toward the viewer: 50 m band is below the 100 m band.
        assertTrue(PovProjector.bandV(50.0) > PovProjector.bandV(100.0))
        assertTrue(PovProjector.bandV(100.0) > PovProjector.bandV(150.0))
        assertTrue(PovProjector.bandV(150.0) > PovProjector.bandV(200.0))
    }

    @Test
    fun straightAheadGroundPointIsCentered() {
        val p = PovProjector.project(0.0, 100.0, 0.0)!!
        assertEquals(0.0, p.u, 1e-12)
        assertEquals(1.7 / 100.0, p.v, 1e-12)
        assertEquals(1.0 / 100.0, p.scale, 1e-12)
    }

    @Test
    fun apexAboveEyeLevelProjectsAboveHorizon() {
        // Apex at 30 m up, 150 m out: v = (1.7 - 30) / 150 < 0 (above horizon).
        val p = PovProjector.project(0.0, 150.0, 30.0)!!
        assertTrue(p.v < 0.0)
    }

    @Test
    fun typicalLandingPointIsInBounds() {
        // Landing at x=+10 m lateral, 163 m out: small positives both axes.
        val p = PovProjector.project(10.0, 163.0, 0.0)!!
        assertEquals(10.0 / 163.0, p.u, 1e-12)
        assertTrue(p.v > 0.0)
        assertTrue(p.v < 1.7 / 100.0) // 163 m is farther than the 100 m band
    }

    @Test
    fun pointsAtOrBehindTheCameraAreRejected() {
        assertNull(PovProjector.project(1.0, 0.0, 0.0))
        assertNull(PovProjector.project(1.0, -5.0, 2.0))
    }

    @Test
    fun scaleShrinksWithDistance() {
        val near = PovProjector.project(0.0, 50.0, 0.0)!!
        val far = PovProjector.project(0.0, 200.0, 0.0)!!
        assertTrue(near.scale > far.scale)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.PovProjectorTest"`
Expected: FAIL — `Unresolved reference 'PovProjector'` at every test call.

- [ ] **Step 3: Write the implementation**

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/range/PovProjector.kt
package com.hpsmiles.golfsim.range

/**
 * Pinhole projection from range world coordinates to normalized screen
 * coordinates. Pure Kotlin (no Android types) so it is unit-testable on the
 * JVM. The canvas layer converts these normalized values to pixels.
 *
 * World axes (metres): x lateral (+right), y down-range (+away from the
 * hitter), z up. Camera: at the origin, [CAM_HEIGHT_M] above the ground,
 * looking straight down +y with no tilt — the horizon is exactly at v = 0.
 */
object PovProjector {

    /** Eye height of the camera above the hitting mat, in metres. */
    const val CAM_HEIGHT_M = 1.7

    /**
     * Normalized projection of a world point. `u` is lateral position (0 =
     * dead ahead, +right), `v` is vertical position (0 = horizon, +toward the
     * viewer's feet), `scale` is the relative on-screen size of a 1 m object
     * at that depth. All values are independent of viewport size.
     */
    data class ProjectedPoint(val u: Double, val v: Double, val scale: Double)

    /**
     * Projects a world point to the screen, or returns null when the point is
     * at or behind the camera plane (y <= 0) — such points must not be drawn.
     */
    fun project(x: Double, y: Double, z: Double): ProjectedPoint? {
        if (y <= 0.0) return null
        return ProjectedPoint(
            u = x / y,
            v = (CAM_HEIGHT_M - z) / y,
            scale = 1.0 / y,
        )
    }

    /**
     * The `v` of the ground at a given down-range distance (a horizontal
     * distance band line on the POV canvas).
     */
    fun bandV(distanceM: Double): Double = CAM_HEIGHT_M / distanceM
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.PovProjectorTest"`
Expected: PASS — XML census `tests="7" failures="0" errors="0"` (plus the
pre-existing app ScaffoldSmokeTest still green in full-module runs).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/PovProjector.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/PovProjectorTest.kt
git commit -m "feat(app): pov range projector"
```

### Task 5: Range screen with POV and top-down canvases

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/DisplayShot.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/TopDownCanvas.kt`
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/MainActivity.kt` (full rewrite)
- Modify: `app/build.gradle.kts` (two dependency lines)
- Delete: `app/src/main/kotlin/com/hpsmiles/golfsim/ShowcaseScreen.kt` (`git rm`)

This task is **assembly-verified** (composables; the projection math is already pinned by Task 4). Verification = `:app:assembleDebug` green.

- [ ] **Step 1: Add :core:ble and :core:physics dependencies**

In `app/build.gradle.kts`, the `dependencies` block currently starts with the designsystem line. Make it read:

```kotlin
dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:ble"))
    implementation(project(":core:physics"))
```

(the remaining existing lines are untouched.)

- [ ] **Step 2: Write DisplayShot, RangeScreen, PovRangeCanvas, TopDownCanvas, AppRoot**

`app/src/main/kotlin/com/hpsmiles/golfsim/range/DisplayShot.kt`:

```kotlin
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult

/**
 * One shot as the range screen holds it: the decoded launch data, the solve
 * inputs used, and the physics result. All UI rendering derives from these.
 */
data class DisplayShot(
    val ballData: BallData,
    val launch: LaunchConditions,
    val shotResult: ShotResult,
)
```

`app/src/main/kotlin/com/hpsmiles/golfsim/range/PovRangeCanvas.kt`:

```kotlin
package com.hpsmiles.golfsim.range

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.physics.ShotResult

private val SKY_TOP = GolfColors.Panel
private val SKY_BOTTOM = GolfColors.Base
private val GROUND_TOP = Color(0xFF131A20)
private val GROUND_BOTTOM = GolfColors.Base

/**
 * Player-perspective range view. Static scene (bands, targets) plus the
 * current shot's tracer and landing pulse, driven by [playFraction] in 0..1
 * (1 = flight complete). All world-to-screen math goes through PovProjector.
 * The tracer arc is a stylized quadratic anchored to the real solve
 * endpoints (documented plan deviation: ShotResult carries no trajectory
 * samples; physical arcs are a Phase C design decision).
 */
@Composable
fun PovRangeCanvas(currentShot: ShotResult?, playFraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizonPx = h * 0.30f
        val focalPx = w * 1.10f
        val centerX = w / 2f

        // Sky and ground gradients.
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(SKY_TOP, SKY_BOTTOM), startY = 0f, endY = horizonPx,
            ),
            size = androidx.compose.ui.geometry.Size(w, horizonPx),
        )
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(GROUND_TOP, GROUND_BOTTOM), startY = horizonPx, endY = h,
            ),
            topLeft = Offset(0f, horizonPx),
            size = androidx.compose.ui.geometry.Size(w, h - horizonPx),
        )
        drawLine(GolfColors.Line, Offset(0f, horizonPx), Offset(w, horizonPx), 1f)

        // Plan-verbatim correction: use android.graphics.Paint directly for
        // native text (androidx Paint.asFrameworkPaint() was wrong in the draft).
        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 10.sp.toPx()
            color = GolfColors.TextMuted.toArgb()
        }

        // Distance bands on the ground: v = camH / distance.
        val bandDistances = listOf(50f, 100f, 150f, 200f)
        for (d in bandDistances) {
            val y = horizonPx + PovProjector.bandV(d.toDouble()) * focalPx
            drawLine(
                color = GolfColors.Line.copy(alpha = 0.8f),
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
            val centre = PovProjector.project(lateralM.toDouble(), distM.toDouble(), 0.0) ?: continue
            val cx = centerX + centre.u * focalPx
            val cy = horizonPx + centre.v * focalPx
            // Ground circle: horizontal radius projects directly; vertical
            // extent from the near/far edge band difference.
            val rx = (targetRadiusM / distM) * focalPx
            val nearV = PovProjector.bandV((distM - targetRadiusM).toDouble())
            val farV = PovProjector.bandV((distM + targetRadiusM).toDouble())
            // Mechanical: bandV returns Double — Size() needs Float.
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

        if (currentShot != null) {
            val s = currentShot
            fun worldToScreen(x: Double, y: Double, z: Double): Offset? {
                val p = PovProjector.project(x, y, z) ?: return null
                return Offset((centerX + p.u * focalPx).toFloat(), (horizonPx + p.v * focalPx).toFloat())
            }

            // Tracer: stylized quadratic through real solve endpoints.
            val points = 40
            val drawn = (points * playFraction.coerceIn(0f, 1f)).toInt()
            if (drawn > 0) {
                val path = androidx.compose.ui.graphics.Path()
                var first = true
                for (i in 0..drawn) {
                    val t = i.toFloat() / points
                    val x = s.sideM * t
                    val y = s.carryM * t
                    val z = 4.0 * s.apexM * t * (1.0 - t)
                    val point = worldToScreen(x, y, z) ?: break
                    if (first) { path.moveTo(point.x, point.y); first = false }
                    else path.lineTo(point.x, point.y)
                }
                if (!first) {
                    drawPath(path, GolfColors.Amber, style = Stroke(width = 2.5f))
                }
                // Ball dot at the current tracer head.
                val t = drawn.toFloat() / points
                val head = worldToScreen(s.sideM * t, s.carryM * t, 4.0 * s.apexM * t * (1.0 - t))
                if (head != null) {
                    drawCircle(GolfColors.AmberGlow, radius = 9.sp.toPx(), center = head)
                    drawCircle(GolfColors.Amber, radius = 5.sp.toPx(), center = head)
                }
            }

            // Landing pulse once the flight has completed.
            if (playFraction >= 1f) {
                val landing = worldToScreen(s.sideM, s.carryM + s.rolloutM * 0.0, 0.0)
                if (landing != null) {
                    val pulse = ((playFraction - 1f) * 2f) % 1f
                    drawCircle(
                        GolfColors.AmberHalo,
                        radius = (6f + 18f * pulse).sp.toPx() * 0.8f,
                        center = landing,
                        style = Stroke(width = 2f),
                    )
                    drawCircle(GolfColors.Amber, radius = 4.sp.toPx(), center = landing)
                }
            }
        }
    }
}
```

(Correction applied: the shipped file uses `android.graphics.Paint` directly for
text; the draft's `Paint().asFrameworkPaint()` extension and the stray
`drawIntoCanvas` no-op were removed before shipping.)

`app/src/main/kotlin/com/hpsmiles/golfsim/range/TopDownCanvas.kt`:

```kotlin
package com.hpsmiles.golfsim.range

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.designsystem.GolfColors

/**
 * Top-down analytics view: range grid every 50 m, teal dispersion history
 * (55% alpha) and the amber last-shot landing.
 */
@Composable
fun TopDownCanvas(shots: List<DisplayShot>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(GolfColors.Base)

        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 10.sp.toPx()
            color = GolfColors.TextMuted.toArgb()
        }

        // World mapping: origin bottom-centre, x lateral ±60 m, y 0..250 m.
        val pxPerM = h / 260f
        val originX = w / 2f
        val originY = h - 8.sp.toPx()

        // Distance bands every 50 m across the full width.
        var distM = 50f
        while (distM <= 250f) {
            val y = originY - distM * pxPerM
            drawLine(
                GolfColors.Line.copy(alpha = 0.8f),
                Offset(0f, y), Offset(w, y), 1f,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${distM.toInt()} M", 8.sp.toPx(), y - 4.sp.toPx(), labelPaint,
            )
            distM += 50f
        }
        // Lateral gridlines every 20 m.
        var lateralM = -60f
        while (lateralM <= 60f) {
            val x = originX + lateralM * pxPerM
            if (x >= 0f && x <= w) {
                drawLine(
                    GolfColors.Line.copy(alpha = 0.5f),
                    Offset(x, 0f), Offset(x, originY), 1f,
                )
            }
            lateralM += 20f
        }

        val history = shots.dropLast(1)
        for (shot in history) {
            val x = originX + shot.shotResult.sideM * pxPerM
            val y = originY - shot.shotResult.carryM * pxPerM
            drawCircle(GolfColors.Teal.copy(alpha = 0.55f), radius = 3.sp.toPx(), center = Offset(x.toFloat(), y.toFloat()))
        }
        shots.lastOrNull()?.let { last ->
            val x = originX + last.shotResult.sideM * pxPerM
            val y = originY - last.shotResult.carryM * pxPerM
            drawCircle(GolfColors.AmberGlow, radius = 8.sp.toPx(), center = Offset(x.toFloat(), y.toFloat()))
            drawCircle(GolfColors.Amber, radius = 4.sp.toPx(), center = Offset(x.toFloat(), y.toFloat()))
        }
    }
}
```

`app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeScreen.kt`:

```kotlin
package com.hpsmiles.golfsim.range

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.ble.DemoShotSource
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTheme
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.MetricChip
import com.hpsmiles.golfsim.core.designsystem.MetricRow
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.core.designsystem.StatusStrip
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.UniformSurface
import java.util.Locale
import kotlin.math.sqrt

/** Metres/second to mph for display. */
private const val MPH_PER_MS = 2.23694

private enum class SpeedMult(val label: String, val divisor: Int) {
    X1("1x", 1), X2("2x", 2), X4("4x", 4);
}

private enum class ViewMode(val label: String) { POV("POV"), TOP_DOWN("TOP-DOWN") }

/**
 * Phase A range screen: demo-shot pipeline. FIRE solves a shot through the
 * real BallFlightEngine and renders metrics INSTANTLY; the tracer animation
 * plays alongside at real duration divided by the speed multiplier. In
 * Phase B/C the DemoShotSource is swapped for the BLE source — the UI
 * does not know where shots come from.
 */
@Composable
fun RangeScreen(modifier: Modifier = Modifier) {
    val shots = remember { mutableStateListOf<DisplayShot>() }
    val demoSource = remember { DemoShotSource() }
    var playFraction by remember { mutableFloatStateOf(1f) }
    var speedMult by remember { mutableStateOf(SpeedMult.X1) }
    var viewMode by remember { mutableStateOf(ViewMode.POV) }
    val currentShot = shots.lastOrNull()

    // Tracer playback: animate playFraction over the shot's real duration.
    LaunchedEffect(currentShot, speedMult) {
        val shot = currentShot ?: return@LaunchedEffect
        if (shot.shotResult.flightTimeSec <= 0.0) return@LaunchedEffect
        val durationMs = shot.shotResult.flightTimeSec * 1000.0 / speedMult.divisor
        var lastNanos = withFrameNanos { it }
        while (playFraction < 1f) {
            val now = withFrameNanos { it }
            val deltaMs = (now - lastNanos) / 1_000_000.0
            lastNanos = now
            playFraction = (playFraction + (deltaMs / durationMs).toFloat()).coerceAtMost(1f)
        }
    }

    fun fire() {
        val ballData: BallData = demoSource.nextShot()
        val launch = LaunchConditions(
            ballSpeedMps = ballData.ballSpeed,
            launchAngleDeg = ballData.launchAngle,
            spinRpm = ballData.totalSpin,
            spinAxisDeg = ballData.spinAxis,
            launchDirDeg = ballData.launchDirection,
        )
        val result = BallFlightEngine.simulate(
            launch, Environment(), UniformSurface(com.hpsmiles.golfsim.core.physics.Surface.FAIRWAY_NORMAL),
        )
        playFraction = 0f
        shots.add(DisplayShot(ballData, launch, result))
        // Metrics render immediately from `shots`; the tracer animates on top.
    }

    Row(modifier = modifier.fillMaxSize().background(GolfColors.Base)) {
        // Range canvas with overlays.
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (viewMode == ViewMode.POV) {
                PovRangeCanvas(currentShot?.shotResult, playFraction, Modifier.fillMaxSize())
            } else {
                TopDownCanvas(shots, Modifier.fillMaxSize())
            }
            // DEMO badge, view toggle, speed toggle.
            Text(
                "DEMO",
                color = GolfColors.Amber,
                style = GolfTypography.Status,
                modifier = Modifier.align(Alignment.TopStart).padding(GolfSpacing.Sm)
                    .border(1.dp, GolfColors.Amber, RoundedCornerShape(50)).padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
            )
            Text(
                "VIEW: ${viewMode.label}",
                color = GolfColors.TextSecondary,
                style = GolfTypography.Status,
                modifier = Modifier.align(Alignment.TopEnd).padding(GolfSpacing.Sm)
                    .clickable {
                        viewMode = if (viewMode == ViewMode.POV) ViewMode.TOP_DOWN else ViewMode.POV
                    }
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                    .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
            )
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(GolfSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
            ) {
                for (speed in SpeedMult.entries) {
                    Text(
                        speed.label,
                        color = if (speed == speedMult) GolfColors.Teal else GolfColors.TextMuted,
                        style = GolfTypography.Status,
                        modifier = Modifier
                            .clickable { speedMult = speed }
                            .border(1.dp, if (speed == speedMult) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                            .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                    )
                }
            }
            // FIRE button, amber, bottom-right.
            Text(
                "FIRE",
                color = GolfColors.Base,
                style = GolfTypography.MetricLabel,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(GolfSpacing.Lg)
                    .background(GolfColors.Amber, RoundedCornerShape(50))
                    .clickable { fire() }
                    .padding(horizontal = GolfSpacing.Xl, vertical = GolfSpacing.Sm),
            )
        }

        // Right panel: metrics.
        Column(
            modifier = Modifier.width(210.dp).fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .background(GolfColors.Panel)
                .padding(GolfSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
        ) {
            SectionCard("LAST SHOT") {
                val shot = currentShot
                if (shot == null) {
                    Text("Fire a shot", style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
                } else {
                    val r = shot.shotResult
                    MetricChip("carry", String.format(Locale.US, "%.0f", r.carryM), "M")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip("total", String.format(Locale.US, "%.0f", r.totalM), "M")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip(
                        "ball", String.format(Locale.US, "%.1f", shot.ballData.ballSpeed * MPH_PER_MS), "MPH",
                        accent = GolfColors.Amber,
                    )
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip("spin", String.format(Locale.US, "%.0f", shot.ballData.totalSpin), "RPM")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip("launch", String.format(Locale.US, "%.1f", shot.ballData.launchAngle), "DEG")
                    Spacer(Modifier.size(GolfSpacing.Xs))
                    MetricChip("axis", String.format(Locale.US, "%.1f", shot.ballData.spinAxis), "DEG")
                }
            }
            SectionCard("SESSION") {
                val carries = shots.map { it.shotResult.carryM }
                val count = carries.size
                MetricRow("shots", "$count", "")
                MetricRow(
                    "avg carry",
                    if (count == 0) "-" else String.format(Locale.US, "%.0f", carries.average()),
                    "M",
                )
                val sigma = if (count > 1) {
                    val mean = carries.average()
                    sqrt(carries.sumOf { (it - mean) * (it - mean) } / count)
                } else 0.0
                MetricRow("sigma", if (count > 1) String.format(Locale.US, "%.1f", sigma) else "-", "M")
            }
        }
    }
}
```

**StatusStrip placement note:** the bottom status strip belongs to the whole screen, so `RangeScreen` cannot own it alone — it is emitted by `AppRoot` below the content area (this matches the M3 showcase layout).

`app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`:

```kotlin
package com.hpsmiles.golfsim.range

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfTheme
import com.hpsmiles.golfsim.core.designsystem.NavRail
import com.hpsmiles.golfsim.core.designsystem.NavRailButton
import com.hpsmiles.golfsim.core.designsystem.StatusStrip

/** Top-level app frame: nav rail + tab content + status strip. */
@Composable
fun AppRoot() {
    GolfTheme {
        Column(Modifier.fillMaxSize().background(GolfColors.Base)) {
            Row(Modifier.weight(1f)) {
                NavRail {
                    NavRailButton("RANGE", selected = true, onClick = { })
                }
                RangeScreen(Modifier.weight(1f))
            }
            // Phase A status: demo mode. Phase B/C swaps in BLE connection state.
            StatusStrip(armed = true, info = "DEMO MODE - FIRE TO SHOOT")
        }
    }
}
```

(`NavRail { ... }` content-slot signature per the M3 kit; `NavRailButton(label, selected, onClick)`.)

- [ ] **Step 3: Rewrite MainActivity; remove ShowcaseScreen**

`app/src/main/kotlin/com/hpsmiles/golfsim/MainActivity.kt` (complete file):

```kotlin
package com.hpsmiles.golfsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hpsmiles.golfsim.range.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}
```

Then (`git rm` the showcase):

```powershell
git rm app/src/main/kotlin/com/hpsmiles/golfsim/ShowcaseScreen.kt
```

- [ ] **Step 4: Assemble both modules**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug :core:connect:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If Kotlin errors appear, fix imports only — do not change component behaviour. Run `:app:testDebugUnitTest` once to confirm prior tests still pass (PovProjectorTest 7/0/0 + ScaffoldSmokeTest 1/0/0).

- [ ] **Step 5: Commit**

```powershell
git add app/build.gradle.kts app/src/main/kotlin/com/hpsmiles/golfsim/MainActivity.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/
git commit -m "feat(app): range screen with pov and top-down canvases"
```

(git rm already staged the deletion.)

### Task 6: Settings screen + SecretStore + BLE permissions

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiley/golfsim/settings/SecretStore.kt` — NOTE: package path is `com/hpsmiles/golfsim/settings/` (typo guard: **hpsmiles**, not hpsmiley)
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt` (rewrite — adds SETTINGS tab)
- Modify: `app/src/main/AndroidManifest.xml` (2 permission lines)

Assembly-verified task (no unit tests — UI + storage wrapper; fault surface covered by compile + tablet gate Secret-persistence check).

- [ ] **Step 1: Write SecretStore.kt**

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/settings/SecretStore.kt
package com.hpsmiles.golfsim.settings

import android.content.Context

/**
 * Stores the Rapsodo user Secret (for the Phase B token fetch).
 * SharedPreferences is sufficient for one string; migrate to DataStore
 * in M5 persistence if requirements grow.
 */
class SecretStore(context: Context) {

    private val prefs = context.getSharedPreferences("golfsim_prefs", Context.MODE_PRIVATE)

    fun getSecret(): String = prefs.getString(KEY, "").orEmpty()

    fun setSecret(secret: String) {
        prefs.edit().putString(KEY, secret).apply()
    }

    companion object {
        private const val KEY = "rapsodo_secret"
    }
}
```

- [ ] **Step 2: Write SettingsScreen.kt**

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/settings/SettingsScreen.kt
package com.hpsmiles.golfsim.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard

/**
 * Phase A settings: only the Rapsodo Secret. Phase B consumes it for the
 * token fetch (requires "Awesome Golf" third-party access enabled in the
 * Rapsodo app — user prerequisite, see M4a spec §2).
 */
@Composable
fun SettingsScreen() {
    // LocalContext.current must run in composable scope, not inside
    // remember's calculation lambda.
    val context = LocalContext.current
    val store = remember { SecretStore(context) }
    var text by remember { mutableStateOf(store.getSecret()) }
    var saved by remember { mutableStateOf(false) }

    SectionCard("RAPSODO AUTH") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; saved = false },
            placeholder = { Text("Rapsodo Secret") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        // Plan-note-shipped pattern: SAVE is a Text label inside a Box with
        // the standard androidx clickable — MetricChip has no onClick variant.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { store.setSecret(text); saved = true }
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = if (saved) "SAVED" else "SAVE",
                style = GolfTypography.MetricLabel,
                color = if (saved) GolfColors.BleArmedGreen else GolfColors.TextPrimary,
            )
        }
        Text(
            text = "Requires Awesome Golf third-party access enabled in the Rapsodo app (Phase B prerequisite).",
            style = GolfTypography.Status,
            color = GolfColors.TextMuted,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

(Correction applied: the block above now matches the shipped file — SAVE is a Text-in-Box with androidx .clickable; GolfTypography.Status is a property read; LocalContext is hoisted. Behavior: typing updates `text`; tapping SAVE persists via SecretStore and shows SAVED.)

- [ ] **Step 3: Rewrite AppRoot.kt with the SETTINGS tab**

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
package com.hpsmiles.golfsim.range

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfTheme
import com.hpsmiles.golfsim.core.designsystem.NavRail
import com.hpsmiles.golfsim.core.designsystem.NavRailButton
import com.hpsmiles.golfsim.core.designsystem.StatusStrip
import com.hpsmiles.golfsim.settings.SettingsScreen

private enum class RangeTab { RANGE, SETTINGS }

@Composable
fun AppRoot() {
    var tab by remember { mutableStateOf(RangeTab.RANGE) }
    GolfTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GolfColors.Base)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(modifier = Modifier.weight(1f)) {
                NavRail {
                    // Mechanical: NavRailButton(label, selected, onClick, modifier) —
                    // a trailing lambda would bind to `modifier`, so name onClick.
                    NavRailButton("RANGE", tab == RangeTab.RANGE, onClick = { tab = RangeTab.RANGE })
                    NavRailButton("SETTINGS", tab == RangeTab.SETTINGS, onClick = { tab = RangeTab.SETTINGS })
                }
                when (tab) {
                    RangeTab.RANGE -> RangeScreen()
                    RangeTab.SETTINGS -> SettingsScreen()
                }
            }
            StatusStrip(
                armed = true,
                info = if (tab == RangeTab.RANGE) "DEMO MODE - FIRE TO SHOOT" else "SETTINGS",
            )
        }
    }
}
```

(RangeScreen gains no signature change; AppRoot now routes both tabs. MainActivity stays `setContent { AppRoot() }`.)

- [ ] **Step 4: Manifest permissions**

In `app/src/main/AndroidManifest.xml`, add immediately before `<application`:

```xml
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

- [ ] **Step 5: Verify assembly**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/settings/ app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt app/src/main/AndroidManifest.xml
git commit -m "feat(app): settings screen secret store and ble permissions"
```

### Task 7: Full build, tablet demo gate, PR, CI (FINAL — merge only after user gate)

**Files:** none created (build/push/PR only).

- [ ] **Step 1: Full build + test census**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build`
Expected: `BUILD SUCCESSFUL`. Census (COUNT, don't assume — ballpark ≈ 142): :core:ble 36 (31 + DemoShotSource 5), :core:connect 19 (CommandEncoder 6 + Sequencer 10 + GattCrypto 3), :app 8 (ScaffoldSmokeTest 1 + PovProjector 7), :core:designsystem 14, :core:physics 65.

- [ ] **Step 2: Install on tablet**

User connects Lenovo TB373FU (USB debugging; accept RSA if prompted). Run `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:installDebug`
Expected: `Installed on 1 device`.

- [ ] **Step 3: USER TABLET DEMO GATE — do NOT proceed to merge without the user's acceptance**

The user fires demo shots and verifies: tracer animates at real speed with 1x/2x/4x toggles; metrics appear INSTANTLY on fire (never gated by the animation); top-down toggle shows history dots + last landing; DEMO badge visible; Secret entered in SETTINGS persists across an app restart. The implementer reports; the orchestrator runs the gate with the user.

- [ ] **Step 4: Push + PR**

```bash
git push -u origin m4a-demo-pipeline
gh pr create --title "M4a: Demo pipeline" --body "$(cat <<'EOF'
## Summary
- ShotSource + seeded DemoShotSource in :core:ble; full :core:connect BLE client (handshake sequencer, command encoder, scanner, GATT client, token provider) JVM-tested against pinned wire bytes
- Range UI in :app: POV canvas + top-down analytics toggle, real-speed playback with 2x/4x, instant metrics, DEMO mode with FIRE button
- Settings screen with Rapsodo Secret storage; BLE permissions

## Test Plan
- [ ] Full build green (census listed in plan Task 7)
- [ ] CI build check green on this PR
- [ ] Tablet demo gate passed (demo shots, instant metrics, Secret persists)
EOF
)"
```

- [ ] **Step 5: CI poll**

`Start-Sleep 120` then a SINGLE `gh pr checks` (NOT --watch; +90s repeats if pending). Expect `build pass`.

- [ ] **Step 6: Tick checkboxes as met; report**

Tick the build census + CI checkboxes via `gh pr edit` once verified. Do NOT merge and do NOT tick the tablet checkbox — that is the user's gate (M0 precedent: `gh pr merge --merge --delete-branch`, no squash, only after user acceptance).

## Self-Review Checklist (for the controller)

1. **Spec coverage** (spec = `docs/superpowers/specs/2026-09-20-m4a-demo-pipeline-design.md`, ca5c66b): §3 pipeline+module map → T1/T5; §4 sequencer/encoder/gatt/scanner/token → T2/T3; §5 PovProjector + RangeScreen + instant metrics → T4/T5; §6 testing/gates → per-task tests + T7 gate; Phase B/C criteria recorded in spec, not this plan's scope.
2. **Placeholder scan:** no TBD/TODO; every code step shows full code; Tasks 3/5/6 are assembly-verified with that stated.
3. **Type consistency:** BallData→LaunchConditions→DisplayShot (T1→T5); CommandTarget→WriteCommand→HandshakeSequencer (T2→T3); ShotResult fields (carryM/totalM/sideM/apexM/flightTimeSec) consumed by T5 canvases; M3 kit signatures used as shipped (NavRailButton(label, selected, onClick), MetricChip(label, value, unit, accent)).

