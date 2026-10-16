# Bag-mapping results views — handover (2026-10-16)

Fresh-session pickup doc. Covers the state of the bag-mapping **results page** and the two
outstanding pieces of work on it.

---

## 1. Where things stand

**Shipped (commit `d69f3e1`)** — bag misread gates + include/exclude override:
- `BagMappingStats.applyMisreadGates` / `applyAutoFilter` — per-club gates over **all** the
  club's shots (dormant below 3 shots). Reason codes `MISREAD_SMASH / CHS / SPIN /
  LAUNCH_ANGLE / DIRECTION`; precedence SMASH > CHS > SPIN > LAUNCH_ANGLE > DIRECTION.
  Tunable constants in `BagMappingStats` (lines ~31-36):
  `SMASH_PHYSICAL_MAX`=1.50, `SMASH_MEDIAN_LOW/HIGH`=0.85/1.15, `CHS_MEDIAN_LOW/HIGH`=0.95/1.05,
  `SPIN_MEDIAN_LOW/HIGH`=0.5/2.0, `LAUNCH_MEDIAN_LOW/HIGH`=0.6/1.5,
  `DIRECTION_LAUNCH_DEG`=12.0, `DIRECTION_SPIN_AXIS_DEG`=20.0.
- `BagMappingShotEntity.includeOverride: Boolean? = null` + `effectiveIncluded() =
  includeOverride ?: !filtered`. Room **v8** migration + committed schema JSON
  (`core/data/schemas/.../8.json`) + `MigrationFrom7Test`.
- `SessionRepository.recomputeAutoFilter` (on append) / `backfillBagMappingAutoFilter`
  (one-time, idempotent, run from `AppRoot`'s init `LaunchedEffect`). Never clobbers
  `includeOverride`.
- Drill-down table reworked: 9 data columns + far-right 22 dp tick, a single header row,
  a live MEDIAN row computed over the **included** shots (13 sp bold, "Median" label at
  `LabelWidth` = 68 dp), and a yellow failing-metric highlight
  (`GolfColors.Comparison.C` 0xFFF0D64A — deliberately NOT the reserved Amber). Ball speed
  and CHS render in **mph** (`BagMappingFormats.mph`, `MPH_PER_MS` = 2.23694).

**Not yet done** — the main RESULT view is still the original carry+total box-plot matrix,
and two work items remain (below).

---

## 2. Outstanding request **C** — changes to the existing matrix view (newest)

> **User (verbatim):** "The current view has statistical symbols that are confusing. Remove
> those, and add a distance scale at the top of the view (stay at the top when scrolling
> down). The C and T values are far too small, make them bigger, and under them were the
> statistical values are give a range for both C and T"

Read as:
1. **Remove the statistical symbols** (the σ / median glyphs) from the matrix view — the user
   finds them confusing.
2. **Add a distance scale pinned to the top of the view** — it must stay visible while the
   list scrolls (sticky header inside the `LazyColumn`).
3. **Make the per-club C (carry) and T (total) values bigger** — they are currently too small.
4. **Under the statistical values, show a RANGE (min–max) for both carry and total.**

Likely touch points: `app/src/main/kotlin/com/hpsmiles/golfsim/bag/BagMappingResult.kt`
(`CarryMatrixSection`, `ClubRow`), `bag/CarryMatrixCanvas.kt`, `bag/BoxPlotGeom.kt`.
`BagMappingStats.distribution(...)` already returns `min`/`max` per club, so no data-layer
change is needed.

---

## 3. Parked request **A** — new SIDE + TOP-DOWN views

> **User (verbatim):** "For the results page, I want the ability to see a spin on view which
> shows a median ball flight trace, and a top down view which shows the shots for each
> station as coloured dots with a fitted dispersion ring. The shots shown should not inlcude
> the excluded shots. The current view is useful but distance isn't the only way to
> effectively bag map, a user also need to be able to see spin and LA to help get a
> consistent trajectory gapping. That is where I am thinking the side view will give that, so
> it maybe needs the numbers also shown."

**Clarifying answer already given:** the SIDE view should show **all clubs overlaid,
colour-coded** (not one club at a time).

**Status: PARKED, mid-brainstorming.** The `brainstorming` skill is loaded and its HARD GATE
applies: **no implementation until a design is presented and approved**, then write a spec to
`docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md` and commit it, self-review, get user
review, then invoke `writing-plans`. The **visual companion was OFFERED and ACCEPTED** (guide:
`C:\Users\smiles\.config\opencode\skills\superpowers\brainstorming\visual-companion.md`; start
with `scripts/start-server.sh --project-dir <repo>`, read `$STATE_DIR/server-info` for the URL,
write HTML fragments to `screen_dir`, read `$STATE_DIR/events` for clicks).

### Recon already gathered (do not re-do)

**(a) Page structure / where a view selector goes**
- `bag/BagMappingScreen.kt:26-134` switches whole pages via
  `enum BagView { AUTO, INTRO, COLLECTING, RESULT, HISTORY }` (line 24) +
  `var view by remember` (45). RESULT renders `BagMappingResult(viewedShots: List<BagMappingShotEntity>)` (107-121).
- `bag/BagMappingResult.kt:55-110` is ONE `Column`: title, date, stale-resume banner,
  `CarryMatrixSection` (92), RETEST/HISTORY `Row` (93-100); `drillClub` state (66) toggles a
  full-screen `DrillDownPanel` overlay (102-109).
- `CarryMatrixSection` (112-176) = `LazyColumn`: legend, per-club `ClubRow` (154;
  `CarryMatrixCanvas` + stats), gap section (157-174). A new view selector slots into the root
  Column between lines 92-93. Precedent enum: `FittingView { COMPARE, RESULTS, HISTORY }`.
- Per-club grouping = `session.clubSnapshot()` (120) + `shots.filter { it.clubName == name }`
  filtered by `effectiveIncluded()` (135).
- Per-shot raw data on `BagMappingShotEntity`: full `BallData` block (`ballSpeedMps`,
  `launchAngleDeg`, `launchDirDeg`, `spinAxisDeg`, `totalSpinRpm`) + `carryM`/`totalM` +
  `effectiveIncluded()`. **Trajectory samples are NOT persisted → traces must be re-simulated.**

**(b) Dispersion-ring API + palette (reuse, do not reinvent)**
- `DispersionEllipse` lives in `fitting/FittingTopDownFit.kt` (32-242):
  `fromKept(points: List<Pair<Double,Double>>): DispersionEllipse?` (78) — Khachiyan MVEE, both
  semi-axes ×1.05 about the centre, **null** for <3 points / collinear. Fields `centreSideM`,
  `centreTotalM`, `semiAxisM`, `semiCrossM`, `angleRad`; helpers `areaM2()`,
  `projectedHalfSideM()`, `projectedHalfTotalM()`.
- Fit: `TopDownFitH(pxPerMX, pxPerMY, originX, midY)` (260) +
  `computeTopDownFitH(width, height, minDistM, maxDistM, maxAbsSideM, hasData, leftPadPx, rightPadPx, vPadPx)` (272);
  `sx(d)=originX+d*pxPerMX`, `sy(s)=midY−s*pxPerMY`.
- Palette: `FittingColors.clubColor(index) = GolfColors.Comparison.{A..F}[index % 6]`
  (`fitting/FittingColors.kt:16-23`), positional by first appearance.
- Draw recipe (`fitting/FittingTopDown.kt`): rings under dots — 72-step world path (360-371),
  white α0.55 @3.5 dp keyline + club colour α0.7 @2 dp (373-382); dots — white α0.6 @4 dp rim +
  colour α0.9 @3 dp (389-396). Ground = `RangeRough` base, `RangeFairway` 28 m corridor,
  `RangeScene.stripeBand` mown stripes (178-198). `CombineTopDownView.kt` already reuses
  `DispersionEllipse` + `FittingColors` — the model to mirror. Legend = the `FlowRow` of
  colour-dot + name + count chips in `FittingTopDownPane` (83-100).

**(c) Physics entry point for the median trace**
- `BallFlightEngine.simulate(launch: LaunchConditions, environment = Environment(),
  surfaces = UniformSurface(Surface.FAIRWAY_NORMAL)): ShotResult`
  (`core/physics/BallFlightEngine.kt:11-55`). `LaunchConditions(ballSpeedMps, launchAngleDeg,
  spinRpm, launchDirDeg, spinAxisDeg)` (throws on impossible decodes).
- `ShotResult.samples: List<TrajectorySample>`; `TrajectorySample(px, py, pz, tSec)` — 10 ms
  fixed-step flight samples ending exactly at landing. Flat side-view polyline =
  `(hypot(px,py), pz)` per sample. `RangeRollout.samples(shot)` (`range/RangeRollout.kt:101-148`)
  appends ground-phase samples.
- Wiring to mirror: `BagMappingCollector.kt:57-59` default simulator +
  `add(BallData)` builds `LaunchConditions` (92-102). A median trace = simulate once from the
  club's median launch metrics (deterministic).

**(d) Existing arc-drawing code**
- Only true trajectory polyline: `PovRangeCanvas.drawTracer` (214-286) — a 3D-projected POV
  polyline; **not reusable** for a flat side view. **No existing flat 2D distance-vs-height
  canvas anywhere** — the SIDE view is greenfield over `ShotResult.samples`.

---

## 4. Repo conventions & tooling

- **Windows**: set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before any
  Gradle command. Gradle flags (e.g. `--console=plain`) **precede** task names; Gradle is quiet
  during configure — quiet ≠ hung (runs 5 s–2 min).
- Build/test: `.\gradlew.bat build`; `:app:testDebugUnitTest` (typed variant is required for
  `--tests`); `:core:data:testDebugUnitTest`; `:core:physics:test` (pure JVM).
- Install on the tablet: `.\gradlew.bat :app:installDebug`. Tablet = **TB373FU**.
  Android does **not** hot-reload: after install, `am force-stop com.hpsmiles.golfsim` then
  relaunch (`adb shell monkey -p com.hpsmiles.golfsim -c android.intent.category.LAUNCHER 1`).
- Room: DB `golfsim.db`, current version **8**. Entity changes need a new migration, a
  committed schema JSON, and a migration test. Never enable destructive migration fallback.
- adb: `C:\Users\smiles\AppData\Local\Android\Sdk\platform-tools\adb.exe` (not on PATH).
  sqlite3 in the same dir. Pull DB:
  `cmd /c "<adb> exec-out run-as com.hpsmiles.golfsim cat databases/golfsim.db > <tmp>\golfsim.db"`
  (also `-wal`, `-shm`).
- Untracked stray `session-combine-2026-03-03.csv` at the repo root — not referenced anywhere,
  left untracked on purpose.

## 5. Suggested order

1. Request **C** (small, well-scoped, user-facing polish on the existing view).
2. Request **A** — resume the brainstorming: ask any remaining clarifying questions, propose
   2-3 approaches, present the design for approval, write the spec, then `writing-plans`.
