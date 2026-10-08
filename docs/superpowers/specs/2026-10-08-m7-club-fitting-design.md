# Design: M7 — Club & Shaft Testing Engine (FIT mode)

Date: 2026-10-08
Status: approved design, not implemented
Origin: ROADMAP.md M7 (line 101); user brief 2026-10-08 (brainstorming session)

Head-to-head club/shaft comparison: a new FIT mode with a live compare view,
a per-club results table with per-shot exclusion, and a colour-coded top-down
view with dispersion ovals. Persisted end-to-end (Room v5), following the M6
bag-mapping architecture (write-through, kill/resume, history).

User-confirmed decisions: sessions are **persisted** (not live-only) and the
architecture is a **bag-mapping clone** (dedicated tables, shots never enter
range sessions).

---

## 1. Mode & navigation

- New `RangeTab.FIT` in `AppRoot.kt` (enum ~:89), `NavRailButton("FIT")`
  (~:651–656), a `when` content branch (~:732–833), and a `routeShot` branch
  (~:264).
- Shot routing: `routeShot` captures for the fitting session whenever the
  FIT tab is active (any FIT-internal view — COMPARE, RESULTS, HISTORY alike,
  matching the "capture live" rule); the baseline state machine decides where
  the shot is shown. Switching to any other tab stops capture — no
  pause-on-leave disposal needed since the gate is the tab itself (deviation
  from BAG's `onCollectingChange` seam, agreed 2026-10-08).
- Fitting shots **never enter range sessions or normal shot history** — same
  rule as games and bag shots. The fitting session is the only record.
- FIT tab internal states (plain Compose state holder, `BagView`-style, no
  ViewModels / Navigation-Compose): **COMPARE** (live) → **RESULTS** (table +
  top-down) → **HISTORY** (past comparisons).

## 2. Data model (Room schema v5)

- `FittingSessionEntity` → `fitting_sessions`: `id`, `started_at`,
  `completed_at` (null = IN_PROGRESS).
- `FittingShotEntity` → `fitting_shots`: `id`, `session_id` (FK, indexed),
  `captured_at`, club snapshot columns (`club_id`, `club_name`, `club_type`,
  `club_was_temp`), metric columns mirroring `ShotEntity` (`club_head_speed_mps`,
  `ball_speed_mps`, `launch_dir_deg`, `launch_angle_deg`, `spin_axis_deg`,
  `spin_rpm`, `carry_m`, `total_m`, `side_m`, `apex_m`, `flight_time_sec`), and
  `excluded` (default 0).
- Migration discipline (AGENTS.md): `MIGRATION_4_5` in the `Mlm2proDatabase`
  companion, `@ColumnInfo(defaultValue)` matching the ALTER TABLE defaults
  exactly, committed schema JSON `core/data/schemas/.../5.json`, migration test
  `MigrationFrom4Test`. Never enable destructive fallback.
- **Comparison club set = distinct club snapshots in the session's shots,
  ordered by first appearance.** No separate club-set column; this is stable
  under kill/resume and survives the end-of-session temp-club purge (per-shot
  snapshot keeps name/type — same trick as `ShotEntity.clubWasTemp`).
- `SessionRepository` additions (mirroring bag mapping): `startFittingSession()`
  (idempotent — returns the existing IN_PROGRESS id), `appendFittingShot()`
  (write-through per shot, returns the record), `setFittingShotsExcluded(ids,
  excluded)`, `completeFittingSession(id)`, `observeActiveFittingSession()`,
  `observeFittingShots(sessionId)`, history flow. New
  `FittingSessionDao`/`FittingShotDao`.
- Aggregates come from a pure `FittingStats` (in `:core:data`, next to
  `BagMappingStats`), reusing `BagMappingStats.distribution()` (median, mean,
  population σ, quartiles) and `applyDuffFilter()` (ball speed < 85 % of median,
  dormant < 3 shots).

## 3. COMPARE (live) view

- Canvas: full-size `RangeLiveView` (the component extracted for BAG collecting
  in the M6 follow-ups) — tracers, follow-cam, standard live view. No colours
  or rings while hitting.
- Right panel (210 dp, range layout): LAST SHOT chips on top (reused); new
  **COMPARISON card** at the bottom where session details normally sit: one
  line per club — colour dot, name, kept-shot count, average distance — with a
  **carry/total toggle**. Amber count badge when a club has < 5 kept shots
  (ClubQualityGate pattern).
- Club picker reuses `ClubPickerOverlay`. In FIT mode the add button is
  labelled **ADD COMPARISON**; `AddClubForm` opens with the TEST checkbox
  pre-checked; on submit the new temp club is auto-set active
  (`ActiveClubStore`). Real bag clubs can be mixed in freely by picking them.
- A **VIEW RESULTS** button in the right panel switches to RESULTS; returning
  to COMPARE keeps collecting into the same session.
- Designer owns the comparison-card layout and button placement (M3 design
  system pass before build).

## 4. RESULTS view

- Full-screen state (like bag RESULT). Contains a top-down canvas and a table;
  a **TABLE | TOP-DOWN segmented switch** selects which fills the screen
  (designer may merge/split this layout — the data seams below are fixed).
- **Table**: one row per club; frozen CLUB column (colour dot + name) plus
  horizontally scrollable metric columns:
  `n · CHS · BALL SPEED · SMASH · CARRY (avg ± σ) · TOTAL (avg ± σ) · LAUNCH ·
  DIR · SPIN · SPIN AXIS · OFFLINE (avg / worst)`.
  - SMASH = per-shot ball speed ÷ CHS, averaged. OFFLINE uses `|side_m|`
    (avg and worst over kept shots).
- **Delta row**: with exactly 2 clubs, one B−A line under the pair; with 3+,
  a tappable baseline club and deltas vs baseline per metric. A delta within
  the metric's noise band renders dimmed so noise isn't over-read. Noise bands
  are tunable constants (initial values, app-native units): carry/total ±2 m,
  spin ±150 rpm, ball speed ±0.7 m/s, CHS ±0.5 m/s, smash ±0.010,
  launch/dir ±0.5°, spin axis ±2°, offline ±1 m.
- **Drill-down** on club-row tap: every shot for that club — kept rows normal,
  excluded rows greyed with an include toggle; exclude tick per shot (M5x tick
  pattern). An **EXCLUDE LIKELY MISREADS** button applies the duff filter in
  one tap.
- Exclusion is **one source of truth** (`fitting_shots.excluded`): it
  simultaneously updates table aggregates, deltas, drill-down, top-down dots,
  dispersion ovals, and the comparison card. Counts shown as
  "8 shots · 2 excluded".

## 5. Top-down view & dispersion ovals

- New `FittingTopDown` painter built on `TopDownCanvas` mechanics (same grid +
  `RangeDecorations` boards). Kept shots drawn as dots coloured per club from a
  fixed 6-colour palette (assigned by first appearance; distinct from the
  teal/blue/amber already in use); legend chips above the canvas.
- Excluded shots are not drawn (they remain visible in the drill-down).
- **Dispersion ovals per club**: pure `DispersionOval` math (app/fitting, pure
  Kotlin + JVM test, like `BoxPlotGeom`) — mean + 2×2 covariance over
  (side, total) of kept shots, eigen-decomposition → 1σ and 2σ ellipse outline
  polygons, projected as ground polygons like the green ovals
  (`RangeScene.circleOutline` pattern via `PovProjector`). Requires ≥ 3 kept
  shots; otherwise no ring is drawn.
- y axis = rest position = **total** (the ball stops at total); the carry/total
  toggle applies to the comparison card and table only, not the top-down.

## 6. Lifecycle, resume, history

- **Auto-start**: hitting the first shot in FIT with no IN_PROGRESS session
  starts one (idempotent). No intro screen.
- **END COMPARISON** in RESULTS completes the session
  (`completeFittingSession`); the tab returns to a fresh empty COMPARE.
  Completing is non-destructive — no confirmation dialog.
- **Kill/resume**: on app start with an IN_PROGRESS fitting session, the FIT
  tab opens in COMPARE with flows rebuilt from persisted shots (bag-resume
  pattern, `AppRoot.kt:568–577`); active club comes from `ActiveClubStore`.
  Resume tolerates a purged temp club (snapshots keep names; active club falls
  back to default).
- **HISTORY** state inside the FIT tab: completed comparisons listed (date,
  club names, shot counts); tapping opens a read-only RESULTS view of that
  session. The normal HISTORY tab is untouched for v1.
- Range/GAMES/BAG behaviour unchanged; temp comparison clubs purge at
  range-session end exactly as today.

## 7. Testing

- Pure JVM: `FittingStatsTest` (per-club aggregates, smash, offline, deltas +
  noise-band flagging), `DispersionOvalTest` (known covariance → expected
  axes/rotation/polygon).
- Room: `MigrationFrom4Test`; `FittingRepositoryTest` (in-memory Room,
  `BagMappingRepositoryTest` pattern): write-through, exclusion updates,
  kill/resume, completion.
- Whole build green per AGENTS.md: `$env:JAVA_HOME` set, Gradle flags precede
  task names, typed `testDebugUnitTest` variants (`:core:physics` uses plain
  `test`).

## 8. Out of scope (YAGNI, approved)

Sortable table columns; best-value-per-column highlighting; alternating
A/B/A/B hitting protocol; CSV export (M9 owns export); equal-shot-count
normalization; colouring the live COMPARE top-down; integrating fitting history
into the main HISTORY tab.
