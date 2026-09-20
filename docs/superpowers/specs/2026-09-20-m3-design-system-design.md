# M3 Design System — Design Spec

**Status:** approved design (all four sections user-approved in brainstorming 2026-09-20)
**Branch:** `m3-design-system` (off `master@a9defd9`)

---

## 1. Goal & exit criteria

Build the Compose design system for the whole app: a `:core:designsystem` Android library
module carrying the Performance Dark theme, the full token set (color / typography /
spacing / motion), and the small component kit shared by every later screen; plus a
showcase screen in `:app` that renders every primitive for on-tablet verification.

**Exit criteria (ROADMAP M3, concretized):**
1. Mockups for the 3 signature screens signed off (done during brainstorming — range view,
   carry matrix, A/B comparison).
2. `:core:designsystem` compiles with theme + primitives; CI green.
3. Showcase screen installs on the Lenovo tablet and renders the design as designed
   (dark palette, chips/rows/rail/strip readable at shed distance, amber reads as
   "live moment") — user-verified, M0-style install gate.

## 2. Background & sources

All visual decisions were made against interactive HTML mockups in the brainstorming
companion (session 2003-1789875726, gitignored at `.superpowers/brainstorm/`).

Approved mockups (files under `.superpowers/brainstorm/2003-1789875726/content/`):
`style-direction.html` (direction C picked), `range-view.html` (top-down range + hole
variant), `player-pov.html` (POV cameras), `playback.html` (playback pacing),
`typography.html`, `carry-matrix-v2.html` (box plots), `ab-comparison-v3.html`
(4-slot comparison, widened palette).

This spec's token values (§5) are the **authoritative record** — mockups are session
artifacts, not repo files.

## 3. User decisions (all confirmed in brainstorming)

1. **Direction — "Performance Dark"**: dark neutral base, two-accent system.
   Rejected: Tour Data (single volt-green), Arcade Energy (neon glow).
2. **Cameras — both**: player-POV fixed camera (default) + follow-cam toggle /
   long-press replay. Top-down stays as the analytics screen.
3. **Playback pacing — real speed by default** with 2x / 4x speed toggle.
   Metrics, landing marker, and top-down dot appear **instantly** regardless of
   animation; animation is cinematic dressing. (ROADMAP M4 exit "<2s perceived" is
   satisfied via instant metrics, not compressed animation — M4 wording amendment
   noted for that milestone.)
4. **Hole mode — in token scope**: dark fairway tone, pin flag marker, distance-to-pin
   readout tokens join M3; the hole range mode itself is M4 UI.
5. **Units — independent toggles**, AU default: **metres for distance, mph for speed**.
   Display-layer concern (`UnitsPreference`: distance m|yd, speed mph|m/s); the engine
   stays SI internally; persistence lands with M5.
6. **Typography — system Roboto** with `tabular-nums` for all metric values; no
   bundled font asset. Rejected: condensed display, mono, hybrid.

Additional structural decision: **full design-system module** (theme + component kit +
showcase + `build-logic` convention plugin) over tokens-only or no-new-module options.

## 4. Architecture & module layout

```
:core:designsystem          NEW — Android library, namespace com.hpsmiles.golfsim.core.designsystem
├── GolfTheme.kt            Material3 darkColorScheme mapped from Performance Dark + Typography + Shapes
├── GolfColors.kt           token object (color families, §5)
├── GolfTypography.kt        Roboto styles, tabular-nums on metric styles
├── GolfSpacing.kt           spacing scale + shape/rail/strip dimensions
├── GolfMotion.kt            durations + easing curves
├── MetricChip.kt            telemetry chip (label/value/unit, accent variant)
├── MetricRow.kt             side-panel row (hot = amber accent)
├── NavRail.kt               slim landscape nav rail + NavRailButton
├── StatusStrip.kt           bottom status strip (BLE armed, session info)
└── SectionCard.kt           standard card container

build-logic/                 NEW — included build with `golf-android-library`
                            convention plugin (library + compose + java-17/kotlin-17
                            alignment in one place — ora-1 M0 note; :core:designsystem
                            is the 4th Gradle module)

:app                        MainActivity scaffold screen → design-system showcase
```

- `:app` gains `implementation(project(":core:designsystem"))`; nothing else changes
  in its build.
- **No new external dependencies** (Roboto is the platform default; no font assets).
- Comparison hues are exposed as tokens (`GolfColors.Comparison.a..d`); slot chips,
  box plots, dispersion overlays, POV canvas are **M4-M7 features built from these
  tokens** — not in M3 (YAGNI).

