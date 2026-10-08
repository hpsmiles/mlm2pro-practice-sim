# M7 Club Fitting — UI Design Notes (Task 6)

Date: 2026-10-08 · Design pass for `docs/superpowers/specs/2026-10-08-m7-club-fitting-design.md` §3–§5.
Audience: implementers. Everything here is a decision, not a suggestion. Do not redesign;
build what is written. All colours/styles/spacing reference `core/designsystem`
(`GolfColors`, `GolfTypography`, `GolfSpacing`, `SectionCard`, `MetricChip`, `MetricRow`,
`StatusStrip`) and the proven idioms in `RangeScreen.kt` / `ClubPickerOverlay.kt`.

Fixed seams: the `FittingScreen` signature (COMPARE / RESULTS / HISTORY views,
TABLE | TOP-DOWN and CARRY | TOTAL modes, `onSetExcluded`, `onComplete`) is already
implemented — style within it. No new screens, no new flows.

---

## 0. Shared decisions (apply to all three views)

### 0.1 Club colour assignment table

Clubs are assigned colours **by first appearance in the session's shots** (spec §5).
Assignment is positional: the Nth distinct club snapshot gets row N of this table.
The table is fixed forever — never hash names to hues.

Revised 2026-10-08 after device testing (§4.5): reordered for consecutive hue
separation; blue nudged to indigo. Letters A–F stay stable, values move with the
order — `GolfColors.Comparison` itself is re-ordered/re-valued so letter = club
position; `FittingColors.CLUB` keeps its plain `A..F` listing.

| # | Name | Hex | Rationale |
|---|------|-----|-----------|
| 1 | `Comparison.A` (teal, unchanged) | `0xFF3FA7A0` | First club = the reference; design-system teal. Hue ≈ 174° |
| 2 | `Comparison.B` (rose) | `0xFFE85C7A` | Hue ≈ 347° — 173° from teal: max separation for the universal 2-club case (was teal+blue at 43°, the on-device complaint) |
| 3 | `Comparison.C` (yellow) | `0xFFF0D64A` | Hue ≈ 54° — 179° from teal, 67° from rose. Lemon-yellow is a device-proven category colour, clearly lighter/greener than reserved Amber `0xFFF2A93B`; amber itself stays reserved |
| 4 | `Comparison.D` (indigo — hex CHANGED from blue `0xFF5B9DF9`) | `0xFF6E7BF2` | Hue ≈ 235° — lifts teal-vs-blue co-display from 43° to 61°; 179° from yellow, 64° from magenta. Same mid luminance as the rest |
| 5 | `Comparison.E` (magenta) | `0xFFD66FD8` | Hue ≈ 299° — 64° from indigo but clearly lighter/purpler vs its blue-dominant neighbour |
| 6 | `Comparison.F` (green) | `0xFF6FD86F` | Hue ≈ 120° — 179° from magenta. Greener than `BleArmedGreen 0xFF7BC96F` (8 dp BLE status dot only, never a category fill) |

Usage-order consecutive separations: 173° / 67° / 179° / 64° / 179°. Weakest
co-displaying pair overall is magenta–rose (48°), which only co-occurs at 5+
clubs. Orange was rejected outright: reserved Amber marks the live moment in the
SAME panel as the comparison rows, so an orange category colour is the one hue
guaranteed to collide on-screen. Rose vs `AlertRed` (BLE dot when disarmed, END
COMPARISON button) never share a card — different contexts, distinct casts.

All six sit in the same mid luminance / saturation band, so 6–10 dp dots and
2 dp ring strokes look uniform and stay legible on `Base 0xFF0E1114` / `Panel 0xFF0B0E11`.
Fixer notes: `GolfColorsTest` pins the A–D hexes — update the B/C/D expectations;
refresh the KDoc in `GolfColors.Comparison` (drop the "≥ 90° separation" line, cite
this table) and in `FittingColors` (rose is now club 2, indigo club 4).

A 7th+ club: reuse slot colours cyclically (A again for club 7) — acceptable because
6-club comparisons are the realistic ceiling; do not invent more hues.

### 0.2 Component states (global vocabulary)

