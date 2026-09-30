# Spec: Physics-faithful post-land bounce rendering (range view)

Date: 2026-09-30
Status: approved design, awaiting implementation plan
Scope: `core/physics` (additive data) + `app` range rendering (`RangeRollout`, `PovRangeCanvas`)

## 1. Problem

After touchdown the post-land phase reads as a painted line on the ground:
`RangeRollout.samples` synthesises a single flat quadratic ease-out from
carry to rest at `pz = 0`, the amber tracer (2.5 px) continues at full
weight across it, and the glowing ball dot slides to a stop. The physics
engine (`BounceRollModel`) already models a real bounce chain — each hop is
solved with `FlightSolver.solve` (displacement, apex height, air time) — but
discards the geometry, keeping only total deltas in `GroundResult`.

User intent (2026-09-30): the post-land phase should look more realistic —
visible bounce hops in the ball path **and** in the tracer line, with the
tracer rendered thinner once it is on the ground.

## 2. Decision (Approach 1 — physics-faithful)

Expose the per-bounce records the engine already computes and render the
real hop chain. Alternatives rejected:

- *App-only cosmetic hops*: invented bounce count/speeds, no tie to the
  model, wedge spin-back faked.
- *Full hop trajectories in `ShotResult`*: captures every hop's complete
  solver samples — max fidelity but bloats the UI contract; overkill.

What the player sees will be exactly what the model computed (including
genuine backward hops for wedge spin-back on greens).

## 3. Data model (core/physics, additive only)

1. New `GroundHop` data class: `landingX`, `landingY` (touch point,
   metres), `apexM` (hop apex height), `durationSec` (hop air time).
2. `GroundResult` gains `hops: List<GroundHop> = emptyList()`.
   - The bounce loop records one `GroundHop` per `FlightSolver` hop solve:
     cumulative touch position (existing `dx`/`dy` accumulation), `apexM`,
     `flightTimeSec`. Up to `MAX_BOUNCES − 1` hops.
   - The final touch → rest displacement stays the existing roll delta
     (`deltaX`/`deltaY` unchanged; rest position semantics untouched).
   - Invariant (test-pinned): cumulative recorded touch points + final
     roll displacement == total ground delta.
3. `ShotResult` gains `groundHops: List<GroundHop> = emptyList()`,
   populated by `BallFlightEngine` from `GroundResult`.
   - Additive default → all existing consumers compile unchanged. Games
     persist only summaries in Room — no schema impact.

No numerical behaviour of the engine changes; carry/rollout/total/apex/
flight-time pins are untouched by construction.

## 4. Ground-phase geometry and timing (app)

`RangeRollout` keeps its name and role but becomes hop-aware:

- **Geometry**: for each recorded hop, a parabolic arc from touch *i* to
  touch *i+1* (~8 samples), apex `pz = hop.apexM` (unscaled — hops are
  ≤ ~2 m; the apex clamp only matters for big drives; accepted
  approximation). After the last touch, a flat roll segment (quadratic
  ease-out on the remaining distance, preserving today's 3.5 m/s² feel)
  ending exactly at `totalM`.
- **Timing**: ground phase duration = Σ hop durations + roll duration,
  uniformly time-scaled into `[MIN_DURATION_SEC, MAX_DURATION_SEC]`
  (unchanged: 0.3 s – 1.8 s). `endFraction` and `LAND_HOLD_SEC` are
  untouched; the last ground sample still rests exactly at `totalM` so the
  follow-cam parking logic (`descentRig` at rest) stays continuous.
- **Spin-back** (`rolloutM < 0`): hop paths run backward; arc `pz` values
  stay positive. The rest dot lands behind the landing marker.
- **No-rollout** (`rolloutM == 0` / no hops): empty ground samples, as today.

## 5. Rendering (PovRangeCanvas)

- `pathSamples` = apex-clamped flight + hop-aware ground samples; the ball
  head follows the same list as now (unchanged plumbing).
- Tracer styling: the path splits at `tSec = flightTimeSec` and strokes in
  two passes — flight portion unchanged (2.5 px `GolfColors.Amber`), ground
  portion thinner (1.5 px, `GolfColors.Amber.copy(alpha = 0.8f)`). Bounce
  touches touch down at `pz = 0`, so hops read as distinct arcs.
- History tracers and previous-shot rest dots get the same hop geometry and
  ground styling automatically (same `pathSamples`).
- Landing dot/ring (carry) and resting dot (total) unchanged.

## 6. Tests

- `core/physics` (pure, deterministic):
  - Hop recording: hop count ≤ MAX_BOUNCES − 1; apexes > 0; durations > 0;
    cumulative touches + roll == ground delta; deterministic repeat.
  - Existing pins green unchanged (`BounceRollModelTest` wedge reversal,
    spin-dominance gate tests, `TourAveragesTest` table, rest-position pins).
- `app` (`RangeRollout`, JVM):
  - Samples start at carry, end exactly at `totalM`, per-hop `pz ∈ (0, apex]`,
    monotonic `tSec`, total duration ≤ 1.8 s.
  - Spin-back case produces backward hops with positive apexes.
  - Zero-rollout shot yields empty samples.
- Full `.\gradlew.bat build` census green.

## 7. Acceptance

- [ ] `GroundResult.hops` / `ShotResult.groundHops` populated for all shots; engine numbers bit-identical to before (tour table pins green).
- [ ] Range view: ball visibly bounces after touchdown; tracer shows the same hop arcs, thinner on the ground; spin-back shots bounce backward.
- [ ] Follow-cam hold continuity preserved (head parks at rest; snap-back unchanged).
- [ ] Full build census green; no Room schema change.

## 8. Out of scope / follow-ups

- `TopDownCanvas` hop rendering.
- Ground effects (landing dust puff, bounce kick marks, ball mark).
- Ball ground shadow + dimmed grounded ball.
