# M2 — Ball-Flight Engine Design Spec

**Date:** 2026-09-20
**Status:** Approved design (user-signed sections 1–4)
**Branch:** `m2-ball-flight` (off `master@62bd017`)

## 1. Goal & Exit Criteria

Build the deterministic ball-flight engine in `:core:physics`: given measured launch
conditions (ball speed, launch angle/direction, spin, spin axis), simulate 3D flight
and ground contact, and produce a `ShotResult` — carry, rollout, total distance, side
drift, apex height, flight time. Realism target: reproduce published Tour average
carry/total distances within a regression gate.

**Exit criteria:**
- The engine reproduces all 8 Tour-average shots (PGA/LPGA driver, 3W, 5i, 7i, PW)
  within **±3 yd carry / ±5 yd total** on default surfaces.
- Fully deterministic: identical inputs → bit-identical outputs, no hardware or
  platform dependencies, all paths unit-tested (including spin-back, firm/soft,
  wind).
- Consumes M1's `BallData` mapping without changes to `:core:ble`.

## 2. Background & Sources

Research (librarian, 2026-09-20) surveyed open-source golf-flight models:

- **Model family chosen: "wind-tunnel-fitted"** — Reynolds-binned lift polynomials and
  Re-piecewise drag, used identically by `gdifiore/libgolf` (GPL-3 — constants
  cross-reference ONLY, no code) and `digitalhand/openfairway` (MIT — primary
  constants source). Ultimate provenance: Bearman & Harvey (1976) and
  Lyu/Kensrud/Smith/Tosaya wind-tunnel data (MDPI Proceedings 2(6):238, 2018).
- Rejected: "classical analytic" family (`Cd = 0.24 + 0.18·S`) — too coarse for
  launch-monitor-grade realism; and porting openfairway's stacked Flightscope-tuned
  correction layers (±45% lift boosts) — unexplainable knobs, no physics.
- Ground model constants: openfairway's MIT `SurfacePhysicsCatalog` +
  `BounceCalculator` (Flightscope-calibrated), Penner's review
  (Rep. Prog. Phys. 66:351-400, 2003 — the `2Rω/7` spin-back impulse),
  stimpmeter physics (measured USGA fact: release at 1.83 m/s; decel = 5.49/stimp
  m/s²), Roh & Lee (Procedia Engineering 2:3167, 2010) and
  Biber et al. (Sports Engineering, 2023).
- Calibration gate data: TrackMan "PGA/LPGA Tour 2023 averages" as bundled by
  libgolf's `test/data/shots_reference.csv`.

**Licensing posture:** lift/drag/ground constants and formulas are facts (not
copyrightable) taken from MIT-licensed openfairway and published papers; libgolf
(GPL) is used only to cross-check numbers. All code is our own Kotlin.

## 3. User Decisions (all confirmed in design review)

1. **Intent: realism for practice** — sim numbers must translate to real-course
   behavior (total distance, release, spin-back).
2. **Surface zoning by landing spot** — physics consults an injected
   `SurfaceProvider`; M2 ships simple zone-table defaults, M4's real target
   layouts implement the same interface.
3. **Surface vocabulary: green / fairway / rough + firmness modifiers** — 3 base
   parameter sets × firm/normal/soft scalings. No hazards, no fringe, no slopes in M2.
4. **Outputs: summary values only** — no trajectory sample array. Engine is
   deterministic so M4 may re-solve if arcs are ever wanted.
5. **Air model: wind vector + air density from temperature/pressure.** No humidity
   or altitude in M2.
6. **Approach B: fitted physics** (wind-tunnel coefficients, physical bounce-roll
   ground model, Tour-regression-gated).

## 4. Module Layout & Component Contracts

All in `:core:physics`, package `com.hpsmiles.golfsim.core.physics`. Pure JVM, no
Android dependencies, no dependency on `:core:ble` (M4 wires `BallData` →
`LaunchConditions`).

