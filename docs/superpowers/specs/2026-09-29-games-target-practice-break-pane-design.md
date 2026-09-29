# Games — Target Practice & Break the Pane (M5.5) — Design

Date: 2026-09-29
Status: Approved (design dialogue 2026-09-29; pending spec review)
Predecessor: M5x (shipped 2026-09-27, `a24e5aa`). Sits between M5x and M6 on the roadmap.

## 1. Summary

Two arcade-practice game modes layered on the proven shot pipeline (BLE → decoder → physics → Compose):

1. **Target Practice** — set a target distance and difficulty; 5 shots at a pinned green with shaded scoring rings; points by miss distance at rest; max 125.
2. **Break the Pane** — set a target distance; a 3×3 glass pane floats at 25% of the target distance; a shot must pass through an unbroken cell **and come to rest on the green** to break that cell; score = shots to break all 9 cells (lower is better).

Both games write one summary row per completed game (Room v3). Range screens, range sessions, and range history are untouched.

## 2. Decisions locked during design dialogue

| Topic | Decision |
|---|---|
| Architecture | Approach A: new `games` package in `:app`, mode dispatcher in `AppRoot`, game state holders mirroring `RangeSession`; overlays-on-range (B) and new Gradle module (C) rejected. |
| Pane structure | 9 separate breakable cells; each breaks once; score = shots to break all 9. |
| Target Practice scoring | Distance bands (see §4). |
| Game persistence | Summary row per completed game (Room v2→v3 migration). |
| Target input | Slider **and** numeric text field, kept in sync, 50–350 m. |
| Target Practice difficulty | 3 settings (EASY/MEDIUM/HARD) changing ring/scoring bands. |
| Score records | Per distance bin (target rounded to nearest 10 m) and difficulty. |
| Target graphic | Graphic golf pin (flagstick + pennant) at the green centre, **both games**. |
| Target Practice rings | Shaded concentric ground rings drawn per difficulty; landing ring = score. |
| Rollout | **Rest position is the scored position in both games.** Carry is used only for pane-plane crossing. |
| Pane cell size | Option 2: cells ~2% of target tall / 4% wide, vertically anchored on a reference trajectory, so all three rows are reachable by flighting (verified with engine calcs). |
| Rollout physics follow-up | Low-shot rollout (0.9 m for low 8i) looks underestimated → **BounceRollModel review flagged as a separate follow-up, out of scope here.** |

### Physics sanity calcs (engine-verified, 140 m target, pane plane 35 m)

8i variants with green-zone surface (stimp 11, green radius 6 m at pin):

| 8i variant | Carry | Rollout | Rest from pin | Height at 35 m | Row (option 2) |
|---|---|---|---|---|---|
| stock (44 m/s, 21°, 7500 rpm) | 118.1 m | 1.4 m | 20.5 m | 12.6 m | middle |
| high (24°, 8500) | 117.4 m | 2.0 m | 20.6 m | 15.0 m | top |
| low (18°, 6500) | 117.7 m | 0.9 m | 21.4 m | 10.3 m | bottom |
| soft (41 m/s, 23°) | 106.3 m | 1.2 m | 32.5 m | 13.5 m | middle |
| firm (47 m/s) | 131.0 m | 2.1 m | 6.9 m | 12.2 m | middle |

Rejected fixed pane geometry (bottom 2 m, side 12% of target): the reachable crossing-height band for shots that still land on the green is only ~10.3–15.0 m (~one 5.6 m row tall), so bottom/top rows were unbreakable and "break all 9" impossible.

## 3. Navigation & scope

- New `GAMES` entry in the `RangeTab` enum (`AppRoot.kt`), new `NavRailButton("GAMES")`.
- `GamesScreen` mode picker (two cards, M3 design system) with in-screen sub-state enum: `PICKER → SETUP → PLAYING → RESULT` (plain `when`-enum, repo convention — no Navigation-Compose).
- Shot dispatch: `AppRoot` routes `gattClient.onMeasurement` shots to `RangeSession` (RANGE tab) **or** the active game state holder — never both. DEMO (FIRE) shots work in games.
- BLE ARM/CONNECT behaviour unchanged; games work with LIVE or DEMO sources.

## 4. Game 1 — Target Practice

