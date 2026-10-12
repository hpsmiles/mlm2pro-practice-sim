# HISTORY Nav + Fitting Results Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure HISTORY navigation (rail-sized sidebar tabs, per-game switcher, combines + fittings tabs) and rework the fitting results screen (top-down under the table, tee-left orientation, fairway/rough shading, PDF sync).

**Architecture:** History work is one screen rewrite plus AppRoot wiring, reusing existing composables (`NavRailButton`, `FittingHistoryView` rows, `CombineResultsView`, `FittingResultsView`). Fitting work is a new pure horizontal fit function (JVM-tested), a reoriented canvas pane, a mirrored PDF path, and a layout change that makes the results page the single scroll owner. No Room changes — history reuses read-only data flows.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-12-history-nav-and-fitting-results-design.md`

**Branch:** `history-nav-fit-results` off master (`734ccbf`).

**Build commands (Windows, per AGENTS.md)**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain build
.\gradlew.bat --console=plain :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.history.GameHistorySortingTest"
.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.fitting.FittingTopDownFitTest"
.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.fitting.FittingPdfDataTest"
.\gradlew.bat --console=plain :app:installDebug
```

## File Structure

- `app/src/main/kotlin/.../history/GameHistorySorting.kt` (mod) — `defaultGameMode`.
- `app/src/test/kotlin/.../history/GameHistorySortingTest.kt` (mod) — default-game pins.
- `app/src/main/kotlin/.../history/HistoryScreen.kt` (mod) — 4-tab sidebar, games switcher, combines + fittings tabs.
- `app/src/main/kotlin/.../range/AppRoot.kt` (mod) — viewed-fitting state + shots flow, `exportFittingPdf(shots)`, HistoryScreen params.
- `app/src/main/kotlin/.../fitting/FittingHistoryView.kt` (mod) — extract `FittingHistoryRow`.
- `app/src/main/kotlin/.../fitting/FittingTopDownFit.kt` (mod) — add `TopDownFitH` + `computeTopDownFitH`; old isotropic contract deleted in T5.
- `app/src/test/kotlin/.../fitting/FittingTopDownFitTest.kt` (mod) — rewrite fit cases.
- `app/src/main/kotlin/.../fitting/FittingTopDown.kt` (mod) — horizontal mapping + shading.
- `app/src/main/kotlin/.../fitting/FittingPdfWriter.kt`, `FittingPdfData.kt` (mod) — mirrored top-down.
- `app/src/test/kotlin/.../fitting/FittingPdfDataTest.kt` (mod).
- `app/src/main/kotlin/.../fitting/FittingResultsView.kt` (mod) — switch removed, stacked scroll layout.
- `app/src/main/kotlin/.../fitting/FittingController.kt` (mod) — `FittingResultsMode` + `resultsMode` deleted.
- `app/src/main/kotlin/.../fitting/FittingTable.kt` (mod) — internal vertical scroll removed.
- `core/designsystem/src/main/kotlin/.../GolfColors.kt` (mod) — `HoleRough`.

Reused unchanged: `NavRailButton` (core/designsystem), `CombineResultsView`, `FittingResultsView` (API), `GameHistoryFormats`, `RangeScene`, `RangeSigns`.

---

### Task 1: defaultGameMode helper

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/history/GameHistorySorting.kt`
- Modify: `app/src/test/kotlin/com/hpsmiles/golfsim/history/GameHistorySortingTest.kt`

- [ ] **Step 1: failing tests**

In `GameHistorySortingTest.kt`, following the file's existing entity-construction style, add:

```kotlin
@Test fun defaultGameModeEmptyIsTargetPractice() {
    assertEquals(GameModes.TARGET_PRACTICE, defaultGameMode(emptyList()))
}

@Test fun defaultGameModeFollowsMostRecentResult() {
    // newest BP → BP; newest TP → TP (other result older)
    assertEquals(GameModes.BREAK_PANE, defaultGameMode(listOf(tpAt(100), bpAt(200))))
    assertEquals(GameModes.TARGET_PRACTICE, defaultGameMode(listOf(bpAt(100), tpAt(200))))
}

