# AGENTS.md — golf sim

Android tablet golf-sim & practice app for the **Rapsodo MLM2PRO** launch monitor. Milestones M0–M6 have shipped, plus the M6 follow-ups (2026-10-07 — see [ROADMAP.md](ROADMAP.md)); this file is the standing charter for agents working in the repo.

## Mission & Constraints

- Replace Rapsodo's stock app gaps: arcade-style range, unrealistic 3D greens, fixed combines, missing training tools → tour-style practice features.
- **Single-device shed setup**: runs entirely on a Lenovo Android tablet. No companion PC, no heavyweight simulator software. Any proposed component must work offline, on-device.
- Target: Android tablet, minSdk 31 (Android 12), landscape-first UI.

## Tech Stack (decided)

- **Kotlin + Jetpack Compose** (not XML views, not cross-platform).
- BLE via Android Bluetooth Low Energy APIs.

## Feature Modules (target scope)

1. **Bag Mapping & True Gapping** (M6, shipped; follow-ups 2026-10-07) — guided full-set workflow; automatic misread/duff filtering; real carry averages; intro ORDER selector (short→long default / random shuffle); dual carry+total box-plot matrix + distance-gap visualization.
2. **Club & Shaft Testing Engine** (M7, planned) — head-to-head comparison (e.g., Shaft A vs B); side-by-side metric overlays; standard deviations; dispersion ovals. Session-scoped TEST clubs (M5x) are the precursor.
3. **Custom Combines** (M8, planned) — user-defined target distances (e.g., 50m / 70m / 90m wedges), NOT Rapsodo's fixed 24-shot template.
4. **Clean Practice Environment** (M4, shipped) — flat target grids / scaled target ovals focused on dispersion & landing metrics; explicitly avoid oversized 3D island greens.
5. **Practice Games** (M5.5, shipped) — Target Practice (rest-position band scoring, archery target face) and Break the Pane (fly-through-cell + rest-on-green break rule); Room v3 `game_results` summaries; HISTORY > GAMES.

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
- The bounce/roll law is deliberately recalibrated when shed feedback demands it (2026-10-03, 2026-10-07 — dated comments in `BounceRollModel`/`Surface`). Any re-tuning must steer with the `RolloutSweepDiagnostic` sweep and keep the existing pin set green (tour averages, green behavior, rough ≤ fairway).

## Conventions for This Repo

- Windows: JDK is not on `PATH` — set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before any Gradle command.
- Gradle flags (e.g. `--console=plain`) must precede task names; Gradle is quiet during configure — quiet ≠ hung (runs take 5s–2min).
- Build everything (assemble + tests): `.\gradlew.bat build`
- Run all unit tests: `.\gradlew.bat test`
- One module's tests: `.\gradlew.bat :core:ble:test`
- One test class: `.\gradlew.bat :core:ble:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.ble.Mlm2proDecoderTest"` — the aggregate `test` task rejects `--tests`; always use the typed `testDebugUnitTest` variant (same for `:app:testDebugUnitTest`).
- Exception: `:core:physics` is a pure-JVM module with no Android variants — run its tests with `.\gradlew.bat :core:physics:test` (no `testDebugUnitTest` exists there).
- Install debug build on a connected tablet: `.\gradlew.bat :app:installDebug`
- Room schema is versioned: entity changes require a new `MIGRATION_N_N+1` in `Mlm2proDatabase`, `@ColumnInfo(defaultValue)` matching the ALTER TABLE defaults exactly, a committed schema JSON under `core/data/schemas/`, and a migration test (see `MigrationFrom1Test`, `MigrationFrom2Test`). Never enable destructive migration fallback.
- BLE parsing and physics code must be unit-testable without a physical MLM2PRO — keep the byte-decoder and ball-flight ODE pure/deterministic (given inputs → same outputs).
- Game code follows the same discipline: `app/.../games/` holds plain Compose state holders + painters (no ViewModels, no Navigation-Compose); scoring/geometry/reveal logic is pure and JVM-tested (`TargetPracticeScoring`, `PaneGeom`, `PaneClip`, `GameLeavePolicy`, ...). Game shots never enter range sessions/shot history — only completed-game summaries via `SessionRepository.saveGameResult` (`game_results`, Room v3). Leaving a game mid-session is governed by `GameLeavePolicy`.
- Bag mapping follows the games pattern: `app/.../bag/` holds plain Compose state holders + painters; the plan snapshot (`clubList`) is the only persisted truth — order/random modes are applied BEFORE `encodeClubSnapshot`, and kill/resume derives the club index from persisted shots. Bag shots never enter range sessions — each is write-through persisted to `bag_mapping_sessions`/`bag_mapping_shots` (Room v4) via `SessionRepository.appendBagMappingShot`; the results matrix plots carry AND total, while gap flags stay carry-based. Live shots reach the collector only while the BAG tab is collecting (`onCollectingChange` seam, pause-on-leave on dispose).
- Club fitting follows the games/bag pattern: `app/.../fitting/` holds plain Compose state holders + painters (no ViewModels, no Navigation-Compose); aggregates/deltas/duff filtering live in pure JVM-tested code (`FittingStats`, `DispersionEllipse`). Fitting shots never enter range sessions — each is write-through persisted to `fitting_sessions`/`fitting_shots` (Room v5) with a per-shot club snapshot (club id/name/type/wasTemp) so the temp-club purge never rewrites fitting history; `routeShot` gates on `tab == RangeTab.FIT` (capture live across FIT sub-views, stops on tab change). Exclusion (`fitting_shots.excluded`) is the single source of truth driving aggregates, deltas, dots and rings. Club colours are positional by first appearance (`FittingColors.clubColor(index)` over `FittingStats.clubOrder`) — keep all views (COMPARE, TABLE, TOP-DOWN, HISTORY) on that rule.
- Do not add cloud/PC dependencies; single-device constraint is a hard architectural boundary.
