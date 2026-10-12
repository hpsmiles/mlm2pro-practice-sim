# MLM2PRO Practice Sim

A tour-style golf practice and simulator app for the **Rapsodo MLM2PRO** launch
monitor. It's built for a single-device shed setup: everything runs on one
Android tablet with no companion PC and no cloud — fully offline once you're
connected.

Where the stock Rapsodo app stops, this picks up: a clean flat range instead of
oversized 3D island greens, real head-to-head club/shaft testing, guided
true-gapping bag mapping, and custom strokes-gained combines — all driven by
live shots straight off the monitor over Bluetooth LE.

- Project setup & tech decisions: [AGENTS.md](AGENTS.md)
- Development roadmap: [ROADMAP.md](ROADMAP.md)

## What it does

- **Live range** — hit a ball and watch it fly in ~2 s: POV range with ball
  tracers, follow-cam, landing points, live dispersion, a last-shot metric bar,
  and standing 50–350 m distance signs.
- **Sessions & history** — every shot is saved to a local database with its club
  tag; browse past sessions with per-club averages and std-dev, exclude mishits,
  and see launch direction.
- **Practice games** — *Target Practice* (band scoring on an archery target
  face) and *Break the Pane* (break all 9 floating cells while resting on the
  green).
- **Custom combines** — catalog 18-shot combines (**WEDGES** 50/70/90 m and
  **APPROACH** 100/150 m + driver) scored with sim-conditioned strokes-gained
  handicap estimates (per-station HCs plus an overall).
- **Bag mapping & true gapping** — a guided full-bag workflow that filters
  misreads/duffs and produces carry + total box-plot matrices with distance-gap
  flags.
- **Club & shaft testing** — head-to-head A/B (shaft vs shaft, head vs head)
  with metric overlays, dispersion rings, a horizontal top-down view, and PDF
  export.

## Requirements

- An **Android tablet running Android 12 (API 31) or newer**, held in landscape.
- A **Rapsodo MLM2PRO** launch monitor.
- **Rapsodo Premium** — required by Rapsodo for the third-party app
  authentication flow (see below).
- Bluetooth enabled on the tablet.

No PC, no cloud, no other sim software is needed. The app stays entirely
on-device; the only network use is the one-time session-token fetch when it
connects.

## Install

