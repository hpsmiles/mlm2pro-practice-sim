# Handover — M8 post-ship refinements

**Date:** 2026-10-11 (evening) · **Repo:** `C:\aaa\code\golf sim`
**Branch:** `m8-custom-combines` (NOT merged to master) · **HEAD:** `a239383` · **Working tree:** clean
**Why this doc:** the refinement increment is complete through T4; only the T5 on-device gate remains (blocked on the tablet being connected).

---

## 1. Where things stand

**M8 (custom combines) is feature-complete, verified and on the tablet.** Shipped in this branch:

- Full combine feature: catalog (WEDGES 50/70/90 m, APPROACH 100/150 m + driver), interleaved 18-shot flow, play screen, full-screen results, Room v6 persistence, HISTORY > GAMES COMBINES group + read-only drill.
- First-run fixes (from the user's first demo combine):
  - **Scoring clamp** — the all-negative root-solve branch can no longer return a better-than-18 estimate (`+Infinity` + `clampedHigh` → displays `25+`).
  - **Target/pin timing** — canvas pin/green + HUD + TARGETS highlight hold the shot's station until its flight animation ends.
  - **Demo tagging** — a session is re-stamped DEMO when a demo shot is routed to it (synchronous `sawDemoShot` gate), so demo runs never set records and show the DEMO marker.
- **Driver station re-rated** against per-band typical drives (Arccos 2026 young-scratch cohort).
- **Fairway narrowed to a constant 28 m** corridor.

**Test state:** 340 green — `:core:scoring:test` 25, `:app:testDebugUnitTest` 308, `:core:data` `CombineRepositoryTest` 7.

**Increment status:** the 5-task refinement increment is **shipped through T4** (`ceead19`, `8047f81`, `74d2152`, `a239383`). **Only T5's on-device gate remains** — it needs the Lenovo tablet connected (`:app:installDebug` + the checklist in §5/§8). Design + plan are committed (`f69c346`).

---

## 2. Remaining work (T5 only)

```powershell
cd 'C:\aaa\code\golf sim'
git status --short          # expect clean
git log --oneline -6        # expect HEAD a239383
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :app:installDebug     # needs the tablet connected (adb serial HA2C96BN)
```

Then walk the user through the on-device checklist (§5 T5 row + §8) and commit any corrections. `gradlew build` already passed for `a239383`.

**If the tablet is not connected**, stop and ask the user to connect it — nothing else is outstanding.


---

## 3. Read these first

| Purpose | Path |
|---|---|
| The increment's 5 tasks (authoritative) | `docs/superpowers/plans/2026-10-11-m8-post-ship-refinements.md` |
| The increment's design + decisions | `docs/superpowers/specs/2026-10-11-m8-post-ship-refinements-design.md` |
| The shipped M8 feature (spec) | `docs/superpowers/specs/2026-10-10-m8-custom-combines-design.md` |
| The shipped M8 feature (plan) | `docs/superpowers/plans/2026-10-10-m8-custom-combines.md` |
| Repo charter / conventions | `AGENTS.md` |
| Roadmap | `ROADMAP.md` |

---

## 4. The execution rig (how tasks are run)

Tasks are executed by **child agent sessions** driven from the repo root:

```powershell
# implementer
opencode run --auto --title '<title>' --model 'neuralwatt/deepseek-v4.1-flash-flex' '<short message pointing at a prompt file>'

# reviewer (spec-compliance, then code-quality)
opencode run --auto --title '<title>' --model 'neuralwatt/deepseek-v4.1-flash-flex#high' '<message>'
```

Rules learned the hard way:

- **Long instructions go in a prompt file** under `.superpowers/m8-exec/` (git-ignored); the `opencode run` message is short and points at it.
- **Pass messages as SINGLE-quoted PowerShell strings.** Double quotes inside a double-quoted string terminate it and the command fails ("Unrecognized flag: …").
- **Never run two Gradle builds concurrently** in this project directory — reviews force test runs, so sequence children (one at a time).
- `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before every Gradle command; flags (`--console=plain`) before task names.
- Per task: **implementer → spec-compliance review → code-quality review → fix loop in the same session if needed**. Reports land in `.superpowers/m8-exec/*-report.md`.
- Children are non-interactive: if one is ambiguous it reports BLOCKED/NEEDS_CONTEXT rather than asking.

---

## 5. Task queue

| # | Task | Status | Commit |
|---|---|---|---|
| T1 | Benchmark lock (`SkillBand.nearestTo`, `CombineResults.lockedBand` + `shotSgForBand`) | **DONE** | `ceead19` |
| T2 | Room v7: `combine_shots.restXM/restYM` (+ `MIGRATION_6_7`, `7.json`, migration test, repository + AppRoot append) | **DONE** | `8047f81` |
| T3 | Results redesign (`CombineResultsView` rewrite) | **DONE** | `74d2152` |
| T4 | Top-down dispersion view (`CombineTopDownView.kt` + pure `CombineDispersion` + tests + toggle) | **DONE** | `a239383` |
| T5 | Final gate (`gradlew build` ✅, `:app:installDebug`, on-device checklist) | **build green; device gate pending** | — |

---

## 6. Locked design decisions (do NOT re-litigate)

- **Benchmark lock:** a completed combine locks to the benchmark band **nearest its overall handicap estimate**; ties resolve to the better (lower) anchor; non-finite/out-of-range → `HCP18` (below −5 → `TOUR`). Every per-shot SG in the results ledger is computed against that locked band (approaches: `strokesGained(band, startLie, startM, endLie, endM)`; driver: `drivingStrokesGained(band, restLie, restDistanceM)`). Station estimates and the VS BANDS strip keep the 4-band solve.
- **Ledger:** collapsible and **collapsed by default**; the header states the benchmark (e.g. "Scored vs 18 HC").
- **Top-down view:** reached by a SUMMARY / TOP-DOWN toggle on the results screen; horizontal plan view, **tee on the left**, 0–330 m to the right; constant 28 m corridor; gridlines every 50 m; 100 m and 150 m greens as circles with pins; three colour-coded groups (100 m, 150 m, driver) drawn as **finish dots + centroid + one dashed 1σ ellipse**; side panel with per-group avg / long–short / left–right; empty state "no dispersion data for this session" when coordinates are absent. **As shipped (T4):** all groups are plotted in **absolute hole coordinates** (`restXM`/`restYM`) — equivalent to the spec's "relative to their own pin" because each approach pin sits at its station's `startDistanceM`, so absolute == pin + offset. The pure stats live in `CombineDispersion` (population σ, axis-aligned 1σ ring); the view is `CombineTopDownView`.
- **Driver model:** per-band typical drives TOUR 272.5 / SCRATCH 239.6 / HCP10 208.9 / HCP18 193.9 m (Arccos 2026, young-scratch cohort for scratch; PGA Tour average for tour). `SG_h = E_h(FAIRWAY, 340 − typicalDriveM_h) − E_h(restLie, restDistanceM)` — position-to-position, **no −1**.
- **Fairway:** constant 28 m (`RangeScene.HALF_WIDTH_M = 14.0`), user-specified, no taper.
- **Scoring clamp:** degenerate all-negative estimates (raw [10,18] diff ≤ 0) → `+Infinity` + `clampedHigh` → renders `25+`.

---

## 7. Key files

**Code (app):** `app/src/main/kotlin/com/hpsmiles/golfsim/combine/` — `CombineCatalog.kt`, `CombinePlan.kt`, `CombineLie.kt`, `CombineGame.kt`, `CombineFormats.kt`, `CombineRecord.kt`, `CombineResults.kt`, `CombineCanvas.kt`, `CombineMetricsPanel.kt`, `CombinePlay.kt`, `CombineResultsView.kt`, `CombineSetup.kt`; plus `range/AppRoot.kt` (routing/wiring), `games/GamesScreen.kt`, `history/HistoryScreen.kt`, `range/RangeScene.kt` (corridor geometry).

**Code (core):** `core/scoring/src/main/kotlin/com/hpsmiles/golfsim/core/scoring/` — `SkillBand.kt`, `Lie.kt`, `SgTables.kt` (generated), `ExpectedStrokes.kt`, `HandicapMatch.kt`; generator + CSV in `core/scoring/tools/`. Data layer: `core/data/src/main/kotlin/.../Mlm2proDatabase.kt`, `SessionRepository.kt`, `CombineEntities.kt`, `CombineDaos.kt`, schemas in `core/data/schemas/`.

**Scratch / git-ignored:** `.superpowers/m8-exec/` (task prompts + child reports), `.superpowers/brainstorm/m8-combines-20261010/content/` (design mockups: `m8-results-redesign-v1.html`, `m8-results-v2-collapsible-ledger.html`), tracker at `C:\Users\smiles\AppData\Local\Temp\opencode\m8-brainstorm-checklist.md`.

---

## 8. Verification commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :core:scoring:test                                  # expect 25+ tests, 0 failures
.\gradlew.bat --console=plain :app:testDebugUnitTest --rerun-tasks                # expect 308+ tests, 0 failures
.\gradlew.bat --console=plain :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.CombineRepositoryTest"
.\gradlew.bat build                                                              # T5 gate (all modules + APKs)
.\gradlew.bat :app:installDebug                                                  # needs the tablet connected
```

**Device:** Lenovo tablet `TB373FU` (adb serial `HA2C96BN`). adb lives at `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`.

**Visual companion server** (design mockups; auto-exits after 30 min idle):

```powershell
$env:BRAINSTORM_DIR='C:\aaa\code\golf sim\.superpowers\brainstorm\m8-combines-20261010'
$env:BRAINSTORM_HOST='127.0.0.1'; $env:BRAINSTORM_URL_HOST='localhost'; $env:BRAINSTORM_PORT='52413'
node 'C:\Users\smiles\.config\opencode\skills\superpowers\brainstorming\scripts\server.cjs'   # then http://localhost:52413
```

---

## 9. Deferred minors (polish batch, non-blocking)

- `DRIVER_HOLE_M` duplicated at 340.0 in `CombineCatalog.kt` and `core/scoring/ExpectedStrokes.kt` (drift risk).
- Stale `RangeSceneTest` test name (`fairway bounds taper from tee to end` — pins are correct).
- Dead `GameHistoryOrder` enum + an unused `alpha` import in `HistoryScreen.kt`.
- `combineRows` row-inclusion/ordering logic is untested pure logic (candidate for extraction + JVM tests).
- The 23-line completion algorithm inline in `AppRoot.kt` (candidate for extraction into a tested helper).
- `displayIndex` expression embedded in `CombinePlay.kt` (extract only if it grows).
- `Lie` accessors public-only-for-internal-use; NaN doc/guard in `ExpectedStrokes`.

---

## 10. User context / preferences

- Single Lenovo tablet, offline, landscape; practice tool for a ~+1 handicap golfer.
- Reviews design via **browser mockups** (visual companion) — prefers options with a clear recommendation; responds well to being shown the numbers.
- **Cost-conscious:** use flex-tier models (`neuralwatt/deepseek-v4.1-flash-flex`, reviewers `#high`) for children.
- Wants the results screen readable at a glance: big overall number, per-target comparison, detail on demand (hence the collapsible ledger).