### Setup
- Target distance 50–350 m: slider + numeric text field, bidirectionally synced; typed values clamp to valid range.
- Difficulty chips EASY / MEDIUM / HARD (default MEDIUM) selecting scoring bands:

| Points | EASY | MEDIUM | HARD |
|---|---|---|---|
| 25 | ≤ 3 m | ≤ 2 m | ≤ 1.5 m |
| 15 | ≤ 7 m | ≤ 5 m | ≤ 3.5 m |
| 10 | ≤ 12 m | ≤ 10 m | ≤ 6 m |
| 5 | ≤ 18 m | ≤ 15 m | ≤ 10 m |
| 0 | beyond | beyond | beyond |

Max 125.

### Scene
- Green (`RangeScene.Green`) centred on the **pin graphic** at the target distance.
- **Pin**: flagstick + pennant drawn via `PovProjector` (vertical projected line + small pennant triangle; flat graphic style).
- **Shaded concentric ground rings** around the pin, one per scoring band, scaled by difficulty (teal shades from `GolfColors`, densest at centre).

### Play & scoring
- 5 shots; MISREAD/MALFORMED ignored.
- Miss distance = `hypot(restX, restY − targetM)` — **rest position** (after bounce/roll on green-zone surface), not carry.
- Score HUD: 5 per-shot point indicators, running total; carry + miss-distance chips per landing.
- After shot 5: result overlay (per-shot points, total, average miss distance); PLAY AGAIN (same target/difficulty) or BACK.

## 5. Game 2 — Break the Pane

### Setup
- Target distance 50–350 m (same slider + text field pattern). No difficulty tiers.

### Geometry (all pure config in `PaneGeom`)
- Pane plane at `y = 0.25 × targetM`, centred on the aim line.
- **Cell height = 2% of target** (2.8 m at 140 m); **cell width = 4% of target** (5.6 m at 140 m). Wider than tall — window-like.
- **Vertical anchor**: `ReferenceTrajectory` (pure) solves a stock shot for the target distance (binary search on ball speed over a reference launch/spin profile by distance bracket) so carry ≈ target; the middle row is centred on its crossing height at the pane plane. Bottom edge is derived (≈ zRef − 1.5 × cellHeight); the fixed 2 m bottom rule is dropped (unreachable per §2 calcs).
- Green at target distance centred on the pin; radius ~6 m at 140 m (same scaling as Target Practice).

### Break rule (per shot, in order)
1. Trajectory must cross the pane plane moving forward; crossing point inside an **unbroken** cell → that cell breaks. Boundaries inclusive upward (crossing on a row top belongs to the upper row). Exactly one cell per crossing; first forward crossing wins.
2. Shot must **come to rest on the green** (rest distance from pin ≤ green radius).
3. Both conditions → cell breaks. Either alone → nothing breaks, but the shot counts.
4. Crossing below/above the pane → no break; feedback "under the pane" / "over the pane".

### Score & feedback
- Score = shots taken until all 9 cells broken; **lower is better**.
- Per-shot feedback: cell broke / "hit pane, missed green" / "landed green, missed pane" / "missed both" (short/left/right).
- HUD: 3×3 pane-state mini-map, shots counter. Result overlay: total shots, PLAY AGAIN / BACK.

### Rendering
- Pane = projected quad via `PovProjector.project()` on 4 corners, grid lines between; glass = low-alpha light teal; broken cells near-transparent with faint crack outline; shatter uses `GolfMotion` constants.
- Near-plane quad clipping: new pure `PaneClip` helper (ground-path clip does not apply to vertical quads).

## 6. Architecture

New package `app/src/main/kotlin/com/hpsmiles/golfsim/games/`:

- `GamesScreen.kt` — picker + sub-state machine.
- `TargetPracticeGame.kt`, `BreakThePaneGame.kt` — plain Compose state holders (RangeSession pattern, injectable clock): own setup state, `add(BallData)`, simulate with `GreenZoneSurfaceProvider`, drive scoring/rendering.
- Pure logic (no Android imports, JVM-tested):
  - `TargetPracticeScoring` — bands → points.
  - `PaneGeom` — cell bounds, crossing/intersection from trajectory samples.
  - `PaneClip` — near-plane quad clipping.
  - `ReferenceTrajectory` — stock-shot solver → pane crossing height.
