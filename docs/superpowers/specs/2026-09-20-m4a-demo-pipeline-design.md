# M4a Demo Pipeline Design Spec

**Status:** approved design, pre-implementation
**Date:** 2026-09-20
**Branch:** m4a-demo-pipeline (off master @ 5463890)
**Scope phase:** M4 Part A (pre-capture) of the Clean Practice Environment

## 1. Goal

Build the complete M4 pipeline — shot source → physics → range view — with the BLE GATT client fully implemented but driven by a **synthetic demo-shot source**, so the vertical slice runs on the tablet today with no device present.

**Exit criteria (Phase A):**

1. Full `gradlew build` green across all modules (new tests included); CI green.
2. On the Lenovo tablet: user fires demo shots and sees — tracer animating at real speed (1x/2x/4x toggle), **metrics rendered instantly** (never gated by animation), top-down analytics toggle showing landing + history, DEMO badge visible, Settings storing the Rapsodo Secret that persists across app restart.
3. All BLE client code (scanner, GATT subscribe, handshake sequencer, token provider, command encoder) committed and JVM-unit-tested — **not yet validated against the device** (Phase B/C).

## 2. Background & Decisions

M4 (Clean Practice Environment) was split by user decision: build first, validate on-device later. Three phases:

- **Phase A (this spec)** — build demo pipeline + full BLE client code, unit-test only.
- **Phase B — bench connection checkout**: indoors, device on desk; exercised end-to-end without hitting balls (scanner → pair → auth → token fetch → config → heartbeat → arm → events; hand-wave may yield misread fixtures).
- **Phase C — shed capture**: real MEASUREMENT payloads, golden fixtures, physics validation vs the Rapsodo app side-by-side.

User-confirmed decisions:

1. **Demo-shot pipeline** as Part A end state (synthetic shots through the real pipeline).
2. **Token workflow**: app fetches the ~3h token from `mlm.rapsodo.com/api/simulator/user/{id}` with the user's Secret (stored once in tablet Settings; internet needed at session start). Prerequisites surfaced to the user: Awesome Golf third-party access enabled in the Rapsodo app, and the user Secret.
3. **Phase A UI scope**: player-POV canvas primary + top-down analytics toggle; follow-cam replay, dispersion-history cloud, and hole mode deferred until real data exists. Real-speed playback with 2x/4x toggle; instant metrics regardless of animation (M3 decision).
4. **Demo shots**: manual fire button, randomized-but-plausible launch conditions cycling clubs (seeded RNG), visible DEMO badge.
5. **Module structure**: new `:core:connect` Android library via the golf-android-library convention plugin; `ShotSource` + `DemoShotSource` added to pure `:core:ble`; range UI lives directly in `:app` (feature-module split deferred to M5, YAGNI).

## 3. Architecture

Pipeline:

```
ShotSource (DemoShotSource now / BLE source Phase B-C)   [:core:ble]
  → emits BallData (M1 types, unchanged)
  → :app session wiring maps BallData → LaunchConditions (field-for-field, no conversion drift — m/s, degrees, rpm)
  → BallFlightEngine.simulate()                           [:core:physics]
  → ShotResult + trajectory samples (deterministic re-solve)
  → RangeScreen (:app): PovRangeCanvas (primary) ⇄ TopDownCanvas (analytics)
    + instant metric chips/rows + StatusStrip + playback at 1x/2x/4x
```

**Modules:**

- `:core:ble` gains **only**: `ShotSource` interface + `DemoShotSource` (seeded RNG, club-cycling plausible launch conditions, JVM-pure and deterministic for tests). M1 decoder stack untouched.
- `:core:connect` — **new** Android library (convention plugin): `Mlm2proScanner`, `Mlm2proGattClient`, `HandshakeSequencer` (pure state machine, JVM-testable), `RapsodoTokenProvider` (HttpURLConnection, zero new deps), `CommandEncoder` (uses `Mlm2proCrypto` from `:core:ble`). Ships in Phase A, exercised only by tests until Phase B.
- `:app` — `RangeScreen` replaces the M3 showcase; minimal `SettingsScreen` (Secret in `SharedPreferences`); BLE permissions in the manifest. Depends on `:core:ble`, `:core:physics`, `:core:designsystem`, `:core:connect`.
- `:core:physics`, `:core:designsystem` — unchanged.

