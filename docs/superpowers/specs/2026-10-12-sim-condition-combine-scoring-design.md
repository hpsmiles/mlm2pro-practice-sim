# Design: Sim-condition combine scoring — SG rebuild + shed→real conversion

Date: 2026-10-12
Builds on: `docs/superpowers/specs/2026-10-10-m8-custom-combines-design.md` (M8 combines + strokes-gained handicap scoring)
Status: approved design; implementation plan to follow

## 1. Problem

M8 scores combine shots against real-course strokes-gained tables (`SgTables.kt`, generated from the tapinstats CSV; per-lie grids × 4 skill bands). Playing the APPROACH combine on 2026-10-12, the owner — a 0.0 handicap golfer who self-rates that session at ≈ 2.5 HC — solved to **−5.8 ("tour-or-better")**, roughly 8 strokes too generous.

Root cause: the tables price real-course costs the shed does not impose — wind, lie variance, green break/speed variance, thick rough, and real penalty stakes for offline shots. The session's measured quality (12/12 greens, 5.4 m / 6.8 m average proximity, 236 m / 14.5 m offline driving) is tour-level *for shed conditions*; no position-pricing scheme can rate it much worse while staying band-relative.

Cross-checks: the owner's Rapsodo combines rate ≈ 5.5 HCP for similar shot quality (their report: overall 83.9 → 5.5 HCP; 100 m proximity 6 m; 145 m proximity 5 m; driving 264 m / 14 m offline); the engine's relative station profile (approach ≫ driver) matches the owner's self-description ("approach is my strength, driver ok but not a strength"). The relative SG signal is sound; the absolute level is not.

## 2. Decision

Two-layer scoring replaces the single-layer real-course SG path:

1. **Layer 1 — sim-native pricing.** Positions are valued at their in-shed cost. Fixes relative distortions: green vs rough vs fairway finishes, band compression across stations, driver-station sensitivity to rough rests.
2. **Layer 2 — shed→real conversion.** Per-station fitted SG constants convert the shed-internal solve into real-course-equivalent HCs, calibrated to the owner's declared anchors (§6): `CONVERSION_APPROACH_100M`, `CONVERSION_APPROACH_150M` (interpolated/extrapolated between stations), and `CONVERSION_DRIVER`.

Considered and rejected:
- *Calibration offsets on raw real-course SG.* Mechanism-blind: leaves rough/green/driver structure distorted, and needs station-differentiated offsets that are pure curve-fitting.
- *Rapsodo-style average-based station scoring.* Loses lie-awareness (an 8 m finish in fairway short ≠ 8 m on green) and discards the working SG ledger.
- *Pure layer 1 without layer 2.* Evaluated and rejected: it still rates the 2026-10-12 session ≈ tour-level overall (≈ −6), because tour players produce tour-level proximity in sheds too. The shed→real gap is a session-level conversion, not a position-pricing effect.

## 3. Layer 1 — sim-native pricing (new pure module `core/scoring/SimExpectedStrokes.kt`)

JVM-pure, deterministic, metres throughout. Real-course tables remain the substrate, corrected by bounded sim-relief terms.

### 3.1 End values — `simEndValue(band, lie, m)`

- **GREEN** (the dominant finish state): two-state flat-green putt model:
  `E_sim_green(band, d) = 1 + (1 − p_sim(band, d)) · γ`
  - `p_sim` — one-putt probability on a dead-flat, known-speed green; logistic in `d` with per-band inflection `d50` (TOUR ≈ 3.2 m falling to HCP18 ≈ 1.8 m) and slope `k ≈ 0.4 /m`, seeded from published make-rate tables shifted favourably for zero break-reading and no speed variance.
  - `γ ≈ 1.05` — expected follow-up strokes after a missed first putt (tap-in range on a flat green).
  - Relief shape: vanishes at tap-in distance, grows with distance (long-range 3-putt removal); no upper bound is guarded — the honest-zone constraint is relief ≥ 0 for d ≥ 3.0 m. The real scratch table prices an 18 ft putt at 1.89; flat-sim says ≈ 1.75.
