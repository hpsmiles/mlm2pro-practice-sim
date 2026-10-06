# Monitor Battery on the Status Strip — Design

Date: 2026-10-06
Status: Approved (brainstorm decisions 2026-10-06)
Follows: `2026-09-30-connect-failure-reason-design.md`

## 1. Problem

The MLM2PRO reports its battery level over BLE: EVENTS code `0x03` decodes to
`Mlm2proEvent.Battery(percent)` (byte 1 = percent), arriving roughly every
~30 s while a session is up. The decode is implemented and test-pinned
(`EventParserTest`, golden `battery-event-*` fixtures, `BenchCaptureRoundtripTest`
→ `Battery(85..83)`), but `Mlm2proGattClient.handleNotification` deliberately
swallows the event ("state kept; sequencer owns protocol"), so nothing reaches
the UI. There is no way to see the monitor's charge during a session — the user
wants a battery readout on the very bottom bar, shown when connected.

## 2. Decisions

1. **Glyph + percent at the right edge of the existing bottom strip.** The
   strip is whole-app, so the readout is visible on every tab. `● ARMED … [▮▮▮ ] 85%`.
2. **Two-state color: `TextMuted` normally, `AlertRed` below 20 %** (glyph and
   number together). Amber stays reserved for live-moment UI.
3. **Shown once the first battery event arrives.** No placeholder in the ~30 s
   window before it; any link teardown clears the reading and hides it again.
4. **Approach A — client-owned `StateFlow<Int?>`.** The client already exposes
   `state: StateFlow<ConnectionState>` to the same strip; battery is the same
   kind of link-status data. The clearing rule lives at the link's single
   teardown choke point (`cleanupConnection`), so a reading can never outlive
   its link.
5. **Display-side clamp to 0–100.** The byte semantics are "believed percent",
   formally unverified; values >100 or <0 are clamped for both fill and label.
   No clamp on ingest — the capture log stays faithful.
6. **Session state only — not persisted.** No Room entity, no migration.

## 3. Data flow & wiring

### 3.1 `Mlm2proGattClient` (core/connect)

- Add `private val _batteryPercent = MutableStateFlow<Int?>(null)` and
  `val batteryPercent: StateFlow<Int?> = _batteryPercent`, sibling of `state`.
- In `handleNotification`'s `Mlm2proMessage.Event` branch, alongside the
  existing misread gate:
  `is Mlm2proEvent.Battery -> _batteryPercent.value = msg.event.percent`
  (update the stale "battery: state kept" comment).
- In `cleanupConnection()`: `_batteryPercent.value = null`. All three teardown
  paths route through it — link loss (`onConnectionStateChange`),
  explicit `disconnect()`, and pre-retry teardown in `connect()`.
- `reportFault` (app-side faults, e.g. token fetch) intentionally does **not**
  clear it: that path keeps the physical link up, so a live reading remains
  truthful.

### 3.2 `AppRoot` (app)

- `val batteryPercent by gattClient.batteryPercent.collectAsState()` next to
  the existing `connectionState` collection (line ~101).
- Pass `batteryPercent = batteryPercent` to the `StatusStrip` call; the
  "DB WRITE FAILING" override on `info` is unaffected.
- Demo mode needs no special case: no connection → `null` → hidden.

### 3.3 Behavior matrix

| Situation | Right side of strip |
|---|---|
| Scanning / connecting / handshaking | hidden (no reading yet) |
| Connected, before first `0x03` event (~30 s) | hidden |
| Connected, reading arrived | glyph + %, muted; red at < 20 % |
| Repeated battery events | last event wins (overwrite) |
| Link teardown (walk-away / DISCONNECT / retry) | cleared → hidden |
| Disarmed (STANDBY, link up) | last reading stays visible |
| App-side fault with live link (token fetch) | reading stays |
| Demo mode | hidden |
| `DB WRITE FAILING` shown | battery unaffected |
| Any tab | same strip, visible |
| Percent >100 or <0 | clamped to 0–100 |

## 4. UI — `StatusStrip` + `BatteryIndicator` (core/designsystem)

### 4.1 `StatusStrip`

New trailing parameter, defaulted so every existing call site is pixel-identical:

```kotlin
fun StatusStrip(
    armed: Boolean,
    info: String,
    modifier: Modifier = Modifier,
    batteryPercent: Int? = null,
)
```

Layout: existing dot + info text; the info `Text` gains
`Modifier.weight(1f)` so the indicator owns the trailing edge
(extreme-length info is constrained rather than displacing the battery;
`maxLines = 1` + ellipsis for safety). Battery row renders only when
`batteryPercent != null`.

### 4.2 `BatteryIndicator` (new file `BatteryIndicator.kt`)

- Row: custom-drawn glyph + `"85%"` label in `GolfTypography.Status`.
  No Material-icons dependency (the repo draws all geometry).
- Glyph: ~20 × 10 dp `Canvas` — rounded-rect body (1 dp stroke, ~2 dp radius)
  occupying the left ~18 dp, a small terminal nub (~2 × 4 dp) centered at the
  right, and an inset fill rect whose width is
  `clampedPercent / 100f × inner width`.
- Gaps: `GolfSpacing.Sm` start padding from the info text, `GolfSpacing.Xs`
  between glyph and label. Fits the 24 dp `StatusStripHeight`.
- Colors: everything `TextMuted` normally; everything `AlertRed` when low.

### 4.3 Pure helper (JVM-testable)

```kotlin
internal data class BatteryDisplay(val percent: Int, val low: Boolean)
internal fun batteryDisplay(percent: Int): BatteryDisplay
```

- `percent` = `percent.coerceIn(0, 100)`.
- `low` = clamped percent `< 20` (boundary: 19 → true, 20 → false).

The composable renders entirely from `BatteryDisplay`.

## 5. Testing & verification

- **`BatteryDisplayTest`** (new, `:core:designsystem`, plain JUnit4): clamp
  cases (0, 100, >100, <0), low boundary 19/20, pass-through of normal values.
- **Existing suites stay green.** `:core:designsystem:test`,
  `:app:testDebugUnitTest` (`describe` untouched), `:core:ble:test` and
  `:core:connect:test` (decode path already pinned; no changes there).
- **Bench / on-device (manual):** connect → ARMED → indicator appears within
  ~30 s with a plausible percent (cross-check capture log); DISCONNECT chip →
  indicator gone; link dropped by walking away → gone; visual pass on the
  tablet in landscape across tabs. Red state cannot be forced on a charged
  monitor, so the unit test is its authoritative check.
- Build commands: typed test task (`--tests` is rejected by the aggregate
  `test` task), then full `.\gradlew.bat build`.

## 6. Out of scope

Charging-state detection (not in the observed protocol), low-battery sound
alerts, battery history/persistence, R-Cloud or Local-mode WiFi battery data,
any change to `EventParser`/decoder byte mappings.
