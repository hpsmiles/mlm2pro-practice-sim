# M1 — MLM2PRO BLE Byte-Decoder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the M0 marker in `:core:ble` with a pure, deterministic, fully-tested MLM2PRO BLE notification decoder (AES decrypt → measurement/event parsing → typed SI results).

**Architecture:** Three standalone pure layers (crypto, measurement parser, event parser) behind a thin characteristic-routing orchestrator, per the approved design spec `docs/superpowers/specs/2026-09-20-m1-ble-decoder-design.md`. Error policy from the spec: runtime data problems never throw (sealed `Malformed`/`Unknown` values); programmer errors (wrong key length) throw `IllegalArgumentException`. Golden fixtures live in `src/test/resources/golden/` and are scanned table-driven — adding an M4 shed capture is dropping a `.properties` file, zero code change.

**Tech Stack:** Kotlin 2.4.20 (pure JVM module `:core:ble`), JUnit 4.13.2 with `Parameterized`, `javax.crypto` (AES-256-CBC + PKCS7), `java.util.Properties` fixtures. **No new dependencies.**

---

## Environment (read this first)

- Repo root / working directory: `C:\aaa\code\golf sim`. Branch `m1-ble-decoder` is already checked out and clean (base: `master` @ `18e5c82`).
- **Windows PowerShell 5.1** — never chain with `&&` (unsupported). Chain with `;`.
- **JDK is not on PATH.** Every Gradle command must set JAVA_HOME in the same command:
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat <task>`
- If your shell tool defaults to a 2-minute timeout, raise it to 600000 ms for Gradle commands (cold runs can exceed 120s).
- The module build file is already configured (M0 `java {}` + `kotlin {}` JVM-17 alignment). No build-file changes are needed in this milestone.
- Unit test runbook: `.\gradlew.bat :core:ble:test --tests "<fully.qualified.ClassName>"`; full build: `.\gradlew.bat build`.

## File structure (complete map)

```
core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/   (create unless noted)
├── Mlm2proCrypto.kt        Task 1 — AES-256-CBC + PKCS7, fixed IV, symmetric
├── BallData.kt             Task 2 — shot data model (SI, full precision)
├── BallDataResult.kt       Task 2 — sealed Shot | Misread | Malformed
├── MeasurementParser.kt    Task 2 — decrypted 20 bytes → BallDataResult
├── Mlm2proEvent.kt         Task 4 — sealed event model
├── EventParser.kt          Task 4 — event bytes → Mlm2proEvent?
├── Characteristic.kt       Task 5 — enum of the 2 M1-parseable notify characteristics
├── Mlm2proMessage.kt       Task 5 — sealed orchestrator result
└── Mlm2proDecoder.kt       Task 5 — orchestrator: routes, decrypts, dispatches
    (delete) Mlm2proBle.kt            Task 6 — M0 marker object removed
core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/
├── Hex.kt                  Task 1 — test-only hex parse/format helpers
├── Mlm2proCryptoTest.kt    Task 1
├── MeasurementParserTest.kt  Task 2
├── GoldenFixturesTest.kt   Task 3 — parameterized, scans golden/
├── EventParserTest.kt      Task 4
├── Mlm2proDecoderTest.kt   Task 5
    (delete) ScaffoldSmokeTest.kt     Task 6 — M0 smoke test removed
core/ble/src/test/resources/golden/
├── measurement-001.properties      Task 3 — the published sample
├── misread-001.properties          Task 3 — all-zero sentinel
├── malformed-length-001.properties Task 3 — empty payload
├── malformed-length-002.properties Task 3 — 19 bytes
└── malformed-length-003.properties Task 3 — 21 bytes
docs/superpowers/specs/2026-09-20-m1-ble-decoder-design.md   Task 5 — one-line §4 amendment (UUID overload)
AGENTS.md                                               Task 6 — reference-repo correction
```

Pinned crypto vector (used by Task 1's wire-format guard; key = bytes `0x00..0x1F`, fixed device IV, plaintext = the published sample):

- plaintext hex: `44004F00E2FF0A01C8FFFC0705000A0000000000` (20 bytes)
- ciphertext hex: `F3E3B5638AFD4B44F165257345EF9E58B18F8C84637EBEB62C3D2EA82981241C` (32 bytes)
- generated with an independent implementation (AES-256-CBC/PKCS7), so it pins the wire format rather than echoing our own code

---

### Task 1: Mlm2proCrypto — AES-256-CBC layer

**Files:**
- Create: `core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/Hex.kt`
- Create: `core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proCryptoTest.kt`
- Create: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proCrypto.kt`

- [ ] **Step 1: Write the test-only Hex helper and the failing crypto tests**

`Hex.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

/** Test-only hex string helpers. */
object Hex {
    fun parse(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun format(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }
}
```

