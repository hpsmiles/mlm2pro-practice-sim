# Games polish batch — High-score celebration, Break-the-Pane alignment/difficulty, MisRead pill

Date: 2026-09-30
Status: Approved (design dialogue 2026-09-30; pending spec review)
Predecessor: 2026-09-29 games spec (`2026-09-29-games-target-practice-break-pane-design.md`, M5.5).

## 1. Summary

Six user-requested polish edits across Target Practice, Break the Pane, and the range misread status:

1. **Target Practice** result overlay: detect a new high score, show confetti **only on a real new record**, and otherwise show how far off the record you were.
2. **Break the Pane** result overlay: the same high-score/confetti treatment (fewest shots wins).
3. **Break the Pane** pane alignment: recalibrate the reference trajectory so a stock shot breaks the **middle** row (targets currently sit too low).
4. **Break the Pane** tracer intersection: draw a dot where the flight crosses the pane plane.
5. **Break the Pane** difficulty tiers with a larger green (EASY/MEDIUM/HARD).
6. **MisRead pill**: stop the stale persisted count showing as a pill around connection at app launch.

No Room schema change; Range behaviour is otherwise untouched.

## 2. Decisions locked (design dialogue 2026-09-30)

| Topic | Decision |
|---|---|
| High-score scope | Both Target Practice **and** Break the Pane. |
| Celebration trigger | Confetti **only when beating an existing record**. First-ever score shows a plain `FIRST SCORE` with **no** confetti. |
| Off-record text | Show the margin vs the best (TP points, BP shots). |
| Best key | `mode` + `distanceBin` (target rounded to nearest 10 m) + `difficulty`. Derived from the existing `game_results` table — **no new table**. |
| Pane alignment | Recalibrate `ReferenceTrajectory` — mid/iron bracket profile becomes **20° / 6500 rpm** (was 18° / 5500). |
| BP difficulty | Add EASY/MEDIUM/HARD chips; green radius at 140 m: **EASY 10 / MEDIUM 8 / HARD 6 m**, scaled by target. |
| Intersection dot | High-contrast marker (white core + amber ring) at the pane-plane crossing of the latest shot. |
| MisRead pill | On restore, start the live counter at **0**; keep the persisted count for History only. |

## 3. High-score celebration (Target Practice + Break the Pane)

### 3.1 Record source (no schema change)
"Best" is derived from `game_results` (`GameResultEntity`): rows already carry `mode`, `difficulty`, `distanceBin`, `score`, `playedAtEpochMs`. Best = `MAX(score)` for `TARGET_PRACTICE`, `MIN(score)` for `BREAK_PANE`, filtered by `mode = :mode AND difficulty = :difficulty AND distanceBin = :bin`.

Legacy `BREAK_PANE` rows have `difficulty = NULL`; after edit 5 all new BP rows carry a difficulty, so those legacy rows simply don't match the key. Acceptable (pre-feature rows).

Add to `GameResultDao` (`core/data/.../dao/GameResultDao.kt`):
- `@Query("SELECT MAX(score) FROM game_results WHERE mode = :mode AND difficulty = :difficulty AND distanceBin = :bin") suspend fun bestHighScore(...): Int?`
- `@Query("SELECT MIN(score) FROM game_results WHERE mode = :mode AND difficulty = :difficulty AND distanceBin = :bin") suspend fun bestLowScore(...): Int?`

Add to `SessionRepository` a best-effort `suspend fun bestGameScore(mode, difficulty, distanceBin, lowerIsBetter): Int?` (never throws; mirrors `saveGameResult` error contract).

### 3.2 Pure comparison + text
New `games/GameRecord.kt` (pure, JVM-testable):

```
enum class RecordOutcome { FIRST_SCORE, NEW_RECORD, OFF_RECORD }

data class RecordComparison(val outcome: RecordOutcome, val best: Int?, val delta: Int)

object GameRecord {
    fun compare(prevBest: Int?, score: Int, lowerIsBetter: Boolean): RecordComparison
    // prevBest == null        -> FIRST_SCORE (best = null, delta = 0)
    // score better than best  -> NEW_RECORD (delta = improvement)
    // otherwise               -> OFF_RECORD (delta = points/shots behind)
}
```

`lowerIsBetter = true` for BP; `false` for TP. Overlay text (TP): `NEW HIGH SCORE` / `N pts off your best — best M` / `FIRST SCORE`. Overlay text (BP): `NEW HIGH SCORE` / `N shots off your best — best M` / `FIRST SCORE`.