- Rendering: `TargetPracticeCanvas.kt`, `PaneCanvas.kt` reusing `PovProjector`, `RangeCamera`, `RangeScene.Green`, `worldToScreen`, `FollowCam`.
- HUD from design system: `MetricChip`, `SectionCard`, `GolfTypography`, `GolfColors` (teal structure; amber reserved for the live moment).

### Physics prerequisites (in `:core:physics`, small + pure)
1. **Rest position on `ShotResult`**: expose explicit `restX`/`restY` (the bounce/roll end position already exists internally as `endX/endY`); unit tests added.
2. **`GreenZoneSurfaceProvider`**: `SurfaceProvider` returning `Surface.GREEN_NORMAL` inside a green oval (centre + radius), `Surface.FAIRWAY_NORMAL` outside (generalizes `ZoneTable`). Pure + tested.

## 7. Persistence (Room v3)

New table `game_results`:

| Column | Type | Notes |
|---|---|---|
| `id` | PK autoincrement | |
| `mode` | TEXT | `"TARGET_PRACTICE"` \| `"BREAK_PANE"` |
| `difficulty` | TEXT NULL | EASY/MEDIUM/HARD; NULL for Break the Pane |
| `distanceBin` | INTEGER | target distance rounded to nearest 10 m |
| `score` | INTEGER | Target Practice: points (max 125). Break the Pane: shots taken (lower better). Meaning per mode. |
| `source` | INTEGER | LIVE=0 / DEMO=1 (mirrors `ShotSource`) |
| `playedAtEpochMs` | INTEGER | |

- Migration `MIGRATION_2_3` in `Mlm2proDatabase` per AGENTS.md rules: `@ColumnInfo(defaultValue)` matching ALTER TABLE defaults exactly, committed schema JSON under `core/data/schemas/`, `MigrationFrom2Test`.
- `GameResultDao` + `SessionRepository` methods; written once per completed game. No view built in this milestone (deferred unless requested).
- Game shots never enter `SessionRepository.appendShot` or range history.

## 8. Edge cases

- Misreads/malformed: never counted as shots.
- Leaving mid-game: confirm dialog if shots taken; in-progress game discarded; no partial summary row.
- END SESSION: games are independent of range sessions.
- Spin-back rest behind landing: rest is wherever motion stops.
- Rest off green after being on it: not on green (rest rule).
- Crossing exactly on cell boundary: inclusive upward; one cell per crossing.
- No pane crossing (under/over): explicit feedback.
- DEMO games: summary rows marked `source = DEMO` for later filtering.
- Setup validation: typed distance clamps 50–350 m; difficulty defaults MEDIUM.

## 9. Testing

Pure JUnit4, mirroring existing patterns (`RangeSignsTest`, `FollowCamTest`, `RangeSessionTest`, `MigrationFrom1Test`):

- `TargetPracticeScoringTest` — band edges per difficulty, totals.
- `PaneGeomTest` — cell assignment incl. boundaries, crossing detection from synthetic `TrajectorySample` lists, no-crossing, one-cell-per-crossing.
- `ReferenceTrajectoryTest` — convergence (carry ≈ target within tolerance for 50/100/140/200/300 m), determinism.
- `PaneClipTest` — fully visible / partially clipped / fully behind.
- `TargetPracticeGameTest`, `BreakThePaneGameTest` — state holders with real golden `BallData` fixtures; counting, break progression, completion, reset.
- `GreenZoneSurfaceProviderTest` (physics) — oval in/out.
- `MigrationFrom2Test` (data) — schema v2→v3.
- Verification: `.\gradlew.bat build` green + device verification of both games end-to-end on the Lenovo tablet with live MLM2PRO.

## 10. Exit criteria

- Both games playable end-to-end on the tablet (LIVE and DEMO), scores correct against the band/cell rules.
- Completed games persist one summary row each; range history unaffected.
- `.\gradlew.bat build` green including new unit tests and migration test.
- Range behaviour byte-for-byte unchanged (RANGE tab regressions: none).

## 11. Follow-ups recorded (out of scope)

- **BounceRollModel review**: low-shot rollout appears underestimated (0.9 m for a low 8i). Revisit Penner spin-back/retention parameters in a dedicated milestone.
- History view for `game_results` (best/last per distance bin per difficulty).
- Green firmness / stimp setting in game setup (`Surface.green(stimp)` already supports it).