`Mlm2proCryptoTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Mlm2proCryptoTest {

    companion object {
        private val KEY = ByteArray(32) { it.toByte() } // test key = bytes 0x00..0x1F
        private val SAMPLE = Hex.parse("44004F00E2FF0A01C8FFFC0705000A0000000000")

        // Wire-format guard generated with an independent AES-256-CBC/PKCS7
        // implementation (key 0x00..0x1F, the device's fixed IV, the published
        // sample plaintext). Pinned on purpose: recomputing it from
        // Mlm2proCrypto itself would be circular and couldn't catch drift.
        private val PINNED_CIPHERTEXT = Hex.parse(
            "F3E3B5638AFD4B44F165257345EF9E58B18F8C84637EBEB62C3D2EA82981241C"
        )
    }

    @Test
    fun roundTripsTwentyByteMeasurement() {
        val ciphertext = Mlm2proCrypto.encrypt(SAMPLE, KEY)
        assertArrayEquals(SAMPLE, Mlm2proCrypto.decrypt(ciphertext, KEY))
    }

    @Test
    fun roundTripsEmptyPlaintext() {
        val ciphertext = Mlm2proCrypto.encrypt(ByteArray(0), KEY)
        assertArrayEquals(ByteArray(0), Mlm2proCrypto.decrypt(ciphertext, KEY))
    }

    @Test
    fun roundTripsSevenByteCommand() {
        val arm = Hex.parse("010D0001000000") // device arm command (M4 will write this)
        assertArrayEquals(arm, Mlm2proCrypto.decrypt(Mlm2proCrypto.encrypt(arm, KEY), KEY))
    }

    @Test
    fun encryptsToPaddedBlockSize() {
        assertEquals(32, Mlm2proCrypto.encrypt(SAMPLE, KEY).size)      // 20 B + PKCS7 → 2 blocks
        assertEquals(16, Mlm2proCrypto.encrypt(ByteArray(0), KEY).size) // 0 B → 1 block
    }

    @Test
    fun pinnedVectorDecryptsToPublishedSample() {
        assertArrayEquals(SAMPLE, Mlm2proCrypto.decrypt(PINNED_CIPHERTEXT, KEY))
    }

    @Test
    fun wrongKeyLengthThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            Mlm2proCrypto.encrypt(SAMPLE, ByteArray(16))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Mlm2proCrypto.decrypt(SAMPLE, ByteArray(64))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails (red)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.Mlm2proCryptoTest"`
Expected: FAIL — compile error `Unresolved reference 'Mlm2proCrypto'` in the test source.

- [ ] **Step 3: Write Mlm2proCrypto**

`Mlm2proCrypto.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-CBC + PKCS7 crypto used by the MLM2PRO BLE link.
 *
 * The device protocol wraps every GATT payload except the initial auth
 * request with this scheme: a fixed IV (reverse-engineered from the Rapsodo
 * app — see docs/superpowers/specs/2026-09-20-m1-ble-decoder-design.md §2)
 * and a 32-byte client-chosen session key sent in the clear in the auth
 * request. Decrypt handles device→client notifications; encrypt is for the
 * future M4 GATT client's command writes (arm/disarm/config).
 *
 * Pure JVM (javax.crypto), no Android dependencies. This is the device's
 * obfuscation layer, not a security boundary: the IV is public and the key
 * crosses the wire in plaintext by protocol design.
 *
 * Key-length errors are programmer errors → IllegalArgumentException, per
 * the spec's fail-fast policy. Corrupt ciphertext throws
 * GeneralSecurityException from [decrypt]; the orchestrator (Mlm2proDecoder)
 * converts that to a Malformed message.
 */
object Mlm2proCrypto {

    private val FIXED_IV = byteArrayOf(
        0x6D, 0x2E, 0x52, 0x13, 0x21, 0x32, 0x04, 0x45,
        0x6F, 0x2C, 0x79, 0x48, 0x10, 0x65, 0x6D, 0x42,
    )

    // PKCS5Padding in the JCA name = PKCS7 for a 16-byte AES block.
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray =
        doCipher(Cipher.ENCRYPT_MODE, plaintext, key)

    fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray =
        doCipher(Cipher.DECRYPT_MODE, ciphertext, key)

    private fun doCipher(mode: Int, input: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 session key must be 32 bytes, was ${key.size}" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(FIXED_IV))
        return cipher.doFinal(input)
    }
}
```

- [ ] **Step 4: Run test to verify it passes (green)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.Mlm2proCryptoTest"`
Expected: PASS — 6 tests, 0 failures.

- [ ] **Step 5: Commit**

```powershell
git add core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proCrypto.kt core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/Hex.kt core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proCryptoTest.kt
git commit -m "feat(core-ble): mlm2pro aes-cbc crypto layer with pinned wire-format vector"
```

---

### Task 2: MeasurementParser + ball data model

