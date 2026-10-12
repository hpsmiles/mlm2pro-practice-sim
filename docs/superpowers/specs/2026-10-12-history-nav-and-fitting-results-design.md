# Design: HISTORY navigation restructure + fitting results (top-down under table, horizontal + fairway/rough)

Date: 2026-10-12
Status: approved (design reviewed by user; drill-down push-down behaviour confirmed)
Builds on: `docs/superpowers/specs/2026-10-08-m7-club-fitting-design.md`, `docs/superpowers/specs/2026-10-10-m8-custom-combines-design.md`

## Context

Enhancement requests from on-device use of the shipped M7/M8 features:

1. In HISTORY, the SESSIONS/GAMES buttons are too small — make them the same size as the rail HISTORY button.
2. Game selection should be a row of game buttons at the top of the games panel; COMBINES moves out to the same
   level as SESSIONS/GAMES; FITTINGS is added at that level too.
3. Fitting results: the combine top-down view (left→right) is better than the fitting one (up/down); the top-down
   should live under the table instead of its own tab; add fairway/rough shading. Expanding a club drill-down must
   push the top-down down (page flow), never render over it.

## 1. HISTORY sidebar

- `HistoryTab` grows to `SESSIONS | GAMES | COMBINES | FITTINGS` (`HistoryScreen.kt`).
- The sidebar column widens to `GolfSpacing.NavRailWidth` (96 dp), Panel background, vertical Sm padding, Sm
  spacing — mirrors `NavRail`. Each tab is a `NavRailButton(label, selected, onClick)` (core/designsystem):
  80×40 dp box, 13 sp label, Teal40 background + Teal text when selected — literally the same component and size
  as the rail HISTORY button next to it. Replaces the current 56 dp text-pill sidebar.
- Switching sidebar tabs closes any open read-only results view (clears `viewedCombineId` and the hoisted
  fitting-view state, §4).

## 2. GAMES panel (per-game switcher)

- Top of the panel: a segmented switch `[TARGET PRACTICE | BREAK THE PANE]` — the app's segmented-chip idiom
  (Row, Panel background, 50% radius, Line border, 2 dp padding; selected chip = Teal text + Teal40 background,
  as used for ORDER|CLUBS and the setup chips). It selects which game's history is listed below.
- Below it the existing header ("GAME SCORES" + RECENT/BEST chips) and `GameResultRow` list stay, now filtered to
  the selected game.
- Default selected game = the game of the most recent result (max `playedAtEpochMs`; tie → TARGET PRACTICE),
  else TARGET PRACTICE. Extract as a pure function in `GameHistorySorting.kt` —
  `defaultGameMode(results: List<GameResultEntity>): String` returning `GameModes.TARGET_PRACTICE` /
  `GameModes.BREAK_PANE` — with a JVM unit test.
- Empty state "NO GAMES YET" kept (per selected game).
- The COMBINES section is removed from this panel (moves to §3).

## 3. COMBINES tab

- The combines list moves out of `GamesHistory` into its own tab: rows with combine name · date (· DEMO marker) ·
  HC, complete sessions only (`facts.size >= CombinePlan.SHOTS_TOTAL`), same derivation as today.
- Own RECENT/BEST chips: RECENT = `startedAtMs` desc; BEST = HC asc (lower is better).
- Tap → read-only `CombineResultsView` in place — the existing `viewedCombineId` flow is unchanged.
- Empty state "NO COMBINES YET".

## 4. FITTINGS tab

- Fitting comparison history rendered inside HISTORY, mirroring `FittingHistoryView` rows (date, colour-dotted
  club names, "N shots", "VIEW →") — no BACK chip (the sidebar is the navigation). Extract the row from
  `FittingHistoryView` as a shared internal composable for reuse.
- Tap → read-only `FittingResultsView` in place. Wiring:
  - AppRoot hoists `historyViewedFittingId: Long?` and derives `historyViewedFittingShots` via
    `observeFittingShots(id)` — mirror of the `fitViewedShots` flow.
  - `HistoryScreen` gains params: `fittingHistory`, `fittingHistoryDetails`, `viewedFittingId`,
    `onOpenFitting(Long?)`, `fittingViewedShots`, `onExportFittingPdf`.
  - The embedded results view uses the shared `fittingController` (baseline / distance-mode prefs shared with the
    FIT tab — same as the existing FIT-history flow) with `readOnly = true`.
  - SAVE PDF in the embed exports that session: generalise AppRoot's `exportFittingPdf` to take the shot list —
    FIT call site builds its closure (`viewedSessionId != null ? fitViewedShots : fitShots`), HISTORY passes its
    viewed shots. `FittingScreen`'s API (`onExportPdf: () -> Unit`) is unchanged.
  - If the viewed session disappears from history, reset the view (same guard as the combine embed).
