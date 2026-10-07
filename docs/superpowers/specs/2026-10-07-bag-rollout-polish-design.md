# Design: HLA metric everywhere, bag mapping order/views, dual-metric results, rollout recalibration

Date: 2026-10-07
Status: shipped 2026-10-07 (commits 15a2cac..b3fb514)
Origin: tablet follow-ups after M6 bag mapping shipped (ROADMAP.md:83)

Five items: (1) show Launch Direction (HLA) on all metric displays, (2) bag mapping
starts at bottom wedge with a Random option, (3) range view visible while collecting
bag mapping shots, (4) bag mapping results show carry AND total simultaneously,
(5) physics: driver rollout too low on fairway + rough out-rolling fairway.

---

## 1. Launch direction (HLA) on all metric displays

Scope confirmed by user: Range + games panel + History table. Bag mapping results
keep their carry/total focus (no HLA there).

Data already flows end-to-end — display-only work:
- `BallData.launchDirection` (`core/ble/BallData.kt:19`, parsed `MeasurementParser.kt:24`)
- `ShotEntity.launchDirDeg` (`core/data/entity/ShotEntity.kt:34`)
- `ShotRecord` embeds full `BallData` (`core/data/record/Mappers.kt:8`)

Changes:
- `history/HistoryFormats.kt`: new `dir(deg)` formatter — `"%+.1f°"` with U+2212 minus
  (match existing convention), signed + right / − left.
- `range/RangeScreen.kt` LAST SHOT chips (~:331–357): new MetricChip `DIR` adjacent to
  the launch-angle chip (:355); value = `shot.ballData.launchDirection` (DisplayShot has BallData).
- `games/GameMetricsPanel.kt` (:55–86): identical chip in the identical position — keep
  the "mirrors RangeScreen" invariant true.
- `history/HistoryScreen.kt`: new DIR column in `ShotRow` (:576–628, after launch :614),
  `HeaderRow` (:508–518) and club average in `AvgRow` (:541–562). `GroupHeaderRow` unchanged
  (no σ dir). `HistoryGrouping.kt` `ClubStats` (:32–78): add `avgDirDeg` + aggregation.
- Formatting/units: degrees, signed; HLA is not affected by unit preference (none exists).

## 2. Bag mapping order: wedge-first default + Random

Current: plan = `clubRecords.filter { !it.isTemp && it.type != PUTTER }` (`AppRoot.kt:577`),
sorted long→short (`SessionRepository.kt:78` + `ClubType.kt:7–14`), session starts at
`currentIndex 0` = driver (`BagMappingCollector.kt:69`). Reorder UI only sorts within
type groups (`BagReorderPanel` / `moveClubWithinTypeGroup`).

Change:
- `startBagTest` gains an order mode: `WEDGE_FIRST` (full plan reversed → LW first,
  ascending to driver) | `RANDOM` (full plan shuffled).
- Reversal/shuffle happens BEFORE `encodeClubSnapshot`, so persistence/resume semantics
  are unchanged; `BagMappingCollector` keeps iterating the plan it is given.
- UI: `BagMappingIntro.kt` order selector, two options, default `WEDGE_FIRST`.
  Designer owns the selector visuals; the chosen mode is exposed via a callback seam
  (`onOrderSelected`-style param) wired by the logic lane in `AppRoot`.
- Random = `kotlin.random.shuffle`, default seed.
- Driver-first mode is dropped (approved; YAGNI).

## 3. Range view visible while collecting

Current: COLLECTING renders `BagMappingCollecting` (`BagMappingCollecting.kt:39`) as a
full-screen text-only panel; live shots route into the collector only when
`tab == RangeTab.BAG && bagCollecting` (`AppRoot.kt:263–277`); leaving the BAG tab pauses
capture (`BagMappingScreen.kt:68–70`).

Change:
- BAG tab collecting state renders the **same range/target view as the RANGE tab live
  view** (tracer, landing marker, last-shot rendering) full-size.
- `BagMappingCollecting` shrinks to a **compact overlay card** on top of it: club banner,
  progress dots, last carry, no-read pill, quality-gate prompt, SKIP/END controls.
  Semi-transparent so the target view stays readable.
- Shot routing, pause-on-leave, and collector semantics unchanged.
- Reuse/extract the range live-view canvas composable; no duplicated rendering logic.
- Designer owns layout/interaction of the overlay.

## 4. Results: carry + total dual boxes per club row

Current: `CarryMatrixSection` (`BagMappingResult.kt:103`) renders a carry-only box-plot
matrix (`CarryMatrixCanvas.kt`); `totalM` is computed and stored
(`BagMappingCollector.kt:50–53`, `:88–104`) but never rendered. `DrillDownPanel`
(:211) shows per-shot carry + smash (:241–243). Gap flags from `GapAnalysis` are carry-based.

