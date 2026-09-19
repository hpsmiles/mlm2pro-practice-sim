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

- Parse MLM2PRO BLE advertisement/GATT data using reverse-engineered byte mappings from open-source repos: **MLM2PRO-BT-APP**, **flighthook**.
- Raw ball metrics captured on-device: Ball Speed, Launch Angle, Launch Direction, Spin, Spin Axis.
- Treat third-party byte-mapping tables as reference data to re-verify against live captures — protocol details may drift between monitor firmware versions.

## Ball Flight Physics

- Pre-calculated 3D ballistic ODE model; reuse aerodynamic drag/lift math from **libgolf** or **golfmodel** rather than inventing formulas.
- Outputs: carry, total distance, side-curve, rollout.

## Conventions for This Repo

- No repo-wide commands exist yet (empty repo, no build files). As the project scaffolds, update this file with exact Gradle commands: build, install, unit tests, and how to run a single test.
- BLE parsing and physics code must be unit-testable without a physical MLM2PRO — keep the byte-decoder and ball-flight ODE pure/deterministic (given inputs → same outputs).
- Do not add cloud/PC dependencies; single-device constraint is a hard architectural boundary.