- **Empty**: `TextMuted 0xFF5C6873` `GolfTypography.BodySmall` placeholder text inside the relevant `SectionCard`. Never an icon, never a spinner.
- **Disabled control**: 1 dp `Line 0xFF28303A` border, `TextMuted` label, `clickable(enabled = false)`.
- **Selected/active chip**: 1 dp `Teal 0xFF3FA7A0` border + `Teal` text (the `OverlayChip` idiom from `RangeScreen.kt:55–65`).
- **Unselected chip**: 1 dp `Line` border + `TextSecondary 0xFF8FA0AC` text.
- **Warning (low sample)**: `Amber 0xFFF2A93B` — count badges only (see 0.3). Nothing else in FIT uses amber; amber never marks a club.
- **Excluded**: `TextMuted` text + strikethrough not used — dimming only (`alpha 0.45f` on the row content). Grey, never red.

### 0.3 Count badge (shared component)

Small pill, height 18 dp, padding h = `GolfSpacing.Sm`, corner 50:
- Normal: background `Panel 0xFF0B0E11`, 1 dp `Line` border, text `GolfTypography.Status`, colour `TextSecondary`, format `"%d"`.
- Low sample (< 5 kept shots): same pill, border `Amber`, text `Amber` (ClubQualityGate pattern).
Placed after club name everywhere a club row appears (comparison card, table CLUB column, legend chips, drill-down header).

### 0.4 Chip font

All chip/segmented labels use `ChipFont` (`GolfTypography.Status.copy(fontSize = 15.sp)`),
the same idiom as VIEW / speed-multiplier chips. Uppercase strings at the call site.

---

## 1. COMPARE view (live)

Screen = `Row` on `GolfColors.Base`: canvas (`weight(1f)`) + 210 dp right panel.
StatusStrip (armed dot + info) runs along the bottom as on RANGE — unchanged.

### 1.1 Canvas region

- Full-size `RangeLiveView` with `liveShots` — tracers/follow-cam exactly as RANGE/BAG. **No club colours, no ovals, no rings while hitting** (spec §3). The top-down colouring lives only in RESULTS.
- Overlays on the canvas (top-right column, `GolfSpacing.Sm` padding, spaced `GolfSpacing.Xs`), mirroring RANGE exactly:
  1. `ACTIVE CLUB` filled teal trigger — the exact `ActiveClubButton` idiom (170×58 dp solid `Teal`, `GolfTypography.Hero` @ 26 sp in `Panel` colour, "ACTIVE CLUB" `MetricLabel` in `TextMuted` beneath). Tap → `ClubPickerOverlay` (FIT variant, §1.3).
  2. `VIEW: COMPARE` chip — 1 dp `Line` border pill, `ChipFont`, `TextSecondary`. Present for continuity but tap does nothing in v1 COMPARE (live view is the only canvas); render it disabled-styled so the idiom reads consistently. *(If cheaper: omit it — omission is approved.)*
- Top-center: speed-multiplier chips row — copy RANGE verbatim (1x/1.5x/2x/4x, selected = `Teal` border+text).
- Bottom-left: reuse the no-read pill behaviour if the live pipeline surfaces misreads; otherwise nothing.

### 1.2 Right panel (210 dp, `Panel` background, `GolfSpacing.Md` padding, vertical scroll, `GolfSpacing.Md` between cards)

Top → bottom:

