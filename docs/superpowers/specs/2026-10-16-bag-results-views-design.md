# Bag Mapping results — new SIDE and TOP-DOWN views

**Date:** 2026-10-16
**Status:** design settled with the user in brainstorming; this document is the spec for review
**Context:** `docs/handover/2026-10-16-bag-results-views-handover.md` (Request A), the M6 bag-mapping feature, and the gap-band work of 2026-10-16 (`GapAnalysis`)

---

## 1. Why

The BAG MAPPING RESULT page is currently one long `LazyColumn`: legend, sticky metre scale, a box-plot row per club (big C/T medians, min–max range line, kept counts), then a DISTANCE GAPS block. Distance is the only lens on the data, and the page leads with it.

Distance alone is not enough to bag map. A player also needs to see **trajectory** (does the 6-iron fly like a 6-iron, or like a 5-iron with a low flight?) and **dispersion** (are the shots grouping, or scattered?). Both are already captured per shot — we simply never show them. Meanwhile the box-plot matrix, which is genuinely useful, occupies the whole page and forces scrolling past 13 cards to reach the gap summary.

The reference the user brought is SkyTrak's "Bag Mapping Overview": one shared-axis chart of every club's flight arc, landing on a single distance axis, with a club header strip, a compact summary of gap statistics, a shaded band marking where clubs bunch, and a CARRY | TOTAL toggle. It deliberately shows no box plots and no per-shot detail — aggregates only.

## 2. Scope

**In scope**

- A new **SIDE** view (default) and a new **TOP-DOWN** view, switched by a segmented control at the top of the results page.
- A **CARRY | TOTAL** toggle governing the distance basis of the new views and of the gap analysis.
- A compact **summary strip** of gap statistics above the chart.
- The existing box-plot matrix and the DISTANCE GAPS block moved into a **collapsed-by-default "DISTRIBUTION & GAPS" panel** at the bottom of the page.
- A **13-hue club ramp** used by the new views (one colour per club, by bag order).

**Out of scope**

- The gap *maths* — the proportional 4–10 % band, the driver exclusion, the flag words and their colours all stay exactly as shipped on 2026-10-16. The only change is which distance the band is applied to, which becomes basis-selected (§5).
- The drill-down shot table (still a full-screen overlay; unchanged).
- The collection workflow, the intro/order screen, the history screen, and the Room schema (no migration; nothing new is persisted).
- `FittingColors.clubColor` and the FITTING / COMBINE views — they keep the existing six-colour cycling convention.
- Refactoring `CombineTopDownView` (we mirror its window-crop pattern rather than extracting a shared helper).

## 3. Decisions settled with the user

| # | Decision |
|---|---|
| 1 | Both new views ship now (SIDE + TOP-DOWN), not just the SkyTrak-style one. |
| 2 | Arcs are **real simulated trajectories** from our own `BallFlightEngine`, not SkyTrak's decorative identical parabolas. |
| 3 | Box plots **and** the gap block both move into the collapsed panel. |
| 4 | Chart treatment **B** — labels at the landing points, no club strip above the plot, distance axis at the **bottom**. |
| 5 | Labels are **two staggered rows** of normal left-to-right text (not rotated). |
| 6 | Palette is a **13-hue bag ramp**, one distinct colour per club. |
| 7 | TOP-DOWN identifies clubs by a **legend row** of colour chip + club name; no names at the rings. |
| 8 | The legend carries **no shot count** (explicit user correction). |
| 9 | **Gaps follow the toggle**: CARRY mode analyses carry distances, TOTAL mode analyses total distances. |
| 10 | Arcs and dots are **tappable** into the shot table. |
| 11 | The driver stays excluded from gap analysis (decided 2026-10-16, unchanged). |

## 4. Page structure

Top to bottom, inside the existing results `Column`:

1. Title + date, stale-resume banner — **unchanged**.
2. `SIDE | TOP-DOWN` segmented control.
3. `CARRY | TOTAL` toggle.
4. Summary strip (one line of statistics).
5. The chart — takes the flex weight (`Modifier.weight(1f)`), roughly 55 % of the page height.
6. Collapsed "DISTRIBUTION & GAPS" panel (header row always visible; body expands).
7. RETEST / HISTORY row — **unchanged**.

