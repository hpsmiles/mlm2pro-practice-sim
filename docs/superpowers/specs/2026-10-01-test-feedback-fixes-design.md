# Test-Feedback Fixes — Design (2026-10-01)

Fixes for issues found during on-table testing of the range and practice games.
Seven items; each lists root cause, change, and verification. No Room schema
changes, no BLE changes. All physics changes stay pure + JVM-tested.

## 1. Post-landing side spin (ball always goes straight after landing)

**Root cause.** `BounceRollModel` (`core/physics/.../BounceRollModel.kt`) preserves the
incoming horizontal direction exactly through every bounce and the roll: it only reads
the spin component along the travel tangent (`omegaT`, from `spin.x`/`spin.y`). The
side-spin component (`spin.z`) never enters the ground model, and no lateral deflection
is representable in the hop math.

**Change.** In `BounceRollModel`:
- At each bounce, add a small lateral velocity kick perpendicular to the travel
  direction, proportional to the side-spin component at impact and the impact speed,
  capped, and scaled by the surface's `spinbackScale`.
- During the final roll, rotate the roll direction gradually toward the side-spin
  direction so the ball keeps drifting the way the spin was working.

Rejected alternative: full 3D friction-force ground simulation — overkill and hard to
tune; violates the deterministic/simple physics discipline.

**Verification.** New JVM tests: a shot with positive `spinAxisDeg` deflects
post-landing lateral drift opposite to a negative one (anti-symmetry); zero side spin
is unchanged vs. the current model; high-side-spin rollout direction differs from
flight-end direction. Full suite: `.\gradlew.bat build`.

## 2. Punch rollout too low on fairway (assume firm fairway)

**Root cause.** (a) The range simulates on `Surface.FAIRWAY_NORMAL`
(`RangeSession.kt:63`); (b) shallow impacts (< `thetaCrit` ≈ 16.6°) bypass the Penner
bounce branch and use `newTan = vh * retention` with retention ≤ 0.55, then the final
roll starts from a small residual speed and `v²/(2a)` on a soft-ish fairway decel
(0.294 m/s²). Punch shots convert almost none of their landing speed into roll.

**Change.**
- Default range surface becomes firm fairway: `FAIRWAY_NORMAL.withFirmness(Firmness.FIRM)`
  (`cor ×1.08`, `rollDecel ×0.60` → 0.177 m/s²).
- Angle-aware bounce retention in `BounceRollModel`: for shallow impacts (below
  `thetaCrit`), retention rises as impact angle decreases and as impact spin is low
  (skid/release), while steep high-spin shots keep the current checking-up behavior.
  Constants tuned in implementation, pinned by tests.

**Verification.** JVM tests: a punch-profile shot (low launch, low spin) landing on
firm fairway produces materially more rollout than today and more than a high-spin
wedge at the same landing speed; wedge behavior regression-pinned.

## 3. Custom range green (user sets distance + size; none if not entered)

**Root cause / current state.** Three hardcoded greens in `RangeScene.greens`
(`RangeScene.kt:56-60`) are rendered unconditionally (`PovRangeCanvas.kt:103-114`);
a second, separate hardcoded list draws teal target ovals (`PovRangeCanvas.kt:158-186`).
Range physics is a decoupled `ZoneTable`, unrelated to the drawn greens.

**Change.**
- Replace the fixed list with a single **optional** custom green
  (`RangeScene.Green(lateralM=0, distanceM, radiusM, fringe)` style), held as state in
  `RangeSession`; session-scoped, not persisted.
- New range green setup UI (slider + text field sync pattern copied from
  `GameSetupScreen`, `GamesScreen.kt:146-169`): distance and size inputs; empty → no
  green anywhere.
- Rendering: `PovRangeCanvas` draws the optional green; the hardcoded teal target ovals
  are removed — the custom green itself is the target. No green → plain range.
- Physics follows the visual: with a green present, `RangeSession` simulates with
  `GreenZoneSurfaceProvider(0.0, distanceM, radiusM)`; without, `UniformSurface(firm
  fairway)` (item 2).

**Verification.** Updated `RangeSceneTest` / `RangeSignsTest` (no fixed greens);
new tests for optional-green rendering inputs and provider wiring; manual check on
tablet.

## 4. Range mat in all modes

**Root cause.** Mat drawing is private to `PovRangeCanvas.matPath`
(`PovRangeCanvas.kt:203-253`); `GameScene.drawGameGround` (`GameScene.kt:76-81`) draws
ground/signs but no mat, so games have no hitting mat.