**Files:**
- Create: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/BallData.kt`
- Create: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/BallDataResult.kt`
- Create: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/MeasurementParser.kt`
- Test: `core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/MeasurementParserTest.kt`

- [ ] **Step 1: Write the failing parser tests**

`MeasurementParserTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementParserTest {

    @Test
    fun parsesPublishedSample() {
        val result = MeasurementParser.parse(
            Hex.parse("44004F00E2FF0A01C8FFFC0705000A0000000000")
        )
        val shot = result as BallDataResult.Shot
        assertEquals(6.8, shot.data.clubHeadSpeed, 1e-9)
        assertEquals(7.9, shot.data.ballSpeed, 1e-9)
        assertEquals(-3.0, shot.data.launchDirection, 1e-9)
        assertEquals(26.6, shot.data.launchAngle, 1e-9)
        assertEquals(-5.6, shot.data.spinAxis, 1e-9)
        assertEquals(2044, shot.data.totalSpin)
        assertEquals(5, shot.data.unknown1)
        assertEquals(10, shot.data.unknown2)
    }

    @Test
    fun readsSignedInt16sAsNegative() {
        val result = MeasurementParser.parse(measurement(hla = -1234, spinAxis = -5678))
        val shot = result as BallDataResult.Shot
        assertEquals(-123.4, shot.data.launchDirection, 1e-9)
        assertEquals(-567.8, shot.data.spinAxis, 1e-9)
    }

    @Test
    fun readsBoundaryRawValues() {
        checkBoundary(32767, 3276.7)   // Int16 max
        checkBoundary(-32768, -3276.8) // Int16 min
        checkBoundary(1, 0.1)          // smallest step; note: a raw of 0 across
                                        // ALL fields is the misread sentinel
    }

    private fun checkBoundary(raw: Int, expectedDegrees: Double) {
        val result = MeasurementParser.parse(measurement(hla = raw, spinAxis = raw))
        val shot = result as BallDataResult.Shot
        assertEquals(expectedDegrees, shot.data.launchDirection, 1e-9)
        assertEquals(expectedDegrees, shot.data.spinAxis, 1e-9)
    }

    @Test
    fun allZeroPayloadIsMisread() {
        assertEquals(BallDataResult.Misread, MeasurementParser.parse(ByteArray(20)))
    }

    @Test
    fun wrongLengthsAreMalformed() {
        assertTrue(MeasurementParser.parse(ByteArray(0)) is BallDataResult.Malformed)
        assertTrue(MeasurementParser.parse(ByteArray(19)) is BallDataResult.Malformed)
        assertTrue(MeasurementParser.parse(ByteArray(21)) is BallDataResult.Malformed)
    }

    /** Builds a synthetic 20-byte payload from raw (pre-scale) Int16 values. */
    private fun measurement(
        chs: Int = 68,
        ball: Int = 79,
        hla: Int = -30,
        vla: Int = 266,
        spinAxis: Int = -56,
        spin: Int = 2044,
        unknown1: Int = 5,
        unknown2: Int = 10,
    ): ByteArray = ByteArray(20).also { b ->
        write16(b, 0, chs); write16(b, 2, ball); write16(b, 4, hla); write16(b, 6, vla)
        write16(b, 8, spinAxis); write16(b, 10, spin); write16(b, 12, unknown1); write16(b, 14, unknown2)
    }

    private fun write16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
    }
}
```

- [ ] **Step 2: Run test to verify it fails (red)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.MeasurementParserTest"`
Expected: FAIL — compile error `Unresolved reference 'MeasurementParser'`.

- [ ] **Step 3: Write the data model and parser**

`BallData.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

/**
 * Ball/club metrics decoded from one MLM2PRO MEASUREMENT notification.
 *
 * SI units (m/s, degrees, rpm), full precision — no rounding anywhere in the
 * decoder; the display layer (M3+) converts to mph/yards and rounds.
 *
 * Sign conventions are UNVERIFIED until live captures (M4): HLA direction and
 * fade-vs-draw spin axis pass through from the device uninterpreted. Offsets
 * 12–15 are unresolved by every public reverse-engineering; they ride along
 * raw so M4 side-by-side captures can establish their meaning.
 */
data class BallData(
    val clubHeadSpeed: Double,    // m/s   — offsets 0–1, raw/10
    val ballSpeed: Double,        // m/s   — offsets 2–3, raw/10
    val launchDirection: Double,  // HLA ° — offsets 4–5, raw/10, signed, sign preserved
    val launchAngle: Double,      // VLA ° — offsets 6–7, raw/10, signed, sign preserved
    val spinAxis: Double,         // °     — offsets 8–9, raw/10, signed, sign preserved
    val totalSpin: Int,           // rpm   — offsets 10–11, UInt16, unscaled
    val unknown1: Int,            // raw   — offsets 12–13, uninterpreted
    val unknown2: Int,            // raw   — offsets 14–15, uninterpreted
)
```

`BallDataResult.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

/**
 * Parse outcome for a MEASUREMENT payload. Runtime data problems never
 * throw — they land here as sealed variants a live session can log and skip.
 */
sealed interface BallDataResult {
    data class Shot(val data: BallData) : BallDataResult
    data object Misread : BallDataResult
    data class Malformed(val reason: String) : BallDataResult
}
```