### 3.3 Flow (AppRoot completion path)
`AppRoot.routeShot` (`AppRoot.kt:169-189`) already calls `takeResult()` and launches the save coroutine. Extend that coroutine to:

1. compute `distanceBin` exactly as `saveGameResult` (`Math.round(distanceM / 10.0) * 10`);
2. `sessionRepository.bestGameScore(mode, difficulty, distanceBin, lowerIsBetter)` **before** the insert;
3. compute `GameRecord.compare(prior, score, lowerIsBetter)`;
4. publish it to the owning game state holder (`targetPractice.setRecord(comparison)` / `breakPane.setRecord(comparison)`);
5. `saveGameResult(...)` as today.

The result overlay already waits for the flight animation to reach `FollowCam.endFraction` (`GameResultOverlayGate.kt`), which gives the query time to land. The holder exposes `record: RecordComparison?` (null = not loaded yet → overlay shows the score without celebration, then updates if the comparison arrives).

`distanceBin` helper currently lives inline in `SessionRepository.saveGameResult` (`:308`); extract a small pure `GameRecord.distanceBin(distanceM)` and use it in both places so the key can never drift.

### 3.4 Confetti
New `games/ConfettiBurst.kt` — a `Canvas` particle burst driven by the existing `withFrameNanos` manual loop (games use no `Animatable`/`animate*AsState`; keep that pattern), ~2.5 s, ~60 particles, gravity + drift, celebratory palette from `GolfColors` (teal/white + gold; **amber stays reserved for the live tracer/rest dot**). Rendered inside the result `SectionCard` region, clipped to the play area, only when `outcome == NEW_RECORD`. Cancels cleanly on replay/back (keyed `LaunchedEffect`).

### 3.5 Overlay wiring
- Target Practice: `TargetPracticePlay.kt:150-177` (`SectionCard(title = "RESULT")`) — add record line + confetti.
- Break the Pane: `BreakPanePlay.kt:137-151` (`SectionCard(title = "PANE BROKEN")`) — add record line + confetti.

## 4. Break the Pane — pane alignment (recalibration)