1. **`SectionCard("LAST SHOT")`** — identical content to RANGE's card (carry/total/ball/club/smash/spin/launch/dir/axis `MetricChip`s, ball chip amber-accented). Empty state: "Fire a shot" `BodySmall` `TextMuted`.
2. **`SectionCard("COMPARISON")`** — the M7 card (replaces RANGE's SESSION card position):
   - Header row inside the card: title "COMPARISON" (from SectionCard) and, on the same visual line, the **carry/total toggle**: two chips `CARRY` | `TOTAL` side by side, `ChipFont`, selected = teal border+text, unselected = `Line` border + `TextSecondary`. Bound to `distanceMode`.
   - Then one row per club, `GolfSpacing.Sm` vertical spacing. Each row, height ≈ 36 dp:
     - 10 dp colour dot (circle, club colour, from §0.1 table)
     - club name — `GolfTypography.Body`, `TextPrimary`, weight(1f), ellipsize
     - count badge (§0.3; amber when < 5 kept)
     - average — `GolfTypography.MetricValue` (17 sp bold tnum), `TextPrimary`, right-aligned, unit " M" in `GolfTypography.Unit` `TextMuted`. Value = carry avg or total avg per the toggle.
   - Row background: `Panel` with corner `GolfSpacing.CornerCard / 2` (MetricRow idiom) but **no left accent bar** — the colour dot replaces it (the accent bar is teal/amber-only by kit contract).
   - **Empty state** (no `activeSession`, zero shots): single line "Hit shots to start comparing" — `BodySmall`, `TextMuted`. Card still renders so layout doesn't jump.
   - The active (currently selected) club's row gets a 1 dp `Teal` border on its row background; other rows no border. Non-active rows are not dimmed.
3. **VIEW RESULTS button** — full panel width, height 44 dp, 1 dp `Teal` border pill (ghost style — the only filled control stays ACTIVE CLUB), `ChipFont`, `Teal` text, label `VIEW RESULTS`. Disabled (border `Line`, text `TextMuted`) while `activeSession == null`. Placed between the COMPARISON card and the HISTORY chip.
4. **HISTORY chip** — full-width ghost chip, 1 dp `Line` border, `ChipFont`, `TextSecondary`, label `HISTORY`. Tap → HISTORY view. Show a small `TextMuted` count suffix when history is non-empty (`HISTORY · 3`).

### 1.3 Club picker overlay (FIT variant)

Reuse `ClubPickerOverlay` mechanics exactly (scrim `0x88000000`, centered `Card` surface, tap-outside dismiss, 84×72 tiles, teal border = active, amber border + TEST tag = temp, auto-scroll on add). FIT-only changes:

- The add tile label reads **`＋ ADD COMPARISON`** instead of `＋ ADD` (same tile size; if "ADD COMPARISON" wraps, shrink to `GolfTypography.Status` @ 13 sp — still one line preferred; the tile may grow to 84×84 to fit two lines).
- Tapping it opens `AddClubForm` with the **TEST checkbox pre-checked** (form otherwise unchanged).
- On submit, the new temp club becomes the active club (`ActiveClubStore`) and the overlay dismisses. Give the freshly added tile a one-shot highlight: 2 dp `Amber` border for ~1.5 s, then it settles to the normal amber temp border. (Simplest implementation: flag on the returned club id + `LaunchedEffect` delay.)
- Everything else (real-club tiles, "—" tile, TEST section header in amber `MetricLabel`) is byte-identical to the RANGE picker.

---

## 2. RESULTS view

Full-screen on `GolfColors.Base` (canvas + panel layout is suspended here — bag RESULT precedent). Column structure:

### 2.1 Header row (full width, height ≈ 52 dp, `GolfSpacing.Md` horizontal padding, `GolfSpacing.Sm` between items)

Left → right:

1. **BACK chip** — ghost pill, 1 dp `Line` border, `ChipFont`, `TextSecondary`, label `← BACK`. Tap → COMPARE (keeps collecting).
2. **Segmented switch `TABLE | TOP-DOWN`** — a single pill container (background `Panel`, 1 dp `Line` border, corner 50) holding two segments, each padded h `GolfSpacing.Md` v `GolfSpacing.Xs`, `ChipFont`. Selected segment: background `Teal40 0x663FA7A0`, text `Teal`. Unselected: transparent background, text `TextSecondary`. Bound to `resultsMode`.
3. Spacer (weight 1f).
4. **Shot-context line** — `GolfTypography.Status`, `TextMuted`, e.g. `24 shots · 3 excluded` (from `sessionShots`). Ellipsizes.
5. **END COMPARISON button** — solid `AlertRed`-bordered ghost: 1 dp `AlertRed 0xFFE86A5E` border pill, `ChipFont`, `AlertRed` text, label `END COMPARISON`. **No confirmation dialog** (spec §6): one tap calls `onComplete`, the tab returns to empty COMPARE. Red is used here because the action is terminal for the session; it is the only red control in FIT.

When viewing a history session (`viewedSessionId != null`): BACK returns to HISTORY, the segmented switch stays, and **END COMPARISON is not rendered** (read-only session); the context line reads the viewed session's date instead of live counts.

### 2.2 TABLE mode (`resultsMode == TABLE`)

One `SectionCard`-framed region filling the screen below the header (padding `GolfSpacing.Md` around; card content is a `Row`):

- **Frozen column** (width 150 dp): header cell `CLUB` (`GolfTypography.MetricLabel`, `TextSecondary`, uppercase). One row per club, row height 40 dp:
  - 10 dp colour dot, club name (`GolfTypography.Body`, `TextPrimary`), count badge (§0.3).
  - Column background `Panel`; the scrollable region to its right shares row heights so rows align.
  - Tap a club row → drill-down (§2.2.2). The row that is the **delta baseline** (when 3+ clubs) gets a 1 dp `Teal` border; tap a second time to re-baseline (cycle handled by controller state — visually just move the border).
- **Metric columns** (horizontal scroll, `Row` of fixed-width columns):
  `n · CHS · BALL SPEED · SMASH · CARRY · TOTAL · LAUNCH · DIR · SPIN · SPIN AXIS · OFFLINE`
  - Column widths: `n` 40 dp; SMASH 64 dp; all others 88 dp. Header cells: `GolfTypography.MetricLabel` `TextSecondary`, uppercase, two lines allowed (e.g. `SPIN\nAXIS`, `BALL\nSPEED`).
  - Cell values: `GolfTypography.MetricValue` @ 15 sp (slightly condensed from 17 sp so ±σ fits), `TextPrimary`, tnum. Formats: `%.0f` m for carry/total, `%.1f` for CHS/ball speed (m/s), `%.2f` smash, `%.1f°` launch/dir/axis, `%.0f` rpm spin, `%.0f / %.0f` offline avg/worst.
  - CARRY and TOTAL cells render `avg ± σ` (σ in `GolfTypography.Unit`, `TextSecondary`, on a second line under the avg — keeps the column narrow and the avg scannable). All other columns single value.
  - CARRY|TOTAL emphasis: honour `distanceMode` — the mode's column header and values get `TextPrimary` + the 1 dp `Teal` top border on the column; the other distance column renders dimmer (`TextSecondary`). (Top-down ignores this toggle per spec §5.)
  - Row separators: 1 dp `Line` horizontal lines across both frozen and scrollable regions (draw in each region so they align).
- **Delta rows** (spec §4): directly under each club row inside the metric region —
  - Exactly 2 clubs: one `B−A` line under the pair. Label cell (frozen column): `Δ B−A` in `GolfTypography.MetricLabel`, `TextSecondary`.
  - 3+ clubs: per club row a `Δ vs baseline` line, labelled `Δ <NAME>`.
  - Delta cell format: signed value in the metric's unit, `GolfTypography.MetricValue` @ 13 sp.
  - **Noise dimming**: when |delta| ≤ the metric's noise band (carry/total ±2 m, spin ±150 rpm, ball speed ±0.7 m/s, CHS ±0.5 m/s, smash ±0.010, launch/dir ±0.5°, spin axis ±2°, offline ±1 m), render the cell text in `TextMuted 0xFF5C6873`. Outside the band: `Teal` for favourable-from-baseline-perspective is **not** attempted (favourability depends on the metric) — use `TextPrimary` for all significant deltas. Keep it strictly: dim = noise, bright = signal.
- **Table empty state**: `activeSession == null` → centered `BodySmall` `TextMuted`: "No comparison yet — hit shots in COMPARE". Header row still renders.

#### 2.2.1 Expandable drill-down

Tap a club row → an expanding region below that club's rows (within the same card, full card width — it is not part of the horizontal scroll; the frozen column and metric region both end and the drill-down spans underneath):

- Header line: colour dot + name (`GolfTypography.ScreenTitle`, `TextPrimary`) + count badge + `· N excluded` (`GolfTypography.Status`, `TextMuted`) + two actions on the right:
  - **`EXCLUDE LIKELY MISREADS`** — ghost pill, 1 dp `Amber` border, `Amber` text, `ChipFont`. One tap applies the duff filter via `onSetExcluded`. Disabled-styled (border/text `Line`/`TextMuted`) when the club has < 3 shots. No dialog; the drill-down rows update in place.
  - **`CLOSE`** — ghost pill, 1 dp `Line` border, `TextSecondary`.
- Shot rows (one per shot, most recent last, height 36 dp, `GolfSpacing.Xs` gaps):
  - Left: keep/exclude **tick control** — 22 dp square, corner `GolfSpacing.Sm`: kept = 1 dp `Line` border with a teal ✓; excluded = 1 dp `Amber` border with an amber ✕. Tap toggles via `onSetExcluded([id], !current)` — one control, no separate INCLUDE button (the tick is the include toggle).
  - Then per-shot values in `GolfTypography.BodySmall` tnum: `carry · total · ball · CHS · smash · spin · dir` (labels shown once in a header line above the rows, `MetricLabel` `TextMuted`). Kept rows: `TextPrimary`. Excluded rows: whole row `alpha 0.45f`, values `TextSecondary`.
  - `club_head_speed > 0` duff-sign is not styled specially — exclusion state is the single source of truth and the tick shows it.
- Only one club's drill-down open at a time (opening another closes the first).

### 2.3 TOP-DOWN mode (`resultsMode == TOP_DOWN`)

- **Legend chips**: horizontal row directly under the header (padding `GolfSpacing.Md`, `GolfSpacing.Sm` gaps). One chip per club: 10 dp colour dot + name (`ChipFont`, `TextPrimary`) + count badge. Chips are not tappable in v1 (no per-club solo/hide — out of scope). If > 4 clubs, wrap to a second row.
- **Canvas**: `FittingTopDown` filling the remaining space on `GolfColors.Base`:
  - Same grid + `RangeDecorations` boards as `TopDownCanvas`.
  - Kept shots: 6 dp dots in the club's colour (§0.1). Excluded shots not drawn.
  - **Dispersion ring** (revised 2026-10-08 device feedback — nested 1σ+2σ read on-device as "more rings than clubs"; original ask was one ring per club): exactly **ONE ring per club at 2σ**, stroked in the club's colour at **55 % alpha** (`Teal55` pattern), 2 dp stroke, no fill (fill would occlude other clubs' dots). 2σ captures ≈ 86 % of a bivariate normal — the "where do my shots land" region, which is what a dispersion ring is for; the table already reports ±σ numerically, so the ring's job is full spread, not core consistency. Requires ≥ 3 kept shots — otherwise no ring for that club (its dots still render). Dots unchanged (6 dp, club colour).
  - y axis = rest position = **total**; no carry/total toggle appears anywhere in this mode.
  - Empty state: centered "No kept shots yet" `BodySmall` `TextMuted` over the bare grid.