```
LaunchConditions(speedMps, launchAngleDeg, launchDirDeg, spinRpm, spinAxisDeg)
Environment(windVectorMps, temperatureC, pressureHpa)   // → airDensity kg/m³
BallFlightEngine.simulate(launch, environment, surfaces): ShotResult
  ├─ FlightSolver      — ODE integration until z ≤ 0 (fixed dt, symplectic Euler)
  │    └─ AerodynamicModel — Cd/Cl/spin-decay pure functions of state
  ├─ BounceRollModel   — bounce loop (COR + Penner spin-back) then roll to rest
  │    └─ SurfaceProvider.surfaceAt(x, y): Surface
  └─ ShotResult(carryM, rolloutM, totalM, sideM, apexM, flightTimeSec)
```

Contracts:
- `BallFlightEngine` — the public entry point, replaces the M0 `BallFlight` marker.
  Deterministic; validates constructor-boundary inputs (see §7); returns SI doubles,
  **no rounding anywhere**.
- `AerodynamicModel` — pure functions: `dragCoefficient(Re, S)`,
  `liftCoefficient(Re, S)`, `spinDecay(omega, dt)`. KDoc cites paper/repo sources.
- `FlightSolver` — integrates position/velocity/spin with wind-relative aerodynamic
  forces until the ball reaches ground; emits `LandingState` (position, velocity,
  spin).
- `BounceRollModel` — consumes `LandingState` + `Surface`; loops bounces, then
  rolls under constant deceleration to rest. Hard bounce cap prevents non-termination.
- `SurfaceProvider` — one-method interface; M2 defaults: `UniformSurface(surface)` and
  `ZoneTable` (rules by distance: e.g. green inside target oval, fairway mid, rough
  beyond 250 m).
- `Environment` — derives density via ideal-gas law from temperature and pressure.

## 5. Aerodynamic Model

State: position (x lateral, y down-range, z up), velocity (m/s), spin vector ω
(rad/s). Forces on wind-relative velocity `v_rel = v − v_wind`:

```
F_drag   = −½ · ρ · A · Cd · |v_rel| · v_rel
F_magnus =  ½ · ρ · A · Cl · |v_rel|² · (ω̂ × v̂_rel)
```

- **Drag coefficient** (Reynolds-piecewise, dimpled ball):
  - Re > 200,000 → Cd = 0.200
  - 50,000–200,000 → cubic fit through the drag crisis:
    `Cd = 1.1948 − 2.09661e-5·Re + 1.42472e-10·Re² − 3.14383e-16·Re³`
  - Re ≤ 30,000 → Cd = 0.38; smoothstep-blended from 0.38 to 0.4632 across
    Re 30,000–50,000 (joining the cubic at its 50k value)
  - Cd minimum floor: 0.223
  - Spin multiplier: `Cd *= min(1 + 4·S², 1.20)` where S = r·ω/|v_rel|
- **Lift coefficient** (Bearman-binned in S at four Re points, verbatim from
  openfairway/libgolf, both ultimately Bearman & Harvey data):
  - `Cl_50k(S) = 0.0472121 + 2.84795·S − 23.4342·S² + 45.4849·S³`
  - `Cl_60k(S) = 0.320524 − 4.7032·S + 14.0613·S²`
  - `Cl_65k(S) = 0.266667 − 4.0·S + 13.3333·S²`
  - `Cl_70k(S) = 0.0496189 + 0.00211396·S + 2.34201·S²`
  - Below Re 50k: ramp (smoothstep) from zero lift toward the 50k bin
  - Above Re 70k: Hill saturation, `Cl = ClMax(S)·S·g/(1 + S·g)`, g = 16
  - `ClMax(S)`: 0.268 → 0.320 linear across S ∈ [0.35, 0.50]
  - Linear interpolation between adjacent Re bins; 50k/60k/65k/70k anchors
