# M4b: Bench Connection Checkout — Design Spec

**Status:** approved design (all sections user-confirmed)
**Date:** 2026-09-20
**Supersedes:** none (extends spec `2026-09-20-m4a-demo-pipeline-design.md` §6 Phase B)

## 1. Goal & Exit Criteria

Take the M4a demo pipeline to a real MLM2PRO at a desk ("bench") and prove the live BLE connection end-to-end, capturing real notification bytes as golden fixtures.

**Exit criteria** (from M4a spec §6 Phase B):
1. Scanner finds the `MLM2-` device powered at the desk.
2. Full handshake reaches ARMED: subscribe ×4 → auth write → WRITE_RESPONSE (userId) → token fetch via embedded Secret → config ×2 → arm → ARMED (device LED green).
3. StatusStrip shows real ConnectionState states (Disconnected/Scanning/Connecting/Handshaking/Armed/Faulted) instead of static DEMO MODE.
4. Captured notification bytes (battery events, hand-wave misreads) exported as golden `.properties` fixtures with `firmware=` property and a `type=event` branch, plus a wrong-key test (M1 oracle follow-ups).

Honest-partial exit is acceptable (e.g., scanner + connect + auth green, arm pending) with captures exported and follow-ups recorded.

## 2. Background & Decisions

M4a shipped the full `:core:connect` BLE client, JVM-tested against pinned wire vectors but never device-validated (by design). The oracle's M4a review surfaced three Important findings that live in that unvalidated scope — the arm/disarm command byte length (pinned 8-byte vs spec'd 7-byte — only the 8-byte form reproduces the verified ciphertext), the missing CCCD (0x2902) descriptor writes and GATT operation serialization, and the API 33+ only `onCharacteristicChanged` override. lib-2's Awesome Golf research further corrected the Secret assumption: the `Secret` header value is a hidden constant shipped AES-encrypted inside community connector apps (public encrypted hex in Duwaynef's `WebApiClient.cs`), not something a user types.

**User-confirmed decisions:**
1. **Secret sourcing — Fetch + embed.** Orchestrator fetches the AES-encrypted Secret blob from the public Duwaynef repo, embeds the encrypted bytes in `:core:connect`, decrypts at runtime via the existing `Mlm2proCrypto`. The plaintext value NEVER appears in docs, commits, or logs.
2. **Sequence — Fixes first.** Pre-bench code fixes land as tested commits before the bench session; the bench session itself is an interactive user-gated debug loop.
3. **Settings screen — guidance text only.** The Secret field is replaced entirely by Awesome Golf authorization instructions (Rapsodo app → Play → Simulation → 3rd Party Apps → Awesome Golf → Authenticate Now; re-authorize every 24h).
4. **Capture UX — in-app capture mode.** A debug capture mode logs raw notification bytes (encrypted + decrypted + UUID + timestamp) and exports them as golden `.properties` fixtures via share/adb-pull.

**Approach A (chosen):** a single-milestone bench loop on one `m4b-bench-connection` branch — pre-bench fixes as tested commits first, then the live bench session at the desk iterating connect→observe→fix→reinstall→retry with in-app captures. (Rejected: B, two separate milestones with extra ceremony; C, a bench-first diagnostic spike that would just re-discover the known suspects.)

## 3. Pre-Bench Fixes (land as tested commits before the session)