## 5. Token spec (authoritative values)

### Color

| Family | Tokens |
|---|---|
| Surfaces | base `#0E1114` · panel `#0B0E11` · card `#151A1F` · line/band `#28303A` · hole-fairway `#161B21` |
| Text | primary `#E7EBEE` · secondary `#8FA0AC` · muted/status `#5C6873` |
| Structural | teal `#3FA7A0` (+ 55% / 40% alpha variants for history dots, dashed clouds) |
| Live moment | amber `#F2A93B` (halo 20% alpha, glow 60%) — reserved, never for static data |
| Comparison | A teal `#3FA7A0` · B azure `#5B9DF9` · C magenta `#D66FD8` · D yellow `#F0D64A` |
| Semantic | BLE-armed green `#7BC96F` · alert red `#E86A5E` (misread ⚠, negative deltas) |

Material3 mapping: base→`background`/`surface`, card→`surfaceVariant`, line→`outline`,
teal→`primary`, amber→`tertiary`, red→`error`, text tiers→`onSurface`/`onSurfaceVariant`.

### Typography — Roboto only, `tabular-nums` on all metric styles

| Style | Spec |
|---|---|
| Metric value | 17sp / 700 |
| Hero value (LAST line) | 22sp / 700 |
| Unit | 10sp / 400, muted |
| Metric label | 11sp / 500, UPPERCASE, +0.8 letter-spacing |
| Status / caption | 10sp / 400, muted |
| Screen title | 15sp / 600 |
| Body | 14sp / 400 |
| Body small | 13sp / 400 |

### Spacing & shape

- Scale: **4 / 8 / 12 / 16 / 24 / 32 dp**
- Nav rail width **56 dp**; status strip height **24 dp**
- Card corner **14 dp**; chips fully rounded (pill)

### Motion

- Landing pulse ring **500 ms** ease-out
- Tracer draw-on **700 ms**
- Shot replay: real flight duration at 1x; 2x / 4x toggle scales the animation only
- Chip / row fade-in **150 ms**
- Material standard easings

## 6. Component kit & showcase screen

| Component | Contract |
|---|---|
| `GolfTheme` | wrapper applying dark MaterialTheme + token locals |
| `MetricChip(label, value, unit, accent)` | telemetry chip; accent → left-border variant |
| `MetricRow(label, value, unit, hot)` | side-panel row; `hot=true` → amber left-accent + tint |
| `NavRail` / `NavRailButton(icon, label, selected)` | 56 dp slim landscape rail |
| `StatusStrip(state)` | BLE armed dot (green) + session text (avg/σ/misread) |
| `SectionCard(title)` | `#151A1F` card, 14 dp radius |

**Showcase screen** (in `:app`, replaces the scaffold text): one landscape screen laid
out like the range view — nav rail left, status strip bottom, a SectionCard grid
demonstrating every chip/row state (normal / hot / teal / amber / comparison hues),
plus BLE-green and alert-red indicators. This is the M3 visual acceptance surface on
the tablet.

## 7. Verification, build & testing

- **Build**: `:core:designsystem` via `build-logic` convention plugin
  (`golf-android-library`). AGP built-in Kotlin (do NOT apply `org.jetbrains.kotlin.android`
  — M0 amendment), compose plugin, minSdk 31.
- **CI**: existing workflow builds the new module; no workflow changes.
- **Unit tests** (JVM, in `:core:designsystem`): assert exact token values against this
  spec (hex literals, spacing numbers, motion durations) so token drift fails CI.
- **No screenshot tests in M3** (YAGNI) — the tablet showcase is the visual gate.
- **Acceptance**: CI green + tablet install of the showcase + user visual check
  (dark, readable at shed distance, amber reads live-moment).

## 8. Out of scope (with future home)

- Chart components (box plots, dispersion overlays) — M6/M7 screens.
- POV / follow-cam canvas — M4 Clean Practice Environment.
- `UnitsPreference` logic + persistence — M5 (tokens carry label units only).
- Slot chips, verdict strips — M7 A/B engine UI.

## 9. M4+ handoff notes

- Metrics/landing marker render instantly; animation never gates feedback (decision 3).
  ROADMAP M4 exit wording ("<2s perceived") should be amended to reference instant
  metrics at M4 kickoff.
- Hole-mode tokens (fairway `#161B21`, pin, to-pin readout) are ready for the M4
  hole-mode UI.
- Comparison hue set reserved for M7 (up to 4 configurations; deltas vs reference slot).
- The 4th-module convention plugin (this milestone) is the pattern for all future
  modules (M5 persistence etc.).
