# Benchmark Report: our ball-flight & ground model vs Trackman truth and OpenGolfSim/fuse

**Date:** 2026-10-16 · **Branch:** `fuse-compare` (worktree) · **Status:** analysis complete, model work pending

## TL;DR (read this first)

Feeding real launch conditions into our engine and into fuse, then comparing both against the
monitors' **own reported carry/total/apex** (1,325 real shots + 24 Foresight reference rows):

1. **Neither engine matches truth well: both carry SHORT.** Sea-level carry bias ours ≈ −6…−8%,
   fuse ≈ −5…−11% (MAPE 6–14% depending on dataset). On Trackman as benchmark neither wins outright:
   fuse better with measured altitude conditions (8.8% vs 11.3% MAPE), ours better at sea level (8.8% vs 13.6%).
2. **Our altitude response is wrong-signed vs Trackman:** at 807 hPa (647 shots, ~1,900 m) giving our
   engine the *correct* measured conditions makes our carry MAPE **worse** (12.5% env vs 9.5% sea),
   while measured carries at altitude get *longer*. Fuse (whose Magnus term ignores air density —
   physically wrong) improves as expected (8.3% vs 13.9%). **P1 fix: air-density/lift/drag/Re response.**
3. **Wedges are our worst class:** −12% carry vs Trackman in BOTH env modes; Foresight's published LW
   table agrees (ours 11.3% short). Fairway metals/drivers are much closer (4–6%).
4. **Totals:** Trackman total MAPE ours 15–16% vs fuse 10–13% — fuse's generous rollout is nearer truth
   here, but per-class is mixed (our fairway-metal rollout 54.7 m vs fuse 29.0 m mean). Needs a
   carry-vs-rollout decomposition against measured `total − carry`.
5. **Sign conventions verified:** our side sign agrees with Trackman's signed carry deviation 99.2%
   (fuse 98.2%) — Trackman launch-direction/spin-axis signs map 1:1 to ours, no mirroring.

**Prior engine-vs-engine result (65-shot own bag session, no truth available):** fuse carried
+4.7% farther than us on average (carry MAPE 5.6%, rollout +7.4 m mean) — i.e. most of fuse's
"longer than us" gap moves *toward* Trackman truth, except at sea level where we beat it.

## 1. What was run

True engine-vs-engine over recorded launch conditions (fairway everywhere, zero wind, dt = 10 ms):

- **Ours:** `BallFlightEngine.simulate` via env-guarded JUnit test
  `core/physics/.../FuseCompareHarnessTest.kt` (input TSV via `FUSE_COMPARE_IN`, output via
  `FUSE_COMPARE_OUT`; no-ops when unset so the normal suite is unaffected).
- **fuse (OpenGolfSim/fuse, TypeScript):** real `BallPhysics` class, flat `THREE.PlaneGeometry`
  fairway, stepped at 10 ms to `isEnded`, bundled with esbuild. Frame mapping: fuse `+HLA` and
  `+spinAxis` curve toward −x → harness negates both (probe confirmed: straight shot side = 0,
  `hla+5` → −21.3 m, `axis+10` → −19.0 m). mph↔m/s converted (fuse Shot takes mph).
- **Benchmark TSV v2 columns:** `id, clubName, ballSpeedMps, launchAngleDeg, launchDirDeg,
  spinAxisDeg, totalSpinRpm, cachedCarryM, cachedTotalM, filtered, includeOverride, tempC,
  pressureHpa, airDensityKgM3, measuredApexM` — cols 11+ optional (bag TSVs stop at col 10 →
  default env). Our harness reads temp/pressure; fuse reads only the air-density column
  (its one ambient knob; `BallPhysics.airDensity` public field).
- Rows outside `LaunchConditions` guard ranges dropped at conversion so both engines see
  identical rows (Trackman 16 dropped of 832 → 816).

## 2. Data sources

| dataset | source | n used | has measured carry/total/apex | env data | license/caveat |
|---|---|---|---|---|---|
| Trackman (PRIMARY) | Kaggle "Golf Swing and Trajectory Data" (Jamie Bain), mirrored raw+clean CSV in github.com/edwardxiong2027/golfswing-trajectory (MIT code) | 816 | carry + total + apex, signed carry/total deviation, spin type "Measured" | per-shot temp F, pressure kPa (80.7 & 101.3 kPa), air density | Kaggle license page not verified (personal benchmark OK; check before redistribution). One player, 4 sessions, club classes inferred (`club_class`) |
| FlightScope Mevo | github.com/sghill/golf (range/, 20 CSVs 2018–19) | 485 | carry + apex (Height) only; **no** direction/axis/total (base-tier export) | none (standard env) | no LICENSE file (default all-rights-reserved — personal use) |
| Foresight | official published Ball Launch Tables (2023 Ball & Club Data PDF + help-center) | 24 | carry only | none | published reference rows, NOT session exports; static |
| Own bag session | local Room DB session 2 (65 shots) | 62 kept | none (cached carry/total ARE our own engine output — bit-identical rerun) | none | used for engine-vs-engine only |