---

## 3. HISTORY view

Simple list on `GolfColors.Base`, replacing the whole RESULTS/TABLE area (the FIT tab's rail and StatusStrip remain):

- Header row (same geometry as §2.1): BACK chip (`← BACK` → COMPARE) + title `COMPARISON HISTORY` (`GolfTypography.ScreenTitle`, `TextPrimary`). No END button, no segmented switch.
- Body: vertical list of completed sessions (`history`, newest first), padding `GolfSpacing.Md`, `GolfSpacing.Md` between rows. Each row is a `SectionCard`-styled container (background `Card 0xFF151A1F`, corner `GolfSpacing.CornerCard`, padding `GolfSpacing.Md`), height ≈ 64 dp:
  - Left: date/time — `GolfTypography.Body`, `TextPrimary` (e.g. `2026-10-08 14:32`).
  - Second line: club names joined ` · ` — `GolfTypography.BodySmall`, `TextSecondary`, each prefixed by a 6 dp colour dot in that session's assigned colours (first-appearance assignment recomputed for that session's shots).
  - Right: shot count `N shots` (`GolfTypography.Status`, `TextMuted`) + **`VIEW →`** ghost chip (1 dp `Line` border, `ChipFont`, `TextSecondary`). Whole row is tappable and does the same thing as VIEW.
