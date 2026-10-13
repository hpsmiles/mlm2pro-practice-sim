# Development Roadmap

Milestone-based sequencing. Every milestone ends demoable on the Lenovo tablet; the golden BLE capture library grows every shed session.

Sequencing approach: **walking skeleton down the pipeline** — prove the risky plumbing (BLE decode → physics) as pure, tested Kotlin first, build the Clean Practice Environment as the first live vertical slice, then layer feature modules onto the proven shot pipeline.

---

## M0 — Scaffold

Gradle + Kotlin + Jetpack Compose project, minSdk 31, module layout, `.gitignore`, GitHub Actions CI (build + unit tests on every push).

**Exit:** skeleton app installs and runs on the Lenovo tablet; CI green. **Shipped.**

## M1 — BLE Byte-Decoder (pure Kotlin, TDD)

Port MLM2PRO byte mappings from open-source references (Duwaynef/MLM2PRO-BT-APP, springbok/MLM2PRO-GSPro-Connector). Capture real GATT notification payloads with the device in the shed and commit them as golden fixtures. Decoder is pure and deterministic: raw bytes → Ball Speed, Launch Angle, Launch Direction, Spin, Spin Axis.

**Exit:** decoder reproduces all captured metrics for every golden fixture. **Shipped.**

## M2 — Ball-Flight Engine (pure Kotlin, TDD)

Port drag/lift ODE math from `libgolf` / `golfmodel`. Validate two ways: published reference trajectories, and side-by-side against the Rapsodo app in the shed. Outputs: carry, total distance, side-curve, rollout.

**Exit:** reference shot set matches Rapsodo within agreed tolerance. **Shipped.**

## M3 — UI Design System (look & feel)

Decide the visual identity before building any screen:

- Theme direction (dark-first for shed/outdoor contrast), color system, typography scale, spacing, motion
- Mockups for the three signature screens: range view, carry matrix, A/B comparison — with approval gate
- Compose design-system module: theme + component kit (buttons, metric cards, charts) as code

Deliberately not designing later module screens up front — each feature milestone (M5+) gets its own UI design pass reusing this design system.

**Exit:** signed-off mockups; design-system module compiles with theme and primitives in place. **Shipped.**

## M4 — Clean Practice Environment (first vertical slice)

Live BLE scanner + GATT client → decoder → physics → UI. Flat range grid, scaled target ovals, shot tracers, landing points, live dispersion, last-shot metric bar.

**Exit:** hit a ball, see it land within ~2s; carry matches Rapsodo. **Shipped** (tracers, painted range and follow-cam followed 2026-09-24/25).

### M4 follow-ups (requested 2026-09-24 — complete)

- **Ball follow-cam view** *(shipped 2026-09-25)*: replay the flight from a camera set distance
   BEHIND THE BALL (translating with it) instead of the fixed player POV — the
   standard sim-software view. Needs a travelling-camera projection layer on
   top of the existing `TrajectorySample` stream (pure math change in
   `PovProjector`, no 3D engine).
- **Visit the ball-flight eye line after multiple shots on the same spot** *(done 2026-10-13)*.

## M5 — Sessions & Persistence

Room DB, session history, club tagging, past-session review.

**Exit:** sessions survive app restart. **Shipped 2026-09-26.**

## M5x — Range & History UI Edits (shipped 2026-09-27)

Six user-requested edits layered on M5, delivered as Room schema v2 + UI rework (design: `docs/superpowers/specs/2026-09-26-m5x-ui-edits-design.md`):

- Filled teal **ACTIVE CLUB** trigger button, top-right under VIEW.
- **Club types** (Driver…Putter) with type-first bag/picker ordering; shared add-club form with TYPE dropdown + TEST toggle.
- **Session-scoped TEST clubs** — purged at session end, shots keep the label and show an amber TEST badge in history.
- History **ORDER | CLUBS** toggle: per-club AVG/σ rows over a unified 11-column shot row.
- **Shot exclusion ticks** — mishits drop from averages/σ/session chips (persisted; counts + CSV export unchanged by design).
- Standing **50–350 m distance signs** (POV perspective boards + top-down icons) replacing the painted band lines.

