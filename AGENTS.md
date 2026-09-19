# AGENTS.md — golf sim

Android tablet golf-sim & practice app for the **Rapsodo MLM2PRO** launch monitor. Greenfield project — this file is the charter until code scaffolding lands.

## Mission & Constraints

- Replace Rapsodo's stock app gaps: arcade-style range, unrealistic 3D greens, fixed combines, missing training tools → tour-style practice features.
- **Single-device shed setup**: runs entirely on a Lenovo Android tablet. No companion PC, no heavyweight simulator software. Any proposed component must work offline, on-device.
- Target: Android tablet, minSdk 31 (Android 12), landscape-first UI.

## Tech Stack (decided)

- **Kotlin + Jetpack Compose** (not XML views, not cross-platform).
- BLE via Android Bluetooth Low Energy APIs.

## Feature Modules (planned scope)

1. **Bag Mapping & True Gapping** — guided full-set workflow; automatic misread/duff filtering; real carry averages; carry matrix + distance-gap visualization.
2. **Club & Shaft Testing Engine** — head-to-head comparison (e.g., Shaft A vs B); side-by-side metric overlays; standard deviations; dispersion ovals.
3. **Custom Combines** — user-defined target distances (e.g., 50m / 70m / 90m wedges), NOT Rapsodo's fixed 24-shot template.
4. **Clean Practice Environment** — flat target grids / scaled target ovals focused on dispersion & landing metrics; explicitly avoid oversized 3D island greens.

## Data Acquisition (BLE)

- Parse MLM2PRO GATT notification data using reverse-engineered byte mappings from open-source repos: **Duwaynef/MLM2PRO-BT-APP** (C#, MIT — ships the written protocol spec `mlm2pro.md`) and **springbok/MLM2PRO-GSPro-Connector** (Python, GPL — independent corroboration, reference-only, no code copying). `flighthook` has no MLM2PRO BLE code and is not a reference.
- Raw ball metrics captured on-device: Ball Speed, Launch Angle, Launch Direction, Spin, Spin Axis.
- Treat third-party byte-mapping tables as reference data to re-verify against live captures — protocol details may drift between monitor firmware versions.

## Ball Flight Physics

- Pre-calculated 3D ballistic ODE model; reuse aerodynamic drag/lift math from **libgolf** or **golfmodel** rather than inventing formulas.
- Outputs: carry, total distance, side-curve, rollout.

## Conventions for This Repo

- Windows: JDK is not on `PATH` — set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before any Gradle command.
- Build everything (assemble + tests): `.\gradlew.bat build`
- Run all unit tests: `.\gradlew.bat test`
- One module's tests: `.\gradlew.bat :core:ble:test`
- One test class: `.\gradlew.bat :core:ble:test --tests "com.hpsmiles.golfsim.core.ble.Mlm2proDecoderTest"`
- Install debug build on a connected tablet: `.\gradlew.bat :app:installDebug`
- BLE parsing and physics code must be unit-testable without a physical MLM2PRO — keep the byte-decoder and ball-flight ODE pure/deterministic (given inputs → same outputs).
- Do not add cloud/PC dependencies; single-device constraint is a hard architectural boundary.