- Empty state: centered `BodySmall` `TextMuted`: "No completed comparisons yet". 

Viewing a history item opens the read-only RESULTS described in §2.1 (viewed-session branch).

---

## 4. Spec tensions found (for the orchestrator)

1. **Yellow D vs reserved Amber.** The design system doc says amber is reserved and "must never be used as a category colour", yet `Comparison.D = 0xFFF0D64A` is an approved M7 category yellow. These are different hexes and read differently side-by-side, but implementers should not "helpfully" swap D for `GolfColors.Amber`. Flagged so nobody re-litigates it during build.
2. **VIEW chip on COMPARE canvas** has no second canvas to toggle to in v1 (colour top-down lives in RESULTS only). I specified it as disabled or omittable — pick omission if it feels dead on device.
3. **Delta colouring.** Spec says dim-below-noise only; a natural temptation is green/red deltas. I explicitly fixed deltas to `TextMuted` (noise) vs `TextPrimary` (signal) to avoid implying good/bad, since favourable direction differs per metric.
4. **Drill-down inside a horizontally-scrolling table** is the one layout that needs care: the drill-down must span the full card width, not live inside the scrollable row — implementers often nest it inside the scrolled Row by accident.
5. **Palette + ring revision (2026-10-08 device feedback).** User findings: nested 1σ+2σ rings read as "more rings than clubs" (→ one 2σ ring per club, §2.3) and teal+blue as clubs 1–2 were too close (→ palette reordered + blue → indigo, §0.1). One palette (`FittingColors.clubColor`) drives all four views, so the fixer changes values/order once in `GolfColors.Comparison` and every view follows; `GolfColorsTest` B/C/D expectations must move with the hexes.