The drill-down stays a full-screen overlay above everything.

State lives in `BagMappingResult` as plain `remember` (the bag/games pattern — no ViewModel):

```kotlin
enum class BagResultsView { SIDE, TOP_DOWN }
enum class DistanceBasis { CARRY, TOTAL }

var view by remember { mutableStateOf(BagResultsView.SIDE) }
var basis by remember { mutableStateOf(DistanceBasis.CARRY) }
var panelOpen by remember { mutableStateOf(false) }
```

All three reset when the user leaves the results page. That is deliberate — the page should open on the view that best answers "how is my bag gapped", not wherever it was left.

## 5. The CARRY | TOTAL basis

`DistanceBasis` drives:

- the **arc endpoint** (CARRY: the landing point; TOTAL: the rest position after rollout),
- the **distance axis extent** (TOTAL runs further),
- the **landing numbers** printed at each label,
- the **top-down** dot distances and therefore the dispersion rings,
- the **summary strip** gap statistics,
- the **DISTANCE GAPS block** verdicts inside the panel.

The box-plot matrix inside the panel is **unaffected** — it always shows both lanes (carry teal above, total blue below), exactly as today. The panel's gap block names its basis in its heading so the two never look contradictory:

- `DISTANCE GAPS · CARRY`
- `DISTANCE GAPS · TOTAL`

**Known assumption.** The 4–10 % band fractions in `GapAnalysis` were derived from carry-based research (see its doc comments). TOTAL mode applies the same fractions to total distances, which is the natural extension of a proportional rule and keeps the bands wider in absolute metres because totals are longer. If the TOTAL bands read badly in use, the fractions are the tuning knob — they are public constants in `GapAnalysis`.

## 6. SIDE view

### 6.1 Data per club

For each club in the session snapshot, in bag order, using **kept shots only** (`effectiveIncluded()`):

1. Compute the median of each launch metric — `ballSpeedMps`, `launchAngleDeg`, `totalSpinRpm`, `launchDirDeg`, `spinAxisDeg` — with `BagMappingStats.median`.
2. Build a `LaunchConditions`. Its `init` block enforces `ballSpeedMps in 0.5..100`, `spinRpm in 0..12000`, `launchAngleDeg in 0..85`, `launchDirDeg`/`spinAxisDeg in -90..90`. A club whose medians fall outside those ranges is **skipped** (no arc, no label) rather than allowed to throw. In practice the decoder already guarantees them; this is a defensive guard for legacy rows.
3. `BallFlightEngine.simulate(launch)` → `ShotResult`. For `DistanceBasis.TOTAL`, append `RangeRollout.samples(shot)` to `shot.samples` to get the ground phase.
4. Convert the polyline to the side view's 2-D frame: for each sample, **downrange** = `hypot(px, py)` and **height** = `pz`.

A club with **zero kept shots** produces no arc.

### 6.2 Scale-to-fit, and why

The authoritative number for a club is the **median of the persisted distance cache** (`carryM` for CARRY, `totalM` for TOTAL) over its kept shots — that is the same number the matrix, the gap analysis and the drill-down show.

A trajectory simulated from the *median of the metrics* does not land at the *median of the distances* (the median of a function is not the function of the medians), and the persisted caches were written at capture time by an engine whose bounce/roll law is periodically recalibrated. So the simulated trace is **uniformly scaled by `k = medianDistanceM / traceEndDistanceM`** (both downrange and height) so that it ends exactly at the printed number.

This is a deliberate, documented fudge: it keeps **one number per club** across the whole page and preserves the arc's *shape* (which is what carries the launch-angle and spin information) while discarding an absolute distance the trace was never authoritative for anyway.

### 6.3 Geometry