@Test fun defaultGameModeTieFallsBackToTargetPractice() {
    // same playedAtEpochMs across both modes → TARGET PRACTICE
    assertEquals(GameModes.TARGET_PRACTICE, defaultGameMode(listOf(tpAt(200), bpAt(200))))
}
```

(Use the file's existing helper style for building `GameResultEntity`; `tpAt`/`bpAt` above stand for whatever local builders fit the file.)

- [ ] **Step 2: run and watch it fail**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.history.GameHistorySortingTest"`
Expected: FAIL — unresolved reference `defaultGameMode`.

- [ ] **Step 3: implement**

```kotlin
/**
 * The game whose history the GAMES panel shows by default: the mode of the most
 * recent result; an empty list or a cross-mode tie (same playedAtEpochMs) falls
 * back to TARGET PRACTICE.
 */
fun defaultGameMode(results: List<GameResultEntity>): String {
    val latest = results.maxByOrNull { it.playedAtEpochMs } ?: return GameModes.TARGET_PRACTICE
    val latestModes = results.filter { it.playedAtEpochMs == latest.playedAtEpochMs }.map { it.mode }.toSet()
    return if (latestModes.size == 1) latestModes.first() else GameModes.TARGET_PRACTICE
}
```

- [ ] **Step 4: green + commit**

Run the targeted test again. Expected: PASS.

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/history/GameHistorySorting.kt \
        app/src/test/kotlin/com/hpsmiles/golfsim/history/GameHistorySortingTest.kt
git commit -m "feat(history): default game selection helper for the games panel"
```

---

### Task 2: HISTORY restructure (sidebar, games switcher, combines + fittings tabs)

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryScreen.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/fitting/FittingHistoryView.kt`

- [ ] **Step 1: sidebar + tabs** (spec §1)

`HistoryTab` grows to four entries: `SESSIONS("SESSIONS")`, `GAMES("GAMES")`, `COMBINES("COMBINES")`,
`FITTINGS("FITTINGS")`. Replace the `HistorySidebar` body: a `Column` of width `GolfSpacing.NavRailWidth`
(96 dp), `fillMaxSize`, `GolfColors.Panel` background, vertical `Sm` padding, `Sm` spacing, centred — one
`NavRailButton(label = tab.label, selected = tab == active, onClick = { onTabChange(tab) })` per entry
(import from `com.hpsmiles.golfsim.core.designsystem`). Same component and 80×40 size as the rail HISTORY
button. The tab-change handler in `HistoryScreen` also clears `viewedCombineId` (step 4 adds the fitting
clear) — switching tabs always closes an open read-only view.

- [ ] **Step 2: GAMES panel switcher** (spec §2)

`GamesHistory` drops its combine params (`combineHistory`, `combineHistoryShots`, `onOpenCombine`) and gains
`var gameMode by remember { mutableStateOf(defaultGameMode(gameResults)) }`. At the top of the panel, a
segmented row of chips for `GameModes.TARGET_PRACTICE` / `GameModes.BREAK_PANE` (labels via
`GameHistoryFormats.modeLabel`; selected = Teal text + Teal40 background; unselected = `TextMuted` text +
`Line` border; 50% radius, padding horizontal `Sm` / vertical 2 dp — same idiom as the existing ORDER chips).
Below it keep the "GAME SCORES" + RECENT/BEST header and render only `grouped[gameMode].orEmpty()`; empty
state stays "NO GAMES YET". Delete the old COMBINES section (:355-393) from this panel.

- [ ] **Step 3: COMBINES tab** (spec §3)

New `CombinesHistory(combineHistory, combineHistoryShots, onOpenCombine)` composable holding the derivation
moved from `GamesHistory` (:288-302): complete sessions only (`facts.size >= CombinePlan.SHOTS_TOTAL`),
`overallEstimate` HC, own `GameHistorySorting.Order` state with RECENT (startedAtMs desc) / BEST (HC asc)
chips, and the existing row layout (name · date · DEMO marker · HC). Tap → `onOpenCombine(session.id)`
(existing `viewedCombineId` flow unchanged). Empty state "NO COMBINES YET". Add the `when(tab)` branch.

