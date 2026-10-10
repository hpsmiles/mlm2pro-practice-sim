# Design: M8 — Custom Combines (catalog combines + strokes-gained handicap scoring)

Date: 2026-10-10
Status: approved design, not implemented
Origin: ROADMAP.md M8; user brief 2026-10-10 (brainstorming session, five
approved sections S1–S5)

A "combine" is a structured, multi-station test — stock distances like 50/70/90 m or
100 m/150 m/driver — played in one interleaved round-robin session. Every shot is
scored with strokes gained against published expected-strokes tables at four handicap
benchmarks (tour, scratch, 10, 18), producing a **handicap estimate per station and
overall**, including resolution below scratch (the user is a +1 player). Results are
persisted (Room v6) and listed in HISTORY > GAMES. All scoring math lives in a new
pure-JVM module `:core:scoring`; the UI follows the games/bag/fitting discipline.

Roadmap note: the M8 line says "user-defined target distances". In this session the
user chose to launch with a **small stock catalog** (shot counts and layouts fixed)
and build the engine generically so custom entry/sets are a cheap follow-up — this
tightens, not cancels, the roadmap intent.

User-confirmed decisions this session:

- Results show **all bands at once** (total SG vs tour/scratch/10/18) under a headline
  per-station + overall handicap estimate; per-shot scores are coarse chips.
- **Stock distances**; the shot count is not user-selectable. Chosen format: **6 shots
  per station, interleaved** — 3 cycles of 2 shots per station — 18 shots (~15–20 min,
  "closer to real golf rather than getting too dialed in at one length").
- **One multi-station combine** per session (per-station results + combined total).
- **Small stock catalog** at launch; the engine is generic so more sets/custom entry
  are cheap follow-ups.
- Sub-scratch: **extrapolate** below scratch via a tour anchor at −5.
- During play: **no SG or HC on screen** — normal shot-metric chips only, with shots-
  hit-per-target counts at the bottom of the right panel (FB1).
- **Driver station has no green** — fairway and rough only (FB1).
- Results additions (FB2): **avg distance to pin** on approach station cards, **avg
  distance** on the driver card.

---

## 1. Scoring model

**Expected strokes.** `E_band(lie, metres)` per band from committed tables (section 2).
Handicap axis anchors: TOUR = −5, SCRATCH = 0, HCP10 = 10, HCP18 = 18 (constants).

**Per shot.** Start lie + start distance-to-pin → rest lie + rest distance-to-pin:

```
SG_h = E_h(startLie, startM) − E_h(endLie, endM) − 1
```

Start lies: FAIRWAY (approach stations), TEE (driver). End lies: **GREEN** (uses the
putt table), FAIRWAY, ROUGH — the only surfaces the app world has (fringe = fairway).

**Handicap match (the "regression").** Solve `SG_h = 0` for continuous h by treating
SG as piecewise linear in h between adjacent band curves (anchors exact; no fabricated
bands). This is closed-form segment arithmetic — no numeric solver. Shots below −5 are
display-clamped ("tour-or-better"); the internal estimate stays continuous (records
compare on it).

**Station and overall.** The station HC = root-solve on the **mean SG over its 6
shots** (not the mean of per-shot matches — per-shot matches are noisy ±~20 hc).
Overall = root-solve on all 18 shots' mean. Per-shot matches render as coarse chips
(`TOUR+` / `SCR` / `≈N`).

**Uncertainty.** `± = SD(per-shot SG at the scratch band, sample n−1) ÷ √n ÷ local
slope` of the mean-SG curve at the root (segment slope; below 0 use the [−5,0] slope,
above 18 use the [10,18] slope, extrapolated to 25). Scratch-projected SD is the
stable spread reference at any level. Sensitivity context: slope ≈0.0254 SG/hc in the
sub-zero and 0–10 zones, ≈0.057 in 10–18 — 1 SG ≈ 25–40 hc, hence coarse chips.

**Display formats.** Estimate: `+N` in [−5,0) (golf plus convention, e.g. `+1.3`),
`N` in [0,25]; below −5 → `tour-or-better`; above 25 → `25+`. With uncertainty:
`≈ 6.2 ± 4` / `≈ +1.3 ± 6`. Per-shot chip: `< −5` → `TOUR+`; [−5,0] → `SCR`; else
`≈` + integer.

