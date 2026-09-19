# M1 — MLM2PRO BLE Byte-Decoder Design

**Module:** `:core:ble` · **Status:** design approved via brainstorming; awaiting implementation plan

## 1. Goal

Replace the M0 marker object in `:core:ble` with a pure, deterministic Kotlin decoder for the Rapsodo MLM2PRO BLE protocol: encrypted GATT notifications in, typed SI-unit shot data and device events out. Everything is unit-testable on the JVM with no hardware. Real shed captures are deferred to M4 (fixtures-first strategy); the decoder ships against the published protocol evidence now.

**Exit criterion (ROADMAP):** decoder reproduces all documented metrics for every golden fixture.

## 2. Protocol background (research-verified)

Reference implementations (both byte-for-byte agreeing on every mapped value):

- **Duwaynef/MLM2PRO-BT-APP** (C#, MIT) — ships the written protocol spec `mlm2pro.md`
- **springbok/MLM2PRO-GSPro-Connector** (Python, GPL-3) — independent corroboration

Neither is copied. Both are reference-only: we re-implement from protocol *facts* (offsets, scales, constants), which satisfies the GPL-3 license as well as our own hygiene preference. The charter's original reference (`divotmaker/flighthook`) contains **no MLM2PRO BLE code** and is dropped as a reference for this milestone; `AGENTS.md` should be updated accordingly when M1 lands.

Key protocol facts driving this design:

- **Transport:** GATT notifications on a bonded device (not advertisements — no public advertisement decode exists). MEASUREMENT and EVENTS are the two characteristics carrying parseable device→client data in M1's scope.
- **Encryption:** all payloads except the initial auth request are AES-256-CBC with PKCS7 padding, a **fixed IV** (bytes `6D2E5213213204456F2C794810656D42`), and a 32-byte client-chosen session key (sent in the plaintext auth request). No chunking, CRC, or sequence numbers — each notification is one self-contained message. A 20-byte measurement travels as one 32-byte ciphertext notification.
- **MEASUREMENT payload** (decrypted, 20 bytes, little-endian):

| Offsets | Type        | Field            | Decode            |
|---------|-------------|------------------|-------------------|
| 0–1     | Int16 LE    | club head speed  | raw / 10 (m/s)    |
| 2–3     | Int16 LE    | ball speed       | raw / 10 (m/s)    |
| 4–5     | Int16 signed| HLA launch direction | raw / 10 °    |
| 6–7     | Int16 signed| VLA launch angle | raw / 10 °        |
| 8–9     | Int16 signed| spin axis        | raw / 10 °        |
| 10–11   | UInt16 LE   | total spin       | raw (rpm)         |
| 12–13   | UInt16 LE   | unknown1         | raw, uninterpreted|
| 14–15   | UInt16 LE   | unknown2         | raw, uninterpreted|
| 16–19   | —           | padding          | zeros             |

- **EVENTS payload** (decrypted): byte 0 = event code, byte 1 = sub-code. `0x00` shot happened, `0x01` processing, `0x02` ready, `0x03` battery (byte 1 = percent), `0x04` unknown, `0x05 0x00` misread, `0x05 0x01` disarmed.
- **Misread sentinel:** 20 zero bytes in place of a measurement.
- **One published sample** (decrypted hex): `44004F00E2FF0A01C8FFFC0705000A0000000000` → CHS 6.8 m/s, ball 7.9 m/s, HLA −3.0°, VLA 26.6°, spin axis −5.6°, spin 2044 rpm, unknown1=5, unknown2=10.
- GATT identifiers: service `DAF9B2A4-E4DB-4BE4-816D-298A050F25CD`; MEASUREMENT char `76830BCE-B9A7-4F69-AEAA-FD5B9F6B0965`; EVENTS char `02E525FD-7960-4EF0-BFB7-DE0F514518FF`.

A complete capture of the M4-relevant handshake, command formats, and event flow lives in the M1 brainstorm record; the M4 design will restate whatever it needs.

## 3. Decisions (user-approved during brainstorming)

1. **Fixtures first, captures in M4.** Seed golden fixtures from the published sample, synthetic vectors, and reproducible AES round-trips. Real shed captures join the fixture library once the M4 GATT client exists.
2. **Scope = payload + AES + events.** All three layers pure in `:core:ble` now. AES covers both directions (decrypt for notifications, encrypt for future command writes).
3. **SI units internally** — m/s, degrees, rpm. Display conversion (mph/yards) belongs to the M3+ UI layer, never the decoder.
4. **Layered structure** (option B from the approaches discussion): crypto, measurement parser, and event parser as standalone units plus a thin orchestrator. Chosen over a flat single-function decoder specifically so M4 can reuse `Mlm2proCrypto` for encrypting arm/disarm/config command writes.

## 4. Module layout

```
core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/
├── Mlm2proCrypto.kt        — AES-256-CBC + PKCS7, fixed IV, symmetric encrypt/decrypt
├── MeasurementParser.kt    — decrypted 20 bytes → BallDataResult
├── EventParser.kt          — decrypted event bytes → Mlm2proEvent
├── BallData.kt             — shot data model (SI units, full precision)
├── BallDataResult.kt       — sealed: Shot | Misread | Malformed
├── Mlm2proEvent.kt         — sealed: ShotDetected | Processing | Ready | Battery | MisreadAlert | Disarmed | Unknown
├── Characteristic.kt       — enum of the two M1-parseable notify characteristics (UUID-bearing)
├── Mlm2proMessage.kt       — sealed orchestrator result: Measurement | Event | Malformed | Unrecognized
└── Mlm2proDecoder.kt       — orchestrator: (characteristic, notification, key) → Mlm2proMessage
```

The M0 marker object `Mlm2proBle` and its smoke test are removed once real tests cover the module.

### Component contracts

- `Mlm2proCrypto.decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray`
  `Mlm2proCrypto.encrypt(plaintext: ByteArray, key: ByteArray): ByteArray`
  — pure functions over `javax.crypto`; fixed IV internal. Throws `IllegalArgumentException` on wrong key length (programmer error, fail fast); decryption failures (bad padding) propagate as exceptions to be caught by the orchestrator, which converts them to `Malformed`.
- `MeasurementParser.parse(payload: ByteArray): BallDataResult` — input is the decrypted payload. All-zero 20 bytes → `Misread`. Length ≠ 20 → `Malformed`.
- `EventParser.parse(payload: ByteArray): Mlm2proEvent?` — `null` only for an empty payload; any other byte string maps to a variant (known code or `Unknown`). Never throws.
- `Mlm2proDecoder.decode(characteristic: Characteristic, notification: ByteArray, key: ByteArray): Mlm2proMessage` — decrypts, routes by characteristic, dispatches to the right parser. The single entry point M4's GATT layer will call.

## 5. Data model

```kotlin
data class BallData(
    val clubHeadSpeed: Double,    // m/s
    val ballSpeed: Double,        // m/s
    val launchDirection: Double,  // HLA, degrees — device sign preserved
    val launchAngle: Double,     // VLA, degrees — device sign preserved
    val spinAxis: Double,         // degrees — device sign preserved
    val totalSpin: Int,           // rpm
    val unknown1: Int,           // raw offsets 12–13, uninterpreted
    val unknown2: Int,           // raw offsets 14–15, uninterpreted
)
```

- **No rounding anywhere in the decoder.** The references round to 2 dp for GSPro display; we keep full precision for M2's physics and round only at display time.
- **Signs are preserved, not interpreted.** The BLE payloads are signed Int16s; which direction positive HLA points and whether positive spin axis means fade or draw is **unverified until live captures** (M4). The decoder is an honest conduit; M2 physics treats these as opaque signed values until M4 settles the semantics.
- **Unknowns ride along raw.** Offsets 12–15 are unresolved by the reference implementations (suspected distances, disproven for the one sample). Keeping them raw lets M4 side-by-side captures decide their meaning (one hypothesis to test: club path, since the Rapsodo app displays it but no public decode maps it — see §9).

```kotlin
sealed interface BallDataResult {
    data class Shot(val data: BallData) : BallDataResult
    data object Misread : BallDataResult
    data class Malformed(val reason: String) : BallDataResult
}

sealed interface Mlm2proEvent {
    data object ShotDetected : Mlm2proEvent  // 0x00
    data object Processing : Mlm2proEvent    // 0x01
    data object Ready : Mlm2proEvent          // 0x02
    data class Battery(val percent: Int) : Mlm2proEvent  // 0x03
    data object MisreadAlert : Mlm2proEvent   // 0x05 0x00
    data object Disarmed : Mlm2proEvent       // 0x05 0x01
    data class Unknown(val code: Int, val subCode: Int?) : Mlm2proEvent  // incl. 0x04
}

sealed interface Mlm2proMessage {
    data class Measurement(val result: BallDataResult) : Mlm2proMessage
    data class Event(val event: Mlm2proEvent) : Mlm2proMessage
    data class Malformed(val reason: String) : Mlm2proMessage
    data class Unrecognized(val characteristicUuid: String) : Mlm2proMessage
}
```

`Characteristic` is an enum with exactly the two M1-parseable notify characteristics (`MEASUREMENT`, `EVENTS`), each carrying its UUID string. Other characteristics (WRITE_RESPONSE, HEARTBEAT, and any future discoveries) are not decoded in M1; if handed to the decoder they yield `Unrecognized`.

## 6. Error handling policy

- **Runtime data problems never throw.** Empty payloads, wrong lengths, corrupt ciphertext, undocumented event codes — all produce sealed values (`Malformed`, `Unknown`) a shed session can log and skip. The decoder must not be able to crash a live practice session with bad input.
- **Programmer errors throw.** Wrong key length (32 bytes required) → `IllegalArgumentException` at the crypto boundary, caught in development not at runtime.

## 7. Testing strategy

Golden fixtures in `core/ble/src/test/resources/golden/` — one self-contained `.properties` file per shot (`java.util.Properties`, no new dependency):

```properties
# golden/measurement-001.properties
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

Seed set:

- `measurement-001` — the published sample
- `misread-001` — all-zero payload → `Misread`
- malformed-length cases (empty, 19 bytes, 21 bytes)
- AES golden vectors — fixed test key + pinned plaintext/ciphertext pair (guards the wire format against accidental refactor breakage)

**Table-driven:** the parser test scans `golden/` and registers one parameterized case per file. Dropping a new `.properties` file into the directory is the whole process for adding M4 shed captures as regression tests — no test-code changes.

Per-layer matrix:

- **Crypto** — round-trip (`decrypt(encrypt(p)) == p`); pinned golden ciphertext; wrong key length throws; padding failures surface as `Malformed` via the decoder
- **MeasurementParser** — all golden fixtures; synthetic signed-value vectors (negative HLA, negative spin axis — the unsigned-read corruption bug class the Python reference explicitly warns about); boundary raws (0, ±32767); misread; malformed lengths
- **EventParser** — every known code maps to the right variant; unknown codes → `Unknown`; battery percent passthrough; empty payload → null
- **Decoder** — routing matrix: MEASUREMENT happy path end-to-end (ciphertext in → `Shot` out), EVENTS routing, decrypt failure → `Malformed`, non-decodable input on a known characteristic → `Malformed`

TDD red/green per component per the charter. CI requires no changes — existing `gradlew build` runs it all. JUnit 4 Parameterized is available already (oracle M0 follow-up: no new dependency needed).

## 8. Out of scope (M1)

- Live BLE: scanning, GATT connection, the auth handshake, cloud token fetch (with its charter-tension), heartbeat discipline, arm/disarm/config writes — all M4
- Parsing WRITE_RESPONSE, HEARTBEAT, or any client→device command beyond the crypto primitive
- Display units (mph/yards) — M3+
- Interpretation of sign semantics — deferred to M4 captures by design

## 9. Verify-against-live-capture list (M4)

Tracked open questions the raw-passthrough design is built to answer via side-by-side captures:

1. HLA / spin-axis **sign meaning** (fade vs draw, left vs right)
2. Whether speeds are **always m/s** regardless of app unit settings
3. Meaning of **unknown1/unknown2** (offsets 12–15)
4. **Club path** — the Rapsodo app displays it; test whether either unknown byte tracks it; also dump full notifications from all characteristics (including the "advanced discovery" `MLM2_BT_` mode) and diff against app-displayed values
5. The `0x04` event code
6. The `0x0D` vs `0x18` arm command families (springbok uses only `0x0D`)
7. Ball-type byte conflict in the config write prose vs table (`01 01` vs `01 02` for range vs Rapsodo ball)
