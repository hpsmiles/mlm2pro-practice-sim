# Bag Mapping & True Gapping (M6) — Design

Date: 2026-10-06
Status: Approved (design dialogue 2026-10-06; pending spec review)
Predecessor: M5.5 Games (shipped 2026-09-27). Follows the roadmap order (M5.5 → M6).

## 1. Summary

A guided full-bag mapping workflow on a new top-level **BAG** tab. The player hits a
guided target of 5 valid shots per club (with a quality-gated option to hit more);
misreads and duffed strikes are filtered automatically; the result is a **box-plot
carry matrix** (M3-approved direction, reconstructed) with adjacent-club gap flags and
per-club drill-down. Progress persists across tab switches and app restarts (resume
where you left off). Completed results live on the BAG tab with a RETEST action; every
past result stays in an in-tab history. Mapping shots are fully isolated from range
sessions and shot history — only mapping data is written, mirroring the games
persistence pattern (Room v4).

Exit criteria (ROADMAP M6): complete full-bag session → matrix + gap chart.

## 2. Decisions locked during design dialogue

| Topic | Decision |
|---|---|
| Shot isolation | Mapping shots never enter range sessions/shot history; dedicated Room tables (games pattern). |
| Sample policy | Guided target **5 valid shots/club**; after the 5th, a quality gate may ask the player to hit more (§4). |
| Shot counting | Auto-filter misreads + duffs; only clean reads count toward the target (§5). |
| Club progression | Guided bag order, auto-advance on target reached; skip + early end allowed; results cover whatever has data. |
| Interruption | **Persist and resume** — progress survives tab switches and app kills; no leave-confirm needed. |
| Retest | Old result stays visible (with "test in progress" indicator) until a new test completes; abandoned retests never touch the existing result. |
| Results layout | Box-plot carry matrix on one shared carry axis, clubs in bag order (M3 `carry-matrix-v2.html` direction; GSCaddie-proven encoding). |
| Club drill-down | Tap a club row → per-shot list for that club from the session (kept vs filtered). All shots stored, so this is v1, not deferred. |
| Units | Metres like the rest of the app (M3 `UnitsPreference` was never implemented; wiring it is out of scope here). |
| Excluded clubs | Putters and TEST (`isTemp`) clubs never enter the guided flow. |
| History | All completed mapping results kept, most recent first, tap → read-only matrix. No retention limit. |

## 3. Navigation & screen states

`RangeTab.BAG` joins `RANGE, GAMES, SETTINGS, HISTORY` in `AppRoot` (`RangeTab` enum +
`NavRailButton` + `when` branch; existing `requestTab()` guard untouched — leaving
mid-collection is safe because progress persists).

`BagMappingScreen` uses an in-screen `when`-enum (games pattern):

- **INTRO** — shown when no result and no in-progress session. Copy reminds the player
  to check the bag is up to date (link/shortcut to Settings BAG section), shows the bag
  summary (club count from the `clubs` flow), and a START TEST button. Bag-empty state
  points to Settings to add clubs.
- **COLLECTING** — guided collection (§4).
- **RESULT** — box-plot carry matrix + stats + gap flags + drill-down + RETEST +
  history entry point (§6).
- **HISTORY** — list of past completed sessions (date, clubs covered, shot count);
  tap → read-only RESULT.

State selection derives from Room (in-progress session exists? latest completed
result?) — no SharedPreferences, no in-memory-only state:

- No result, no in-progress session → INTRO.
- In-progress session, **no** prior result → COLLECTING (first-ever test).
- In-progress session **and** prior result exists → RESULT (the prior result), with a
  "TEST IN PROGRESS — resume" banner/button leading to COLLECTING. This honors the
  retest decision: old results stay browsable while the new test runs.
- Result, no in-progress session → RESULT.
- HISTORY reachable from RESULT (and INTRO once any history exists).

## 4. Collection flow

- The session snapshots the club list at start (`bag_mapping_sessions` created
  immediately with status `IN_PROGRESS`). Clubs added to the bag mid-test do not join
  the session; club renames/deletes afterwards don't affect past sessions (snapshot
  columns on shots).
- Clubs iterate in bag order (`ClubType` declaration order, then sort order — same
  ordering the `clubs` flow already uses). Putters and TEST clubs excluded.
- **COLLECTING UI**: current-club banner, 5 progress dots, last-shot carry readout,
  filtered shots marked inline ("no read (N)" pill reuses the existing misread
  pattern), "next up: X" preview, END TEST (early finish allowed) and skip-club
  control.
- Shot routing: while collecting, `AppRoot` routes decoded `BallData` to the mapping
  session state holder (same seam games use) **and** write-through-persists each shot
  to Room — this is what makes resume-after-kill work.