`MeasurementParser.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

/**
 * Parses the decrypted 20-byte MLM2PRO MEASUREMENT payload.
 *
 * Layout (little-endian): 0–1 club head speed, 2–3 ball speed (both Int16
 * raw/10 m/s); 4–5 HLA, 6–7 VLA, 8–9 spin axis (signed Int16 raw/10 °);
 * 10–11 total spin (UInt16 rpm); 12–13 / 14–15 unknown raw; 16–19 zeros.
 * An all-zero payload is the documented misread sentinel.
 */
object MeasurementParser {

    const val PAYLOAD_SIZE: Int = 20

    fun parse(payload: ByteArray): BallDataResult = when {
        payload.size != PAYLOAD_SIZE ->
            BallDataResult.Malformed("expected $PAYLOAD_SIZE-byte payload, was ${payload.size}")
        payload.all { it == 0.toByte() } ->
            BallDataResult.Misread
        else -> BallDataResult.Shot(
            BallData(
                clubHeadSpeed = int16(payload, 0) / 10.0,
                ballSpeed = int16(payload, 2) / 10.0,
                launchDirection = int16(payload, 4) / 10.0,
                launchAngle = int16(payload, 6) / 10.0,
                spinAxis = int16(payload, 8) / 10.0,
                totalSpin = uint16(payload, 10),
                unknown1 = uint16(payload, 12),
                unknown2 = uint16(payload, 14),
            )
        )
    }

    private fun int16(b: ByteArray, off: Int): Int {
        val raw = le16(b, off)
        return if (raw >= 0x8000) raw - 0x10000 else raw
    }

    private fun uint16(b: ByteArray, off: Int): Int = le16(b, off)

    private fun le16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or (b[off + 1].toInt() and 0xFF shl 8)
}
```

- [ ] **Step 4: Run test to verify it passes (green)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.MeasurementParserTest"`
Expected: PASS — 5 tests, 0 failures.

- [ ] **Step 5: Commit**

```powershell
git add core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/BallData.kt core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/BallDataResult.kt core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/MeasurementParser.kt core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/MeasurementParserTest.kt
git commit -m "feat(core-ble): measurement payload parser with SI ball data model"
```

---

### Task 3: Golden fixture harness (table-driven, scan-directory)

**Files:**
- Create: `core/ble/src/test/resources/golden/measurement-001.properties`
- Create: `core/ble/src/test/resources/golden/misread-001.properties`
- Create: `core/ble/src/test/resources/golden/malformed-length-001.properties`
- Create: `core/ble/src/test/resources/golden/malformed-length-002.properties`
- Create: `core/ble/src/test/resources/golden/malformed-length-003.properties`
- Test: `core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/GoldenFixturesTest.kt`

- [ ] **Step 1: Write the five seed fixture files**

`golden/measurement-001.properties`:

```properties
# The single published MLM2PRO measurement sample (Duwaynef/MLM2PRO-BT-APP, mlm2pro.md).
type=measurement
hex=44004F00E2FF0A01C8FFFC0705000A0000000000
clubHeadSpeed=6.8
ballSpeed=7.9
launchDirection=-3.0
launchAngle=26.6
spinAxis=-5.6
totalSpin=2044
unknown1=5
unknown2=10
```

`golden/misread-001.properties`:

```properties
# All-zero 20-byte payload - the documented misread sentinel.
type=misread
hex=0000000000000000000000000000000000000000
```

`golden/malformed-length-001.properties`:

```properties
# Empty payload.
type=malformed
hex=
```

`golden/malformed-length-002.properties`:

```properties
# 19 bytes - one byte short.
type=malformed
hex=44004F00E2FF0A01C8FFFC0705000A00000000
```

`golden/malformed-length-003.properties`:

```properties
# 21 bytes - one byte long.
type=malformed
hex=44004F00E2FF0A01C8FFFC0705000A000000000000
```

- [ ] **Step 2: Write the parameterized test that scans the golden directory**

`GoldenFixturesTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.Properties

/**
 * Table-driven golden-fixture regression test: every *.properties file under
 * src/test/resources/golden/ becomes one test case named after the file.
 * Adding an M4 shed capture is dropping a new .properties file here — no
 * test-code change.
 *
 * File schema:
 *   type   = measurement | misread | malformed
 *   hex    = payload bytes (case-insensitive; empty string = empty payload)
 *   (measurement only) clubHeadSpeed, ballSpeed, launchDirection, launchAngle,
 *   spinAxis (Double); totalSpin, unknown1, unknown2 (Int)
 *
 * NOTE: this harness exercises the DECRYPTED payload against MeasurementParser.
 * Full ciphertext-in → result-out coverage lives in Mlm2proDecoderTest; M4
 * capture files that bundle ciphertext + session key will extend the schema.
 */
@RunWith(Parameterized::class)
class GoldenFixturesTest(private val fixtureName: String, private val fixture: Properties) {

    @Test
    fun decodesAsExpected() {
        val payload = Hex.parse(fixture.getProperty("hex"))
        when (fixture.getProperty("type")) {
            "measurement" -> assertShot(payload)
            "misread" -> assertEquals(BallDataResult.Misread, MeasurementParser.parse(payload))
            "malformed" -> assertTrue(
                "$fixtureName should be Malformed",
                MeasurementParser.parse(payload) is BallDataResult.Malformed,
            )
            else -> fail("unknown fixture type: ${fixture.getProperty("type")}")
        }
    }

    private fun assertShot(payload: ByteArray) {
        val result = MeasurementParser.parse(payload)
        val shot = result as? BallDataResult.Shot
            ?: fail("$fixtureName should parse to a Shot, was $result")
        assertEquals(d("clubHeadSpeed"), shot.data.clubHeadSpeed, 1e-9)
        assertEquals(d("ballSpeed"), shot.data.ballSpeed, 1e-9)
        assertEquals(d("launchDirection"), shot.data.launchDirection, 1e-9)
        assertEquals(d("launchAngle"), shot.data.launchAngle, 1e-9)
        assertEquals(d("spinAxis"), shot.data.spinAxis, 1e-9)
        assertEquals(i("totalSpin"), shot.data.totalSpin)
        assertEquals(i("unknown1"), shot.data.unknown1)
        assertEquals(i("unknown2"), shot.data.unknown2)
    }

    private fun d(key: String) = fixture.getProperty(key).toDouble()
    private fun i(key: String) = fixture.getProperty(key).toInt()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): Collection<Array<Any>> =
            goldenFiles().map { file ->
                arrayOf(file.name, file.inputStream().use { Properties().apply { load(it) } })
            }

        private fun goldenFiles(): List<File> =
            GoldenFixturesTest::class.java.classLoader.getResources("golden").toList()
                .map(::File)
                .flatMap { dir -> dir.walkTopDown().filter { f -> f.isFile && f.name.endsWith(".properties") } }
                .sortedBy { it.name }
    }
}
```

- [ ] **Step 3: Run the harness (passes immediately — the parser already exists; this proves the harness and seed files agree)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.GoldenFixturesTest"`
Expected: PASS — 5 test cases (one per fixture file), 0 failures. If any case fails, the fixture file or harness is wrong — fix before committing. The published-sample case must decode to exactly the values in the M1 spec §2.

- [ ] **Step 4: Negative control — prove the harness can fail (one-off, then revert)**

Temporarily change `totalSpin=2044` to `totalSpin=2045` in `golden/measurement-001.properties`, re-run the same command. Expected: FAIL on the `measurement-001` case. Restore `2044`, re-run, confirm PASS. Nothing from this probe gets committed.

- [ ] **Step 5: Commit**

```powershell
git add core/ble/src/test/resources/golden
git commit -m "test(core-ble): table-driven golden fixture harness with five seed fixtures"
```

---

### Task 4: EventParser + event model

**Files:**
- Create: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proEvent.kt`
- Create: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/EventParser.kt`
- Test: `core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/EventParserTest.kt`

- [ ] **Step 1: Write the failing event tests**

`EventParserTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventParserTest {

    @Test
    fun shotDetected() {
        assertEquals(Mlm2proEvent.ShotDetected, EventParser.parse(Hex.parse("00")))
    }

    @Test
    fun processing() {
        assertEquals(Mlm2proEvent.Processing, EventParser.parse(Hex.parse("01")))
    }

    @Test
    fun ready() {
        assertEquals(Mlm2proEvent.Ready, EventParser.parse(Hex.parse("02")))
    }

    @Test
    fun batteryPercentPassesThrough() {
        assertEquals(Mlm2proEvent.Battery(100), EventParser.parse(Hex.parse("0364")))
        assertEquals(Mlm2proEvent.Battery(0), EventParser.parse(Hex.parse("0300")))
    }

    @Test
    fun misreadAlert() {
        assertEquals(Mlm2proEvent.MisreadAlert, EventParser.parse(Hex.parse("0500")))
    }

    @Test
    fun disarmed() {
        assertEquals(Mlm2proEvent.Disarmed, EventParser.parse(Hex.parse("0501")))
    }

    @Test
    fun unknownCodesArePreserved() {
        assertEquals(Mlm2proEvent.Unknown(0x04, null), EventParser.parse(Hex.parse("04")))
        assertEquals(Mlm2proEvent.Unknown(0x99, 0x12), EventParser.parse(Hex.parse("9912")))
        assertEquals(Mlm2proEvent.Unknown(0x05, 0x09), EventParser.parse(Hex.parse("0509")))
    }

    @Test
    fun emptyPayloadIsNull() {
        assertNull(EventParser.parse(ByteArray(0)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails (red)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.EventParserTest"`
Expected: FAIL — compile error `Unresolved reference 'EventParser'`.

- [ ] **Step 3: Write the event model and parser**