Change:
- Each club row shows **two boxes — carry and total — on one shared metre axis** spanning
  `[min kept carry, max kept total]`.
- Two-color legend (CARRY / TOTAL); median labels for both; σ/kept counts remain visible.
- Gap flags stay carry-based. Drill-down rows gain total distance per shot.
- `BagMappingStats.distribution` (:69) reused for both metrics over kept shots.
- Designer owns visual treatment (colors, ticks, spacing).

## 5. Physics recalibration: fairway rollout + rough ordering

User observation (quoted): *"Range and Target Practice, when hitting driver at a 240m
target. Balls that were straight but a little short had minimal roll out, but left shots
had more rollout. Some explained by lower launch and spin, but the difference was large.
The lack of rollout on the fairway may be the bigger issue."*

### Root cause (hand-verified against code)
In `BounceRollModel.kt:110–141` the branch flips at `impactAngle == surface.thetaCritRad`:
- **Steep branch** (:117–134): `angleBlend` (:131–133, span `STEEP_BLEND_SPAN_RAD = 0.35`)
  blends `vh * retention` toward small `ejection − backImpulse`. For a straight driver
  (impact ≈ 23°, ≈ 2600 rpm, fairway `thetaCrit` 0.29 rad): post-bounce speed collapses
  ~11 → ~7.6 m/s → **~10 m rollout**; real driver on normal fairway rolls 20–35 m.
- **Shallow branch** (:135–141): `vh * retention + SHALLOW_SKID_MAX * shallow * lowSpin`
  → for the same driver arriving just under thetaCrit ≈ 12.7 m/s → **~30 m rollout**.
  ±1° landing angle ⇒ ~3× rollout — the straight-vs-pulled asymmetry.
- **Rough ordering**: `ROUGH_NORMAL.thetaCritRad = 0.35` > fairway 0.29
  (`Surface.kt:88–95`) → the same driver impact is "steep" (slow) on fairway but "shallow"
  (fast) on rough → rough out-rolls fairway. `BounceRollModelTest.roughStopsShorterThanFairway`
  only covers a 26.5° steep landing, so it misses the driver band.

Surfaces in use: range = `FAIRWAY_NORMAL.withFirmness(FIRM)` + green oval
(`RangeSession.kt:171–196`); games = `GreenZoneSurfaceProvider` with normal fairway;
bag mapping = `UniformSurface(FAIRWAY_NORMAL)`. Rough is unreachable in-app today; the
invariant is still fixed at model level.

### Requirements
1. Tangential speed after bounce is **continuous and monotonic vs impact angle across
   thetaCrit** (no cliff).
2. Driver fairway rollout lands in a realistic band: **~18–35 m on normal fairway**
   (carry ≈ 230–250 m); firm fairway ≥ normal; green behavior untouched.
3. **Rough rollout ≤ fairway rollout for identical landing states** at all angles,
   especially the driver band (15–25°).
4. Existing pins stay green: `TourAveragesTest`, `BounceRollModelTest`,
   `BounceRollSkidTest`, `SurfaceTest`, `RestPositionTest`, `TourOrderingTest`.
5. Document the deliberate recalibration in code (Surface/BounceRollModel carry
   "frozen M2 law" comments — mark the recalibration as intentional with date).
6. Escalate to @oracle if the first calibration pass breaks existing pins.

### Process
- Build a deterministic JVM diagnostic first (pure sweep: driver/iron/wedge landing
  states × impact angle 10–50° × spin 1500–10000 rpm × surfaces {fairway, firm fairway,
  rough, green} → rollout table). Steer param changes with it.
- Add regression tests: driver-band continuity (rollout varies smoothly, no 3× jumps),
  rough ≤ fairway ordering across angles/spins.
- Accepted side effect: higher fairway rollout shifts rest positions slightly in
  Target Practice / Break the Pane bands.

---

## Testing & build conventions
- Unit tests: `HistoryGrouping` avg dir; plan order reversal/shuffle (extract pure helper);
  dual distributions in `BagMappingStats` (existing pattern); physics regression tests above.
- All: `.\gradlew.bat build`; module tests via typed `testDebugUnitTest` variants
  (AGENTS.md conventions; `$env:JAVA_HOME` required).

## Implementation lanes (post plan approval)
- **Lane A (fixer)**: HLA metric — RangeScreen, GameMetricsPanel, HistoryScreen,
  HistoryGrouping, HistoryFormats.
- **Lane B (designer)**: bag UI — collecting overlay over range view (item 3), dual-box
  results matrix (item 4), intro order selector visuals (item 2 UI seam only, no AppRoot).
- **Lane C (fixer)**: physics recalibration (core/physics only).
- **Lane D (fixer, after B)**: bag order logic — `startBagTest(orderMode)`, plan
  reverse/shuffle helper, AppRoot wiring of the intro seam.
- Parallel: A ∥ B ∥ C; D after B (bag/*.kt + AppRoot ownership).
