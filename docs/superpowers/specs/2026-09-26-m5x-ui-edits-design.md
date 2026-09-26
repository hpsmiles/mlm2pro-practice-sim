# M5x — Six UI Edits Design (club trigger & types · history club view · range signs)

Status: approved design, 2026-09-26 · Follow-on to M5 (sessions persistence, merged). Mockups: `.superpowers/brainstorm/m5x-ui-edits-20260926-200308/` · Exit criteria: the six user-requested edits ship without regressing M5 behavior.

## 1. Mission

Six refinements to the merged M5 baseline, ordered by the user: club trigger + club types + temp "TEST" clubs (edits 1–3), history club grouping with shot exclusion (edits 4–5), and real-range distance signs replacing painted band lines (edit 6). No scope beyond the six.

## 2. Decision ledger (user-locked, 2026-09-26)

| # | Request | Resolution |
|---|---|---|
| E1 | Club pill to top-right, under VIEW | **Subsumed into E2** — pill is replaced by the filled club button |
| E2 | Club picker trigger larger + visually distinct | Solid teal **ACTIVE CLUB button** top-right under VIEW; only filled button on range |
| E3 | Add-club: club-type dropdown + temp-club flag | Compact inline **add-club form** (TYPE ▾ · name · TEST checkbox) in picker + Settings BAG |
| E4 | History group-by-club with averages | **[ORDER \| CLUBS]** segmented toggle in detail header; full grouped list |
| E5 | Tick box to exclude mishits from averages | Persisted `shots.excluded`; excluded shots drop from AVG/σ/header stats |
| E6 | Remove ground distance lines → signs | One dark-slate sign per 50 m (50–350), alternating sides, both views |

Work order: Groups 1‑4 as listed (pill/trigger → picker/types → history → signs).

## 3. Room schema v2 (first migration; version 1 → 2, exportSchema)

All three groups' storage changes ride **one** migration:

| Table | Change | Notes |
|---|---|---|
| `clubs` | `+ type: TEXT NOT NULL DEFAULT 'IRON'` | one of `DRIVER, WOOD, HYBRID, DRIVING_IRON, IRON, WEDGE, PUTTER` (enum `name()`) |
| `clubs` | `+ isTemp: INTEGER NOT NULL DEFAULT 0` | 1 = TEST club, auto-deleted at session end |
| `shots` | `+ excluded: INTEGER NOT NULL DEFAULT 0` | 1 = mishit, dropped from averages/σ/aggregates |
| `shots` | `+ clubWasTemp: INTEGER NOT NULL DEFAULT 0` | snapshot at capture — keeps the TEST badge truthful after the temp club is deleted |

Migration SQL (after the two ALTER TABLEs per table): `UPDATE clubs SET type='DRIVER' WHERE name='D'; UPDATE clubs SET type='WOOD' WHERE name IN('3W','5W'); UPDATE clubs SET type='HYBRID' WHERE name='4H'; UPDATE clubs SET type='IRON' WHERE name IN('4i','5i','6i','7i','8i','9i'); UPDATE clubs SET type='WEDGE' WHERE name IN('PW','GW','SW','LW');` — DEFAULT 'IRON' covers anything unmatched. `MIGRATION_1_2` object + committed `2.json`; `DEFAULT_CLUBS` seed gains types for fresh installs. Destructive fallback stays OFF (no `fallbackToDestructiveMigration` today; adding it would wipe M5 sessions).

## 4. Group 1+2 — Club trigger, types, TEST clubs

### 4.1 Trigger button (RangeScreen)