- [ ] **Step 4: FITTINGS tab + wiring** (spec §4)

- `FittingHistoryView.kt`: extract the 64 dp row into `internal fun FittingHistoryRow(session:
  FittingSessionEntity, details: List<Pair<String, Int>>, onClick: () -> Unit)`; `FittingHistoryView` keeps
  its BACK chip + title and uses the extracted row.
- `HistoryScreen` gains params: `fittingController: FittingController`, `fittingHistory:
  List<FittingSessionEntity>`, `fittingHistoryDetails: Map<Long, List<Pair<String, Int>>>`,
  `viewedFittingId: Long?`, `onOpenFitting: (Long?) -> Unit`, `fittingViewedShots: List<FittingShotEntity>`,
  `onSetExcluded: (List<Long>, Boolean) -> Unit`, `onExportFittingPdf: () -> Unit`.
- FITTINGS branch: header "COMPARISON HISTORY" + `LazyColumn` of `FittingHistoryRow`s (empty: "No completed
  comparisons yet"); when `viewedFittingId != null`, render read-only `FittingResultsView(shots =
  fittingViewedShots, readOnly = true, controller = fittingController, onBack = { onOpenFitting(null) },
  onComplete = {}, onSetExcluded = onSetExcluded, onExportPdf = onExportFittingPdf)`. Vanished-session
  guard: `LaunchedEffect(viewedFittingId)` clears when the id is not in `fittingHistory` (mirror the combine
  guard).
- `AppRoot`: add `var historyViewedFittingId by remember { mutableStateOf<Long?>(null) }` and derive
  `historyViewedFittingShots` keyed on it, mirroring the `fitViewedShots` flow (:665-671). Generalise
  `exportFittingPdf(shots: List<FittingShotEntity>)` (drop the internal resolution at :809); the FIT call
  site (:1077) becomes `onExportPdf = { exportFittingPdf(if (fittingController.viewedSessionId != null)
  fitViewedShots else fitShots) }`. Pass the new `HistoryScreen` params, with `onExportFittingPdf = {
  exportFittingPdf(historyViewedFittingShots) }`.
- Extend the tab-change handler to also call `onOpenFitting(null)`.

- [ ] **Step 5: verify + commit**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --rerun-tasks`
Expected: all green (screens are untested by convention; the new logic lives in `defaultGameMode`).

```bash
git commit -m "feat(history): 4-tab sidebar, per-game switcher, combines + fittings tabs"
```

---

### Task 3: Horizontal fit function (pure, tested)

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/fitting/FittingTopDownFit.kt`
- Modify: `app/src/test/kotlin/com/hpsmiles/golfsim/fitting/FittingTopDownFitTest.kt`

- [ ] **Step 1: failing tests**

Rewrite the `computeTopDownFit` cases in `FittingTopDownFitTest.kt` for the new contract (keep any
`DispersionEllipse` coverage). Use `W = 1000.0`, `H = 400.0`:

```kotlin
@Test fun noDataFallbackCoversFairway() {
    val fit = computeTopDownFitH(W, H, 0.0, 0.0, 0.0, hasData = false)
    val px = (W - 26.0 - 12.0) / (330.0 + 2 * 19.8)   // span 330, pad = 0.06 * 330
    assertEquals(px, fit.pxPerMX, 1e-9)
    assertEquals(26.0 + 19.8 * px, fit.originX, 1e-9)  // world 0 sits one pad in from the left
    assertEquals((H / 2 - 12.0) / 35.0, fit.pxPerMY, 1e-9)
    assertEquals(H / 2, fit.midY, 1e-9)
}