- **FAIRWAY** (missed green): `E_sim = (1 − κ_fairway) · E_real`, seed `κ_fairway ≈ 0.10` (range 0.05–0.15). Flat, predictable surrounds make chips slightly cheaper.
- **ROUGH**: `E_sim = (1 − κ_fairway) · F + (1 − κ_rough) · (R − F)`, where `F = E_real(FAIRWAY, m)` and `R = E_real(ROUGH, m)` — a relieved-penalty form that removes a bounded share of rough's excess over fairway rather than discounting rough's absolute cost. Seed range 0.12–0.20; the calibration scan settles at the 0.12 boundary and the driver anchor is absorbed by CONVERSION_DRIVER instead.

  **Finding — the multiplicative rough form inverted rough below fairway.** The real-course tables keep rough ≥ fairway everywhere (minimum gap ≈ +0.11 strokes for all bands), so the original multiplicative form `(1 − κ_rough) · E_rough` prices rough *cheaper* than fairway at long approach distances — e.g. TOUR from 220 m — breaking the model's own rough-punishes-worse invariant for every seed in the §8 ranges (0.12–0.20). The relieved-penalty form above preserves ordering by construction across the entire fitting grid: rough is always fairway plus a nonnegative, damped excess `(1 − κ_rough)·(R − F)`. The shed removes some of rough's sting, never all of it, never flips it.

### 3.2 Start values — `simStartValue(band, lie, m)`

Derived, not independently discounted (independent start/end discounts break zero-sum):
`E_sim_start(band, d) = 1 + Σ_i p_band(i | start=d) · E_sim_end(i)`

`p_band` is a parametric analytic outcome distribution per band and start distance:
- Seeds from published real-course band stats (GIR rate, proximity mean/SD) for 100 m / 150 m starts — declared starting points: 100 m: TOUR GIR ≈ .95 / prox ≈ 5.8 m; SCRATCH ≈ .88 / 7.5 m; HCP10 ≈ .75 / 10 m; HCP18 ≈ .60 / 12.5 m; 150 m: TOUR ≈ .85 / 7.5 m; SCRATCH ≈ .70 / 10.5 m; HCP10 ≈ .55 / 13.5 m; HCP18 ≈ .40 / 16 m (exact values with source citations pinned in code at implementation).
- Shed adjustments (declared parameters): `shedTightening` — fraction of real-course dispersion variance removed (no wind, flat lie; seed ≈ 0.25) — and miss relocation (the 30 m flat green catches short/long misses; no bunkers or OB).
- Integration is deterministic at load time — no runtime Monte Carlo.

### 3.3 SG functions

- `simStrokesGained(band, startLie, startM, endLie, endM) = simStartValue − simEndValue − 1`
- `simDrivingStrokesGained(band, restLie, restDistanceM) = simEndValue(band, FAIRWAY, DRIVER_HOLE_M − band.typicalDriveM) − simEndValue(band, restLie, restDistanceM)` — same baseline-relative shape as today; baseline and rest relief largely cancel at similar distances, rough relief on rests is the material term. `DRIVER_HOLE_M = 340.0` (module-local, mirroring the app constant).

### 3.4 Guards (JVM-tested)

- Monotone-increasing in distance per lie; relief ≥ 0; sane value floors.
- `E_sim ≤ E_real` (relief ≥ 0) for d ≥ 3.0 m (below 3.0 the flat-sim model may conservatively overprice tap-ins; the overcharge is absorbed by Layer 2); tap-in E(0.3) ∈ (1.0, 1.15]; no cross-lie ordering guard (chipping from tight fairway beats an equal-length putt on the real substrate).
- Band ordering: `TOUR ≤ SCRATCH ≤ HCP10 ≤ HCP18` for every priced position.
- **Zero-sum:** a band golfer playing their own modelled distribution scores SG ≈ 0 against their own band anchor (layer 1 only, pre-conversion). Zero-sum is exact by construction (E_start = 1 + Σ p·E_end).

## 4. Layer 2 — shed→real conversion

- Fitted SG offsets applied per shot, one per station: `CONVERSION_APPROACH_100M`, `CONVERSION_APPROACH_150M`, and `CONVERSION_DRIVER` — the DRIVER value fitted at −0.065042 SG/shot (encodes the missing real-course penalty stakes for offline drives).
- Applied to per-shot SG at the scoring seam before any solve, so station estimates, the overall solve, per-shot chips, and the locked benchmark band all shift consistently.
- Fitted by 1-D numeric root-find on the calibration session (§6) so each station solves to its declared anchor.
- Rationale recorded with the constants: the approach conversion represents how much shed proximity overstates real-course proximity (wind, lie variance, forgiving green); the driver conversion represents the missing real-course penalty stakes for offline drives (no OB/trees/water in the shed).
- **Per-station constants (empirical outcome):** the single APPROACH constant failed on the calibration data (raw station gap 2.21 HC vs anchor gap 4 HC), so the primary path is three fitted constants — CONVERSION_APPROACH_100M, CONVERSION_APPROACH_150M, CONVERSION_DRIVER. The WEDGES combine uses `approachOffsetFor(startDistanceM)`: linear interpolation between the two fitted approach stations, linear extrapolation outside [100, 150].