- Top-right column below the existing VIEW chip, right-aligned (today's top-left pill is removed; the top-left column then starts with TRACER/PREV).
- Solid teal fill (`GolfColors.Teal`), ~170 × 58 dp: club name ~26 sp bold, dark text, `▾` suffix, `ACTIVE CLUB` label beneath. Empty state shows `—`. Only filled colored control on the range screen.

### 4.2 Type model

- `ClubType` enum, 7 values in bag order: Driver, Wood, Hybrid, Driving Iron, Iron, Wedge, Putter.
- Bag/picker ordering: **type-first, then `sortOrder`** — the type → ordinal mapping is applied where `observeClubs()` emits (repository), one sort site, trivially testable.
- Manual adds default to IRON in the form; picker tiles stay the current 84 × 72 dp `ClubTile` grid — visually unchanged apart from order.

### 4.3 TEST clubs (temp clubs)

- Created via the add-club form's **TEST checkbox** (amber, caption "session only" — toggle sits under the name field).
- `isTemp=1`; picker lists them under a trailing **TEST section** (amber header; tiles amber-bordered with small TEST label). TEST clubs support everything real clubs do (select as active, retag to, shoot with).
- **Lifetime:** persisted in Room so they survive app restart mid-session (M5 restores the live session + bag); **purged when END SESSION runs** (single rule: every `endSession()` deletes `clubs WHERE isTemp=1` — a TEST club added with no open session lives until the next END SESSION).
- **Shots never rewritten:** `clubName` snapshot + `shots.clubWasTemp` snapshot persist forever; DELETE only touches the bag.
- **ActiveClubStore guard:** after purge, if the stored active name is no longer in the bag, active club falls back to `—` (same rule as any deleted club today, applied on bag re-emission).
- **Duplicates** (M7 shaft testing): user types the suffix — `7i-A`/`7i-B`. Unique-name invariant unchanged; no auto-suffix.

### 4.4 Add-club form (shared: picker overlay + Settings ▸ BAG)

Compact inline row, one row + buttons: `TYPE: IRON ▾` (small dropdown, 7 types) · name field · `☐ TEST` checkbox inline at row end (amber, tiny "session only" caption beneath it); `CANCEL` + `ADD CLUB` below. Validation unchanged (non-blank, ≤ 20 chars, no commas, unique — `addClub` rejects, `SQLiteConstraintException` caught). Settings BAG replaces its current add row with this same component; rename/delete rows unchanged there.

## 5. Group 3 — History club grouping + shot exclusion

### 5.1 Mode toggle

Detail header gains a segmented **[ ORDER | CLUBS ]** toggle (remembered per screen visit, not persisted; default ORDER). ORDER = today's flat table (by `seq`).

### 5.2 Unified shot row (both modes)

All rows use one component with columns **# · CARRY · TOTAL · SIDE · APEX · BALL · CLUB · SMASH · LAUNCH · AXIS · SPIN** + tick cell on the right (FLIGHT TIME deliberately out). Wider than today — the row scrolls horizontally under existing chips, same pattern as the summary chip strip. Long-press → multi-select → retag machinery keep working unchanged in both modes (tick ≠ selection; tick toggles `excluded`).

### 5.3 CLUBS mode

- Groups keyed by `shot.clubName` (`null` → "—"). Ordering: current-bag clubs by type-first bag order; names no longer in the bag (deleted TEST clubs) after them, alphabetical.
- Group header: club name · shot count · σ CARRY · σ BALL · amber **TEST badge** when any included shot has `clubWasTemp=1`.
- Per-club **teal AVG row** directly under each group, aligned column-for-column: every metric averaged over non-excluded shots (side = signed mean; smash, axis averaged too).
- Retag to TEST clubs works with the club list (name chip strip) while such clubs exist.

### 5.4 Exclusion (edit 5)

- Tick on any row flips `shots.excluded` (repository `setShotsExcluded` batch). Excluded row: dimmed, strikethrough, tick drawn amber.
- Excluded shots drop from: per-club AVG + σ rows, session header chips (avg carry, longest). **All SQL aggregates** (`SessionSummaryRow` avg/max + master list summaries) filter `WHERE excluded=0` — misread count and live-only toggle unaffected (independent filters; both compose).

## 6. Group 4 — Range distance signs

### 6.1 Removed

- POV band lines + right-aligned "NN M" labels (PovRangeCanvas ~:204‑217); top-down band lines + edge labels (TopDownCanvas ~:44‑56). Both views.

### 6.2 Added

- One standing sign per distance in `50..350 step 50` (7 signs), **alternating sides starting RIGHT at 50 m**: 50 R, 100 L, 150 R, 200 L, 250 R, 300 L, 350 R.
- Placement: `x = side × (fairwayHalfWidth(y) + 2.5 m)`, `y = distance` (just outside the fairway edge; clear of the greens at ±12 m).
- Style (mockup `range-signs-alternating.html`): dark-slate board (Card bg, Line border) with a thin teal top-edge accent, TextPrimary number **without "M"**, thin Line-colored posts.
- World size: board ~3.0 × 1.9 m on ~1.2 m posts.
- POV rendering: project the board quad through `PovProjector`(corners at z = 1.2…3.1), fill with paint; number via a reused native Paint scaled by projected board height; cull when `groundDepth ≤ 0` (same as bands). Signs are world-fixed → follow-cam correct by construction.
- Top-down rendering: small fixed-size board icon (~26 × 16 dp) at the sign position — fixed size for readability, not world-scaled.
- Dashed tee guides, mow stripes, greens, tracer, and the right metrics panel are untouched.

## 7. Data flow

- `observeClubs()` now emits `ClubRecord(id, name, type, isTemp)` in type-first order; `addClub(name, type, isTest=false)`; `endSession()` = set `endedAt` + purge `isTemp` clubs (single transaction).
- `persist()` stamps `clubName` + `clubWasTemp` from the current club record.
- `setShotsExcluded(ids, excluded)` = single `UPDATE ... WHERE id IN (...)`, mirroring `retagShotIds` (flows re-emit).
- Aggregates (`SessionSummaryRow`, per-club math) read `excluded=0` only.
- History CLUBS/AVG/σ: computed in-memory from the session's `ShotRecord` list (pure functions, unit-testable: grouping, ordering, averages, σ).

## 8. Error handling

- Unchanged M5 posture: no-crash persistence, misreads never open sessions, corrupt-DB recovery still works (v2 migration must run inside the recovery path too — test it).
- Picker with zero TEST clubs hides the TEST section; zero groups in CLUBS mode shows an empty message.

## 9. Testing

- **Pure JVM (`:core:data`, `:app` — `runBlocking` per `GatedFlowTest` pattern, `runTest` where already wired):**
  - stats: averages/σ per group + session chips honoring `excluded` (side signed mean included);
  - grouping + ordering (bag-order first, unknown names after, TEST badge via `clubWasTemp`);
  - `addClub` validation + duplicate rejection with types; temp-club purge on `endSession()` only;
  - `RangeSession`/`persist` stamping `clubWasTemp`;
  - `signPlan()` — pure data: distances, alternating sides, edge-offset positions (RangeScene).
- **Robolectric in-memory Room:** `MIGRATION_1_2` via MigrationTestHelper (createDatabase v1 → migrate → verify columns/seed mapping; schema JSONs committed), `setShotsExcluded` re-emission, aggregates excluding flagged shots.
- **On-device exit checklist:** migrate an existing M5 install (verify bag mapping + shot data intact), run a TEST club end-to-end (add → shoot → END → disappears from bag, stays grouped + badged in history), exclude a mishit (chips recompute), eyeball signs in POV + top-down incl. follow-cam at 250 m+.

## 10. Out of scope

Dispersion ovals / matrix (M6/M7), bag reordering, club-type editing after add, sign customization settings, CSV (M9), any network. E1's pill no longer exists separately (subsumed by E2).

## 11. Risks / watch items

- **First Room migration** — schema export must land (`2.json`) and the corrupt-file recovery path must re-run the migration; tested on-device against a real M5 install before shipping.
- **History row width** — 11 columns + tick needs the horizontal scroll affordance to be obvious on the tablet; keep column set frozen (HistoryFormats).
- **POV sign text scaling** — reuse one native Paint (band-label pattern), scale text by projected board height; watch overdraw with 7 signs + tracer history at 300+ m.
- **Grouping at session scale** — in-memory grouping of a few hundred shots is fine; no SQL GROUP BY needed.