`Mlm2proEvent.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

/**
 * Device lifecycle events from the MLM2PRO EVENTS characteristic.
 *
 * Codes: 0x00 shot happened, 0x01 processing shot, 0x02 ready for shot,
 * 0x03 battery (byte 1 = percent), 0x05 0x00 last shot misread,
 * 0x05 0x01 device disarmed. Everything else — including the unresolved
 * 0x04 — lands in [Unknown] with both bytes preserved, per the design's
 * "never throw, never lose data" event policy.
 */
sealed interface Mlm2proEvent {
    data object ShotDetected : Mlm2proEvent   // 0x00
    data object Processing : Mlm2proEvent     // 0x01
    data object Ready : Mlm2proEvent           // 0x02
    data class Battery(val percent: Int) : Mlm2proEvent  // 0x03
    data object MisreadAlert : Mlm2proEvent    // 0x05 0x00
    data object Disarmed : Mlm2proEvent        // 0x05 0x01
    data class Unknown(val code: Int, val subCode: Int?) : Mlm2proEvent
}
```

`EventParser.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

/**
 * Parses the decrypted MLM2PRO EVENTS payload: byte 0 = event code,
 * byte 1 = sub-code. Known codes map to typed variants; anything else maps
 * to [Mlm2proEvent.Unknown]. Never throws; returns null only for an empty
 * payload (nothing to parse).
 */
object EventParser {

    fun parse(payload: ByteArray): Mlm2proEvent? {
        if (payload.isEmpty()) return null
        val code = payload[0].toInt() and 0xFF
        val subCode = if (payload.size > 1) payload[1].toInt() and 0xFF else null
        return when (code) {
            0x00 -> Mlm2proEvent.ShotDetected
            0x01 -> Mlm2proEvent.Processing
            0x02 -> Mlm2proEvent.Ready
            0x03 -> if (subCode != null) Mlm2proEvent.Battery(subCode) else Mlm2proEvent.Unknown(code, subCode)
            0x05 -> when (subCode) {
                0x00 -> Mlm2proEvent.MisreadAlert
                0x01 -> Mlm2proEvent.Disarmed
                else -> Mlm2proEvent.Unknown(code, subCode)
            }
            else -> Mlm2proEvent.Unknown(code, subCode)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes (green)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.EventParserTest"`
Expected: PASS — 8 tests, 0 failures.

- [ ] **Step 5: Commit**

```powershell
git add core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proEvent.kt core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/EventParser.kt core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/EventParserTest.kt
git commit -m "feat(core-ble): event payload parser with unknown-code preservation"
```

---

### Task 5: Characteristic routing decoder (the entry point)

**Files:**
- Create: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Characteristic.kt`
- Create: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proMessage.kt`
- Create: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proDecoder.kt`
- Test: `core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proDecoderTest.kt`
- Modify: `docs/superpowers/specs/2026-09-20-m1-ble-decoder-design.md` (§4 — add UUID-overload contract line)

- [ ] **Step 1: Write the failing decoder tests**

`Mlm2proDecoderTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Mlm2proDecoderTest {

    companion object {
        private val KEY = ByteArray(32) { it.toByte() }
        private val SAMPLE = Hex.parse("44004F00E2FF0A01C8FFFC0705000A0000000000")
    }

    @Test
    fun decodesMeasurementEndToEnd() {
        val notification = Mlm2proCrypto.encrypt(SAMPLE, KEY)
        val message = Mlm2proDecoder.decode(Characteristic.MEASUREMENT, notification, KEY)
        val measurement = message as Mlm2proMessage.Measurement
        val shot = measurement.result as BallDataResult.Shot
        assertEquals(7.9, shot.data.ballSpeed, 1e-9)
        assertEquals(-5.6, shot.data.spinAxis, 1e-9)
        assertEquals(2044, shot.data.totalSpin)
    }

    @Test
    fun decodesMisreadEndToEnd() {
        val notification = Mlm2proCrypto.encrypt(ByteArray(20), KEY)
        val message = Mlm2proDecoder.decode(Characteristic.MEASUREMENT, notification, KEY)
        val measurement = message as Mlm2proMessage.Measurement
        assertEquals(BallDataResult.Misread, measurement.result)
    }

    @Test
    fun decodesEventsEndToEnd() {
        val notification = Mlm2proCrypto.encrypt(Hex.parse("00"), KEY)
        val message = Mlm2proDecoder.decode(Characteristic.EVENTS, notification, KEY)
        assertEquals(Mlm2proEvent.ShotDetected, (message as Mlm2proMessage.Event).event)
    }

    @Test
    fun corruptCiphertextIsMalformedNotThrown() {
        // 31 bytes: not a multiple of the 16-byte AES block — decrypt fails,
        // the decoder must convert that to Malformed, never throw.
        val message = Mlm2proDecoder.decode(Characteristic.MEASUREMENT, ByteArray(31), KEY)
        assertTrue(message is Mlm2proMessage.Malformed)
    }

    @Test
    fun wrongKeyLengthStillThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            Mlm2proDecoder.decode(Characteristic.MEASUREMENT, ByteArray(32), ByteArray(16))
        }
    }

    @Test
    fun uuidOverloadDecodesMeasurement() {
        val notification = Mlm2proCrypto.encrypt(SAMPLE, KEY)
        // Android's BLE stack hands over lowercase UUID strings.
        val message = Mlm2proDecoder.decode(
            "76830bce-b9a7-4f69-aeaa-fd5b9f6b0965", notification, KEY
        )
        assertTrue(message is Mlm2proMessage.Measurement)
    }

    @Test
    fun unknownUuidYieldsUnrecognized() {
        // WRITE_RESPONSE characteristic — known to exist, not decoded in M1.
        val notification = Mlm2proCrypto.encrypt(SAMPLE, KEY)
        val message = Mlm2proDecoder.decode(
            "CFBBCB0D-7121-4BC2-BF54-8284166D61F0", notification, KEY
        )
        val unrecognized = message as Mlm2proMessage.Unrecognized
        assertEquals("CFBBCB0D-7121-4BC2-BF54-8284166D61F0", unrecognized.characteristicUuid)
    }
}
```

- [ ] **Step 2: Run test to verify it fails (red)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.Mlm2proDecoderTest"`
Expected: FAIL — compile error `Unresolved reference 'Mlm2proDecoder'`.

