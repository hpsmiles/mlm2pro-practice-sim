# Design: M8 post-ship refinements (results redesign + top-down dispersion)

Date: 2026-10-11
Status: approved (mockups reviewed by the user; decisions locked below)
Builds on: `docs/superpowers/specs/2026-10-10-m8-custom-combines-design.md`

## Context

M8 shipped (catalog combines with strokes-gained handicap scoring). The first on-device demo run surfaced
three bugs — all fixed: the scoring root-finder returned a better-than-scratch estimate for shots worse than
every band (negative `[10,18]` slope), the canvas pin/green advanced mid-flight, and demo runs were tagged LIVE.
Two model issues were also raised and fixed: the driver station now rates drives against per-band typical
drive distances (tour 272.5 / scratch 239.6 / 10 HC 208.9 / 18 HC 193.9 m, Arccos 2026), and the fairway is a
constant 28 m wide.

This increment covers the two design requests from that run.

## 1. Session benchmark lock

- On completion, a session locks to the **benchmark band nearest its overall handicap estimate** — the
  nearest anchor among `TOUR (-5)`, `SCRATCH (0)`, `HCP10 (10)`, `HCP18 (18)`. The demo run (overall ≈ 22)
  locks to `HCP18`.
- Non-finite or out-of-range estimates clamp before the comparison (`+Infinity`/NaN → `HCP18`; below −5 →
  `TOUR`), so a degenerate run can never lock to the wrong end.
- Every per-shot SG shown in the results ledger is computed against the locked band:
  - approach shots: `strokesGained(band, startLie, startM, endLie, endM)`
  - driver shots: `drivingStrokesGained(band, restLie, restDistanceM)`
- The ledger header states the benchmark (e.g. "Scored vs 18 HC").
- Station estimates, the per-station diverging bars and the VS BANDS strip keep the existing 4-band solve —
  only the per-shot ledger column changes.

## 2. Results screen (redesign)

One vertical column, replacing the current per-station cards:

1. **Hero**: overall handicap centred and large, with the ± uncertainty in a smaller font beneath it.
2. **Per-target row**: three items side by side (100 m / 150 m / driver), each with its station estimate and
   a sub-line — "avg to pin" for the approach stations, "avg total" for the driver.
3. **VS BANDS strip**: the four diverging bars (tour / scratch / 10 HC / 18 HC) as today.
4. **Record banner** (non-read-only only).
5. **SHOTS**: a collapsible section, **collapsed by default**, whose header shows the shot count and the locked
   benchmark; expanding reveals the ledger table (#, target, result, lie, SG vs the locked band).
6. **BACK** / **PLAY AGAIN** buttons.

The read-only history drill reuses the same view; its behaviour (no PLAY AGAIN, no record card, no confetti)
is unchanged.

## 3. Top-down dispersion view

- Reached from the results screen via a **TOP-DOWN** toggle.
- Plan view, horizontal, **true-scale** — one pixels-per-metre for both axes, so greens render as real
  circles and rings keep their shape. The distance window is cropped to the session's actual content
  (greens, finishes, ring extents), snapped outward to 25 m and clamped to the fairway; the full 0–330 m
  corridor appears only when content demands it, and the tee marker shows only when inside the window.
- The fairway corridor at its constant 28 m width wears the range treatment (mown stripes on the rough
  ground, framed card pane) — same palette as the fitting top-down. Distance gridlines + labels every
  50 m inside the window; ±10/±20 m dashed lateral guides with labels give the lateral scale. The 100 m
  and 150 m greens are drawn as circles with pins.
- Three colour-coded shot groups (100 m, 150 m, driver): finish dots, a centroid marker, and the
  minimum-enclosing dispersion ring drawn dashed (M7 5%-buffer ellipse, containment-verified). Approach
  groups are plotted relative to their own pin; the driver group is plotted in absolute hole coordinates
  (its finishes along the corridor).
- Side panel: per-group stats — avg to pin (approaches) / avg total (driver), long–short spread, left–right
  spread.

## 4. Storage (required by section 3)

`combine_shots` currently stores only `restLie` and `restDistanceM`, so lateral/longitudinal positions are not
recoverable. Add nullable `restXM` / `restYM` (the rest position in hole metres, from the ball-flight model's
`ShotResult.restX` / `restY`), populated when a shot is appended. Room v7: `MIGRATION_6_7`, schema JSON, and a
migration test, per repo convention. Sessions recorded before this change have no coordinates and the top-down
view shows "no dispersion data for this session"; old rows are **not** backfilled.

## Out of scope

Backfilling old sessions; per-station benchmark locks; 2σ rings; vertical top-down orientation;
tee-to-finish driver lines; a separate top-down tab in HISTORY.
