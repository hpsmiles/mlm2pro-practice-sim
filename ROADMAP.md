# Development Roadmap

Milestone-based sequencing. Every milestone ends demoable on the Lenovo tablet; the golden BLE capture library grows every shed session.

Sequencing approach: **walking skeleton down the pipeline** — prove the risky plumbing (BLE decode → physics) as pure, tested Kotlin first, build the Clean Practice Environment as the first live vertical slice, then layer feature modules onto the proven shot pipeline.

---

## M0 — Scaffold

Gradle + Kotlin + Jetpack Compose project, minSdk 31, module layout, `.gitignore`, GitHub Actions CI (build + unit tests on every push).

**Exit:** skeleton app installs and runs on the Lenovo tablet; CI green.

## M1 — BLE Byte-Decoder (pure Kotlin, TDD)

Port MLM2PRO byte mappings from open-source references (Duwaynef/MLM2PRO-BT-APP, springbok/MLM2PRO-GSPro-Connector). Capture real GATT notification payloads with the device in the shed and commit them as golden fixtures. Decoder is pure and deterministic: raw bytes → Ball Speed, Launch Angle, Launch Direction, Spin, Spin Axis.

**Exit:** decoder reproduces all captured metrics for every golden fixture.

## M2 — Ball-Flight Engine (pure Kotlin, TDD)

Port drag/lift ODE math from `libgolf` / `golfmodel`. Validate two ways: published reference trajectories, and side-by-side against the Rapsodo app in the shed. Outputs: carry, total distance, side-curve, rollout.

**Exit:** reference shot set matches Rapsodo within agreed tolerance.

## M3 — UI Design System (look & feel)

Decide the visual identity before building any screen:

- Theme direction (dark-first for shed/outdoor contrast), color system, typography scale, spacing, motion
- Mockups for the three signature screens: range view, carry matrix, A/B comparison — with approval gate
- Compose design-system module: theme + component kit (buttons, metric cards, charts) as code

Deliberately not designing later module screens up front — each feature milestone (M5+) gets its own UI design pass reusing this design system.

**Exit:** signed-off mockups; design-system module compiles with theme and primitives in place.

## M4 — Clean Practice Environment (first vertical slice)

Live BLE scanner + GATT client → decoder → physics → UI. Flat range grid, scaled target ovals, shot tracers, landing points, live dispersion, last-shot metric bar.

**Exit:** hit a ball, see it land within ~2s; carry matches Rapsodo.

### M4 follow-ups (requested 2026-09-24 — TODO, not scheduled yet)

- **Ball follow-cam view**: replay the flight from a camera set distance
  BEHIND THE BALL (translating with it) instead of the fixed player POV — the
  standard sim-software view. Needs a travelling-camera projection layer on
  top of the existing `TrajectorySample` stream (pure math change in
  `PovProjector`, no 3D engine).
- Visit the ball-flight eye line after multiple shots on the same spot.

## M5 — Sessions & Persistence

Room DB, session history, club tagging, past-session review.

**Exit:** sessions survive app restart.

## M6 — Bag Mapping & True Gapping

Guided full-bag workflow, automatic misread/duff filtering, real carry averages, carry matrix, distance-gap visualization. UI design pass on design system.

**Exit:** complete full-bag session → matrix + gap chart.

## M7 — Club & Shaft Testing Engine

Head-to-head A/B sessions (shaft vs shaft, head vs head), side-by-side metric overlays, standard deviations, dispersion ovals. UI design pass on design system.

**Exit:** Shaft A vs Shaft B comparison report.

## M8 — Custom Combines

User-defined target distances (e.g., 50m / 70m / 90m wedges) — not the fixed 24-shot template. Scoring, results vs targets. UI design pass on design system.

**Exit:** custom combine configurable and scoreable.

## M9 — Hardening

BLE disconnect/reconnect recovery, error states, settings, local CSV export, tablet performance.

**Exit:** unattended shed use end-to-end.

---

## Cross-cutting Conventions

- Pure modules (decoder, physics) stay unit-testable without hardware — given inputs → same outputs.
- Golden BLE captures are accumulated from real shed sessions and are the regression baseline for the decoder.
- No feature screen is built without a design pass grounded in the M3 design system.
- Single-device constraint is hard: no cloud/PC dependencies at any milestone.