- Empty state reuses "No completed comparisons yet" (existing text).

## 5. Fit results: top-down under the table

- Remove the `TABLE | TOP-DOWN` segmented switch; delete `FittingResultsMode` and `controller.resultsMode`
  (grep-verified: used only by `FittingResultsView` + `FittingController`).
- Layout: the 52 dp header row (BACK / context / SAVE PDF / END COMPARISON) stays pinned; below it a single
  scrollable column (`weight(1f)`) holds the table card, then the top-down pane (`fillMaxWidth`, fixed ~360 dp —
  device-tunable), then bottom spacing.
- `FittingTable` loses its internal vertical scroll (only caller is `FittingResultsView`) and is called with
  `fillMaxWidth`; the page becomes the single scroll owner. The horizontal metric scroll and frozen CLUB column
  are unaffected (orthogonal axis).
- Expanding a club drill-down (rows added below the club) grows the page and **pushes the top-down down**; it can
  never render over it — column flow, not an overlay. Applies to live and history-opened results alike.

## 6. Fit top-down: horizontal orientation + fairway/rough shading

- Mapping mirrors the combine view: tee left → distance right (`sx(d) = leftPad + d·pxPerMX`), lateral on the
  y-axis (`sy(s) = midY − s·pxPerMY`); anisotropic scales.
- Fit windows: distance = kept shots + ring extents + pad (existing min-span/min-pad rules); lateral half-extent =
  `max(corridorHalfM × 2.5 = 35 m, content extent + margin)` so the 28 m fairway is always visible. New pure fit
  function (JVM-tested, replaces the isotropic `computeTopDownFit` contract; keeps a no-data fallback).
- Kept elements, re-oriented: legend chips unchanged; RangeSigns distance boards stand along the bottom edge at
  `x = sx(distanceM)`; lateral gridlines become horizontal; centre line + dashed guide lines horizontal; RangeMat
  becomes the rotated world rect; ball dot at `(sx(0), sy(0))`; per-club rings traced as 72-step world→screen
  paths (mirror of the combine view — required under anisotropic scale, and replaces the old rotation-sign
  pitfall); dots r = 3 dp, alpha 0.8; empty state kept.
- Shading: whole pane ground = rough tint; fairway band = lateral ±14 m (`RangeScene.fairwayHalfWidth`),
  distance 10–330 m, filled `GolfColors.HoleFairway` (same token the combine corridor uses). Add a subtle
  `HoleRough` token to `GolfColors` — must stay quiet enough that dots/rings remain readable. The combine view is
  unchanged.
- PDF sync: `FittingPdfWriter.drawTopDown` and the `FittingPdfData` top-down derivation mirror the new pane
  (orientation, windows, path-traced rings; light print theme, shading subtle).

## 7. Wiring summary

- `AppRoot`: `historyViewedFittingId` state + shots flow; `exportFittingPdf(shots)` generalisation; new
  `HistoryScreen` params; FIT `onExportPdf` closure.
- `HistoryScreen`: enum +2 entries; sidebar swap; GAMES panel changes; new combines/fittings tabs.
- `GamesScreen` (play tab) and the combine view are unchanged. No Room/schema changes.

## Tests

- `FittingTopDownFitTest` — update to the new fit contract (4 existing `computeTopDownFit` cases).
- `FittingPdfDataTest` — update for the mirrored derivation.
- New pure test for the default-game-selection helper.
- Existing history tests unaffected; `GamesScreenTest` (GameMode pin) unaffected.
- Verification: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` → `.\gradlew.bat build` +
  targeted `:app:testDebugUnitTest`, then `:app:installDebug` for an on-device pass (sidebar sizing, game
  switcher, combines/fittings tabs, drill-down push-down, orientation + shading, PDF export from both entry
  points, ring-vs-dots alignment).

## Out of scope

Combine view changes; GamesScreen play-tab cards; Range/Sessions views; Room changes; new fitting metrics.