**Key property:** the UI never knows where shots come from. Swapping `DemoShotSource` for the BLE source in Phase B/C is wiring only.

## 4. :core:connect — BLE Client

**HandshakeSequencer** — pure Kotlin state machine, zero Android imports. Inputs: `onNotification(characteristic, payload)`, `onTokenFetched(token)`. Outputs: `WriteCommand(characteristic, plaintext)` (encrypted/sent by the GATT layer). States:

```
IDLE → SUBSCRIBED (×4) → AUTH_SENT → AUTHED (WRITE_RESPONSE 0x02, userId captured)
    → TOKEN_WAIT → CONFIG_WRITE_1 → (200ms) → CONFIG_WRITE_2
    → READY → ARMED ⇄ DISARMED (arm 010D0001000000 / disarm 010D0000000000, on demand)
    heartbeat [0x01] every 2s while connected
```

All timing (200ms config gap, heartbeat cadence, 20s notify-resubscribe) via an injected clock/scheduler interface — virtual time in JVM tests, no sleeps.

**Mlm2proScanner** — scans for `MLM2-` prefixed device names; `Flow<Mlm2proDevice>`; thin Android wrapper.

**Mlm2proGattClient** — bonded connect; subscribes to the 4 notify characteristics (EVENTS, HEARTBEAT, MEASUREMENT, WRITE_RESPONSE); routes payloads through the M1 `Mlm2proDecoder` with the session key; feeds the sequencer; executes writes via `CommandEncoder`. Exposes a sealed `ConnectionState` flow (Disconnected / Connecting / Handshaking / Armed / Faulted(reason)) rendered in the StatusStrip.

**RapsodoTokenProvider** — `fetch(userId, secret) → TokenResult` via `HttpURLConnection GET` on `mlm.rapsodo.com/api/simulator/user/{id}` with the `Secret` header; ~3h expiry with re-fetch on sequencer request; interface-based for fake injection.

**Protocol facts** (from M1 spec / reverse-engineering references): pair as `MLM2-<serial>`; service DAF9B2A4-E4DB-4BE4-816D-298A050F25CD; config write ×2 with 200ms gap; event codes 0x00 shot, 0x01 processing, 0x02 ready, 0x03 battery, 0x05 misread/disarmed; putts do not flow over MEASUREMENT.

**Test strategy:** scripted-sequencer tests feed exact notification bytes (real hex where available) through the real decoder; assert exact write sequences (including encrypted bytes pinned via M1 crypto constants), state transitions, timing gaps via virtual clock, and every failure path (auth rejected, token fetch failed, heartbeat timeout, disconnect mid-handshake).

## 5. Range UI (:app)

**PovProjector** — pure Kotlin object, JVM-testable, no Android types. Input `(x, y, z)` world metres (x lateral +right, y down-range, z up) → screen `(u, v, scale)` via a fixed camera at origin, height 1.7 m, looking down-range (standard pinhole):

```
u = f · x / y        v = f · (camH − z) / y        scale = f / y
```

Bounded and deterministic (clips points behind the camera). Pinned unit tests: horizon line v-position; distance bands 50/100/150/200 m at exact v positions; target ovals as ground ellipses; tracer apex projection (30 m apex at 150 m out culminates near horizon); landing-pulse position; behind-camera clipping. Visual reference: the M3-approved range-view mock values.

**RangeScreen** (replaces the M3 showcase; reuses the M3 kit):