Raw URLs: `https://raw.githubusercontent.com/edwardxiong2027/golfswing-trajectory/main/data/golf_trajectory_clean.csv`
(clean — use it: fixes negative smash, adds `session_id`, `club_class`) and
`https://github.com/sghill/golf`.

## 3. Results (full tables: temp workspace, see §5)

### 3.1 Headline — carry vs measured

| dataset | mode | n | ours MAPE% | fuse MAPE% | ours bias% | fuse bias% | ours-vs-fuse MAPE% |
|---|---|---|---|---|---|---|---|
| trackman | measured-env | 816 | 11.3 | **8.8** | −11.3 | −4.5 | 11.2 |
| trackman | sea-level | 816 | **8.8** | 13.6 | −8.0 | −11.1 | 8.0 |
| mevo | standard | 485 | **6.4** | 7.0 | −6.3 | −6.7 | 3.1 |
| foresight | standard | 24 | 4.2 | **3.7** | −2.4 | +0.8 | 5.5 |

Total vs measured (trackman only): ours 16.5%/15.4% (env/sea), fuse 10.0%/13.1%.
Apex vs measured (>1 m): trackman ours 14.6%/9.0%, fuse 10.2%/9.8%; mevo ours 21.5%, fuse 15.1%.

### 3.2 The altitude anomaly (Trackman split by session pressure)

| group | n | ours env | ours sea | fuse env | fuse sea |
|---|---|---|---|---|---|
| high-alt (807 hPa, ρ≈0.95) | 647 | 12.5 | **9.5** | **8.3** | 13.9 |
| sea (101 kPa) | 169 | 6.6 | **6.1** | **10.7** | 12.2 |

Ours gets *worse* with correct conditions at altitude; fuse gets better. Our lift scales with ρ
(and our Cd is Re-dependent, Re∝ρ) so low ρ shortens our carry; Trackman-measured reality at
altitude lengthens (drag loss dominates). Fuse's magnusCoeff has no ρ term (wrong physics, right
empirical direction here). **At sea level we beat fuse on this dataset (6.1% vs 10.7%).**

### 3.3 Trackman per-class (measured carry / ours / fuse, MAPE%)

| class | n | meas | ours(env) | fuse(env) | ours% | fuse% | ours%(sea) | fuse%(sea) |
|---|---|---|---|---|---|---|---|---|
| unknown | 69 | 134.7 | 119.0 | 130.4 | 12.1 | 10.1 | 9.9 | 16.3 |
| long_iron_hybrid | 273 | 136.4 | 122.4 | 126.9 | 11.1 | 9.3 | 8.5 | 15.0 |
| fairway_metal | 86 | 124.8 | 117.6 | 124.3 | 6.5 | 7.7 | 5.0 | 9.2 |
| mid_iron | 253 | 113.1 | 99.5 | 106.9 | 13.0 | 9.9 | 9.5 | 14.7 |
| driver | 6 | 154.6 | 145.1 | 158.8 | 6.0 | 5.4 | 4.0 | 6.5 |
| wedge | 44 | 84.2 | 73.9 | 82.7 | **12.6** | 6.3 | **12.1** | 10.0 |
| short_iron | 85 | 112.9 | 101.3 | 108.5 | 11.0 | 5.2 | 9.6 | 10.5 |

