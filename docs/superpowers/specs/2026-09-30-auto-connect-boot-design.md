# Auto-Connect on Boot — Design

Date: 2026-09-30
Status: approved (user sign-off on §1–§4, 2026-09-30)

## Goal

The app opens ready to catch shots: it starts in LIVE mode, immediately scans
for the MLM2PRO, connects, handshakes, and auto-arms — zero taps on a normal
shed boot. Failures retry a bounded number of times, then fall back to the
existing manual CONNECT ritual.

## Background (current behavior)

- `AppRoot` boots in DEMO mode (`demo = true`); shots flow only after the user
  taps CONNECT (permission check → `Mlm2proScanner.scan().first()` →
  `gattClient.connect`), then toggles ARM.
- After granting the two runtime permissions (`BLUETOOTH_SCAN`,
  `BLUETOOTH_CONNECT`), the permission launcher only sets a flag — the user
  must tap CONNECT a second time.
- `Mlm2proScanner.scan()` suspends forever until a device is found or the
  collecting coroutine is cancelled; there is no scan timeout.
- The GATT client fires `onMeasurement` for any decoded shot regardless of
  ARM state; ARM is a device-side protocol state (LED/session gate).
- Guard rails that must survive: `scanning` flag (no double scan), connect
  accepted only from `Disconnected`/`Faulted`, single BLE link per monitor.

## §1 Behavior

1. **App opens** → if BLE permissions are not granted, the system dialog
   appears immediately. On grant, the auto-connect cycle starts automatically
   (no second tap). On deny, no cycle starts; the `NO PERM` chip shows and
   CONNECT re-requests.
2. **Cycle:** scan (10 s timeout per attempt) → GATT connect → handshake →
   auto-ARM. The app boots in **LIVE** mode (`demo` initial value becomes
   `false`); the MODE chip still switches to DEMO for FIRE.
3. **Failure handling:** the cycle runs an immediate first attempt, then up
   to 3 retries with backoff 5 s → 10 s → 20 s (4 connection tries total).
   Any failure — scan timeout, connect fault, handshake fault, or mid-session
   link drop — consumes the cycle. During the wait the status strip shows
   `RETRYING (1/3)…`–`RETRYING (3/3)…`; after the final failure it shows
   `BLE DISCONNECTED`.
4. **Manual controls:**
   - CONNECT always starts a fresh 3-attempt cycle.
   - DISCONNECT cancels the cycle — no retries until CONNECT or app restart.
   - STANDBY (disarm) is a manual override; auto-connect never re-arms behind
     the user's back. It arms exactly once per newly-established link.

## §2 Components

### `AutoConnectPolicy` (new, `core/connect`)

Pure Kotlin state machine — no Android imports, JVM-testable.

```
events:   start() · attemptFailed() · linkEstablished() · linkDropped()
          userDisconnect() · manualConnect()
outputs:  StartAttempt · WaitThenAttempt(delayMs) · Arm · GiveUp · Stop
config:   maxRetries = 3, backoff = [5_000, 10_000, 20_000] ms
```

Rules:

- `start()` / `manualConnect()` → fresh budget, emit `StartAttempt`
  (the first connection try runs immediately; up to 3 retries follow).
- `attemptFailed()` → while retries remain, `WaitThenAttempt(next backoff)`;
  otherwise `GiveUp`.
- `linkEstablished()` → emit `Arm`, reset the attempt budget (so a later
  `linkDropped()` starts a fresh cycle).
- `userDisconnect()` → emit `Stop`; all subsequent events except
  `manualConnect()` are ignored.
- `linkDropped()` while already waiting in a retry cycle is ignored
  (single-fire per drop).

### `AppRoot` wiring (thin)

- One `LaunchedEffect(Unit)`: permission check → request if needed →
  `policy.start()`.
- The permission launcher callback: on all-granted → `policy.start()`.
- Drives scan+connect per policy output; scan wrapped in
  `withTimeoutOrNull(10_000) { Mlm2proScanner(context).scan().first() }`;
  null result → `policy.attemptFailed()`.
- Collects `connectionState`: `Handshaking → Disarmed` transition while a
  cycle is active → `policy.linkEstablished()` → on `Arm`, `gattClient.arm()`.
  A transition back to `Disconnected`/`Faulted` after establishment →
  `policy.linkDropped()` (treated as attempt failure).
- `demo` initial value `false`; `describe()` gains the retry label
  (`RETRYING (n/3)…`).
- DISCONNECT chip → `gattClient.disconnect()` + `policy.userDisconnect()`.
- No changes to `Mlm2proGattClient` or `Mlm2proScanner`.

## §3 Error handling / edge cases

- **Scan timeout (monitor off):** null from `withTimeoutOrNull` →
  `attemptFailed()` → backoff → retry; after 3 → `GiveUp`, chip stays
  tappable.
- **Bluetooth adapter off:** scan fails/empty → same attempt-failure path
  (retries are cheap; no special-casing).
- **Permission denied at boot:** no cycle; CONNECT re-requests; grant starts
  the cycle via the launcher callback.
- **Faulted handshake:** `Faulted` state → `attemptFailed()`; the existing
  Disconnected/Faulted connect guard prevents double links.
- **Token-fetch failure** (premium-gated devices): unchanged — Faulted, and
  bounded retries re-attempt.
- **Process death / swipe-away:** nothing persists; reopen starts a fresh
  cycle. No foreground service (rejected: notification requirement +
  lifecycle complexity, YAGNI for a dedicated shed tablet).

## §4 Testing

- **`AutoConnectPolicyTest`** (JVM, `core/connect`): boot → `StartAttempt`;
  4× `attemptFailed` → `GiveUp` with waits 5/10/20 s between retries;
  `linkEstablished` → `Arm` + budget reset; `linkDropped` → fresh cycle;
  `userDisconnect` → `Stop` with later drops ignored; `manualConnect` →
  fresh cycle.
- **Existing suites:** `:core:ble` and `:core:connect` stay green.
- **On-tablet manual verify:** cold start with monitor off → 3 retries →
  `BLE DISCONNECTED`; monitor on → auto cycle → `ARMED` in LIVE; DISCONNECT
  cancels; CONNECT restarts; STANDBY stays manual.
