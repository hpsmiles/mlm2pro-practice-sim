# Design: Real-trajectory tracer + painted range (Option B)

Date: 2026-09-24 · Status: approved (design discussion 2026-09-24)

## Goal

1. The POV tracer follows the **actual modelled ball flight** (ODE positions), not the
   stylized 40-point quadratic Bézier currently fit to `ShotResult` summaries.
2. The POV range reads as a **painted practice ground**: fairway, rough, mow stripes,
   target greens with fringes, horizon haze. Still 100% Compose Canvas + `PovProjector`
   — no 3D engine, no new dependencies, single-device constraint respected.

## Non-goals

- No trees, no 3D meshes, no Filament/SceneView.
- No topography in this phase, but the scene gains a `groundHeight(x, y)` seam
  (defaults to 0) so future terrain is a data change, not a renderer rewrite.
- Top-down canvas, tracer toggles, DEMO/LIVE flow, metric panels: unchanged.

## Stream 1 — Real-trajectory tracer

### Physics (`:core:physics`)

- New `TrajectorySample(px: Double, py: Double, pz: Double, tSec: Double)` data class.
- `FlightSolver.solve` collects per-step positions (native dt = 10 ms; a 7 s flight is
  ~700 samples) and returns them via a new `samples: List<TrajectorySample>` field on
  `LandingState`.
- `BallFlightEngine.simulate` copies them onto a new `samples` field of `ShotResult`.
  Additive change; all existing consumers compile unchanged.
- **Bit-identical guarantee:** sample collection must not alter integration math.
  Existing golden values in `FlightSolverTest` / engine tests stay exactly as-is.
- Memory: a few thousand Doubles per shot in `RangeSession.shots` — negligible.

### Rendering (`PovRangeCanvas.kt`)

- Delete the stylized `tracerPath()` quadratic + apex-rescale; replace with:
  project `shotResult.samples` → polyline (drawn portion chosen by
  `playFraction × flightTimeSec` vs each sample's `tSec`).
- Ball head = projected sample at the current animation time; landing ring unchanged.
- Keep the launch-anchor clamp (near-field points below the bottom edge snap to the
  anchor).
- Framing: if the real apex projects above the top margin (top 8% rule), apply the
  existing apex-scale logic (single scalar z multiplier) to the real samples so large
  apex shots stay in frame.
- History lines draw the same sampled polylines, teal/faded as today.

## Stream 2 — Painted range

### Scene definition (new file `RangeScene.kt`, pure Kotlin, JVM-testable)

- `groundHeight(x, y): Double` — seam, returns 0.0 everywhere this phase.
- Fairway: tapered polygon from tee, ~40 m wide at y = 10 m, centered x = 0, to y = 180 m.
- Rough: everything else out to y = 200 m (darker/coarser tones).
- Greens: 3 target greens with fringe rings (e.g. at lateral −12/0/12 m, distances
  75/100/150 m — matching existing target oval positions), pushed through the same
  world coords so POV and top-down agree.
- Target ovals remain drawn on top (they are the aim UI, not scenery).

### Draw order (all flat polygons via `PovProjector.project`, `drawPath`)

1. Ground base + rough polygons
2. Fairway polygon
3. Mow stripes: alternating bands every 12 m down-range on the fairway, two lightness
   levels of the same hue, projected trapezoids
4. Greens: shading gradient (brighter centre → fringe), fringe ring
5. Distance-band lines + labels (existing, labels drawn last among ground layers)
6. Horizon haze: alpha gradient overlay over the top ~15% of the ground area

### Landing marker

Landing dot/ring unchanged in behavior; it already projects to ground level and will
sit naturally on the painted surface.

## Testing

- `TrajectorySample` collection: solver test asserts first sample ≈ launch step, count
  ≈ flightTimeSec/DT, monotonic t; golden summary deltas untouched.
- `RangeScene`: polygon vertices inside expected bounds; stripe/green surface lookup
  returns expected `Surface` per (x, y); `groundHeight` contract (0 everywhere this phase).
- Rendering is deterministic; manual smoke on the FIRE button + speed multipliers.

## Effort

- Stream 1 ~0.5 day · Stream 2 ~1–1.5 day. Total ~2 days.