- **Distance axis**: from `0` (the tee) to `1.06 × max(medianDistanceM)` for headroom.
- **Ticks**: `BoxPlotGeom.niceTicks(0.0, hiM)` — for the live 237 m session this yields 20 m steps (0, 20, … 260). Ticks and their labels are drawn **beneath** the axis line.
- **Height axis**: auto-scaled so the tallest arc's apex fills **85 %** of the plot height; **unlabelled**, with a small caption "height, not to scale" near the origin. One shared vertical scale across all clubs, so relative apex heights stay truthful — a wedge visibly arcs higher than a driver.
- **Origin marker**: a small tee/origin tick at x = 0, labelled "tee".

### 6.4 Drawing

For each club, in order:

1. A **polyline** through the sampled points, in the club's ramp colour, stroked ~2 dp with round joins. Landing point marked with a filled dot (~4 dp) in the same colour with a thin white rim (the fitting top-down's dot treatment).
2. The arc is drawn **first**, all arcs beneath all markers and labels.

### 6.5 Labels

Each club gets one label at its landing x, alternating between two rows beneath the axis (`row = clubIndex % 2`), with a short leader line from the axis down to the label. Text is `"<club name> <distance>"`, where the distance is the club's **median distance for the current basis** — the same number the arc lands on (§6.2) — rounded to whole metres, in the club's ramp colour.

Labels are **real composables positioned in a `Box` over the canvas**, not canvas text — so they are accessible, are real tap targets, and wrap/ellipsis correctly. `Modifier.offset { }` places each one from the same axis fit the canvas uses.

**Accepted limitation** (user-accepted in the mockup): the two-row stagger is a fixed alternation, not a collision solver. With the live bag, 5i (176 m) and 7i (160 m) share a row ~48 px apart, so unusually long club names or 4-digit distances can begin to touch. A real de-collision pass is out of scope.

**Tap targets**: the landing marker and the label both open the drill-down for that club. The arc body itself is **not** hit-tested — the marker and label sit exactly where a user aims, and hit-testing a polyline adds cost and error for no practical gain. This is the interpretation of "arcs and dots are tappable" recorded in the spec; if the user wants the arc body tappable too, that is a follow-up.

## 7. TOP-DOWN view

True-scale, mirroring the fitting top-down's palette, ground treatment and dot/ring recipe (`FittingTopDown.kt`, `FittingTopDownFit.kt`, `CombineTopDownView.kt` as the model).

**Per kept shot** of each club, one `BallFlightEngine.simulate` from that shot's own launch conditions, giving:

- **lateral** — CARRY: the `px` of the **last flight sample**; TOTAL: `ShotResult.sideM` (the rest position's lateral offset).
- **distance** — the **persisted cache**: `shot.carryM` for CARRY, `shot.totalM` for TOTAL.

Using the cache for distance and the simulation only for lateral is deliberate: the roll law (the part that can be recalibrated) affects distance, never lateral deviation, and the cache is the number shown everywhere else on the page. The simulation supplies exactly the one thing the entity does not store.

`BagMappingShotEntity` has **no lateral column** — this is why a simulation is needed at all. It is cheap (a few ms per shot) and deterministic.

**Rings**: `DispersionEllipse.fromKept(points)` over the club's kept shots' `(lateral, distance)` pairs. It returns `null` for fewer than 3 points or a degenerate (collinear) cloud, so 1–2 shot clubs draw dots only.

**Window**: mirror the combine view's inline crop — `floor(minDistance / 25) * 25` coerced at least 0, `ceil(maxDistance / 25) * 25` coerced at most `RangeScene.FAIRWAY_END_Y`, a minimum 50 m window with the same re-centring guard, `leftPad`/`rightPad` from the canvas, then `pxPerM = (width - leftPad - rightPad) / windowWidthM`. Lateral half-window sized to fit the widest content (corridor visibility scale ∪ `maxAbsSideM` + margin), as the combine view does.

**Ground**: `GolfColors.RangeRough` base, the `RangeFairway` corridor, `RangeScene.stripeBand` mown stripes intersected with the cropped window.

**Legend**: a wrapping `FlowRow` of **colour chip + club name only — no shot count** — above the plot.

**Dots**: white α0.6 @4 dp rim + ramp colour α0.9 @3 dp (the fitting recipe). Rings: white α0.55 @3.5 dp keyline + ramp colour α0.7 @2 dp, drawn beneath the dots. A dot is a tap target into the drill-down for its club.

**Excluded shots never appear** — every dot, ring and count comes from kept shots.

## 8. Summary strip

One line above the chart, built from the **same** `GapAnalysis` result the panel's gap block uses (so the two can never disagree):

- `AVG GAP` — mean of the gap distances, 1 dp (a mean, so it is not a distance any pair actually has).
- `CLOSEST` — the smallest gap with its club pair, whole metres.
- `FARTHEST` — the largest gap with its club pair, whole metres.
- counts — `n good / n big / n tight`, tinted teal / amber / grey to match the gap block.

All four follow the current basis and exclude the driver. With the live session in CARRY mode this reads `AVG GAP 12.4 m · CLOSEST 4 m (5i–4i) · FARTHEST 22 m (SW–GW) · 5 good / 4 big / 2 tight`.

## 9. Collapsed "DISTRIBUTION & GAPS" panel

A header row that is always visible — a chevron plus `DISTRIBUTION & GAPS` and a one-line hint — with a tappable row that toggles `panelOpen`. Collapsed by default. When open, it contains, in order:

1. The existing box-plot matrix, unchanged: legend (`CARRY` / `TOTAL` swatches), sticky metre scale, one tappable `ClubRow` per club with the big C/T medians, the min–max range line and the kept/excluded count.
2. The DISTANCE GAPS block, unchanged except that its heading names the basis and its verdicts come from the basis-matched medians.

Nothing in either is rewritten — they are moved and re-headed.

## 10. Club colour ramp

A new `BagRamp` in the bag package:

```kotlin
object BagRamp {
    /** One distinct hue per club, by bag order. */
    fun clubColor(index: Int): Color = HUES[index.mod(HUES.size)]
    val hueCount: Int
}
```

- The first six hues are exactly `GolfColors.Comparison.A..F` (teal, rose, yellow, indigo, magenta, green), so a short bag looks like every other comparison view in the app.
- Seven further hues follow, chosen to stay distinguishable from each other and from `GolfColors.Base`; **the designer owns the final values** and must check them rendered on the dark base. Proposed: orange `#E4572E`, sky `#5B9DF9`, olive `#8FBF4A`, violet `#C77DFF`, aqua `#57D6C4`, apricot `#FFB86B`, slate `#B0BEC5`.
- Assignment is **positional by bag order**, exactly like `FittingColors.clubColor` — the same rule in all views on the page, so the legend, the arcs, the labels and the rings always agree.
- Known collision, accepted: hue 1 is `Comparison.A`, which is also `GolfColors.Teal` — the "good" verdict colour. The ramp appears in the chart and the top-down legend; the verdict colours appear in the summary strip and the panel's gap block. They are separated by context and region, and the alternative (skipping teal) breaks the first-six convention.

## 11. Edge cases

| Case | Behaviour |
|---|---|
| Club with 0 kept shots | No arc, no label, no dots, no ring. Appears in the panel as today's "no data" row. |
| Club with 1–2 kept shots | Arc and label present (a median of 2 is fine); dots present; **no ring**. |
| All shots of a club excluded | Same as 0 kept shots. |
| A club's median launch metrics out of `LaunchConditions` range | Club skipped in the new views (no throw); still present in the panel. |
| Session with a single club | Chart draws one arc; summary strip and gap block show "no gaps" as today. |
| Degenerate trace (`traceEndDistanceM <= 0`) | Club skipped — no division by zero in the scale-to-fit factor. |
| More than 13 clubs | Ramp cycles via `index.mod(hueCount)`; documented, not expected (the bag excludes putters and TEST clubs). |

## 12. Files and architecture

Pure logic (JVM-testable, no Compose, no Android framework):

- `app/.../bag/BagResultsView.kt` — `BagResultsView`, `DistanceBasis` enums (or fold into an existing bag file if that reads better).
- `app/.../bag/BagTrace.kt` — median launch conditions per club → `LaunchConditions`; the per-shot top-down point extraction; the scale-to-fit factor. Returns plain data classes of doubles.
- `app/.../bag/BagSideGeom.kt` — axis fit (distance → x), height normalisation, label row assignment, tick list. Pure functions over doubles, in the spirit of `BoxPlotGeom`.
- `app/.../bag/BagSummary.kt` — the summary strip's statistics from a `List<GapEntry>`.
- `app/.../bag/BagRamp.kt` — the colour ramp.

Compose (canvas + layout only, plain state holders — no ViewModels, no Navigation-Compose):

- `app/.../bag/BagSideView.kt` — the arc canvas + the positioned label composables.
- `app/.../bag/BagTopDownView.kt` — the dots/rings canvas + the legend `FlowRow`.
- `app/.../bag/BagResultsSummary.kt` — the strip.
- `app/.../bag/BagDistPanel.kt` — the collapsible wrapper around the existing matrix + gap block.
- `app/.../bag/BagMappingResult.kt` — page composition, the two toggles, the panel state, wiring into the existing drill-down.

Reuse, do not reinvent: `BallFlightEngine`, `RangeRollout.samples`, `DispersionEllipse` + `computeTopDownFitH`, `BoxPlotGeom.axis/niceTicks/x`, `BagMappingStats.median/distribution`, `GapAnalysis`, `BagMappingFormats`, the `GolfColors`/`GolfTypography`/`GolfSpacing` tokens, and the fitting top-down's draw recipe.

Expensive work (simulations) is computed once per `(session, basis)` inside `remember` keyed on the shot list identity, never per recomposition or per frame.

## 13. Testing

Pure JVM unit tests, following the bag suite's existing style:

- `BagTraceTest` — medians per club over kept shots only; excluded shots ignored; out-of-range medians yield "skip"; the scale-to-fit factor maps the trace end exactly onto the median distance; TOTAL appends the ground phase and CARRY does not.
- `BagSideGeomTest` — axis fit maps 0 → origin and the max → the right edge; ticks inside the axis and monotonic; the tallest apex lands at 85 % of the plot height; label rows alternate and follow ascending landing order.
- `BagTopDownPointsTest` — per-shot lateral/distance extraction for both bases; ring is null below 3 points and present above; the window crop snaps to 25 m with the 50 m floor.
- `BagSummaryTest` — avg/closest/farthest and the counts, driver excluded, identical verdicts to the gap block for the same input.
- `BagRampTest` — 13 distinct hues; the first six are `Comparison.A..F`; cycling beyond 13.

Existing suites must stay green untouched: `GapAnalysisTest`, `BoxPlotGeomTest`, `BagMappingFormatsTest`, `BagMappingCollectorTest`, `BagPlanOrderTest`, `ClubQualityGateTest`.

## 14. Verification plan

1. `:app:testDebugUnitTest --tests "com.hpsmiles.golfsim.bag.*"` and `:app:assembleDebug` green.
2. Install on the tablet (`:app:installDebug`), relaunch, navigate to BAG, confirm the results page.
3. `uiautomator dump` the page to confirm the structure and the exact text of the strip, the legend and the panel header.
4. Capture screenshots for both views and both bases (four combinations) and have the observer review them: arc shapes differ by club; labels stagger and do not collide; the scale is linear and shared; the legend has no shot counts; rings sit on their dots; nothing clips; the collapsed panel starts closed and opens correctly.
5. Confirm the panel's matrix renders identically to the pre-change page (the move is a relocation, not a rewrite), apart from the gap block's new basis suffix in its heading.

## 15. Risks

- **Arc readability.** Real trajectories on a 260 m axis need the 85 % height normalisation to be legible at all; if it still reads poorly on the tablet, the knob is that fraction (and the "height, not to scale" caption is the honesty mechanism).
- **13 hues.** Genuinely hard to keep distinguishable; the designer must verify rendered separation and may swap hues.
- **Label collision.** Accepted limitation above; the stagger is not a solver.
- **TOTAL bands.** The band fractions are carry-derived; see §5.
- **Two distance truths.** The panel shows both lanes while the rest of the page follows the toggle; the basis suffix in the gap heading and the strip's labelling are the mitigation.