- [ ] **Step 3: Write the enum, message model, and decoder**

`Characteristic.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

/**
 * The two MLM2PRO GATT notify characteristics that carry device-to-client
 * data decodable in M1. The remaining known characteristics (AUTH_REQUEST,
 * COMMAND, CONFIGURE are write-only; WRITE_RESPONSE, HEARTBEAT are M4 scope)
 * are deliberately absent: hand one to [Mlm2proDecoder.decode] via its UUID
 * overload and it yields [Mlm2proMessage.Unrecognized].
 */
enum class Characteristic(val uuid: String) {
    MEASUREMENT("76830BCE-B9A7-4F69-AEAA-FD5B9F6B0965"),
    EVENTS("02E525FD-7960-4EF0-BFB7-DE0F514518FF");

    companion object {
        fun fromUuid(uuid: String): Characteristic? =
            entries.firstOrNull { it.uuid.equals(uuid, ignoreCase = true) }
    }
}
```

`Mlm2proMessage.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

/**
 * Result of decoding one GATT notification. Runtime data problems never
 * throw — they land here as sealed variants a live session can log and skip.
 */
sealed interface Mlm2proMessage {
    data class Measurement(val result: BallDataResult) : Mlm2proMessage
    data class Event(val event: Mlm2proEvent) : Mlm2proMessage
    data class Malformed(val reason: String) : Mlm2proMessage
    data class Unrecognized(val characteristicUuid: String) : Mlm2proMessage
}
```

`Mlm2proDecoder.kt`:

```kotlin
package com.hpsmiles.golfsim.core.ble

import java.security.GeneralSecurityException

/**
 * Entry point for decoding MLM2PRO GATT notifications: decrypt, route by
 * characteristic, parse. This is what M4's GATT layer calls.
 *
 * Error policy (design spec §6): runtime data problems (corrupt ciphertext,
 * malformed payloads) become [Mlm2proMessage.Malformed] — the decoder must
 * never crash a live practice session. Programmer errors (wrong key length)
 * throw IllegalArgumentException.
 */
object Mlm2proDecoder {

    fun decode(
        characteristic: Characteristic,
        notification: ByteArray,
        key: ByteArray,
    ): Mlm2proMessage = try {
        val plaintext = Mlm2proCrypto.decrypt(notification, key)
        when (characteristic) {
            Characteristic.MEASUREMENT ->
                Mlm2proMessage.Measurement(MeasurementParser.parse(plaintext))
            Characteristic.EVENTS ->
                Mlm2proMessage.Event(
                    // Defensive: a decrypted EVENTS payload is never empty
                    // (PKCS7 always yields >= 1 byte), but EventParser's
                    // contract allows null — treat that as malformed data.
                    EventParser.parse(plaintext)
                        ?: return Mlm2proMessage.Malformed("empty EVENTS payload")
                )
        }
    } catch (e: GeneralSecurityException) {
        Mlm2proMessage.Malformed("decrypt failed: ${e.message}")
    }

    /**
     * UUID-string overload — the form Android's BLE stack naturally
     * supplies. Unknown UUIDs yield [Mlm2proMessage.Unrecognized].
     */
    fun decode(
        characteristicUuid: String,
        notification: ByteArray,
        key: ByteArray,
    ): Mlm2proMessage {
        val characteristic = Characteristic.fromUuid(characteristicUuid)
            ?: return Mlm2proMessage.Unrecognized(characteristicUuid)
        return decode(characteristic, notification, key)
    }
}
```

- [ ] **Step 4: Run test to verify it passes (green)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.Mlm2proDecoderTest"`
Expected: PASS — 7 tests, 0 failures.

- [ ] **Step 5: Amend the design spec (one line in §4, keeps spec and code in sync)**

In `docs/superpowers/specs/2026-09-20-m1-ble-decoder-design.md`, find:

```
- `Mlm2proDecoder.decode(characteristic: Characteristic, notification: ByteArray, key: ByteArray): Mlm2proMessage` — decrypts, routes by characteristic, dispatches to the right parser. The single entry point M4's GATT layer will call.
```

Append a new line directly after it:

```
- `Mlm2proDecoder.decode(characteristicUuid: String, notification: ByteArray, key: ByteArray): Mlm2proMessage` — overload taking a raw GATT characteristic UUID (what Android's BLE stack hands over); resolves case-insensitively via `Characteristic.fromUuid`, yielding `Unrecognized(uuid)` for anything outside the enum. The M4 GATT layer calls this form.
```

- [ ] **Step 6: Commit**

```powershell
git add core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Characteristic.kt core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proMessage.kt core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proDecoder.kt core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proDecoderTest.kt docs/superpowers/specs/2026-09-20-m1-ble-decoder-design.md
git commit -m "feat(core-ble): characteristic-routing decoder with uuid overload"
```

---

### Task 6: Remove the M0 marker, correct AGENTS.md, full build + PR

**Files:**
- Delete: `core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proBle.kt` (M0 marker object)
- Delete: `core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/ScaffoldSmokeTest.kt` (M0 smoke test)
- Modify: `AGENTS.md` (Data Acquisition section — reference-repo correction ordered by the spec §2)

- [ ] **Step 1: Delete the M0 marker and its smoke test**

```powershell
git rm core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/Mlm2proBle.kt core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/ScaffoldSmokeTest.kt
```

(Their purpose — proving the module compiles and tests run — is now covered by the real decoder test suite: 6 crypto + 5 measurement + 5 golden + 8 event + 7 decoder = 31 test cases.)

- [ ] **Step 2: Correct the reference repos in AGENTS.md**

In `AGENTS.md`, find this line under "Data Acquisition (BLE)":

```
- Parse MLM2PRO BLE advertisement/GATT data using reverse-engineered byte mappings from open-source repos: **MLM2PRO-BT-APP**, **flighthook**.
```

Replace it with:

```
- Parse MLM2PRO GATT notification data using reverse-engineered byte mappings from open-source repos: **Duwaynef/MLM2PRO-BT-APP** (C#, MIT — ships the written protocol spec `mlm2pro.md`) and **springbok/MLM2PRO-GSPro-Connector** (Python, GPL — independent corroboration, reference-only, no code copying). `flighthook` has no MLM2PRO BLE code and is not a reference.
```

- [ ] **Step 3: Run the full build (all modules, all tests, lint)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build`
Expected: BUILD SUCCESSFUL — `:core:ble:test` reports all decoder tests passing (including the golden fixture harness; M0 smoke test gone), `:app` and `:core:physics` unchanged and green.

- [ ] **Step 4: Commit**

```powershell
git add AGENTS.md
git commit -m "chore: remove m0 scaffold markers; correct reference repos in AGENTS.md"
```

- [ ] **Step 5: Push and open the PR**

```powershell
git push -u origin m1-ble-decoder
gh pr create --title "M1: MLM2PRO BLE byte-decoder" --body "## Summary
- Replaces the M0 marker in :core:ble with the layered decoder from the approved spec: Mlm2proCrypto (AES-256-CBC/PKCS7, fixed IV), MeasurementParser (20-byte LE payload, SI units, signed-field safe), EventParser (typed events, unknown-code preservation), Mlm2proDecoder orchestrator (characteristic routing, UUID overload).
- Table-driven golden-fixture harness in src/test/resources/golden/ — M4 shed captures get added by dropping .properties files.
- Pinned AES wire-format vector computed with an independent implementation guards against crypto regressions.
- Corrects AGENTS.md reference repos (flighthook has no MLM2PRO BLE code).

## Test Plan
- [x] :core:ble:test — 31 cases green (6 crypto, 5 measurement, 5 golden, 8 event, 7 decoder)
- [x] gradlew build green across all modules
- [ ] CI green on this PR"
```

- [ ] **Step 6: Verify CI green on the PR**

CI starts automatically (existing workflow). Poll without blocking: wait ~2 minutes, then run `gh pr checks`. If still pending, wait ~2 more minutes and re-run. Expected: `build  pass`. Do NOT use `gh pr checks --watch` (it appears to hang in some shells) — single polls only. If CI fails, read `gh run view <id> --log-failed`, fix, push, re-poll.

---

## Plan self-review checklist (for the executing orchestrator)

- Spec coverage: every §4 component exists as a task (crypto T1, measurement T2, events T4, decoder T5); §7 fixture schema and seed set in T3; §6 error policy encoded in T2/T5 tests; spec §2/§9 items are documentation, not code.
- The five seed fixture files: one measurement, one misread, three malformed lengths (empty/19/21 bytes).
- Type consistency: `Mlm2proCrypto.encrypt/decrypt`, `MeasurementParser.parse`, `EventParser.parse`, `Mlm2proDecoder.decode` (enum + UUID overloads), `BallData` field names, sealed variants `Shot/Misread/Malformed`, `Mlm2proEvent` variants, `Mlm2proMessage` variants — identical across all tasks.
- Exit criterion (ROADMAP M1): decoder reproduces all documented metrics for every golden fixture — proven by GoldenFixturesTest + the end-to-end decoder tests.