**Exit:** device-verified over a live M5 install (includes Room migration v1→v2); merged as `a24e5aa`.

## M5.5 — Games: Target Practice & Break the Pane (shipped 2026-09-29)

Two practice games layered on the proven shot pipeline, delivered with Room schema v3 `game_results` (design: `docs/superpowers/specs/2026-09-29-games-target-practice-break-pane-design.md`):

- **Target Practice** — pick a distance (50–350 m) and difficulty; 5 shots scored 25/15/10/5 by distance bands from the ball's **rest position** (rollout counts). Archery-bullseye target face sized to the outer band with ring score labels; per-shot scores reveal only after the ball finishes rolling.
- **Break the Pane** — floating 3×3 pane at 20% of the target distance (cells 1.6%/2.0% of target, middle row anchored to the reference trajectory's pane-crossing height); a shot breaks a cell only if it flies through an unbroken cell **and** rests on the green; score = shots taken to break all 9 (lower is better).
- **Shared**: GAMES tab + mode dispatcher (game shots never enter range sessions), speed chips (1x/1.5x/2x/4x) with real-flight pacing and snap-back, range dressing (mow stripes + distance boards via the shared `RangeDecorations` painter), right-side shot metrics panel, follow-cam, and HISTORY > GAMES with per-game-type grouping plus RECENT/BEST sorting.

**Exit:** both games playable LIVE + demo, scored and persisted; device-verified over five amendment rounds; final whole-branch review clean; merged as `19ecc6d`.

## M6 — Bag Mapping & True Gapping (shipped 2026-10-06)

Guided full-bag workflow, automatic misread/duff filtering, real carry averages, carry matrix, distance-gap visualization. UI design pass on design system.

**Exit:** complete full-bag session → matrix + gap chart.

### M6 follow-ups (shipped 2026-10-07)

Five post-M6 refinements (design: `docs/superpowers/specs/2026-10-07-bag-rollout-polish-design.md`; plan: `docs/superpowers/plans/2026-10-07-bag-rollout-polish.md`):

- **Launch Direction (HLA) everywhere** — signed DIR chip in the range LAST SHOT panel and the shared games metrics panel; DIR column + per-club average in history (− left / + right, M4c-verified).
- **Bag mapping order selector** — intro-screen ORDER choice: SHORT → LONG (default, LW → driver) or RANDOM shuffle; applied to the plan before the session snapshot so kill/resume keeps the sequence.
- **Range view while collecting** — the BAG tab keeps the live range/tracer view (extracted `RangeLiveView`) with the collecting panel as a compact overlay card.
- **Dual-metric results** — each club row plots carry (teal) and total (blue) boxes on one shared metre axis; drill-down rows show both; gap flags stay carry-based.
- **Rollout recalibration (physics)** — driver fairway rollout bounded to a realistic 18–35 m band via a continuous low-spin relief law across thetaCrit; rough stops shorter than fairway for identical landings (test-enforced); the straight-vs-left rollout asymmetry narrows. Tour-average and green pins bit-identical; range/green surfaces unchanged.

**Exit:** full build + all module tests green; cross-lane review approved; pushed as `b3fb514`.

## M7 — Club & Shaft Testing Engine (shipped 2026-10-08)

Head-to-head A/B sessions (shaft vs shaft, head vs head), side-by-side metric overlays, standard deviations, dispersion ovals. UI design pass on design system.

**Shipped 2026-10-08.** FIT tab: live COMPARE comparison card (ADD COMPARISON, per-club colours), RESULTS table (per-club metrics, noise-dimmed deltas, per-shot exclusion, AREA), TOP-DOWN with rotated minimum-enclosing-ellipse dispersion rings + auto-fit, read-only HISTORY, persisted Room v5 with kill/resume, PDF export via share sheet. Spec: `docs/superpowers/specs/2026-10-08-m7-club-fitting-design.md`.

**Follow-up (roadmap 2026-10-08):** top-down view should show the same fairway width as the range view. *(delivered 2026-10-12 — the fitting top-down now uses the range's fairway/rough shading with distance/lateral scale references)*

**Exit:** Shaft A vs Shaft B comparison report.

## M8 — Custom Combines (shipped 2026-10-10; refinements 2026-10-11; sim-condition scoring 2026-10-13)

Catalog 18-shot combines — WEDGES (50/70/90 m) and APPROACH (100/150 m + driver) — interleaved station flow, per-station strokes-gained handicap estimates plus an overall HC, Room v6 persistence, HISTORY drill-in (design: `docs/superpowers/specs/2026-10-10-m8-custom-combines-design.md`). First-run fixes from the first demo combine (scoring clamp, target/pin timing, demo tagging); driver station re-rated against per-band typical drives; fairway narrowed to a constant 28 m corridor. Post-ship refinements: results redesign, TOP-DOWN dispersion view, Room v7 rest coordinates (design: `docs/superpowers/specs/2026-10-11-m8-post-ship-refinements-design.md`).

**Sim-condition scoring (2026-10-13):** the real-course SG tables flattered shed results (a real ~2.5 HC session solved to TOUR+). Replaced with sim-conditioned pricing — `SimExpectedStrokes` (flat-green putt model, shed-discounted real-course fairway/rough, analytic outcome grid) + fitted per-station `CombineConversions` (anchors: 100 m +3, 150 m −1, driver +8, overall 2.5) — behind the single `CombineResults.shotSgForBand` seam, pinned by `CombineScoringGateTest` (design: `docs/superpowers/specs/2026-10-12-sim-condition-combine-scoring-design.md`). The 2026-10-12 session now scores ≈2.8 HC (100 m +3.0 / 150 m −1.0 / driver +8.0).

**True-scale top-down (2026-10-13):** the results TOP-DOWN view renders one px/m on both axes (true circles/ellipses), crops the distance window to the session's content (25 m snapping, scales for 300 m drives), shows ±10/±20 m lateral guides, and wears the fitting top-down's palette (framed pane, rough ground, mown stripes).

**Exit:** custom combine configurable and scoreable. **Shipped.**

## History nav + fitting results redesign (shipped 2026-10-13)

Four-tab history sidebar (SESSIONS | GAMES | COMBINES | FITTINGS) with per-game switcher and push-down drill-ins; fitting results rebuilt as a horizontal top-down pane (tee left, fairway/rough shading, distance/lateral scale references) stacked under the metrics table, per-shot columns in the PDF export (design: `docs/superpowers/specs/2026-10-12-history-nav-and-fitting-results-design.md`). Merged from `history-nav-fit-results`.

## v0.1.0 — first public release (shipped 2026-10-13)

GitHub release `v0.1.0` with a **signed** release APK (verified with `apksigner`, tag `v0.1.0`). README rewritten as user-facing docs: what the app does, install, an ordered 5-step monitor-connect flow (turn on → connect/align in the Rapsodo app → authorize → disconnect there → connect this app), per-tab usage, and build/release notes. Release signing stays local-only: a gitignored `keystore.properties` + `golfsim-release.jks` at the repo root sign `:app:assembleRelease` (falls back to unsigned when absent, so CI/fresh clones stay green). Back up the keystore — upgrades must use the same key. Commits: `0058204`, `e9ee1e2`.

## M9 — Hardening

BLE disconnect/reconnect recovery, error states, settings, local CSV export, tablet performance.

**Exit:** unattended shed use end-to-end.

---

## Cross-cutting Conventions

- Pure modules (decoder, physics) stay unit-testable without hardware — given inputs → same outputs.
- Golden BLE captures are accumulated from real shed sessions and are the regression baseline for the decoder.
- No feature screen is built without a design pass grounded in the M3 design system.
- Single-device constraint is hard: no cloud/PC dependencies at any milestone.
