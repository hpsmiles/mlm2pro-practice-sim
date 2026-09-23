# HANDOVER — M4c shed capture (golf sim)

Written 2026-09-20 after M4b completion. **Read this fully before starting.**
Self-contained: project state, environment, hard-won protocol knowledge, and
the M4c task definition. You (laptop session) will do the shed capture with a
real ball and extend the golden-fixture base with live shot measurements.

---

## 1. Project state

- Repo: `hpsmiles/mlm2pro-practice-sim`. Branch: **master** at `4290dcf`
  (merge of PR #6). All feature branches merged and deleted. Tree clean
  apart from this doc.
- Milestones: **M0–M4b COMPLETE.** M4c (shed capture) is next. M4d+
  afterwards per the master plan (see `docs/plans/` if present).
- App status: installs and runs on the Lenovo TB373FU tablet (serial
  HA2C96BN). Full BLE chain works live:
  scan → connect → subscribe → auth → token → config ×2 → **auto-ARM 500 ms
  after READY** → green LED → EVENTS notifications (battery every ~30 s).
- Test census: **168** (core:ble 39 / core:connect 47 / app 8 /
  core:designsystem 14 / core:physics 65). Full build:
  `.\gradlew.bat build`.

## 2. Environment conventions (Windows dev machine)

- **JDK is not on PATH.** Every Gradle command needs, in the same command:
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` first.
- adb: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" ...`
- PowerShell **5.1**: no `&&`. Multi-line `git commit -m` fails — write the
  message to a file (e.g. `C:\Users\harry\AppData\Local\Temp\opencode\commit-msg.txt`)
  and `git commit -F`. Here-string args also fail.
- `--tests` filter is broken by config cache ("Unknown command-line option
  '--tests'") — run whole module suites (`.\gradlew.bat :core:ble:test`).
- Test result XMLs: `core\<module>\build\test-results\testDebugUnitTest\`.
- Install on tablet: `.\gradlew.bat :app:installDebug`
  (device attached via USB). Launch:
  `adb shell am start -n com.hpsmiles.golfsim/.MainActivity`.
- App files dir access: `adb exec-out run-as com.hpsmiles.golfsim cat files/capture.properties`.
- Clear logcat before a session: `adb logcat -c`; read bench logs:
  `adb logcat -d -s BenchGatt` (also BenchToken, CaptureExport).

## 3. Module map (what lives where)

| Path | Contents |
|---|---|
| `app/...range/AppRoot.kt` | Connect flow UI glue, scan→connect coroutine, ARM/STANDBY strip. `benchSessionKey()` = `ByteArray(32){it.toByte()}` deterministic bench key. Connect requests are gated (must be `Disconnected`/`Faulted`). |
| `app/...range/RangeScreen.kt` | Range UI, CONNECT button (requests runtime BLE perms first). |
| `app/...settings/SettingsScreen.kt` | Rapsodo auth guidance + DEBUG capture card: CAPTURE ON/OFF, live count (polled 250 ms while ON), EXPORT writes `filesDir/capture.properties` + share intent. |
| `core/connect/.../Mlm2proGattClient.kt` | GATT client: connect guard, service discovery, CCCD subscribes, serial `GattOpQueue`, 500 ms ticker (heartbeats 2 s, resubscribe 20 s, auto-ARM reflect), notification routing, capture recording, `cleanupConnection` teardown. TAG `BenchGatt`. |
| `core/connect/.../HandshakeSequencer.kt` | Pure protocol state machine: AUTH_SENT→TOKEN_WAIT→CONFIG×2 (200 ms apart)→READY→(auto-ARM +500 ms)→ARMED/DISARMED. |
| `core/connect/.../CommandEncoder.kt` | Protocol bytes: AUTH (38 B incl. LE userId), CONFIGURE (16 B: `01 02 00 00` + LE16 pressure `7D C8` + LE16 temp `DC 05` + **LE32 token**), ARM/DISARM, **heartbeat = PLAINTEXT single byte `0x01`** (NOT encrypted — encrypted heartbeats kill the link at ~15 s). |
| `core/connect/.../RapsodoTokenProvider.kt` | Web API `https://mlm.rapsodo.com/api/simulator/user/{id}` with Secret header; response `{"success":true,"user":{"id":…,"token":"1043255814","expireDate":…}}` (token is a quoted numeric string). |
| `core/connect/.../CaptureLog.kt` | In-memory capture → `.properties` export (`firmware=`, per-event `uuid/encryptedHex/decryptedHex`, `wrongKey` vector). |
| `core/ble/.../Mlm2proDecoder.kt` + `EventParser`/`MeasurementParser`/`Mlm2proCrypto` | Pure decode: decrypt (AES, deterministic for a given key) → route by characteristic → parse. EVENTS first byte: `00` shot detected, `01` processing, `02` ready, `03` battery (byte 2 = percent), `05 00` misread, `05 01` disarmed. MEASUREMENT → `BallData`. |
| `core/ble/.../Characteristic.kt` | Only MEASUREMENT + EVENTS have enum entries; others route via UUID string in the GATT client. |
| `core/physics/...` | Ball-flight ODE (M2). Sign conventions **unverified against live data — that's M4c work**. |
| `core/ble/src/test/resources/golden/` | Golden `.properties` fixtures: measurement-001, misread-001, malformed-length 001-003, battery-event 001-003. Harness: `GoldenFixturesTest` (`type=measurement|misread|malformed|event-battery`). |
| `core/connect/src/test/resources/bench/` | `m4b-bench-capture-2026-09-20.properties` — verbatim live capture; `BenchCaptureRoundtripTest` pins ciphertext→bench-key decrypt→Battery(85..83). |

## 4. Protocol facts learned the hard way (M4b bench session)

Full history is in the merged commits (`1846962`, `37ae74e`, `45284f0`,
`a38b0af`, `1f31e08`, `664fe65`, `b432b24`, `9af8ded`). Key facts:

1. `connectGatt()` does NOT auto-discover services — call `gatt.discoverServices()`
   from `onConnectionStateChange(STATE_CONNECTED)`.
2. Heartbeat must be **plaintext `0x01`** to the heartbeat characteristic
   (`ef6a028e-…`), every 2 s. Encrypted heartbeats → device terminates at a
   constant ~15 s (status 19 = peer terminate).
3. CONFIGURE token is **little-endian** (whole 16 B frame is LE).
4. Device answers CONFIGURE with a second WRITE_RESPONSE first byte `0x00`
   ("accepted"). WRITE_RESPONSE char: `cfbbcb0d-…`.
5. Token response shape (see §3 RapsodoTokenProvider). INTERNET permission
   is in the manifest now.
6. Auto-ARM mirrors the reference `Task.Delay(500)`; user disarm is sticky
   (auto-arm never re-fires from DISARMED).
7. `connect()` has a re-entrancy guard: refuses while a link is live
   (double-tap used to fork a second link; the MLM2PRO accepts two
   simultaneous connections and every notification arrived twice).
8. Battery events (`03 percent`) arrive on EVENTS every ~30 s; observed
   0x55→0x52 stepping down over minutes (semantics beyond byte value
   unverified — could be % or ADC; treat as opaque pin for now).
9. Bench ciphertext pins use the deterministic bench key; goldens with
   ciphertext can be decrypted in tests via
   `Mlm2proCrypto.decrypt(bytes, ByteArray(32){it.toByte()})`.
10. Operation on device (pre-session): user must keep **Awesome Golf
    authorization current** in the Rapsodo app (Play → Simulation → 3rd
    Party Apps → Authenticate Now, valid 24 h; session token ~3 h). The
    MLM2PRO must NOT be actively connected in the Rapsodo app while our
    app scans (it won't advertise). Wake the device before scanning.

## 5. M4c task — shed capture with the real ball

**Goal:** validate the MEASUREMENT pipeline end-to-end with true ball
flight, and pin real golden fixtures including live misread events + live
measurement payloads; determine HLA/launch-direction sign conventions
against observable ball flight.

### Pre-flight
1. Confirm repo clean on master, `.\gradlew.bat build` green.
2. Install: `.\gradlew.bat :app:installDebug` with the tablet attached.
3. User re-authenticates Awesome Golf if >24 h since last auth.

### Capture drill (in the shed, ball + net)
1. `adb logcat -c`; launch app; CONNECT (single tap; auto-arms).
2. Settings → CAPTURE ON.
3. Hit a bucket: mix of clean shots, deliberate mishits, and at least one
   obvious duff/chunk to fish for a live `05 00` misread event. Watch the
   count climb (updates every 250 ms).
4. EXPORT → `adb exec-out run-as com.hpsmiles.golfsim cat files/capture.properties`
   (save a local copy after each batch — EXPORT overwrites).
5. Update `scan snapshot` via logcat: `-s BenchGatt` shows decodes and
   write activity; MEASUREMENT notifications are recorded but not logged
   verbosely.

### Post-capture engineering
1. Drop captured entries into golden fixtures:
   - MEASUREMENT payloads → `type=measurement` files (schema already
     supports clubHeadSpeed/ballSpeed/launchDirection/launchAngle/
     spinAxis/totalSpin/unknown1/unknown2 — fill from `MeasurementParser`
     results).
   - Live misread `05 00` → new `type=misread` (or extend harness if the
     payload differs from the all-zero pin — do NOT assume; pin what the
     device actually sends).
   - If shapes differ from the M1 pins, the decoder needs a diffPass first —
     update `MeasurementParser` with tests red→green before touching more.
2. **Sign-convention verification** (M1 spec §9 open question): hit known
   fades vs draws + known high vs low launches; compare parsed
   `launchDirection`/`spinAxis` signs and `HLA` semantics against observed
   flight. Fix `BallData.kt` comment BLOCK and `LaunchConditions.kt`
   docs; adjust parser mapping if inverted (TDD: write the failing pin
   from capture first).
3. Add misread-filtering behavior requirements to the M4d scope notes
   (bag mapping needs duff/duff-filtering; the M4b misread sentinel +
   live capture are the raw material).

### Acceptance (M4c done when)
- [x] Live MEASUREMENT notifications decoded to sane BallData (spot-check
      ball speeds vs expectations for the club used)
- [x] Golden fixtures: ≥1 live measurement + ≥1 live misread committed
- [x] Sign conventions resolved and documented (or explicitly deferred
      with evidence for why undecided)
- [x] Full build census green; PR merged with bench box ticked
      (follow the M4b PR pattern: branch, PR, boxes, merge)

## 5b. M4c session results (2026-09-23)

Two live batches captured on-device (8-iron, Lenovo tablet, session ~16:40–17:00):
`batch1` = 5 warm-up shots (sane 8i numbers: ball speed 98.9–107.0 mph, smash
1.21–1.28, VLA 16–23°, spin 5.9k–8.2k rpm). `batch2` = 4 shaped shots (fade,
punch, high, draw) + 1 mishit, saved as
`core/ble/src/test/resources/golden/mlm2pro-live-shapetest-2026-09-23.properties`
(+ per-event fixtures, pinned by `LiveShapetestCaptureTest`). Census
178 tests green (ble 39→51).

**Sign conventions verified** (user-observed flight vs bytes, moderate
confidence — net made sighting imprecise, but all four shots are mutually
consistent with the TrackMan convention):
- HLA / `launchDirection`: negative = left of target, positive = right.
  Fade started left (−6.9°), draw started righter (−1.8°).
- Spin axis: positive = right curve (fade, +11.3°), negative = left curve
  (draw, −18.6°). No parser change needed — mapping was already correct;
  `BallData.kt` + `LaunchConditions.kt` docs updated with
  [Verified on-device] markers.
- **Live misread captured in BOTH forms**: EVENTS `05 00` frame (event-010)
  AND all-zero 20-byte MEASUREMENT payload (event-027). Both pinned as
  golden fixtures and asserted in `LiveShapetestCaptureTest`.

### M4d scope notes: misread-filtering requirements

Raw material for bag mapping (Feature 1): real duffs arrive as either the
EVENTS `05 00` misread event or the all-zero MEASUREMENT sentinel (confirmed
live, both forms in one batch). Requirements for M4d:

1. A shot flow that ends in either misread form must be recorded as a
   **non-shot** in any session/bag-mapping aggregation — never as a BallData
   row with zeros.
2. Misreads must NOT re-trigger auto-ARM misbehavior: after `05 00` the link
   returns to READY and auto-ARM fires +500 ms as usual (observed live —
   shot cadence continued normally).
3. Duff *filtering* (suspect metrics on otherwise-plausible shots, e.g.
   anomalous smash vs. the club's history) is a separate, later heuristic —
   out of scope until gapping data exists; do not conflate with the
   protocol-level misread sentinel.
4. UI should surface misreads as a dismissible "no read" pill, not silently
   drop them (player must know the monitor didn't see the ball).

## 6. Known gaps / open questions

- ~~**Misread live event still unpinned**~~ **RESOLVED 2026-09-23**: real
  mishit produced both `05 00` and all-zero MEASUREMENT — both pinned as
  golden fixtures (see section 5b).
- **`firmware=` in capture exports** is a placeholder (`unknown-M4b-bench`)
  — no channel known to read device firmware; update if discovered.
- Battery byte stepping (85→82 over minutes) semantics unverified.
- The `handleNotification` write-response path double-checks state via
  `AUTH_SENT` guard; late config-accept WRITE_RESPONSEs are safely
  ignored.
- CaptureLog records only when `enabled`; toggling OFF does not clear
  entries (re-ON continues the same list). EXPORT rewrites the whole file.

## 7. Process conventions (unchanged)

- TDD for all pure-logic changes (superpowers: test-driven-development).
  GATT/UI glue is bench-validated (module split decision).
- Systematic debugging: root cause before fixes (see M4b history — every
  bench symptom traced to a specific protocol/code fault before patching).
- Do not add cloud/PC dependencies (single-device constraint, AGENTS.md).
- Commit style: `fix(scope): …` / `test(scope): …`, message files for
  multi-line on PS 5.1.

**Good luck in the shed. The connect layer is solid — trust the ARMED +
green LED state, and capture everything.**