Foresight speed-tier crossover: slower tier fuse clearly better (hy22 1.2% vs ours 8.3%; lw 2.4%
vs 11.3%); faster tier ours better (7i 2.9% vs fuse 9.3%; 6i 2.2% vs 9.2%) — consistent with the
bag-session finding (fuse's τ≈0.7–1.4 s spin decay starves lift at high speed).

### 3.4 Verification notes

- Sign check: |sim side|>0.25 m & |measured dev|>1 m → agreement ours 99.2% / fuse 98.2% (n=776).
- Our rerun of the own-bag cached values: bit-identical (0.000000 m) — engine deterministic.
- All runs: 0 errors, 0 DNFs (4,282 simulations total).

## 4. Improvement backlog (assuming Trackman = benchmark)

Prioritized; each item = observation → investigation → validation.

### P1 — Air-density response (altitude): wrong-signed carry response
- **Evidence:** §3.2. Env mode adds −3.0 pp MAPE for us at altitude; bias −11.3% vs −8.0%.
- **Where to look:** `AerodynamicModel.kt` (lift ∝ ρ? Cl polynomials), `Environment.airDensity`,
  `FlightSolver` force assembly, and the Re-dependent Cd piecewise (Re∝ρ — lower ρ may walk Cd
  into a *higher* band, stacking drag increase on top of lift loss).
- **Target:** d(carry)/d(altitude) > 0 and ≈ Trackman's; our current physics makes it < 0 for irons.
- **Validation:** add paired unit tests — identical launch at 1013 hPa vs 807 hPa must carry
  farther at 807 hPa; pin against a few measured high-alt Trackman rows (§P5).

### P2 — Systematic sea-level carry shortfall (~−6…−8%)
- **Evidence:** trackman sea group ours 6.1% MAPE / −8.0% bias overall; mevo −6.3%; foresight −2.4%.
  Fuse is similarly short but less so on trackman env (−4.5%).
- **Candidates:** Cd magnitude/limits (floor 0.38, caps, CdMin 0.223), lift polynomials /
  `clMax(S)`, spin decay τ=12 s (lift lost early in trajectory?), and **fixture provenance**:
  `TourAveragesTest`/`pgaDriver` pins (e.g. PGA driver 275.9 y carry at 171.5 mph) may come from
  older/other sources than Trackman — reconcile what our green pins actually encode before
  recalibrating (AGENTS rule: keep the pin set green or consciously re-pin with dated notes).
- **Method suggestion:** per-launch-condition error curve (carry residual vs ball speed/spin/VLA)
  from `benchmark.csv` — systematic shape tells you Cd-vs-Cl vs spin-decay apart faster than
  single-number MAPE.

### P3 — Wedge/high-spin carry (~−12% vs truth, both modes)
- **Evidence:** wedge class 12.6/12.1 (env/sea); Foresight LW table ours 11.3% short.
  Independent of altitude → separate mechanism from P1.
- **Candidates:** Cl behavior at high S (S = ωR/v large for wedges: 11,000 rpm at 33 m/s),
  spin-decay too aggressive early, or Cd spin multiplier `1+8S²` over-dragging high-spin balls.
- **Validation:** wedge rows (trackman `club_class=wedge`, mevo PW/GW) as pinned fixtures.

### P4 — Rollout / total distance
- **Evidence:** Trackman total MAPE ours 15–16% vs fuse 10–13%; our rollouts run much lower than
  fuse everywhere (bag: +7.4 m mean fuse−ours; mevo per-club ours 2–7 m vs fuse 9–19 m) but
  fairway_metal ours 54.7 m vs fuse 29.0 m (mixed!). Carry bias is more negative than total bias
  → rollout partially compensates, masking carry error.
- **Task:** decompose measured `total − carry` per class vs our `rolloutM` (data ready in
  `benchmark.csv`: mCarry/mTotal/oRoll/fRoll) before touching `BounceRollModel`.
  Re-tuning rules: steer with `RolloutSweepDiagnostic`, keep pin set green (AGENTS.md),
  dated comments in `BounceRollModel`/`Surface`.

### P5 — Turn the benchmark into permanent regression tests (do early, cheap)
- Pick ~10–15 golden Trackman rows (sea-level subset, spread across classes incl. a wedge, plus
  2–3 high-alt rows), convert to a committed fixture (launch conditions + measured carry/total),
  and pin OUR outputs with tolerances (e.g. ±3% carry) the way `TourAveragesTest` does.
  Gives the model work a target beyond "MAPE went down on a temp CSV".
- Fixtures must be re-derivable: URLs in §2 + conversion spec in §1/§5.

### What NOT to copy from fuse
- Fuse is reference-only (PolyForm Noncommercial — **no code copying**), zero tests, hand-tuned
  lookups. Its wins (ρ-independent Magnus, looser ground roll) are *empirical directions*, to be
  re-derived and re-pinned inside our own validated model, not transplanted.

## 5. Reproduction & artifacts (TEMP — may be wiped)

Workspace `WS = C:\Users\smiles\AppData\Local\Temp\opencode\fuse-compare` (ephemeral!):

- Data: `trackman_raw.csv`, `trackman_clean.csv`, `mevo-golf/` (clone), `shots.tsv` (own bag).
- Scripts: `convert-benchmark.mjs` (all three datasets → v2 TSVs), `extract-shots.mjs` (own bag),
  `merge-benchmark.mjs` → **`benchmark-report.md`** (full per-club tables) + **`benchmark.csv`**
  (per-shot: inputs + measured + both engines), `merge-report.mjs` → `report.md` (bag study),
  `pressure-split.mjs` (§3.2), `profile-trackman.mjs`.
- fuse clone: `WS/fuse` (shallow clone; `npm install --ignore-scripts`; bundle:
  `npx esbuild compare-harness.ts --bundle --platform=node --format=esm --alias:@=./src --outfile=compare-harness.mjs`;
  run: `node fuse\compare-harness.mjs <in.tsv> <out.tsv>` or `--probe <out.txt>`).
- Ours: PowerShell, `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`,
  `$env:FUSE_COMPARE_IN/OUT`, then
  `.\gradlew.bat --console=plain :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.FuseCompareHarnessTest" --rerun`
  (`--rerun` required — Gradle does not track env vars; without them the test is a vacuous skip).
- Frame probe expectation: straight side≈0; +HLA/+axis (after our negation) → negative x.

## 6. Caveats

- Foresight set = published typical launch tables, not sessions; straight shots only.
- Mevo = base-tier export: no launch direction/spin axis/total → straight carry/apex only.
- Trackman: one player, mostly high-altitude sessions; `club_class` is derived, not measured;
  carry is Trackman's radar-tracked value (trusted as ground truth for this exercise).
- Environment effects only differ on Trackman (only set with env columns); Mevo/Foresight rows
  run at standard 15 °C / 1013.25 hPa on both engines.
- fuse license: PolyForm Noncommercial 1.0.0 — numeric outputs only; no code reuse.