- **(a) Serialized one-op-at-a-time GATT operation queue.** All GATT writes (subscribe ×4 → auth → config ×2 → heartbeat) flow through a serialized queue driven by `onDescriptorWrite`/`onCharacteristicWrite` callbacks (Android permits one in-flight GATT op). `subscribe()` writes the CCCD (0x2902) `ENABLE_NOTIFICATION` descriptor after `setCharacteristicNotification` — without it most peripherals never notify.
- **(b) Callback compat.** Override BOTH `onCharacteristicChanged` signatures — the 2-arg deprecated variant (API 31-32) and the 3-arg variant (API 33+) — both forwarding to one handler. `minSdk` is 31; the current 3-arg-only override silently drops notifications on Android 12/12L.
- **(c) Arm/disarm + live status.** `arm()`/`disarm()`/`connectionState` exposed on `Mlm2proGattClient`; `ConnectionState.Armed` becomes reachable; StatusStrip drives from the live connection state (existing BLE-green/red colors); the DEMO toggle is retained as a fallback.
- **(d) Runtime permission flow.** The CONNECT entry point checks `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` grants, requests them via the system dialog if ungranted, and shows a guidance line on denial (the M4a scanner silently emits nothing when ungranted).
- **(e) Embedded Secret wired.** The encrypted blob (fetched from the public Duwaynef repo `WebApiClient.cs` `_secretEnc` hex) is embedded in `:core:connect` and decrypted at runtime via the existing `Mlm2proCrypto`. Flow: userId (from auth WRITE_RESPONSE) → `GET https://mlm.rapsodo.com/api/simulator/user/{id}` with the `Secret` header → `TokenResult`. The plaintext Secret never appears in docs, commits, or logs.
- **(f) Settings screen → guidance text.** The Secret field is replaced by Awesome Golf authorization instructions: Rapsodo app → Play → Simulation → 3rd Party Apps → Awesome Golf → Authenticate Now, with the 24-hour re-authorization note.
- **(g) Capture mode.** A debug capture mode logs raw notification bytes (encrypted + decrypted + UUID + timestamp) and exports golden `.properties` fixtures with a `firmware=` property and a `type=event` branch, plus a wrong-key test (M1 oracle follow-ups). Captures feed the existing M1 `GoldenFixturesTest` harness drop-file pattern.

## 4. Bench Session

**Desk setup:** MLM2PRO powered, Awesome Golf authorized (<24h old), tablet TB373FU with the new build installed.

**Session checklist:** permission grant → scanner finds the `MLM2-` device (RSSI list) → tap to connect (system pairing on first connect) → GATT subscribes → auth write → WRITE_RESPONSE with userId arrives → token fetch via embedded Secret → config ×2 (200 ms gap) → ARMED — device LED green → heartbeat at 2 s cadence. StatusStrip shows live states throughout. A battery event arrives → captured. A hand-wave over the MLM2PRO → misread event arrives → captured. Both exported as golden `.properties` fixtures (firmware version from device info, type=event branch).

**Known failure modes — first-checks ladder:**
1. Arm/disarm 7-vs-8-byte length divergence (pinned 8-byte with trailing 0x00 vs spec's 7-byte) — if arm fails, try the 7-byte variant first.
2. CCCD/serialization — if no WRITE_RESPONSE arrives, log `onDescriptorWrite` + queue state.
3. Token fetch failures — SSL/anti-virus interception, or no internet during the session.
4. 24h auth expiry mid-session — device LED stays red; re-authenticate in the Rapsodo app and retry (power-cycle the MLM2PRO if it was idle-red).

**Fix loop:** adb reinstall per fix (orchestrator-driven, JAVA_HOME convention); StatusStrip state + logcat + in-app capture for diagnosis. The session ends when the exit criteria are met, or as an honest partial with captures exported and follow-ups recorded.

## 5. Testing

- **JVM unit tests:** serialization queue ordering/routing; callback forwarding (both variants); arm/disarm state transitions; `TokenResult` handling.
- **Assembly checks:** module `assembleDebug` green; existing 142-test census unaffected.
- **Bench session:** user-gated interactive loop with on-device acceptance (§4 checklist).

## 6. Out of Scope

M4c shed capture (real MEASUREMENT payloads, carry-vs-Rapsodo validation), follow-cam replay, dispersion-history cloud on POV, hole mode, physical trajectory arcs (real-arc rendering).

## 7. Sources

M4a spec (`2026-09-20-m4a-demo-pipeline-design.md`), ora-1's M4a review findings, lib-2's Awesome Golf research, Duwaynef `MLM2PRO-BT-APP` repo, springbok `MLM2PRO-GSPro-Connector` repo.
