# Break-the-Pane pane retune — design amendment

Date: 2026-09-30
Status: Approved (design dialogue 2026-09-30)
Parent spec: `2026-09-30-games-polish-highscore-pane-misread-design.md` (§4 recalibration)

## Problem (measured on the user's 19-shot 8i capture @ 140 m)

The pane grid is centred on the stock-8i crossing (`zRef`) at plane `0.25 × target`, with
`cellH = 0.02 × target` (2.80 m) and `cellW = 0.04 × target` (5.60 m). Against real dispersion:

- Lateral |x| median ≈ 0.5 m, max ≈ 2.9 m — but the side columns only begin at `0.5 × cellW` = 2.8 m
  and reach out to 8.4 m, so side columns are effectively unhittable; ~17/19 shots fall in the middle column.
- The top row begins `0.5 × cellH` = 1.4 m above the stock crossing, so only a ~29° launch reaches it.

Moving the plane farther out was evaluated and rejected: it widens the vertical fan and makes the
top/bottom rows overshoot at small cell sizes. The fix is therefore to keep the pane distance and
shrink the cells.

## Decision

Change two `PaneGeom` fractions; keep the plane distance and the vertical centre.

| Parameter | Before | After |
|---|---|---|
| `cellHM` | `0.02 × target` | **`0.016 × target`** |
| `cellWM` | `0.04 × target` | **`0.020 × target`** |
| plane fraction | `0.25 × target` | unchanged |
| vertical centre | stock reference crossing at the plane | unchanged |

Cell area drops ~60% (6.3 m² vs 15.7 m² at 140 m).

## Resulting geometry

At 140 m (`zRef` = 12.50 m, plane 35.0 m, cellH 2.24 m, cellW 2.80 m):
- rows — bottom `9.14–11.38`, middle `11.38–13.62`, top `13.62–15.86` m
- columns — left `−4.20…−1.40`, middle `±1.40`, right `+1.40…+4.20` m (pane spans ±4.20 m)

At 125 m (`zRef` = 10.78 m, plane 31.25 m, cellH 2.00 m, cellW 2.50 m): columns span ±3.75 m.

## Reachability (the intended mechanic)

The **starting line breaks the pane; the curve brings the ball back to the green.**

- Stock 8i (bs≈44, la≈20°, spin 6500) → **middle**.
- High 8i (**la ≈ 24.3°**) → **top row**; flat 8i (**la ≈ 17.9°**) → **bottom row**.
- Off-line start line (**ld ≈ ±2.3° to enter, ≈ ±4.6° to centre** a side column, sa = 0) → **side column**;
  a modest curve then returns the ball toward the green. No extreme spin axis required.

## Tests

JVM reachability tests (in `app`, using the real `BallFlightEngine`) pin the above so the tuning
cannot silently regress; existing `PaneGeomTest` cell-size/comment expectations are updated.

## Non-goals

No change to the plane distance, vertical centre, green sizing/difficulty, scoring, or the break rule.