@Test fun distanceWindowExpandsAndPads() {
    val fit = computeTopDownFitH(W, H, 150.0, 170.0, 5.0, hasData = true)
    val px = (W - 38.0) / 50.0                          // span 20 → 30, centre 160, lo 145, pad 10
    assertEquals(px, fit.pxPerMX, 1e-9)
    assertEquals(26.0 - 135.0 * px, fit.originX, 1e-9)
    assertEquals(35.0, (H / 2 - 12.0) / fit.pxPerMY, 1e-9)  // corridor floor (14 m × 2.5)
}

@Test fun lateralExpandsForWideDispersion() {
    val fit = computeTopDownFitH(W, H, 100.0, 100.0, 40.0, hasData = true)
    assertEquals(42.0, (H / 2 - 12.0) / fit.pxPerMY, 1e-9)  // 40 + 2 m content margin
}

@Test fun windowEdgesMapToPads() {
    val fit = computeTopDownFitH(W, H, 150.0, 170.0, 5.0, hasData = true)
    assertEquals(26.0, fit.originX + 135.0 * fit.pxPerMX, 1e-9)       // lo − pad at the left pad
    assertEquals(W - 12.0, fit.originX + 185.0 * fit.pxPerMX, 1e-9)   // hi + pad at the right pad
}
```

- [ ] **Step 2: run and watch it fail**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.fitting.FittingTopDownFitTest"`
Expected: FAIL — unresolved reference `computeTopDownFitH`.

- [ ] **Step 3: implement** (keep the old isotropic contract for now — the PDF still uses it until Task 5, so every commit keeps compiling)

In `FittingTopDownFit.kt`:

```kotlin
/**
 * Horizontal (tee-left) plan fit. Screen mapping:
 *   sx(d) = originX + d * pxPerMX   (d = distance metres from the tee)
 *   sy(s) = midY - s * pxPerMY      (s = lateral metres, positive up)
 * [originX] is the screen x of world distance 0 and may fall off-canvas when the
 * fitted distance window excludes the tee.
 */
data class TopDownFitH(
    val pxPerMX: Double,
    val pxPerMY: Double,
    val originX: Double,
    val midY: Double,
)

const val LATERAL_CONTENT_MARGIN_M = 2.0
const val DEFAULT_LEFT_PAD_PX = 26.0
const val DEFAULT_RIGHT_PAD_PX = 12.0
const val DEFAULT_V_PAD_PX = 12.0

fun computeTopDownFitH(
    width: Double,
    height: Double,
    minDistM: Double,
    maxDistM: Double,
    maxAbsSideM: Double,
    hasData: Boolean,
    leftPadPx: Double = DEFAULT_LEFT_PAD_PX,
    rightPadPx: Double = DEFAULT_RIGHT_PAD_PX,
    vPadPx: Double = DEFAULT_V_PAD_PX,
): TopDownFitH {
    val lo = if (hasData) minDistM else 0.0
    val hi = if (hasData) maxDistM else RangeScene.FAIRWAY_END_Y
    val span = maxOf(hi - lo, MIN_SPAN_M)
    val centre = (lo + hi) / 2
    val pad = maxOf(MIN_PAD_M, 0.06 * span)
    val pxPerMX = (width - leftPadPx - rightPadPx) / (span + 2 * pad)
    val originX = leftPadPx - (centre - span / 2 - pad) * pxPerMX
    val lateralHalfM = maxOf(RangeScene.fairwayHalfWidth(0.0) * 2.5, maxAbsSideM + LATERAL_CONTENT_MARGIN_M)
    val pxPerMY = (height / 2 - vPadPx) / lateralHalfM
    return TopDownFitH(pxPerMX, pxPerMY, originX, height / 2)
}
```

- [ ] **Step 4: green + commit**

Run the targeted test again. Expected: PASS.

```bash
git commit -m "feat(fitting): horizontal top-down fit (pure, tested)"
```

---

### Task 4: FittingTopDownPane reorientation + shading

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/fitting/FittingTopDown.kt`
- Modify: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfColors.kt`

- [ ] **Step 1: HoleRough token**