## 5. Seam (the only app change)

`CombineResults.shotSgForBand` swaps `strokesGained` / `drivingStrokesGained` for the sim variants and adds the station-kind conversion. Everything downstream is unchanged: `handicapMatchSg`, `SkillBand` anchors and `typicalDriveM`, locked-band logic, results views, Room schema, storage. Games/bag/fitting scoring untouched. The WEDGES combine inherits the same layer automatically (its stations use the APPROACH/DRIVER conversions until its own anchors exist).

## 6. Acceptance gate (calibration session: 2026-10-12, 18 shots)

Owner-declared anchors (real HC 0.0; session self-rated ≈ 2.5 HC):

| Station | Measured session facts | Anchor |
|---|---|---|
| 100 m | avg to pin 5.4 m, 6/6 green (per shot 5.75 / 2.54 / 6.83 / 5.80 / 8.70 / 2.81 m) | **+3 HC ± 1.0** |
| 150 m | avg to pin 6.8 m, 6/6 green (per shot 1.18 / 4.86 / 12.72 / 6.95 / 5.78 / 9.07 m) | **−1 HC ± 1.0** |
| DRIVER | avg total 236.2 m, avg offline 14.5 m, 4/6 fairway (offline per drive 6.1 / 8.9 / 26.1 / 12.9 / 21.5 / 11.3 m; rough rests are shots 10 and 16) | **+8 HC ± 1.0** |
| Overall | — | **2.5 HC ± 1.0, emergent (not fitted)** |

Fitting procedure: per-combo knob grid (κ_rough, γ, κ_fairway, shedTightening within §3/§8 ranges), three 1-D bisects (one per station anchor), accept when emergent overall ∈ [1.5, 3.5]; report back if no combo passes.

Procedure: fit layer-1 parameters and layer-2 constants against this session, then re-run the exact stored rows through production `combineResults` (scratch-test pattern) — all four targets within tolerance ⇒ gate passed.

Tuning rules: only the declared parameters (`d50` per band, `k`, `γ`, `κ_fairway`, `κ_rough`, `shedTightening`, distribution seeds) within the documented ranges; stop as soon as the gate passes; record seeds and finals in §8. The constants live in code; any later change goes through this document.

## 7. Testing plan

- New pure module: parameterised JVM tests for every guard in §3.4, plus zero-sum per band.
- No-regression: existing `SgTables` / `HandicapMatch` tests untouched and green; combine flow, Room schema, and UI unchanged.
- Calibration reproduction: scratch-test rerun of the 2026-10-12 rows through production `combineResults` prints the corrected ledger — the concrete before/after evidence (current production output: overall −5.8, stations −6.6 / −10.5 / +4.7).

## 8. Tuning log (filled during implementation)

| Parameter | Seed | Final | Note |
|---|---|---|---|
| κ_fairway | 0.10 | 0.05 | |
| κ_rough | 0.15 | 0.12 | seed 0.15; calibration scan settled at the grid's lower boundary (0.12) |
| γ | 1.05 | 1.0 | |
| d50 (TOUR/SCRATCH/HCP10/HCP18) | 3.2 / 2.75 / 2.2 / 1.8 m | 3.2 / 2.75 / 2.2 / 1.8 m | unchanged |
| slope k | 0.4 /m | 0.4 /m | unchanged seed |
| shedTightening | 0.25 | 0.3 | |
| CONVERSION_APPROACH_100M | — | −0.169254 | fitted on §6 session (SG/shot) |
| CONVERSION_APPROACH_150M | — | −0.221857 | fitted on §6 session (SG/shot) |
| CONVERSION_DRIVER | — | −0.065042 | fitted (missing penalty stakes) |

Winning combo κ_rough 0.12 / γ 1.0 / κ_fairway 0.05 / shedTightening 0.3 → station estimates 3.000 / −1.000 / 8.000 exactly on anchors, emergent overall 2.834 (gate 2.5 ± 1.0) on the 2026-10-12 calibration session.