- **Spin decay in flight**: exponential, `ω *= exp(−dt/τ)`, default **τ = 5 s**
  (Flightscope-calibrated openfairway value). Named parameter — tunable via the
  regression gate; flagged for live-capture verification (sources spread 5–35 s).
- **Integration**: fixed-step **symplectic Euler**, **dt = 10 ms**. Deterministic
  by construction; accuracy within inches of reference solutions at this step size.
- **Air density**: ideal-gas law from temperature and pressure,
  ρ = P/(287.05 J·kg⁻¹K⁻¹ · T). Defaults: 15 °C, 1013.25 hPa → 1.225 kg/m³.

**No correction-layer stacking**: none of openfairway's tuned lift/drag boost
layers. If the regression gate fails, named physical parameters change.

## 6. Ground Model

On touchdown (`z ≤ 0`), decompose velocity into surface-normal/tangential parts;
the surface comes from `SurfaceProvider.surfaceAt(x, y)`.

**Bounce phase** (loops, hard cap 4 bounces; collapses to roll below ~4 m/s normal
energy):
- Normal: `v_n' = −COR_eff · v_n`
  - Base law: `COR(v_n) = 0.45 − 0.01·v_n + 0.0002·v_n²`, capped at 0.25 above
    20 m/s, zero below 2 m/s
  - Spin reduction: COR reduced 0% → 30% across 0–1500 rpm, up to 70% ≥ 3000 rpm
    (backspin digs in; wedges don't ricochet)
- Tangential:
  - Steep+fast (impact angle ≥ θ_crit AND |v| ≥ 20 m/s) — Penner branch:
    `v_t' = retention · |v| · sin(θ − θ_crit) − 2R·ω_t·spinbackScale/7`,
    with first-bounce retention `0.55 · clamp(1 − rpm/8000, 0.40, 1)`. The
    `2Rω_t/7` rigid-body impulse can reverse the ball (wedge, soft green).
  - Shallow/low-energy: `v_t' = (1 − μ_k · (1 − firmness)) · v_t`
- Spin retention after bounce: parameterized (fairway 0.75, green 0.85).

**Roll phase**: constant deceleration in the 2D ground plane to rest:
- Green: **stimp-anchored** — decel = 5.49/stimp m/s² (Stimpmeter: 1.83 m/s
  release; USGA bands). Default green = stimp 11.
- Fairway/rough: rolling-μ parameters (0.050 / 0.095 default)
- Lateral velocity decays at the same deceleration; rest when speed < 0.1 m/s.

**Surface parameter defaults** (openfairway MIT catalogue, cross-checked libgolf):

| Surface | COR base | μ_kinetic | roll μ | θ_crit (rad) | spin scale |
|---|---|---|---|---|---|
| Green | 0.45 | 0.58 | stimp-based | 0.36 | 1.12 |
| Fairway | 0.40 | 0.50 | 0.050 | 0.29 | 0.78 |
| Rough | 0.35 | 0.62 | 0.095 | 0.35 | 0.70 |
| Firm modifier | higher COR | 0.30 | 0.030 | 0.25 | 0.60 |
| Soft modifier | lower COR | 0.56 | 0.070 | 0.32 | 0.92 |

Firmness is a **modifier** applied to the 3 base turfs (soft/normal/firm), built
from the openfairway Firm/FairwaySoft column pairs — not 9 separate presets.

**Ball physical constants**: mass 45.93 g, radius 21.335 mm (R&A/USGA maximums).

## 7. Error Handling & Validation

- **Constructor-boundary validation throws** (programmer errors): negative spin rpm,
  ball speed outside [0.5, 100] m/s, launch angles outside real ranges, negative
  temperature/pressure, wind speed > 40 m/s.
- **Runtime weirdness yields sane results** (never throws): S = 0 (Cl = 0, no
  division by zero — all spin ratios guarded), extreme spins (12,000 rpm), ±90°
  spin axis, chip that lands 1 m away and only rolls.
- Determinism is absolute: pure Kotlin doubles, fixed dt, no wall clock, no
  randomness, no platform math dependencies beyond basic arithmetic + `exp`.

## 8. Calibration & Testing

**Regression gate (the exit criterion):** 8 drop-in `.properties` fixtures in
`core/physics/src/test/resources/tour/`, same table-driven Parameterized harness
pattern as M1's `golden/`:

| Shot | Ball speed | VLA | Spin | Carry (yd) | Total (yd) | Rollout (yd) |
|---|---|---|---|---|---|---|
| PGA driver | 171.5 mph | 10.4° | 2545 | 275 | 295 | 20 |
| PGA 3-wood | 162.0 mph | 9.3° | 3663 | 243 | 260 | 17 |
| PGA 5-iron | 135.0 mph | 14.8° | 5280 | 194 | 205 | 11 |
| PGA 7-iron | 123.0 mph | 16.3° | 7124 | 172 | 179 | 7 |
| PGA PW | 102.0 mph | 24.2° | 9304 | 136 | 140 | 4 |
| LPGA driver | 140.0 mph | 13.2° | 2611 | 218 | 238 | 20 |
| LPGA 7-iron | 99.5 mph | 17.1° | 6417 | 141 | 148 | 7 |
| LPGA PW | 82.0 mph | 24.6° | 8525 | 111 | 115 | 4 |

TrackMan "Tour 2023 averages" (as bundled by libgolf's shots_reference.csv; the
widely circulated Tour-averages table). Assert **carry ±3 yd, total ±5 yd**; the
rollout ladder emerges from the ground model, not fitted per-club.

**Unit test matrix:**
- AerodynamicModel: Cd plateau/crisis/floor values, Cl bin-boundary continuity,
  spin-factor caps, spin-decay law
- FlightSolver: determinism (repeat-solve bit-identical), monotonicity (headwind
  shortens carry, more spin → different apex), no-spin arc ≈ pure ballistic+drag
- BounceRollModel: COR law shape, Penner branch only in steep+fast window,
  spin-back can produce **negative rollout** (wedge, soft green), bounce cap,
  stimp-anchored decel values (stimp 10 → 0.549 m/s²)
- BallFlightEngine: end-to-end — draw vs fade curve opposite directions; firm >
  normal > soft rollout ordering; zoning — same shot hitting the green zone
  stops shorter than the fairway zone
- Edge cases from §7: zero spin, ±90° axis, extreme spin, chip

**Gate policy:** failures are fixed by tuning named physical parameters (τ, drag
spin cap, firmness scalings) — never by adding an invisible correction layer or
per-club hacks.

## 9. Out of Scope (M2)

- Trajectory sample arrays (summary only; re-solve later if M4 wants arcs)
- Course layouts, slopes, elevation — flat ground (charter: Clean Practice is
  flat target grids)
- Hazards: bunker/water (M4 games)
- Humidity/altitude in the air model
- Putting (MEASUREMENT characteristic carries no putts — M1 finding)
- UI/display units (M3+ converts SI → yards/mph at display)

## 10. Verify Against Live Capture (mirrors M1 §9)

When the M4 GATT client produces real shed captures, verify:
1. **Spin-decay τ** — the 5 s default is the widest-spread constant (sources
   5–35 s); compare predicted vs actual carries across spins.
2. **Fairway firm/soft rolling friction** — the only constants with no measured
   primary source (simulator-tuned column pairs today).
3. **HLA / spin-axis sign interpretation** — side-curve direction depends on
   which sign is draw vs fade (M1 unknown, preserved raw through the decoder).
4. **COR-vs-speed parabola shape** vs Biber et al. 2023 measured green bounces.
Golden-fixture workflow from M1 extends: every capture becomes a new regression
fixture with the measured landing truth (observed carry/roll where capture allows).
