# Follow Cam, Raised Viewpoint & Range Mat — Design

Date: 2026-09-25 · Branch `m4d-live-shots` · Status: approved by user (this session)

## Motivation

The static POV camera (1.7 m eye height, 4.5 m behind the tee) is a player-perspective view: big driver apexes exit the top of frame, and the whole 0–180 m flight cannot be watched in one static frame. Research (this session, via @librarian) confirmed that sim/broadcast range framings use elevated cameras, and that GSPro-style follow cams translate with the ball after impact. Decisions locked in brainstorming with visual mockups (persisted in `.superpowers/brainstorm/175-1790250692/content/`: `viewpoint-heights.html` option C, `mat-styles-v2.html` option B2):

1. **Ball follow cam** after impact, with a descent pitch-in ending in a ~45° view of the landing area.
2. **Raised static viewpoint**: 8 m high crane position.
3. **Range mat**: commercial rubber-base + turf-strip mat, 2.5 × 2.5 m, ball on the right (right-handed golfer).

## 1. Camera & projection (`PovProjector`)

`PovProjector` is generalized from hard-coded constants to an explicit camera value:

```kotlin
data class RangeCamera(val x: Double, val y: Double, val z: Double, val pitchRad: Double)
```

- World camera position + pitch (nose-down angle; 0 = level). No yaw, no roll — one rotation axis only.
- `RangeCamera.STATIC = (0, −CAM_BACK_M, CAM_HEIGHT_M, 0)` (camera sits behind the tee at negative world y) where **`CAM_HEIGHT_M = 8.0`**, **`CAM_BACK_M = 21.2`** (tee stays at `v ≈ 0.377`, visible just above the bottom edge — matches approved mockup C).
- Projection with pitch θ, relative point `(dx, dy, dz)` where `dz = z − camZ`:
  - `depth = dy·cosθ − dz·sinθ`
  - screen `u = dx / depth`
  - screen `v = −(dy·sinθ + dz·cosθ) / depth`
  - At `pitch = 0` this is exactly today's pinhole (`u = x/depth`, `v = (camH − z)/depth`).
- Points at/behind the camera plane (`depth <= 0`) still return null.
- `bandV(distanceM)` generalizes to the camera: ground bands remain screen-horizontal under pitch (rotation is about the lateral axis).
- Static view keeps pitch 0, so the existing address/launch/review framing (horizon at 30% from top) is unchanged.

## 2. `FollowCam` — phase machine (new pure module)

Pure function `(ShotResult, playFraction) → RangeCamera`. No Android deps. All numbers are named tunable constants at the top of the file.

Timeline, with `t` = seconds since impact, `T = flightTimeSec`, `t_apex` = time of max-z sample (found from `samples`):

| Phase | Camera | Bounds |
|---|---|---|
| Static window | `STATIC`, pitch 0 | `t < FOLLOW_DELAY_SEC` (1.5 s), **or early-engage**: ball's static projection reaches near the top of frame — whichever is first (big apexes are never lost waiting for the timer) |
| Blend | smoothstep lerp STATIC → chase rig (target moves during blend), pitch 0 | `BLEND_SEC = 0.8` |
| Chase | ball + `(0, −CHASE_BACK_M, +CHASE_UP_M)` = (0, −20, +2.5), lateral x tracks ball, pitch 0 | until descent pitch-in |
| Descent pitch-in | ease from chase rig → landing overlook rig, driven by `progress = (t − t_apex)/(T − t_apex)` (smoothstep) | apex → touchdown |
| Landing hold | overlook rig parked | `LAND_HOLD_SEC = 2.5` |
| Snap back | cut to `STATIC`, pitch 0 | after hold; full tracer + landing marker persist, tee ball returns |

**Landing overlook rig**: positioned behind-and-above the landing point so the camera axis passes through the landing spot at the pitch angle — `position = landing + (0, −OVERLOOK_DIST_M·cosθ, OVERLOOK_DIST_M·sinθ)` with **`OVERLOOK_DIST_M = 25`**, **`LAND_PITCH_DEG = 45.0`** (user: "45° may not be right but it's a starting value" — this constant is the tuning knob; at 45° the horizon sits above the top of frame and the whole screen is the landing approach). Pitch-ease and position-ease share the same `progress` so the camera noses down as the ball falls, reaching 45° exactly at touchdown with the landing area centered.

Edge cases:
- **Short shots** (landing before delay/blend would finish): skip straight to landing hold — never point the camera at empty sky.
- **New shot during any phase**: animation restarts at 0 (existing `LaunchedEffect(session.tick)`), camera snaps to STATIC automatically.
- Deterministic: same inputs → same outputs, always.

## 3. Animation driver (`RangeScreen`)

- `playFraction` extends past 1 to `1 + LAND_HOLD_SEC / flightTimeSec`; the animation loop's stop condition and `coerceAtMost` change accordingly.
- Tracer still draws to `min(fraction, 1) · flightTimeSec`.
- Waiting-ball condition (`playFraction >= 1f`) moves to "after snap-back" (the extended end).
- The existing speed multiplier scales the whole timeline (hold included) — consistent with slow-mo review.

## 4. Range mat

New pure geometry (constants object, e.g. `RangeMat`; no behavior):
- Rubber base: 2.5 × 2.5 m quad, ball **0.35 m** from the right edge (right-handed golfer; mat extends left toward the stance area). World coords: ball at (0,0); base spans x ∈ [−2.15, +0.35], y ∈ [−1.25, +1.25].
- Turf strip inset: approx 1.8 × 1.9 m (x ∈ [−1.75, +0.05], y ∈ [−0.95, +0.95]).
- Thin hitting line through the ball (x ∈ [−0.01, +0.01], length of strip).
- Colors (approved mockup B2): base `#22262B`, edge stroke `#16191D`, turf strip `#3B7D4C`, strip edge `#2E5F3B`, hitting line `#356B44`.
- Drawn in `PovRangeCanvas` through the per-frame camera (it sweeps past during chase — launch-speed feel). The mat's near edge is below the frame bottom — reads like the mat runs up to the hitter's feet.
- `TopDownCanvas` gets a small mat quad at the origin for orientation.

## 5. Tee ball visibility

At `CAM_BACK_M = 21.2` a true-scale ball projects to ~2 px. The waiting tee ball uses a minimum on-screen radius (~4 px + proportional glow). In-flight head dots are already fixed-pixel and unchanged.

## 6. Testing

- `PovProjectorTest`: update hand-computed values for the new static rig (constants change from 1.7/4.5 — existing expectations break by design); add pitched-projection cases (e.g. 45° down-looking at a known point) and camera-relative null cases.
- New `FollowCamTest`: phase boundaries; early-engage rule; blend monotonicity; chase offset; pitch ≈ 0 through chase, `LAND_PITCH_DEG` at touchdown and through hold, 0 after snap-back; short-shot degradation; extend-fraction driver math; determinism (same inputs → identical cameras).
- Physics goldens untouched (`FlightSolver`, `TrajectorySample` unchanged).
- Full `.\gradlew.bat build` green; `:app:installDebug` on the tablet; manual check of the whole follow sequence (demo driver + wedge + live shot), history behavior during chase, top-down mat.

## Out of scope

- No yaw/roll, no FOV change (focal 1.1w stays).
- TOP-DOWN view camera behavior (unchanged aside from mat quad).
- Left-handed layout / configurable mat (constants assume right-hander; single-user shed app).
- Tracer style, history fading, target visuals (unchanged).
