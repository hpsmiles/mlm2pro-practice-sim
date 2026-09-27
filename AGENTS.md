# AGENTS.md — golf sim

Android tablet golf-sim & practice app for the **Rapsodo MLM2PRO** launch monitor. Milestones M0–M5x have shipped (see [ROADMAP.md](ROADMAP.md)); this file is the standing charter for agents working in the repo.

## Mission & Constraints

- Replace Rapsodo's stock app gaps: arcade-style range, unrealistic 3D greens, fixed combines, missing training tools → tour-style practice features.
- **Single-device shed setup**: runs entirely on a Lenovo Android tablet. No companion PC, no heavyweight simulator software. Any proposed component must work offline, on-device.
- Target: Android tablet, minSdk 31 (Android 12), landscape-first UI.

## Tech Stack (decided)

- **Kotlin + Jetpack Compose** (not XML views, not cross-platform).
- BLE via Android Bluetooth Low Energy APIs.

## Feature Modules (target scope)

1. **Bag Mapping & True Gapping** (M6, planned) — guided full-set workflow; automatic misread/duff filtering; real carry averages; carry matrix + distance-gap visualization.
2. **Club & Shaft Testing Engine** (M7, planned) — head-to-head comparison (e.g., Shaft A vs B); side-by-side metric overlays; standard deviations; dispersion ovals. Session-scoped TEST clubs (M5x) are the precursor.
3. **Custom Combines** (M8, planned) — user-defined target distances (e.g., 50m / 70m / 90m wedges), NOT Rapsodo's fixed 24-shot template.
4. **Clean Practice Environment** (M4, shipped) — flat target grids / scaled target ovals focused on dispersion & landing metrics; explicitly avoid oversized 3D island greens.

## Data Acquisition (BLE)

- Parse MLM2PRO GATT notification data using reverse-engineered byte mappings from open-source repos: **Duwaynef/MLM2PRO-BT-APP** (C#, MIT — ships the written protocol spec `mlm2pro.md`) and **springbok/MLM2PRO-GSPro-Connector** (Python, GPL — independent corroboration, reference-only, no code copying). `flighthook` has no MLM2PRO BLE code and is not a reference.
- Raw ball metrics captured on-device: Ball Speed, Launch Angle, Launch Direction, Spin, Spin Axis, Club Head Speed.
- **Club data (Angle of Attack, Club Path, Face Angle, Face-to-Path) is NOT obtainable via BLE** — device-measured but Rapsodo-app (premium)/R-Cloud-cloud only; confirmed multi-source 2026-09-26, see `docs/research/2026-09-26-mlm2pro-club-data-and-transports.md`. Do not expect these in MEASUREMENT frames. MEASUREMENT offsets 12–15 are unconfirmed distance candidates, not club data.
- **Shot metrics = BLE only** (Rapsodo's "Direct" mode); stay BLE-only for shot data. The app's separate **"Local" mode** is a local-WiFi link carrying the live Impact Vision camera stream + shot clips in-session (R-Cloud upload is post-session) — this is the "direct wifi" seen in the Rapsodo app. Topology/ports/protocol are undocumented; discovery plan in `docs/research/2026-09-26-mlm2pro-club-data-and-transports.md` §4.
- **Official sim integrations (GSPro, E6 Connect, Awesome Golf) all connect as BLE GATT clients directly to the monitor** using 24-hour third-party auth tokens issued via the Rapsodo app's "3rd Party Apps" flow (Rapsodo Premium required; token exchange via `mlm.rapsodo.com`). No sim uses the monitor's WiFi for data — BLE-only is the complete sanctioned architecture, and our client implements the same pattern. Details: research doc §5.
- Treat third-party byte-mapping tables as reference data to re-verify against live captures — protocol details may drift between monitor firmware versions.

## Ball Flight Physics

- Pre-calculated 3D ballistic ODE model; reuse aerodynamic drag/lift math from **libgolf** or **golfmodel** rather than inventing formulas.
- Outputs: carry, total distance, side-curve, rollout.

## Conventions for This Repo

- Windows: JDK is not on `PATH` — set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before any Gradle command.
- Gradle flags (e.g. `--console=plain`) must precede task names; Gradle is quiet during configure — quiet ≠ hung (runs take 5s–2min).
- Build everything (assemble + tests): `.\gradlew.bat build`
- Run all unit tests: `.\gradlew.bat test`
- One module's tests: `.\gradlew.bat :core:ble:test`
- One test class: `.\gradlew.bat :core:ble:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.ble.Mlm2proDecoderTest"` — the aggregate `test` task rejects `--tests`; always use the typed `testDebugUnitTest` variant (same for `:app:testDebugUnitTest`).
- Install debug build on a connected tablet: `.\gradlew.bat :app:installDebug`
- Room schema is versioned: entity changes require a new `MIGRATION_N_N+1` in `Mlm2proDatabase`, `@ColumnInfo(defaultValue)` matching the ALTER TABLE defaults exactly, a committed schema JSON under `core/data/schemas/`, and a migration test (see `MigrationFrom1Test`). Never enable destructive migration fallback.
- BLE parsing and physics code must be unit-testable without a physical MLM2PRO — keep the byte-decoder and ball-flight ODE pure/deterministic (given inputs → same outputs).
- Do not add cloud/PC dependencies; single-device constraint is a hard architectural boundary.