- **NavRail** left — RANGE selected; SETTINGS opens the Secret screen.
- **PovRangeCanvas** (center, primary) — ground gradient, distance bands, teal target ovals, amber tracer + landing pulse for the current shot, played at real duration (playtime = solved flightTimeSec; toggle 1x/2x/4x).
- **Top-down toggle chip** (top-right) — switches to the analytics canvas: range grid, dispersion-history dots (teal, faded), last-shot landing (amber).
- **Right panel** — `SectionCard`s with `MetricChip`s (carry/total/ball/spin/launch/axis; metres + mph per the M3 units decision) and session `MetricRow`s (avg/σ per club).
- **StatusStrip** (bottom) — Phase A shows DEMO + FIRE button (amber accent); Phase B/C swaps in the live BLE `ConnectionState`.
- **Instant-metrics rule (M3):** chips/rows/landing-dot render the moment a shot is solved — tracer animation never gates them.
- **DEMO badge** — persistent marker top-left while `DemoShotSource` is active.
- Session state (shot list, aggregates) in-memory only — persistence is M5.
- **SettingsScreen** — single text field for the Rapsodo Secret → `SharedPreferences`; nothing else.

## 6. Testing & Phase Gates

**Unit tests (all JVM, no device):**

1. `HandshakeSequencer` scripted-protocol tests — captured/plausible notification payloads through the real `Mlm2proDecoder`; assert exact write sequences, state transitions, timing gaps via virtual clock (200 ms config gap, 2 s heartbeat, 20 s resubscribe), and failure paths (auth reject, token fail, heartbeat timeout, disconnect mid-handshake).
2. `DemoShotSource` determinism — same seed → identical shot sequence; plausible per-club ranges (speed/spin/VLA bounds from the tour table).
3. `PovProjector` pins — as listed in §5.
4. `CommandEncoder` pins — encrypted bytes for arm/disarm/config via `Mlm2proCrypto` with a pinned key → exact ciphertext hex (M1-style wire-format pins).

CI unchanged (`gradlew build`; no device tests).

**Phase A completion gate (M0-style tablet demo gate):** user fires demo shots on the Lenovo tablet — tracer animates at real speed (1x/2x/4x), metrics appear instantly, top-down toggle shows landing, DEMO badge visible, Settings Secret persists across restart.

**Phase B — bench connection checkout, exit criteria:** scanner finds the MLM2- device on the desk; full handshake reaches ARMED (auth → token fetch via stored Secret → config ×2 → arm); StatusStrip shows BLE states; device-side LED/heartbeat behaviors as expected; any captured notification bytes (battery events, hand-wave misreads) exported as new `.properties` golden fixtures (firmware= property + `type=event` branch, per M1 oracle follow-ups).

**Phase C — shed capture, exit criteria:** every shot's real MEASUREMENT decoded; carry within tolerance vs the Rapsodo app side-by-side (M2 calibration validation); golden fixture library grows with real captures; ROADMAP M4 exit wording amended to instant-metrics semantics.

## 7. Out of Scope (recorded)

Follow-cam replay; dispersion-history cloud on the POV canvas; hole-mode UI (tokens exist); navigation structure beyond RANGE/SETTINGS stubs; session persistence (M5); real-time putting (out of protocol scope); :feature module split (M5).

## 8. Sources

- M1 BLE decoder spec + implementation (byte protocol, crypto, fixtures) — `docs/superpowers/specs/2026-09-20-m1-ble-decoder-design.md`.
- M2 ball-flight spec (ShotResult, LaunchConditions, BallFlightEngine) — `docs/superpowers/specs/2026-09-20-m2-ball-flight-design.md`.
- M3 design system spec (tokens, kit, units, instant-metrics decision) — `docs/superpowers/specs/2026-09-20-m3-design-system-design.md`.
- Range-view visual reference: `.superpowers/brainstorm/2003-1789875726/content/range-view.html` (approved mockup values).