**Change.** Extract the mat painter into `RangeDecorations.drawRangeMat(cam, w, h)`
and call it from `GameScene.drawGameGround` (covers Target Practice + Break the Pane)
and from `PovRangeCanvas`. Top-down already draws a mat.

**Verification.** Manual visual check in both games; existing tests keep passing.

## 5. Pane plane closer

**Root cause.** `PaneGeom` puts the pane at 25% of the target distance; user finds it
too far.

**Change.** `PaneGeom.planeYM` fraction: 0.25 → **0.20 × target** (28 m at a 140 m
shot). Cell sizes unchanged. Ensure `zRefM` derives from
`ReferenceTrajectory.paneCrossingHeightM(target, planeFraction)` so the middle row
re-anchors to the new plane automatically.

**Verification.** Updated `PaneGeomTest` pins (35.0 / 2.24 / 2.80 → new plane value);
`ReferenceTrajectoryTest` if it pins 0.25 defaults.

## 6. Bigger pane-game green on all difficulties

**Root cause.** Green radii @140 m: EASY 10 / MEDIUM 8 / HARD 6 m — medium reads
small, hard smaller still; user wants all raised.

**Change.** `BreakPaneGreen.radiusAt140m`: EASY 10 → **12**, MEDIUM 8 → **10**,
HARD 6 → **8** (scaling with `targetM/140` unchanged).

**Verification.** Updated `BreakPaneGreen` tests; on-green scoring still bounded by
the new radius in `BreakThePaneGame`.

## 7. Left panel overdrawn by the range view during flight

**Root cause.** The NavRail is an opaque sibling in the root `Row` and cannot be
overlapped in layout — but Compose does **not** clip drawing to composable bounds by
default, and no `clipToBounds`/`clipRect` exists anywhere in the app. During the
follow-cam chase the flight canvas paints outside its own bounds onto the rail.

**Change.** Add `.clipToBounds()` to the canvas containers: `RangeScreen`'s
`BoxWithConstraints` (both POV and top-down paths) and, defensively, the game play
canvas containers (`TargetPracticePlay`, `BreakPanePlay`).

**Verification.** Manual: fire a high-fade/high-draw shot in DEMO and confirm the
NavRail stays clean during the chase; unit tests unaffected (pure-canvas change).

## Rollout order

1. Item 7 (one-liner, unblocks visual verification)
2. Items 2 + 1 (physics: surfaces/bounce model + tests)
3. Item 3 (custom green + provider wiring)
4. Item 4 (mat extraction)
5. Items 5 + 6 (constants + test pins)

Final gate: `.\gradlew.bat build` (assemble + all unit tests) and a tablet
`installDebug` pass over range + both games.

## Addendum 2026-10-01 (post-tablet): ground-roll recalibration

FlightScope Trajectory Optimizer cross-check (195.5 m carry / 5200 rpm / 16 deg
/ axis 4.3L -> roll 2.3 m) against our 22.3 m on firm fairway drove three
BounceRollModel changes:

1. Steep-arrival roll brake: a final bounce arriving at/above thetaCrit gets
   roll decel x(1..4), scaled by the FIRST-impact grip ratio (R*omega/vh, ramp
   0.30-0.45). Spinner arrivals check (FS case now 5.3-7.8 m); low-ratio
   driver-class arrivals (TrackMan pga-driver 13.1 yd, pga-3-wood 16.3 yd)
   roll untouched.
2. Fairway/rough steep branch blends raw Penner with the retention bounce over
   arrival steepness (STEEP_BLEND_SPAN_RAD 0.35): punchy 20-25 deg arrivals
   release; tour-iron 45+ deg arrivals keep the full Penner check.
3. FAIRWAY_NORMAL rollDecel stays 0.030 g (tour-calibrated); the brake, not a
   slipperier fairway, bounds checked-up run-out.

Six TrackMan tour fixtures re-pinned (rollout + total): lpga-7i 0.8->1.1,
lpga-driver 9.1->10.4, lpga-pw 0.7->0.5, pga-5i 8.9->3.7, pga-7i 2.8->1.4,
pga-pw 2.3->1.2 (yards). Known residual: pga-5i rollout 3.7 vs 8.9 source ?
its first-impact grip ratio (~0.44) is indistinguishable from the FS case by
(angle, ratio); accepted to fix the user-visible spinner case.