Add next to `HoleFairway` in `GolfColors.kt`: `HoleRough` — a subtle tint distinct from both `Base` and
`HoleFairway`, quiet enough that dots and rings stay readable (suggested start `Color(0xFF101612)`; tune on
device).

- [ ] **Step 2: rewrite the canvas** (spec §6)

In `FittingTopDownPane`, keep the legend row, the "Many clubs" hint, excluded-shot handling and the
"No kept shots yet" empty state. Replace the mapping and elements:

- Fit bounds loop: `minDistM`/`maxDistM` from kept shot totals + ring `projectedHalfTotalM` around
  `centreTotalM`; `maxAbsSideM` = max `|centreSide| + projectedHalfSideM` (fall back to per-shot extremes
  when no ellipse). Then `val fit = computeTopDownFitH(width, height, minDistM, maxDistM, maxAbsSideM,
  hasData, leftPadPx = 26.dp.toPx(), rightPadPx = 12.dp.toPx(), vPadPx = 12.dp.toPx())`.
- Mapping: `sx(d) = fit.originX + d * fit.pxPerMX`, `sy(s) = fit.midY - s * fit.pxPerMY`.
- Shading (drawn first): rough = `drawRect(GolfColors.HoleRough)` over the full canvas; fairway =
  `drawRect(GolfColors.HoleFairway, topLeft = Offset(sx(10f), sy(14f)), size = Size(sx(330f) - sx(10f),
  sy(-14f) - sy(14f)))` (the constant 28 m corridor, `RangeScene.fairwayHalfWidth`).
- Lateral gridlines: horizontal lines at `sy(l)` for `l` in −60..60 step 20 (`GolfColors.Line`, alpha 0.5),
  spanning the canvas.
- Centre line: horizontal at `midY` from `sx(FAIRWAY_TEE_Y)` to `sx(GROUND_END_Y)`, white
  `CENTER_LINE_ALPHA`, 1.5f. Dashed guides: horizontal dashed lines at `sy(l)` for
  `GUIDE_LINE_LATERALS_M`, dash from `GUIDE_DASH_ON_M/OFF_M`, white `GUIDE_LINE_ALPHA`.
- RangeMat: the world rect (`BASE_X_MIN/X_MAX` lateral × `BASE_Y_MIN/Y_MAX` distance) mapped as
  `x = sx(BASE_Y_*)`, `y = sy(BASE_X_*)`, stripes likewise (world → screen swap).
- Ball dot: white 2.sp at `(sx(0f), sy(0f))`.
- RangeSigns: along the bottom edge — each `RangeSigns.signPlan()` board upright with its base at
  `height − vPadPx`, `x = sx(sign.distanceM)`; skip signs whose x is off-canvas.
- Rings: trace each ellipse as a 72-step path in world coords — `worldSide = centreSideM + u·cos − v·sin`,
  `worldDist = centreTotalM + u·sin + v·cos`, screen `(sx(worldDist), sy(worldSide))` (mirror
  `CombineTopDownView` :205-243) — stroke 2 dp, colour alpha 0.55, no fill. This replaces the old
  `rotate(-deg(angleRad))` + `drawOval` (wrong under anisotropic scale).
- Dots: r = 3 dp, alpha 0.8, at `(sx(totalM), sy(sideM))`.

- [ ] **Step 3: verify + commit**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --rerun-tasks`
Expected: all green.

```bash
git commit -m "feat(fitting): tee-left top-down pane with fairway/rough shading"
```

---

### Task 5: PDF sync + old isotropic contract removal

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/fitting/FittingPdfData.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/fitting/FittingPdfWriter.kt`
- Modify: `app/src/test/kotlin/com/hpsmiles/golfsim/fitting/FittingPdfDataTest.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/fitting/FittingTopDownFit.kt`

- [ ] **Step 1: mirror the pane derivation in `FittingPdfData`**