- On the 5th valid shot for a club, `ClubQualityGate` evaluates (§5): pass →
  auto-advance to next club; prompt → player chooses HIT MORE (up to 15 kept shots for
  the club, gate re-evaluates) or ACCEPT RESULT (advance anyway).
- Last club done → session completes (`status=COMPLETED`, `completedAtMs` set) →
  RESULT. Completing a session makes it the active result (latest completed); a
  previous active result remains in history untouched.
- At most one `IN_PROGRESS` session exists (repository-enforced). If one exists, the
  BAG tab surfaces it (COLLECTING, or RESULT + "resume" banner when a prior result
  exists — §3); START TEST only appears when none does. Starting fresh is never
  destructive: abandoning a test = never completing it; old results are untouched.

## 5. Filtering & quality gate (pure, JVM-tested)

**Protocol misreads are never stored.** A shot flow ending in the EVENTS `05 00` frame
or the all-zero MEASUREMENT sentinel is a non-shot (M4b/M4d rule): surfaced only as
the "no read" pill, never a row. Malformed decodes likewise never reach the session.

**Duff filter** (`BagMappingStats`), recomputed over the club's whole shot set on
every write so kept-set, stats, and plots stay consistent as data grows:

- Dormant while the club has < 3 shots (a median needs ≥3 shots before it stands on
  evidence other than the shot being judged). Before that, everything counts.
- From 3+ shots, **every** shot (including the earliest) is judged: ball speed
  < 85% of the club's median ball speed (median over **all stored shots** for that
  club, kept and filtered alike — matching the worked example) → `filtered=1`,
  `filterReason=DUFF_LOW_BALL_SPEED`.
  Filtered shots don't count toward the target but stay stored and visible (hollow
  dots, drill-down rows).
- Ball speed (not carry) is the discriminator: a duffed strike shows low ball speed
  against normal club head speed (both on BLE), while carry is a physics output that
  can compound an anomaly.
