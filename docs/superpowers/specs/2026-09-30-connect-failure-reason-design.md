# Connect Failure Reason on the Status Strip — Design

Date: 2026-09-30
Status: Approved (brainstorm decisions 2026-09-30)
Follows: `2026-09-30-auto-connect-boot-design.md`

## 1. Problem

When the MLM2PRO cannot connect, the bottom status strip shows only
`BLE DISCONNECTED` or `BLE FAULTED - SEE CAPTURE`. The actual cause already
exists in the system — `ConnectionState.Faulted(reason)` carries a raw string
(`gatt 133`, `service discovery 8`, `token fetch: HTTP 503`, …) and the
auto-connect scan timeout is logged — but the strip throws all of it away.
The user wants an outline of *why* the connection failed, on the bottom bar.

## 2. Decisions

1. **One-line, combined label** — the reason is appended to the existing
   status text; no second line in the strip.
2. **Shown during retries and after final failure** — e.g.
   `RETRYING (2/3)… NO MONITOR FOUND` while the cycle is running, and the
   reason remains on the final `BLE DISCONNECTED` label after GiveUp.
3. **Approach 1 — capture at the wiring layer, map for display in the app.**
   `core/connect` is untouched: raw fault strings stay load-bearing for
   logcat, capture exports, and pinned tests. The app records the last
   failure cause and translates it for display.
4. **`SEE CAPTURE` is dropped from the fault label.** The reason text replaces
   it; raw codes remain available in logcat and the capture export
   (Settings → EXPORT).

## 3. Failure-cause capture (AppRoot wiring)

New `lastFailure: String?` remember-state holding a display-ready phrase.

**Set points:**

- Scan timeout in `runConnectAttempt` (no advertisement within 10 s): set
  `lastFailure = ConnectFailureHints.phrase(ConnectFailureHints.SCAN_TIMEOUT)`
  (`NO MONITOR FOUND`) before returning `true` to the policy. `lastFailure`
  is display-ready at every set point; the sentinel never reaches the strip.
  Today this failure only reaches a log line.
- `routeConnectionState` on `Faulted`: set
  `lastFailure = ConnectFailureHints.phrase(fault.reason)`. This covers
  connect-time faults and mid-session link drops alike, so a retry cycle
  after losing the link shows its cause, e.g.
  `RETRYING (1/3)… MONITOR CLOSED LINK (GATT 19)`.

**Clear points:**

- `handleAutoAction(StartAttempt)` — a new attempt begins; the label and the
  reason both reset (alongside the existing `autoRetryLabel = null`).
- `routeConnectionState` on `Armed`/`Disarmed` — the link established;
  no failure to remember.
- `handleAutoAction(Stop)` — the user tapped DISCONNECT; a stale reason must
  not outlive their action.

**GiveUp does not clear** `lastFailure`: the final `BLE DISCONNECTED` keeps
its reason.

**Known edge (accepted):** a mid-session drop that arrives as a clean
`Disconnected` (cleanup status GATT_SUCCESS) sets no reason of its own, so
the retry cycle may briefly show the previous attempt's cause. The next
attempt start clears it. In practice monitor power-off produces a
`Faulted("gatt 8"/"gatt 133")`, not a clean close; no extra machinery for
the rare clean-close path (YAGNI).

## 4. Pure mapper — `ConnectFailureHints.kt` (app/range)

Single pure function plus a scan-timeout sentinel; JVM-testable, no Android
imports. Raw fault strings come from `Mlm2proGattClient` (bounded set) and
`RapsodoTokenProvider.Failure.reason`.

| Input (raw) | Output phrase |
|---|---|
| `SCAN_TIMEOUT` sentinel (wiring-set) | `NO MONITOR FOUND` |
| `gatt 8` | `MONITOR NOT RESPONDING (GATT 8)` |
| `gatt 19` | `MONITOR CLOSED LINK (GATT 19)` |
| `gatt 133` | `CONNECT REJECTED (GATT 133)` |
| other `gatt N` | `LINK ERROR (GATT N)` |
| `service discovery N` | `SERVICE DISCOVERY FAILED` |
| `discoverServices rejected` | `SERVICE DISCOVERY FAILED` |
| `connectGatt returned null` | `BLUETOOTH STACK REFUSED` |
| `token fetch: X` | `TOKEN FETCH FAILED: X` |
| `SecurityException` | `BLUETOOTH PERMISSION MISSING` |
| anything else (incl. empty) | `CONNECT FAILED: <raw>` |

Phrases are short uppercase fragments (matching strip style). Raw reasons in
this system are short and bounded; no truncation.

## 5. Label composition — `describe()`

New optional parameter `failureReason: String? = null` (existing call sites
compile unchanged). Precedence and combination:

1. `demo` → `DEMO MODE - FIRE TO SHOOT` (unchanged; reason suppressed).
2. `retryLabel != null` → `"$retryLabel $failureReason"` when a reason exists
   (`RETRYING (2/3)… NO MONITOR FOUND`), else bare retry label.
3. `scanning` → `SCANNING…` (mid-attempt; no cause is current).
4. Otherwise by state:
   - `Faulted` → `"BLE FAULTED - $failureReason"` when a reason exists, else
     bare `BLE FAULTED` (SEE CAPTURE removed entirely).
   - `Disconnected` → `"BLE DISCONNECTED - $failureReason"` when a reason
     exists, else `BLE DISCONNECTED`.
   - `Connecting`/`Handshaking`/`Armed`/`Disarmed` unchanged.

`StatusStrip` and its `armed` calculation are untouched — it receives the
composed string via `info` as today. The KDoc on `describe` is updated.

## 6. Testing

- **`ConnectFailureHintsTest`** (new, app module, plain JUnit4): one case per
  table row, plus unmapped-`gatt` fallback, token-fetch remainder passthrough,
  empty/unknown fallback, and the scan-timeout sentinel.
- **`DescribeLabelTest`** (new, app module): demo precedence suppresses the
  reason; retry with/without reason; scanning hides the reason; `Faulted`
  with/without reason (SEE CAPTURE absent); `Disconnected` with/without
  reason; Armed unaffected.
- Run: `:app:testDebugUnitTest` (typed variant; the aggregate `test` task
  rejects `--tests`), then a full `.\gradlew.bat build`.

## 7. Out of scope

Core reason-string changes, capture-export format, retry policy changes,
two-line strip layout, localization (single-locale uppercase strips).