**Golden fixture (user's example, pinned in tests, tolerance 0.005).** 100 m station,
all on green, rests 1, 6, 7, 8, 10, 12 m → per-shot: 1 m `TOUR+` (SG tour +0.761 /
scratch +0.951), 6/7/8 m SCR (SG_sc +0.101 / +0.048 / +0.005), 10 m → HC 3.1
(SG −0.171/−0.070/+0.158/+0.559), 12 m → HC 7.2 (SG_sc −0.145). Station mean SG:
tour +0.021 / scratch +0.148 / h10 +0.411 / h18 +0.865 → h* ≈ −5.84 →
"tour-or-better". SD(scratch) 0.403 → SE 0.164 → ÷0.0254 ([−5,0] slope) → ±6.5.

**Lie sensitivity (why the world is part of the score).** Same 100 m shot resting 12 m
from the pin: GREEN → HC 7, FAIRWAY → HC 14, ROUGH → HC 19. The surface the ball
visibly rests on (decided by the same provider the physics uses) *is* the scoring lie —
no hidden translation. Station green sizes are therefore deliberate scoring design
knobs (catalog fields, section 4).

Scoring is distance-based (no direction component); direction still shows in the shot
chips. Distances clamp to table grid ends.

## 2. SG data & provenance

- Source: `sg_reference_full.csv`, from the user's own app (tapinstats.com) — 5,280
  rows; skill_levels tour / scratch / hcp10 / hcp18 — only 4 real bands; lies putt /
  fairway / rough / sand / tee / recovery; metres; expected_strokes. User approved
  vendoring the CSV; it lives at `core/scoring/tools/sg_reference_full.csv`.
- **Two series per (band, lie)**: the vendored CSV labels rows by `distance_unit` —
  series 1 is the true metre conversion (unit `m`); series 2 is the same grid in
  imperial units (unit `ft` for putt, `yd` for the rest) with identical values.
  Only unit `m` rows are canonical; the imperial duplicates MUST be ignored (they
  would read putt 5 ft as "5 m" → 1.23 instead of 1.857 at 5 m).
  Detect series 1 as everything before the first distance decrease. All expected-strokes
  figures in this design were verified against series 1.
- Committed tables: putt 60 points (0.30–18.29 m), fairway/rough 120 points each
  (4.6–548.6 m), tee 120 points (91–636 m). Distance grid stored once per lie + a
  value array per band. Sand/recovery are dropped (app has no such surfaces) but the
  committed generation script can regenerate them in one line.
- Source anomalies kept as-is: e.g. non-monotonic dips of 0.01 strokes in the source
  are not smoothed.
- Data-vetting tests (section 8) pin: `E_tour(fairway, 91.4 m) = 2.80` (exact row),
  `E_scratch(putt, 5.0 m) = 1.857` (relabel-rejection), and band ordering
  tour ≤ scratch ≤ 10 ≤ 18 at every grid point.
- **Provenance:** resolved — the table is from the user's own app (tapinstats.com) and
  the user approved committing it. The source CSV is vendored (path above) alongside a
  committed Node generation script `core/scoring/tools/generate-sg-tables.cjs` that
  regenerates the Kotlin tables from it in one command (generated file carries a
  documented header). No published-table cross-check is required for v1; the file
  passed self-consistency checks (each band's implied average ≈ SG 0 at its level).

## 3. Module `:core:scoring`

Pure Kotlin/JVM module mirroring `:core:physics` (no Android, no IO, deterministic).
Added via `include(":core:scoring")`; tests via `.\gradlew.bat :core:scoring:test`.

API (pure functions):

- `enum SkillBand { TOUR(−5.0), SCRATCH(0), HCP10(10), HCP18(18) }`
- `enum Lie { TEE, FAIRWAY, GREEN, ROUGH }` (GREEN → putt table)
- `expectedStrokes(band, lie, metres)` — linear interpolation, clamped at grid ends
- `strokesGained(band, startLie, startM, endLie, endM) = E(start) − E(end) − 1`
- `handicapMatch(shots) → HandicapEstimate(est, low, high, perBandSg, clampedLow,
  clampedHigh)` — accepts any shot list (a station's 6, or all 18 for overall)

The app builds `ShotInput`s (start/end lie + distance to pin) from its own geometry
and calls the module; the module knows nothing about scenes, greens or screens.

## 4. Catalog v1 & combine flow

**Catalog (data-driven; more entries later are cheap):**

- **WEDGES** — stations 50 m, 70 m, 90 m.
- **APPROACH** — stations 100 m, 150 m, driver.
- Station order fixed short→long. Layouts fixed: each station carries **3 fixed pin
  placements** (one per visit) → the target relocates between visits, and every play of
  a combine is the same course (comparable across sessions). No randomness. Approach
  stations use a generous scoring green: radius is a per-station catalog constant
  (v1 default ~15 m; tune after first live sessions — a stingy green inflates every
  HC, per the lie-sensitivity table).

**Session format.** 6 shots per station, interleaved: cycle 1 = 2 shots at each
station in order, then cycles 2 and 3 → 18 shots. Between visits: 2-second banner
("NEXT: 150 m APPROACH · visit 2") + camera glide, pin relocates, **no taps**. After
cycle 3 → RESULTS.

**Driver station (uniform scoring rule, hole model).** Par-4 model: tee-to-pin fixed
at **340 m**; start expectation = data TEE table at 340 m (tour baseline E ≈ 3.94).
After the shot rests, scoring = rest lie + remaining distance to pin. The scene is tee
box + fairway corridor + rough — **no green, no pin marker, no "to pin" readout**
(FB1); the 340 m baseline drives scoring only, invisibly. Stray drives cost distance
and lie only — no obstacle model (no trees/water/bunkers) in v1.

**States.** SETUP (catalog cards with names/stations/blurb + the four reference bands +
~15–20 min estimate + a "results go to HISTORY" hint + START/CANCEL) → PLAYING →
RESULTS (full-screen — an 18-shot summary does not fit the games overlay card).

**Live capture.** Same seam as games/bag: shots route to the combine only while the
combine is playing (GameMode dispatch hoisted to AppRoot, games pattern); leaving the
tab stops capture. Misread-guard only: every valid decoded shot counts — no
delete-last affordance (approved). Speed chips reuse `GameSpeedControl`. Leaving or
killing the app is safe (write-through, section 6); no destructive-leave dialog.
Demo mode uses the existing injectable simulator seam → DEMO-source sessions,
device-free.

## 5. Screens

Approved as in-browser mockups (session-local references, git-ignored:
`combine-v2-play-and-results.html`, `combine-v3-results-avg.html`); this spec governs.

- **GAMES picker**: a third game card (COMBINES). With an unfinished session it shows
  `RESUME · 7/18` (bag affordance pattern).
- **PLAY**: full-screen like games play — canvas + 210 dp right panel; top-left station
  indicator (`100 M · VISIT 2/3`), speed chips, quick-settings gear (hidden on
  results). Right panel: LAST SHOT card = `LastShotChips` reused **verbatim**
  (carry · total · ball MPH · club MPH · smash · spin · launch · dir · axis; muted
  "Fire a shot" initially), then a **TARGETS card** (replaces SESSION): per-station
  shots-hit counts with the current station highlighted + total `8/18`.
  **No SG or HC anywhere during play.** Driver play: same panel; scene without green.
- **RESULTS** (full screen): headline **overall HC** (`≈ 6.2 ± 4` style); band strip —
  total session SG vs TOUR / SCRATCH / 10 / 18; three station cards, each with HC ±
  uncertainty, a four-bar diverging SG display (zero-centred; teal positive / red
  negative), and either **avg distance to pin** (approach) or **avg distance** hit
  (driver, mean of 340 m − remaining); per-station per-shot ledger — approach rows
  `rest · lie · match chip`, driver rows `distance hit · lie · match chip`; a coarse-
  chip caveat note (single-shot matches are ±~20 hc; station/overall are the signal);
  NEW RECORD banner with previous best when present (reuse `ConfettiBurst`); PLAY
  AGAIN / BACK.

## 6. Persistence & history (Room v6)

Two new tables, mirroring bag mapping:

- `combine_sessions` — `id` PK, `combine_id`, `combine_name` (snapshot), `started_at`,
  `completed_at` (null = IN_PROGRESS), `source` (LIVE=0 / DEMO=1), `layout_version`
  (catalog layout constant — old results keep meaning if the catalog changes).
- `combine_shots` — `id` PK, `session_id` (FK, indexed), `shot_index` (0–17),
  `station_index`, `rest_lie` (GREEN/FAIRWAY/ROUGH), `rest_distance_m`.

**Raw facts only**: SG values, HC matches, band breakdowns, averages, and records are
recomputed deterministically on read from the shots — nothing derived is ever stored
stale. Repository additions mirror bag naming: `startCombineSession()` (idempotent
in-progress), `appendCombineShot()` (write-through per shot), `completeCombineSession()`,
`observeActiveCombineSession()`, sessions/shots flows for history. New
`CombineSessionDao` / `CombineShotDao`.

**Resume**: an unfinished session (row without `completed_at` + its shots) makes the
picker card show `RESUME · 7/18`; the continue point (station/cycle/shot) derives from
the persisted shot count — same mechanism as bag kill/resume. One open combine at a
time; starting a different one asks once before discarding the unfinished one.

**HISTORY > GAMES** gains a COMBINE group next to Target Practice / Break the Pane:
completed sessions with overall HC ±, RECENT/BEST sorting (best = lowest overall HC);
tapping opens the read-only RESULTS view (fitting pattern — free, since everything
recomputes from shots). **NEW RECORD** = lowest overall HC among completed LIVE
sessions of the same combine; demo sessions appear in history but never set records.

**Migration discipline (AGENTS.md):** `MIGRATION_5_6` in `Mlm2proDatabase`,
`@ColumnInfo(defaultValue)` matching ALTER defaults, committed schema JSON
`core/data/schemas/.../6.json`, migration test `MigrationFrom5Test`. No destructive
fallback, ever.

## 7. Architecture & integration

- `app/.../combine/` — plain Compose state holder + painters (no ViewModels, no
  Navigation-Compose), games/bag/fitting discipline. Pure, JVM-tested pieces:
  `CombineCatalog` (layouts), `CombinePlan` (shot index ↔ station/cycle mapping,
  resume derivation), history sorting/formats, record detection.
- `GameMode.COMBINE` added to the games picker dispatch enum (distinct from
  core:data `GameModes`, which covers game_results only — combines never write
  `game_results`, and combine shots never enter range sessions/shot history).
- Reuse: `GameSpeedControl`, `LastShotChips`, `GameQuickSettings`, `ConfettiBurst`,
  follow-cam/camera-glide patterns, scene decorations/painters.
- The scoring lie comes from the same surface the ball rests on (shared provider with
  physics) — one source of truth, no parallel classifier.

## 8. Testing

- **:core:scoring (pure JVM)**: grid integrity (ascending, shared grid lengths, no
  NaN); anchor pins (`E_tour(fairway, 91.4)=2.80`); relabel-rejection
  (`E_scratch(putt, 5.0)=1.857`); band-ordering invariant at every grid point; golden
  fixture (section 1) at tolerance 0.005; root/clamp edge cases (below tour, above 25,
  single shot, empty list, grid-end clamping).
- **App pure logic (JVM)**: `CombinePlan` (interleave mapping, resume index from shot
  count, ledger/aggregate assembly inputs), chip classification, record detection.
- **Room**: `MigrationFrom5Test`; `CombineRepositoryTest` (in-memory Room,
  `BagMappingRepositoryTest` pattern): write-through, resume from partial session,
  completion, record query (LIVE-only).
- **Manual on-device**: live shots route only while playing; speed chips; banner/glide
  pacing; marker relocation; kill/resume at shot 8; demo mode device-free; results
  hand-checked against the golden fixture; HISTORY group rendering.
- Whole build green per AGENTS.md (JAVA_HOME set, Gradle flags, typed test task
  variants).

## 9. Out of scope (YAGNI, approved)

Custom distance builder / user-defined combines (engine is generic; follow-up);
per-user selectable shot counts; sand/recovery lies (regenerable via script);
bunkers/water/trees obstacles; synthetic in-between handicap bands (no fabrication);
CSV export; points-style scoring beyond SG-HC; random pin layouts; putting out a hole
(a green rest is scored once via the putt table — no second shot); cross-checking the
SG source against published tables (self-consistency passed; user owns provenance).