## Addendum (2026-10-08, device feedback — post-Task 11)

These decisions supersede/conflict-resolve the sections above where they differ. Both were user-requested after on-device verification; the designer (des-1, notes commit 0ea32c7) owns §0.1/§2.3 changes; the orchestrator owns this addendum.

### §2.2 AREA column (user ruling 2026-10-08, 2nd revision — ellipse inscribed in the buffered bbox)

- Metric column **AREA** after OFFLINE (88 dp, unit-less header like all columns).
- User rulings (same day, in sequence): "I want the ring to be a small 5% buffered ring, and the area of this to be the number on the table" (buffer: **5 % of span** per side), then on-device: "The bounding area should still be a elipse, and have a 5% buffer. The area of the ring is the number the user should see as an area metric."
- Final definition: the **ellipse inscribed in the 5 %-buffered bbox** of the club's kept shots — centre = box centre, semi-axes = buffered half-extents; AREA = π/4 × buffered width × depth (m²), rendered unit-in-cell (e.g. `412 m²`). This is the Golf Digest equipment-testing dispersion convention (lib-1 survey). A kept shot at a bbox corner may lie slightly outside the inscribed ellipse — inherent to the convention (box defines extents).
- `-` when the club has fewer than 3 kept shots (same guard as the ring); **no Δ row** (unchanged).
- Implementation: `DispersionBox` in `FittingTopDownFit.kt` (`BBOX_BUFFER_FRACTION = 0.05`, `ellipseAreaM2()`); the 2σ `DispersionOval` was removed from the app (git-recoverable for M9).

### §2.3 top-down ring + auto-fit (user ruling 2026-10-08 — supersedes the 2σ ring)

- The ring per club is the **ellipse inscribed in the buffered bounding box** of the kept shots — the same `DispersionBox` geometry the AREA column reports, drawn as an oval: 2 dp stroke, club colour @ 0.55 α, no fill; ≥3 kept shots; a zero-span axis renders as a line (matches the data).
- Auto-fit bounds use the buffered box extents (min/max side/total grown 5% per axis) across all clubs that have a box; the empty fallback is unchanged (exact previous full-range mapping, no visual jump).
- Isotropic scale: px/m = min(yFit, xFit); y-window bottom-anchored (shots near the bottom, same as the full-range view); x centred. Padding: max(10 m, 6 % of the content span); minimum content span 30 m; lateral pad 48 px.
- Grid/boards/mat remain world-anchored and simply fall outside the window; the lateral gridlines still span the full canvas.