**Root cause (quantified on the user's capture, 2026-09-30):** `PaneGeom` anchors the middle row on `ReferenceTrajectory.paneCrossingHeightM(target)`. The mid bracket (`120 < target ≤ 220`) used `18° / 5500 rpm`, which is flatter than a real iron. Measured: real stock 8i shots (bs 44–49 m/s, LA 18–22°, spin ~6600–7000) cross the pane at 9.5–12.6 m while the anchor sat at `zRef(125) = 9.66 m`, so stock shots broke the **top** row. The synthetic stock 8i (44 m/s, 21°, 7500) also crossed at 11.40 m.

**Change:** `ReferenceTrajectory.profileFor` (`core/physics/.../ReferenceTrajectory.kt:52-56`) mid bracket → `20.0 to 6500` (short and long brackets unchanged).

**Effect (calibration sweep over the captured shots):**
- `refCross(125) = 10.782 m`, `refCross(140) = 12.495 m` (was 9.659 / 11.198).
- Synthetic stock 8i breaks the **middle** row at both 125 m and 140 m.
- Clean captured shots: at 125 m middle=10 / top=1 / bottom=3; at 140 m middle=8 / top=1 / bottom=5 — middle-dominant with bottom and top still reachable, so "break all 9" stays completable.

No change to plane fraction (0.25), cell height (2% of target) or cell width (4%). The bracket discontinuity at 120 m is now small (21°/7500 → 20°/6500).

Tests: update any `ReferenceTrajectoryTest`/`PaneGeomTest` assertion that pins `paneCrossingHeightM` or `zRefM`; add an assertion that a representative stock iron crossing lands in the middle row.

## 5. Break the Pane — difficulty + green size

- `GamesScreen.kt:183-211` `BreakPaneSetup`: add EASY/MEDIUM/HARD chips (default MEDIUM, same UI as `TargetPracticeSetup`) and change `onStart: (Double, Difficulty) -> Unit`; the picker call site (`:85-96`) passes it to `breakPane.start(target, difficulty)`.
- `BreakThePaneGame.kt`:
  - add `var difficulty: Difficulty = Difficulty.MEDIUM; private set`;
  - `fun start(targetM: Double, difficulty: Difficulty)`;
  - `greenRadiusM() = BreakPaneGreen.radiusAt140m(difficulty) * targetM / 140.0`;
  - `takeResult()` returns `difficulty.name` (non-null).
  - The default `simulator` closure already reads `greenRadiusM()`, so the rollout surface picks up the difficulty automatically.
- New pure `games/BreakPaneGreen.kt`: `radiusAt140m(difficulty)` = EASY 10 / MEDIUM 8 / HARD 6. JVM-tested.
- `BreakPanePlay.kt:146` PLAY AGAIN → `game.start(game.targetM, game.difficulty)`.
- Persistence: writes the existing `difficulty` column (no migration). `GameResultEntity` doc note about "null for Break the Pane" becomes stale — update it.

## 6. Break the Pane — intersection dot

Add a helper (in `PaneGeom` or a small `PaneIntersection.kt`) that returns the pane crossing of a shot together with the **normalised reveal fraction** — the position of the crossing within the exact sample list `GameScene.drawTracer` draws (`FollowCam.scaledSamples(result, apexVMin) + RangeRollout.samples(result)`). Then in `BreakPaneCanvas.kt` (after the `drawTracer` call, `:58`) draw a high-contrast marker at the projected `(xM, planeYM, zM)` once `playFraction >= revealFraction`:

- white filled core (radius ~5f) + amber ring (`Stroke ~2f`), so the break point reads against the teal pane and amber tracer.

Keep the fraction math pure and unit-tested (crossing at/after the plane, no crossing → no dot).

## 7. MisRead pill fix

`RangeSession.restore()` (`RangeSession.kt:80-94`) currently does `misreadCount.intValue = restored.misreadCount` (`:91`), reseeding the live pill from the persisted open-session count. Since the app auto-connects on launch, that stale count renders as the red `no read (n)` pill (`RangeScreen.kt:181-202`) right around connection — the reported symptom. The capture contains **no** misread frames (verified), confirming it is not a monitor event.

Change `:91` to `misreadCount.intValue = 0` (start the live pill fresh each launch). The persisted count still flows into History via `RestoredSession`/`SessionRepository`, so no other behaviour changes. `lastMisreadMs = 0L` is unchanged. `dismissMisreads()` still zeroes the live count.

Test: `RangeSessionTest` — restore with a non-zero persisted count → live `misreadCount == 0`.

## 8. Files

**New**
- `app/.../games/GameRecord.kt` — pure comparison + `distanceBin` + text.
- `app/.../games/ConfettiBurst.kt` — particle burst composable.
- `app/.../games/BreakPaneGreen.kt` — pure per-difficulty green radius.
- (`app/.../games/PaneIntersection.kt` — only if the reveal-fraction helper doesn't fit `PaneGeom`.)

**Modified**
- `core/physics/.../ReferenceTrajectory.kt` (profile), `core/physics` tests.
- `core/data/.../dao/GameResultDao.kt`, `core/data/.../SessionRepository.kt`, `core/data/.../entity/GameResultEntity.kt` (doc).
- `app/.../games/BreakThePaneGame.kt`, `GamesScreen.kt`, `BreakPanePlay.kt`, `TargetPracticePlay.kt`, `BreakPaneCanvas.kt`.
- `app/.../range/RangeSession.kt`, `app/.../range/AppRoot.kt`.

**Room:** no schema change / no migration.

## 9. Error handling

- Best-score query is best-effort (never throws); on failure the overlay shows the score with no celebration (`record == null`).
- Confetti cancels on replay/back/navigation.
- Misreads/malformed still never count as shots.

## 10. Testing

Pure JUnit4 (repo patterns):
- `GameRecordTest` — FIRST_SCORE / NEW_RECORD / OFF_RECORD for both higher- and lower-is-better; `distanceBin` rounding.
- `BreakPaneGreenTest` — radii per difficulty.
- Pane recalibration — reference/stock crossing lands in the middle row; rows reachable at 125/140 m; updated `ReferenceTrajectoryTest`/`PaneGeomTest`.
- Intersection reveal-fraction — crossing present/absent.
- `RangeSessionTest` — restore zeroes the live misread count.

Verification:
- `.\gradlew.bat build` green (JAVA_HOME set per AGENTS.md).
- On-tablet: play both games to completion — confirm first-score vs new-record vs off-record messaging, confetti only on a new record, BP stock shot breaks the middle, BP difficulty changes green size, intersection dot visible, and no stale misread pill on launch.

## 11. Exit criteria

- All six edits observable on the tablet; TP and BP completions show the correct record outcome.
- Recalibrated pane: a stock shot breaks the middle at the target distance; all rows reachable.
- No stale `no read` pill at launch.
- Build green including new/updated tests; RANGE behaviour unchanged.