Download `app-release.apk` from the latest
[release](https://github.com/hpsmiles/mlm2pro-practice-sim/releases), copy it to
the tablet, and open it. You'll need to allow installing apps from unknown
sources for the file manager you use.

To build and install from source instead, see [Building](#building-from-source).

## Connecting the monitor

The app is a Bluetooth LE client that talks to the monitor directly, the same
way GSPro / E6 / Awesome Golf do. Rapsodo gates that behind a session token that
you authorize once in the Rapsodo app, and only one app can hold the monitor's
link at a time — so the order matters:

1. **Turn on the monitor.** Power up the MLM2PRO and give it a few seconds to
   boot and settle. Make sure it's charged and within a few metres of the
   tablet.

2. **Connect in the Rapsodo app.** Open the Rapsodo app and connect to the
   monitor as usual. This is also the right moment to physically align and level
   the unit for your bay — **our app has no alignment camera or device-setup
   view of its own**, so do any positioning/alignment here while you have the
   Rapsodo display in front of you.

3. **Authorize third-party access.** Still in the Rapsodo app, go to
   *Play → Simulation → 3rd Party Apps → Awesome Golf → Authenticate Now*.
   - Re-authorize **every 24 hours** (a Rapsodo requirement).
   - The session token lasts about **3 hours**.

4. **Disconnect in the Rapsodo app.** Close/end the connection in the Rapsodo
   app so it releases the monitor's Bluetooth link back to the system. Only one
   client can hold the link, so this step is what frees it for us.

5. **Connect this app.** Now open the MLM2PRO Practice Sim and grant the
   Bluetooth permissions when prompted. It scans for the monitor and connects
   automatically — no menu diving required.

The connection state shows in the left rail. Once connected the app arms itself
and waits for shots.

**If it won't connect:**

- If the status stays red/dark, re-authorize in the Rapsodo app and
  **power-cycle the monitor**.
- Keep the tablet within a few metres and make sure nothing else is connected.
- A failed scan reports `NO MONITOR FOUND`; GATT errors (e.g. `GATT 133`) usually
  clear after a power-cycle.

### Rail controls

The left rail holds the global controls:

| Control | What it does |
| --- | --- |
| **MODE: LIVE / DEMO** | LIVE takes real shots from the monitor. DEMO replays simulated shots so you can try the app without hardware. |
| **FIRE** | Fires one simulated shot — shown only in DEMO mode. |
| **CONNECT** | Starts a connection attempt manually. |
| **ARM / STANDBY** | Arms the monitor to capture a shot, or puts it on standby. |
| **DISCONNECT** | Tears the link down. |
| **END SESSION** | Closes the current range session so it moves into HISTORY. |

## Using the app

The rail has six tabs.

### RANGE

The main practice view. Pick an **ACTIVE CLUB** (top-right), then hit balls —
each shot is tagged with that club and saved. The last-shot bar shows ball
speed, launch, spin, carry/total and direction; the canvas shows the tracer,
landing point and running dispersion. Use **END SESSION** in the rail to close
out and file the session into history.

### GAMES

Three modes, each with its own setup and scoring:

- **Target Practice** — choose a distance (50–350 m) and difficulty. Five shots
  are scored by distance bands from where the ball *rests* (rollout counts) on
  an archery target face.
- **Break the Pane** — a floating 3×3 pane sits ahead of you; a shot breaks a
  cell only if it flies through an unbroken cell *and* rests on the green. Break
  all nine in as few shots as possible.
- **Combines** — pick **WEDGES** or **APPROACH** and work through the station
  flow. Each run is scored as strokes-gained per station plus an overall
  handicap estimate. Unfinished runs can be resumed.

Speed chips (1x/1.5x/2x/4x) pace the ball flight; the right-hand panel shows
per-shot metrics.

### BAG

Guided bag mapping & true gapping. Start a **TEST** with the whole bag (choose
short→long or a random order), hit a few shots per club, and the app filters
misreads/duffs and builds a carry + total box-plot matrix with distance-gap
flags. The live range view stays up while you collect.

### FIT

Club & shaft testing engine. Add two or more comparisons (shaft vs shaft, head
vs head), hit shots for each, and compare them side by side: metric table,
noise-dimmed deltas, standard deviations, dispersion rings, and a horizontal
top-down view. Results can be exported to **PDF** through the share sheet. Past
fitting sessions are kept read-only in history.

### SETTINGS

- **RAPSODO AUTH** — a reminder of the third-party authorization steps above.
- **BAG** — add, rename, delete and reorder your club tags. Renames apply to new
  shots only; saved shots keep the tag they were recorded with.
- **GREEN / TURF** — set the green and fairway surfaces used by the range and
  games (softer greens hold, firmer turf runs out).
- **SOUND** — game cue sounds on/off.
- **DEBUG — NOTIFICATION CAPTURE** — bench tooling for recording raw BLE
  notifications during a live session.

### HISTORY

Four tabs across the sidebar:

- **SESSIONS** — past range sessions with a per-club AVG/σ view, an 11-column
  shot list, and shot-exclusion ticks (excluded mishits drop out of the
  averages but stay in the record).
- **GAMES** — completed Target Practice and Break the Pane results, with RECENT
  / BEST sorting.
- **COMBINES** — completed combine runs with a drill-in to per-station scoring
  and a true-scale top-down dispersion view.
- **FITTINGS** — read-only fitting results with the top-down pane and PDF
  export.

## Building from source

Requires **JDK 17** and an **Android SDK** (Android Studio bundles both).

    ./gradlew build          # assemble + run all unit tests
    ./gradlew test           # unit tests only
    ./gradlew :app:installDebug   # install a debug build on a connected tablet

Set `JAVA_HOME` to your JDK first (on Windows, the Android Studio JBR works:
`C:\Program Files\Android\Android Studio\jbr`).

## Releasing (maintainers)

Release APKs are signed from a keystore that is **never committed** — the repo
is public. Create a keystore and a `keystore.properties` at the repo root
(both gitignored):

    keytool -genkeypair -v -keystore golfsim-release.jks -storetype PKCS12 \
      -alias golfsim -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=Golf Sim Release, OU=GolfSim, O=GolfSim, C=GB"

`keystore.properties`:

    storeFile=golfsim-release.jks
    storePassword=<password>
    keyAlias=golfsim
    keyPassword=<password>

Then:

    ./gradlew :app:assembleRelease
    # -> app/build/outputs/apk/release/app-release.apk

Absent `keystore.properties`, the release build still compiles (unsigned), so
CI and fresh clones stay green. **Back up the keystore** — future updates must be
signed with the same key or devices will refuse the upgrade.

## Limitations

- **No club data over BLE.** Angle of attack, club path, face angle and
  face-to-path are measured by the device but only exposed by the Rapsodo app /
  R-Cloud, not over Bluetooth. Ball speed, launch angle, launch direction, spin
  and spin axis are the inputs the app works from.
- **Single device.** The tablet does everything; there is no PC or cloud
  component by design.
- **One monitor per link.** Close other sims and the Rapsodo app while this app
  is connected.

## Modules

`:app` · `:core:ble` (MLM2PRO byte-decoder) · `:core:physics` (ball-flight ODE)
· `:core:connect` (GATT client) · `:core:scoring` (strokes-gained) ·
`:core:designsystem` · `:core:data` (Room)