- Low-side only in v1 — an unusually long strike is real data, not an outlier.
- Poisoned-baseline case (first two shots both duffs → median sits low, 85% check
  can't separate): caught by the window condition of the quality gate → "hit more"
  prompt → more clean shots pull the median up → recompute filters the duffs. The two
  rules cover each other's blind spots.

**Quality gate** (`ClubQualityGate`, constants in one object): after the 5th valid
shot, prompt "hit more" iff `filteredCount >= 3` OR kept-shot carry window
(`max − min` over kept shots) > 20% of the kept shots' median carry. Hard cap 15 kept
shots/club; the player can always accept the result.

## 6. Results: box-plot carry matrix

- One shared horizontal carry axis (auto-ranged to data), one row per club in bag
  order (driver top). Per club, over **kept** shots: whiskers = shortest–longest, box
  = Q1–Q3, volt tick = median, dot = mean, hollow amber dots = filtered shots.
- Stats column per club: median · σ · kept count (+ filtered count).
- **Gap flags** between adjacent rows (`GapAnalysis`): median gap flagged
  **tight < 8 m** · **healthy 8–20 m** · **wide > 20 m** · **inverted** (shorter
  club's median ≥ longer's). Single-club/partial bags degenerate gracefully.
- Tap a club row → drill-down list: every stored shot for that club (timestamp, carry,
  ball speed, smash factor, filtered + reason). Read-only in v1 (no manual exclude).
- RETEST starts a new session (old result remains visible behind an "in progress"
  banner until the new one completes). HISTORY lists past sessions.
- Rendered with Compose Canvas; value→pixel geometry in pure `BoxPlotGeom` (JVM-tested).

## 7. Data model (Room v3 → v4)

Two new tables; existing tables untouched. New `MIGRATION_3_4` in `Mlm2proDatabase`
companion + committed `4.json` + `MigrationFrom3Test` (hand-built raw SQLite v3 file
per `MigrationFrom1Test`/`MigrationFrom2Test` pattern; assert prior sessions, shots,
clubs, game results survive).

`bag_mapping_sessions`
- `id` INTEGER PK AUTOINCREMENT
- `startedAtMs` INTEGER NOT NULL
- `completedAtMs` INTEGER (null while open)
- `status` TEXT NOT NULL (`IN_PROGRESS` | `COMPLETED`)

`bag_mapping_shots`
- `id` INTEGER PK AUTOINCREMENT
- `sessionId` INTEGER NOT NULL (indexed; plain column, no hard FK — repo manages
  lifecycle, matching existing entity conventions)
- `clubName` TEXT NOT NULL, `clubType` TEXT NOT NULL (snapshots)
- `timestampMs` INTEGER NOT NULL
- `clubHeadSpeedMps`, `ballSpeedMps` REAL NOT NULL
- `launchAngleDeg`, `launchDirDeg`, `spinAxisDeg` REAL NOT NULL
- `totalSpinRpm` INTEGER NOT NULL
- `carryM`, `totalM` REAL NOT NULL (physics caches, same as `shots`)
- `filtered` INTEGER NOT NULL (0/1, recomputed cache)
- `filterReason` TEXT (null when kept; `DUFF_LOW_BALL_SPEED`)

DAOs: `bagMappingSessionDao()`, `bagMappingShotDao()`. Repository API
(`SessionRepository`, following existing style): `startBagMappingSession()`,
`activeBagMappingSession()` (in-progress or null), `appendBagMappingShot(...)`,
`completeBagMappingSession(id)`, `latestCompletedBagMappingSession()`,
`bagMappingHistory()`, `bagMappingShots(sessionId)`. Filtering recompute happens in
the repository write path via the pure stats module.

## 8. Pure logic modules (JVM-tested, no Android deps)

- `BagMappingStats` — per-club kept-set, median/mean/σ/Q1/Q3/min/max, duff filter
  (§5). Deterministic: same shots in → same verdicts out.
- `ClubQualityGate` — the §5 gate predicate + constants.
- `GapAnalysis` — ordered clubs → adjacent gaps + flags (§6).
- `BoxPlotGeom` — value→pixel mapping for the matrix painter.

These live beside the game logic classes (`TargetPracticeScoring`, `PaneGeom`, …) and
follow the same discipline: plain Kotlin, golden-case unit tests, no framework types.

## 9. Error handling & edge cases

- **Bag empty** at INTRO → prompt to add clubs in Settings; START TEST disabled.
- **Bag = putter/TEST only** after exclusions → same as empty.
- **Single club** mapped → matrix renders one row, no gap flags.
- **Partial bag** (skip/early end) → clubs without data show "no data" rows; gaps only
  computed between adjacent *mapped* clubs shown in bag order.
- **App kill mid-collection** → reopen BAG tab → resume from Room (shots already
  write-through persisted).
- **Club deleted/renamed after a session** → sessions unaffected (snapshot columns).
- **Recompute determinism** → filtering is a pure function of the club's shot set;
  a re-opened session recomputes identical verdicts.
- **Corrupt DB** → existing `recoverFromCorruptFile()` path untouched; mapping tables
  rebuilt empty like the rest.

## 10. Testing

- **Pure JVM**: `BagMappingStatsTest` (golden cases: duff sequences, dormant-below-3,
  recompute-retroactively-filters-early-shots, poisoned baseline, all-kept,
  low-side-only), `ClubQualityGateTest` (3+ filtered trigger, window trigger, 15 cap,
  accept), `GapAnalysisTest` (tight/healthy/wide/inverted, single club, partial bag),
  `BoxPlotGeomTest` (range mapping, zero-width edges).
- **Repository**: start/resume/complete lifecycle, at-most-one-in-progress, active
  result = latest completed, history ordering, range-session isolation (mapping shots
  never appear in `shots` and vice versa), recompute-on-write consistency.
- **Migration**: `MigrationFrom3Test` + committed `4.json`.
- **UI state holder**: collection flow (auto-advance, gate prompt, resume) — plain
  state holder tests like the games use (injectable clock where timing matters).

## 11. Out of scope (future)

Manual shot exclusion in mapping results; `UnitsPreference` wiring (M3 decision,
unimplemented); rolling cross-session bag profiles (Trackman-style); CSV export;
dispersion ovals / 2D scatter (M7 territory); A/B comparison (M7); on-course yardage
card integration.

## 12. Sources

- `ROADMAP.md` M6 entry.
- M3 design-system spec (`docs/superpowers/specs/2026-09-20-m3-design-system-design.md`)
  — approved `carry-matrix-v2.html` (box plots) direction; mockup itself was a
  gitignored session artifact, reconstructed 2026-10-06 in the brainstorm companion.
- M4c/M4d handovers — misread sentinel rules (misreads are non-shots; duff heuristic
  explicitly deferred to gapping work — this spec is that work).
- Industry research (2026-10-06, librarian): Trackman Map My Bag (6–30 shots/club,
  rolling avg, avg+SD), FlightScope (~10 shots, manual outlier delete, green/red
  bands), Garmin Bag Mapping (5 shots/club, avg + min–max band), Foresight MyBag
  (3–7 shots, gapping + overlap ID), SkyTrak (target gap + tolerance), Shot Scope
  P-Avg / Arccos Smart Distance (outlier-removed well-struck distance), GSCaddie
  (median + IQR box + percentile whiskers on shared axis, ≥8 shots, gap flags amber
  wide / red inverted). Full digest in the 2026-10-06 brainstorm session.