The top-down model (:65, :225-235) mirrors `FittingTopDownPane` — switch it to `computeTopDownFitH` with the
same min/max-distance + max-abs-side derivation (PDF's own width/height).

- [ ] **Step 2: reorient `drawTopDown`**

Vertical distance gridlines every 25 m (labels every 50 m "N m" at the bottom); horizontal centre line at
`midY`; rings path-traced (72-step world→screen, same as the pane — delete the rotate+drawOval and its
rotation-sign comment); dots at `(sx(total), sy(side))`; subtle fairway/rough shading in the light print
theme (light green band `sx(10)..sx(330)` × `sy(±14)` over a near-white rough ground).

- [ ] **Step 3: delete the old contract**

Remove `TopDownFit`, `computeTopDownFit` and constants used only by them (`DEFAULT_LATERAL_PAD_PX`,
`X_EPSILON` if now unused).

- [ ] **Step 4: update tests, verify + commit**

Update `FittingPdfDataTest.kt` for the new derivation; run:
`.\gradlew.bat --console=plain :app:testDebugUnitTest --rerun-tasks` → all green.

```bash
git commit -m "feat(fitting): PDF top-down mirrors the horizontal pane"
```

---

### Task 6: Results layout — top-down under the table

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/fitting/FittingResultsView.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/fitting/FittingController.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/fitting/FittingTable.kt`

- [ ] **Step 1: remove the switch** (spec §5)

Delete the `TABLE | TOP-DOWN` segmented switch (:74-99), the `FittingResultsMode` enum and the
`resultsMode` field from `FittingController` (grep-verified single-use).

- [ ] **Step 2: stacked scroll layout**

Content becomes a single scroll column under the pinned 52 dp header:
`Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()))` containing the table
card (`clip(CornerCard)`, `Card` background, `Md` padding; `FittingTable(..., modifier =
Modifier.fillMaxWidth())`) then `FittingTopDownPane(shots, Modifier.fillMaxWidth().height(360.dp))` then
bottom spacing. The page is the single scroll owner — expanding a club drill-down pushes the top-down down;
nothing overlays it.

- [ ] **Step 3: drop the table's internal scroll**

Remove `verticalScroll(rememberScrollState())` from `FittingTable`'s root Column (keep the shared horizontal
`metricScroll`); the only caller is `FittingResultsView`.

- [ ] **Step 4: verify + commit**

Run: `.\gradlew.bat --console=plain :app:testDebugUnitTest --rerun-tasks`
Expected: all green.

```bash
git commit -m "feat(fitting): results page stacks the top-down under the table"
```

---

### Task 7: Final gate

- [ ] **Step 1:** `.\gradlew.bat --console=plain build` → BUILD SUCCESSFUL (assemble + all tests).
- [ ] **Step 2:** `.\gradlew.bat :app:installDebug` (tablet `TB373FU - 16`).
- [ ] **Step 3:** hand the user the on-device checklist: HISTORY sidebar buttons the same size as the rail
  button; GAMES switcher row (TP/BP) with default following the latest result; COMBINES tab listing with
  read-only open; FITTINGS tab listing with read-only open + SAVE PDF; switching sidebar tabs closes open
  views; fitting results with the table then the top-down below (expanding a club pushes it down); tee-left
  orientation with fairway/rough shading and rings tracking the dots; PDF export from both FIT and HISTORY
  entry points showing the new orientation.
- [ ] **Step 4:** commit any checklist corrections.

## Self-review

- Spec coverage: §1 → T2; §2 → T2; §3 → T2; §4 → T2; §5 → T6; §6 → T3 (fit function), T4 (pane), T5 (PDF).
- Type consistency: `TopDownFitH(pxPerMX, pxPerMY, originX, midY)`, `computeTopDownFitH(...)`,
  `defaultGameMode(results)`, `historyViewedFittingId` / `historyViewedFittingShots`,
  `exportFittingPdf(shots)`, `FittingHistoryRow(session, details, onClick)` — used consistently across tasks.
- No placeholder steps; every task names its files, verification command and commit message. The old
  isotropic fit contract is deleted only in T5, after its last consumer migrates, so every commit compiles.
